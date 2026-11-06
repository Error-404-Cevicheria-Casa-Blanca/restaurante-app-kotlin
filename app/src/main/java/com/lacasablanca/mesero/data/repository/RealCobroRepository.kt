package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.CobroApi
import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import retrofit2.Response
import java.io.IOException

/** Cuenta y cobro contra la API real (PRB-55). */
class RealCobroRepository(private val api: CobroApi) : CobroRepository {

    override suspend fun obtenerCuenta(mesaId: Long): Resultado<CuentaResponse> =
        ejecutar { api.obtenerCuenta(mesaId) }

    override suspend fun cobrar(mesaId: Long, request: CobrarRequest): Resultado<CobroResponse> =
        ejecutar(errorConflicto = ErrorApp.CONFLICTO_COBRO) { api.cobrar(mesaId, request) }

    /**
     * [errorConflicto] distingue el significado del 409: en el cobro es
     * "la cuenta ya fue pagada"; en otros endpoints es un conflicto generico.
     */
    private suspend fun <T> ejecutar(
        errorConflicto: ErrorApp = ErrorApp.CONFLICTO,
        llamada: suspend () -> Response<T>
    ): Resultado<T> {
        return try {
            val respuesta = llamada()
            if (respuesta.isSuccessful) {
                val cuerpo = respuesta.body()
                if (cuerpo != null) {
                    Resultado.Exito(cuerpo)
                } else {
                    Resultado.Fallo(ErrorApp.SERVIDOR)
                }
            } else {
                val error = if (respuesta.code() == CODIGO_CONFLICTO) {
                    errorConflicto
                } else {
                    errorDesdeCodigoHttp(respuesta.code())
                }
                Resultado.Fallo(error)
            }
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    private companion object {
        const val CODIGO_CONFLICTO = 409
    }
}
