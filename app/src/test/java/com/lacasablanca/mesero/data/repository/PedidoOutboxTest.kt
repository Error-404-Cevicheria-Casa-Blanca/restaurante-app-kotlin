package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.OutboxPedidoStore
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.ItemPedido
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal

/** PRB-63: el repositorio real encola el pedido cuando la red falla. */
class PedidoOutboxTest {

    private val api = mockk<PedidosApi>()
    private val outbox = mockk<OutboxPedidoStore>()
    private val repositorio = RealPedidoRepository(api, outbox)

    private fun items() = listOf(
        ItemPedido(
            productoId = 10L,
            cantidad = 2,
            notas = "sin picante",
            precioUnitario = BigDecimal("48.00")
        )
    )

    @Test
    fun `sin red guarda el pedido en el outbox`() = runTest {
        coEvery { api.crearPedido(any(), any()) } throws IOException("sin red")
        coEvery { outbox.guardar(any()) } returns true

        val resultado = repositorio.enviarPedido(7L, items(), "uuid-1")

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 1) {
            outbox.guardar(match { it.clientRequestId == "uuid-1" && it.mesaId == 7L })
        }
    }

    @Test
    fun `un 409 no se encola porque no es un fallo de red`() = runTest {
        coEvery { api.crearPedido(any(), any()) } throws errorHttp(409)

        val resultado = repositorio.enviarPedido(7L, items(), "uuid-1")

        assertEquals(ErrorApp.CONFLICTO_MESA, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 0) { outbox.guardar(any()) }
    }

    private fun errorHttp(codigo: Int): HttpException {
        val cuerpo = "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(codigo, cuerpo))
    }
}
