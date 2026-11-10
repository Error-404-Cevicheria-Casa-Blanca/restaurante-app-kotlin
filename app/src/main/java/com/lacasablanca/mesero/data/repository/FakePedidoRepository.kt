package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.CalculoIgv
import com.lacasablanca.mesero.domain.model.EstadoPlato
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.Modificador
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.domain.model.PedidoExistente
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

    /**
     * Pedido de demostracion mutable: anular una linea la quita y el cambio
     * se conserva si se vuelve a consultar la mesa.
     */
    private var pedidoActual: PedidoExistente = PEDIDO_DE_MESA_FAKE

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

        // Misma regla que PedidoService: el recargo de modificadores se suma
        // al precio unitario antes de multiplicar por la cantidad.
        val totalBruto = items.fold(BigDecimal.ZERO) { acumulado, item ->
            val precioUnitario = item.precioUnitario
                .add(Modificador.recargoTotal(item.modificadores))
            acumulado + precioUnitario.multiply(BigDecimal.valueOf(item.cantidad.toLong()))
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

    /**
     * DATOS FAKE (no reales). Simula `GET /api/v1/pedidos?mesa_id=X` para
     * probar el modo lectura de PRB-83 mientras PRB-82 no esta cerrado.
     */
    override suspend fun obtenerPedidoDeMesa(mesaId: Long): Resultado<PedidoExistente> {
        delay(latenciaMs)
        if (mesaId <= 0L) return Resultado.Fallo(ErrorApp.VALIDACION)
        return Resultado.Exito(pedidoActual)
    }

    /**
     * DATOS FAKE (no reales). Simula el POST de anulacion (PRB-77). La linea
     * [ITEM_YA_SERVIDO_FAKE] responde 409 para poder probar ese camino sin
     * backend; la otra linea se elimina de verdad del pedido de demostracion.
     */
    override suspend fun anularItem(pedidoId: Long, itemId: Long): Resultado<Unit> {
        delay(latenciaMs)
        if (pedidoId <= 0L || itemId <= 0L) return Resultado.Fallo(ErrorApp.VALIDACION)
        if (itemId == ITEM_YA_SERVIDO_FAKE) return Resultado.Fallo(ErrorApp.PLATO_YA_SERVIDO)

        val linea = pedidoActual.lineas.firstOrNull { it.itemId == itemId }
            ?: return Resultado.Fallo(ErrorApp.NO_ENCONTRADO)
        pedidoActual = pedidoActual.copy(lineas = pedidoActual.lineas - linea)
        return Resultado.Exito(Unit)
    }

    companion object {
        const val LATENCIA_SIMULADA_MS = 400L
        const val ESTADO_PENDIENTE = "PENDIENTE"

        /** Id del detalle fake del ceviche: se puede anular correctamente. */
        const val ITEM_CEVICHE_FAKE = 501L

        /** Id del detalle fake que simula un plato ya servido (responde 409). */
        const val ITEM_YA_SERVIDO_FAKE = 502L

        /**
         * Pedido ficticio de 2 lineas (2 ceviches + 1 chicha) = S/ 111.00.
         * Los estados permiten probar la pildora de PRB-76 sin backend: el
         * ceviche esta LISTO (pulso) y la chicha ya fue SERVIDA (responde 409
         * al intentar anularla).
         */
        val PEDIDO_DE_MESA_FAKE: PedidoExistente = PedidoExistente(
            pedidoId = 100L,
            estado = ESTADO_PENDIENTE,
            lineas = listOf(
                ItemCarrito(
                    producto = FakeCatalogoRepository.PRODUCTOS_FAKE.first { it.id == 10L },
                    cantidad = 2,
                    notas = "sin picante",
                    itemId = ITEM_CEVICHE_FAKE,
                    estadoPlato = EstadoPlato.LISTO,
                    // Datos fake: modificadores gratuitos para mostrar la
                    // lista en modo lectura sin alterar el total de 111.00.
                    modificadores = listOf(
                        Modificador("Sin cebolla", BigDecimal("0.00")),
                        Modificador("Extra limón", BigDecimal("0.00"))
                    )
                ),
                ItemCarrito(
                    producto = FakeCatalogoRepository.PRODUCTOS_FAKE.first { it.id == 13L },
                    cantidad = 1,
                    itemId = ITEM_YA_SERVIDO_FAKE,
                    estadoPlato = EstadoPlato.SERVIDO,
                    modificadores = listOf(Modificador("Sin hielo", BigDecimal("0.00")))
                )
            )
        )
    }
}
