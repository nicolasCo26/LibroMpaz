package com.librompaz.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Curso o nivel (entidad "Estudiante y curso" del dominio). Tabla "cursos". */
@Entity(tableName = "cursos")
data class Curso(
    @PrimaryKey val id: String,
    val nombre: String
)

/** Tabla "asignaturas". */
@Entity(tableName = "asignaturas")
data class Asignatura(
    @PrimaryKey val id: String,
    val nombre: String
)

/**
 * Estudiante con identificación interna y nombre FICTICIO (prohibido usar datos reales o RUT).
 * Tabla "estudiantes": cada estudiante pertenece a un curso (clave foránea cursoId).
 */
@Entity(
    tableName = "estudiantes",
    foreignKeys = [ForeignKey(entity = Curso::class, parentColumns = ["id"], childColumns = ["cursoId"])],
    indices = [Index("cursoId")]
)
data class Estudiante(
    @PrimaryKey val id: String,
    val nombreFicticio: String,
    val cursoId: String
)

/**
 * Asignación de carga docente (entidad del dominio): vínculo docente–curso–asignatura.
 * RF-01: el docente solo ve los cursos y asignaturas bajo su asignación formal.
 * Supuesto: un docente puede tener más de una asignatura.
 */
@Entity(
    tableName = "cargas_docentes",
    foreignKeys = [
        ForeignKey(entity = Usuario::class, parentColumns = ["id"], childColumns = ["docenteId"]),
        ForeignKey(entity = Curso::class, parentColumns = ["id"], childColumns = ["cursoId"]),
        ForeignKey(entity = Asignatura::class, parentColumns = ["id"], childColumns = ["asignaturaId"])
    ],
    indices = [Index("docenteId"), Index("cursoId"), Index("asignaturaId")]
)
data class CargaDocente(
    @PrimaryKey val id: String,
    val docenteId: String,
    val cursoId: String,
    val asignaturaId: String
)
