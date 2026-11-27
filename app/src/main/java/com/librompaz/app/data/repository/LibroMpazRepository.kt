package com.librompaz.app.data.repository

import androidx.room.withTransaction
import com.librompaz.app.data.MockData
import com.librompaz.app.data.local.AcademicoDao
import com.librompaz.app.data.local.AnotacionDao
import com.librompaz.app.data.local.AppDatabase
import com.librompaz.app.data.local.AsistenciaDao
import com.librompaz.app.data.local.AuditoriaDao
import com.librompaz.app.data.local.CalificacionDao
import com.librompaz.app.data.local.SesionDao
import com.librompaz.app.data.local.UsuarioDao
import com.librompaz.app.data.remote.AnotacionDto
import com.librompaz.app.data.remote.AnotacionesApi
import com.librompaz.app.data.remote.CalificacionesApi
import com.librompaz.app.data.remote.NotaDto
import com.librompaz.app.data.remote.PlanillaNotas
import com.librompaz.app.data.remote.RetrofitClient
import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.CargaDocente
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.model.RegistroAsistencia
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.SesionClase
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.model.Usuario
import com.librompaz.app.util.ahora
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import retrofit2.Response

/**
 * REPOSITORY: la única puerta de entrada a los datos para el ViewModel.
 *
 * Combina dos fuentes:
 *  - Room (SQLite en el teléfono): usuarios, cursos, asistencia, sesiones y auditoría. Además
 *    guarda una COPIA LOCAL de evaluaciones, notas y anotaciones para que las pantallas las
 *    observen con Flow y sigan mostrando datos si no hay red.
 *  - Microservicios propios (Retrofit): ms-calificaciones y ms-anotaciones son la fuente de
 *    verdad de esos datos. Toda escritura va PRIMERO al microservicio; solo si responde bien se
 *    actualiza la copia local. Si falla, se lanza la excepción y la copia local no cambia.
 *
 * Regla de negocio RN-02 (auditoría automática e inalterable): cada cambio guarda su auditoría
 * DENTRO de la misma transacción de Room. O se guardan las dos cosas, o ninguna.
 *
 * Las dependencias llegan por el constructor para poder reemplazarlas por mocks en las pruebas.
 */
