package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import kotlinx.coroutines.delay

/**
 * DATOS FAKE (no reales). Simula la API de mesas mientras el backend no esta
 * levantado: 13 mesas confirmadas por el equipo (primer piso 1-7, segundo piso 8-13)
 * con mezcla de los 4 estados.
 */
class FakeMesaRepository(private val latenciaMs: Long = LATENCIA_SIMULADA_MS) : MesaRepository {

    private val mesas = MESAS_FAKE.toMutableList()

    override suspend fun listarMesas(): Resultado<List<Mesa>> {
        delay(latenciaMs)
        return Resultado.Exito(mesas.toList())
    }

    override suspend fun cambiarEstado(id: Long, estado: EstadoMesa): Resultado<Mesa> {
        delay(latenciaMs)
        val indice = mesas.indexOfFirst { it.id == id }
        if (indice < 0) return Resultado.Fallo(ErrorApp.NO_ENCONTRADO)

        val mesa = mesas[indice]
        if (estado == EstadoMesa.ATENDIDA && mesa.estado != EstadoMesa.LIBRE) {
            return Resultado.Fallo(ErrorApp.CONFLICTO)
        }
        val actualizada = mesa.copy(estado = estado)
        mesas[indice] = actualizada
        return Resultado.Exito(actualizada)
    }

    companion object {
        const val LATENCIA_SIMULADA_MS = 500L

        /** 7 mesas en primer piso + 6 en segundo piso = 13. */
        val MESAS_FAKE: List<Mesa> = listOf(
            Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE),
            Mesa(2, 2, 2, "Primer piso", EstadoMesa.LIBRE),
            Mesa(3, 3, 6, "Primer piso", EstadoMesa.OCUPADA),
            Mesa(4, 4, 4, "Primer piso", EstadoMesa.ATENDIDA),
            Mesa(5, 5, 2, "Primer piso", EstadoMesa.CUENTA_SOLICITADA),
            Mesa(6, 6, 6, "Primer piso", EstadoMesa.LIBRE),
            Mesa(7, 7, 4, "Primer piso", EstadoMesa.OCUPADA),
            Mesa(8, 8, 4, "Segundo piso", EstadoMesa.LIBRE),
            Mesa(9, 9, 6, "Segundo piso", EstadoMesa.CUENTA_SOLICITADA),
            Mesa(10, 10, 2, "Segundo piso", EstadoMesa.LIBRE),
            Mesa(11, 11, 4, "Segundo piso", EstadoMesa.ATENDIDA),
            Mesa(12, 12, 8, "Segundo piso", EstadoMesa.LIBRE),
            Mesa(13, 13, 6, "Segundo piso", EstadoMesa.OCUPADA)
        )
    }
}
