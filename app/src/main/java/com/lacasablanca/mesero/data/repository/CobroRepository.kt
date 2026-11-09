package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.TrabajoImpresionDto
import com.lacasablanca.mesero.domain.Resultado

/** Unica fuente de datos de la cuenta, el cobro y la impresion de una mesa. */
interface CobroRepository {
    suspend fun obtenerCuenta(mesaId: Long): Resultado<CuentaResponse>
    suspend fun cobrar(mesaId: Long, request: CobrarRequest): Resultado<CobroResponse>

    /** Solicita la pre-cuenta y encola su impresion en la ticketera (PRB-73). */
    suspend fun imprimirPrecuenta(mesaId: Long): Resultado<CuentaResponse>

    /** Reencola la ultima pre-cuenta impresa (PRB-73). */
    suspend fun reimprimirPrecuenta(mesaId: Long): Resultado<TrabajoImpresionDto>
}
