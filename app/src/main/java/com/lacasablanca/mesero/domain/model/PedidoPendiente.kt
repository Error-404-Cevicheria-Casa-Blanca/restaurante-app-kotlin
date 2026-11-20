package com.lacasablanca.mesero.domain.model

object ErroresOutbox {
    const val CONFLICTO = "CONFLICTO"
    const val SESION_EXPIRADA = "SESION_EXPIRADA"
    const val RED = "RED"
}

data class PedidoPendiente(
    val id: Long,
    val mesaId: Long,
    val creadoEn: Long,
    val intentos: Int,
    val ultimoError: String?
) {
    val conflicto: Boolean
        get() = ultimoError == ErroresOutbox.CONFLICTO || ultimoError == "HTTP 409"
}

data class ResumenOutbox(
    val pendientes: List<PedidoPendiente> = emptyList()
) {
    val total: Int get() = pendientes.size
    val conflictos: Int get() = pendientes.count { it.conflicto }
}
