package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import com.lacasablanca.mesero.data.remote.dto.TrabajoImpresionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato de pre-cuenta y cobro (PRB-55 / PRB-56).
 * El POST de cobro deja la mesa en LIBRE en el servidor; la app no hace PATCH extra.
 */
interface CobroApi {

    @GET("api/v1/mesas/{id}/cuenta")
    suspend fun obtenerCuenta(@Path("id") mesaId: Long): Response<CuentaResponse>

    @POST("api/v1/mesas/{id}/cobrar")
    suspend fun cobrar(
        @Path("id") mesaId: Long,
        @Body request: CobrarRequest
    ): Response<CobroResponse>

    @POST("api/v1/mesas/{id}/precuenta")
    suspend fun imprimirPrecuenta(@Path("id") mesaId: Long): Response<CuentaResponse>

    @POST("api/v1/mesas/{id}/precuenta/reimprimir")
    suspend fun reimprimirPrecuenta(@Path("id") mesaId: Long): Response<TrabajoImpresionDto>

    /**
     * PRB-88: cuenta de un pedido sin mesa para el cobro de delivery.
     * El backend devuelve el pedido consolidado con items y totales.
     */
    @GET("api/v1/pedidos/{id}")
    suspend fun obtenerPedido(@Path("id") pedidoId: Long): Response<PedidoResponseDto>

    /**
     * PRB-88: cobra un pedido sin mesa. No libera ninguna mesa; el backend
     * marca el pedido como COBRADO.
     */
    @POST("api/v1/pedidos/{id}/cobrar")
    suspend fun cobrarPedido(
        @Path("id") pedidoId: Long,
        @Body request: CobrarRequest
    ): Response<CobroResponse>
}
