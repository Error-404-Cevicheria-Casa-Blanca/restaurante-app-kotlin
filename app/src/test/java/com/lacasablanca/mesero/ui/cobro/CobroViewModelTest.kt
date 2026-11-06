package com.lacasablanca.mesero.ui.cobro

import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.data.repository.FakeCobroRepository
import com.lacasablanca.mesero.data.repository.FakeConsumoStore
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.MetodoPago
import io.mockk.coEvery
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

@OptIn(ExperimentalCoroutinesApi::class)
class CobroViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repositorio = mockk<CobroRepository>()

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(total: String = "100.00"): CobroViewModel {
        coEvery { repositorio.obtenerCuenta(7L) } returns Resultado.Exito(cuenta(total))
        return CobroViewModel(7L, repositorio, testDispatcher)
    }

    private fun cuenta(total: String): CuentaResponse = CuentaResponse(
        mesaId = 7L,
        pedidos = emptyList(),
        items = emptyList(),
        subtotal = BigDecimal(total),
        igv = BigDecimal.ZERO,
        total = BigDecimal(total),
        propinaSugerida = BigDecimal.ZERO
    )

    private fun agregar(
        viewModel: CobroViewModel,
        metodo: MetodoPago,
        monto: String,
        referencia: String? = null
    ) {
        viewModel.seleccionarMetodo(metodo)
        viewModel.actualizarMontoPago(monto)
        if (referencia != null) viewModel.actualizarReferencia(referencia)
        viewModel.agregarPago()
    }

    @Test
    fun `pago mixto suma parciales y habilita el cobro`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("100.00")
        advanceUntilIdle()

        agregar(viewModel, MetodoPago.EFECTIVO, "40")

        assertEquals(1, viewModel.estado.value.pagos.size)
        assertEquals("40.00", viewModel.estado.value.pagos.single().monto.toPlainString())
        assertEquals(
            0,
            viewModel.estado.value.saldoPendiente.compareTo(BigDecimal("60.00"))
        )
        assertFalse(viewModel.estado.value.confirmarHabilitado)

        agregar(viewModel, MetodoPago.YAPE, "60", "YP-001")

        assertEquals(2, viewModel.estado.value.pagos.size)
        assertEquals(
            0,
            viewModel.estado.value.totalPagado.compareTo(BigDecimal("100.00"))
        )
        assertEquals(
            0,
            viewModel.estado.value.saldoPendiente.compareTo(BigDecimal.ZERO)
        )
        assertTrue(viewModel.estado.value.confirmarHabilitado)
        assertEquals("YAPE", viewModel.estado.value.pagos.last().metodo)
        assertEquals("YP-001", viewModel.estado.value.pagos.last().referencia)
    }

    @Test
    fun `yape plin y tarjeta aceptan montos parciales editables`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("96.00")
        advanceUntilIdle()

        agregar(viewModel, MetodoPago.YAPE, "32", "Y-1")
        agregar(viewModel, MetodoPago.PLIN, "32", "P-1")
        agregar(viewModel, MetodoPago.TARJETA, "32", "T-1")

        assertEquals(3, viewModel.estado.value.pagos.size)
        assertEquals(
            listOf("YAPE", "PLIN", "TARJETA"),
            viewModel.estado.value.pagos.map { it.metodo }
        )
        assertTrue(viewModel.estado.value.confirmarHabilitado)
    }

    @Test
    fun `division de 100 entre 3 reparte en centimos sin descuadre`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel("100.00")
            advanceUntilIdle()

            assertTrue(viewModel.establecerDivision(3))

            val cuotas = viewModel.estado.value.cuotas
            assertEquals(3, cuotas.size)
            assertEquals(
                listOf("33.34", "33.33", "33.33"),
                cuotas.map { it.monto.toPlainString() }
            )
            val suma = cuotas.fold(BigDecimal.ZERO) { acumulado, cuota ->
                acumulado.add(cuota.monto)
            }
            assertEquals(0, suma.compareTo(BigDecimal("100.00")))
        }

    @Test
    fun `la division incluye la propina en el total repartido`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("100.00")
        advanceUntilIdle()

        viewModel.actualizarPropina("0.01")
        assertTrue(viewModel.establecerDivision(3))

        val cuotas = viewModel.estado.value.cuotas
        val suma = cuotas.fold(BigDecimal.ZERO) { acumulado, cuota ->
            acumulado.add(cuota.monto)
        }
        assertEquals(0, suma.compareTo(BigDecimal("100.01")))
        assertEquals(
            listOf("33.34", "33.34", "33.33"),
            cuotas.map { it.monto.toPlainString() }
        )
    }

    @Test
    fun `el efectivo con excedente calcula el vuelto y registra solo el saldo`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel("100.00")
            advanceUntilIdle()

            viewModel.seleccionarMetodo(MetodoPago.EFECTIVO)
            viewModel.actualizarMontoPago("120")

            assertEquals(
                0,
                viewModel.estado.value.vuelto.compareTo(BigDecimal("20.00"))
            )

            viewModel.agregarPago()

            assertEquals("100.00", viewModel.estado.value.pagos.single().monto.toPlainString())
            assertEquals(
                0,
                viewModel.estado.value.vuelto.compareTo(BigDecimal("20.00"))
            )
            assertEquals(
                0,
                viewModel.estado.value.saldoPendiente.compareTo(BigDecimal.ZERO)
            )
            assertTrue(viewModel.estado.value.confirmarHabilitado)
        }

    @Test
    fun `un pago sin efectivo no puede superar el saldo pendiente`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("100.00")
        advanceUntilIdle()

        agregar(viewModel, MetodoPago.EFECTIVO, "40")
        viewModel.seleccionarMetodo(MetodoPago.YAPE)
        viewModel.actualizarMontoPago("60.01")
        viewModel.actualizarReferencia("Y-1")
        viewModel.agregarPago()

        assertEquals(CobroViewModel.ERROR_MONTO_EXCEDE, viewModel.estado.value.error)
        assertEquals(1, viewModel.estado.value.pagos.size)
        assertEquals(
            0,
            viewModel.estado.value.saldoPendiente.compareTo(BigDecimal("60.00"))
        )
    }

    @Test
    fun `un monto cero no se agrega`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("100.00")
        advanceUntilIdle()

        viewModel.seleccionarMetodo(MetodoPago.EFECTIVO)
        viewModel.actualizarMontoPago("0")
        viewModel.agregarPago()

        assertEquals(CobroViewModel.ERROR_MONTO, viewModel.estado.value.error)
        assertTrue(viewModel.estado.value.pagos.isEmpty())
    }

    @Test
    fun `la division exige al menos dos comensales`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("100.00")
        advanceUntilIdle()

        assertFalse(viewModel.establecerDivision(1))

        assertEquals(CobroViewModel.ERROR_COMENSALES, viewModel.estado.value.error)
        assertFalse(viewModel.estado.value.dividirCuenta)
        assertTrue(viewModel.estado.value.cuotas.isEmpty())
    }

    @Test
    fun `la cuenta fake de 100 se divide entre 3 sin backend`() = runTest(testDispatcher) {
        val consumo = FakeConsumoStore().apply { registrar(7L, BigDecimal("100.00")) }
        val fake = FakeCobroRepository(consumoStore = consumo, latenciaMs = 0)
        val viewModel = CobroViewModel(7L, fake, testDispatcher)
        advanceUntilIdle()

        assertEquals("100.00", viewModel.estado.value.total.toPlainString())
        assertTrue(viewModel.establecerDivision(3))
        val suma = viewModel.estado.value.cuotas.fold(BigDecimal.ZERO) { acumulado, cuota ->
            acumulado.add(cuota.monto)
        }
        assertEquals(0, suma.compareTo(BigDecimal("100.00")))
    }
}
