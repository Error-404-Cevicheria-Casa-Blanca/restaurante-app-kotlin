package com.lacasablanca.mesero.ui.mesas

import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.FakeMesaRepository
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MesasViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mesaRepository = mockk<MesaRepository>()
    private val authRepository = mockk<AuthRepository>(relaxed = true)

    private val mesas = FakeMesaRepository.MESAS_FAKE

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(): MesasViewModel {
        coEvery { authRepository.sesionGuardada() } returns Sesion(
            token = "token-prueba",
            usuario = Usuario(id = 1, nombre = "Mozo Prueba", rol = "MOZO")
        )
        return MesasViewModel(mesaRepository, authRepository, testDispatcher)
    }

    private fun TestScope.escucharEventos(viewModel: MesasViewModel): List<MesasEvento> {
        val eventos = mutableListOf<MesasEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventos.toList(eventos) }
        return eventos
    }

    @Test
    fun `carga las 13 mesas con zonas y contadores`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertFalse(estado.cargando)
        assertEquals(13, estado.mesas.size)
        assertEquals(listOf("Primer piso", "Segundo piso"), estado.zonas)
        assertEquals("Mozo Prueba", estado.nombreUsuario)
        assertEquals(13, estado.mesasVisibles.size)
        assertEquals(6, estado.contadores[EstadoMesa.LIBRE])
        assertEquals(3, estado.contadores[EstadoMesa.OCUPADA])
        assertEquals(2, estado.contadores[EstadoMesa.ATENDIDA])
        assertEquals(2, estado.contadores[EstadoMesa.CUENTA_SOLICITADA])
    }

    @Test
    fun `filtrar por zona recalcula visibles y contadores`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.filtrarZona("Primer piso")

        val estado = viewModel.estado.value
        assertEquals(7, estado.mesasVisibles.size)
        assertEquals(3, estado.contadores[EstadoMesa.LIBRE])
        assertEquals(2, estado.contadores[EstadoMesa.OCUPADA])
        assertEquals(1, estado.contadores[EstadoMesa.ATENDIDA])
        assertEquals(1, estado.contadores[EstadoMesa.CUENTA_SOLICITADA])
    }

    @Test
    fun `filtrar por estado deja solo las mesas de ese estado`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.filtrarEstado(EstadoMesa.OCUPADA)

        val ocupadas = viewModel.estado.value.mesasVisibles
        assertEquals(3, ocupadas.size)
        assertTrue(ocupadas.all { it.estado == EstadoMesa.OCUPADA })

        viewModel.filtrarEstado(null)
        assertEquals(13, viewModel.estado.value.mesasVisibles.size)
    }

    @Test
    fun `una mesa libre pasa a atendida y navega a comanda`() = runTest(testDispatcher) {
        val libre = mesas.first { it.estaLibre }
        val actualizada = libre.copy(estado = EstadoMesa.ATENDIDA)
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery { mesaRepository.cambiarEstado(libre.id, EstadoMesa.ATENDIDA) } returns
            Resultado.Exito(actualizada)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.seleccionarMesa(libre)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.IrAComanda && it.mesa.id == libre.id })
        assertEquals(
            EstadoMesa.ATENDIDA,
            viewModel.estado.value.mesas.first { it.id == libre.id }.estado
        )
        coVerify(exactly = 1) { mesaRepository.cambiarEstado(libre.id, EstadoMesa.ATENDIDA) }
    }

    @Test
    fun `una mesa ocupada avisa y no navega`() = runTest(testDispatcher) {
        val ocupada = mesas.first { it.estado == EstadoMesa.OCUPADA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.seleccionarMesa(ocupada)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.Aviso && it.tipo == AvisoMesas.MESA_NO_LIBRE })
        assertTrue(eventos.none { it is MesasEvento.IrAComanda })
        coVerify(exactly = 0) { mesaRepository.cambiarEstado(any(), any()) }
    }

    @Test
    fun `si el patch falla por conflicto avisa y recarga`() = runTest(testDispatcher) {
        val libre = mesas.first { it.estaLibre }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery { mesaRepository.cambiarEstado(libre.id, EstadoMesa.ATENDIDA) } returns
            Resultado.Fallo(ErrorApp.CONFLICTO)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.seleccionarMesa(libre)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.Aviso && it.tipo == AvisoMesas.MESA_TOMADA })
        assertTrue(eventos.none { it is MesasEvento.IrAComanda })
        coVerify(exactly = 2) { mesaRepository.listarMesas() }
    }

    @Test
    fun `un 401 al cargar cierra sesion y avisa`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Fallo(ErrorApp.SESION_EXPIRADA)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.cerrarSesion() }
        assertTrue(eventos.any { it is MesasEvento.SesionExpirada })
        assertFalse(viewModel.estado.value.cargando)
    }

    @Test
    fun `un error de red deja el estado listo para reintentar`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Fallo(ErrorApp.RED)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertFalse(estado.cargando)
        assertEquals(ErrorMesas.RED, estado.error)

        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        viewModel.cargar()
        advanceUntilIdle()

        assertFalse(viewModel.estado.value.cargando)
        assertEquals(null, viewModel.estado.value.error)
        assertEquals(13, viewModel.estado.value.mesas.size)
    }

    @Test
    fun `recargar al volver refresca el estado sin mostrar el progreso`() = runTest(testDispatcher) {
        val libre = mesas.first { it.estaLibre }
        val atendida = libre.copy(estado = EstadoMesa.ATENDIDA)
        coEvery { mesaRepository.listarMesas() } returnsMany listOf(
            Resultado.Exito(mesas),
            Resultado.Exito(mesas.map { if (it.id == libre.id) atendida else it })
        )

        val viewModel = crearViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.estado.value.cargando)

        viewModel.recargarAlVolver()
        assertFalse(viewModel.estado.value.cargando)
        advanceUntilIdle()

        assertEquals(
            EstadoMesa.ATENDIDA,
            viewModel.estado.value.mesas.first { it.id == libre.id }.estado
        )
        coVerify(exactly = 2) { mesaRepository.listarMesas() }
    }

    @Test
    fun `liberar una mesa atendida la devuelve a libre`() = runTest(testDispatcher) {
        val atendida = mesas.first { it.estado == EstadoMesa.ATENDIDA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) } returns
            Resultado.Exito(atendida.copy(estado = EstadoMesa.LIBRE))

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        assertEquals(
            EstadoMesa.LIBRE,
            viewModel.estado.value.mesas.first { it.id == atendida.id }.estado
        )
    }

    @Test
    fun `liberar una mesa libre no llama al repositorio`() = runTest(testDispatcher) {
        val libre = mesas.first { it.estaLibre }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.liberarMesa(libre.id)
        advanceUntilIdle()

        coVerify(exactly = 0) { mesaRepository.cambiarEstado(any(), any()) }
    }

    @Test
    fun `cerrar sesion avisa a la activity`() = runTest(testDispatcher) {
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.cerrarSesion()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.cerrarSesion() }
        assertTrue(eventos.any { it is MesasEvento.SesionCerrada })
    }

    @Test
    fun `sin sesion guardada avisa sesion expirada`() = runTest(testDispatcher) {
        coEvery { authRepository.sesionGuardada() } returns null

        val viewModel = MesasViewModel(mesaRepository, authRepository, testDispatcher)
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.SesionExpirada })
        assertFalse(viewModel.estado.value.cargando)
        coVerify(exactly = 0) { mesaRepository.listarMesas() }
    }
}
