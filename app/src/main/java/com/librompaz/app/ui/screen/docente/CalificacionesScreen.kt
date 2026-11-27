package com.librompaz.app.ui.screen.docente

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.librompaz.app.model.Evaluacion
import com.librompaz.app.ui.components.AvisoExito
import com.librompaz.app.ui.components.AvisoServidor
import com.librompaz.app.ui.components.BarraSuperior
import com.librompaz.app.ui.components.CampoFecha
import com.librompaz.app.ui.components.SelectorDesplegable
import com.librompaz.app.ui.components.textoError
import com.librompaz.app.util.hoy
import com.librompaz.app.viewmodel.ErroresEvaluacion
import com.librompaz.app.viewmodel.LibroMpazViewModel

/**
 * Módulo "Calificaciones" (docente, RF-08): crear un instrumento de evaluación y registrar las
 * notas de cada estudiante (escala chilena 1,0 a 7,0). Cada nota se valida mientras se escribe
 * (el error aparece bajo el campo). Pide confirmación antes de sobrescribir notas o de eliminar
 * una evaluación (RNF-02).
 *
 * Los datos viven en el microservicio ms-calificaciones: crear (POST), corregir (PUT), eliminar
 * (DELETE) y guardar notas (PUT) pasan por él. Al abrir la pantalla se sincroniza (GET).
 */
