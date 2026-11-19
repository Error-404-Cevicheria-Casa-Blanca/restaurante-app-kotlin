package com.lacasablanca.mesero.domain.model

/** Usuario autenticado (respuesta de /auth/login, contrato v1). */
data class Usuario(
    val id: Long,
    val nombre: String,
    val rol: String,
    val nombreVisible: String = "",
    val turno: Turno? = null,
    val terminal: Terminal? = null
)
