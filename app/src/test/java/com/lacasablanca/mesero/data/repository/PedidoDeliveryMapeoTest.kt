package com.lacasablanca.mesero.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lacasablanca.mesero.data.local.offline.OutboxPedidoStore
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.ItemPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.TipoPedido
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

/**
 * PRB-94: contrato del pedido para llevar con el backend de PRB-79.
 * `mesa_id` nulo, `tipo_pedido = PARA_LLEVAR` y `cliente_nombre`.
 */
class PedidoDeliveryMapeoTest {

    private val api = mockk<PedidosApi>()

    private fun items() = listOf(
        ItemPedido(
            productoId = 10L,
            cantidad = 2,
            notas = "Empaque térmico",
            precioUnitario = BigDecimal("48.00")
        )
    )

    private fun pedidoOk(id: Long = 99L) = PedidoResponseDto(
        id = id,
        estado = "PENDIENTE",
        total = BigDecimal("96.00")
    )

    @Test
    fun `el request para llevar serializa tipo_pedido y cliente_nombre sin mesa_id`() {
        val request = CrearPedidoRequest(
            mesaId = null,
            items = listOf(ItemPedidoRequest(productoId = 10L, cantidad = 2)),
            clientRequestId = "uuid-llevar",
            tipoPedido = TipoPedido.PARA_LLEVAR.name,
            clienteNombre = "Carlos Mendoza"
        )

        val json = JsonParser.parseString(Gson().toJson(request)).asJsonObject

        assertEquals("PARA_LLEVAR", json.get("tipo_pedido").asString)
        assertEquals("Carlos Mendoza", json.get("cliente_nombre").asString)
        // Gson omite los null: el backend interpreta mesa_id ausente como null.
        assertFalse(json.has("mesa_id"))
    }

    @Test
    fun `el repositorio real envia mesa_id nulo y tipo PARA_LLEVAR`() = runTest {
        val capturado = slot<CrearPedidoRequest>()
        coEvery { api.crearPedido(any(), capture(capturado)) } returns pedidoOk()
        val repositorio = RealPedidoRepository(api)

        val resultado = repositorio.enviarPedidoParaLlevar(
            items = items(),
            clientRequestId = "uuid-llevar",
            clienteNombre = "Carlos Mendoza"
        )

        assertTrue(resultado is Resultado.Exito)
        assertEquals(99L, (resultado as Resultado.Exito).datos.id)
        assertNull(capturado.captured.mesaId)
        assertEquals("PARA_LLEVAR", capturado.captured.tipoPedido)
        assertEquals("Carlos Mendoza", capturado.captured.clienteNombre)
        assertEquals(1, capturado.captured.items.size)
        coVerify(exactly = 1) { api.crearPedido("uuid-llevar", any()) }
    }

    @Test
    fun `un pedido de mesa conserva mesa_id y tipo SALON`() = runTest {
        val capturado = slot<CrearPedidoRequest>()
        coEvery { api.crearPedido(any(), capture(capturado)) } returns pedidoOk(id = 23L)
        val repositorio = RealPedidoRepository(api)

        repositorio.enviarPedido(7L, items(), "uuid-mesa")

        assertEquals(7L, capturado.captured.mesaId)
        assertEquals("SALON", capturado.captured.tipoPedido)
        assertNull(capturado.captured.clienteNombre)
    }

    @Test
    fun `un nombre en blanco no llama al backend`() = runTest {
        val repositorio = RealPedidoRepository(api)

        val resultado = repositorio.enviarPedidoParaLlevar(items(), "uuid-llevar", "   ")

        assertEquals(ErrorApp.VALIDACION, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 0) { api.crearPedido(any(), any()) }
    }

    @Test
    fun `un fallo de red del pedido para llevar se guarda en el outbox`() = runTest {
        val outbox = mockk<OutboxPedidoStore>()
        coEvery { api.crearPedido(any(), any()) } throws IOException("sin red")
        coEvery { outbox.guardar(any()) } returns true
        val repositorio = RealPedidoRepository(api, outbox)

        val resultado = repositorio.enviarPedidoParaLlevar(
            items = items(),
            clientRequestId = "uuid-llevar",
            clienteNombre = "Carlos Mendoza"
        )

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 1) {
            outbox.guardar(
                match {
                    it.mesaId == null &&
                        it.tipoPedido == "PARA_LLEVAR" &&
                        it.clienteNombre == "Carlos Mendoza"
                }
            )
        }
    }

    @Test
    fun `el fake para llevar calcula el total y no acumula consumo de mesa`() = runTest {
        val consumo = FakeConsumoStore()
        val fake = FakePedidoRepository(consumo, latenciaMs = 0)

        val resultado = fake.enviarPedidoParaLlevar(
            items = items(),
            clientRequestId = "uuid-llevar",
            clienteNombre = "Carlos Mendoza"
        )

        assertTrue(resultado is Resultado.Exito)
        assertEquals("96.00", (resultado as Resultado.Exito).datos.total.toPlainString())
        assertNull(consumo.total(1L))
    }
}
