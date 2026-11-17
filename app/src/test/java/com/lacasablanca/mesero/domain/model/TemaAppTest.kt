package com.lacasablanca.mesero.domain.model

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class TemaAppTest {

    @Test
    fun `tema por defecto es oscuro Marea Nocturna`() {
        assertEquals(TemaApp.OSCURO, TemaApp.POR_DEFECTO)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, TemaApp.OSCURO.modoNoche)
    }

    @Test
    fun `desdeClave resuelve cada variante valida`() {
        assertEquals(TemaApp.CLARO, TemaApp.desdeClave("claro"))
        assertEquals(TemaApp.OSCURO, TemaApp.desdeClave("oscuro"))
        assertEquals(TemaApp.AUTOMATICO, TemaApp.desdeClave("automatico"))
    }

    @Test
    fun `desdeClave con nulo o desconocido devuelve el tema por defecto`() {
        assertEquals(TemaApp.POR_DEFECTO, TemaApp.desdeClave(null))
        assertEquals(TemaApp.POR_DEFECTO, TemaApp.desdeClave(""))
        assertEquals(TemaApp.POR_DEFECTO, TemaApp.desdeClave("desconocido"))
    }

    @Test
    fun `modos de noche coinciden con AppCompatDelegate`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, TemaApp.CLARO.modoNoche)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, TemaApp.OSCURO.modoNoche)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, TemaApp.AUTOMATICO.modoNoche)
    }
}
