package com.librompaz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librompaz.app.data.remote.Feriado
import com.librompaz.app.data.repository.FeriadosRepository
import com.librompaz.app.util.hoy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Los tres estados posibles de la consulta a la API externa. */
sealed interface FeriadosUiState {
    data object Cargando : FeriadosUiState
    data class Exito(val feriados: List<Feriado>) : FeriadosUiState
    data class Error(val mensaje: String) : FeriadosUiState
}

/**
 * ViewModel de la API EXTERNA de feriados (Nager.Date). Está separado de LibroMpazViewModel
 * porque es información de solo lectura que no interfiere con los datos del colegio: se muestra
 * en "Mis cursos", en el panel de Inspectoría y como aviso al pasar asistencia en un feriado.
 *
 * @param fechaHoy fecha actual AAAA-MM-DD (en las pruebas se pasa una fija).
 */
class FeriadosViewModel(
    private val repository: FeriadosRepository = FeriadosRepository(),
    private val fechaHoy: () -> String = { hoy() }
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeriadosUiState>(FeriadosUiState.Cargando)
    val uiState: StateFlow<FeriadosUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    /** Consulta la API. Se puede volver a llamar con el botón "Reintentar". */
    fun cargar() {
        viewModelScope.launch {
            _uiState.value = FeriadosUiState.Cargando
            _uiState.value = try {
                FeriadosUiState.Exito(repository.feriadosDesde(fechaHoy().take(4).toInt()))
            } catch (e: Exception) {
                FeriadosUiState.Error(mensajeDeError(e))
            }
        }
    }

    /** Los próximos feriados desde hoy (incluido). Vacío si aún no hay datos. */
    fun proximos(cantidad: Int = 3): List<Feriado> {
        val hoy = fechaHoy()
        return feriados().filter { it.date >= hoy }.take(cantidad)
    }

    /** El feriado que cae en la fecha indicada, o null si es día hábil. */
    fun feriadoEn(fecha: String): Feriado? = feriados().find { it.date == fecha }

    private fun feriados(): List<Feriado> = (_uiState.value as? FeriadosUiState.Exito)?.feriados.orEmpty()
}
