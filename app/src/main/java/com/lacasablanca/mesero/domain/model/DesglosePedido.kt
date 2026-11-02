package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Desglose informativo de la comanda. Los precios de la carta INCLUYEN IGV (D13).
 * Coincide con `PedidoService` del backend Java:
 *   total    = suma de (precio_unitario * cantidad)
 *   subtotal = total / 1.18  (HALF_UP, 2 decimales)
 *   igv      = total - subtotal
 * Por eso el IGV se obtiene restando, no multiplicando: evita diferencias de centimos.
 */
data class DesglosePedido(
    val subtotal: BigDecimal,
    val igv: BigDecimal,
    val total: BigDecimal
)

object CalculoIgv {

    /** Factor oficial del IGV peruano (18%). */
    val FACTOR: BigDecimal = BigDecimal("1.18")

    fun desglosar(totalBruto: BigDecimal): DesglosePedido {
        val total = totalBruto.setScale(2, RoundingMode.HALF_UP)
        val subtotal = total.divide(FACTOR, 2, RoundingMode.HALF_UP)
        val igv = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP)
        return DesglosePedido(subtotal = subtotal, igv = igv, total = total)
    }
}
