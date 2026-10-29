package com.librompaz.app.data.repository

import androidx.room.withTransaction
import com.librompaz.app.data.MockData
import com.librompaz.app.data.local.AppDatabase
import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.Calificacion
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

/**
 * REPOSITORY: la única puerta de entrada a los datos para el ViewModel.
 *
 * El ViewModel no sabe si los datos vienen de Room, de una API REST o de ambos; solo le pide
 * datos al repositorio. En la Experiencia 3, cuando se agregue Retrofit, se cambia esta clase y
 * el ViewModel y las pantallas quedan iguales.
 *
 * Regla de negocio RN-02 (auditoría automática e inalterable): cada función que crea o
 * modifica algo registra su auditoría DENTRO de la misma transacción (db.withTransaction).
 * Así, o se guardan las dos cosas, o ninguna: no puede quedar un cambio sin auditar.
 */
class LibroMpazRepository(private val db: AppDatabase) {

    private val usuarioDao = db.usuarioDao()
    private val academicoDao = db.academicoDao()
    private val asistenciaDao = db.asistenciaDao()
    private val sesionDao = db.sesionDao()
    private val calificacionDao = db.calificacionDao()
    private val anotacionDao = db.anotacionDao()
    private val auditoriaDao = db.auditoriaDao()

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
        db.withTransaction {
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

    // ---------- Escrituras (todas con auditoría en la misma transacción) ----------

    /** Guarda o sobrescribe la asistencia del curso en esa fecha. */
    suspend fun guardarAsistencia(
        usuario: Usuario,
        cursoId: String,
        fecha: String,
        estados: Map<String, EstadoAsistencia>,
        detalle: String
    ) = db.withTransaction {
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
    ) = db.withTransaction {
        sesionDao.insertar(
            SesionClase(nuevoId(), cursoId, asignaturaId, fecha, bloque, oaIds, contenido, usuario.nombre, ahora())
        )
        auditar(usuario, "CREAR", detalle)
    }

    suspend fun crearEvaluacion(
        usuario: Usuario,
        cursoId: String,
        asignaturaId: String,
        nombreInstrumento: String,
        fecha: String,
        detalle: String
    ) = db.withTransaction {
        calificacionDao.insertarEvaluacion(Evaluacion(nuevoId(), cursoId, asignaturaId, nombreInstrumento, fecha))
        auditar(usuario, "CREAR", detalle)
    }

    /** Elimina la evaluación; sus notas se borran por CASCADE. */
    suspend fun eliminarEvaluacion(usuario: Usuario, evaluacion: Evaluacion, detalle: String) = db.withTransaction {
        calificacionDao.eliminarEvaluacion(evaluacion)
        auditar(usuario, "ELIMINAR", detalle)
    }

    /**
     * Guarda las notas (estudianteId -> nota). Los estudiantes en sinNota tenían nota y el
     * docente la dejó en blanco: esa nota se elimina.
     */
    suspend fun guardarCalificaciones(
        usuario: Usuario,
        evaluacionId: String,
        notas: Map<String, Double>,
        sinNota: List<String>,
        detalle: String
    ) = db.withTransaction {
        val habiaNotas = calificacionDao.notasDe(evaluacionId).isNotEmpty()
        calificacionDao.guardarNotas(
            notas.map { (estudianteId, nota) ->
                Calificacion("${evaluacionId}_$estudianteId", evaluacionId, estudianteId, nota, usuario.nombre)
            }
        )
        if (sinNota.isNotEmpty()) calificacionDao.eliminarNotas(evaluacionId, sinNota)
        auditar(usuario, if (habiaNotas) "MODIFICAR" else "CREAR", detalle)
    }

    /** La "firma digital" es el usuario autenticado + la fecha y hora del momento (supuesto). */
    suspend fun registrarAnotacion(
        usuario: Usuario,
        estudianteId: String,
        tipo: TipoAnotacion,
        descripcion: String,
        detalle: String
    ) = db.withTransaction {
        anotacionDao.insertar(Anotacion(nuevoId(), estudianteId, tipo, descripcion, ahora(), usuario.nombre))
        auditar(usuario, "CREAR", detalle)
    }

    /** Exportar un reporte no modifica datos, pero igual queda en la auditoría (trazabilidad). */
    suspend fun registrarExportacion(usuario: Usuario, detalle: String) = auditar(usuario, "EXPORTAR", detalle)

    private suspend fun auditar(usuario: Usuario, accion: String, detalle: String) {
        auditoriaDao.insertar(RegistroAuditoria(nuevoId(), usuario.nombre, usuario.perfil, accion, detalle, ahora()))
    }

    private fun nuevoId(): String = UUID.randomUUID().toString()
}
