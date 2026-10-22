package com.lacasablanca.mesero.data.remote

import com.lacasablanca.mesero.data.remote.dto.CambioEstadoDto
import com.lacasablanca.mesero.data.remote.dto.MesaDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/** Contrato v1: GET /api/v1/mesas y PATCH /api/v1/mesas/{id}/estado. */
interface MesasApi {
    @GET("api/v1/mesas")
    suspend fun listar(): List<MesaDto>

    @PATCH("api/v1/mesas/{id}/estado")
    suspend fun cambiarEstado(
        @Path("id") id: Long,
        @Body cuerpo: CambioEstadoDto
    ): MesaDto
}
