package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.offline.CatalogoDao
import com.lacasablanca.mesero.data.local.offline.CategoriaEntity
import com.lacasablanca.mesero.data.local.offline.ProductoEntity
import com.lacasablanca.mesero.data.local.offline.aDominio
import com.lacasablanca.mesero.data.local.offline.aEntidad
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.Modificador
import com.lacasablanca.mesero.domain.model.Producto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** PRB-62: cache de catalogo con Room y respaldo offline. */
class CatalogoOfflineRepositoryTest {

    private val remoto = mockk<CatalogoRepository>()
    private val dao = mockk<CatalogoDao>()
    private val repositorio = CatalogoOfflineRepository(remoto, dao)

    private val producto = Producto(
        id = 10L,
        nombre = "Ceviche Clásico",
        descripcion = "Pescado del día",
        precio = BigDecimal("48.00"),
        disponible = true,
        categoriaId = 1L,
        imagenUrl = null,
        estacion = "BARRA_FRIA"
    )

    private val productoEntity = ProductoEntity(
        id = 10L,
        nombre = "Ceviche Clásico",
        descripcion = "Pescado del día",
        precio = "48.00",
        disponible = true,
        categoriaId = 1L,
        imagenUrl = null,
        estacion = "BARRA_FRIA"
    )

    @Test
    fun `con red devuelve los productos y actualiza la cache`() = runTest {
        coEvery { remoto.listarProductos() } returns Resultado.Exito(listOf(producto))
        coEvery { dao.reemplazarProductos(any()) } just Runs

        val resultado = repositorio.listarProductos()

        assertTrue(resultado is Resultado.Exito)
        assertEquals(10L, (resultado as Resultado.Exito).datos.single().id)
        coVerify(exactly = 1) {
            dao.reemplazarProductos(match { it.single().precio == "48.00" })
        }
    }

    @Test
    fun `sin red devuelve los productos cacheados`() = runTest {
        coEvery { remoto.listarProductos() } returns Resultado.Fallo(ErrorApp.RED)
        coEvery { dao.productos() } returns listOf(productoEntity)

        val resultado = repositorio.listarProductos()

        assertTrue(resultado is Resultado.Exito)
        val datos = (resultado as Resultado.Exito).datos.single()
        assertEquals(10L, datos.id)
        assertEquals(BigDecimal("48.00"), datos.precio)
        assertEquals("BARRA_FRIA", datos.estacion)
    }

    @Test
    fun `sin red y sin cache conserva el error de red`() = runTest {
        coEvery { remoto.listarProductos() } returns Resultado.Fallo(ErrorApp.RED)
        coEvery { dao.productos() } returns emptyList()

        val resultado = repositorio.listarProductos()

        assertEquals(ErrorApp.RED, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un error que no es de red no consulta la cache`() = runTest {
        coEvery { remoto.listarProductos() } returns Resultado.Fallo(ErrorApp.SERVIDOR)

        val resultado = repositorio.listarProductos()

        assertEquals(ErrorApp.SERVIDOR, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 0) { dao.productos() }
    }

    @Test
    fun `los modificadores sobreviven a la cache local`() {
        val conModificadores = producto.copy(
            modificadores = listOf(
                Modificador("Extra queso", BigDecimal("2.00")),
                Modificador("Sin cebolla", BigDecimal("0.00"))
            )
        )

        val recuperado = conModificadores.aEntidad().aDominio()

        assertEquals(2, recuperado.modificadores.size)
        assertEquals("Extra queso", recuperado.modificadores[0].nombre)
        assertEquals("2.00", recuperado.modificadores[0].precioAdicional.toPlainString())
        assertEquals("Sin cebolla", recuperado.modificadores[1].nombre)
    }

    @Test
    fun `las categorias tambien se cachean para pintar la carta offline`() = runTest {
        val categoria = Categoria(id = 1L, nombre = "Entradas")
        coEvery { remoto.listarCategorias() } returns Resultado.Exito(listOf(categoria))
        coEvery { dao.reemplazarCategorias(any()) } just Runs

        val enLinea = repositorio.listarCategorias()
        assertTrue(enLinea is Resultado.Exito)

        coEvery { remoto.listarCategorias() } returns Resultado.Fallo(ErrorApp.RED)
        coEvery { dao.categorias() } returns listOf(CategoriaEntity(1L, "Entradas"))

        val offline = repositorio.listarCategorias()
        assertEquals("Entradas", (offline as Resultado.Exito).datos.single().nombre)
    }
}
