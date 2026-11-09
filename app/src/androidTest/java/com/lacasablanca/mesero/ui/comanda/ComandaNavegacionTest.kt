package com.lacasablanca.mesero.ui.comanda

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de humo de la comanda como detalle (PRB-88, Fase 4).
 *
 * Abre una mesa libre del mapa fake, comprueba que la barra de navegacion sigue
 * visible dentro de la comanda, cambia a Pedidos y vuelve a Salon. No valida el
 * contenido del carrito (eso lo cubren los tests del ViewModel), pero si el
 * inflado del fragmento, el scope del ViewModel y el hide/show del detalle.
 *
 * La carga de mesas usa latencia simulada (500 ms) y el PATCH fake otros
 * 500 ms; por eso se reintenta hasta ver la vista, en vez de un sleep fijo.
 */
@RunWith(AndroidJUnit4::class)
class ComandaNavegacionTest {

    private val contenedor
        get() = ApplicationProvider.getApplicationContext<MeseroApp>().container

    @Before
    fun iniciarSesionFake() {
        runBlocking {
            contenedor.authRepository.iniciarSesion("mozo", "mozo123")
        }
    }

    @After
    fun limpiarEstado() {
        runBlocking {
            contenedor.mesaRepository.cambiarEstado(1L, EstadoMesa.LIBRE)
            contenedor.authRepository.cerrarSesion()
        }
    }

    @Test
    fun abrirComandaConservaLaBarraYVuelveAlTab() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val mesa1 = allOf(
                withId(R.id.cardMesa),
                hasDescendant(withText("Mesa 1"))
            )
            esperarVista(mesa1)
            onView(mesa1).perform(click())

            esperarVista(withId(R.id.barraComanda))
            onView(withId(R.id.navegacionPrincipal)).check(matches(isDisplayed()))

            onView(withId(R.id.navPedidos)).perform(click())
            onView(
                allOf(
                    withId(R.id.textoPlaceholder),
                    withText(R.string.nav_pedidos)
                )
            ).check(matches(isDisplayed()))

            onView(withId(R.id.navSalon)).perform(click())
            onView(withId(R.id.barraComanda)).check(matches(isDisplayed()))
        } finally {
            scenario.close()
        }
    }

    /** Reintenta hasta que el matcher este visible; al final lanza el error real. */
    private fun esperarVista(matcher: Matcher<View>) {
        val limite = System.currentTimeMillis() + ESPERA_MAXIMA_MS
        while (System.currentTimeMillis() < limite) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (e: NoMatchingViewException) {
                Thread.sleep(ESPERA_REINTENTO_MS)
            }
        }
        onView(matcher).check(matches(isDisplayed()))
    }

    private companion object {
        const val ESPERA_MAXIMA_MS = 10_000L
        const val ESPERA_REINTENTO_MS = 250L
    }
}
