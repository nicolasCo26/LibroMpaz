package com.librompaz.app.ui.screen.direccion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.librompaz.app.navigation.Rutas
import com.librompaz.app.ui.components.AvisoExito
import com.librompaz.app.ui.components.AvisoServidor
import com.librompaz.app.ui.components.BarraSuperior
import com.librompaz.app.util.hoy
import com.librompaz.app.viewmodel.LibroMpazViewModel

/**
 * Módulo "Panel administrativo" (Dirección, RF-12): estado global del colegio con métricas
 * consolidadas por curso, acceso al registro de auditoría y exportación del reporte.
 *
 * RECURSO NATIVO: "Exportar reporte (CSV)" abre el selector de archivos del sistema
 * (ActivityResultContracts.CreateDocument). El usuario elige dónde guardar y el ViewModel
 * escribe el archivo (ver recursosnativos/ExportadorCsv.kt). No requiere permisos.
 */
@Composable
fun PanelDireccionScreen(
    navController: NavHostController,
    viewModel: LibroMpazViewModel,
    onCerrarSesion: () -> Unit
) {
    val usuario by viewModel.usuarioActivo.collectAsState()
    val resumen by viewModel.resumenCursos.collectAsState()
    var mensaje by remember { mutableStateOf<String?>(null) }
    var exportoBien by remember { mutableStateOf(true) }

    // Lanzador del selector nativo "Guardar como". Devuelve el Uri elegido, o null si se cancela.
    val selectorArchivo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) {
            exportoBien = false
            mensaje = "Exportación cancelada."
        } else {
            viewModel.exportarReporte(uri) { ok ->
                exportoBien = ok
                mensaje = if (ok) "Reporte exportado correctamente." else "No se pudo escribir el archivo."
            }
        }
    }

    // Trae de los microservicios las notas y anotaciones más recientes.
    LaunchedEffect(Unit) { viewModel.sincronizar() }

    Scaffold(topBar = { BarraSuperior("Panel Dirección", navController, onCerrarSesion) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AvisoServidor(viewModel)
            Text("Hola, ${usuario?.nombre.orEmpty()}", style = MaterialTheme.typography.titleLarge)
            Text("Estado global por curso", style = MaterialTheme.typography.titleSmall)

            resumen.forEach { fila ->
                val curso = viewModel.cursoPorId(fila.cursoId)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(curso?.nombre.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Metrica("Asistencia", fila.porcentajeAsistencia?.let { "$it %" })
                        Metrica("Clases registradas", fila.sesionesRegistradas?.toString())
                        Metrica("Notas registradas", fila.calificacionesRegistradas?.toString())
                        Metrica("Anotaciones negativas", fila.anotacionesNegativas?.toString())
                    }
                }
            }

            AvisoExito(mensaje = mensaje, onOcultar = { mensaje = null }, esError = !exportoBien)

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { selectorArchivo.launch("reporte_libro_mpaz_${hoy()}.csv") }
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(ButtonDefaults.IconSize))
                Text("Exportar reporte (CSV)")
            }

            Button(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(Rutas.DIR_AUDITORIA) }) {
                Text("Registro de auditoría")
            }
        }
    }
}

@Composable
private fun Metrica(nombre: String, valor: String?) {
    Row {
        Text("$nombre: ", style = MaterialTheme.typography.bodyMedium)
        Text(valor ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
