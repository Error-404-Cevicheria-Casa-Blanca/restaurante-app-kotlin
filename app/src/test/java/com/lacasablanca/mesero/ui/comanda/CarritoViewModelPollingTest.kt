package com.lacasablanca.mesero.ui.comanda

import com.lacasablanca.mesero.data.repository.CatalogoRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.EstadoPlato
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.domain.model.PedidoExistente
import com.lacasablanca.mesero.domain.model.Producto
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * PRB-90: polling del estado de los platos en modo lectura.
 *
 * El reloj virtual del [StandardTestDispatcher] permite comprobar el ciclo de
 * 5 segundos sin esperas reales. Nunca se usa `advanceUntilIdle` con el bucle
 * activo porque el polling es infinito por diseño.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarritoViewModelPollingTest {

    private val testDispatcher = StandardTestDispatcher()

    private val catalogoRepository = mockk<CatalogoRepository>()
    private val pedidoRepository = mockk<PedidoRepository>()

    private val categorias = listOf(
        Categoria(1, "Entradas"),
        Categoria(2, "Platos de Fondo"),
        Categoria(3, "Bebidas")
    )

    private val ceviche = Producto(
        id = 10,
        nombre = "Ceviche Clásico",
        descripcion = "",
        precio = BigDecimal("48.00"),
        disponible = true,
        categoriaId = 2,
        imagenUrl = null,
        estacion = "BARRA_FRIA"
    )
    private val tiradito = Producto(
        id = 11,
        nombre = "Tiradito al Ají Amarillo",
        descripcion = "",
        precio = BigDecimal("46.00"),
        disponible = true,
        categoriaId = 1,
        imagenUrl = null,
        estacion = "BARRA_FRIA"
    )
    private val chicha = Producto(
        id = 13,
        nombre = "Chicha Morada 1L",
        descripcion = "",
        precio = BigDecimal("15.00"),
        disponible = true,
        categoriaId = 3,
        imagenUrl = null,
        estacion = "BEBIDAS"
    )

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(): CarritoViewModel {
        coEvery { catalogoRepository.listarCategorias() } returns Resultado.Exito(categorias)
        coEvery {
            catalogoRepository.listarProductos()
        } returns Resultado.Exito(listOf(ceviche, tiradito, chicha))
        return CarritoViewModel(catalogoRepository, pedidoRepository, testDispatcher)
    }

    private fun pedidoExistente(estadoCeviche: EstadoPlato = EstadoPlato.LISTO): PedidoExistente =
        PedidoExistente(
            pedidoId = 100L,
            estado = "PENDIENTE",
            lineas = listOf(
                ItemCarrito(
                    producto = ceviche,
                    cantidad = 2,
                    notas = "sin picante",
                    itemId = 501L,
                    estadoPlato = estadoCeviche
                ),
                ItemCarrito(
                    producto = tiradito,
                    cantidad = 1,
                    itemId = 502L,
                    estadoPlato = EstadoPlato.EN_PREPARACION
                )
            )
        )

    private fun pedidoEnviado(): PedidoEnviado = PedidoEnviado(
        id = 200L,
        estado = "PENDIENTE",
        subtotal = BigDecimal("12.71"),
        igv = BigDecimal("2.29"),
        total = BigDecimal("15.00")
    )

    private fun TestScope.escucharEventos(viewModel: CarritoViewModel): List<CarritoEvento> {
        val eventos = mutableListOf<CarritoEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventos.toList(eventos)
        }
        return eventos
    }

    @Test
    fun `el polling refresca los estados y conserva los platos nuevos`() =
        runTest(testDispatcher) {
            coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returnsMany listOf(
                Resultado.Exito(pedidoExistente(EstadoPlato.PENDIENTE)),
                Resultado.Exito(pedidoExistente(EstadoPlato.LISTO))
            )

            val viewModel = crearViewModel()
            advanceUntilIdle()
            viewModel.activarModoLecura(7L)
            advanceUntilIdle()
            viewModel.agregarProducto(chicha)
            assertEquals(
                EstadoPlato.PENDIENTE,
                viewModel.estado.value.itemsExistentes.first().estadoPlato
            )

            viewModel.startPolling(7L)
            advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS)
            runCurrent()

            val estado = viewModel.estado.value
            // El estado del plato ya enviado se actualiza con el GET.
            assertEquals(EstadoPlato.LISTO, estado.itemsExistentes.first().estadoPlato)
            assertEquals(2, estado.itemsExistentes.size)
            // El plato nuevo que el mesero agregaba sigue en el carrito.
            assertEquals(1, estado.items.size)
            assertEquals(chicha.id, estado.items.single().producto.id)
            coVerify(exactly = 2) { pedidoRepository.obtenerPedidoDeMesa(7L) }
            viewModel.stopPolling()
        }

    @Test
    fun `el polling no toca cantidades ni notas de los platos nuevos`() =
        runTest(testDispatcher) {
            coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returnsMany listOf(
                Resultado.Exito(pedidoExistente(EstadoPlato.PENDIENTE)),
                Resultado.Exito(pedidoExistente(EstadoPlato.EN_PREPARACION))
            )

            val viewModel = crearViewModel()
            advanceUntilIdle()
            viewModel.activarModoLecura(7L)
            advanceUntilIdle()
            viewModel.agregarProducto(chicha)
            viewModel.aumentarCantidad(chicha.id)
            viewModel.actualizarNotas(chicha.id, "sin hielo")

            viewModel.startPolling(7L)
            advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS)
            runCurrent()

            val nuevo = viewModel.estado.value.items.single()
            assertEquals(chicha.id, nuevo.producto.id)
            assertEquals(2, nuevo.cantidad)
            assertEquals("sin hielo", nuevo.notas)
            assertEquals(
                EstadoPlato.EN_PREPARACION,
                viewModel.estado.value.itemsExistentes.first().estadoPlato
            )
            viewModel.stopPolling()
        }

    @Test
    fun `stopPolling cancela el refresco`() = runTest(testDispatcher) {
        coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returns
            Resultado.Exito(pedidoExistente())

        val viewModel = crearViewModel()
        advanceUntilIdle()
        viewModel.activarModoLecura(7L)
        advanceUntilIdle()

        viewModel.startPolling(7L)
        advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS)
        runCurrent()
        coVerify(exactly = 2) { pedidoRepository.obtenerPedidoDeMesa(7L) }

        viewModel.stopPolling()
        advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS * 3)
        runCurrent()

        coVerify(exactly = 2) { pedidoRepository.obtenerPedidoDeMesa(7L) }
    }

    @Test
    fun `enviar una comanda detiene el polling`() = runTest(testDispatcher) {
        coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returns
            Resultado.Exito(pedidoExistente())
        coEvery { pedidoRepository.enviarPedido(7L, any(), any()) } returns
            Resultado.Exito(pedidoEnviado())

        val viewModel = crearViewModel()
        advanceUntilIdle()
        viewModel.activarModoLecura(7L)
        advanceUntilIdle()
        viewModel.agregarProducto(chicha)

        viewModel.startPolling(7L)
        runCurrent()
        viewModel.enviarPedido(7L)
        runCurrent()

        // Tras enviar, ningun ciclo mas del polling consulta el pedido.
        advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS * 3)
        runCurrent()

        coVerify(exactly = 1) { pedidoRepository.obtenerPedidoDeMesa(7L) }
        assertTrue(viewModel.estado.value.items.isEmpty())
    }

    @Test
    fun `si el envio falla por red el polling se reanuda`() = runTest(testDispatcher) {
        coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returns
            Resultado.Exito(pedidoExistente())
        coEvery { pedidoRepository.enviarPedido(7L, any(), any()) } returns
            Resultado.Fallo(ErrorApp.RED)

        val viewModel = crearViewModel()
        advanceUntilIdle()
        viewModel.activarModoLecura(7L)
        advanceUntilIdle()
        viewModel.agregarProducto(chicha)

        viewModel.startPolling(7L)
        runCurrent()
        viewModel.enviarPedido(7L)
        runCurrent()

        // El envio no salio: la pantalla sigue en modo lectura y se refresca.
        advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS)
        runCurrent()
        coVerify(exactly = 2) { pedidoRepository.obtenerPedidoDeMesa(7L) }
        viewModel.stopPolling()
    }

    @Test
    fun `si la pantalla sale durante un envio fallido el polling no se reanuda`() =
        runTest(testDispatcher) {
            coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returns
                Resultado.Exito(pedidoExistente())
            coEvery { pedidoRepository.enviarPedido(7L, any(), any()) } coAnswers {
                delay(1_000L)
                Resultado.Fallo(ErrorApp.RED)
            }

            val viewModel = crearViewModel()
            advanceUntilIdle()
            viewModel.activarModoLecura(7L)
            advanceUntilIdle()
            viewModel.agregarProducto(chicha)

            viewModel.startPolling(7L)
            runCurrent()
            viewModel.enviarPedido(7L)
            runCurrent()

            // onStop mientras el POST sigue en vuelo.
            viewModel.stopPolling()
            advanceTimeBy(1_000L)
            runCurrent()

            // El POST fallo fuera de pantalla: no debe reabrir el bucle.
            advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS * 3)
            runCurrent()
            coVerify(exactly = 1) { pedidoRepository.obtenerPedidoDeMesa(7L) }
        }

    @Test
    fun `un fallo de red del polling conserva la comanda pintada`() =
        runTest(testDispatcher) {
            coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returnsMany listOf(
                Resultado.Exito(pedidoExistente()),
                Resultado.Fallo(ErrorApp.RED)
            )

            val viewModel = crearViewModel()
            advanceUntilIdle()
            viewModel.activarModoLecura(7L)
            advanceUntilIdle()
            viewModel.agregarProducto(chicha)

            viewModel.startPolling(7L)
            advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS)
            runCurrent()

            val estado = viewModel.estado.value
            assertEquals(2, estado.itemsExistentes.size)
            assertEquals(1, estado.items.size)
            assertNull(estado.errorPedidoExistente)
            assertTrue(estado.modoLectura)
            viewModel.stopPolling()
        }

    @Test
    fun `una sesion expirada en el polling corta el bucle y avisa`() =
        runTest(testDispatcher) {
            coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returnsMany listOf(
                Resultado.Exito(pedidoExistente()),
                Resultado.Fallo(ErrorApp.SESION_EXPIRADA)
            )

            val viewModel = crearViewModel()
            val eventos = escucharEventos(viewModel)
            advanceUntilIdle()
            viewModel.activarModoLecura(7L)
            advanceUntilIdle()

            viewModel.startPolling(7L)
            advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS)
            runCurrent()

            assertTrue(eventos.any { it is CarritoEvento.SesionExpirada })
            advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS * 2)
            runCurrent()
            coVerify(exactly = 2) { pedidoRepository.obtenerPedidoDeMesa(7L) }
        }

    @Test
    fun `llamar dos veces a startPolling no duplica el bucle`() = runTest(testDispatcher) {
        coEvery { pedidoRepository.obtenerPedidoDeMesa(7L) } returns
            Resultado.Exito(pedidoExistente())

        val viewModel = crearViewModel()
        advanceUntilIdle()
        viewModel.activarModoLecura(7L)
        advanceUntilIdle()

        viewModel.startPolling(7L)
        viewModel.startPolling(7L)
        advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS)
        runCurrent()

        coVerify(exactly = 2) { pedidoRepository.obtenerPedidoDeMesa(7L) }
        viewModel.stopPolling()
    }

    @Test
    fun `sin modo lectura el polling no consulta el pedido`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.startPolling(7L)
        advanceTimeBy(CarritoViewModel.INTERVALO_POLLING_MS * 3)
        runCurrent()

        coVerify(exactly = 0) { pedidoRepository.obtenerPedidoDeMesa(any()) }
    }
}
