package com.librompaz.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.librompaz.app.data.local.AppDatabase
import com.librompaz.app.data.repository.LibroMpazRepository
import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.Asignatura
import com.librompaz.app.model.Calificacion
import com.librompaz.app.model.CargaDocente
import com.librompaz.app.model.Curso
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.Estudiante
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.model.ObjetivoAprendizaje
import com.librompaz.app.model.Perfil
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.ResumenCurso
import com.librompaz.app.model.SesionClase
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.model.Usuario
import com.librompaz.app.recursosnativos.ExportadorCsv
import com.librompaz.app.util.ahora
import com.librompaz.app.util.hoy
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del formulario de login (data class + StateFlow en el ViewModel). */
data class LoginUiState(
    val usuario: String = "",
    val clave: String = "",
    val errores: ErroresLogin = ErroresLogin(),
    val ingresando: Boolean = false
)

/**
 * VIEWMODEL único, compartido por todas las pantallas (se crea una sola vez en AppNavigation).
 * Guarda el estado de las pantallas y decide QUÉ hacer (validar, guardar, calcular métricas).
 * No sabe CÓMO se guardan los datos: eso es trabajo de LibroMpazRepository.
 *
 * Flujo de un dato: Pantalla (@Composable) -> ViewModel (valida) -> Repository -> microservicio
 * (Retrofit) y/o DAO -> Room. Y de vuelta: Room emite un Flow -> el ViewModel lo expone como
 * StateFlow -> la pantalla lo observa con collectAsState() y se redibuja sola.
 *
 * Es AndroidViewModel porque necesita un Context para abrir la base de datos y escribir el
 * archivo del reporte. El repositorio llega por el constructor para poder usar un mock en las
 * pruebas; @JvmOverloads crea además el constructor (Application) que usa viewModel().
 */
class LibroMpazViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: LibroMpazRepository = LibroMpazRepository.crear(AppDatabase.getInstance(application))
) : AndroidViewModel(application) {

    /** false mientras se pobla la base la primera vez (el login muestra un indicador de carga). */
    private val _datosListos = MutableStateFlow(false)
    val datosListos: StateFlow<Boolean> = _datosListos.asStateFlow()

    /**
     * Convierte un Flow de Room en StateFlow. Eagerly: empieza a escuchar de inmediato y no se
     * detiene, así .value siempre está al día (las funciones de abajo como cursoPorId() lo leen).
     * Es seguro porque hay un solo ViewModel y la base es pequeña.
     */
    private fun <T> Flow<List<T>>.comoEstado(): StateFlow<List<T>> =
        stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // =====================================================================
    // Conexión con los microservicios (persistencia remota)
    // =====================================================================

    /** true = el último contacto con los microservicios funcionó; false = falló; null = aún no se sabe. */
    private val _conectado = MutableStateFlow<Boolean?>(null)
    val conectado: StateFlow<Boolean?> = _conectado.asStateFlow()

    private val _sincronizando = MutableStateFlow(false)
    val sincronizando: StateFlow<Boolean> = _sincronizando.asStateFlow()

    /** Último error al hablar con un microservicio (las pantallas lo muestran en rojo). */
    private val _errorServidor = MutableStateFlow<String?>(null)
    val errorServidor: StateFlow<String?> = _errorServidor.asStateFlow()

    fun limpiarErrorServidor() {
        _errorServidor.value = null
    }

    // Va DESPUÉS de declarar el estado de conexión: si la corrutina corre de inmediato,
    // sincronizar() ya encuentra _sincronizando y _conectado creados.
    init {
        viewModelScope.launch {
            repository.poblarSiVacia()
            _datosListos.value = true
            sincronizar()
        }
    }

    /**
     * GET a los microservicios: trae evaluaciones, notas y anotaciones y actualiza la copia local.
     * Se llama al abrir la app, al ingresar y al entrar a cada pantalla que usa esos datos.
     */
    fun sincronizar() {
        viewModelScope.launch {
            _sincronizando.value = true
            try {
                repository.sincronizar()
                _conectado.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _conectado.value = false
            } finally {
                _sincronizando.value = false
            }
        }
    }

    /**
     * Ejecuta una escritura que pasa por un microservicio. Si responde bien, llama a alTerminar();
     * si falla (sin red, 400, 403...), deja el mensaje en errorServidor y no se guarda nada.
     */
    private fun enServidor(accion: suspend () -> Unit, alTerminar: () -> Unit) {
        viewModelScope.launch {
            try {
                accion()
                _conectado.value = true
                _errorServidor.value = null
                alTerminar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is IOException) _conectado.value = false
                _errorServidor.value = mensajeDeError(e)
            }
        }
    }

    // =====================================================================
    // Módulo "Autenticación y alcance por rol" (RF-01, RF-02)
    // =====================================================================

    private val _usuarioActivo = MutableStateFlow<Usuario?>(null)
    val usuarioActivo: StateFlow<Usuario?> = _usuarioActivo.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    // Al escribir se limpia el error de ese campo.
    fun onUsuarioChange(valor: String) =
        _login.update { it.copy(usuario = valor, errores = it.errores.copy(usuario = null, general = null)) }

    fun onClaveChange(valor: String) =
        _login.update { it.copy(clave = valor, errores = it.errores.copy(clave = null, general = null)) }

    /**
     * Valida los campos y busca el usuario en Room. Si las credenciales son correctas, deja el
     * usuario activo y llama a onIngresar(perfil): AppNavigation lleva a cada perfil a su panel.
     */
    fun ingresar(onIngresar: (Perfil) -> Unit) {
        val estado = _login.value
        val errores = validarLogin(estado.usuario, estado.clave)
        if (errores.hayErrores) {
            _login.update { it.copy(errores = errores) }
            return
        }
        viewModelScope.launch {
            _login.update { it.copy(ingresando = true) }
            val usuario = repository.buscarUsuario(estado.usuario.trim())
            if (usuario == null || usuario.clave != estado.clave) {
                // Mensaje genérico a propósito: no revela si el correo existe.
                _login.update { it.copy(ingresando = false, errores = ErroresLogin(general = "Correo o clave incorrectos")) }
                return@launch
            }
            _usuarioActivo.value = usuario
            _login.value = LoginUiState()   // limpia el formulario: la clave no queda en memoria
            onIngresar(usuario.perfil)
            sincronizar()
        }
    }

    fun logout() {
        _usuarioActivo.value = null
        _login.value = LoginUiState()
        seleccionarCurso(null)
    }

    /**
     * RBAC (RF-01): las cargas SOLO del docente autenticado. La consulta filtra por docenteId en
     * SQL (AcademicoDao.cargasDe). flatMapLatest cambia de consulta cada vez que cambia el usuario.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val misCargas: StateFlow<List<CargaDocente>> = _usuarioActivo
        .flatMapLatest { usuario ->
            if (usuario?.perfil == Perfil.DOCENTE) repository.cargasDe(usuario.id) else flowOf(emptyList())
        }
        .comoEstado()

    /** RBAC (RF-02): el docente solo registra en los cursos (y asignaturas) que tiene asignados. */
    private fun tieneCarga(cursoId: String, asignaturaId: String? = null): Boolean =
        _usuarioActivo.value?.perfil == Perfil.DOCENTE &&
            misCargas.value.any { it.cursoId == cursoId && (asignaturaId == null || it.asignaturaId == asignaturaId) }

    // =====================================================================
    // Datos de referencia y selección
    // =====================================================================

    val cursos: StateFlow<List<Curso>> = repository.cursos.comoEstado()
    val asignaturas: StateFlow<List<Asignatura>> = repository.asignaturas.comoEstado()
    val estudiantes: StateFlow<List<Estudiante>> = repository.estudiantes.comoEstado()
    val objetivos: StateFlow<List<ObjetivoAprendizaje>> = repository.objetivos.comoEstado()
    private val cargas: StateFlow<List<CargaDocente>> = repository.cargas.comoEstado()

    private val _cursoSeleccionadoId = MutableStateFlow<String?>(null)
    val cursoSeleccionadoId: StateFlow<String?> = _cursoSeleccionadoId.asStateFlow()

    private val _asignaturaSeleccionadaId = MutableStateFlow<String?>(null)
    val asignaturaSeleccionadaId: StateFlow<String?> = _asignaturaSeleccionadaId.asStateFlow()

    /** El curso y la asignatura elegidos se comparten entre pantallas a través del ViewModel. */
    fun seleccionarCurso(cursoId: String?, asignaturaId: String? = null) {
        _cursoSeleccionadoId.value = cursoId
        _asignaturaSeleccionadaId.value = asignaturaId
    }

    fun cursoPorId(id: String?): Curso? = cursos.value.find { it.id == id }
    fun asignaturaPorId(id: String?): Asignatura? = asignaturas.value.find { it.id == id }
    fun estudiantePorId(id: String?): Estudiante? = estudiantes.value.find { it.id == id }
    fun estudiantesDelCurso(cursoId: String?): List<Estudiante> = estudiantes.value.filter { it.cursoId == cursoId }
    fun objetivosDe(asignaturaId: String?): List<ObjetivoAprendizaje> = objetivos.value.filter { it.asignaturaId == asignaturaId }

    // =====================================================================
    // Módulo "Asistencia" (RF-03, RF-04, RF-05)
    // =====================================================================

    private val asistencias = repository.asistencias.comoEstado()

    /** Porcentaje de asistencia de cada curso (cursoId -> %), para Inspectoría. */
    val porcentajesAsistencia: StateFlow<Map<String, Int>> = asistencias
        .map { calcularPorcentajesPorCurso(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Estudiantes con inasistencias reiteradas y su número de ausencias (RF-11). */
    val alertasInasistencia: StateFlow<List<Pair<Estudiante, Int>>> =
        combine(asistencias, estudiantes) { registros, lista -> calcularInasistenciasReiteradas(registros, lista) }
            .comoEstado()

    /** Asistencia ya guardada del curso en esa fecha (idEstudiante -> estado), para corregirla. */
    suspend fun asistenciaGuardada(cursoId: String, fecha: String): Map<String, EstadoAsistencia> =
        repository.asistenciaDe(cursoId, fecha).associate { it.estudianteId to it.estado }

    fun validarFechaAsistencia(fecha: String): String? = validarFecha(fecha, hoy())

    /**
     * Guarda (o sobrescribe) la asistencia. Pueden hacerlo el docente del curso o Inspectoría
     * (RF-05). La auditoría la registra el repositorio en la misma transacción.
     */
    fun guardarAsistencia(
        cursoId: String,
        fecha: String,
        estados: Map<String, EstadoAsistencia>,
        alTerminar: () -> Unit
    ) {
        val usuario = _usuarioActivo.value ?: return
        val autorizado = usuario.perfil == Perfil.INSPECTORIA || tieneCarga(cursoId)
        if (!autorizado || validarFechaAsistencia(fecha) != null || estados.isEmpty()) return

        val detalle = "Asistencia ${cursoPorId(cursoId)?.nombre ?: cursoId}, $fecha"
        viewModelScope.launch {
            repository.guardarAsistencia(usuario, cursoId, fecha, estados, detalle)
            alTerminar()
        }
    }

    // =====================================================================
    // Módulo "Cobertura curricular" (RF-06, RF-07)
    // =====================================================================

    val sesiones: StateFlow<List<SesionClase>> = repository.sesiones.comoEstado()

    fun sesionesDe(cursoId: String?, asignaturaId: String?): List<SesionClase> =
        sesiones.value.filter { it.cursoId == cursoId && it.asignaturaId == asignaturaId }

    /** Valida y guarda la sesión. Devuelve los errores para mostrarlos junto a cada campo. */
    fun guardarSesion(
        cursoId: String,
        asignaturaId: String,
        fecha: String,
        bloque: String,
        oaIds: List<String>,
        contenido: String,
        alTerminar: () -> Unit
    ): ErroresSesion {
        val errores = validarSesion(fecha, bloque, oaIds, contenido, hoy())
        val usuario = _usuarioActivo.value
        if (errores.hayErrores || usuario == null || !tieneCarga(cursoId, asignaturaId)) return errores

        val detalle = "Sesión de clase ${cursoPorId(cursoId)?.nombre}, ${asignaturaPorId(asignaturaId)?.nombre}, $fecha ${bloque.trim()}"
        viewModelScope.launch {
            repository.guardarSesion(usuario, cursoId, asignaturaId, fecha, bloque.trim(), oaIds, contenido.trim(), detalle)
            alTerminar()
        }
        return errores
    }

    /** Ids de los OA ya trabajados en el curso y asignatura (vista de UTP). */
    fun oasTrabajados(cursoId: String?, asignaturaId: String?): Set<String> =
        calcularOasTrabajados(sesiones.value, cursoId, asignaturaId)

    // =====================================================================
    // Módulo "Calificaciones" (RF-08, RF-09)
    // =====================================================================

    val evaluaciones: StateFlow<List<Evaluacion>> = repository.evaluaciones.comoEstado()
    val calificaciones: StateFlow<List<Calificacion>> = repository.calificaciones.comoEstado()

    fun evaluacionesDe(cursoId: String?, asignaturaId: String?): List<Evaluacion> =
        evaluaciones.value.filter { it.cursoId == cursoId && it.asignaturaId == asignaturaId }

    fun calificacionesDe(evaluacionId: String?): List<Calificacion> =
        calificaciones.value.filter { it.evaluacionId == evaluacionId }

    fun crearEvaluacion(
        cursoId: String,
        asignaturaId: String,
        nombreInstrumento: String,
        fecha: String,
        alTerminar: () -> Unit
    ): ErroresEvaluacion {
        val errores = validarEvaluacion(nombreInstrumento, fecha, hoy())
        val usuario = _usuarioActivo.value
        if (errores.hayErrores || usuario == null || !tieneCarga(cursoId, asignaturaId)) return errores

        val nombre = nombreInstrumento.trim()
        val detalle = "Evaluación \"$nombre\" ${cursoPorId(cursoId)?.nombre}, ${asignaturaPorId(asignaturaId)?.nombre}"
        enServidor({ repository.crearEvaluacion(usuario, cursoId, asignaturaId, nombre, fecha, detalle) }, alTerminar)
        return errores
    }

    /** Corrige el nombre o la fecha de una evaluación (PUT al microservicio). */
    fun actualizarEvaluacion(
        evaluacion: Evaluacion,
        nombreInstrumento: String,
        fecha: String,
        alTerminar: () -> Unit
    ): ErroresEvaluacion {
        val errores = validarEvaluacion(nombreInstrumento, fecha, hoy())
        val usuario = _usuarioActivo.value
        if (errores.hayErrores || usuario == null || !tieneCarga(evaluacion.cursoId, evaluacion.asignaturaId)) return errores

        val editada = evaluacion.copy(nombreInstrumento = nombreInstrumento.trim(), fecha = fecha)
        val detalle = "Evaluación \"${evaluacion.nombreInstrumento}\" corregida a \"${editada.nombreInstrumento}\" (${editada.fecha})"
        enServidor({ repository.actualizarEvaluacion(usuario, editada, detalle) }, alTerminar)
        return errores
    }

    /** Error de una nota escrita (o null si es válida). La regla está en Validaciones.kt. */
    fun errorDeNota(texto: String): String? = validarNota(texto)

    /**
     * Guarda las notas escritas (idEstudiante -> texto). Devuelve los errores por estudiante: si
     * hay alguno, no se guarda nada. Una nota que existía y se dejó en blanco se elimina.
     */
    fun guardarCalificaciones(
        evaluacion: Evaluacion,
        notas: Map<String, String>,
        alTerminar: () -> Unit
    ): Map<String, String> {
        val errores = notas.mapNotNull { (estudianteId, texto) -> validarNota(texto)?.let { estudianteId to it } }.toMap()
        val usuario = _usuarioActivo.value
        if (errores.isNotEmpty() || usuario == null || !tieneCarga(evaluacion.cursoId, evaluacion.asignaturaId)) return errores

        val validas = notas.mapNotNull { (estudianteId, texto) -> parsearNota(texto)?.let { estudianteId to it } }.toMap()
        val sinNota = calificacionesDe(evaluacion.id).map { it.estudianteId }.filter { it !in validas }
        if (validas.isEmpty() && sinNota.isEmpty()) return errores

        val detalle = "Notas de \"${evaluacion.nombreInstrumento}\" ${cursoPorId(evaluacion.cursoId)?.nombre} (${validas.size} notas)"
        enServidor({ repository.guardarCalificaciones(usuario, evaluacion.id, validas, detalle) }, alTerminar)
        return errores
    }

    /** Elimina la evaluación y sus notas (la pantalla pide confirmación antes, RNF-02). */
    fun eliminarEvaluacion(evaluacion: Evaluacion, alTerminar: () -> Unit) {
        val usuario = _usuarioActivo.value ?: return
        if (!tieneCarga(evaluacion.cursoId, evaluacion.asignaturaId)) return
        val detalle = "Evaluación \"${evaluacion.nombreInstrumento}\" ${cursoPorId(evaluacion.cursoId)?.nombre} y sus notas"
        enServidor({ repository.eliminarEvaluacion(usuario, evaluacion, detalle) }, alTerminar)
    }

    /** Desfases u omisiones en el registro de notas, para UTP. */
    val desfases: StateFlow<List<String>> = combine(
        cargas,
        evaluaciones,
        calificaciones,
        estudiantes,
        combine(cursos, asignaturas) { c, a -> c to a }
    ) { listaCargas, listaEvaluaciones, listaNotas, listaEstudiantes, (listaCursos, listaAsignaturas) ->
        calcularDesfases(listaCargas, listaEvaluaciones, listaNotas, listaEstudiantes, listaCursos, listaAsignaturas)
    }.comoEstado()

    // =====================================================================
    // Módulo "Hoja de vida" (RF-10, RF-11)
    // =====================================================================

    val anotaciones: StateFlow<List<Anotacion>> = repository.anotaciones.comoEstado()

    fun anotacionesDe(estudianteId: String?): List<Anotacion> =
        anotaciones.value.filter { it.estudianteId == estudianteId }

    /** Hoja de vida del colegio filtrada por tipo (null = todas), para Inspectoría. */
    fun anotacionesPorTipo(tipo: TipoAnotacion?): List<Anotacion> =
        anotaciones.value.filter { tipo == null || it.tipo == tipo }

    fun validarNuevaAnotacion(estudiante: Estudiante?, tipo: TipoAnotacion?, descripcion: String): ErroresAnotacion =
        validarAnotacion(estudiante != null, tipo != null, descripcion)

    /** Registra la anotación con marca temporal y firma (usuario activo + fecha y hora). */
    fun registrarAnotacion(estudiante: Estudiante, tipo: TipoAnotacion, descripcion: String, alTerminar: () -> Unit) {
        val usuario = _usuarioActivo.value ?: return
        if (validarNuevaAnotacion(estudiante, tipo, descripcion).hayErrores || !tieneCarga(estudiante.cursoId)) return
        val detalle = "${tipo.etiqueta} a ${estudiante.nombreFicticio}"
        enServidor({ repository.registrarAnotacion(usuario, estudiante.id, tipo, descripcion.trim(), detalle) }, alTerminar)
    }

    /** Solo quien firmó la anotación puede corregirla o eliminarla (el microservicio lo vuelve a revisar). */
    fun puedeModificar(anotacion: Anotacion): Boolean = anotacion.firmadoPor == _usuarioActivo.value?.nombre

    /** Corrige el tipo y la descripción (PUT). La firma original (quién y cuándo) no cambia. */
    fun actualizarAnotacion(
        anotacion: Anotacion,
        tipo: TipoAnotacion?,
        descripcion: String,
        alTerminar: () -> Unit
    ): ErroresAnotacion {
        val errores = validarAnotacion(true, tipo != null, descripcion)
        val usuario = _usuarioActivo.value
        if (errores.hayErrores || tipo == null || usuario == null || !puedeModificar(anotacion)) return errores
        val detalle = "Anotación de ${estudiantePorId(anotacion.estudianteId)?.nombreFicticio} corregida (${tipo.etiqueta})"
        enServidor({ repository.actualizarAnotacion(usuario, anotacion, tipo, descripcion.trim(), detalle) }, alTerminar)
        return errores
    }

    /** Elimina la anotación (DELETE). La pantalla pide confirmación antes (RNF-02). */
    fun eliminarAnotacion(anotacion: Anotacion, alTerminar: () -> Unit) {
        val usuario = _usuarioActivo.value ?: return
        if (!puedeModificar(anotacion)) return
        val detalle = "${anotacion.tipo.etiqueta} de ${estudiantePorId(anotacion.estudianteId)?.nombreFicticio} eliminada"
        enServidor({ repository.eliminarAnotacion(usuario, anotacion, detalle) }, alTerminar)
    }

    /** Anotaciones negativas de estudiantes que requieren intervención de Inspectoría. */
    val alertasAnotaciones: StateFlow<List<Anotacion>> = anotaciones
        .map { calcularAnotacionesIntervencion(it) }
        .comoEstado()

    // =====================================================================
    // Módulo "Panel administrativo" (Dirección, RF-12) y auditoría (RN-02)
    // =====================================================================

    val auditoria: StateFlow<List<RegistroAuditoria>> = repository.auditoria.comoEstado()

    /** Métricas consolidadas por curso. Se recalculan solas cuando cambia cualquier tabla. */
    val resumenCursos: StateFlow<List<ResumenCurso>> = combine(
        cursos,
        asistencias,
        sesiones,
        combine(evaluaciones, calificaciones) { e, c -> e to c },
        combine(anotaciones, estudiantes) { a, e -> a to e }
    ) { listaCursos, listaAsistencias, listaSesiones, (listaEvaluaciones, listaNotas), (listaAnotaciones, listaEstudiantes) ->
        calcularResumenPorCurso(listaCursos, listaAsistencias, listaSesiones, listaEvaluaciones, listaNotas, listaAnotaciones, listaEstudiantes)
    }.comoEstado()

    /**
     * RECURSO NATIVO: escribe el reporte CSV en el archivo que Dirección eligió con el selector
     * del sistema (ver recursosnativos/ExportadorCsv.kt). Solo métricas por curso, sin nombres de
     * estudiantes. La exportación queda en la auditoría.
     */
    fun exportarReporte(uri: Uri, alTerminar: (Boolean) -> Unit) {
        val usuario = _usuarioActivo.value
        if (usuario?.perfil != Perfil.DIRECCION) {
            alTerminar(false)
            return
        }
        val contenido = generarReporteCsv(resumenCursos.value, cursos.value, usuario.nombre, ahora())
        viewModelScope.launch {
            val ok = ExportadorCsv.escribir(getApplication(), uri, contenido)
            if (ok) repository.registrarExportacion(usuario, "Reporte CSV de métricas por curso")
            alTerminar(ok)
        }
    }
}
