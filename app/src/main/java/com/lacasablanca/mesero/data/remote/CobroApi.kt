package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
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
    suspend fun imprimirPrecuenta(@Path("id") mesaId: Long): Response<Unit>
}
