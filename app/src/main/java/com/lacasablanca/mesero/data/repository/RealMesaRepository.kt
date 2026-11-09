package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.MesasApi
import com.lacasablanca.mesero.data.remote.dto.CambioEstadoDto
import com.lacasablanca.mesero.data.remote.dto.DividirMesaRequest
import com.lacasablanca.mesero.data.remote.dto.FusionarMesasRequest
import com.lacasablanca.mesero.data.remote.dto.TransferirMesaRequest
import com.lacasablanca.mesero.data.remote.dto.aCuentaMesa
import com.lacasablanca.mesero.data.remote.dto.aDominio
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import com.lacasablanca.mesero.domain.model.CuentaMesa
import com.lacasablanca.mesero.domain.model.DivisionMesa
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import retrofit2.HttpException
import java.io.IOException

/**
 * Mesas contra la API real: GET, PATCH de estado y las operaciones de salon
 * de PRB-75 (dividir, fusionar y transferir).
 */
class RealMesaRepository(private val api: MesasApi) : MesaRepository {

    override suspend fun listarMesas(): Resultado<List<Mesa>> =
        ejecutar { api.listar().map { it.aDominio() } }

    override suspend fun cambiarEstado(id: Long, estado: EstadoMesa): Resultado<Mesa> =
        ejecutar { api.cambiarEstado(id, CambioEstadoDto(estado.valorApi)).aDominio() }

    override suspend fun dividirMesa(
        mesaOrigenId: Long,
        itemIds: List<Long>,
        mesaDestinoId: Long?
    ): Resultado<DivisionMesa> {
        if (mesaOrigenId <= 0L || itemIds.isEmpty()) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }
        if (itemIds.any { it <= 0L } || (mesaDestinoId != null && mesaDestinoId <= 0L)) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }
        return ejecutar {
            api.dividirMesa(
                mesaOrigenId,
                DividirMesaRequest(itemIds = itemIds, mesaDestinoId = mesaDestinoId)
            ).aDominio()
        }
    }

    override suspend fun fusionarMesas(
        mesaDestinoId: Long,
        mesaOrigenId: Long
    ): Resultado<CuentaMesa> {
        if (mesaDestinoId <= 0L || mesaOrigenId <= 0L) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }
        return ejecutar {
            api.fusionarMesa(
                mesaDestinoId,
                FusionarMesasRequest(mesaOrigenId = mesaOrigenId)
            ).aCuentaMesa(mesaDestinoId)
        }
    }

    override suspend fun transferirMesa(
        mesaOrigenId: Long,
        mesaDestinoId: Long
    ): Resultado<CuentaMesa> {
        if (mesaOrigenId <= 0L || mesaDestinoId <= 0L) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }
        return ejecutar {
            api.transferirMesa(
                mesaOrigenId,
                TransferirMesaRequest(mesaDestinoId = mesaDestinoId)
            ).aCuentaMesa(mesaDestinoId)
        }
    }

    /**
     * Traduce errores HTTP/IO a [ErrorApp]. Un 409 conserva `CONFLICTO`: en
     * estas operaciones significa que alguna mesa cambio de estado.
     */
    private suspend fun <T> ejecutar(bloque: suspend () -> T): Resultado<T> =
        try {
            Resultado.Exito(bloque())
        } catch (e: HttpException) {
            Resultado.Fallo(errorDesdeCodigoHttp(e.code()))
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
}
