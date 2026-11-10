package com.lacasablanca.mesero.ui.mesas

import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.FakeCatalogoRepository
import com.lacasablanca.mesero.data.repository.FakeMesaRepository
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.CuentaMesa
import com.lacasablanca.mesero.domain.model.DivisionMesa
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.PedidoExistente
import com.lacasablanca.mesero.domain.model.Sesion
import com.lacasablanca.mesero.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class MesasViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mesaRepository = mockk<MesaRepository>()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val pedidoRepository = mockk<PedidoRepository>()

    private val mesas = FakeMesaRepository.MESAS_FAKE

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    /**
     * El ViewModel ya no carga en `init`: la unica carga inicial es el primer
     * ciclo de `startPolling()` (PRB-42). En estos tests, que no arrancan el
     * bucle, se dispara esa primera lectura de forma explicita.
     */
    private fun crearViewModel(conPedidos: Boolean = false): MesasViewModel {
        coEvery { authRepository.sesionGuardada() } returns Sesion(
            token = "token-prueba",
            usuario = Usuario(id = 1, nombre = "Mozo Prueba", rol = "MOZO")
        )
        val viewModel = MesasViewModel(
            mesaRepository = mesaRepository,
            authRepository = authRepository,
            ioDispatcher = testDispatcher,
            pedidoRepository = pedidoRepository.takeIf { conPedidos }
        )
        viewModel.cargar()
        return viewModel
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
        assertEquals("MOZO", estado.rolUsuario)
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
    fun `una mesa ocupada abre la comanda en modo lectura`() = runTest(testDispatcher) {
        val ocupada = mesas.first { it.estado == EstadoMesa.OCUPADA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.seleccionarMesa(ocupada)
        advanceUntilIdle()

        assertTrue(
            eventos.any { it is MesasEvento.IrAComandaLectura && it.mesa.id == ocupada.id }
        )
        assertTrue(eventos.none { it is MesasEvento.Aviso })
        assertTrue(eventos.none { it is MesasEvento.IrAComanda })
        // Consultar una mesa ocupada no cambia su estado.
        coVerify(exactly = 0) { mesaRepository.cambiarEstado(any(), any()) }
    }

    @Test
    fun `una mesa atendida ofrece retomar la comanda o liberarla`() = runTest(testDispatcher) {
        val atendida = mesas.first { it.estado == EstadoMesa.ATENDIDA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.seleccionarMesa(atendida)
        advanceUntilIdle()

        assertTrue(
            eventos.any {
                it is MesasEvento.OpcionesMesaAtendida && it.mesa.id == atendida.id
            }
        )
        assertTrue(eventos.none { it is MesasEvento.Aviso })
        // Tocar la mesa no cambia su estado: el PATCH solo ocurre al liberar.
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

        val libresAntes = viewModel.estado.value.contadores[EstadoMesa.LIBRE] ?: 0
        val atendidasAntes = viewModel.estado.value.contadores[EstadoMesa.ATENDIDA] ?: 0

        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        assertEquals(
            EstadoMesa.LIBRE,
            viewModel.estado.value.mesas.first { it.id == atendida.id }.estado
        )
        assertEquals(libresAntes + 1, viewModel.estado.value.contadores[EstadoMesa.LIBRE])
        assertEquals(atendidasAntes - 1, viewModel.estado.value.contadores[EstadoMesa.ATENDIDA])
    }

    @Test
    fun `liberar una mesa atendida emite el evento de liberada`() = runTest(testDispatcher) {
        val atendida = mesas.first { it.estado == EstadoMesa.ATENDIDA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) } returns
            Resultado.Exito(atendida.copy(estado = EstadoMesa.LIBRE))

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        assertTrue(
            eventos.any { it is MesasEvento.MesaLiberada && it.mesaId == atendida.id }
        )
        coVerify(exactly = 1) { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) }
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
    fun `si el patch de liberar falla por red avisa y conserva atendida`() = runTest(testDispatcher) {
        val atendida = mesas.first { it.estado == EstadoMesa.ATENDIDA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) } returns
            Resultado.Fallo(ErrorApp.RED)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.Aviso && it.tipo == AvisoMesas.ERROR_LIBERAR })
        assertEquals(
            EstadoMesa.ATENDIDA,
            viewModel.estado.value.mesas.first { it.id == atendida.id }.estado
        )
    }

    @Test
    fun `liberar con sesion expirada cierra la sesion y avisa`() = runTest(testDispatcher) {
        val atendida = mesas.first { it.estado == EstadoMesa.ATENDIDA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery { mesaRepository.cambiarEstado(atendida.id, EstadoMesa.LIBRE) } returns
            Resultado.Fallo(ErrorApp.SESION_EXPIRADA)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.liberarMesa(atendida.id)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.SesionExpirada })
        coVerify(exactly = 1) { authRepository.cerrarSesion() }
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

    // ---------------------------------------------------------------------
    // PRB-93: dividir, fusionar y transferir.
    // ---------------------------------------------------------------------

    @Test
    fun `dividir una mesa exitosamente avisa y refresca el mapa`() = runTest(testDispatcher) {
        val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
        val destino = mesas.first { it.estaLibre }
        val division = DivisionMesa(
            mesaOrigenId = origen.id,
            mesaDestinoId = destino.id,
            pedidoOrigenId = 100L,
            pedidoNuevoId = 200L,
            totalCombinado = BigDecimal("96.00")
        )
        coEvery { mesaRepository.listarMesas() } returnsMany listOf(
            Resultado.Exito(mesas),
            Resultado.Exito(
                mesas.map { if (it.id == destino.id) it.copy(estado = EstadoMesa.OCUPADA) else it }
            )
        )
        coEvery {
            mesaRepository.dividirMesa(origen.id, listOf(501L, 502L), destino.id)
        } returns Resultado.Exito(division)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.dividirMesa(origen, listOf(501L, 502L), destino)
        advanceUntilIdle()

        val completada = eventos.filterIsInstance<MesasEvento.AccionMesaCompletada>().single()
        assertEquals(AccionMesa.DIVIDIR, completada.accion)
        assertEquals(origen.id, completada.mesaOrigen.id)
        assertEquals(destino.id, completada.mesaDestino?.id)
        assertEquals(2, completada.platos)
        assertEquals(BigDecimal("96.00"), completada.total)
        assertEquals(
            EstadoMesa.OCUPADA,
            viewModel.estado.value.mesas.first { it.id == destino.id }.estado
        )
        assertNull(viewModel.estado.value.accionEnCurso)
        coVerify(exactly = 2) { mesaRepository.listarMesas() }
    }

    @Test
    fun `fusionar mesas exitosamente libera la origen y refresca`() = runTest(testDispatcher) {
        val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
        val destino = mesas.first { it.estado == EstadoMesa.OCUPADA && it.id != origen.id }
        coEvery { mesaRepository.listarMesas() } returnsMany listOf(
            Resultado.Exito(mesas),
            Resultado.Exito(
                mesas.map { if (it.id == origen.id) it.copy(estado = EstadoMesa.LIBRE) else it }
            )
        )
        coEvery {
            mesaRepository.fusionarMesas(destino.id, origen.id)
        } returns Resultado.Exito(CuentaMesa(destino.id, BigDecimal("84.00")))

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.fusionarMesas(mesaDestino = destino, mesaOrigen = origen)
        advanceUntilIdle()

        val completada = eventos.filterIsInstance<MesasEvento.AccionMesaCompletada>().single()
        assertEquals(AccionMesa.FUSIONAR, completada.accion)
        assertEquals(BigDecimal("84.00"), completada.total)
        assertEquals(
            EstadoMesa.LIBRE,
            viewModel.estado.value.mesas.first { it.id == origen.id }.estado
        )
        coVerify(exactly = 2) { mesaRepository.listarMesas() }
    }

    @Test
    fun `transferir una mesa exitosamente avisa y refresca`() = runTest(testDispatcher) {
        val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
        val destino = mesas.first { it.estaLibre }
        coEvery { mesaRepository.listarMesas() } returnsMany listOf(
            Resultado.Exito(mesas),
            Resultado.Exito(
                mesas.map {
                    when (it.id) {
                        origen.id -> it.copy(estado = EstadoMesa.LIBRE)
                        destino.id -> it.copy(estado = EstadoMesa.OCUPADA)
                        else -> it
                    }
                }
            )
        )
        coEvery {
            mesaRepository.transferirMesa(origen.id, destino.id)
        } returns Resultado.Exito(CuentaMesa(destino.id, BigDecimal("48.00")))

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.transferirMesa(mesaOrigen = origen, mesaDestino = destino)
        advanceUntilIdle()

        val completada = eventos.filterIsInstance<MesasEvento.AccionMesaCompletada>().single()
        assertEquals(AccionMesa.TRANSFERIR, completada.accion)
        assertEquals(
            EstadoMesa.OCUPADA,
            viewModel.estado.value.mesas.first { it.id == destino.id }.estado
        )
        assertEquals(
            EstadoMesa.LIBRE,
            viewModel.estado.value.mesas.first { it.id == origen.id }.estado
        )
        coVerify(exactly = 2) { mesaRepository.listarMesas() }
    }

    @Test
    fun `un 409 al dividir avisa conflicto y refresca el mapa`() = runTest(testDispatcher) {
        val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
        val destino = mesas.first { it.estaLibre }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery {
            mesaRepository.dividirMesa(any(), any(), any())
        } returns Resultado.Fallo(ErrorApp.CONFLICTO)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.dividirMesa(origen, listOf(501L), destino)
        advanceUntilIdle()

        assertTrue(
            eventos.any { it is MesasEvento.Aviso && it.tipo == AvisoMesas.CONFLICTO_ACCION }
        )
        assertTrue(eventos.none { it is MesasEvento.AccionMesaCompletada })
        assertNull(viewModel.estado.value.accionEnCurso)
        coVerify(exactly = 2) { mesaRepository.listarMesas() }
    }

    @Test
    fun `un error de red al transferir avisa sin refrescar`() = runTest(testDispatcher) {
        val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
        val destino = mesas.first { it.estaLibre }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery {
            mesaRepository.transferirMesa(origen.id, destino.id)
        } returns Resultado.Fallo(ErrorApp.RED)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.transferirMesa(origen, destino)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.Aviso && it.tipo == AvisoMesas.ERROR_RED })
        assertNull(viewModel.estado.value.accionEnCurso)
        coVerify(exactly = 1) { mesaRepository.listarMesas() }
    }

    @Test
    fun `una sesion expirada al dividir cierra la sesion`() = runTest(testDispatcher) {
        val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
        val destino = mesas.first { it.estaLibre }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery {
            mesaRepository.dividirMesa(any(), any(), any())
        } returns Resultado.Fallo(ErrorApp.SESION_EXPIRADA)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.dividirMesa(origen, listOf(501L), destino)
        advanceUntilIdle()

        assertTrue(eventos.any { it is MesasEvento.SesionExpirada })
        coVerify(exactly = 1) { authRepository.cerrarSesion() }
    }

    @Test
    fun `preparar division entrega solo lineas con item_id y destinos validos`() =
        runTest(testDispatcher) {
            val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
            val producto = FakeCatalogoRepository.PRODUCTOS_FAKE.first()
            coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
            coEvery { pedidoRepository.obtenerPedidoDeMesa(origen.id) } returns
                Resultado.Exito(
                    PedidoExistente(
                        lineas = listOf(
                            ItemCarrito(producto = producto, cantidad = 2, itemId = 501L),
                            ItemCarrito(producto = producto, cantidad = 1, itemId = null)
                        )
                    )
                )

            val viewModel = crearViewModel(conPedidos = true)
            val eventos = escucharEventos(viewModel)
            advanceUntilIdle()

            viewModel.prepararDivision(origen)
            advanceUntilIdle()

            val evento = eventos.filterIsInstance<MesasEvento.MostrarDivision>().single()
            assertEquals(1, evento.lineas.size)
            assertEquals(501L, evento.lineas.single().itemId)
            assertTrue(evento.destinos.any { it.id == origen.id })
            assertTrue(
                evento.destinos
                    .filter { it.id != origen.id }
                    .none {
                        it.estado == EstadoMesa.OCUPADA ||
                            it.estado == EstadoMesa.CUENTA_SOLICITADA
                    }
            )
        }

    @Test
    fun `preparar division sin platos avisa y no abre el dialogo`() = runTest(testDispatcher) {
        val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
        coEvery { pedidoRepository.obtenerPedidoDeMesa(origen.id) } returns
            Resultado.Exito(PedidoExistente.VACIO)

        val viewModel = crearViewModel(conPedidos = true)
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.prepararDivision(origen)
        advanceUntilIdle()

        assertTrue(
            eventos.any { it is MesasEvento.Aviso && it.tipo == AvisoMesas.SIN_PLATOS_DIVIDIR }
        )
        assertTrue(eventos.none { it is MesasEvento.MostrarDivision })
    }

    @Test
    fun `preparar fusion solo ofrece mesas ocupadas o atendidas`() = runTest(testDispatcher) {
        val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
        coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        viewModel.prepararFusion(origen)
        advanceUntilIdle()

        val evento = eventos.filterIsInstance<MesasEvento.MostrarDestino>().single()
        assertEquals(AccionMesa.FUSIONAR, evento.accion)
        assertEquals(4, evento.destinos.size)
        assertTrue(
            evento.destinos.all {
                it.id != origen.id &&
                    (it.estado == EstadoMesa.OCUPADA || it.estado == EstadoMesa.ATENDIDA)
            }
        )
    }

    @Test
    fun `preparar transferencia solo ofrece mesas libres o atendidas`() =
        runTest(testDispatcher) {
            val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
            coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)

            val viewModel = crearViewModel()
            val eventos = escucharEventos(viewModel)
            advanceUntilIdle()

            viewModel.prepararTransferencia(origen)
            advanceUntilIdle()

            val evento = eventos.filterIsInstance<MesasEvento.MostrarDestino>().single()
            assertEquals(AccionMesa.TRANSFERIR, evento.accion)
            assertEquals(8, evento.destinos.size)
            assertTrue(
                evento.destinos.all {
                    it.id != origen.id &&
                        (it.estado == EstadoMesa.LIBRE || it.estado == EstadoMesa.ATENDIDA)
                }
            )
        }

    @Test
    fun `una segunda accion se ignora mientras la primera esta en curso`() =
        runTest(testDispatcher) {
            val origen = mesas.first { it.estado == EstadoMesa.OCUPADA }
            val destino = mesas.first { it.estaLibre }
            coEvery { mesaRepository.listarMesas() } returns Resultado.Exito(mesas)
            coEvery { mesaRepository.transferirMesa(origen.id, destino.id) } coAnswers {
                delay(1_000L)
                Resultado.Exito(CuentaMesa(destino.id, BigDecimal.ZERO))
            }

            val viewModel = crearViewModel()
            escucharEventos(viewModel)
            advanceUntilIdle()

            viewModel.transferirMesa(origen, destino)
            advanceTimeBy(10L)
            assertEquals(AccionMesa.TRANSFERIR, viewModel.estado.value.accionEnCurso)

            viewModel.transferirMesa(origen, destino)
            advanceUntilIdle()

            coVerify(exactly = 1) { mesaRepository.transferirMesa(origen.id, destino.id) }
        }
}
