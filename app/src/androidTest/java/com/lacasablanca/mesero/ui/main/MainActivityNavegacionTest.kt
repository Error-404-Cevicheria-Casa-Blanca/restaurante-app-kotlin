package com.lacasablanca.mesero.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lacasablanca.mesero.R
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de humo de la navegacion de PRB-88 (Fase 1).
 *
 * Verifica el contenedor comun: barra visible, 3 destinos, cambio de tab,
 * conservacion del tab al recrear la Activity y back de Pedidos a Salon.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityNavegacionTest {

    @Test
    fun muestraLosTresDestinosYConservaElTabAlRecrear() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.navegacionPrincipal)).check(matches(isDisplayed()))
            onView(tituloVisible()).check(matches(withText(R.string.nav_salon)))

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
            onView(tituloVisible()).check(matches(withText(R.string.nav_pedidos)))

            scenario.recreate()
            onView(tituloVisible()).check(matches(withText(R.string.nav_pedidos)))

            pressBack()
            onView(tituloVisible()).check(matches(withText(R.string.nav_salon)))

            onView(withId(R.id.navPerfil)).perform(click())
            onView(tituloVisible()).check(matches(withText(R.string.nav_perfil)))
        } finally {
            scenario.close()
        }
    }

    /**
     * Los 3 fragmentos quedan vivos (hide/show) y comparten el id del titulo;
     * solo el visible debe participar en la asercion.
     */
    private fun tituloVisible() = allOf(
        withId(R.id.textoPlaceholder),
        withEffectiveVisibility(Visibility.VISIBLE)
    )
}
