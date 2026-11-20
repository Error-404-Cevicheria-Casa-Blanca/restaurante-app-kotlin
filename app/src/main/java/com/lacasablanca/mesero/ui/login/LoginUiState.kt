package com.lacasablanca.mesero.ui.login

/** Errores de login que la Activity traduce a strings. */
enum class ErrorLogin {
    CAMPOS_VACIOS,
    USUARIO_REQUERIDO,
    CREDENCIALES,
    PIN_INCORRECTO,
    BLOQUEADO,
    VALIDACION,
    RED,
    ROL_NO_PERMITIDO,
    DESCONOCIDO
}

data class LoginUiState(
    val usuario: String = "",
    val clave: String = "",
    val modoPin: Boolean = false,
    val pin: String = "",
    val cargando: Boolean = false,
    val error: ErrorLogin? = null,
    val mostrarClave: Boolean = false
) {
    val digitosPin: Int get() = pin.length

    companion object {
        const val LONGITUD_PIN = 4
    }
}

sealed interface LoginEvento {
    object IrAMesas : LoginEvento
}
