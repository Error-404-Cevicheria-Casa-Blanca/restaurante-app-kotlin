package com.lacasablanca.mesero.ui.pedidos

import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.OrigenPedido
import com.lacasablanca.mesero.domain.model.PedidoActivo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PedidosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repositorio = mockk<PedidoRepository>()
    private val auth = mockk<AuthRepository>()

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun pedido(
        id: Long,
        tipo: OrigenPedido = OrigenPedido.MESA,
        estado: String = "PENDIENTE",
        mesaId: Long? = if (tipo == OrigenPedido.MESA) 5L else null,
        cliente: String? = if (tipo == OrigenPedido.MESA) null else "Cliente $id"
    ): PedidoActivo = PedidoActivo(
        id = id,
        mesaId = mesaId,
        tipoPedido = tipo,
        clienteNombre = cliente,
        estado = estado,
        total = BigDecimal("50.00")
    )

    private fun crearViewModel(): PedidosViewModel =
        PedidosViewModel(repositorio, auth, testDispatcher)

    @Test
    fun `carga y descarta estados no activos`() = runTest(testDispatcher) {
        coEvery { repositorio.listarPedidosActivos() } returns Resultado.Exito(
            listOf(
                pedido(1L, estado = "PENDIENTE"),
                pedido(2L, estado = "COBRADO"),
                pedido(3L, estado = "ANULADO"),
                pedido(4L, estado = "LISTO")
            )
        )

        val viewModel = crearViewModel()
        advanceUntilIdle()

        assertEquals(listOf(1L, 4L), viewModel.estado.value.pedidos.map { it.id })
    }

    @Test
    fun `filtra mesa y para llevar incluyendo delivery`() = runTest(testDispatcher) {
        coEvery { repositorio.listarPedidosActivos() } returns Resultado.Exito(
            listOf(
                pedido(1L, OrigenPedido.MESA),
                pedido(2L, OrigenPedido.PARA_LLEVAR),
                pedido(3L, OrigenPedido.DELIVERY)
            )
        )

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.filtrar(FiltroPedidos.MESA)
        assertEquals(1, viewModel.estado.value.pedidosVisibles.size)

        viewModel.filtrar(FiltroPedidos.PARA_LLEVAR)
        assertEquals(2, viewModel.estado.value.pedidosVisibles.size)

        viewModel.filtrar(FiltroPedidos.TODOS)
        assertEquals(3, viewModel.estado.value.pedidosVisibles.size)
    }

    @Test
    fun `error de red deja el estado de error`() = runTest(testDispatcher) {
        coEvery { repositorio.listarPedidosActivos() } returns
            Resultado.Fallo(ErrorApp.RED)

        val viewModel = crearViewModel()
        advanceUntilIdle()

        assertEquals(ErrorPedidos.RED, viewModel.estado.value.error)
        assertEquals(false, viewModel.estado.value.cargando)
    }

    @Test
    fun `cobrar un pedido sin mesa emite evento de cobro`() = runTest(testDispatcher) {
        val delivery = pedido(10L, OrigenPedido.DELIVERY, cliente = "Lucía")
        coEvery { repositorio.listarPedidosActivos() } returns Resultado.Exito(
            listOf(delivery)
        )

        val viewModel = crearViewModel()
        advanceUntilIdle()

        val evento = async { viewModel.eventos.first() }
        viewModel.cobrar(delivery)
        advanceUntilIdle()

        assertEquals(
            PedidosEvento.IrACobroPedido(delivery.id, delivery.clienteNombre),
            evento.await()
        )
    }

    @Test
    fun `cobrar un pedido de mesa no emite evento`() = runTest(testDispatcher) {
        val mesa = pedido(11L, OrigenPedido.MESA)
        coEvery { repositorio.listarPedidosActivos() } returns Resultado.Exito(
            listOf(mesa)
        )

        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.cobrar(mesa)
        advanceUntilIdle()

        val evento = withTimeoutOrNull(100) { viewModel.eventos.first() }
        assertNull(evento)
    }

    @Test
    fun `sesion expirada cierra sesion y emite evento`() = runTest(testDispatcher) {
        coEvery { repositorio.listarPedidosActivos() } returns
            Resultado.Fallo(ErrorApp.SESION_EXPIRADA)
        coEvery { auth.cerrarSesion() } returns Unit

        val viewModel = crearViewModel()
        advanceUntilIdle()

        assertEquals(PedidosEvento.SesionExpirada, viewModel.eventos.first())
        coVerify(exactly = 1) { auth.cerrarSesion() }
    }

    @Test
    fun `vacio es verdadero sin pedidos visibles`() = runTest(testDispatcher) {
        coEvery { repositorio.listarPedidosActivos() } returns
            Resultado.Exito(emptyList())

        val viewModel = crearViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.estado.value.vacio)
    }
}
