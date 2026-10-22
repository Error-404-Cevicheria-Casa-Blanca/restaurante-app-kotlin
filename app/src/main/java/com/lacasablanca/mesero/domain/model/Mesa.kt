package com.lacasablanca.mesero.domain.model

/** Mesa del salon. Campos del contrato v1: id, numero, capacidad, ubicacion, estado. */
data class Mesa(
    val id: Long,
    val numero: Int,
    val capacidad: Int,
    val ubicacion: String,
    val estado: EstadoMesa
) {
    val estaLibre: Boolean get() = estado == EstadoMesa.LIBRE
}
