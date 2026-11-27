package com.librompaz.app.ui.screen.comun

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.librompaz.app.model.EstadoAsistencia
import com.librompaz.app.ui.components.AvisoExito
import com.librompaz.app.ui.components.BarraSuperior
import com.librompaz.app.ui.components.CampoFecha
import com.librompaz.app.util.hoy
import com.librompaz.app.viewmodel.FeriadosViewModel
import com.librompaz.app.viewmodel.LibroMpazViewModel

/**
 * Módulo "Asistencia": registro diario del curso con cuatro estados (P presente, A ausente,
 * T atrasado, J justificado). Pensada para resolverse en menos de un minuto (RF-04): todos
 * parten "presente" y solo se toca a quienes no lo están. La usan el Docente (su curso) e
 * Inspectoría (cualquier curso, RF-05).
 *
 * Al cambiar la fecha se carga lo ya registrado ese día (se puede corregir). Pide confirmación
 * antes de guardar si va a sobrescribir un registro (RNF-02).
 * Supuesto: se permite corregir días anteriores, y queda auditado.
 *
 * Si la fecha es feriado (API externa de feriados) se muestra un aviso; no bloquea el registro.
 */
@Composable
fun AsistenciaScreen(
    navController: NavHostController,
    viewModel: LibroMpazViewModel,
    feriadosViewModel: FeriadosViewModel
) {
    val cursoId by viewModel.cursoSeleccionadoId.collectAsState()
    val cursos by viewModel.cursos.collectAsState()
    val todos by viewModel.estudiantes.collectAsState()
    val curso = remember(cursos, cursoId) { viewModel.cursoPorId(cursoId) }
    val estudiantes = remember(todos, cursoId) { viewModel.estudiantesDelCurso(cursoId) }

    var fecha by rememberSaveable { mutableStateOf(hoy()) }
    var errorFecha by remember { mutableStateOf<String?>(null) }
    var estados by remember { mutableStateOf(mapOf<String, EstadoAsistencia>()) }
    var yaRegistrada by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var confirmando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    val estadoFeriados by feriadosViewModel.uiState.collectAsState()
    val feriado = remember(estadoFeriados, fecha) { feriadosViewModel.feriadoEn(fecha) }

    // Cada vez que cambia el curso o la fecha, se lee desde Room lo ya registrado.
    LaunchedEffect(cursoId, fecha) {
        cargando = true
        val guardada = viewModel.asistenciaGuardada(cursoId.orEmpty(), fecha)
        estados = guardada
        yaRegistrada = guardada.isNotEmpty()
        cargando = false
    }

    // Los estudiantes que no se tocaron quedan como PRESENTE.
    val completo = estudiantes.associate { it.id to (estados[it.id] ?: EstadoAsistencia.PRESENTE) }

    Scaffold(topBar = { BarraSuperior("Asistencia · ${curso?.nombre.orEmpty()}", navController) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            CampoFecha(
                etiqueta = "Fecha",
                fecha = fecha,
                onFechaChange = {
                    fecha = it
                    errorFecha = null
                },
                error = errorFecha,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            if (cargando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Text(
                if (yaRegistrada) "Ya hay asistencia registrada este día: puedes corregirla."
                else "Sin registro para este día: todos parten como presentes.",
                style = MaterialTheme.typography.bodySmall,
                color = if (yaRegistrada) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            feriado?.let {
                Text(
                    "Este día es feriado: ${it.localName}. Normalmente no hay clases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Resumen del día (se actualiza al tocar cada estado).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    EstadoAsistencia.entries.joinToString(" · ") { estado ->
                        "${estado.abreviatura} ${completo.values.count { it == estado }}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { estados = estudiantes.associate { it.id to EstadoAsistencia.PRESENTE } }) {
                    Text("Todos presentes")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(estudiantes, key = { it.id }) { estudiante ->
                    val actual = completo[estudiante.id] ?: EstadoAsistencia.PRESENTE
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(estudiante.nombreFicticio, modifier = Modifier.weight(1f))
                        EstadoAsistencia.entries.forEach { opcion ->
                            BotonEstado(
                                estado = opcion,
                                seleccionado = actual == opcion,
                                onClick = { estados = estados + (estudiante.id to opcion) }
                            )
                        }
                    }
                }
            }

            AvisoExito(mensaje = mensaje, onOcultar = { mensaje = null })

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = estudiantes.isNotEmpty() && !cargando,
                onClick = {
                    // La validación está en el ViewModel; aquí solo se muestra el resultado.
                    errorFecha = viewModel.validarFechaAsistencia(fecha)
                    if (errorFecha == null) confirmando = true
                }
            ) { Text("Guardar asistencia") }
        }
    }

    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text(if (yaRegistrada) "¿Sobrescribir asistencia?" else "¿Guardar asistencia?") },
            text = {
                Text(
                    if (yaRegistrada) "Ya existe un registro para el $fecha. Se va a reemplazar y el cambio quedará en la auditoría."
                    else "Se registrará la asistencia del $fecha para ${estudiantes.size} estudiantes."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    viewModel.guardarAsistencia(cursoId.orEmpty(), fecha, completo) {
                        yaRegistrada = true
                        mensaje = "Asistencia del $fecha guardada."
                    }
                }) { Text(if (yaRegistrada) "Sobrescribir" else "Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmando = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Color de cada estado: verde presente, rojo ausente, naranjo atrasado, azul justificado. */
@Composable
private fun colorDe(estado: EstadoAsistencia): Color = when (estado) {
    EstadoAsistencia.PRESENTE -> MaterialTheme.colorScheme.secondary
    EstadoAsistencia.AUSENTE -> MaterialTheme.colorScheme.error
    EstadoAsistencia.ATRASADO -> MaterialTheme.colorScheme.tertiary
    EstadoAsistencia.JUSTIFICADO -> MaterialTheme.colorScheme.primary
}

/** Botón compacto de un estado: al elegirlo, el relleno cambia de color con una animación. */
@Composable
private fun BotonEstado(estado: EstadoAsistencia, seleccionado: Boolean, onClick: () -> Unit) {
    val color = colorDe(estado)
    val fondo by animateColorAsState(if (seleccionado) color else Color.Transparent, label = "fondoEstado")
    val texto by animateColorAsState(if (seleccionado) Color.White else color, label = "textoEstado")

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(40.dp),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = fondo, contentColor = texto)
    ) { Text(estado.abreviatura) }
}
