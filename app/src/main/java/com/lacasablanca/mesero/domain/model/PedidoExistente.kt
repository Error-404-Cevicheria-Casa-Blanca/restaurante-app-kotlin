package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pedido ya registrado en una mesa OCUPADA (PRB-83).
 *
 * Las lineas llegan del `GET /api/v1/pedidos?mesa_id=X` y se guardan como
 * [ItemCarrito] para reutilizar el calculo de importes con BigDecimal.
 * La forma exacta de la respuesta queda como ASSUMPTION hasta que se cierre
 * PRB-82 (backend); el repositorio fake permite probar la UI mientras tanto.
 */
data class PedidoExistente(
    val pedidoId: Long = 0L,
    val estado: String = "",
    val lineas: List<ItemCarrito> = emptyList()
) {
    val total: BigDecimal
        get() = lineas
            .fold(BigDecimal.ZERO) { acumulado, item -> acumulado + item.subtotal }
            .setScale(2, RoundingMode.HALF_UP)

    companion object {
        /** Mesa sin pedido registrado (404 o arreglo vacio en el backend). */
        val VACIO = PedidoExistente()
    }
}
