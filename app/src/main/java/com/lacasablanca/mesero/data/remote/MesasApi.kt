package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.CambioEstadoDto
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.DividirMesaRequest
import com.lacasablanca.mesero.data.remote.dto.DivisionMesaResponseDto
import com.lacasablanca.mesero.data.remote.dto.FusionarMesasRequest
import com.lacasablanca.mesero.data.remote.dto.MesaDto
import com.lacasablanca.mesero.data.remote.dto.TransferirMesaRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato v1 de mesas: listado, cambio de estado y operaciones de salon
 * (PRB-75 / PRB-93).
 */
interface MesasApi {
    @GET("api/v1/mesas")
    suspend fun listar(): List<MesaDto>

    @PATCH("api/v1/mesas/{id}/estado")
    suspend fun cambiarEstado(
        @Path("id") id: Long,
        @Body cuerpo: CambioEstadoDto
    ): MesaDto

    /** Mueve los platos indicados a una cuenta nueva (mesa destino opcional). */
    @POST("api/v1/mesas/{id}/dividir")
    suspend fun dividirMesa(
        @Path("id") id: Long,
        @Body cuerpo: DividirMesaRequest
    ): DivisionMesaResponseDto

    /** Une los pedidos de [FusionarMesasRequest.mesaOrigenId] en la mesa {id}. */
    @POST("api/v1/mesas/{id}/fusionar")
    suspend fun fusionarMesa(
        @Path("id") id: Long,
        @Body cuerpo: FusionarMesasRequest
    ): CuentaResponse

    /** Mueve los pedidos de la mesa {id} a la mesa destino. */
    @POST("api/v1/mesas/{id}/transferir")
    suspend fun transferirMesa(
        @Path("id") id: Long,
        @Body cuerpo: TransferirMesaRequest
    ): CuentaResponse
}
