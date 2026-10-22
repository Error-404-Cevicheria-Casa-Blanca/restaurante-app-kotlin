package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.MesasApi
import com.lacasablanca.mesero.data.remote.dto.CambioEstadoDto
import com.lacasablanca.mesero.data.remote.dto.aDominio
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.errorDesdeCodigoHttp
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import retrofit2.HttpException
import java.io.IOException

/** Mesas contra la API real (GET /mesas y PATCH /mesas/{id}/estado). */
class RealMesaRepository(private val api: MesasApi) : MesaRepository {

    override suspend fun listarMesas(): Resultado<List<Mesa>> {
        return try {
            Resultado.Exito(api.listar().map { it.aDominio() })
        } catch (e: HttpException) {
            Resultado.Fallo(errorDesdeCodigoHttp(e.code()))
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }

    override suspend fun cambiarEstado(id: Long, estado: EstadoMesa): Resultado<Mesa> {
        return try {
            val actualizada = api.cambiarEstado(id, CambioEstadoDto(estado.valorApi))
            Resultado.Exito(actualizada.aDominio())
        } catch (e: HttpException) {
            Resultado.Fallo(errorDesdeCodigoHttp(e.code()))
        } catch (e: IOException) {
            Resultado.Fallo(ErrorApp.RED)
        } catch (e: Exception) {
            Resultado.Fallo(ErrorApp.DESCONOCIDO)
        }
    }
}
