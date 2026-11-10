package com.lacasablanca.mesero.data.remote.dto

import com.lacasablanca.mesero.domain.model.OrigenPedido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** PRB-88 Fase 6: mapeo de GET /api/v1/pedidos a PedidoActivo. */
class PedidoActivoMapeoTest {

    @Test
    fun `mapea una mesa por mesa_id`() {
        val pedido = PedidoResponseDto(
            id = 101L,
            mesaId = 5L,
            estado = "PENDIENTE",
            total = BigDecimal("84.00"),
            tipoPedido = "SALON"
        )

        val activo = pedido.aPedidoActivo()

        assertEquals(OrigenPedido.MESA, activo?.tipoPedido)
        assertEquals(5L, activo?.mesaId)
        assertEquals("PENDIENTE", activo?.estado)
        assertEquals(BigDecimal("84.00"), activo?.total)
        assertFalse(activo?.puedeCobrar ?: true)
    }

    @Test
    fun `mapea para llevar por tipo y cliente`() {
        val pedido = PedidoResponseDto(
            id = 103L,
            mesaId = null,
            estado = "PENDIENTE",
            total = BigDecimal("60.00"),
            tipoPedido = "PARA_LLEVAR",
            clienteNombre = "Carlos Mendoza",
            paraLlevar = true
        )

        val activo = pedido.aPedidoActivo()

        assertEquals(OrigenPedido.PARA_LLEVAR, activo?.tipoPedido)
        assertEquals("Carlos Mendoza", activo?.clienteNombre)
        assertTrue(activo?.puedeCobrar ?: false)
    }

    @Test
    fun `mapea delivery por tipo`() {
        val pedido = PedidoResponseDto(
            id = 104L,
            mesaId = null,
            estado = "EN_PREPARACION",
            total = BigDecimal("75.00"),
            tipoPedido = "DELIVERY",
            clienteNombre = "Lucía Torres",
            paraLlevar = true
        )

        val activo = pedido.aPedidoActivo()

        assertEquals(OrigenPedido.DELIVERY, activo?.tipoPedido)
        assertEquals("Lucía Torres", activo?.clienteNombre)
        assertTrue(activo?.puedeCobrar ?: false)
    }

    @Test
    fun `sin tipo_pedido usa para_llevar`() {
        val pedido = PedidoResponseDto(
            id = 105L,
            mesaId = null,
            estado = "LISTO",
            total = BigDecimal("30.00"),
            paraLlevar = true
        )

        val activo = pedido.aPedidoActivo()

        assertEquals(OrigenPedido.PARA_LLEVAR, activo?.tipoPedido)
        assertTrue(activo?.puedeCobrar ?: false)
    }

    @Test
    fun `descarta un pedido sin id`() {
        val pedido = PedidoResponseDto(
            id = null,
            mesaId = 5L,
            estado = "PENDIENTE",
            total = BigDecimal("10.00")
        )

        assertNull(pedido.aPedidoActivo())
    }
}
