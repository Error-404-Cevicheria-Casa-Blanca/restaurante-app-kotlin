package com.lacasablanca.mesero.data.repository

import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.local.TokenStore
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Sesion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAuthRepositoryTest {

    private val tokenStore = object : TokenStore {
        override suspend fun guardar(sesion: Sesion) = Unit

        override suspend fun leer(): Sesion? = null

        override suspend fun limpiar() = Unit
    }

    private val repository = FakeAuthRepository(SessionManager(tokenStore), latenciaMs = 0)

    @Test
    fun `acepta el pin demo con un usuario no vacio`() = runTest {
        val resultado = repository.validarPin("Mozo Demo", FakeAuthRepository.PIN_DEMO)

        assertTrue(resultado is Resultado.Exito)
    }

    @Test
    fun `rechaza un pin distinto al demo`() = runTest {
        val resultado = repository.validarPin("Mozo Demo", "0000")

        assertTrue(resultado is Resultado.Fallo)
        assertEquals(ErrorApp.CREDENCIALES, (resultado as Resultado.Fallo).error)
    }

    @Test
    fun `rechaza el pin demo si el usuario esta vacio`() = runTest {
        val resultado = repository.validarPin("", FakeAuthRepository.PIN_DEMO)

        assertTrue(resultado is Resultado.Fallo)
        assertEquals(ErrorApp.CREDENCIALES, (resultado as Resultado.Fallo).error)
    }
}
