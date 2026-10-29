package com.librompaz.app.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val FORMATO_FECHA = "yyyy-MM-dd"

/** Fecha de hoy en formato AAAA-MM-DD (para dejarla como valor inicial en los formularios). */
fun hoy(): String = SimpleDateFormat(FORMATO_FECHA, Locale.getDefault()).format(Date())

/** Fecha y hora actuales (AAAA-MM-DD HH:mm:ss): marca temporal de la auditoría y de las firmas. */
fun ahora(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

/**
 * El DatePicker de Material 3 entrega la fecha elegida como milisegundos a medianoche en UTC.
 * Por eso se formatea en UTC (si no, en Chile podría aparecer el día anterior).
 */
fun fechaDesdeMillis(millis: Long): String = formatoUtc().format(Date(millis))

/** Lo inverso: AAAA-MM-DD a milisegundos UTC. Devuelve null si el texto no es una fecha válida. */
fun millisDesdeFecha(fecha: String): Long? =
    try {
        formatoUtc().parse(fecha)?.time
    } catch (e: ParseException) {
        null
    }

private fun formatoUtc() = SimpleDateFormat(FORMATO_FECHA, Locale.getDefault()).apply {
    timeZone = TimeZone.getTimeZone("UTC")
    isLenient = false   // no acepta fechas como 2026-02-31
}
