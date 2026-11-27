package com.librompaz.app.data.repository

import com.librompaz.app.data.remote.Feriado
import com.librompaz.app.data.remote.FeriadosApi
import com.librompaz.app.data.remote.RetrofitClient

/**
 * Acceso a la API EXTERNA de feriados. A diferencia de LibroMpazRepository, no escribe en Room
 * ni en los microservicios: solo consulta y devuelve los datos tal como llegan.
 */
class FeriadosRepository(private val api: FeriadosApi = RetrofitClient.feriadosApi) {

    /** Feriados del año indicado y del siguiente, ordenados por fecha (sirve a fin de año). */
    suspend fun feriadosDesde(anio: Int): List<Feriado> =
        (api.feriados(anio) + api.feriados(anio + 1)).sortedBy { it.date }
}
