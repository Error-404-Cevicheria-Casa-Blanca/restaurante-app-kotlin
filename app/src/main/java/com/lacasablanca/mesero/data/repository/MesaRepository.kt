package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa

/** Unica fuente de datos del mapa de mesas para la UI. */
interface MesaRepository {
    suspend fun listarMesas(): Resultado<List<Mesa>>
    suspend fun cambiarEstado(id: Long, estado: EstadoMesa): Resultado<Mesa>
}
