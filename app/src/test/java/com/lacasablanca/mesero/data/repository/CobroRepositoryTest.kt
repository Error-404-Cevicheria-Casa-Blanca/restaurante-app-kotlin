package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.remote.CobroApi
import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.CobroResponse
import com.lacasablanca.mesero.data.remote.dto.PagoRequest
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal

/**
 * Traduccion de errores HTTP del cobro (PRB-2).
 * El 409 del cobro no es el mismo que el 409 de "mesa ya tomada".
 */
class CobroRepositoryTest {

    private val api = mockk<CobroApi>()
    private val repositorio = RealCobroRepository(api)

    private fun request(): CobrarRequest = CobrarRequest(
        pagos = listOf(PagoRequest(metodo = "EFECTIVO", monto = BigDecimal("96.00"))),
        propina = BigDecimal.ZERO,
        observacion = null,
        clientRequestId = "cobro-uuid-1"
    )

    private fun error(codigo: Int): Response<CobroResponse> = Response.error(
        codigo,
        "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
    )

    @Test
    fun `un 409 al cobrar se traduce a cuenta ya pagada`() = runTest {
        coEvery { api.cobrar(7L, any()) } returns error(409)

        val resultado = repositorio.cobrar(7L, request())

        assertEquals(ErrorApp.CONFLICTO_COBRO, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un 423 al cobrar se traduce a bloqueado`() = runTest {
        coEvery { api.cobrar(7L, any()) } returns error(423)

        val resultado = repositorio.cobrar(7L, request())

        assertEquals(ErrorApp.BLOQUEADO, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `un 409 al consultar la cuenta conserva el conflicto generico`() = runTest {
        coEvery { api.obtenerCuenta(7L) } returns Response.error(
            409,
            "{\"mensaje\":\"error\"}".toResponseBody("application/json".toMediaType())
        )

        val resultado = repositorio.obtenerCuenta(7L)

        assertEquals(ErrorApp.CONFLICTO, (resultado as Resultado.Fallo).error)
    }

    // ---------------------------------------------------------------------
    // PRB-94: un pedido para llevar no tiene mesa que liberar
    // ---------------------------------------------------------------------

    @Test
    fun `un cobro sin mesa no intenta liberar ninguna mesa`() = runTest {
        val mesas = mockk<MesaRepository>()
        val fake = FakeCobroRepository(mesas, latenciaMs = 0)

        val resultado = fake.cobrar(0L, request())

        assertEquals(ErrorApp.VALIDACION, (resultado as Resultado.Fallo).error)
        coVerify(exactly = 0) { mesas.cambiarEstado(any(), any()) }
    }
}
