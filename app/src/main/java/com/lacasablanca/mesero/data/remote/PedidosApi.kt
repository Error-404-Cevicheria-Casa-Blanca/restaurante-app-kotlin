package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.PedidoExistenteDto
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * POST /api/v1/pedidos (PedidoController.java). Responde 201 Created
 * con el pedido consolidado y el total calculado por el backend.
 *
 * El header `Idempotency-Key` y el campo `client_request_id` llevan el mismo
 * UUID: si la red falla despues de que el backend creo el pedido, el reintento
 * con la misma clave devuelve el pedido existente en vez de duplicarlo.
 */
interface PedidosApi {

    @POST("api/v1/pedidos")
    suspend fun crearPedido(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CrearPedidoRequest
    ): PedidoResponseDto

    /**
     * PRB-83: pedido activo de una mesa OCUPADA. La forma de la respuesta se
     * documenta como ASSUMPTION en [PedidoExistenteDto] hasta cerrar PRB-82.
     */
    @GET("api/v1/pedidos")
    suspend fun obtenerPedidoDeMesa(@Query("mesa_id") mesaId: Long): PedidoExistenteDto?
}
