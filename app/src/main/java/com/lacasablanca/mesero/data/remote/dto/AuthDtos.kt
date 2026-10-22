package com.lacasablanca.mesero.data.remote.dto

/** Request de POST /api/v1/auth/login (contrato v1, propuesta D4). */
data class LoginRequestDto(
    val usuario: String,
    val clave: String
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
    val rol: String? = null
)
