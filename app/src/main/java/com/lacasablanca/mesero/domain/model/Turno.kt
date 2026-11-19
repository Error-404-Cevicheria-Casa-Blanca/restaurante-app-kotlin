package com.lacasablanca.mesero.domain.model

/** Turno activo del mesero (PRB-85). */
data class Turno(
    val id: Long = 0L,
    val nombre: String = "",
    val inicio: String = "",
    val fin: String = ""
)
