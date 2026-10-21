package com.lacasablanca.mesero

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Prueba de humo: verifica que las dependencias principales resuelven y funcionan.
 * Usa MockK para mocking y runTest de kotlinx-coroutines-test para corrutinas.
 */
class DependenciasSmokeTest {

    @Test
    fun `mockk crea mock y verifica interaccion`() = runTest {
        val servicio = mockk<ServicioFalso>()
        coEvery { servicio.obtenerDato() } coAnswers { "dato-mock" }

        val resultado = servicio.obtenerDato()

        assertEquals("dato-mock", resultado)
        coVerify(exactly = 1) { servicio.obtenerDato() }
    }

    @Test
    fun `mockk responde con runTest en corrutina`() = runTest {
        val servicio = mockk<ServicioFalso>()
        coEvery { servicio.sumar(2, 3) } coAnswers { 5 }

        val resultado = servicio.sumar(2, 3)

        assertEquals(5, resultado)
        coVerify(exactly = 1) { servicio.sumar(2, 3) }
    }

    @Test
    fun `okhttp client se construye correctamente`() {
        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        assertNotNull(client)
        assertTrue(client.interceptors.isNotEmpty())
    }

    @Test
    fun `retrofit con gson se construye correctamente`() {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        assertNotNull(retrofit)
        assertEquals("http://localhost:8080/", retrofit.baseUrl().toString())
    }

    @Test
    fun `mockk settings relajado funciona`() = runTest {
        val servicio = mockk<ServicioFalso>(relaxed = true)
        val resultado = servicio.obtenerDato()

        assertNotNull(resultado)
    }

    interface ServicioFalso {
        suspend fun obtenerDato(): String
        suspend fun sumar(a: Int, b: Int): Int
    }
}