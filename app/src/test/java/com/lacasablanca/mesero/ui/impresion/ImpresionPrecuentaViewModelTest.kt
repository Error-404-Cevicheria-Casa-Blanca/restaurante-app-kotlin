package com.lacasablanca.mesero.ui.impresion

import com.lacasablanca.mesero.data.remote.dto.CuentaResponse
import com.lacasablanca.mesero.data.remote.dto.TrabajoImpresionDto
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ImpresionPrecuentaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repositorio = mockk<CobroRepository>()

    private val cuenta = CuentaResponse(
        mesaId = 7L,
        pedidos = emptyList(),
        items = emptyList(),
        subtotal = BigDecimal("81.36"),
        igv = BigDecimal("14.64"),
        total = BigDecimal("96.00"),
        propinaSugerida = BigDecimal.ZERO
    )

    private val trabajo = TrabajoImpresionDto(
        jobId = "job-1",
        mesaId = 7L,
        tipo = "PRECUENTA_80MM",
        estado = "PENDIENTE"
    )

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel(mesaId: Long = 7L) =
        ImpresionPrecuentaViewModel(mesaId, repositorio, testDispatcher)

    @Test
    fun `imprimir la precuenta avisa que se envio a la impresora`() =
        runTest(testDispatcher) {
            coEvery { repositorio.imprimirPrecuenta(7L) } returns Resultado.Exito(cuenta)

            val viewModel = crearViewModel()
            viewModel.imprimirPrecuenta()
            advanceUntilIdle()

            coVerify(exactly = 1) { repositorio.imprimirPrecuenta(7L) }
            assertEquals(AvisoImpresion.ENVIADA, viewModel.estado.value.aviso)
            assertFalse(viewModel.estado.value.imprimiendo)
        }

    @Test
    fun `reimprimir usa el endpoint de reimpresion`() = runTest(testDispatcher) {
        coEvery { repositorio.reimprimirPrecuenta(7L) } returns Resultado.Exito(trabajo)

        val viewModel = crearViewModel()
        viewModel.reimprimirPrecuenta()
        advanceUntilIdle()

        coVerify(exactly = 1) { repositorio.reimprimirPrecuenta(7L) }
        coVerify(exactly = 0) { repositorio.imprimirPrecuenta(any()) }
        assertEquals(AvisoImpresion.ENVIADA, viewModel.estado.value.aviso)
    }

    @Test
    fun `un fallo de red avisa error de conexion`() = runTest(testDispatcher) {
        coEvery { repositorio.imprimirPrecuenta(7L) } returns Resultado.Fallo(ErrorApp.RED)

        val viewModel = crearViewModel()
        viewModel.imprimirPrecuenta()
        advanceUntilIdle()

        assertEquals(AvisoImpresion.ERROR_CONEXION, viewModel.estado.value.aviso)
        assertFalse(viewModel.estado.value.imprimiendo)
    }

    @Test
    fun `una sesion expirada tiene su propio aviso`() = runTest(testDispatcher) {
        coEvery { repositorio.reimprimirPrecuenta(7L) } returns
            Resultado.Fallo(ErrorApp.SESION_EXPIRADA)

        val viewModel = crearViewModel()
        viewModel.reimprimirPrecuenta()
        advanceUntilIdle()

        assertEquals(AvisoImpresion.SESION_EXPIRADA, viewModel.estado.value.aviso)
    }

    @Test
    fun `un error del servidor avisa error generico`() = runTest(testDispatcher) {
        coEvery { repositorio.imprimirPrecuenta(7L) } returns
            Resultado.Fallo(ErrorApp.SERVIDOR)

        val viewModel = crearViewModel()
        viewModel.imprimirPrecuenta()
        advanceUntilIdle()

        assertEquals(AvisoImpresion.ERROR, viewModel.estado.value.aviso)
    }

    @Test
    fun `un id invalido no llama al repositorio`() = runTest(testDispatcher) {
        val viewModel = crearViewModel(mesaId = 0L)
        viewModel.imprimirPrecuenta()
        viewModel.reimprimirPrecuenta()
        advanceUntilIdle()

        coVerify(exactly = 0) { repositorio.imprimirPrecuenta(any()) }
        coVerify(exactly = 0) { repositorio.reimprimirPrecuenta(any()) }
        assertNull(viewModel.estado.value.aviso)
    }

    @Test
    fun `los toques repetidos no duplican la peticion`() = runTest(testDispatcher) {
        coEvery { repositorio.imprimirPrecuenta(7L) } returns Resultado.Exito(cuenta)

        val viewModel = crearViewModel()
        viewModel.imprimirPrecuenta()
        viewModel.imprimirPrecuenta()
        advanceUntilIdle()

        coVerify(exactly = 1) { repositorio.imprimirPrecuenta(7L) }
    }

    @Test
    fun `consumir el aviso lo limpia`() = runTest(testDispatcher) {
        coEvery { repositorio.imprimirPrecuenta(7L) } returns Resultado.Exito(cuenta)

        val viewModel = crearViewModel()
        viewModel.imprimirPrecuenta()
        advanceUntilIdle()
        viewModel.consumirAviso()

        assertNull(viewModel.estado.value.aviso)
    }
}
