package com.librompaz.app.recursosnativos

import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RECURSO NATIVO: almacenamiento del dispositivo mediante el selector de archivos del sistema
 * (Storage Access Framework, SAF).
 *
 * Cómo funciona:
 *  1. La pantalla (PanelDireccionScreen) abre el selector nativo de Android con
 *     rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")).
 *  2. El usuario elige DÓNDE guardar (Descargas, Drive, etc.) y Android devuelve un Uri.
 *  3. Esta clase escribe el reporte en ese Uri con el ContentResolver.
 *
 * Permisos: NO se necesita declarar ninguno en el AndroidManifest. Con SAF el usuario elige el
 * archivo y Android le da a la app acceso solo a ESE archivo (ni a la galería ni al resto del
 * almacenamiento). Respeta las restricciones del proyecto: no usa GPS, cámara, micrófono ni
 * biometría, y la app no envía nada por su cuenta (sin notificaciones, correos ni mensajes).
 */
object ExportadorCsv {

    /** Escribe el texto en el archivo elegido. Devuelve true si se pudo escribir. */
    suspend fun escribir(context: Context, uri: Uri, contenido: String): Boolean =
        withContext(Dispatchers.IO) {   // escribir en disco no puede hacerse en el hilo principal
            try {
                val salida = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                salida.use {
                    // BOM UTF-8: para que Excel muestre bien los tildes y la "ñ".
                    it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    it.write(contenido.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
}
