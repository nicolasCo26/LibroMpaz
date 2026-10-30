package com.librompaz.app.ui.screen.docente

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.librompaz.app.ui.components.AvisoExito
import com.librompaz.app.ui.components.BarraSuperior
import com.librompaz.app.ui.components.CampoFecha
import com.librompaz.app.ui.components.textoError
import com.librompaz.app.util.hoy
import com.librompaz.app.viewmodel.ErroresSesion
import com.librompaz.app.viewmodel.LibroMpazViewModel

/**
 * Módulo "Cobertura curricular" (docente, RF-06): registrar los OA y contenidos trabajados en
 * una sesión o bloque, y ver las sesiones ya registradas. El ViewModel valida que haya fecha,
 * bloque, al menos un OA y contenido; los errores se muestran junto a cada campo.
 */
@Composable
fun RegistroClaseScreen(navController: NavHostController, viewModel: LibroMpazViewModel) {
    val cursoId by viewModel.cursoSeleccionadoId.collectAsState()
    val asignaturaId by viewModel.asignaturaSeleccionadaId.collectAsState()
    val todas by viewModel.sesiones.collectAsState()
    val todosLosOa by viewModel.objetivos.collectAsState()
    val objetivos = remember(todosLosOa, asignaturaId) { viewModel.objetivosDe(asignaturaId) }
    val registradas = remember(todas, cursoId, asignaturaId) { viewModel.sesionesDe(cursoId, asignaturaId) }

    var fecha by rememberSaveable { mutableStateOf(hoy()) }
    var bloque by rememberSaveable { mutableStateOf("") }
    var seleccionados by remember { mutableStateOf(setOf<String>()) }
    var contenido by rememberSaveable { mutableStateOf("") }
    var errores by remember { mutableStateOf(ErroresSesion()) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BarraSuperior("Registro de clase", navController) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoFecha(
                    etiqueta = "Fecha",
                    fecha = fecha,
                    onFechaChange = {
                        fecha = it
                        errores = errores.copy(fecha = null)
                    },
                    error = errores.fecha,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = bloque,
                    onValueChange = {
                        bloque = it
                        errores = errores.copy(bloque = null)
                    },
                    label = { Text("Bloque") },
                    placeholder = { Text("Bloque 1") },
                    singleLine = true,
                    isError = errores.bloque != null,
                    supportingText = textoError(errores.bloque),
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Objetivos de Aprendizaje trabajados", style = MaterialTheme.typography.titleSmall)
            objetivos.forEach { oa ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = oa.id in seleccionados,
                        onCheckedChange = { marcado ->
                            seleccionados = if (marcado) seleccionados + oa.id else seleccionados - oa.id
                            errores = errores.copy(objetivos = null)
                        }
                    )
                    Column {
                        Text(oa.codigo, style = MaterialTheme.typography.labelMedium)
                        Text(oa.descripcion, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            errores.objetivos?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = contenido,
                onValueChange = {
                    contenido = it
                    errores = errores.copy(contenido = null)
                },
                label = { Text("Contenidos abordados") },
                minLines = 3,
                isError = errores.contenido != null,
                supportingText = textoError(errores.contenido),
                modifier = Modifier.fillMaxWidth()
            )

            AvisoExito(mensaje = mensaje, onOcultar = { mensaje = null })

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    errores = viewModel.guardarSesion(
                        cursoId.orEmpty(), asignaturaId.orEmpty(), fecha, bloque, seleccionados.toList(), contenido
                    ) {
                        mensaje = "Clase del $fecha registrada."
                        bloque = ""
                        seleccionados = emptySet()
                        contenido = ""
                    }
                }
            ) { Text("Guardar clase") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Clases registradas (${registradas.size})", style = MaterialTheme.typography.titleSmall)
            if (registradas.isEmpty()) {
                Text("Todavía no hay clases registradas.", style = MaterialTheme.typography.bodySmall)
            }
            registradas.forEach { sesion ->
                Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${sesion.fecha} · ${sesion.bloque}", style = MaterialTheme.typography.titleSmall)
                        Text(sesion.contenido, style = MaterialTheme.typography.bodySmall)
                        val codigos = sesion.oaIds.mapNotNull { id -> objetivos.find { it.id == id }?.codigo }
                        Text("OA: ${codigos.joinToString(", ")} · Registró: ${sesion.registradoPor}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
