package com.librompaz.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
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
import com.librompaz.app.viewmodel.FeriadosUiState
import com.librompaz.app.viewmodel.FeriadosViewModel

/**
 * Próximos feriados de Chile (API EXTERNA Nager.Date). Muestra un estado distinto para
 * Cargando, Éxito y Error, igual que el patrón UiState del ViewModel.
 */
@Composable
fun TarjetaFeriados(viewModel: FeriadosViewModel, modifier: Modifier = Modifier) {
    val estado by viewModel.uiState.collectAsState()

    Card(modifier = modifier.fillMaxWidth().animateContentSize().testTag("tarjeta_feriados")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Event, contentDescription = null)
                Text("Próximos feriados (sin clases)", style = MaterialTheme.typography.titleSmall)
            }
            when (val e = estado) {
                FeriadosUiState.Cargando -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                is FeriadosUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.mensaje, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.cargar() }) { Text("Reintentar") }
                }
                is FeriadosUiState.Exito -> {
                    val proximos = viewModel.proximos()
                    if (proximos.isEmpty()) Text("No hay feriados próximos.", style = MaterialTheme.typography.bodySmall)
                    proximos.forEach { feriado ->
                        Text("${feriado.date} · ${feriado.localName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Fuente: date.nager.at (API pública)", style = MaterialTheme.typography.labelSmall)
        }
    }
}
