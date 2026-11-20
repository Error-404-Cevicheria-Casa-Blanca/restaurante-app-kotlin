package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.OutboxPedidoStore
import com.lacasablanca.mesero.data.local.offline.PedidoPendienteEntity
import com.lacasablanca.mesero.data.local.offline.PedidoSync
import com.lacasablanca.mesero.domain.model.ErroresOutbox
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxRepositoryTest {

    private val store = mockk<OutboxPedidoStore>()
    private val sync = mockk<PedidoSync>()
    private val repositorio = DefaultOutboxRepository(store, sync)

    private fun pendiente(id: Long, error: String?) = PedidoPendienteEntity(
        id = id,
        clientRequestId = "uuid-$id",
        mesaId = 7L,
        payload = "{}",
        creadoEn = 1L,
        ultimoError = error
    )

    @Test
    fun `resumen cuenta pendientes y conflictos`() = runTest {
        coEvery { store.pendientes() } returns listOf(
            pendiente(1L, ErroresOutbox.CONFLICTO),
            pendiente(2L, ErroresOutbox.RED)
        )

        val resumen = repositorio.resumen()

        assertEquals(2, resumen.total)
        assertEquals(1, resumen.conflictos)
    }

    @Test
    fun `descartar conflictos elimina solo los rechazados con 409`() = runTest {
        coEvery { store.pendientes() } returns listOf(
            pendiente(1L, ErroresOutbox.CONFLICTO),
            pendiente(2L, ErroresOutbox.RED)
        )
        coEvery { store.eliminar(1L) } just Runs

        repositorio.descartarConflictos()

        coVerify(exactly = 1) { store.eliminar(1L) }
        coVerify(exactly = 0) { store.eliminar(2L) }
    }

    @Test
    fun `sincronizar delega en PedidoSync`() = runTest {
        coEvery { sync.sincronizar() } returns true

        assertTrue(repositorio.sincronizar())
    }
}
