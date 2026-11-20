package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Request de POST /api/v1/auth/login (contrato v1, propuesta D4). */
data class LoginRequestDto(
    val usuario: String,
    val clave: String,
    @SerializedName("terminal_codigo")
    val terminalCodigo: String? = null
)

/** Request de POST /api/v1/auth/pin (PRB-54, verificado en el backend Java). */
data class PinRequestDto(
    val usuario: String,
    val pin: String,
    @SerializedName("terminal_codigo")
    val terminalCodigo: String? = null
)

/** Response de POST /api/v1/auth/login (contrato v1, propuesta D4). */
data class LoginResponseDto(
    val token: String? = null,
    val tipo: String? = null,
    val usuario: UsuarioDto? = null
)

data class UsuarioDto(
    val id: Long? = null,
    val nombre: String? = null,
    @SerializedName("nombre_visible")
    val nombreVisible: String? = null,
    val rol: String? = null,
    val turno: TurnoDto? = null,
    val terminal: TerminalDto? = null
)

/** Turno activo resuelto por el backend en el login (PRB-85). */
data class TurnoDto(
    val id: Long? = null,
    val nombre: String? = null,
    val inicio: String? = null,
    val fin: String? = null
)

/** Terminal fisica (tablet) asignada a la sesion (PRB-85). */
data class TerminalDto(
    val id: Long? = null,
    val codigo: String? = null,
    val nombre: String? = null
)
