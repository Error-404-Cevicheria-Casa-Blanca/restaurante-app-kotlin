package com.lacasablanca.mesero.ui.mesas

import com.lacasablanca.mesero.core.Conectividad
import com.lacasablanca.mesero.core.Sincronizacion
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.FakeMesaRepository
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.data.repository.OutboxRepository
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.PedidoPendiente
import com.lacasablanca.mesero.domain.model.ResumenOutbox
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MesasViewModelOfflineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mesaRepository = mockk<MesaRepository>()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val outboxRepository = mockk<OutboxRepository>(relaxed = true)
    private val mesas = FakeMesaRepository.MESAS_FAKE

    private class ConectividadFake(var online: Boolean) : Conectividad {
        override fun estaEnLineaAhora(): Boolean = online
    }

    private class SincronizacionFake : Sincronizacion {
        var programado = false
        var cancelado = false

        override fun programar() {
            programado = true
        }

        override fun cancelar() {
            cancelado = true
        }
    }

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear(
        conectividad: Conectividad = ConectividadFake(online = true),
        sincronizacion: Sincronizacion? = null
    ): MesasViewModel {
        coEvery { authRepository.sesionGuardada() } returns Sesion(
            token = "token-prueba",
            usuario = Usuario(id = 1, nombre = "Mozo Prueba", rol = "MOZO")
        )
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery { outboxRepository.resumen() } returns ResumenOutbox()
        return MesasViewModel(
            mesaRepository = mesaRepository,
            authRepository = authRepository,
            ioDispatcher = testDispatcher,
            outboxRepository = outboxRepository,
            conectividad = conectividad,
            sincronizacion = sincronizacion
        )
    }

    private fun TestScope.escucharEventos(viewModel: MesasViewModel): List<MesasEvento> {
        val eventos = mutableListOf<MesasEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventos.toList(eventos)
        }
        return eventos
    }

    @Test
    fun `sin red no se abre una mesa nueva`() = runTest(testDispatcher) {
        val conectividad = ConectividadFake(online = false)
        val viewModel = crear(conectividad)
        val eventos = escucharEventos(viewModel)
        viewModel.cargar()
        advanceUntilIdle()

        val libre = mesas.first { it.estado == EstadoMesa.LIBRE }
        viewModel.seleccionarMesa(libre)
        advanceUntilIdle()

        assertTrue(
            eventos.any {
                it is MesasEvento.Aviso &&
                    it.tipo == AvisoMesas.SIN_CONEXION &&
                    it.mesa?.id == libre.id
            }
        )
        coVerify(exactly = 0) { mesaRepository.cambiarEstado(any(), any()) }
    }

    @Test
    fun `con red la mesa libre se abre y navega a la comanda`() = runTest(testDispatcher) {
        val viewModel = crear()
        val eventos = escucharEventos(viewModel)
        viewModel.cargar()
        advanceUntilIdle()

        val libre = mesas.first { it.estado == EstadoMesa.LIBRE }
        coEvery {
            mesaRepository.cambiarEstado(libre.id, EstadoMesa.ATENDIDA)
        } returns Resultado.Exito(libre.copy(estado = EstadoMesa.ATENDIDA))

        viewModel.seleccionarMesa(libre)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.IrAComanda })
    }

    @Test
    fun `el estado expone los pendientes y conflictos del outbox`() = runTest(testDispatcher) {
        val viewModel = crear()
        coEvery { outboxRepository.resumen() } returns ResumenOutbox(
            listOf(
                PedidoPendiente(1L, 7L, 1L, 0, null),
                PedidoPendiente(2L, 8L, 2L, 1, "CONFLICTO")
            )
        )
        viewModel.cargar()
        advanceUntilIdle()

        assertEquals(2, viewModel.estado.value.pendientesSync)
        assertEquals(1, viewModel.estado.value.conflictosSync)
    }

    @Test
    fun `reintentar pendientes invoca al outbox y avisa el resultado`() = runTest(testDispatcher) {
        coEvery { outboxRepository.sincronizar() } returns true
        val viewModel = crear()
        val eventos = escucharEventos(viewModel)
        viewModel.cargar()
        advanceUntilIdle()

        viewModel.reintentarPendientes()
        advanceUntilIdle()

        coVerify(exactly = 1) { outboxRepository.sincronizar() }
        assertTrue(eventos.any { it is MesasEvento.SincronizacionTerminada && it.exito })
    }

    @Test
    fun `descartar conflictos limpia la cola y avisa`() = runTest(testDispatcher) {
        coEvery { outboxRepository.descartarConflictos() } just Runs
        val viewModel = crear()
        val eventos = escucharEventos(viewModel)
        viewModel.cargar()
        advanceUntilIdle()

        viewModel.descartarConflictos()
        advanceUntilIdle()

        coVerify(exactly = 1) { outboxRepository.descartarConflictos() }
        assertTrue(eventos.any { it is MesasEvento.ConflictosDescartados })
    }

    @Test
    fun `cerrar sesion cancela el worker de sincronizacion`() = runTest(testDispatcher) {
        val sincronizacion = SincronizacionFake()
        val viewModel = crear(sincronizacion = sincronizacion)
        viewModel.cargar()
        advanceUntilIdle()

        viewModel.cerrarSesion()
        advanceUntilIdle()

        assertTrue(sincronizacion.cancelado)
    }
}
