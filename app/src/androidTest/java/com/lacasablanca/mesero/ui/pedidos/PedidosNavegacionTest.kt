package com.lacasablanca.mesero.ui.pedidos

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRB-88 Fase 6: Pedidos real -> boton Cobrar de un delivery -> CobroFragment.
 * Usa los datos fake de FakePedidoRepository (delivery #104, Lucía Torres).
 */
@RunWith(AndroidJUnit4::class)
class PedidosNavegacionTest {

    private val contenedor
        get() = ApplicationProvider.getApplicationContext<MeseroApp>().container

    @Before
    fun iniciarSesionFake() {
        runBlocking {
            contenedor.authRepository.iniciarSesion("mozo", "mozo123")
        }
    }

    @After
    fun cerrarSesionFake() {
        runBlocking { contenedor.authRepository.cerrarSesion() }
    }

    @Test
    fun cobraUnDeliveryDesdeLaListaDePedidos() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.navPedidos)).perform(click())
            esperarVista(withId(R.id.textoTituloPedidos))

            val cardDelivery = allOf(
                withId(R.id.cardPedido),
                hasDescendant(withText("Delivery: Lucía Torres"))
            )
            val btnCobrarDelivery = allOf(
                withId(R.id.btnCobrarPedido),
                isDescendantOfA(cardDelivery)
            )
            // El delivery es el cuarto pedido fake; se desplaza para que el
            // boton quede completamente visible antes del click.
            scenario.onActivity { activity ->
                activity.findViewById<RecyclerView>(R.id.recyclerPedidos)
                    .scrollToPosition(3)
            }
            esperarVista(btnCobrarDelivery)
            onView(btnCobrarDelivery).perform(click())

            esperarVista(withId(R.id.barraCobro))
            onView(withId(R.id.navegacionPrincipal)).check(matches(isDisplayed()))
            onView(withText("Cobrar Pedido #104")).check(matches(isDisplayed()))
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
