package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal

/**
 * Resultado de `POST /api/v1/mesas/{id}/dividir` (PRB-75).
 *
 * El backend crea un pedido nuevo con los platos seleccionados; [pedidoOrigenId]
 * y [pedidoNuevoId] permiten trazarlo. [totalCombinado] lo calcula el servidor
 * con BigDecimal.
 */
data class DivisionMesa(
    val mesaOrigenId: Long,
    val mesaDestinoId: Long,
    val pedidoOrigenId: Long,
    val pedidoNuevoId: Long,
    val totalCombinado: BigDecimal
)

/**
 * Cuenta resumida que devuelven fusionar y transferir (PRB-75).
 *
 * La app solo necesita la mesa afectada y su total; el detalle completo sigue
 * viviendo en Cobro.
 */
data class CuentaMesa(
    val mesaId: Long,
    val total: BigDecimal
)
