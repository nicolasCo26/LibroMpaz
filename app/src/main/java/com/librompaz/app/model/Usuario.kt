package com.librompaz.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Usuario del sistema (solo datos ficticios). Tabla "usuarios".
 * Solo existen los cuatro perfiles internos: no hay usuarios estudiantes ni apoderados (RN-01).
 */
@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey val id: String,
    val nombre: String,
    val usuario: String,
    val clave: String,
    val perfil: Perfil
)
