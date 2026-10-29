package com.librompaz.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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

/**
 * DATABASE: une todas las entidades (tablas) con sus DAO y entrega UNA sola instancia de la base
 * de datos para toda la app (patrón singleton). El archivo SQLite se llama "libro_mpaz.db" y
 * queda en el almacenamiento interno de la app: los datos sobreviven al cierre de la app.
 *
 * Si se cambia una entidad (por ejemplo, se agrega una columna), hay que subir "version".
 */
@Database(
    entities = [
        Usuario::class,
        Curso::class,
        Asignatura::class,
        Estudiante::class,
        CargaDocente::class,
        RegistroAsistencia::class,
        ObjetivoAprendizaje::class,
        SesionClase::class,
        Evaluacion::class,
        Calificacion::class,
        Anotacion::class,
        RegistroAuditoria::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun academicoDao(): AcademicoDao
    abstract fun asistenciaDao(): AsistenciaDao
    abstract fun sesionDao(): SesionDao
    abstract fun calificacionDao(): CalificacionDao
    abstract fun anotacionDao(): AnotacionDao
    abstract fun auditoriaDao(): AuditoriaDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "libro_mpaz.db"
                )
                    // Solo en desarrollo: si cambia el esquema, borra y recrea la base.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instancia = it }
            }
    }
}
