package com.librompaz.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EstadoAsistencia(val etiqueta: String, val abreviatura: String) {
    PRESENTE("Presente", "P"),
    AUSENTE("Ausente", "A"),
    ATRASADO("Atrasado", "T"),
    JUSTIFICADO("Justificado", "J")
}

/**
 * Registro de asistencia diaria (entidad del dominio). registradoPor y fechaHoraRegistro son la
 * "firma" automática exigida por la regla de auditoría.
 *
 * Tabla "asistencias": una fila por estudiante y fecha. Por eso el id se arma como
 * "<estudianteId>_<fecha>": si se vuelve a guardar el mismo día, la fila se reemplaza
 * (no se duplica). El estado (enum) Room lo guarda como texto.
 */
@Entity(
    tableName = "asistencias",
    foreignKeys = [ForeignKey(entity = Estudiante::class, parentColumns = ["id"], childColumns = ["estudianteId"])],
    indices = [Index("estudianteId"), Index(value = ["cursoId", "fecha"])]
)
data class RegistroAsistencia(
    @PrimaryKey val id: String,
    val estudianteId: String,
    val cursoId: String,
    val fecha: String,
    val estado: EstadoAsistencia,
    val registradoPor: String,
    val fechaHoraRegistro: String
)
