package com.lacasablanca.mesero.domain.model

/**
 * Medios de pago aceptados por el contrato de cobro.
 * La UI de PRB-56 muestra cuatro; TRANSFERENCIA queda disponible para el contrato.
 */
enum class MetodoPago(val valorApi: String) {
    EFECTIVO("EFECTIVO"),
    YAPE("YAPE"),
    PLIN("PLIN"),
    TARJETA("TARJETA"),
    TRANSFERENCIA("TRANSFERENCIA")
}
