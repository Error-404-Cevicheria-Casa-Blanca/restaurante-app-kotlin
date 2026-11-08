package com.lacasablanca.mesero.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Linea del carrito local. `cantidad` siempre es >= 1 (lo garantiza el ViewModel).
 * El subtotal de la linea se calcula con BigDecimal y dos decimales.
 */
data class ItemCarrito(
    val producto: Producto,
    val cantidad: Int,
    val notas: String = "",
    /**
     * Id del detalle en el backend (`pedido_item.id`). Null en las lineas que
     * aun no se enviaron y cuando el backend no lo informa. Es el `itemId`
     * que exige `POST /api/v1/pedidos/{pedidoId}/items/{itemId}/anular`
     * (PRB-77).
     */
    val itemId: Long? = null
) {
    val subtotal: BigDecimal
        get() = producto.precio
            .multiply(BigDecimal.valueOf(cantidad.toLong()))
            .setScale(2, RoundingMode.HALF_UP)
}
