package com.lacasablanca.mesero.ui.login

/** Errores de login que la Activity traduce a strings. */
enum class ErrorLogin {
    CAMPOS_VACIOS,
    CREDENCIALES,
    RED,
    ROL_NO_PERMITIDO,
    DESCONOCIDO
}

data class LoginUiState(
    val usuario: String = "",
    val clave: String = "",
    val cargando: Boolean = false,
    val error: ErrorLogin? = null,
    val mostrarClave: Boolean = false
)

sealed interface LoginEvento {
    object IrAMesas : LoginEvento
}
