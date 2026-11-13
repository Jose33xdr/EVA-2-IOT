package com.app.smartgarden.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.smartgarden.registros.Registro
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var uiState by mutableStateOf(AuthUiState())
        private set

    companion object {
        const val MAX_INTENTOS = 3
    }

    private fun traducirErrorAuth(e: Exception?): String {
        val msg = e?.localizedMessage ?: "Error desconocido"
        return when {
            msg.contains("password", ignoreCase = true) || msg.contains("credential", ignoreCase = true) || msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                "Contraseña incorrecta o credenciales inválidas."
            msg.contains("no user record", ignoreCase = true) || msg.contains("user-not-found", ignoreCase = true) ->
                "No existe una cuenta registrada con este correo."
            msg.contains("network", ignoreCase = true) ->
                "Error de red. Verifica tu conexión a internet."
            msg.contains("blocked", ignoreCase = true) || msg.contains("disabled", ignoreCase = true) ->
                "Esta cuenta ha sido desactivada por un administrador."
            else -> msg
        }
    }

    // ---------- VALIDACIÓN DE CONTRASEÑA SEGURA ----------
    fun validarPasswordSegura(password: String): String? {
        if (password.length < 8) return "Debe tener al menos 8 caracteres"
        if (!password.any { it.isUpperCase() }) return "Debe incluir al menos una mayúscula"
        if (!password.any { it.isLowerCase() }) return "Debe incluir al menos una minúscula"
        if (!password.any { it.isDigit() }) return "Debe incluir al menos un número"
        if (!password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }) {
            return "Debe incluir al menos un símbolo (ej: !@#$%)"
        }
        return null
    }

    // ---------- REGISTRO ----------
    fun registrar(email: String, password: String, confirmPassword: String) {
        val errorPassword = validarPasswordSegura(password)
        if (errorPassword != null) {
            uiState = uiState.copy(mensajeError = errorPassword)
            return
        }
        if (password != confirmPassword) {
            uiState = uiState.copy(mensajeError = "Las contraseñas no coinciden")
            return
        }

        uiState = uiState.copy(cargando = true, mensajeError = null)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                val datosUsuario = hashMapOf(
                    "email" to email,
                    "fechaRegistro" to System.currentTimeMillis(),
                    "rol" to "usuario"
                )
                db.collection("usuarios").document(uid).set(datosUsuario)
                    .addOnSuccessListener {
                        Registro.log(
                            tipo = "CUENTA_CREADA",
                            descripcion = "Cuenta creada: $email",
                            uid = uid,
                            email = email,
                            onListo = {
                                auth.signOut()
                                uiState = uiState.copy(cargando = false, registroExitoso = true)
                            }
                        )
                    }
                    .addOnFailureListener { e ->
                        uiState = uiState.copy(cargando = false, mensajeError = traducirErrorAuth(e))
                    }
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(cargando = false, mensajeError = traducirErrorAuth(e))
            }
    }

    // ---------- LOGIN CON CONTROL DE INTENTOS ----------
    fun login(email: String, password: String) {
        uiState = uiState.copy(cargando = true, mensajeError = null)
        val emailKey = email.trim().lowercase()

        // Si es el admin por defecto, garantizamos que funcione con Inacap.2030
        if (emailKey == "admin@gmail.com" && password == "Inacap.2030") {
            db.collection("intentosLogin").document(emailKey)
                .set(mapOf("intentosFallidos" to 0L, "bloqueadoHasta" to 0L))
                .addOnCompleteListener {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                            val uid = result.user?.uid ?: ""
                            db.collection("usuarios").document(uid).set(
                                mapOf(
                                    "email" to email,
                                    "rol" to "admin",
                                    "activo" to true,
                                    "fechaRegistro" to System.currentTimeMillis()
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            ).addOnSuccessListener {
                                Registro.log("LOGIN", "Inicio de sesión Admin")
                                uiState = uiState.copy(cargando = false, loginExitoso = true)
                            }
                        }
                        .addOnFailureListener {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { result ->
                                    val uid = result.user?.uid ?: ""
                                    db.collection("usuarios").document(uid).set(
                                        mapOf(
                                            "email" to email,
                                            "rol" to "admin",
                                            "activo" to true,
                                            "fechaRegistro" to System.currentTimeMillis()
                                        )
                                    ).addOnSuccessListener {
                                        Registro.log("LOGIN", "Creación e inicio de sesión Admin por defecto")
                                        uiState = uiState.copy(cargando = false, loginExitoso = true)
                                    }
                                }
                                .addOnFailureListener { eCrear ->
                                    uiState = uiState.copy(cargando = false, mensajeError = traducirErrorAuth(eCrear))
                                }
                        }
                }
            return
        }

        db.collection("intentosLogin").document(emailKey).get()
            .addOnSuccessListener { doc ->
                val bloqueadoHasta = doc.getLong("bloqueadoHasta") ?: 0L

                if (bloqueadoHasta > 0L) {
                    uiState = uiState.copy(
                        cargando = false,
                        mensajeError = "Cuenta bloqueada por superar los 3 intentos. Utiliza '¿Olvidaste tu contraseña?' para desbloquearla.",
                        mostrarRecuperar = true
                    )
                    return@addOnSuccessListener
                }

                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        db.collection("intentosLogin").document(emailKey)
                            .set(mapOf("intentosFallidos" to 0L, "bloqueadoHasta" to 0L))

                        // Una cuenta desactivada por un administrador no puede entrar
                        val uid = result.user?.uid ?: ""
                        db.collection("usuarios").document(uid).get()
                            .addOnSuccessListener { perfil ->
                                if (perfil.getBoolean("activo") == false) {
                                    Registro.log(
                                        "LOGIN",
                                        "Intento de acceso con cuenta desactivada",
                                        onListo = { auth.signOut() }
                                    )
                                    uiState = uiState.copy(
                                        cargando = false,
                                        mensajeError = "Tu cuenta fue desactivada por un administrador."
                                    )
                                } else {
                                    Registro.log("LOGIN", "Inicio de sesión")
                                    uiState = uiState.copy(cargando = false, loginExitoso = true)
                                }
                            }
                            .addOnFailureListener { e ->
                                auth.signOut()
                                uiState = uiState.copy(cargando = false, mensajeError = traducirErrorAuth(e))
                            }
                    }
                    .addOnFailureListener {
                        manejarIntentoFallido(emailKey, doc)
                    }
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(cargando = false, mensajeError = traducirErrorAuth(e))
            }
    }

    private fun manejarIntentoFallido(emailKey: String, doc: DocumentSnapshot) {
        val intentosActuales = (doc.getLong("intentosFallidos") ?: 0L).toInt() + 1

        if (intentosActuales >= MAX_INTENTOS) {
            val bloqueoHasta = Long.MAX_VALUE
            db.collection("intentosLogin").document(emailKey)
                .set(mapOf("intentosFallidos" to intentosActuales.toLong(), "bloqueadoHasta" to bloqueoHasta))
            uiState = uiState.copy(
                cargando = false,
                mensajeError = "Superaste el máximo de 3 intentos. Cuenta bloqueada. Utiliza '¿Olvidaste tu contraseña?' para desbloquearla.",
                mostrarRecuperar = true
            )
        } else {
            db.collection("intentosLogin").document(emailKey)
                .set(mapOf("intentosFallidos" to intentosActuales.toLong(), "bloqueadoHasta" to 0L))
            val restantes = MAX_INTENTOS - intentosActuales
            uiState = uiState.copy(
                cargando = false,
                mensajeError = "Credenciales incorrectas. Te quedan $restantes intento(s).",
                mostrarRecuperar = true
            )
        }
    }

    // ---------- RECUPERAR CONTRASEÑA ----------
    fun recuperarPassword(email: String) {
        val emailKey = email.trim().lowercase()
        if (emailKey.isBlank()) {
            uiState = uiState.copy(mensajeError = "Ingresa tu correo electrónico arriba para recuperar la contraseña")
            return
        }
        uiState = uiState.copy(cargando = true, mensajeError = null)
        auth.sendPasswordResetEmail(emailKey)
            .addOnSuccessListener {
                db.collection("intentosLogin").document(emailKey)
                    .set(mapOf("intentosFallidos" to 0L, "bloqueadoHasta" to 0L))
                    .addOnSuccessListener {
                        uiState = uiState.copy(
                            cargando = false,
                            mensajeError = "Se envió un correo de recuperación a $email. Cuenta desbloqueada.",
                            mostrarRecuperar = false
                        )
                    }
                    .addOnFailureListener { e ->
                        uiState = uiState.copy(cargando = false, mensajeError = traducirErrorAuth(e))
                    }
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(cargando = false, mensajeError = traducirErrorAuth(e))
            }
    }

    fun limpiarMensaje() {
        uiState = uiState.copy(mensajeError = null)
    }
}

data class AuthUiState(
    val cargando: Boolean = false,
    val mensajeError: String? = null,
    val loginExitoso: Boolean = false,
    val registroExitoso: Boolean = false,
    val mostrarRecuperar: Boolean = false
)