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

    override suspend fun enviarPedido(
        mesaId: Long,
        items: List<ItemPedido>
    ): Resultado<PedidoEnviado> {
        delay(latenciaMs)

        if (mesaId <= 0L || items.isEmpty() || items.any { it.cantidad < 1 }) {
            return Resultado.Fallo(ErrorApp.VALIDACION)
        }

        val totalBruto = items.fold(BigDecimal.ZERO) { acumulado, item ->
            acumulado + item.precioUnitario.multiply(BigDecimal.valueOf(item.cantidad.toLong()))
        }
        val desglose = CalculoIgv.desglosar(totalBruto)
        consumoStore?.registrar(mesaId, desglose.total)

        return Resultado.Exito(
            PedidoEnviado(
                id = siguienteId++,
                estado = ESTADO_PENDIENTE,
                subtotal = desglose.subtotal,
                igv = desglose.igv,
                total = desglose.total
            )
        )
    }

    companion object {
        const val LATENCIA_SIMULADA_MS = 400L
        const val ESTADO_PENDIENTE = "PENDIENTE"
    }
}
