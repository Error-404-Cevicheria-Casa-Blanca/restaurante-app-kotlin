package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.lacasablanca.mesero.domain.model.CuentaMesa
import com.lacasablanca.mesero.domain.model.DivisionMesa
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Contrato real de PRB-75, verificado en `restaurante-backend-java`
 * (`DividirMesaRequest.java`, `FusionarMesasRequest.java`,
 * `TransferirMesaRequest.java` y `MesaController.java`).
 *
 * Los ids viajan como numeros; el backend los recibe como `Integer`, por eso
 * se envian como [Long] sin perder el formato JSON.
 */
data class DividirMesaRequest(
    @SerializedName("item_ids") val itemIds: List<Long>,
    @SerializedName("mesa_destino_id") val mesaDestinoId: Long?
)

data class FusionarMesasRequest(
    @SerializedName("mesa_origen_id") val mesaOrigenId: Long
)

data class TransferirMesaRequest(
    @SerializedName("mesa_destino_id") val mesaDestinoId: Long
)

/**
 * Respuesta de `POST /api/v1/mesas/{id}/dividir`
 * (`DivisionMesaResponse.java`). Los pedidos se modelan con el mismo DTO del
 * pedido consolidado para no duplicar su contrato.
 */
data class DivisionMesaResponseDto(
    @SerializedName("mesa_origen_id") val mesaOrigenId: Long? = null,
    @SerializedName("mesa_destino_id") val mesaDestinoId: Long? = null,
    @SerializedName("pedido_origen") val pedidoOrigen: PedidoResponseDto? = null,
    @SerializedName("pedido_nuevo") val pedidoNuevo: PedidoResponseDto? = null,
    @SerializedName("total_combinado") val totalCombinado: BigDecimal? = null
)

/**
 * Mapeo tolerante: si el backend omite un id o el total, se usa 0 en vez de
 * romper la respuesta. Los importes se fijan a dos decimales porque el backend
 * trabaja en soles con BigDecimal.
 */
fun DivisionMesaResponseDto.aDominio(): DivisionMesa = DivisionMesa(
    mesaOrigenId = mesaOrigenId ?: 0L,
    mesaDestinoId = mesaDestinoId ?: 0L,
    pedidoOrigenId = pedidoOrigen?.id ?: 0L,
    pedidoNuevoId = pedidoNuevo?.id ?: 0L,
    totalCombinado = (totalCombinado ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
)

/**
 * Fusionar y transferir devuelven `CuentaResponse` (`CuentaResponse.java`).
 * [mesaIdPorDefecto] cubre una respuesta sin `mesa_id` (se conoce por la
 * peticion).
 */
fun CuentaResponse.aCuentaMesa(mesaIdPorDefecto: Long): CuentaMesa = CuentaMesa(
    mesaId = mesaId ?: mesaIdPorDefecto,
    total = (total ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
)
