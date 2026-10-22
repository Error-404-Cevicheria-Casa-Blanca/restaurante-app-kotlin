package com.lacasablanca.mesero.domain.model

/** Sesion activa: token JWT + datos del usuario que lo obtuvo. */
data class Sesion(
    val token: String,
    val usuario: Usuario
)
