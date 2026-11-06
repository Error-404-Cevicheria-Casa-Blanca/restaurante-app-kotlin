package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.CalculoIgv
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import kotlinx.coroutines.delay
import java.math.BigDecimal

/**
 * DATOS FAKE (no reales). Simula POST /api/v1/pedidos sin servidor.
 * Aplica exactamente el mismo desglose de IGV que PedidoService.java.
 */
class FakePedidoRepository(
    private val consumoStore: FakeConsumoStore? = null,
    private val latenciaMs: Long = LATENCIA_SIMULADA_MS
) : PedidoRepository {

    private var siguienteId: Long = 1L

    /** Respuestas ya creadas por clave: simula la idempotencia del backend. */
    private val respuestasPorClave = mutableMapOf<String, PedidoEnviado>()

    override suspend fun enviarPedido(
        mesaId: Long,
        items: List<ItemPedido>,
        clientRequestId: String
    ): Resultado<PedidoEnviado> {
        delay(latenciaMs)

        if (mesaId <= 0L || items.isEmpty() || clientRequestId.isBlank()) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }
        if (items.any { it.cantidad < 1 }) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }

        respuestasPorClave[clientRequestId]?.let { return Resultado.Exito(it) }

        val totalBruto = items.fold(BigDecimal.ZERO) { acumulado, item ->
            acumulado + item.precioUnitario.multiply(BigDecimal.valueOf(item.cantidad.toLong()))
        }
        val desglose = CalculoIgv.desglosar(totalBruto)
        consumoStore?.registrar(mesaId, desglose.total)

        val pedido = PedidoEnviado(
            id = siguienteId++,
            estado = ESTADO_PENDIENTE,
            subtotal = desglose.subtotal,
            igv = desglose.igv,
            total = desglose.total
        )
        respuestasPorClave[clientRequestId] = pedido
        return Resultado.Exito(pedido)
    }

    companion object {
        const val LATENCIA_SIMULADA_MS = 400L
        const val ESTADO_PENDIENTE = "PENDIENTE"
    }
}
