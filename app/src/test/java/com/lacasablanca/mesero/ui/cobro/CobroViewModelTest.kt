package com.lacasablanca.mesero.ui.cobro

import androidx.lifecycle.SavedStateHandle
import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.data.repository.FakeCobroRepository
import com.lacasablanca.mesero.data.repository.FakeConsumoStore
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.MetodoPago
import com.lacasablanca.mesero.domain.model.TipoDescuento
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
import org.junit.Assert.assertNull
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
        return CobroViewModel(7L, repositorio, SavedStateHandle(), testDispatcher)
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
                listOf("33.33", "33.33", "33.34"),
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
            listOf("33.33", "33.34", "33.34"),
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
        val viewModel = CobroViewModel(7L, fake, SavedStateHandle(), testDispatcher)
        advanceUntilIdle()

        assertEquals("100.00", viewModel.estado.value.total.toPlainString())
        assertTrue(viewModel.establecerDivision(3))
        val suma = viewModel.estado.value.cuotas.fold(BigDecimal.ZERO) { acumulado, cuota ->
            acumulado.add(cuota.monto)
        }
        assertEquals(0, suma.compareTo(BigDecimal("100.00")))
    }

    @Test
    fun `yape 50 y efectivo 46 completan la cuenta sin sobrepago`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel("96.00")
            advanceUntilIdle()

            agregar(viewModel, MetodoPago.YAPE, "50", "YP-50")

            assertEquals(
                0,
                viewModel.estado.value.saldoPendiente.compareTo(BigDecimal("46.00"))
            )
            assertFalse(viewModel.estado.value.confirmarHabilitado)

            agregar(viewModel, MetodoPago.EFECTIVO, "46")

            assertEquals(2, viewModel.estado.value.pagos.size)
            assertEquals(
                0,
                viewModel.estado.value.totalPagado.compareTo(BigDecimal("96.00"))
            )
            assertEquals(
                0,
                viewModel.estado.value.saldoPendiente.compareTo(BigDecimal.ZERO)
            )
            assertTrue(viewModel.estado.value.confirmarHabilitado)
        }

    @Test
    fun `efectivo 50 sobre saldo 46 registra 46 y vuelto 4`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("96.00")
        advanceUntilIdle()

        agregar(viewModel, MetodoPago.YAPE, "50", "YP-50")
        agregar(viewModel, MetodoPago.EFECTIVO, "50")

        assertEquals("46.00", viewModel.estado.value.pagos.last().monto.toPlainString())
        assertEquals(
            0,
            viewModel.estado.value.vuelto.compareTo(BigDecimal("4.00"))
        )
        assertEquals(
            0,
            viewModel.estado.value.saldoPendiente.compareTo(BigDecimal.ZERO)
        )
        assertTrue(viewModel.estado.value.confirmarHabilitado)
    }

    @Test
    fun `tarjeta de 20 sobre saldo 10 se bloquea`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("96.00")
        advanceUntilIdle()

        agregar(viewModel, MetodoPago.EFECTIVO, "86")
        viewModel.seleccionarMetodo(MetodoPago.TARJETA)
        viewModel.actualizarMontoPago("20")
        viewModel.actualizarReferencia("T-20")
        viewModel.agregarPago()

        assertEquals(CobroViewModel.ERROR_MONTO_EXCEDE, viewModel.estado.value.error)
        assertEquals(1, viewModel.estado.value.pagos.size)
        assertEquals(
            0,
            viewModel.estado.value.saldoPendiente.compareTo(BigDecimal("10.00"))
        )
        assertFalse(viewModel.estado.value.confirmarHabilitado)
    }

    @Test
    fun `yape sin referencia no se agrega`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("96.00")
        advanceUntilIdle()

        viewModel.seleccionarMetodo(MetodoPago.YAPE)
        viewModel.actualizarMontoPago("50")
        viewModel.agregarPago()

        assertEquals(CobroViewModel.ERROR_REFERENCIA, viewModel.estado.value.error)
        assertTrue(viewModel.estado.value.pagos.isEmpty())
        assertEquals(
            0,
            viewModel.estado.value.saldoPendiente.compareTo(BigDecimal("96.00"))
        )
    }

    @Test
    fun `cancelar la division conserva los pagos agregados`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("100.00")
        advanceUntilIdle()

        agregar(viewModel, MetodoPago.EFECTIVO, "40")
        assertTrue(viewModel.establecerDivision(3))
        viewModel.desactivarDivision()

        assertEquals(1, viewModel.estado.value.pagos.size)
        assertEquals("40.00", viewModel.estado.value.pagos.single().monto.toPlainString())
        assertFalse(viewModel.estado.value.dividirCuenta)
        assertTrue(viewModel.estado.value.cuotas.isEmpty())
    }

    @Test
    fun `limpiar el error permite reintentar el mismo bloqueo`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("96.00")
        advanceUntilIdle()

        agregar(viewModel, MetodoPago.EFECTIVO, "86")
        viewModel.seleccionarMetodo(MetodoPago.TARJETA)
        viewModel.actualizarMontoPago("20")
        viewModel.actualizarReferencia("T-20")
        viewModel.agregarPago()

        assertEquals(CobroViewModel.ERROR_MONTO_EXCEDE, viewModel.estado.value.error)

        viewModel.limpiarError()
        assertNull(viewModel.estado.value.error)

        viewModel.agregarPago()
        assertEquals(CobroViewModel.ERROR_MONTO_EXCEDE, viewModel.estado.value.error)
        assertEquals(1, viewModel.estado.value.pagos.size)
    }

    @Test
    fun `conserva el clientRequestId al recrear el ViewModel con el estado guardado`() =
        runTest(testDispatcher) {
            coEvery { repositorio.obtenerCuenta(7L) } returns Resultado.Exito(cuenta("10.00"))
            val enviados = mutableListOf<String>()
            coEvery { repositorio.cobrar(7L, any()) } answers {
                enviados += secondArg<CobrarRequest>().clientRequestId
                Resultado.Exito(CobroResponse())
            }

            fun clonar(handle: SavedStateHandle): SavedStateHandle =
                SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

            fun crearYConfirmar(handle: SavedStateHandle): CobroViewModel {
                val viewModel = CobroViewModel(7L, repositorio, handle, testDispatcher)
                advanceUntilIdle()
                viewModel.seleccionarMetodo(MetodoPago.EFECTIVO)
                viewModel.actualizarMontoPago("10")
                viewModel.agregarPago()
                viewModel.confirmarCobro()
                advanceUntilIdle()
                return viewModel
            }

            val guardado = SavedStateHandle()
            crearYConfirmar(guardado)
            crearYConfirmar(clonar(guardado))

            assertEquals(2, enviados.size)
            assertTrue(enviados.first().isNotBlank())
            assertEquals(enviados.first(), enviados.last())
        }

    @Test
    fun `el descuento porcentual recalcula subtotal igv y total`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("96.00")
        advanceUntilIdle()

        assertTrue(viewModel.aplicarDescuento(TipoDescuento.PORCENTAJE, "10"))

        val estado = viewModel.estado.value
        assertEquals(TipoDescuento.PORCENTAJE, estado.descuento?.tipo)
        assertEquals("10.00", estado.descuento?.valor?.toPlainString())
        assertEquals("9.60", estado.montoDescuento.toPlainString())
        assertEquals("86.40", estado.total.toPlainString())
        assertEquals("73.22", estado.subtotal.toPlainString())
        assertEquals("13.18", estado.igv.toPlainString())
    }

    @Test
    fun `el descuento de monto fijo se resta del total sin tocar la cuenta`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel("96.00")
            advanceUntilIdle()

            assertTrue(viewModel.aplicarDescuento(TipoDescuento.MONTO_FIJO, "20"))

            val estado = viewModel.estado.value
            assertEquals(TipoDescuento.MONTO_FIJO, estado.descuento?.tipo)
            assertEquals("20.00", estado.montoDescuento.toPlainString())
            assertEquals("76.00", estado.total.toPlainString())
            assertEquals("64.41", estado.subtotal.toPlainString())
            assertEquals("11.59", estado.igv.toPlainString())
            assertEquals("96.00", estado.totalBruto.toPlainString())
        }

    @Test
    fun `un descuento fuera de rango no se aplica`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("96.00")
        advanceUntilIdle()

        assertFalse(viewModel.aplicarDescuento(TipoDescuento.PORCENTAJE, "101"))
        assertEquals(CobroViewModel.ERROR_DESCUENTO, viewModel.estado.value.error)
        assertNull(viewModel.estado.value.descuento)

        viewModel.limpiarError()
        assertFalse(viewModel.aplicarDescuento(TipoDescuento.MONTO_FIJO, "96.01"))
        assertEquals(CobroViewModel.ERROR_DESCUENTO, viewModel.estado.value.error)
        assertNull(viewModel.estado.value.descuento)

        viewModel.limpiarError()
        assertFalse(viewModel.aplicarDescuento(TipoDescuento.PORCENTAJE, "0"))
        assertEquals(CobroViewModel.ERROR_DESCUENTO, viewModel.estado.value.error)
        assertNull(viewModel.estado.value.descuento)
    }

    @Test
    fun `quitar el descuento devuelve los totales originales`() = runTest(testDispatcher) {
        val viewModel = crearViewModel("96.00")
        advanceUntilIdle()

        assertTrue(viewModel.aplicarDescuento(TipoDescuento.PORCENTAJE, "10"))
        viewModel.quitarDescuento()

        val estado = viewModel.estado.value
        assertNull(estado.descuento)
        assertEquals("96.00", estado.total.toPlainString())
        assertEquals("81.36", estado.subtotal.toPlainString())
        assertEquals("14.64", estado.igv.toPlainString())
    }

    @Test
    fun `el cobro envia el descuento y se completa con el total rebajado`() =
        runTest(testDispatcher) {
            coEvery { repositorio.obtenerCuenta(7L) } returns Resultado.Exito(cuenta("96.00"))
            val enviados = mutableListOf<CobrarRequest>()
            coEvery { repositorio.cobrar(7L, any()) } answers {
                enviados += secondArg<CobrarRequest>()
                Resultado.Exito(CobroResponse())
            }
            val viewModel = CobroViewModel(7L, repositorio, SavedStateHandle(), testDispatcher)
            advanceUntilIdle()

            assertTrue(viewModel.aplicarDescuento(TipoDescuento.PORCENTAJE, "10"))
            agregar(viewModel, MetodoPago.EFECTIVO, "86.40")

            assertTrue(viewModel.estado.value.confirmarHabilitado)
            viewModel.confirmarCobro()
            advanceUntilIdle()

            assertEquals(1, enviados.size)
            assertEquals("9.60", enviados.single().descuento.toPlainString())
            assertTrue(viewModel.estado.value.exito)
        }

    @Test
    fun `un descuento que deja el total bajo lo pagado se rechaza`() =
        runTest(testDispatcher) {
            val viewModel = crearViewModel("96.00")
            advanceUntilIdle()
            agregar(viewModel, MetodoPago.EFECTIVO, "90")

            assertFalse(viewModel.aplicarDescuento(TipoDescuento.MONTO_FIJO, "20"))

            assertEquals(CobroViewModel.ERROR_DESCUENTO_PAGADO, viewModel.estado.value.error)
            assertNull(viewModel.estado.value.descuento)
            assertEquals("96.00", viewModel.estado.value.total.toPlainString())
        }

    @Test
    fun `la cuenta fake acepta el descuento y cobra el saldo rebajado`() =
        runTest(testDispatcher) {
            val consumo = FakeConsumoStore().apply { registrar(7L, BigDecimal("100.00")) }
            val fake = FakeCobroRepository(consumoStore = consumo, latenciaMs = 0)
            val viewModel = CobroViewModel(7L, fake, SavedStateHandle(), testDispatcher)
            advanceUntilIdle()

            assertTrue(viewModel.aplicarDescuento(TipoDescuento.MONTO_FIJO, "20"))
            agregar(viewModel, MetodoPago.EFECTIVO, "80")
            viewModel.confirmarCobro()
            advanceUntilIdle()

            assertTrue(viewModel.estado.value.exito)
            assertEquals("80.00", viewModel.estado.value.totalConPropina.toPlainString())
        }
}
