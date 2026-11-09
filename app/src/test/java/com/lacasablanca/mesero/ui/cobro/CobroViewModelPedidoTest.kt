package com.lacasablanca.mesero.ui.cobro

import androidx.lifecycle.SavedStateHandle
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.ItemCuenta
import com.lacasablanca.mesero.data.remote.dto.PedidoResumenCuenta
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.DestinoCobro
import com.lacasablanca.mesero.domain.model.MetodoPago
import com.lacasablanca.mesero.domain.model.TipoDescuento
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * PRB-88 Fase 5: el cobro de un pedido sin mesa usa los endpoints de pedido
 * y no toca ninguna mesa. Los tests de mesa existentes se mantienen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CobroViewModelPedidoTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repositorio = mockk<CobroRepository>()
    private val pedidoId = 7L

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun cuentaPedido(total: String = "60.00"): CuentaResponse = CuentaResponse(
        mesaId = null,
        pedidos = listOf(
            PedidoResumenCuenta(
                id = pedidoId,
                estado = "PENDIENTE",
                total = BigDecimal(total)
            )
        ),
        items = listOf(
            ItemCuenta(
                nombre = "Ceviche Clásico",
                cantidad = 1,
                precioUnitario = BigDecimal("48.00"),
                subtotalLinea = BigDecimal("48.00")
            )
        ),
        subtotal = BigDecimal("50.85"),
        igv = BigDecimal("9.15"),
        total = BigDecimal(total),
        propinaSugerida = null
    )

    private fun crearViewModel(): CobroViewModel {
        coEvery { repositorio.obtenerCuentaPedido(pedidoId) } returns
            Resultado.Exito(cuentaPedido())
        return CobroViewModel(
            destino = DestinoCobro.Pedido(pedidoId, "Carlos Mendoza"),
            cobroRepository = repositorio,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `carga la cuenta del pedido sin consultar mesas`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        assertEquals(BigDecimal("60.00"), viewModel.estado.value.cuenta?.total)
        coVerify(exactly = 0) { repositorio.obtenerCuenta(any()) }
        coVerify(exactly = 1) { repositorio.obtenerCuentaPedido(pedidoId) }
    }

    @Test
    fun `confirma el cobro con el endpoint de pedido`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        coEvery { repositorio.cobrarPedido(pedidoId, any()) } returns Resultado.Exito(
            CobroResponse(
                total = BigDecimal("60.00"),
                pagado = BigDecimal("60.00"),
                vuelto = BigDecimal.ZERO
            )
        )

        viewModel.seleccionarMetodo(MetodoPago.EFECTIVO)
        viewModel.actualizarMontoPago("60")
        viewModel.agregarPago()
        viewModel.confirmarCobro()
        advanceUntilIdle()

        assertTrue(viewModel.estado.value.exito)
        coVerify(exactly = 1) { repositorio.cobrarPedido(pedidoId, any()) }
        coVerify(exactly = 0) { repositorio.cobrar(any(), any()) }
    }

    @Test
    fun `el descuento no se aplica a un pedido`() = runTest(testDispatcher) {
        val viewModel = crearViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.aplicarDescuento(TipoDescuento.PORCENTAJE, "10"))
        assertEquals(CobroViewModel.ERROR_DESCUENTO, viewModel.estado.value.error)
    }
}
