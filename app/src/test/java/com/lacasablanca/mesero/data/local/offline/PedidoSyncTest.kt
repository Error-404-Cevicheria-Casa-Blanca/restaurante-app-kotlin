package com.lacasablanca.mesero.data.local.offline

import com.google.gson.Gson
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.ItemPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** PRB-63: logica del SyncWorker (envio, reintento y errores definitivos). */
class PedidoSyncTest {

    private val outbox = mockk<OutboxPedidoStore>()
    private val api = mockk<PedidosApi>()
    private val gson = Gson()
    private val sync = PedidoSync(outbox, api, gson)

    private val request = CrearPedidoRequest(
        mesaId = 7L,
        items = listOf(ItemPedidoRequest(productoId = 10L, cantidad = 1)),
        clientRequestId = "uuid-1"
    )

    private fun pendiente(id: Long = 1L, payload: String = gson.toJson(request)) =
        PedidoPendienteEntity(
            id = id,
            clientRequestId = "uuid-1",
            mesaId = 7L,
            payload = payload,
            creadoEn = 1L
        )

    @Test
    fun `sin pendientes no llama al backend`() = runTest {
        coEvery { outbox.pendientes() } returns emptyList()

        assertTrue(sync.sincronizar())
        coVerify(exactly = 0) { api.crearPedido(any(), any()) }
    }

    @Test
    fun `un pedido pendiente se envia y se elimina de la cola`() = runTest {
        coEvery { outbox.pendientes() } returns listOf(pendiente())
        coEvery { api.crearPedido("uuid-1", request) } returns PedidoResponseDto(id = 55L)
        coEvery { outbox.eliminar(1L) } just Runs

        assertTrue(sync.sincronizar())
        coVerify(exactly = 1) { outbox.eliminar(1L) }
        coVerify(exactly = 0) { outbox.registrarFallo(any(), any()) }
    }

    @Test
    fun `sin red registra el fallo y pide reintento`() = runTest {
        coEvery { outbox.pendientes() } returns listOf(pendiente())
        coEvery { api.crearPedido(any(), any()) } throws IOException("sin red")
        coEvery { outbox.registrarFallo(1L, PedidoSync.ERROR_RED) } just Runs

        assertFalse(sync.sincronizar())
        coVerify(exactly = 1) { outbox.registrarFallo(1L, PedidoSync.ERROR_RED) }
        coVerify(exactly = 0) { outbox.eliminar(any()) }
    }

    @Test
    fun `un 409 no se reintenta pero se conserva para revision`() = runTest {
        coEvery { outbox.pendientes() } returns listOf(pendiente())
        coEvery { api.crearPedido(any(), any()) } throws errorHttp(409)
        coEvery { outbox.registrarFallo(1L, "HTTP 409") } just Runs

        assertTrue(sync.sincronizar())
        coVerify(exactly = 1) { outbox.registrarFallo(1L, "HTTP 409") }
        coVerify(exactly = 0) { outbox.eliminar(any()) }
    }

    @Test
    fun `un 500 si se reintenta`() = runTest {
        coEvery { outbox.pendientes() } returns listOf(pendiente())
        coEvery { api.crearPedido(any(), any()) } throws errorHttp(500)
        coEvery { outbox.registrarFallo(1L, "HTTP 500") } just Runs

        assertFalse(sync.sincronizar())
    }

    @Test
    fun `un payload corrupto se registra sin llamar al backend`() = runTest {
        coEvery { outbox.pendientes() } returns listOf(pendiente(payload = "{roto"))
        coEvery { outbox.registrarFallo(1L, PedidoSync.ERROR_PAYLOAD) } just Runs

        assertTrue(sync.sincronizar())
        coVerify(exactly = 0) { api.crearPedido(any(), any()) }
    }

    private fun errorHttp(codigo: Int): HttpException {
        val cuerpo = "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(codigo, cuerpo))
    }
}
