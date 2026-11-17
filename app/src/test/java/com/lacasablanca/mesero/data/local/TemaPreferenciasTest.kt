package com.lacasablanca.mesero.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.lacasablanca.mesero.domain.model.TemaApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TemaPreferenciasTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var contexto: Context

    @Before
    fun configurar() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        contexto = mockk(relaxed = true)
        every { contexto.getSharedPreferences(any(), any()) } returns prefs
        mockkStatic(AppCompatDelegate::class)
    }

    @After
    fun limpiar() {
        unmockkStatic(AppCompatDelegate::class)
    }

    @Test
    fun `leerTemaSincrono interpreta el cache de SharedPreferences`() {
        every { prefs.getString("tema_app", null) } returns "claro"
        val repo = TemaPreferencias(contexto)

        assertEquals(TemaApp.CLARO, repo.leerTemaSincrono())
    }

    @Test
    fun `leerTemaSincrono sin valor previo cae en oscuro por defecto`() {
        every { prefs.getString("tema_app", null) } returns null
        val repo = TemaPreferencias(contexto)

        assertEquals(TemaApp.OSCURO, repo.leerTemaSincrono())
    }

    @Test
    fun `aplicarTemaGuardado llama a AppCompatDelegate cuando el modo cambia`() {
        every { prefs.getString("tema_app", null) } returns "claro"
        every { AppCompatDelegate.getDefaultNightMode() } returns AppCompatDelegate.MODE_NIGHT_YES
        val repo = TemaPreferencias(contexto)

        repo.aplicarTemaGuardado()

        verify(exactly = 1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    @Test
    fun `aplicarTemaGuardado no re-aplica si el modo ya coincide`() {
        every { prefs.getString("tema_app", null) } returns "oscuro"
        every { AppCompatDelegate.getDefaultNightMode() } returns AppCompatDelegate.MODE_NIGHT_YES
        val repo = TemaPreferencias(contexto)

        repo.aplicarTemaGuardado()

        verify(exactly = 0) { AppCompatDelegate.setDefaultNightMode(any()) }
    }
}
