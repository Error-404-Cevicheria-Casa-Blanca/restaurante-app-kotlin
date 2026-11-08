package com.lacasablanca.mesero.data.remote.dto

import com.lacasablanca.mesero.data.repository.FakePedidoRepository
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoPlato
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * PRB-76: el estado de cada plato viaja del DTO de `GET /api/v1/pedidos` al
 * modelo de dominio. La pildora de la comanda solo necesita este mapeo.
 */
class PedidoExistenteMapeoTest {

    @Test
    fun `mapea el estado de cada linea del pedido`() {
        val pedido = PedidoExistenteDto(
            id = 100L,
            mesaId = 7L,
            items = listOf(
                item(id = 501L, productoId = 10L, estado = "PENDIENTE"),
                item(id = 502L, productoId = 11L, estado = "EN_PREPARACION"),
                item(id = 503L, productoId = 12L, estado = "LISTO"),
                item(id = 504L, productoId = 13L, estado = "SERVIDO"),
                item(id = 505L, productoId = 14L, estado = "ANULADO"),
                item(id = 506L, productoId = 15L, estado = "COCINANDO")
            )
        ).aDominio()

        assertEquals(
            listOf(
                EstadoPlato.PENDIENTE,
                EstadoPlato.EN_PREPARACION,
                EstadoPlato.LISTO,
                EstadoPlato.SERVIDO,
                EstadoPlato.ANULADO,
                EstadoPlato.EN_PREPARACION
            ),
            pedido.lineas.map { it.estadoPlato }
        )
    }

    @Test
    fun `sin estado en el json la linea queda sin estado`() {
        val pedido = PedidoExistenteDto(
            id = 100L,
            mesaId = 7L,
            items = listOf(item(id = 501L, productoId = 10L, estado = null))
        ).aDominio()

        assertNull(pedido.lineas.single().estadoPlato)
    }

    @Test
    fun `un estado desconocido no rompe el mapeo`() {
        val pedido = PedidoExistenteDto(
            id = 100L,
            mesaId = 7L,
            items = listOf(item(id = 501L, productoId = 10L, estado = "FRIO"))
        ).aDominio()

        assertEquals(EstadoPlato.DESCONOCIDO, pedido.lineas.single().estadoPlato)
    }

    @Test
    fun `el pedido fake trae estados para la demo`() = runTest {
        val resultado = FakePedidoRepository(latenciaMs = 0).obtenerPedidoDeMesa(3L)

        assertTrue(resultado is Resultado.Exito)
        val pedido = (resultado as Resultado.Exito).datos
        assertEquals(EstadoPlato.LISTO, pedido.lineas[0].estadoPlato)
        assertEquals(EstadoPlato.SERVIDO, pedido.lineas[1].estadoPlato)
    }

    private fun item(id: Long, productoId: Long, estado: String?): ItemPedidoExistenteDto =
        ItemPedidoExistenteDto(
            id = id,
            productoId = productoId,
            nombre = "Plato $productoId",
            cantidad = 1,
            precioUnitario = BigDecimal("10.00"),
            estado = estado
        )
}
