package com.librompaz.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.librompaz.app.R
import com.librompaz.app.model.Perfil
import com.librompaz.app.ui.components.textoError
import com.librompaz.app.viewmodel.LibroMpazViewModel

/**
 * Módulo "Autenticación y alcance por rol": inicio de sesión contra la tabla "usuarios" de Room.
 * Según el perfil (Docente, UTP, Inspectoría o Dirección), AppNavigation lleva a la pantalla
 * principal de cada uno. Regla de negocio: no existe acceso para estudiantes ni apoderados.
 *
 * El estado del formulario (correo, clave y errores) vive en el ViewModel (LoginUiState);
 * esta pantalla solo lo dibuja y avisa los cambios.
 */
@Composable
fun LoginScreen(
    viewModel: LibroMpazViewModel,
    onIngresar: (Perfil) -> Unit
) {
    val estado by viewModel.login.collectAsState()
    val datosListos by viewModel.datosListos.collectAsState()
    // rememberSaveable: se mantiene aunque se gire la pantalla.
    var claveVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_app),
                contentDescription = "Logo Libro MPAZ",
                modifier = Modifier.size(110.dp)
            )
            Text("Libro MPAZ", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Libro digital de clases · Escuela Escritora Marcela Paz",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = estado.usuario,
                onValueChange = viewModel::onUsuarioChange,
                label = { Text("Correo institucional") },
                isError = estado.errores.usuario != null,
                supportingText = textoError(estado.errores.usuario),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = estado.clave,
                onValueChange = viewModel::onClaveChange,
                label = { Text("Clave") },
                singleLine = true,
                isError = estado.errores.clave != null,
                supportingText = textoError(estado.errores.clave),
                visualTransformation = if (claveVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { claveVisible = !claveVisible }) {
                        Icon(
                            imageVector = if (claveVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (claveVisible) "Ocultar clave" else "Mostrar clave"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Error general (credenciales incorrectas), con animación de aparición.
            AnimatedVisibility(visible = estado.errores.general != null, enter = fadeIn(), exit = fadeOut()) {
                Text(estado.errores.general.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = datosListos && !estado.ingresando,
                onClick = { viewModel.ingresar(onIngresar) }
            ) {
                if (!datosListos || estado.ingresando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Ingresar")
                }
            }

            Text(
                "Acceso exclusivo para el personal del establecimiento.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
