package com.librompaz.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro de auditoría (entidad "Control de seguridad y trazabilidad" del dominio). Regla de
 * negocio RN-02: toda creación o modificación queda auditada (usuario, fecha y hora) de forma
 * automática e inalterable.
 *
 * Tabla "auditoria". Es INALTERABLE porque su DAO (AuditoriaDao) solo tiene @Insert y
 * consultas: no existe ninguna función para actualizar ni borrar estas filas.
 */
@Entity(tableName = "auditoria")
data class RegistroAuditoria(
    @PrimaryKey val id: String,
    val usuario: String,
    val perfil: Perfil,
    val accion: String,
    val detalle: String,
    val fechaHora: String
)

/**
 * Métricas consolidadas de un curso para Dirección. NO es una tabla: se calcula a partir de
 * las otras (ver viewmodel/Metricas.kt). Los valores null significan "sin datos todavía".
 */
data class ResumenCurso(
    val cursoId: String,
    val porcentajeAsistencia: Int? = null,
    val sesionesRegistradas: Int? = null,
    val calificacionesRegistradas: Int? = null,
    val anotacionesNegativas: Int? = null
)
