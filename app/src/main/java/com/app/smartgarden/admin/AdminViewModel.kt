package com.app.smartgarden.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.smartgarden.registros.Registro
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class UsuarioAdmin(
    val uid: String = "",
    val email: String = "",
    val rol: String = "usuario",
    val fechaRegistro: Long = 0L,
    val activo: Boolean = true
)

data class AdminUiState(
    val usuarios: List<UsuarioAdmin> = emptyList(),
    val cargando: Boolean = true,
    val mensajeError: String? = null,
    val mensajeInfo: String? = null
)

class AdminViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    var uiState by mutableStateOf(AdminUiState())
        private set

    // UID del admin que está usando la app (para no permitir que se quite el rol a sí mismo)
    val uidActual: String? get() = auth.currentUser?.uid

    init {
        asegurarPerfilAdmin()
        observarUsuarios()
    }

    private fun asegurarPerfilAdmin() {
        val user = auth.currentUser ?: return
        if (user.email?.lowercase() == "admin@gmail.com") {
            db.collection("usuarios").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        db.collection("usuarios").document(user.uid).set(
                            mapOf(
                                "email" to "admin@gmail.com",
                                "rol" to "admin",
                                "activo" to true,
                                "fechaRegistro" to System.currentTimeMillis()
                            )
                        )
                    }
                }
        }
    }

    // Escucha en tiempo real a TODOS los usuarios registrados
    private fun observarUsuarios() {
        listener?.remove()
        listener = db.collection("usuarios")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    uiState = uiState.copy(cargando = false, mensajeError = error.localizedMessage)
                    return@addSnapshotListener
                }

                val lista = snapshot?.documents?.map { doc ->
                    UsuarioAdmin(
                        uid = doc.id,
                        email = doc.getString("email") ?: "(sin correo)",
                        rol = doc.getString("rol") ?: "usuario",
                        fechaRegistro = doc.getLong("fechaRegistro") ?: 0L,
                        activo = doc.getBoolean("activo") != false
                    )
                }?.sortedBy { it.email.lowercase() } ?: emptyList()

                uiState = uiState.copy(cargando = false, usuarios = lista, mensajeError = null)
            }
    }

    // Cambia el rol de un usuario entre "usuario" y "admin"
    fun cambiarRol(uid: String, nuevoRol: String) {
        // Seguridad: un admin no puede cambiarse el rol a sí mismo (evita quedarse sin admins)
        if (uid == uidActual) return
        if (nuevoRol != "usuario" && nuevoRol != "admin") return

        db.collection("usuarios").document(uid)
            .update("rol", nuevoRol)
            .addOnSuccessListener {
                val emailObjetivo = uiState.usuarios.firstOrNull { it.uid == uid }?.email
                Registro.log(
                    "ROL",
                    "Rol cambiado a \"$nuevoRol\"",
                    uidAfectado = uid,
                    emailAfectado = emailObjetivo
                )
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    // Activa o desactiva una cuenta. Una cuenta desactivada no puede iniciar sesión
    // y, si estaba conectada, se cierra su sesión en tiempo real.
    fun cambiarActivo(uid: String, activo: Boolean) {
        if (uid == uidActual) return // el admin no puede desactivarse a sí mismo
        uiState = uiState.copy(mensajeError = null, mensajeInfo = null)

        db.collection("usuarios").document(uid)
            .update("activo", activo)
            .addOnSuccessListener {
                val emailObjetivo = uiState.usuarios.firstOrNull { it.uid == uid }?.email
                Registro.log(
                    "CREDENCIALES",
                    if (activo) "Cuenta activada" else "Cuenta desactivada",
                    uidAfectado = uid,
                    emailAfectado = emailObjetivo
                )
                uiState = uiState.copy(
                    mensajeInfo = if (activo) "Cuenta activada" else "Cuenta desactivada"
                )
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    // Envía al usuario un correo para que ELLA/ÉL elija una contraseña nueva.
    // (El admin nunca conoce ni define la contraseña de otra persona.)
    fun enviarRestablecimiento(usuario: UsuarioAdmin) {
        uiState = uiState.copy(mensajeError = null, mensajeInfo = null)
        auth.sendPasswordResetEmail(usuario.email)
            .addOnSuccessListener {
                Registro.log(
                    "CREDENCIALES",
                    "Correo de restablecimiento de contraseña enviado",
                    uidAfectado = usuario.uid,
                    emailAfectado = usuario.email
                )
                uiState = uiState.copy(mensajeInfo = "Correo de restablecimiento enviado a ${usuario.email}")
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    // Crea un usuario nuevo usando una app secundaria de Firebase para no cerrar la sesión del admin
    fun crearUsuario(email: String, password: String, rol: String, context: android.content.Context, onExito: () -> Unit) {
        val errorPassword = com.app.smartgarden.auth.AuthViewModel().validarPasswordSegura(password)
        if (email.isBlank() || !email.contains("@")) {
            uiState = uiState.copy(mensajeError = "Correo electrónico inválido")
            return
        }
        if (errorPassword != null) {
            uiState = uiState.copy(mensajeError = errorPassword)
            return
        }
        if (rol != "usuario" && rol != "admin") {
            uiState = uiState.copy(mensajeError = "Rol inválido")
            return
        }

        uiState = uiState.copy(cargando = true, mensajeError = null, mensajeInfo = null)

        try {
            val currentApp = com.google.firebase.FirebaseApp.getInstance()
            val secondaryApp = try {
                com.google.firebase.FirebaseApp.getInstance("TempAuthApp")
            } catch (e: Exception) {
                com.google.firebase.FirebaseApp.initializeApp(context, currentApp.options, "TempAuthApp")
            }
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            secondaryAuth.createUserWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener
                    val datosUsuario = hashMapOf(
                        "email" to email.trim(),
                        "fechaRegistro" to System.currentTimeMillis(),
                        "rol" to rol,
                        "activo" to true
                    )
                    val secondaryDb = FirebaseFirestore.getInstance(secondaryApp)
                    secondaryDb.collection("usuarios").document(uid).set(datosUsuario)
                        .addOnSuccessListener {
                            secondaryAuth.signOut()
                            Registro.log(
                                tipo = "CUENTA_CREADA",
                                descripcion = "Cuenta creada por admin: ${email.trim()} (rol: $rol)",
                                uidAfectado = uid,
                                emailAfectado = email.trim()
                            )
                            uiState = uiState.copy(cargando = false, mensajeInfo = "Usuario creado exitosamente")
                            onExito()
                        }
                        .addOnFailureListener { e ->
                            secondaryAuth.signOut()
                            uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
                        }
                }
                .addOnFailureListener { e ->
                    secondaryAuth.signOut()
                    val msg = e.localizedMessage ?: ""
                    val mensajeAmigable = if (msg.contains("email-already-in-use", ignoreCase = true) || msg.contains("already in use", ignoreCase = true)) {
                        "Este correo ya está registrado en Firebase Auth. Prueba con otro correo distinto."
                    } else {
                        msg
                    }
                    uiState = uiState.copy(cargando = false, mensajeError = mensajeAmigable)
                }
        } catch (e: Exception) {
            uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
        }
    }

    // Edita los datos de un usuario (email, rol, activo)
    fun editarUsuario(uid: String, nuevoEmail: String, nuevoRol: String, nuevoActivo: Boolean) {
        if (nuevoEmail.isBlank() || !nuevoEmail.contains("@")) {
            uiState = uiState.copy(mensajeError = "Correo electrónico inválido")
            return
        }
        if (nuevoRol != "usuario" && nuevoRol != "admin") return
        if (uid == uidActual && !nuevoActivo) {
            uiState = uiState.copy(mensajeError = "No puedes desactivar tu propia cuenta de administrador")
            return
        }
        if (uid == uidActual && nuevoRol != "admin") {
            uiState = uiState.copy(mensajeError = "No puedes quitarte el rol de administrador a ti mismo")
            return
        }

        uiState = uiState.copy(mensajeError = null, mensajeInfo = null)

        val updates = mapOf(
            "email" to nuevoEmail.trim(),
            "rol" to nuevoRol,
            "activo" to nuevoActivo
        )

        db.collection("usuarios").document(uid)
            .update(updates)
            .addOnSuccessListener {
                Registro.log(
                    "ROL",
                    "Usuario editado: ${nuevoEmail.trim()} (rol: $nuevoRol, activo: $nuevoActivo)",
                    uidAfectado = uid,
                    emailAfectado = nuevoEmail.trim()
                )
                uiState = uiState.copy(mensajeInfo = "Usuario actualizado correctamente")
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    // Elimina un usuario y todas sus plantas y libera sus sensores asociados
    fun eliminarUsuario(usuario: UsuarioAdmin) {
        if (usuario.uid == uidActual) {
            uiState = uiState.copy(mensajeError = "No puedes eliminar tu propia cuenta de administrador")
            return
        }

        uiState = uiState.copy(cargando = true, mensajeError = null, mensajeInfo = null)

        db.collection("usuarios").document(usuario.uid).collection("plantas").get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    val sensorId = doc.getString("sensorId")
                    if (!sensorId.isNullOrBlank()) {
                        db.collection("sensores").document(sensorId)
                            .update(mapOf("asignado" to false, "plantaId" to null, "uidDuenoPlanta" to null))
                    }
                    batch.delete(doc.reference)
                }
                batch.delete(db.collection("usuarios").document(usuario.uid))

                batch.commit()
                    .addOnSuccessListener {
                        Registro.log(
                            "CUENTA_ELIMINADA",
                            "Usuario eliminado por administrador: ${usuario.email}",
                            uidAfectado = usuario.uid,
                            emailAfectado = usuario.email
                        )
                        uiState = uiState.copy(cargando = false, mensajeInfo = "Usuario ${usuario.email} eliminado")
                    }
                    .addOnFailureListener { e ->
                        uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
                    }
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    // Elimina todos los usuarios excepto el administrador actual (admin@gmail.com)
    fun eliminarTodosLosUsuariosExceptoAdmin() {
        uiState = uiState.copy(cargando = true, mensajeError = null, mensajeInfo = null)
        db.collection("usuarios").get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                val tasks = mutableListOf<com.google.android.gms.tasks.Task<*>>()

                for (doc in snapshot.documents) {
                    val uid = doc.id
                    val email = doc.getString("email") ?: ""
                    if (uid == uidActual || email.lowercase() == "admin@gmail.com") continue

                    val plantasTask = db.collection("usuarios").document(uid).collection("plantas").get()
                        .addOnSuccessListener { plantasSnap ->
                            for (plantaDoc in plantasSnap.documents) {
                                val sensorId = plantaDoc.getString("sensorId")
                                if (!sensorId.isNullOrBlank()) {
                                    db.collection("sensores").document(sensorId)
                                        .update(mapOf("asignado" to false, "plantaId" to null, "uidDuenoPlanta" to null))
                                }
                                batch.delete(plantaDoc.reference)
                            }
                        }
                    tasks.add(plantasTask)
                    batch.delete(doc.reference)
                }

                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                    .addOnSuccessListener {
                        batch.commit()
                            .addOnSuccessListener {
                                Registro.log(
                                    "CUENTA_ELIMINADA",
                                    "Todos los usuarios de prueba fueron eliminados por el administrador",
                                    uidAfectado = "todos",
                                    emailAfectado = "varios"
                                )
                                uiState = uiState.copy(cargando = false, mensajeInfo = "Todos los usuarios de prueba fueron eliminados")
                            }
                            .addOnFailureListener { e ->
                                uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
                            }
                    }
                    .addOnFailureListener { e ->
                        uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
                    }
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
            }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
