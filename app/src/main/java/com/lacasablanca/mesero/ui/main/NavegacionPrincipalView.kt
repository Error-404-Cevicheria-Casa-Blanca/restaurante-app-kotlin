package com.lacasablanca.mesero.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.lacasablanca.mesero.databinding.ViewNavegacionBottomBinding
import com.lacasablanca.mesero.databinding.ViewNavegacionRailBinding

/**
 * Navegacion principal de PRB-88.
 *
 * En telefono infla un [com.google.android.material.bottomnavigation.BottomNavigationView]
 * y en tablet (>= 600dp) un
 * [com.google.android.material.navigationrail.NavigationRailView].
 *
 * Ambos son [NavigationBarView], pero NO comparten jerarquia entre si: si el
 * layout de la Activity declarara los dos tags con el mismo id, ViewBinding
 * generaria un tipo y fallaria al inflar la otra variante. Esta vista encapsula
 * el tipo concreto y expone una API estable (menu, seleccion, listener) para
 * que MainActivity tenga un unico id y un unico codigo en ambos dispositivos.
 */
class NavegacionPrincipalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val barra: NavigationBarView

    init {
        barra = if (esTablet(context)) {
            ViewNavegacionRailBinding.inflate(LayoutInflater.from(context), this, true)
                .navegacionInterna
        } else {
            ViewNavegacionBottomBinding.inflate(LayoutInflater.from(context), this, true)
                .navegacionInterna
        }
    }

    /** Menu de los 3 destinos, ya inflado desde `menu_navegacion.xml`. */
    val menu: Menu
        get() = barra.menu

    /** Id del destino seleccionado (`navSalon`, `navPedidos` o `navPerfil`). */
    var selectedItemId: Int
        get() = barra.selectedItemId
        set(value) {
            barra.selectedItemId = value
        }

    fun setOnItemSelectedListener(listener: NavigationBarView.OnItemSelectedListener?) {
        barra.setOnItemSelectedListener(listener)
    }

    /** Accesibilidad: `MenuItem` no declara contentDescription en XML. */
    fun setItemContentDescription(itemId: Int, texto: CharSequence) {
        menu.findItem(itemId)?.contentDescription = texto
    }

    /** Texto del ítem seleccionado (util para pruebas y accesibilidad). */
    fun itemSeleccionado(): MenuItem? = menu.findItem(selectedItemId)

    /** true si la variante inflada es NavigationRailView (tablet >= 600dp). */
    fun usaRail(): Boolean = barra is NavigationRailView

    private fun esTablet(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= UMBRAL_TABLET_DP

    private companion object {
        const val UMBRAL_TABLET_DP = 600
    }
}
