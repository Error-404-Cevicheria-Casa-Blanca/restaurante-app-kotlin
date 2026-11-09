package com.lacasablanca.mesero.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de humo de la navegacion de PRB-88 (Fases 1 a 3).
 *
 * Verifica el contenedor comun: barra visible, 3 destinos, mapa real en Salon,
 * perfil real, cambio de tab, conservacion del tab al recrear la Activity,
 * back de Pedidos a Salon y cierre de sesion desde el perfil.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityNavegacionTest {

    private val contenedor
        get() = ApplicationProvider.getApplicationContext<MeseroApp>().container

    @Before
    fun iniciarSesionFake() {
        // El mapa real de Salon usa MesasViewModel y necesita una sesion.
        runBlocking {
            contenedor.authRepository.iniciarSesion("mozo", "mozo123")
        }
    }

    @After
    fun cerrarSesionFake() {
        runBlocking { contenedor.authRepository.cerrarSesion() }
    }

    @Test
    fun muestraLosTresDestinosYConservaElTabAlRecrear() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.navegacionPrincipal)).check(matches(isDisplayed()))
            onView(withId(R.id.btnActualizar)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                val navegacion = activity.findViewById<NavegacionPrincipalView>(
                    R.id.navegacionPrincipal
                )
                assertEquals(3, navegacion.menu.size())
                assertEquals(R.id.navSalon, navegacion.selectedItemId)
                assertEquals(
                    activity.getString(R.string.cd_nav_pedidos),
                    navegacion.menu.findItem(R.id.navPedidos)?.contentDescription
                )
            }

            onView(withId(R.id.navPedidos)).perform(click())
            onView(withId(R.id.textoTituloPedidos)).check(matches(isDisplayed()))

            scenario.recreate()
            onView(withId(R.id.textoTituloPedidos)).check(matches(isDisplayed()))

            pressBack()
            onView(withId(R.id.btnActualizar)).check(matches(isDisplayed()))
            // La barra anima el cambio de seleccion; se espera antes del
            // siguiente toque para que el test no dependa de la animacion.
            Thread.sleep(ESPERA_SELECCION_MS)

            onView(withId(R.id.navPerfil)).perform(click())
            onView(withId(R.id.barraPerfil)).check(matches(isDisplayed()))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun cerrarSesionDesdePerfilVuelveAlLogin() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.navPerfil)).perform(click())
            onView(withId(R.id.btnCerrarSesionPerfil))
                .check(matches(isDisplayed()))
                .perform(click())

            // LoginActivity arranca con CLEAR_TASK y MainActivity se termina.
            onView(withId(R.id.btnIngresar)).check(matches(isDisplayed()))
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val ESPERA_SELECCION_MS = 500L
    }
}
