package com.lacasablanca.mesero.data.local.offline

import com.google.gson.Gson
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.ItemPedidoRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRB-63: persistencia del outbox en Room (el DAO se sustituye por MockK). */
class RoomOutboxPedidoStoreTest {

    private val dao = mockk<PedidoPendienteDao>()
    private val store = RoomOutboxPedidoStore(dao, Gson(), reloj = { 123L })

    private val request = CrearPedidoRequest(
        mesaId = 7L,
        items = listOf(ItemPedidoRequest(productoId = 10L, cantidad = 2, notas = "sin picante")),
        clientRequestId = "uuid-1"
    )

    @Test
    fun `guardar serializa el request con su clave de idempotencia`() = runTest {
        val capturado = slot<PedidoPendienteEntity>()
        coEvery { dao.insertar(capture(capturado)) } returns 1L

        assertTrue(store.guardar(request))

        assertEquals("uuid-1", capturado.captured.clientRequestId)
        assertEquals(7L, capturado.captured.mesaId)
        assertEquals(123L, capturado.captured.creadoEn)
        assertTrue(capturado.captured.payload.contains("\"client_request_id\":\"uuid-1\""))
        assertTrue(capturado.captured.payload.contains("\"producto_id\":10"))
        assertTrue(capturado.captured.payload.contains("\"mesa_id\":7"))
    }

    @Test
    fun `guardar devuelve false si la base local falla`() = runTest {
        coEvery { dao.insertar(any()) } throws IllegalStateException("disco lleno")

        assertFalse(store.guardar(request))
    }

    @Test
    fun `pendientes devuelve la cola del dao`() = runTest {
        val pendiente = PedidoPendienteEntity(
            id = 1L,
            clientRequestId = "uuid-1",
            mesaId = 7L,
            payload = "{}",
            creadoEn = 1L
        )
        coEvery { dao.pendientes() } returns listOf(pendiente)

        assertEquals(1, store.pendientes().size)
    }

    @Test
    fun `eliminar y registrar fallo delegan en el dao`() = runTest {
        coEvery { dao.eliminar(1L) } just Runs
        coEvery { dao.registrarFallo(1L, "RED") } just Runs

        store.eliminar(1L)
        store.registrarFallo(1L, "RED")

        coVerify(exactly = 1) { dao.eliminar(1L) }
        coVerify(exactly = 1) { dao.registrarFallo(1L, "RED") }
    }
}
