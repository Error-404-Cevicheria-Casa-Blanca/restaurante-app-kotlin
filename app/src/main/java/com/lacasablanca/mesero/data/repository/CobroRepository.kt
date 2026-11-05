package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.domain.Resultado

/** Unica fuente de datos de la cuenta y el cobro de una mesa. */
interface CobroRepository {
    suspend fun obtenerCuenta(mesaId: Long): Resultado<CuentaResponse>
    suspend fun cobrar(mesaId: Long, request: CobrarRequest): Resultado<CobroResponse>
}
