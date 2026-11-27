package com.librompaz.app.viewmodel

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.string.shouldContain
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** Cada error de red se traduce a un mensaje entendible para el usuario. */
class ErroresRedTest : StringSpec({

    fun http(codigo: Int) = HttpException(Response.error<Any>(codigo, "{}".toResponseBody()))

    "sin red se indica que se revisen los microservicios" {
        mensajeDeError(IOException("timeout")) shouldContain "Sin conexión"
    }

    "cada código HTTP tiene su mensaje" {
        forAll(
            row(400, "400"),
            row(403, "403"),
            row(404, "404"),
            row(409, "409"),
            row(500, "500")
        ) { codigo, esperado ->
            mensajeDeError(http(codigo)) shouldContain esperado
        }
    }

    "otros errores muestran su descripción" {
        mensajeDeError(IllegalStateException("algo falló")) shouldContain "algo falló"
    }
})
