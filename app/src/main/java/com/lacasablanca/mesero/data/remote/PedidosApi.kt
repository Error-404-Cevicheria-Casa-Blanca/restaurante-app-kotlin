package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * POST /api/v1/pedidos (PedidoController.java). Responde 201 Created
 * con el pedido consolidado y el total calculado por el backend.
 */
interface PedidosApi {

    @POST("api/v1/pedidos")
    suspend fun crearPedido(@Body request: CrearPedidoRequest): PedidoResponseDto
}
