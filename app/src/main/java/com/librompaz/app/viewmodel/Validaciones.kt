package com.librompaz.app.viewmodel

import com.librompaz.app.util.millisDesdeFecha

// =====================================================================================
// Reglas de validación de todos los formularios, como funciones puras (sin Android): se
// pueden probar con JUnit sin emulador (ver app/src/test/.../ValidacionesTest.kt).
// Cada data class de errores usa null para decir "este campo está correcto".
// =====================================================================================

const val NOTA_MINIMA = 1.0
const val NOTA_MAXIMA = 7.0

private val FORMATO_CORREO = Regex("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")
private val FORMATO_FECHA = Regex("^\\d{4}-\\d{2}-\\d{2}$")

// Escala chilena: un dígito entero y, opcionalmente, un decimal con coma o punto (5 / 5,5 / 5.5).
private val FORMATO_NOTA = Regex("^\\d([.,]\\d)?$")

// ---------- Login ----------

data class ErroresLogin(
    val usuario: String? = null,
    val clave: String? = null,
    val general: String? = null
) {
    val hayErrores: Boolean get() = listOf(usuario, clave, general).any { it != null }
}

fun validarLogin(usuario: String, clave: String): ErroresLogin = ErroresLogin(
    usuario = when {
        usuario.isBlank() -> "Ingresa tu correo institucional"
        !FORMATO_CORREO.matches(usuario.trim()) -> "El correo no tiene un formato válido"
        else -> null
    },
    clave = when {
        clave.isBlank() -> "Ingresa tu clave"
        clave.length < 6 -> "La clave tiene al menos 6 caracteres"
        else -> null
    }
)

// ---------- Fechas (asistencia, clases, evaluaciones) ----------

/** Fecha obligatoria, con formato AAAA-MM-DD, existente y no futura. hoy también va en AAAA-MM-DD. */
fun validarFecha(fecha: String, hoy: String): String? = when {
    fecha.isBlank() -> "La fecha es obligatoria"
    !FORMATO_FECHA.matches(fecha) || millisDesdeFecha(fecha) == null -> "Usa el formato AAAA-MM-DD"
    fecha > hoy -> "La fecha no puede ser futura"   // en AAAA-MM-DD el orden de texto = orden de fecha
    else -> null
}

// ---------- Calificaciones ----------

/** Convierte la nota escrita ("5,5" o "5.5") a número. */
fun parsearNota(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()

/** Mensaje de error de una nota escrita, o null si es válida. Una nota en blanco no es error (falta). */
fun validarNota(texto: String): String? {
    val limpio = texto.trim()
    if (limpio.isEmpty()) return null
    val nota = parsearNota(limpio)
    return when {
        nota == null -> "Debe ser un número"
        !FORMATO_NOTA.matches(limpio) -> "Usa un decimal (ej: 5,5)"
        nota < NOTA_MINIMA || nota > NOTA_MAXIMA -> "Va de 1,0 a 7,0"
        else -> null
    }
}

data class ErroresEvaluacion(
    val nombre: String? = null,
    val fecha: String? = null
) {
    val hayErrores: Boolean get() = listOf(nombre, fecha).any { it != null }
}

fun validarEvaluacion(nombre: String, fecha: String, hoy: String): ErroresEvaluacion = ErroresEvaluacion(
    nombre = when {
        nombre.isBlank() -> "El nombre del instrumento es obligatorio"
        nombre.trim().length < 3 -> "Mínimo 3 caracteres"
        nombre.trim().length > 60 -> "Máximo 60 caracteres"
        else -> null
    },
    fecha = validarFecha(fecha, hoy)
)

// ---------- Registro de clase (OA y contenidos) ----------

data class ErroresSesion(
    val fecha: String? = null,
    val bloque: String? = null,
    val objetivos: String? = null,
    val contenido: String? = null
) {
    val hayErrores: Boolean get() = listOf(fecha, bloque, objetivos, contenido).any { it != null }
}

fun validarSesion(fecha: String, bloque: String, oaIds: List<String>, contenido: String, hoy: String): ErroresSesion =
    ErroresSesion(
        fecha = validarFecha(fecha, hoy),
        bloque = if (bloque.isBlank()) "Indica el bloque (ej: Bloque 1)" else null,
        objetivos = if (oaIds.isEmpty()) "Selecciona al menos un OA" else null,
        contenido = when {
            contenido.isBlank() -> "Describe los contenidos abordados"
            contenido.trim().length < 10 -> "Mínimo 10 caracteres"
            else -> null
        }
    )

// ---------- Hoja de vida ----------

data class ErroresAnotacion(
    val estudiante: String? = null,
    val tipo: String? = null,
    val descripcion: String? = null
) {
    val hayErrores: Boolean get() = listOf(estudiante, tipo, descripcion).any { it != null }
}

fun validarAnotacion(hayEstudiante: Boolean, hayTipo: Boolean, descripcion: String): ErroresAnotacion = ErroresAnotacion(
    estudiante = if (!hayEstudiante) "Elige un estudiante" else null,
    tipo = if (!hayTipo) "Elige el tipo de anotación" else null,
    descripcion = when {
        descripcion.isBlank() -> "La descripción es obligatoria"
        descripcion.trim().length < 10 -> "Mínimo 10 caracteres"
        descripcion.trim().length > 300 -> "Máximo 300 caracteres"
        else -> null
    }
)
