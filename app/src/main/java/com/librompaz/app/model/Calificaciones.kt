package com.librompaz.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Instrumento de evaluación (parte de la "Planilla de calificaciones" del caso). Tabla "evaluaciones". */
@Entity(
    tableName = "evaluaciones",
    foreignKeys = [
        ForeignKey(entity = Curso::class, parentColumns = ["id"], childColumns = ["cursoId"]),
        ForeignKey(entity = Asignatura::class, parentColumns = ["id"], childColumns = ["asignaturaId"])
    ],
    indices = [Index("cursoId"), Index("asignaturaId")]
)
data class Evaluacion(
    @PrimaryKey val id: String,
    val cursoId: String,
    val asignaturaId: String,
    val nombreInstrumento: String,
    val fecha: String
)

/**
 * Calificación en escala numérica chilena (1,0 a 7,0: se valida en viewmodel/Validaciones.kt).
 *
 * Tabla "calificaciones": una nota por estudiante y evaluación, por eso el id es
 * "<evaluacionId>_<estudianteId>". onDelete = CASCADE: si se elimina la evaluación, SQLite
 * borra sus notas automáticamente.
 */
@Entity(
    tableName = "calificaciones",
    foreignKeys = [
        ForeignKey(
            entity = Evaluacion::class,
            parentColumns = ["id"],
            childColumns = ["evaluacionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(entity = Estudiante::class, parentColumns = ["id"], childColumns = ["estudianteId"])
    ],
    indices = [Index("evaluacionId"), Index("estudianteId")]
)
data class Calificacion(
    @PrimaryKey val id: String,
    val evaluacionId: String,
    val estudianteId: String,
    val nota: Double,
    val registradoPor: String
)
