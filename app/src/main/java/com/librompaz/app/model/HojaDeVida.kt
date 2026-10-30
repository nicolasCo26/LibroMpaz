package com.librompaz.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TipoAnotacion(val etiqueta: String) {
    POSITIVA("Anotación positiva"),
    NEGATIVA("Anotación negativa"),
    OBSERVACION("Observación conductual")
}

/**
 * Anotación de la hoja de vida escolar (entidad del dominio), con marca temporal y "firma digital"
 * del docente responsable. Tabla "anotaciones".
 *
 * Supuesto: la firma digital es el registro automático del usuario
 * autenticado + fecha y hora, después de una confirmación explícita del docente. Una vez
 * registrada, la anotación no se puede editar ni borrar desde la app.
 */
@Entity(
    tableName = "anotaciones",
    foreignKeys = [ForeignKey(entity = Estudiante::class, parentColumns = ["id"], childColumns = ["estudianteId"])],
    indices = [Index("estudianteId")]
)
data class Anotacion(
    @PrimaryKey val id: String,
    val estudianteId: String,
    val tipo: TipoAnotacion,
    val descripcion: String,
    val fechaHora: String,
    val firmadoPor: String
)
