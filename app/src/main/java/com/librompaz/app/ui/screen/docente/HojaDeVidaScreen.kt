package com.librompaz.app.ui.screen.docente

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.librompaz.app.model.Estudiante
import com.librompaz.app.model.TipoAnotacion
import com.librompaz.app.ui.components.AvisoExito
import com.librompaz.app.ui.components.BarraSuperior
import com.librompaz.app.ui.components.SelectorDesplegable
import com.librompaz.app.ui.components.textoError
import com.librompaz.app.viewmodel.ErroresAnotacion
import com.librompaz.app.viewmodel.LibroMpazViewModel

/**
 * Módulo "Hoja de vida" (docente, RF-10): registrar anotaciones positivas, negativas u
 * observaciones conductuales de un estudiante, y ver su historial. Cada anotación queda con
 * marca temporal y "firma" del docente responsable (usuario + fecha + hora), después de una
 * confirmación explícita. Una vez registrada no se puede modificar.
 */
@Composable
fun HojaDeVidaScreen(navController: NavHostController, viewModel: LibroMpazViewModel) {
    val cursoId by viewModel.cursoSeleccionadoId.collectAsState()
    val usuario by viewModel.usuarioActivo.collectAsState()
    val todas by viewModel.anotaciones.collectAsState()
    val todos by viewModel.estudiantes.collectAsState()
    val estudiantes = remember(todos, cursoId) { viewModel.estudiantesDelCurso(cursoId) }

    var estudiante by remember { mutableStateOf<Estudiante?>(null) }
    var tipo by remember { mutableStateOf<TipoAnotacion?>(null) }
    var descripcion by remember { mutableStateOf("") }
    var errores by remember { mutableStateOf(ErroresAnotacion()) }
    var confirmando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    val historial = remember(todas, estudiante) { viewModel.anotacionesDe(estudiante?.id) }

    Scaffold(topBar = { BarraSuperior("Hoja de vida", navController) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectorDesplegable(
                etiqueta = "Estudiante",
                opciones = estudiantes,
                seleccion = estudiante,
                textoDe = { it.nombreFicticio },
                onSeleccion = {
                    estudiante = it
                    errores = errores.copy(estudiante = null)
                },
                textoVacio = "Elige un estudiante",
                error = errores.estudiante
            )
            SelectorDesplegable(
                etiqueta = "Tipo de anotación",
                opciones = TipoAnotacion.entries,
                seleccion = tipo,
                textoDe = { it.etiqueta },
                onSeleccion = {
                    tipo = it
                    errores = errores.copy(tipo = null)
                },
                error = errores.tipo
            )
            OutlinedTextField(
                value = descripcion,
                onValueChange = {
                    descripcion = it
                    errores = errores.copy(descripcion = null)
                },
                label = { Text("Descripción") },
                minLines = 3,
                isError = errores.descripcion != null,
                supportingText = textoError(errores.descripcion) ?: { Text("${descripcion.trim().length}/300") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Firma: ${usuario?.nombre.orEmpty()} · la fecha y hora se registran automáticamente.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    errores = viewModel.validarNuevaAnotacion(estudiante, tipo, descripcion)
                    if (!errores.hayErrores) confirmando = true
                }
            ) { Text("Registrar anotación") }

            AvisoExito(mensaje = mensaje, onOcultar = { mensaje = null })

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                if (estudiante == null) "Elige un estudiante para ver su historial"
                else "Historial de ${estudiante?.nombreFicticio} (${historial.size})",
                style = MaterialTheme.typography.titleSmall
            )
            historial.forEach { anotacion ->
                val color = when (anotacion.tipo) {
                    TipoAnotacion.POSITIVA -> MaterialTheme.colorScheme.secondaryContainer
                    TipoAnotacion.NEGATIVA -> MaterialTheme.colorScheme.errorContainer
                    TipoAnotacion.OBSERVACION -> MaterialTheme.colorScheme.surfaceVariant
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(anotacion.tipo.etiqueta, style = MaterialTheme.typography.titleSmall)
                        Text(anotacion.descripcion, style = MaterialTheme.typography.bodySmall)
                        Text("${anotacion.fechaHora} · Firmó: ${anotacion.firmadoPor}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text("¿Registrar anotación?") },
            text = { Text("Quedará firmada con tu usuario, fecha y hora, y no se podrá modificar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    val elegido = estudiante
                    val tipoElegido = tipo
                    if (elegido != null && tipoElegido != null) {
                        viewModel.registrarAnotacion(elegido, tipoElegido, descripcion) {
                            descripcion = ""
                            tipo = null
                            mensaje = "Anotación registrada y firmada."
                        }
                    }
                }) { Text("Registrar y firmar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmando = false }) { Text("Cancelar") }
            }
        )
    }
}
