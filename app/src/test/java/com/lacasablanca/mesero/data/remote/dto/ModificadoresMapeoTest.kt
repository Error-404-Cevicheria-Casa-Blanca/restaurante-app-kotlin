package com.lacasablanca.mesero.data.remote.dto

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** PRB-92: contrato de modificadores con el backend Java (PRB-74). */
class ModificadoresMapeoTest {

    @Test
    fun `el catalogo mapea nombre y precio adicional`() {
        val producto = ProductoDto(
            id = 10,
            nombre = "Ceviche Clásico",
            precio = BigDecimal("48.00"),
            disponible = true,
            categoriaId = 2,
            modificadores = listOf(
                ModificadorDto("Extra queso", BigDecimal("2.00")),
                ModificadorDto("Sin cebolla", BigDecimal("0.00"))
            )
        ).aDominio()

        assertEquals(2, producto.modificadores.size)
        assertEquals("Extra queso", producto.modificadores[0].nombre)
        assertEquals("2.00", producto.modificadores[0].precioAdicional.toPlainString())
        assertEquals("0.00", producto.modificadores[1].precioAdicional.toPlainString())
    }

    @Test
    fun `un modificador sin nombre se descarta`() {
        val producto = ProductoDto(
            id = 10,
            nombre = "Ceviche Clásico",
            precio = BigDecimal("48.00"),
            modificadores = listOf(
                ModificadorDto("   ", BigDecimal("9.00")),
                ModificadorDto(null, BigDecimal("9.00"))
            )
        ).aDominio()

        assertTrue(producto.modificadores.isEmpty())
    }

    @Test
    fun `sin lista de modificadores el producto queda vacio`() {
        val producto = ProductoDto(
            id = 10,
            nombre = "Ceviche Clásico",
            precio = BigDecimal("48.00")
        ).aDominio()

        assertTrue(producto.modificadores.isEmpty())
    }

    @Test
    fun `el pedido existente reconstruye el precio base sin duplicar el recargo`() {
        val pedido = PedidoExistenteDto(
            id = 55L,
            mesaId = 7L,
            estado = "PENDIENTE",
            items = listOf(
                ItemPedidoExistenteDto(
                    id = 501L,
                    productoId = 10L,
                    nombre = "Ceviche Clásico",
                    cantidad = 2,
                    // El backend ya incluye el recargo en precio_unitario.
                    precioUnitario = BigDecimal("50.00"),
                    modificadores = listOf(
                        ModificadorDto("Extra queso", BigDecimal("2.00"))
                    )
                )
            )
        ).aDominio()

        val linea = pedido.lineas.single()
        assertEquals("48.00", linea.producto.precio.toPlainString())
        assertEquals("50.00", linea.precioUnitarioConModificadores.toPlainString())
        assertEquals("100.00", linea.subtotal.toPlainString())
        assertEquals("100.00", pedido.total.toPlainString())
        assertEquals("Extra queso", linea.modificadores.single().nombre)
    }

    @Test
    fun `el request serializa precio_adicional en snake_case`() {
        val request = ItemPedidoRequest(
            productoId = 10L,
            cantidad = 2,
            modificadores = listOf(
                ModificadorRequest("Extra queso", BigDecimal("2.00")),
                ModificadorRequest("Sin cebolla", BigDecimal("0.00"))
            )
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"precio_adicional\""))

        val modificadores = JsonParser.parseString(json)
            .asJsonObject
            .getAsJsonArray("modificadores")
        assertEquals(2, modificadores.size())
        assertEquals(
            0,
            modificadores[0].asJsonObject.get("precio_adicional")
                .asBigDecimal.compareTo(BigDecimal("2.00"))
        )
        assertEquals("Extra queso", modificadores[0].asJsonObject.get("nombre").asString)
    }
}
