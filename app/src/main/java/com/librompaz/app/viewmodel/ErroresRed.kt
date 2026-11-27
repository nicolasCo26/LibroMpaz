package com.librompaz.app.viewmodel

import java.io.IOException
import retrofit2.HttpException

/**
 * Traduce una excepción de Retrofit a un mensaje para el usuario.
 *  - IOException: no hubo respuesta (sin red o el microservicio está apagado).
 *  - HttpException: el servidor respondió con un código de error (4xx o 5xx).
 */
fun mensajeDeError(e: Throwable): String = when (e) {
    is IOException -> "Sin conexión con el servidor. Revisa que los microservicios estén encendidos."
    is HttpException -> when (e.code()) {
        400 -> "El servidor rechazó los datos (400). Revisa el formulario."
        403 -> "No autorizado (403): solo quien firmó el registro puede modificarlo."
        404 -> "El registro ya no existe en el servidor (404)."
        409 -> "El registro ya existe en el servidor (409)."
        else -> "El servidor respondió con error ${e.code()}."
    }
    else -> "Error inesperado: ${e.message ?: e::class.simpleName}"
}
