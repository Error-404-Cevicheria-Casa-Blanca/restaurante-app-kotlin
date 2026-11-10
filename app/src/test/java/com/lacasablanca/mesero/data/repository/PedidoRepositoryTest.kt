package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.ItemPedidoExistenteDto
import com.lacasablanca.mesero.data.remote.dto.PedidoExistenteDto
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.Modificador
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal

/** PRB-83: mapeo y traduccion de errores de GET /api/v1/pedidos?mesa_id=X. */
class PedidoRepositoryTest {

    private val api = mockk<PedidosApi>()
    private val repositorio = RealPedidoRepository(api)

    @Test
    fun `consulta el pedido de la mesa y lo mapea a dominio`() = runTest {
        coEvery { api.obtenerPedidoDeMesa(7L) } returns PedidoExistenteDto(
            id = 55L,
            mesaId = 7L,
            estado = "PENDIENTE",
            items = listOf(
                ItemPedidoExistenteDto(
                    productoId = 10L,
                    nombre = "Ceviche Clásico",
                    cantidad = 2,
                    precioUnitario = BigDecimal("48.00"),
                    notas = "sin picante"
                ),
                ItemPedidoExistenteDto(
                    productoId = 13L,
                    nombre = "Chicha Morada 1L",
                    cantidad = 1,
                    precioUnitario = BigDecimal("15.00")
                )
            )
        )

        val resultado = repositorio.obtenerPedidoDeMesa(7L)

        assertTrue(resultado is Resultado.Exito)
        val pedido = (resultado as Resultado.Exito).datos
        assertEquals(55L, pedido.pedidoId)
        assertEquals("PENDIENTE", pedido.estado)
        assertEquals(2, pedido.lineas.size)
        assertEquals("sin picante", pedido.lineas.first().notas)
        assertEquals("111.00", pedido.total.toPlainString())
        coVerify(exactly = 1) { api.obtenerPedidoDeMesa(7L) }
    }

    @Test
    fun `un 404 se traduce a pedido vacio`() = runTest {
        coEvery { api.obtenerPedidoDeMesa(7L) } throws errorHttp(404)

        val resultado = repositorio.obtenerPedidoDeMesa(7L)

        assertTrue(resultado is Resultado.Exito)
        assertTrue((resultado as Resultado.Exito).datos.lineas.isEmpty())
    }

    @Test
    fun `una respuesta vacia (204) se traduce a pedido vacio`() = runTest {
        coEvery { api.obtenerPedidoDeMesa(7L) } returns null

        val resultado = repositorio.obtenerPedidoDeMesa(7L)

        assertTrue(resultado is Resultado.Exito)
        assertTrue((resultado as Resultado.Exito).datos.lineas.isEmpty())
    }

    @Test
    fun `un fallo de red al consultar el pedido se traduce a red`() = runTest {
        coEvery { api.obtenerPedidoDeMesa(7L) } throws IOException("sin red")

        val resultado = repositorio.obtenerPedidoDeMesa(7L)

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un id invalido no consulta el pedido`() = runTest {
        val resultado = repositorio.obtenerPedidoDeMesa(0L)

        assertEquals(ErrorApp.VALIDACION, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 0) { api.obtenerPedidoDeMesa(any()) }
    }

    @Test
    fun `el fake devuelve el pedido de demostracion con lineas`() = runTest {
        val fake = FakePedidoRepository(latenciaMs = 0)

        val resultado = fake.obtenerPedidoDeMesa(3L)

        assertTrue(resultado is Resultado.Exito)
        val pedido = (resultado as Resultado.Exito).datos
        assertEquals(2, pedido.lineas.size)
        assertEquals("111.00", pedido.total.toPlainString())
    }

    // ---------------------------------------------------------------------
    // Anulacion de platos (PRB-77)
    // ---------------------------------------------------------------------

    @Test
    fun `anular un item llama al endpoint y devuelve exito`() = runTest {
        coEvery { api.anularItem(100L, 501L) } returns Response.success(Unit)

        val resultado = repositorio.anularItem(100L, 501L)

        assertTrue(resultado is Resultado.Exito)
        coVerify(exactly = 1) { api.anularItem(100L, 501L) }
    }

    @Test
    fun `un 409 al anular se traduce a plato ya servido`() = runTest {
        coEvery { api.anularItem(100L, 502L) } returns Response.error(
            409,
            "{\"mensaje\":\"servido\"}".toResponseBody("application/json".toMediaType())
        )

        val resultado = repositorio.anularItem(100L, 502L)

        assertEquals(ErrorApp.PLATO_YA_SERVIDO, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `ids invalidos no llaman al endpoint de anulacion`() = runTest {
        val resultado = repositorio.anularItem(0L, 501L)

        assertEquals(ErrorApp.VALIDACION, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 0) { api.anularItem(any(), any()) }
    }

    @Test
    fun `un fallo de red al anular se traduce a red`() = runTest {
        coEvery { api.anularItem(100L, 501L) } throws IOException("sin red")

        val resultado = repositorio.anularItem(100L, 501L)

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `el envio mapea los modificadores al request`() = runTest {
        val requestCapturado = slot<CrearPedidoRequest>()
        coEvery { api.crearPedido(any(), capture(requestCapturado)) } returns PedidoResponseDto(
            id = 23L,
            mesaId = 7L,
            estado = "PENDIENTE",
            total = BigDecimal("100.00")
        )

        val resultado = repositorio.enviarPedido(
            mesaId = 7L,
            items = listOf(
                ItemPedido(
                    productoId = 10L,
                    cantidad = 2,
                    notas = "sin picante",
                    precioUnitario = BigDecimal("48.00"),
                    modificadores = listOf(
                        Modificador("Extra queso", BigDecimal("2.00")),
                        Modificador("Sin cebolla", BigDecimal("0.00"))
                    )
                )
            ),
            clientRequestId = "clave-modificadores"
        )

        assertTrue(resultado is Resultado.Exito)
        val item = requestCapturado.captured.items.single()
        assertEquals(2, item.modificadores.size)
        assertEquals("Extra queso", item.modificadores[0].nombre)
        assertEquals("2.00", item.modificadores[0].precioAdicional.toPlainString())
    }

    private fun errorHttp(codigo: Int): HttpException {
        val cuerpo = "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(codigo, cuerpo))
    }
}
