package com.librompaz.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.librompaz.app.viewmodel.LibroMpazViewModel

/**
 * Estado de la conexión con los microservicios, para las pantallas que usan datos remotos:
 *  - Sin conexión: aviso con botón "Reintentar" (se siguen viendo los datos de la copia local).
 *  - Error de una operación (400, 403, sin red...): mensaje rojo que se oculta solo.
 */
@Composable
fun AvisoServidor(viewModel: LibroMpazViewModel, modifier: Modifier = Modifier) {
    val conectado by viewModel.conectado.collectAsState()
    val sincronizando by viewModel.sincronizando.collectAsState()
    val error by viewModel.errorServidor.collectAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(visible = conectado == false) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth().testTag("aviso_sin_conexion")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null)
                    Text(
                        "Sin conexión con el servidor: se muestra la última copia guardada.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (sincronizando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { viewModel.sincronizar() }) { Text("Reintentar") }
                    }
                }
            }
        }
        AvisoExito(mensaje = error, onOcultar = { viewModel.limpiarErrorServidor() }, esError = true)
    }
}