class LibroMpazRepository(
    private val usuarioDao: UsuarioDao,
    private val academicoDao: AcademicoDao,
    private val asistenciaDao: AsistenciaDao,
    private val sesionDao: SesionDao,
    private val calificacionDao: CalificacionDao,
    private val anotacionDao: AnotacionDao,
    private val auditoriaDao: AuditoriaDao,
    private val calificacionesApi: CalificacionesApi,
    private val anotacionesApi: AnotacionesApi,
    /** Ejecuta el bloque dentro de una transacción de Room (en las pruebas se ejecuta directo). */
    private val enTransaccion: suspend (suspend () -> Unit) -> Unit
) {

    companion object {
        /** Repositorio real: la base Room de la app y los microservicios configurados en Retrofit. */
        fun crear(db: AppDatabase) = LibroMpazRepository(
            usuarioDao = db.usuarioDao(),
            academicoDao = db.academicoDao(),
            asistenciaDao = db.asistenciaDao(),
            sesionDao = db.sesionDao(),
            calificacionDao = db.calificacionDao(),
            anotacionDao = db.anotacionDao(),
            auditoriaDao = db.auditoriaDao(),
            calificacionesApi = RetrofitClient.calificacionesApi,
            anotacionesApi = RetrofitClient.anotacionesApi,
            enTransaccion = { bloque -> db.withTransaction { bloque() } }
        )
    }

    // ---------- Lecturas observables (Flow: se actualizan solas cuando cambia la tabla) ----------

    val cursos = academicoDao.cursos()
    val asignaturas = academicoDao.asignaturas()
    val estudiantes = academicoDao.estudiantes()
    val objetivos = academicoDao.objetivos()
    val cargas = academicoDao.cargas()
    val asistencias = asistenciaDao.todas()
    val sesiones = sesionDao.todas()
    val evaluaciones = calificacionDao.evaluaciones()
    val calificaciones = calificacionDao.calificaciones()
    val anotaciones = anotacionDao.todas()
    val auditoria = auditoriaDao.todos()

    fun cargasDe(docenteId: String): Flow<List<CargaDocente>> = academicoDao.cargasDe(docenteId)

    // ---------- Lecturas puntuales (suspend) ----------

    suspend fun buscarUsuario(usuario: String): Usuario? = usuarioDao.buscarPorUsuario(usuario)

    suspend fun asistenciaDe(cursoId: String, fecha: String): List<RegistroAsistencia> =
        asistenciaDao.delCursoEnFecha(cursoId, fecha)

    /** La primera vez que se abre la app (base vacía), inserta los datos ficticios de MockData. */
    suspend fun poblarSiVacia() {
        if (usuarioDao.contar() > 0) return
        enTransaccion {
            // El orden importa por las claves foráneas: primero las tablas "padre".
            usuarioDao.insertarTodos(MockData.usuarios)
            academicoDao.insertarCursos(MockData.cursos)
            academicoDao.insertarAsignaturas(MockData.asignaturas)
            academicoDao.insertarEstudiantes(MockData.estudiantes)
            academicoDao.insertarObjetivos(MockData.objetivos)
            academicoDao.insertarCargas(MockData.cargas)
            asistenciaDao.guardar(MockData.asistencias)
            sesionDao.insertarTodas(MockData.sesiones)
            calificacionDao.insertarEvaluaciones(MockData.evaluaciones)
            calificacionDao.guardarNotas(MockData.calificaciones)
            anotacionDao.insertarTodas(MockData.anotaciones)
            auditoriaDao.insertarTodos(MockData.auditoria)
        }
    }

    // ---------- Sincronización con los microservicios (GET) ----------

    /**
     * Descarga evaluaciones, notas y anotaciones de los microservicios y reemplaza la copia local.
     * Se descartan los registros que apuntan a un curso, asignatura o estudiante que no existe en
     * el teléfono (romperían las claves foráneas de Room). Si no hay red, lanza IOException.
     */
    suspend fun sincronizar() {
        val evaluacionesRemotas = calificacionesApi.evaluaciones()
        val notasRemotas = calificacionesApi.calificaciones()
        val anotacionesRemotas = anotacionesApi.anotaciones()

        val cursos = academicoDao.idsCursos().toSet()
        val asignaturas = academicoDao.idsAsignaturas().toSet()
        val estudiantes = academicoDao.idsEstudiantes().toSet()
        val evaluaciones = evaluacionesRemotas.filter { it.cursoId in cursos && it.asignaturaId in asignaturas }
        val idsEvaluaciones = evaluaciones.map { it.id }.toSet()

        enTransaccion {
            calificacionDao.eliminarTodasLasEvaluaciones()   // CASCADE borra también las notas
            calificacionDao.insertarEvaluaciones(evaluaciones)
            calificacionDao.guardarNotas(notasRemotas.filter { it.evaluacionId in idsEvaluaciones && it.estudianteId in estudiantes })
            anotacionDao.eliminarTodas()
            anotacionDao.insertarTodas(anotacionesRemotas.filter { it.estudianteId in estudiantes })
        }
    }

    // ---------- Escrituras locales (Room) ----------

    /** Guarda o sobrescribe la asistencia del curso en esa fecha. */
    suspend fun guardarAsistencia(
        usuario: Usuario,
        cursoId: String,
        fecha: String,
        estados: Map<String, EstadoAsistencia>,
        detalle: String
    ) = enTransaccion {
        val yaExistia = asistenciaDao.delCursoEnFecha(cursoId, fecha).isNotEmpty()
        val marca = ahora()
        asistenciaDao.guardar(
            estados.map { (estudianteId, estado) ->
                RegistroAsistencia("${estudianteId}_$fecha", estudianteId, cursoId, fecha, estado, usuario.nombre, marca)
            }
        )
        auditar(usuario, if (yaExistia) "MODIFICAR" else "CREAR", detalle)
    }

    suspend fun guardarSesion(
        usuario: Usuario,
        cursoId: String,
        asignaturaId: String,
        fecha: String,
        bloque: String,
        oaIds: List<String>,
        contenido: String,
        detalle: String
    ) = enTransaccion {
        sesionDao.insertar(
            SesionClase(nuevoId(), cursoId, asignaturaId, fecha, bloque, oaIds, contenido, usuario.nombre, ahora())
        )
        auditar(usuario, "CREAR", detalle)
    }

    // ---------- Calificaciones: microservicio ms-calificaciones + copia local ----------

    /** POST /api/evaluaciones */
    suspend fun crearEvaluacion(
        usuario: Usuario,
        cursoId: String,
        asignaturaId: String,
        nombreInstrumento: String,
        fecha: String,
        detalle: String
    ) {
        val creada = calificacionesApi.crearEvaluacion(Evaluacion(nuevoId(), cursoId, asignaturaId, nombreInstrumento, fecha))
        enTransaccion {
            calificacionDao.insertarEvaluacion(creada)
            auditar(usuario, "CREAR", detalle)
        }
    }

    /** PUT /api/evaluaciones/{id}: corrige el nombre y la fecha. */
    suspend fun actualizarEvaluacion(usuario: Usuario, evaluacion: Evaluacion, detalle: String) {
        val actualizada = calificacionesApi.actualizarEvaluacion(evaluacion.id, evaluacion)
        enTransaccion {
            calificacionDao.actualizarEvaluacion(actualizada)
            auditar(usuario, "MODIFICAR", detalle)
        }
    }

    /** DELETE /api/evaluaciones/{id}: el microservicio borra también sus notas; aquí lo hace el CASCADE. */
    suspend fun eliminarEvaluacion(usuario: Usuario, evaluacion: Evaluacion, detalle: String) {
        verificarEliminacion(calificacionesApi.eliminarEvaluacion(evaluacion.id))
        enTransaccion {
            calificacionDao.eliminarEvaluacion(evaluacion)
            auditar(usuario, "ELIMINAR", detalle)
        }
    }

    /**
     * PUT /api/evaluaciones/{id}/calificaciones: envía la planilla completa (estudianteId -> nota).
     * Las notas que no van en la planilla se eliminan en el servidor y en la copia local.
     */
    suspend fun guardarCalificaciones(
        usuario: Usuario,
        evaluacionId: String,
        notas: Map<String, Double>,
        detalle: String
    ) {
        val habiaNotas = calificacionDao.notasDe(evaluacionId).isNotEmpty()
        val planilla = PlanillaNotas(usuario.nombre, notas.map { (estudianteId, nota) -> NotaDto(estudianteId, nota) })
        val guardadas = calificacionesApi.guardarNotas(evaluacionId, planilla)
        enTransaccion {
            calificacionDao.eliminarNotasDe(evaluacionId)
            calificacionDao.guardarNotas(guardadas)
            auditar(usuario, if (habiaNotas) "MODIFICAR" else "CREAR", detalle)
        }
    }

    // ---------- Hoja de vida: microservicio ms-anotaciones + copia local ----------

    /** POST /api/anotaciones. La "firma" es el usuario autenticado + la fecha y hora que pone el servidor. */
    suspend fun registrarAnotacion(
        usuario: Usuario,
        estudianteId: String,
        tipo: TipoAnotacion,
        descripcion: String,
        detalle: String
    ) {
        val creada = anotacionesApi.crear(AnotacionDto(nuevoId(), estudianteId, tipo, descripcion, usuario.nombre))
        enTransaccion {
            anotacionDao.insertar(creada)
            auditar(usuario, "CREAR", detalle)
        }
    }

    /** PUT /api/anotaciones/{id}: corrige tipo y descripción. El servidor responde 403 si no la firmó este usuario. */
    suspend fun actualizarAnotacion(
        usuario: Usuario,
        anotacion: Anotacion,
        tipo: TipoAnotacion,
        descripcion: String,
        detalle: String
    ) {
        val actualizada = anotacionesApi.actualizar(
            anotacion.id, AnotacionDto(anotacion.id, anotacion.estudianteId, tipo, descripcion, usuario.nombre)
        )
        enTransaccion {
            anotacionDao.insertar(actualizada)   // REPLACE: sobrescribe la copia local
            auditar(usuario, "MODIFICAR", detalle)
        }
    }

    /** DELETE /api/anotaciones/{id}?solicitante=... */
    suspend fun eliminarAnotacion(usuario: Usuario, anotacion: Anotacion, detalle: String) {
        verificarEliminacion(anotacionesApi.eliminar(anotacion.id, usuario.nombre))
        enTransaccion {
            anotacionDao.eliminar(anotacion)
            auditar(usuario, "ELIMINAR", detalle)
        }
    }

    // ---------- Otros ----------

    /** Exportar un reporte no modifica datos, pero igual queda en la auditoría (trazabilidad). */
    suspend fun registrarExportacion(usuario: Usuario, detalle: String) = auditar(usuario, "EXPORTAR", detalle)

    private suspend fun auditar(usuario: Usuario, accion: String, detalle: String) {
        auditoriaDao.insertar(RegistroAuditoria(nuevoId(), usuario.nombre, usuario.perfil, accion, detalle, ahora()))
    }

    /** Un DELETE responde 204. Si el registro ya no existía (404) se borra igual la copia local. */
    private fun verificarEliminacion(respuesta: Response<Unit>) {
        if (!respuesta.isSuccessful && respuesta.code() != 404) throw HttpException(respuesta)
    }

    private fun nuevoId(): String = UUID.randomUUID().toString()
}
