package com.lacasablanca.mesero.ui.comanda

import com.lacasablanca.mesero.data.repository.CatalogoRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.Modificador
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.domain.model.Producto
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * PRB-92: seleccion de modificadores por plato, lineas separadas por
 * combinacion y recalculo de importes con BigDecimal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarritoViewModelModificadoresTest {

    private val testDispatcher = StandardTestDispatcher()
    private val catalogoRepository = mockk<CatalogoRepository>()
    private val pedidoRepository = mockk<PedidoRepository>()

    private val extraQueso = Modificador("Extra queso", BigDecimal("2.00"))
    private val sinCebolla = Modificador("Sin cebolla", BigDecimal("0.00"))
    private val extraLimon = Modificador("Extra limón", BigDecimal("0.00"))

    private val ceviche = Producto(
        id = 10,
        nombre = "Ceviche Clásico",
        descripcion = "Pescado del día, camote y choclo.",
        precio = BigDecimal("48.00"),
        disponible = true,
        categoriaId = 2,
        imagenUrl = null,
        estacion = "BARRA_FRIA",
        modificadores = listOf(sinCebolla, extraLimon, extraQueso)
    )

    private val tiradito = Producto(
        id = 11,
        nombre = "Tiradito al Ají Amarillo",
        descripcion = "Láminas de corvina.",
        precio = BigDecimal("46.00"),
        disponible = true,
        categoriaId = 1,
        imagenUrl = null,
        estacion = "BARRA_FRIA",
        modificadores = listOf(Modificador("Extra canchita", BigDecimal("1.50")))
    )

    private val categorias = listOf(
        Categoria(1, "Entradas"),
        Categoria(2, "Platos de Fondo")
    )
    private val productos = listOf(ceviche, tiradito)

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
        coEvery { catalogoRepository.listarProductos() } returns Resultado.Exito(productos)
        return CarritoViewModel(catalogoRepository, pedidoRepository, testDispatcher)
    }

    private fun pedidoOk(): PedidoEnviado = PedidoEnviado(
        id = 23L,
        estado = "PENDIENTE",
        subtotal = BigDecimal("42.37"),
        igv = BigDecimal("7.63"),
        total = BigDecimal("50.00")
    )

    @Test
    fun `un modificador con precio actualiza el subtotal y el total`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel()
            advanceUntilIdle()

            viewModel.agregarProducto(ceviche, listOf(extraQueso))

            val estado = viewModel.estado.value
            val item = estado.items.single()
            assertEquals("50.00", item.precioUnitarioConModificadores.toPlainString())
            assertEquals("50.00", item.subtotal.toPlainString())
            assertEquals("50.00", estado.total.toPlainString())
            assertEquals("42.37", estado.subtotal.toPlainString())
            assertEquals("7.63", estado.igv.toPlainString())
        }

    @Test
    fun `el recargo se aplica por unidad`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche, listOf(extraQueso))
        viewModel.aumentarCantidad(viewModel.estado.value.items.single())

        val estado = viewModel.estado.value
        assertEquals(2, estado.items.single().cantidad)
        assertEquals("100.00", estado.items.single().subtotal.toPlainString())
        assertEquals("100.00", estado.total.toPlainString())
    }

    @Test
    fun `un modificador gratuito no cambia el total`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche, listOf(sinCebolla, extraLimon))

        val estado = viewModel.estado.value
        assertEquals("48.00", estado.total.toPlainString())
        assertEquals(2, estado.items.single().modificadores.size)
    }

    @Test
    fun `dos combinaciones del mismo plato crean lineas separadas`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel()
            advanceUntilIdle()

            viewModel.agregarProducto(ceviche, listOf(extraQueso))
            viewModel.agregarProducto(ceviche, listOf(sinCebolla))

            val estado = viewModel.estado.value
            assertEquals(2, estado.items.size)
            assertEquals("50.00", estado.items[0].subtotal.toPlainString())
            assertEquals("48.00", estado.items[1].subtotal.toPlainString())
            assertEquals("98.00", estado.total.toPlainString())
        }

    @Test
    fun `la misma combinacion suma cantidad en una sola linea`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel()
            advanceUntilIdle()

            viewModel.agregarProducto(ceviche, listOf(extraQueso))
            viewModel.agregarProducto(ceviche, listOf(extraQueso))

            val estado = viewModel.estado.value
            assertEquals(1, estado.items.size)
            assertEquals(2, estado.items.single().cantidad)
            assertEquals("100.00", estado.total.toPlainString())
        }

    @Test
    fun `un modificador que el plato no ofrece se ignora`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(
            ceviche,
            listOf(Modificador("Extra langosta", BigDecimal("100.00")))
        )

        val estado = viewModel.estado.value
        assertTrue(estado.items.single().modificadores.isEmpty())
        assertEquals("48.00", estado.total.toPlainString())
    }

    @Test
    fun `operar una linea concreta no toca la otra combinacion`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel()
            advanceUntilIdle()

            viewModel.agregarProducto(ceviche, listOf(extraQueso))
            viewModel.agregarProducto(ceviche, listOf(sinCebolla))

            val sinCebollaItem = viewModel.estado.value.items[1]
            viewModel.aumentarCantidad(sinCebollaItem)
            assertEquals(1, viewModel.estado.value.items[0].cantidad)
            assertEquals(2, viewModel.estado.value.items[1].cantidad)

            viewModel.eliminarProducto(viewModel.estado.value.items[0])
            val restante = viewModel.estado.value.items.single()
            assertEquals(2, restante.cantidad)
            assertEquals(sinCebolla.nombre, restante.modificadores.single().nombre)
        }

    @Test
    fun `la nota se edita solo en la linea elegida`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche, listOf(extraQueso))
        viewModel.agregarProducto(ceviche, listOf(sinCebolla))

        viewModel.actualizarNotas(viewModel.estado.value.items[1], "sin limón")

        assertEquals("", viewModel.estado.value.items[0].notas)
        assertEquals("sin limón", viewModel.estado.value.items[1].notas)
    }

    @Test
    fun `la seleccion viaja en el ItemPedido enviado`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        val itemsEnviados = slot<List<ItemPedido>>()
        coEvery {
            pedidoRepository.enviarPedido(7L, capture(itemsEnviados), any())
        } returns Resultado.Exito(pedidoOk())

        viewModel.agregarProducto(ceviche, listOf(extraQueso, sinCebolla))
        viewModel.enviarPedido(7L)
        advanceUntilIdle()

        val enviado = itemsEnviados.captured.single()
        assertEquals(2, enviado.modificadores.size)
        assertEquals("Extra queso", enviado.modificadores[0].nombre)
        assertEquals("2.00", enviado.modificadores[0].precioAdicional.toPlainString())
        assertEquals("Sin cebolla", enviado.modificadores[1].nombre)
    }
}
