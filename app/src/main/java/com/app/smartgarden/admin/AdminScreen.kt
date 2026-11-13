package com.app.smartgarden.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.smartgarden.ui.theme.AccentGreen
import com.app.smartgarden.ui.theme.BackgroundSecondary
import com.app.smartgarden.ui.theme.ErrorRed
import com.app.smartgarden.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    adminViewModel: AdminViewModel = viewModel(),
    onVerPlantas: (String) -> Unit,
    onVerRegistros: () -> Unit,
    onVerSensores: () -> Unit,
    onVolver: () -> Unit
) {
    val uiState = adminViewModel.uiState
    val context = LocalContext.current

    var usuarioParaEditar by remember { mutableStateOf<UsuarioAdmin?>(null) }
    var usuarioParaEliminar by remember { mutableStateOf<UsuarioAdmin?>(null) }
    var mostrarDialogoCrear by remember { mutableStateOf(false) }
    var mostrarDialogoEliminarTodos by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Administración",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarDialogoCrear = true },
                containerColor = AccentGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Agregar usuario"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.cargando) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AccentGreen
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onVerRegistros,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Text("Registros")
                            }
                            Button(
                                onClick = onVerSensores,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Text("Sensores")
                            }
                            Button(
                                onClick = { mostrarDialogoEliminarTodos = true },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                            ) {
                                Text("Borrar todos", color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Usuarios (${uiState.usuarios.size})",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(uiState.usuarios, key = { it.uid }) { usuario ->
                        TarjetaUsuario(
                            usuario = usuario,
                            esYo = usuario.uid == adminViewModel.uidActual,
                            onEditar = { usuarioParaEditar = usuario },
                            onEliminar = { usuarioParaEliminar = usuario },
                            onVerPlantas = { onVerPlantas(usuario.uid) },
                            onToggleActivo = { adminViewModel.cambiarActivo(usuario.uid, !usuario.activo) },
                            onRestablecer = { adminViewModel.enviarRestablecimiento(usuario) }
                        )
                    }
                }
            }

            uiState.mensajeInfo?.let { info ->
                Text(
                    text = info,
                    color = AccentGreen,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            uiState.mensajeError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }

    // Diálogo para Crear Usuario
    if (mostrarDialogoCrear) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var rol by remember { mutableStateOf("usuario") }

        AlertDialog(
            onDismissRequest = { mostrarDialogoCrear = false },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Crear Nuevo Usuario",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña (mín. 8 caracteres, Mayús, mín., núm., símbolo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Rol:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rol == "usuario",
                            onClick = { rol = "usuario" },
                            label = { Text("Usuario") }
                        )
                        FilterChip(
                            selected = rol == "admin",
                            onClick = { rol = "admin" },
                            label = { Text("Administrador") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.crearUsuario(email, password, rol, context) {
                            mostrarDialogoCrear = false
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoCrear = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Diálogo para Editar Usuario
    usuarioParaEditar?.let { usuario ->
        var email by remember { mutableStateOf(usuario.email) }
        var rol by remember { mutableStateOf(usuario.rol) }
        var activo by remember { mutableStateOf(usuario.activo) }

        AlertDialog(
            onDismissRequest = { usuarioParaEditar = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Editar Usuario",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Rol:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rol == "usuario",
                            onClick = { rol = "usuario" },
                            label = { Text("Usuario") }
                        )
                        FilterChip(
                            selected = rol == "admin",
                            onClick = { rol = "admin" },
                            label = { Text("Administrador") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cuenta activa", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = activo,
                            onCheckedChange = { activo = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.editarUsuario(usuario.uid, email, rol, activo)
                        usuarioParaEditar = null
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { usuarioParaEditar = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Diálogo para Eliminar Usuario
    usuarioParaEliminar?.let { usuario ->
        AlertDialog(
            onDismissRequest = { usuarioParaEliminar = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Eliminar Usuario",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas eliminar permanentemente a ${usuario.email}? Se eliminarán todas sus plantas y registros asociados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.eliminarUsuario(usuario)
                        usuarioParaEliminar = null
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { usuarioParaEliminar = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Diálogo para Borrar Todos los Usuarios
    if (mostrarDialogoEliminarTodos) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoEliminarTodos = false },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Borrar todos los usuarios",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas eliminar todos los usuarios de prueba y sus plantas? La cuenta de admin@gmail.com se mantendrá intacta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.eliminarTodosLosUsuariosExceptoAdmin()
                        mostrarDialogoEliminarTodos = false
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Eliminar todos")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoEliminarTodos = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaUsuario(
    usuario: UsuarioAdmin,
    esYo: Boolean,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
    onVerPlantas: () -> Unit,
    onToggleActivo: () -> Unit,
    onRestablecer: () -> Unit
) {
    val esAdmin = usuario.rol == "admin"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundSecondary)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = usuario.email,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (esYo) {
                    Text(
                        text = "Tu cuenta",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (!usuario.activo) {
                    Text(
                        text = "Cuenta desactivada",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (esAdmin) AccentGreen.copy(alpha = 0.15f)
                        else TextSecondary.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (esAdmin) "Administrador" else "Usuario",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (esAdmin) AccentGreen else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FlowRow {
            TextButton(onClick = onVerPlantas) {
                Text("Ver plantas", color = AccentGreen)
            }
            TextButton(onClick = onEditar) {
                Text("Editar", color = AccentGreen)
            }
            TextButton(onClick = onRestablecer) {
                Text("Restablecer contraseña", color = AccentGreen)
            }
            // Un admin no puede desactivarse ni eliminarse a sí mismo
            if (!esYo) {
                TextButton(onClick = onToggleActivo) {
                    Text(
                        text = if (usuario.activo) "Desactivar" else "Activar",
                        color = if (usuario.activo) ErrorRed else AccentGreen
                    )
                }
                TextButton(onClick = onEliminar) {
                    Text("Eliminar", color = ErrorRed)
                }
            }
        }
    }
}
