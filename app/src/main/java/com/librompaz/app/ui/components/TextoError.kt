package com.librompaz.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Para el parámetro supportingText de los campos: muestra el error debajo del campo, o nada si
 * el error es null. Se usa junto con isError = error != null.
 */
fun textoError(error: String?): (@Composable () -> Unit)? =
    if (error != null) {
        { Text(error) }
    } else {
        null
    }
