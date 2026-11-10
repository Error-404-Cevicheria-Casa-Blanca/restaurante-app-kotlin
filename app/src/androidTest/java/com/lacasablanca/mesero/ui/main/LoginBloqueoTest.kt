package com.lacasablanca.mesero.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.ui.bloqueo.BloqueoActivity
import com.lacasablanca.mesero.ui.login.LoginActivity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRB-88: la barra de navegacion no debe existir en Login ni en Bloqueo.
 * Se ejecuta sin sesion para que Login no navegue a MainActivity.
 */
@RunWith(AndroidJUnit4::class)
class LoginBloqueoTest {

    private val contenedor
        get() = ApplicationProvider.getApplicationContext<MeseroApp>().container

    @Before
    fun cerrarSesion() {
        runBlocking { contenedor.authRepository.cerrarSesion() }
    }

    @Test
    fun loginNoMuestraLaBarra() {
        val scenario = ActivityScenario.launch(LoginActivity::class.java)
        try {
            onView(withId(R.id.navegacionPrincipal)).check(doesNotExist())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun bloqueoNoMuestraLaBarra() {
        val scenario = ActivityScenario.launch(BloqueoActivity::class.java)
        try {
            onView(withId(R.id.navegacionPrincipal)).check(doesNotExist())
        } finally {
            scenario.close()
        }
    }
}
