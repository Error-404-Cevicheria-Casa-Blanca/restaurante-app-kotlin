package com.lacasablanca.mesero.ui.comanda

import com.lacasablanca.mesero.data.repository.CatalogoRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Pruebas del carrito (PRB-38): altas/bajas, agrupacion por categoria,
 * formula de IGV del backend (IGV = Total - Subtotal) y envio del pedido.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarritoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val catalogoRepository = mockk<CatalogoRepository>()
    private val pedidoRepository = mockk<PedidoRepository>()

    private val categorias = listOf(
        Categoria(1, "Entradas"),
        Categoria(2, "Platos de Fondo"),
        Categoria(4, "Postres")
    )

    private val ceviche = Producto(
        id = 10,
        nombre = "Ceviche Clásico",
        descripcion = "Pescado del día, camote y choclo.",
        precio = BigDecimal("48.00"),
        disponible = true,
        categoriaId = 2,
        imagenUrl = null,
        estacion = "BARRA_FRIA"
    )
    private val tiradito = Producto(
        id = 11,
        nombre = "Tiradito al Ají Amarillo",
        descripcion = "Láminas de corvina.",
        precio = BigDecimal("46.00"),
        disponible = true,
        categoriaId = 1,
        imagenUrl = null,
        estacion = "BARRA_FRIA"
    )
    private val suspiro = Producto(
        id = 12,
        nombre = "Suspiro a la Limeña",
        descripcion = "Postre tradicional.",
        precio = BigDecimal("18.00"),
        disponible = false,
        categoriaId = 4,
        imagenUrl = null,
        estacion = "POSTRES"
    )
    private val productos = listOf(ceviche, tiradito, suspiro)

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

    private fun crearViewModelSinStubs(): CarritoViewModel =
        CarritoViewModel(catalogoRepository, pedidoRepository, testDispatcher)

    private fun TestScope.escucharEventos(viewModel: CarritoViewModel): List<CarritoEvento> {
        val eventos = mutableListOf<CarritoEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventos.toList(eventos)
        }
        return eventos
    }

    @Test
    fun `carga el catalogo y lo agrupa por categoria`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        val estado = viewModel.estado.value
        assertFalse(estado.cargando)
        assertNull(estado.errorCatalogo)
        assertEquals(3, estado.categorias.size)
        assertEquals(3, estado.productos.size)

        val encabezados = estado.filas
            .filterIsInstance<FilaCatalogo.Encabezado>()
            .map { it.nombre }
        assertEquals(listOf("Entradas", "Platos de Fondo", "Postres"), encabezados)
        assertEquals(6, estado.filas.size)
    }

    @Test
    fun `agregar un plato crea la linea y suma cantidades`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche)
        viewModel.agregarProducto(ceviche)

        val item = viewModel.estado.value.items.single()
        assertEquals(10L, item.producto.id)
        assertEquals(2, item.cantidad)
        assertEquals("96.00", item.subtotal.toPlainString())
        assertEquals(2, viewModel.estado.value.cantidadItems)
    }

    @Test
    fun `aumentar y disminuir ajustan la cantidad`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche)
        viewModel.aumentarCantidad(ceviche.id)
        assertEquals(2, viewModel.estado.value.items.single().cantidad)

        viewModel.disminuirCantidad(ceviche.id)
        assertEquals(1, viewModel.estado.value.items.single().cantidad)
    }

    @Test
    fun `disminuir la ultima unidad elimina la linea`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(tiradito)
        viewModel.disminuirCantidad(tiradito.id)

        assertTrue(viewModel.estado.value.carritoVacio)
        assertEquals(0, viewModel.estado.value.cantidadItems)
    }

    @Test
    fun `eliminar quita solo el plato indicado`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche)
        viewModel.agregarProducto(tiradito)
        viewModel.eliminarProducto(ceviche.id)

        val items = viewModel.estado.value.items
        assertEquals(1, items.size)
        assertEquals(tiradito.id, items.single().producto.id)
    }

    @Test
    fun `no agrega platos no disponibles`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(suspiro)

        assertTrue(viewModel.estado.value.carritoVacio)
    }

    @Test
    fun `el igv se obtiene restando al total como en el backend`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche)
        viewModel.agregarProducto(ceviche)
        viewModel.agregarProducto(tiradito)

        val estado = viewModel.estado.value
        // 48*2 + 46 = 142.00; 142/1.18 = 120.34; IGV = 142 - 120.34 = 21.66
        assertEquals("142.00", estado.total.toPlainString())
        assertEquals("120.34", estado.subtotal.toPlainString())
        assertEquals("21.66", estado.igv.toPlainString())
        assertEquals(
            estado.total.toPlainString(),
            estado.subtotal.add(estado.igv).toPlainString()
        )
    }

    @Test
    fun `el igv de una sola linea de 48 soles tambien cuadra`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche)

        val estado = viewModel.estado.value
        assertEquals("48.00", estado.total.toPlainString())
        assertEquals("40.68", estado.subtotal.toPlainString())
        assertEquals("7.32", estado.igv.toPlainString())
    }

    @Test
    fun `filtrar por categoria deja solo esa categoria`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.filtrarCategoria(1L)

        val filas = viewModel.estado.value.filas
        assertEquals(2, filas.size)
        assertEquals("Entradas", (filas.first() as FilaCatalogo.Encabezado).nombre)
        assertEquals(tiradito.id, (filas.last() as FilaCatalogo.Fila).producto.id)

        viewModel.filtrarCategoria(null)
        assertEquals(6, viewModel.estado.value.filas.size)
    }

    @Test
    fun `enviar con exito vacia el carrito y emite el evento`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        val itemsEnviados = slot<List<ItemPedido>>()
        coEvery {
            pedidoRepository.enviarPedido(7L, capture(itemsEnviados))
        } returns Resultado.Exito(
            PedidoEnviado(
                id = 23,
                estado = "PENDIENTE",
                subtotal = BigDecimal("81.36"),
                igv = BigDecimal("14.64"),
                total = BigDecimal("96.00")
            )
        )

        viewModel.agregarProducto(ceviche)
        viewModel.agregarProducto(ceviche)
        viewModel.enviarPedido(7L)
        advanceUntilIdle()

        assertTrue(viewModel.estado.value.carritoVacio)
        assertTrue(viewModel.estado.value.items.isEmpty())
        assertFalse(viewModel.estado.value.enviando)
        assertTrue(eventos.any { it is CarritoEvento.PedidoEnviadoOk })

        assertEquals(1, itemsEnviados.captured.size)
        assertEquals(ceviche.id, itemsEnviados.captured.single().productoId)
        assertEquals(2, itemsEnviados.captured.single().cantidad)
        assertEquals("48.00", itemsEnviados.captured.single().precioUnitario.toPlainString())
        coVerify(exactly = 1) { pedidoRepository.enviarPedido(7L, any()) }
    }

    @Test
    fun `si el envio falla conserva el carrito y emite el error`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        coEvery { pedidoRepository.enviarPedido(7L, any()) } returns
            Resultado.Fallo(ErrorApp.RED)

        viewModel.agregarProducto(ceviche)
        viewModel.enviarPedido(7L)
        advanceUntilIdle()

        assertEquals(1, viewModel.estado.value.items.size)
        assertFalse(viewModel.estado.value.enviando)
        val error = eventos.filterIsInstance<CarritoEvento.ErrorAlEnviar>().single()
        assertEquals(ErrorApp.RED, error.error)
        assertTrue(eventos.none { it is CarritoEvento.PedidoEnviadoOk })
    }

    @Test
    fun `la nota se recorta y viaja en el pedido`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche)
        viewModel.actualizarNotas(ceviche.id, "  sin picante  ")
        assertEquals("sin picante", viewModel.estado.value.items.single().notas)

        val itemsEnviados = slot<List<ItemPedido>>()
        coEvery {
            pedidoRepository.enviarPedido(7L, capture(itemsEnviados))
        } returns Resultado.Fallo(ErrorApp.RED)

        viewModel.enviarPedido(7L)
        advanceUntilIdle()

        assertEquals("sin picante", itemsEnviados.captured.single().notas)
    }

    @Test
    fun `una nota en blanco se guarda vacia`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.agregarProducto(ceviche)
        viewModel.actualizarNotas(ceviche.id, "   ")

        assertEquals("", viewModel.estado.value.items.single().notas)
    }

    @Test
    fun `un carrito vacio no llama al repositorio de pedidos`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        viewModel.enviarPedido(7L)
        advanceUntilIdle()

        coVerify(exactly = 0) { pedidoRepository.enviarPedido(any(), any()) }
    }

    @Test
    fun `un error de red al cargar deja reintentar`() = runTest(testDispatcher) {
        coEvery { catalogoRepository.listarCategorias() } returns Resultado.Fallo(ErrorApp.RED)
        val viewModel = crearViewModelSinStubs()
        advanceUntilIdle()

        assertEquals(ErrorCarrito.RED, viewModel.estado.value.errorCatalogo)
        assertFalse(viewModel.estado.value.cargando)
        assertTrue(viewModel.estado.value.filas.isEmpty())

        coEvery { catalogoRepository.listarCategorias() } returns Resultado.Exito(categorias)
        coEvery { catalogoRepository.listarProductos() } returns Resultado.Exito(productos)
        viewModel.cargar()
        advanceUntilIdle()

        assertNull(viewModel.estado.value.errorCatalogo)
        assertEquals(6, viewModel.estado.value.filas.size)
    }

    @Test
    fun `sesion expirada al cargar emite el evento`() = runTest(testDispatcher) {
        coEvery { catalogoRepository.listarCategorias() } returns
            Resultado.Fallo(ErrorApp.SESION_EXPIRADA)
        val viewModel = crearViewModelSinStubs()
        val eventos = escucharEventos(viewModel)
        advanceUntilIdle()

        assertTrue(eventos.any { it is CarritoEvento.SesionExpirada })
        assertFalse(viewModel.estado.value.cargando)
        coVerify(exactly = 0) { catalogoRepository.listarProductos() }
    }
}
