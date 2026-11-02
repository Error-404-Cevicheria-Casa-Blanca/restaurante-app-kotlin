package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.CatalogoApi
import com.lacasablanca.mesero.data.remote.PedidosApi
import com.lacasablanca.mesero.data.remote.dto.CategoriaDto
import com.lacasablanca.mesero.data.remote.dto.CrearPedidoRequest
import com.lacasablanca.mesero.data.remote.dto.PedidoResponseDto
import com.lacasablanca.mesero.data.remote.dto.ProductoDto
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.ItemPedido
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal

/** Contrato del catalogo y del POST /pedidos contra la API real y los fakes. */
class CatalogoRepositoryTest {

    private val catalogoApi = mockk<CatalogoApi>()
    private val pedidosApi = mockk<PedidosApi>()

    @Test
    fun `mapea categorias y productos del backend java`() = runTest {
        coEvery { catalogoApi.listarCategorias() } returns listOf(
            CategoriaDto(id = 1, nombre = "Entradas"),
            CategoriaDto(id = 2, nombre = "Platos de Fondo")
        )
        coEvery { catalogoApi.listarProductos() } returns listOf(
            ProductoDto(
                id = 10,
                nombre = "Ceviche Clásico",
                descripcion = "Pescado fresco marinado en limón",
                precio = BigDecimal("48.00"),
                disponible = true,
                categoriaId = 2,
                imagenUrl = "https://lacasablanca.pe/ceviche.jpg",
                estacion = "BARRA_FRIA"
            )
        )

        val repositorio = RealCatalogoRepository(catalogoApi)

        when (val categorias = repositorio.listarCategorias()) {
            is Resultado.Exito -> {
                assertEquals(2, categorias.datos.size)
                assertEquals("Entradas", categorias.datos.first().nombre)
            }

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${categorias.error}")
        }

        when (val productos = repositorio.listarProductos()) {
            is Resultado.Exito -> {
                val producto = productos.datos.single()
                assertEquals(10L, producto.id)
                assertEquals(2L, producto.categoriaId)
                assertEquals("BARRA_FRIA", producto.estacion)
                assertEquals("https://lacasablanca.pe/ceviche.jpg", producto.imagenUrl)
                assertEquals("48.00", producto.precio.toPlainString())
                assertTrue(producto.disponible)
            }

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${productos.error}")
        }
    }

    @Test
    fun `un 401 en el catalogo se traduce a sesion expirada`() = runTest {
        coEvery { catalogoApi.listarCategorias() } throws errorHttp(401)

        val resultado = RealCatalogoRepository(catalogoApi).listarCategorias()

        assertEquals(ErrorApp.SESION_EXPIRADA, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un fallo de red en productos se traduce a error de red`() = runTest {
        coEvery { catalogoApi.listarProductos() } throws IOException("sin red")

        val resultado = RealCatalogoRepository(catalogoApi).listarProductos()

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `el pedido envia snake_case y mapea la respuesta`() = runTest {
        val capturado = slot<CrearPedidoRequest>()
        coEvery { pedidosApi.crearPedido(capture(capturado)) } returns PedidoResponseDto(
            id = 23,
            mesaId = 7,
            estado = "PENDIENTE",
            subtotal = BigDecimal("81.36"),
            igv = BigDecimal("14.64"),
            total = BigDecimal("96.00")
        )

        val resultado = RealPedidoRepository(pedidosApi).enviarPedido(
            mesaId = 7L,
            items = listOf(
                ItemPedido(
                    productoId = 10L,
                    cantidad = 2,
                    notas = "",
                    precioUnitario = BigDecimal("48.00")
                )
            )
        )

        val request = capturado.captured
        assertEquals(7L, request.mesaId)
        assertEquals(1, request.items.size)
        assertEquals(10L, request.items.single().productoId)
        assertEquals(2, request.items.single().cantidad)
        assertNull(request.items.single().notas)

        when (resultado) {
            is Resultado.Exito -> {
                assertEquals(23L, resultado.datos.id)
                assertEquals("PENDIENTE", resultado.datos.estado)
                assertEquals("96.00", resultado.datos.total.toPlainString())
            }

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${resultado.error}")
        }
    }

    @Test
    fun `un 409 al enviar el pedido se traduce a conflicto`() = runTest {
        coEvery { pedidosApi.crearPedido(any()) } throws errorHttp(409)

        val resultado = RealPedidoRepository(pedidosApi).enviarPedido(
            mesaId = 7L,
            items = listOf(ItemPedido(10L, 1, "", BigDecimal("48.00")))
        )

        assertEquals(ErrorApp.CONFLICTO, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un pedido sin lineas no llama a la api`() = runTest {
        val resultado = RealPedidoRepository(pedidosApi).enviarPedido(7L, emptyList())

        assertEquals(ErrorApp.VALIDACION, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `el fake de pedidos aplica la formula de igv del backend`() = runTest {
        val resultado = FakePedidoRepository(latenciaMs = 0).enviarPedido(
            mesaId = 7L,
            items = listOf(
                ItemPedido(10L, 2, "", BigDecimal("48.00"))
            )
        )

        when (resultado) {
            is Resultado.Exito -> {
                assertEquals("96.00", resultado.datos.total.toPlainString())
                assertEquals("81.36", resultado.datos.subtotal.toPlainString())
                assertEquals("14.64", resultado.datos.igv.toPlainString())
            }

            is Resultado.Fallo -> fail("Se esperaba exito y llego ${resultado.error}")
        }
    }

    @Test
    fun `el fake de catalogo trae las 4 categorias y un plato no disponible`() = runTest {
        val repositorio = FakeCatalogoRepository(latenciaMs = 0)

        val categorias = (repositorio.listarCategorias() as Resultado.Exito).datos
        val productos = (repositorio.listarProductos() as Resultado.Exito).datos

        assertEquals(4, categorias.size)
        assertTrue(productos.any { !it.disponible })
        assertTrue(productos.any { it.nombre.contains("Ceviche", ignoreCase = true) })
    }

    private fun errorHttp(codigo: Int): HttpException {
        val cuerpo = "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(codigo, cuerpo))
    }
}
