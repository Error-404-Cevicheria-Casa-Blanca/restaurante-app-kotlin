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
    val itemId: Long? = null,
    /**
     * PRB-76: estado de preparacion que informa el backend para un plato ya
     * enviado. null = la linea es nueva (aun no viajo a cocina) o el backend
     * todavia no informa el estado; en ambos casos la UI no pinta la pildora.
     */
    val estadoPlato: EstadoPlato? = null,
    /**
     * PRB-92: modificadores elegidos para esta linea ("Sin cebolla",
     * "Extra queso"...). La identidad de una linea del carrito es la pareja
     * (producto, modificadores): el mismo plato con selecciones distintas son
     * dos lineas separadas.
     */
    val modificadores: List<Modificador> = emptyList()
) {
    /** Recargo por unidad que aportan los modificadores (BigDecimal). */
    val recargoModificadores: BigDecimal
        get() = Modificador.recargoTotal(modificadores)

    /**
     * Precio unitario final: precio de carta + recargo de modificadores.
     * El backend aplica el recargo por unidad y despues multiplica por la
     * cantidad (`PedidoService`), por eso se calcula en ese orden.
     */
    val precioUnitarioConModificadores: BigDecimal
        get() = producto.precio.add(recargoModificadores).setScale(2, RoundingMode.HALF_UP)

    val subtotal: BigDecimal
        get() = precioUnitarioConModificadores
            .multiply(BigDecimal.valueOf(cantidad.toLong()))
            .setScale(2, RoundingMode.HALF_UP)
}
