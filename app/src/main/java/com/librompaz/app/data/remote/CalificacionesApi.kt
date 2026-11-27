package com.librompaz.app.data.remote

import com.librompaz.app.model.Calificacion
import com.librompaz.app.model.Evaluacion
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Endpoints del microservicio PROPIO ms-calificaciones (Spring Boot, puerto 8081).
 * El JSON de Evaluacion y Calificacion tiene los mismos campos que las entidades de Room, por eso
 * se reutilizan como modelo de respuesta.
 */
interface CalificacionesApi {

    @GET("api/evaluaciones")
    suspend fun evaluaciones(): List<Evaluacion>

    /** POST: crea la evaluación. Responde 201 Created con la evaluación guardada. */
    @POST("api/evaluaciones")
    suspend fun crearEvaluacion(@Body evaluacion: Evaluacion): Evaluacion

    /** PUT: reemplaza nombre y fecha. Responde 200 OK. */
    @PUT("api/evaluaciones/{id}")
    suspend fun actualizarEvaluacion(@Path("id") id: String, @Body evaluacion: Evaluacion): Evaluacion

    /** DELETE: borra la evaluación y sus notas. Responde 204 No Content (sin cuerpo). */
    @DELETE("api/evaluaciones/{id}")
    suspend fun eliminarEvaluacion(@Path("id") id: String): Response<Unit>

    /** Todas las notas del colegio (para sincronizar la copia local). */
    @GET("api/calificaciones")
    suspend fun calificaciones(): List<Calificacion>

    /** PUT: reemplaza la planilla completa de la evaluación. */
    @PUT("api/evaluaciones/{id}/calificaciones")
    suspend fun guardarNotas(@Path("id") evaluacionId: String, @Body planilla: PlanillaNotas): List<Calificacion>
}

/** Cuerpo de PUT /api/evaluaciones/{id}/calificaciones. */
data class PlanillaNotas(val registradoPor: String, val notas: List<NotaDto>)

data class NotaDto(val estudianteId: String, val nota: Double)
