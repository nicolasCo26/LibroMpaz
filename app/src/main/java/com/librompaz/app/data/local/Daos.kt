package com.librompaz.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.Asignatura
import com.librompaz.app.model.Calificacion
import com.librompaz.app.model.CargaDocente
import com.librompaz.app.model.Curso
import com.librompaz.app.model.Estudiante
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.model.ObjetivoAprendizaje
import com.librompaz.app.model.RegistroAsistencia
import com.librompaz.app.model.RegistroAuditoria
import com.librompaz.app.model.SesionClase
import com.librompaz.app.model.Usuario
import kotlinx.coroutines.flow.Flow

// =====================================================================================
// DAOs (Data Access Object): las consultas a cada tabla. Room escribe la implementación al
// compilar (por eso se usa KSP).
//  - Las lecturas que las pantallas OBSERVAN devuelven Flow (no son suspend): Room emite una
//    lista nueva cada vez que la tabla cambia, y la pantalla se actualiza sola.
//  - Las escrituras y las lecturas puntuales son suspend: tocan el disco y no pueden
//    ejecutarse en el hilo principal.
// =====================================================================================

/** Módulo "Autenticación y alcance por rol". */
@Dao
interface UsuarioDao {

    // Login: busca por usuario sin distinguir mayúsculas. La clave se compara en el ViewModel.
    @Query("SELECT * FROM usuarios WHERE usuario = :usuario COLLATE NOCASE LIMIT 1")
    suspend fun buscarPorUsuario(usuario: String): Usuario?

    // Para saber si la base está vacía y hay que poblarla con los datos ficticios.
    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun contar(): Int

    @Insert
    suspend fun insertarTodos(usuarios: List<Usuario>)
}

/** Datos de referencia: cursos, asignaturas, estudiantes, OA y carga docente. */
@Dao
interface AcademicoDao {

    @Query("SELECT * FROM cursos ORDER BY nombre")
    fun cursos(): Flow<List<Curso>>

    @Query("SELECT * FROM asignaturas ORDER BY nombre")
    fun asignaturas(): Flow<List<Asignatura>>

    @Query("SELECT * FROM estudiantes ORDER BY nombreFicticio")
    fun estudiantes(): Flow<List<Estudiante>>

    @Query("SELECT * FROM objetivos ORDER BY asignaturaId, codigo")
    fun objetivos(): Flow<List<ObjetivoAprendizaje>>

    @Query("SELECT * FROM cargas_docentes")
    fun cargas(): Flow<List<CargaDocente>>

    // RBAC (RF-01): SOLO las cargas del docente indicado.
    @Query("SELECT * FROM cargas_docentes WHERE docenteId = :docenteId")
    fun cargasDe(docenteId: String): Flow<List<CargaDocente>>

    @Insert
    suspend fun insertarCursos(cursos: List<Curso>)

    @Insert
    suspend fun insertarAsignaturas(asignaturas: List<Asignatura>)

    @Insert
    suspend fun insertarEstudiantes(estudiantes: List<Estudiante>)

    @Insert
    suspend fun insertarObjetivos(objetivos: List<ObjetivoAprendizaje>)

    @Insert
    suspend fun insertarCargas(cargas: List<CargaDocente>)
}

/** Módulo "Asistencia". */
@Dao
interface AsistenciaDao {

    @Query("SELECT * FROM asistencias ORDER BY fecha DESC")
    fun todas(): Flow<List<RegistroAsistencia>>

    // Lo ya registrado de un curso en una fecha (para mostrarlo y poder corregirlo).
    @Query("SELECT * FROM asistencias WHERE cursoId = :cursoId AND fecha = :fecha")
    suspend fun delCursoEnFecha(cursoId: String, fecha: String): List<RegistroAsistencia>

    // REPLACE: como el id es "<estudiante>_<fecha>", volver a guardar el mismo día sobrescribe.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(registros: List<RegistroAsistencia>)
}

/** Módulo "Cobertura curricular". */
@Dao
interface SesionDao {

    @Query("SELECT * FROM sesiones ORDER BY fecha DESC, bloque")
    fun todas(): Flow<List<SesionClase>>

    @Insert
    suspend fun insertar(sesion: SesionClase)

    @Insert
    suspend fun insertarTodas(sesiones: List<SesionClase>)
}

/** Módulo "Calificaciones". */
@Dao
interface CalificacionDao {

    @Query("SELECT * FROM evaluaciones ORDER BY fecha DESC")
    fun evaluaciones(): Flow<List<Evaluacion>>

    @Query("SELECT * FROM calificaciones")
    fun calificaciones(): Flow<List<Calificacion>>

    @Query("SELECT * FROM calificaciones WHERE evaluacionId = :evaluacionId")
    suspend fun notasDe(evaluacionId: String): List<Calificacion>

    @Insert
    suspend fun insertarEvaluacion(evaluacion: Evaluacion)

    @Insert
    suspend fun insertarEvaluaciones(evaluaciones: List<Evaluacion>)

    // Al borrar la evaluación, sus notas se borran solas (ForeignKey.CASCADE en Calificacion).
    @Delete
    suspend fun eliminarEvaluacion(evaluacion: Evaluacion)

    // REPLACE: el id es "<evaluacion>_<estudiante>", así que corregir una nota la sobrescribe.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarNotas(notas: List<Calificacion>)

    // Notas que el docente dejó en blanco al corregir: se eliminan.
    @Query("DELETE FROM calificaciones WHERE evaluacionId = :evaluacionId AND estudianteId IN (:estudianteIds)")
    suspend fun eliminarNotas(evaluacionId: String, estudianteIds: List<String>)
}

/** Módulo "Hoja de vida": solo se agregan anotaciones (no se editan ni borran). */
@Dao
interface AnotacionDao {

    @Query("SELECT * FROM anotaciones ORDER BY fechaHora DESC")
    fun todas(): Flow<List<Anotacion>>

    @Insert
    suspend fun insertar(anotacion: Anotacion)

    @Insert
    suspend fun insertarTodas(anotaciones: List<Anotacion>)
}

/**
 * Auditoría (RN-02): INALTERABLE. A propósito este DAO no tiene @Update ni @Delete, así que
 * desde la app no hay forma de modificar ni borrar un registro de auditoría.
 */
@Dao
interface AuditoriaDao {

    @Query("SELECT * FROM auditoria ORDER BY fechaHora DESC")
    fun todos(): Flow<List<RegistroAuditoria>>

    @Insert
    suspend fun insertar(registro: RegistroAuditoria)

    @Insert
    suspend fun insertarTodos(registros: List<RegistroAuditoria>)
}
