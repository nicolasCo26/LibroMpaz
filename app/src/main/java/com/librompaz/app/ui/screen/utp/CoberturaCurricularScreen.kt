package com.librompaz.app.ui.screen.utp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.librompaz.app.model.Asignatura
import com.librompaz.app.model.Curso
import com.librompaz.app.ui.components.BarraSuperior
import com.librompaz.app.ui.components.SelectorDesplegable
import com.librompaz.app.viewmodel.LibroMpazViewModel

/**
 * Módulo "Cobertura curricular" (UTP, RF-07): por curso y asignatura, qué OA ya se trabajaron
 * (según las clases registradas por los docentes) y cuáles faltan.
 */
@Composable
fun CoberturaCurricularScreen(navController: NavHostController, viewModel: LibroMpazViewModel) {
    val cursos by viewModel.cursos.collectAsState()
    val asignaturas by viewModel.asignaturas.collectAsState()
    val sesiones by viewModel.sesiones.collectAsState()
    val todosLosOa by viewModel.objetivos.collectAsState()

    var curso by remember { mutableStateOf<Curso?>(null) }
    var asignatura by remember { mutableStateOf<Asignatura?>(null) }
    val cursoElegido = curso ?: cursos.firstOrNull()
    val asignaturaElegida = asignatura ?: asignaturas.firstOrNull()

    val objetivos = remember(todosLosOa, asignaturaElegida) { viewModel.objetivosDe(asignaturaElegida?.id) }
    val trabajados = remember(sesiones, cursoElegido, asignaturaElegida) {
        viewModel.oasTrabajados(cursoElegido?.id, asignaturaElegida?.id)
    }
    val avance = if (objetivos.isEmpty()) 0f else trabajados.size.toFloat() / objetivos.size
    // La barra de avance se mueve con una animación al cambiar de curso o asignatura.
    val avanceAnimado by animateFloatAsState(targetValue = avance, animationSpec = tween(600), label = "avance")

    Scaffold(topBar = { BarraSuperior("Cobertura curricular", navController) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectorDesplegable(
                etiqueta = "Curso",
                opciones = cursos,
                seleccion = cursoElegido,
                textoDe = { it.nombre },
                onSeleccion = { curso = it }
            )
            SelectorDesplegable(
                etiqueta = "Asignatura",
                opciones = asignaturas,
                seleccion = asignaturaElegida,
                textoDe = { it.nombre },
                onSeleccion = { asignatura = it }
            )

            Text(
                "OA trabajados: ${trabajados.size} de ${objetivos.size} (${Math.round(avance * 100)} %)",
                style = MaterialTheme.typography.titleMedium
            )
            LinearProgressIndicator(
                progress = { avanceAnimado },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )

            objetivos.forEach { oa ->
                val hecho = oa.id in trabajados
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hecho) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (hecho) "Trabajado" else "Pendiente",
                            tint = if (hecho) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(oa.codigo, style = MaterialTheme.typography.titleSmall)
                            Text(oa.descripcion, style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (hecho) "Trabajado" else "Pendiente",
                                color = if (hecho) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
