package com.lacasablanca.mesero.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRB-88: valida que en tablet (sw600dp) se infla NavigationRailView y no el
 * BottomNavigationView. En telefono se omite con Assume.
 *
 * Para ejecutarla en tablet:
 *   .\gradlew.bat connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.lacasablanca.mesero.ui.main.NavegacionTabletTest
 */
@RunWith(AndroidJUnit4::class)
class NavegacionTabletTest {

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
    fun usaNavigationRailEnTablet() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(
            "Solo aplica a tablet (smallestScreenWidthDp >= 600)",
            contexto.resources.configuration.smallestScreenWidthDp >= 600
        )

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val navegacion = activity.findViewById<NavegacionPrincipalView>(
                    R.id.navegacionPrincipal
                )
                assertTrue(
                    "En tablet debe inflarse NavigationRailView",
                    navegacion.usaRail()
                )
            }
            onView(withId(R.id.navegacionPrincipal)).check(matches(isDisplayed()))
        } finally {
            scenario.close()
        }
    }
}
