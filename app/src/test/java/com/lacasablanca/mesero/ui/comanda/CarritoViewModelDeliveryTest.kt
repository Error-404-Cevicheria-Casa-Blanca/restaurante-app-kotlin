package com.lacasablanca.mesero.ui.comanda

import com.lacasablanca.mesero.data.repository.CatalogoRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.DestinoPedido
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.domain.model.Producto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
import java.math.BigDecimal

/**
 * PRB-94: el mismo catalogo/carrito, pero el envio va sin mesa con
 * `tipo_pedido = PARA_LLEVAR` y el nombre del cliente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarritoViewModelDeliveryTest {

    private val testDispatcher = StandardTestDispatcher()

    private val catalogoRepository = mockk<CatalogoRepository>()
    private val pedidoRepository = mockk<PedidoRepository>()

    private val categorias = listOf(Categoria(2, "Platos de Fondo"))
    private val ceviche = Producto(
        id = 10,
        nombre = "Ceviche Clásico",
        descripcion = "Pescado del día.",
        precio = BigDecimal("48.00"),
        disponible = true,
        categoriaId = 2,
        imagenUrl = null,
        estacion = "BARRA_FRIA"
    )

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(cliente: String = "Carlos Mendoza"): CarritoViewModel {
        coEvery { catalogoRepository.listarCategorias() } returns Resultado.Exito(categorias)
        coEvery { catalogoRepository.listarProductos() } returns Resultado.Exito(listOf(ceviche))
        return CarritoViewModel(
            catalogoRepository = catalogoRepository,
            pedidoRepository = pedidoRepository,
            ioDispatcher = testDispatcher,
            destino = DestinoPedido.ParaLlevar(cliente)
        )
    }

    private fun TestScope.escucharEventos(viewModel: CarritoViewModel): List<CarritoEvento> {
        val eventos = mutableListOf<CarritoEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventos.toList(eventos)
        }
        return eventos
    }

    private fun pedidoOk() = PedidoEnviado(
        id = 77L,
        estado = "PENDIENTE",
        subtotal = BigDecimal("40.68"),
        igv = BigDecimal("7.32"),
        total = BigDecimal("48.00")
    )

    @Test
    fun `el estado expone el destino para llevar y su cliente`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertTrue(estado.esParaLlevar)
        assertEquals("Carlos Mendoza", estado.clienteNombre)
    }

    @Test
    fun `confirmar envia sin mesa con el nombre del cliente`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        val itemsEnviados = slot<List<ItemPedido>>()
        val clienteEnviado = slot<String>()
        coEvery {
            pedidoRepository.enviarPedidoParaLlevar(
                capture(itemsEnviados),
                any(),
                capture(clienteEnviado)
            )
        } returns Resultado.Exito(pedidoOk())

        viewModel.agregarProducto(ceviche)
        viewModel.enviarPedido()
        advanceUntilIdle()

        assertEquals("Carlos Mendoza", clienteEnviado.captured)
        assertEquals(ceviche.id, itemsEnviados.captured.single().productoId)
        assertEquals(1, itemsEnviados.captured.single().cantidad)
        assertTrue(viewModel.estado.value.carritoVacio)
        assertTrue(eventos.any { it is CarritoEvento.PedidoEnviadoOk })
        // Nunca viaja por la ruta de mesa.
        coVerify(exactly = 0) { pedidoRepository.enviarPedido(any(), any(), any()) }
    }

    @Test
    fun `un fallo conserva el carrito y permite reintentar`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        coEvery {
            pedidoRepository.enviarPedidoParaLlevar(any(), any(), any())
        } returns Resultado.Fallo(ErrorApp.RED)

        viewModel.agregarProducto(ceviche)
        viewModel.enviarPedido()
        advanceUntilIdle()

        assertEquals(1, viewModel.estado.value.items.size)
        assertFalse(viewModel.estado.value.enviando)
        val error = eventos.filterIsInstance<CarritoEvento.ErrorAlEnviar>().single()
        assertEquals(ErrorApp.RED, error.error)
    }

    @Test
    fun `un nombre en blanco no envia el pedido`() = runTest(testDispatcher) {
        val viewModel = crearViewModel(cliente = "   ")
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche)
        viewModel.enviarPedido()
        advanceUntilIdle()

        coVerify(exactly = 0) {
            pedidoRepository.enviarPedidoParaLlevar(any(), any(), any())
        }
        coVerify(exactly = 0) { pedidoRepository.enviarPedido(any(), any(), any()) }
        assertEquals(1, viewModel.estado.value.items.size)
    }

    @Test
    fun `reintentar tras un fallo reutiliza la misma clave de idempotencia`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel()
            advanceUntilIdle()

            val claves = mutableListOf<String>()
            coEvery {
                pedidoRepository.enviarPedidoParaLlevar(any(), capture(claves), any())
            } returns Resultado.Fallo(ErrorApp.RED)

            viewModel.agregarProducto(ceviche)
            viewModel.enviarPedido()
            advanceUntilIdle()
            viewModel.enviarPedido()
            advanceUntilIdle()

            assertEquals(2, claves.size)
            assertTrue(claves.first().isNotBlank())
            assertEquals(claves[0], claves[1])
        }
}
