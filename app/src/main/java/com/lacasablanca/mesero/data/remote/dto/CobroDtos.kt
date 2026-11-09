package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * Respuesta de GET /api/v1/mesas/{id}/cuenta.
 * Contrato confirmado para PRB-56 (pre-cuenta y cobro).
 */
data class CuentaResponse(
    @SerializedName("mesa_id") val mesaId: Long? = null,
    val pedidos: List<PedidoResumenCuenta>? = null,
    val items: List<ItemCuenta>? = null,
    val subtotal: BigDecimal? = null,
    val igv: BigDecimal? = null,
    val total: BigDecimal? = null,
    @SerializedName("propina_sugerida") val propinaSugerida: BigDecimal? = null
)

/** Linea de la cuenta: plato, cantidad, precio unitario y subtotal. */
data class ItemCuenta(
    val nombre: String? = null,
    val cantidad: Int? = null,
    @SerializedName("precio_unitario") val precioUnitario: BigDecimal? = null,
    @SerializedName("subtotal_linea") val subtotalLinea: BigDecimal? = null
)

/**
 * Resumen de pedido incluido en la cuenta y en la respuesta del cobro.
 * ASSUMPTION(PRB-56): el contrato no detalla sus campos; se modelan id, estado y total.
 */
data class PedidoResumenCuenta(
    val id: Long? = null,
    val estado: String? = null,
    val total: BigDecimal? = null
)

/**
 * Pago individual del cobro.
 * La referencia es obligatoria cuando el metodo no es EFECTIVO.
 */
data class PagoRequest(
    val metodo: String,
    val monto: BigDecimal,
    val referencia: String? = null
)

/** Cuerpo de POST /api/v1/mesas/{id}/cobrar. */
data class CobrarRequest(
    val pagos: List<PagoRequest>,
    val propina: BigDecimal = BigDecimal.ZERO,
    /**
     * Monto del descuento ya aplicado en soles (PRB-80).
     * ASSUMPTION(PRB-80): el contrato v1 aun no define este campo; se envia
     * el monto final y no el tipo/porcentaje, que es solo de la UI.
     */
    val descuento: BigDecimal = BigDecimal.ZERO,
    val observacion: String? = null,
    @SerializedName("client_request_id") val clientRequestId: String
)

/** Respuesta de POST /api/v1/mesas/{id}/cobrar. */
data class CobroResponse(
    val total: BigDecimal? = null,
    val pagado: BigDecimal? = null,
    val vuelto: BigDecimal? = null,
    val pedidos: List<PedidoResumenCuenta>? = null
)
