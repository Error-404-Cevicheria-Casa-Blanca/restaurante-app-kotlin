package com.lacasablanca.mesero.ui.cobro

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * PRB-88 Fase 5: abre el cobro de un pedido delivery desde Pedidos.
 * Verifica que la barra sigue visible, el titulo del pedido y que el
 * descuento/pre-cuenta de mesa quedan ocultos.
 */
@RunWith(AndroidJUnit4::class)
class CobroPedidoNavegacionTest {

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
    fun abreCobroDePedidoYConservaLaBarra() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.navPedidos)).perform(click())
            onView(withId(R.id.textoTituloPedidos)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                activity.abrirCobroPedido(7L, "Carlos Mendoza")
            }

            esperarVista(withId(R.id.barraCobro))
            onView(withId(R.id.navegacionPrincipal)).check(matches(isDisplayed()))
            onView(withText("Cobrar Pedido #7")).check(matches(isDisplayed()))
            onView(withId(R.id.btnAplicarDescuento))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.btnImprimir))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))

            // Cambiar de tab no debe destruir el cobro; al volver a Pedidos
            // debe seguir en pantalla y con la barra visible.
            onView(withId(R.id.navSalon)).perform(click())
            onView(withId(R.id.btnActualizar)).check(matches(isDisplayed()))
            onView(withId(R.id.navPedidos)).perform(click())
            onView(withId(R.id.barraCobro)).check(matches(isDisplayed()))
        } finally {
            scenario.close()
        }
    }

    @Test
    fun atrasEnCobroVuelveAPedidos() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.navPedidos)).perform(click())
            onView(withId(R.id.textoTituloPedidos)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                activity.abrirCobroPedido(7L, "Carlos Mendoza")
            }
            esperarVista(withId(R.id.barraCobro))

            pressBack()
            onView(withId(R.id.btnSalirSinCobrar))
                .check(matches(isDisplayed()))
                .perform(click())

            esperarVista(withId(R.id.textoTituloPedidos))
            onView(withId(R.id.barraCobro)).check(doesNotExist())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun alTocarMesaConCobroOcultoMuestraConfirmacionYPermiteAbrirMesa() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.navPedidos)).perform(click())
            esperarVista(withId(R.id.textoTituloPedidos))

            scenario.onActivity { activity ->
                activity.abrirCobroPedido(7L, "Carlos Mendoza")
            }
            esperarVista(withId(R.id.barraCobro))

            // Cambia a Salon sin cerrar el cobro del pedido y toca Mesa 5 (Cuenta solicitada).
            onView(withId(R.id.navSalon)).perform(click())
            esperarVista(withId(R.id.recyclerMesas))
            val mesa1 = allOf(
                withId(R.id.cardMesa),
                hasDescendant(withText("Mesa 1"))
            )
            esperarVista(mesa1)
            scenario.onActivity { activity ->
                activity.findViewById<RecyclerView>(R.id.recyclerMesas).scrollToPosition(4)
            }
            val mesa5 = allOf(
                withId(R.id.cardMesa),
                hasDescendant(withText("Mesa 5"))
            )
            esperarVista(mesa5)
            onView(mesa5).perform(click())

            // Primero verifica volver al cobro pendiente.
            esperarVista(withId(R.id.btnEntendido))
            onView(withId(R.id.btnEntendido)).perform(click())
            esperarVista(withText("Cobrar Pedido #7"))

            // Vuelve a Salon, toca Mesa 5 y elige descartar el pendiente para abrir Cobrar Mesa 5.
            onView(withId(R.id.navSalon)).perform(click())
            esperarVista(withId(R.id.recyclerMesas))
            scenario.onActivity { activity ->
                activity.findViewById<RecyclerView>(R.id.recyclerMesas).scrollToPosition(4)
            }
            esperarVista(mesa5)
            onView(mesa5).perform(click())
            esperarVista(withId(R.id.btnSecundarioAviso))
            onView(withId(R.id.btnSecundarioAviso)).perform(click())

            esperarVista(withText("Cobrar Mesa 5"))
            onView(withId(R.id.badgeEstadoCobro)).check(matches(isDisplayed()))
            onView(withId(R.id.btnImprimir)).check(matches(isDisplayed()))
            esperarVista(withId(R.id.btnReimprimir))
            esperarVista(withId(R.id.btnAplicarDescuento))
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
            } catch (_: NoMatchingViewException) {
                Thread.sleep(ESPERA_REINTENTO_MS)
            } catch (_: AssertionError) {
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