@Composable
fun CalificacionesScreen(navController: NavHostController, viewModel: LibroMpazViewModel) {
    val cursoId by viewModel.cursoSeleccionadoId.collectAsState()
    val asignaturaId by viewModel.asignaturaSeleccionadaId.collectAsState()
    val todas by viewModel.evaluaciones.collectAsState()
    val todasLasNotas by viewModel.calificaciones.collectAsState()
    val todos by viewModel.estudiantes.collectAsState()
    val evaluaciones = remember(todas, cursoId, asignaturaId) { viewModel.evaluacionesDe(cursoId, asignaturaId) }
    val estudiantes = remember(todos, cursoId) { viewModel.estudiantesDelCurso(cursoId) }

    // Se guarda el id elegido: así, al corregir o sincronizar, se muestra la versión nueva.
    var evaluacionId by rememberSaveable { mutableStateOf<String?>(null) }
    val evaluacion = evaluaciones.find { it.id == evaluacionId }
    var nuevoNombre by rememberSaveable { mutableStateOf("") }
    var nuevaFecha by rememberSaveable { mutableStateOf(hoy()) }
    var erroresEvaluacion by remember { mutableStateOf(ErroresEvaluacion()) }

    // Notas escritas (idEstudiante -> texto). Parten con lo ya guardado de la evaluación elegida.
    val guardadas = remember(todasLasNotas, evaluacion) { viewModel.calificacionesDe(evaluacion?.id) }
    var notas by remember(evaluacionId, guardadas) {
        mutableStateOf(guardadas.associate { it.estudianteId to it.nota.toString().replace('.', ',') })
    }
    var confirmandoNotas by remember { mutableStateOf(false) }
    var confirmandoEliminar by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // GET al microservicio cada vez que se entra: muestra los cambios hechos desde otro equipo.
    LaunchedEffect(Unit) { viewModel.sincronizar() }

    Scaffold(topBar = { BarraSuperior("Calificaciones", navController) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AvisoServidor(viewModel)
            Text("Nueva evaluación", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = nuevoNombre,
                onValueChange = {
                    nuevoNombre = it
                    erroresEvaluacion = erroresEvaluacion.copy(nombre = null)
                },
                label = { Text("Nombre del instrumento") },
                placeholder = { Text("Ej: Prueba unidad 2") },
                singleLine = true,
                isError = erroresEvaluacion.nombre != null,
                supportingText = textoError(erroresEvaluacion.nombre),
                modifier = Modifier.fillMaxWidth().testTag("campo_nombre_evaluacion")
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoFecha(
                    etiqueta = "Fecha",
                    fecha = nuevaFecha,
                    onFechaChange = {
                        nuevaFecha = it
                        erroresEvaluacion = erroresEvaluacion.copy(fecha = null)
                    },
                    error = erroresEvaluacion.fecha,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    erroresEvaluacion = viewModel.crearEvaluacion(cursoId.orEmpty(), asignaturaId.orEmpty(), nuevoNombre, nuevaFecha) {
                        mensaje = "Evaluación \"${nuevoNombre.trim()}\" creada. Elígela abajo para ingresar notas."
                        nuevoNombre = ""
                    }
                }) { Text("Crear") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectorDesplegable(
                    etiqueta = "Evaluación",
                    opciones = evaluaciones,
                    seleccion = evaluacion,
                    textoDe = { "${it.nombreInstrumento} (${it.fecha})" },
                    onSeleccion = { evaluacionId = it.id },
                    textoVacio = if (evaluaciones.isEmpty()) "Aún no hay evaluaciones" else "Elige una evaluación",
                    modifier = Modifier.weight(1f)
                )
                if (evaluacion != null) {
                    IconButton(onClick = { editando = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar evaluación")
                    }
                    IconButton(onClick = { confirmandoEliminar = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar evaluación", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // La planilla de notas aparece con una animación al elegir una evaluación.
            AnimatedVisibility(
                visible = evaluacion != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Notas ingresadas: ${notas.values.count { it.isNotBlank() }} de ${estudiantes.size}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    estudiantes.forEach { estudiante ->
                        val texto = notas[estudiante.id].orEmpty()
                        val error = viewModel.errorDeNota(texto)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(estudiante.nombreFicticio, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = texto,
                                onValueChange = { notas = notas + (estudiante.id to it) },
                                label = { Text("Nota") },
                                singleLine = true,
                                isError = error != null,
                                supportingText = textoError(error),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(130.dp)
                            )
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        // Se puede guardar si hay al menos una nota escrita y ninguna con error.
                        enabled = notas.values.any { it.isNotBlank() } && notas.values.none { viewModel.errorDeNota(it) != null },
                        onClick = { confirmandoNotas = true }
                    ) { Text("Guardar notas") }
                }
            }

            AvisoExito(mensaje = mensaje, onOcultar = { mensaje = null })
        }
    }

    if (confirmandoNotas) {
        AlertDialog(
            onDismissRequest = { confirmandoNotas = false },
            title = { Text(if (guardadas.isEmpty()) "¿Guardar notas?" else "¿Sobrescribir notas?") },
            text = {
                Text(
                    if (guardadas.isEmpty()) "Se guardarán las notas de \"${evaluacion?.nombreInstrumento}\"."
                    else "Las notas que ya existían para esta evaluación se van a reemplazar. Las que dejaste en blanco se eliminarán."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoNotas = false
                    evaluacion?.let {
                        viewModel.guardarCalificaciones(it, notas) { mensaje = "Notas guardadas correctamente." }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoNotas = false }) { Text("Cancelar") }
            }
        )
    }

    if (confirmandoEliminar) {
        AlertDialog(
            onDismissRequest = { confirmandoEliminar = false },
            title = { Text("¿Eliminar evaluación?") },
            text = { Text("Se eliminará \"${evaluacion?.nombreInstrumento}\" y TODAS sus notas. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoEliminar = false
                    evaluacion?.let {
                        viewModel.eliminarEvaluacion(it) {
                            evaluacionId = null
                            mensaje = "Evaluación eliminada."
                        }
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoEliminar = false }) { Text("Cancelar") }
            }
        )
    }

    if (editando) {
        evaluacion?.let { actual ->
            DialogoEditarEvaluacion(
                evaluacion = actual,
                onGuardar = { nombre, fecha ->
                    viewModel.actualizarEvaluacion(actual, nombre, fecha) {
                        editando = false
                        mensaje = "Evaluación corregida."
                    }
                },
                onCancelar = { editando = false }
            )
        }
    }
}

/** Diálogo para corregir el nombre y la fecha de una evaluación (PUT /api/evaluaciones/{id}). */
@Composable
private fun DialogoEditarEvaluacion(
    evaluacion: Evaluacion,
    onGuardar: (nombre: String, fecha: String) -> ErroresEvaluacion,
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf(evaluacion.nombreInstrumento) }
    var fecha by remember { mutableStateOf(evaluacion.fecha) }
    var errores by remember { mutableStateOf(ErroresEvaluacion()) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Editar evaluación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        errores = errores.copy(nombre = null)
                    },
                    label = { Text("Nombre del instrumento") },
                    singleLine = true,
                    isError = errores.nombre != null,
                    supportingText = textoError(errores.nombre),
                    modifier = Modifier.fillMaxWidth()
                )
                CampoFecha(
                    etiqueta = "Fecha",
                    fecha = fecha,
                    onFechaChange = {
                        fecha = it
                        errores = errores.copy(fecha = null)
                    },
                    error = errores.fecha
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { errores = onGuardar(nombre, fecha) }) { Text("Guardar cambios") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
