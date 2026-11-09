package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.MesaDao
import com.lacasablanca.mesero.data.local.offline.aDominio
import com.lacasablanca.mesero.data.local.offline.aEntidad
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa

/**
 * Cachea el mapa de mesas en Room (PRB-62): sin red se pinta la ultima copia.
 * Los cambios de estado NO se encolan offline (D34): el cobro y la operacion
 * de salon requieren el backend para no descuadrar la caja.
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
}
