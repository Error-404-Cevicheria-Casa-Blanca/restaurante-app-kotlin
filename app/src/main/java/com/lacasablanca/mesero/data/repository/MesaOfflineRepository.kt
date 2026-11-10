package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.MesaDao
import com.lacasablanca.mesero.data.local.offline.aDominio
import com.lacasablanca.mesero.data.local.offline.aEntidad
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.CuentaMesa
import com.lacasablanca.mesero.domain.model.DivisionMesa
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa

/**
 * Cachea el mapa de mesas en Room (PRB-62): sin red se pinta la ultima copia.
 * Los cambios de estado y las operaciones de salon NO se encolan offline
 * (D34): requieren el backend para no descuadrar la caja. Tras una operacion
 * exitosa, el refresco del mapa actualiza la cache.
 */
class MesaOfflineRepository(
    private val remoto: MesaRepository,
    private val dao: MesaDao
) : MesaRepository {

    override suspend fun listarMesas(): Resultado<List<Mesa>> {
        return when (val resultado = remoto.listarMesas()) {
            is Resultado.Exito -> {
                dao.reemplazar(resultado.datos.map { it.aEntidad() })
                resultado
            }

            is Resultado.Fallo -> {
                if (resultado.error != ErrorApp.RED) return resultado
                val cache = dao.mesas().map { it.aDominio() }
                if (cache.isEmpty()) resultado else Resultado.Exito(cache)
            }
        }
    }

    override suspend fun cambiarEstado(id: Long, estado: EstadoMesa): Resultado<Mesa> {
        return when (val resultado = remoto.cambiarEstado(id, estado)) {
            is Resultado.Exito -> {
                dao.actualizarEstado(resultado.datos.id, resultado.datos.estado.valorApi)
                resultado
            }

            is Resultado.Fallo -> resultado
        }
    }

    override suspend fun dividirMesa(
        mesaOrigenId: Long,
        itemIds: List<Long>,
        mesaDestinoId: Long?
    ): Resultado<DivisionMesa> = remoto.dividirMesa(mesaOrigenId, itemIds, mesaDestinoId)

    override suspend fun fusionarMesas(
        mesaDestinoId: Long,
        mesaOrigenId: Long
    ): Resultado<CuentaMesa> = remoto.fusionarMesas(mesaDestinoId, mesaOrigenId)

    override suspend fun transferirMesa(
        mesaOrigenId: Long,
        mesaDestinoId: Long
    ): Resultado<CuentaMesa> = remoto.transferirMesa(mesaOrigenId, mesaDestinoId)
}
