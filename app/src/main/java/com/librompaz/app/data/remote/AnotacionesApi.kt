package com.librompaz.app.data.remote

import com.librompaz.app.model.Anotacion
import com.librompaz.app.model.TipoAnotacion
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints del microservicio PROPIO ms-anotaciones (Spring Boot, puerto 8082). */
interface AnotacionesApi {

    @GET("api/anotaciones")
    suspend fun anotaciones(): List<Anotacion>

    /** POST: el servidor pone la fecha y hora de la firma y responde 201 Created. */
    @POST("api/anotaciones")
    suspend fun crear(@Body anotacion: AnotacionDto): Anotacion

    /** PUT: corrige tipo y descripción. Responde 403 si quien pide no es quien firmó. */
    @PUT("api/anotaciones/{id}")
    suspend fun actualizar(@Path("id") id: String, @Body anotacion: AnotacionDto): Anotacion

    /** DELETE: responde 204 No Content, o 403 si quien pide no es quien firmó. */
    @DELETE("api/anotaciones/{id}")
    suspend fun eliminar(@Path("id") id: String, @Query("solicitante") solicitante: String): Response<Unit>
}

/** Cuerpo de POST y PUT /api/anotaciones (sin fechaHora: la pone el servidor). */
data class AnotacionDto(
    val id: String,
    val estudianteId: String,
    val tipo: TipoAnotacion,
    val descripcion: String,
    val firmadoPor: String
)
