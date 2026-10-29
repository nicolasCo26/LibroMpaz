package com.librompaz.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Mensaje de resultado animado (Guía 12: AnimatedVisibility). Aparece con un fundido y
 * expansión, y se oculta solo después de 3 segundos llamando a onOcultar().
 *
 * @param mensaje texto a mostrar (null = oculto).
 * @param esError true para mostrarlo con los colores de error.
 */
@Composable
fun AvisoExito(
    mensaje: String?,
    onOcultar: () -> Unit,
    modifier: Modifier = Modifier,
    esError: Boolean = false
) {
    // Se recuerda el último texto para que no desaparezca de golpe durante la animación de salida.
    var ultimo by remember { mutableStateOf("") }
    if (mensaje != null) ultimo = mensaje

    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            delay(3_000)
            onOcultar()
        }
    }

    AnimatedVisibility(
        visible = mensaje != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (esError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (esError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(if (esError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle, contentDescription = null)
                Text(ultimo, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
