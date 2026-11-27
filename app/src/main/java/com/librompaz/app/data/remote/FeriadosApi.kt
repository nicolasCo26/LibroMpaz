package com.librompaz.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * API EXTERNA pública Nager.Date (https://date.nager.at): feriados oficiales de Chile.
 * No requiere clave. Ejemplo: GET https://date.nager.at/api/v3/PublicHolidays/2026/CL
 * Solo se lee: no se guarda nada en Room ni en los microservicios propios.
 */
interface FeriadosApi {

    @GET("api/v3/PublicHolidays/{anio}/{pais}")
    suspend fun feriados(@Path("anio") anio: Int, @Path("pais") pais: String = "CL"): List<Feriado>
}

/** Solo los campos del JSON que usa la app (Gson ignora el resto). */
data class Feriado(
    val date: String,        // AAAA-MM-DD
    val localName: String    // nombre en español, por ejemplo "Fiestas Patrias"
)
