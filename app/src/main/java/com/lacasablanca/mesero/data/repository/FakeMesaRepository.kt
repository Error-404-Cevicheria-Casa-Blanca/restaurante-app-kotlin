package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.CuentaMesa
import com.lacasablanca.mesero.domain.model.DivisionMesa
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import kotlinx.coroutines.delay
import java.math.BigDecimal

/**
 * DATOS FAKE (no reales). Simula la API de mesas mientras el backend no esta
 * levantado: 13 mesas confirmadas por el equipo (primer piso 1-7, segundo piso 8-13)
 * con mezcla de los 4 estados.
 *
 * PRB-93: las operaciones de salon respetan las validaciones de estado de
 * `PedidoService` (origen no libre, destino disponible), pero no hay pedidos
 * reales que mover ni consumos que sumar; por eso los totales son 0.00 y los
 * ids de pedido son ficticios.
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

    override suspend fun dividirMesa(
        mesaOrigenId: Long,
        itemIds: List<Long>,
        mesaDestinoId: Long?
    ): Resultado<DivisionMesa> {
        delay(latenciaMs)
        if (itemIds.isEmpty()) return Resultado.Fallo(ErrorApp.VALIDACION)
        return aplicarDivision(mesaOrigenId, mesaDestinoId)
    }

    private fun aplicarDivision(
        mesaOrigenId: Long,
        mesaDestinoId: Long?
    ): Resultado<DivisionMesa> {
        val indiceOrigen = mesas.indexOfFirst { it.id == mesaOrigenId }
        if (indiceOrigen < 0) return Resultado.Fallo(ErrorApp.NO_ENCONTRADO)
        val origen = mesas[indiceOrigen]
        if (origen.estado == EstadoMesa.LIBRE) return Resultado.Fallo(ErrorApp.CONFLICTO)

        val destinoId = mesaDestinoId ?: mesaOrigenId
        val indiceDestino = mesas.indexOfFirst { it.id == destinoId }
        if (indiceDestino < 0) return Resultado.Fallo(ErrorApp.NO_ENCONTRADO)
        val destino = mesas[indiceDestino]
        if (destino.id != origen.id && !destino.disponibleParaRecibir) {
            return Resultado.Fallo(ErrorApp.CONFLICTO)
        }
        if (destino.id != origen.id) {
            mesas[indiceDestino] = destino.copy(estado = EstadoMesa.OCUPADA)
        }

        // DATOS FAKE: ids de pedido ficticios y total 0.00; el backend real
        // devuelve los ids y el total calculado con BigDecimal.
        return Resultado.Exito(
            DivisionMesa(
                mesaOrigenId = origen.id,
                mesaDestinoId = destino.id,
                pedidoOrigenId = 0L,
                pedidoNuevoId = 0L,
                totalCombinado = BigDecimal.ZERO
            )
        )
    }

    override suspend fun fusionarMesas(
        mesaDestinoId: Long,
        mesaOrigenId: Long
    ): Resultado<CuentaMesa> {
        delay(latenciaMs)
        if (mesaDestinoId == mesaOrigenId) return Resultado.Fallo(ErrorApp.CONFLICTO)

        val indiceDestino = mesas.indexOfFirst { it.id == mesaDestinoId }
        val indiceOrigen = mesas.indexOfFirst { it.id == mesaOrigenId }
        if (indiceDestino < 0 || indiceOrigen < 0) return Resultado.Fallo(ErrorApp.NO_ENCONTRADO)

        val destino = mesas[indiceDestino]
        val origen = mesas[indiceOrigen]
        if (!destino.tienePedidoActivo || !origen.tienePedidoActivo) {
            return Resultado.Fallo(ErrorApp.CONFLICTO)
        }
        mesas[indiceOrigen] = origen.copy(estado = EstadoMesa.LIBRE)
        // DATOS FAKE: el backend devuelve la cuenta real de la mesa destino.
        return Resultado.Exito(CuentaMesa(mesaId = destino.id, total = BigDecimal.ZERO))
    }

    override suspend fun transferirMesa(
        mesaOrigenId: Long,
        mesaDestinoId: Long
    ): Resultado<CuentaMesa> {
        delay(latenciaMs)
        if (mesaOrigenId == mesaDestinoId) return Resultado.Fallo(ErrorApp.CONFLICTO)

        val indiceOrigen = mesas.indexOfFirst { it.id == mesaOrigenId }
        val indiceDestino = mesas.indexOfFirst { it.id == mesaDestinoId }
        if (indiceOrigen < 0 || indiceDestino < 0) return Resultado.Fallo(ErrorApp.NO_ENCONTRADO)

        val origen = mesas[indiceOrigen]
        val destino = mesas[indiceDestino]
        if (origen.estado == EstadoMesa.LIBRE || !destino.disponibleParaRecibir) {
            return Resultado.Fallo(ErrorApp.CONFLICTO)
        }
        mesas[indiceDestino] = destino.copy(estado = origen.estado)
        mesas[indiceOrigen] = origen.copy(estado = EstadoMesa.LIBRE)
        // DATOS FAKE: el backend devuelve la cuenta real tras mover los pedidos.
        return Resultado.Exito(CuentaMesa(mesaId = destino.id, total = BigDecimal.ZERO))
    }

    /** El backend solo rechaza destinos OCUPADA o CUENTA_SOLICITADA. */
    private val Mesa.disponibleParaRecibir: Boolean
        get() = estado == EstadoMesa.LIBRE || estado == EstadoMesa.ATENDIDA

    /** El backend exige que ambas mesas tengan pedidos activos. */
    private val Mesa.tienePedidoActivo: Boolean
        get() = estado == EstadoMesa.OCUPADA || estado == EstadoMesa.ATENDIDA

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
