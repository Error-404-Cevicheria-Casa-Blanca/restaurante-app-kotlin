package com.lacasablanca.mesero.ui.main

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ActivityMainBinding
import com.lacasablanca.mesero.ui.pedidos.PedidosFragment
import com.lacasablanca.mesero.ui.perfil.PerfilFragment
import com.lacasablanca.mesero.ui.salon.SalonFragment

/**
 * Contenedor principal de la app (PRB-88).
 *
 * Mantiene los 3 destinos vivos con `add` + `hide/show` para no perder su
 * estado al cambiar de tab. La barra es NavegacionPrincipalView: en telefono
 * infla un BottomNavigationView y en tablet un NavigationRailView, con el mismo
 * id `navegacionPrincipal` y la misma API.
 *
 * Back stack:
 * - En Salon, Atras sale de la app.
 * - En Pedidos o Perfil, Atras vuelve a Salon.
 * - Los detalles (Comanda/Cobro) se apilan en la Fase 4-5 y se resolveran en
 *   este mismo callback.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val navegacion get() = binding.navegacionPrincipal

    private var tabActual = R.id.navSalon

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarNavegacion()

        val tabInicial = savedInstanceState?.getInt(KEY_TAB, R.id.navSalon) ?: R.id.navSalon
        seleccionarTab(tabInicial)

        onBackPressedDispatcher.addCallback(this) {
            if (tabActual == R.id.navSalon) {
                finish()
            } else {
                seleccionarTab(R.id.navSalon)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_TAB, tabActual)
        super.onSaveInstanceState(outState)
    }

    private fun configurarNavegacion() {
        navegacion.setOnItemSelectedListener { item ->
            mostrarTab(item.itemId)
            true
        }
        // MenuItem no admite contentDescription en XML; se asigna en codigo.
        navegacion.setItemContentDescription(R.id.navSalon, getString(R.string.cd_nav_salon))
        navegacion.setItemContentDescription(R.id.navPedidos, getString(R.string.cd_nav_pedidos))
        navegacion.setItemContentDescription(R.id.navPerfil, getString(R.string.cd_nav_perfil))
    }

    /**
     * Cambia de tab. Si el destino ya estaba seleccionado no dispara el
     * listener de la barra, por eso se muestra explicitamente.
     */
    fun seleccionarTab(tabId: Int) {
        val destino = tabId.takeIf { it in TABS } ?: R.id.navSalon
        if (navegacion.selectedItemId == destino) {
            mostrarTab(destino)
        } else {
            navegacion.selectedItemId = destino
        }
    }

    private fun mostrarTab(tabId: Int) {
        tabActual = tabId
        val transaccion = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)

        TABS.forEach { id ->
            val tag = tagDe(id)
            val fragmento = supportFragmentManager.findFragmentByTag(tag)
            when {
                id == tabId && fragmento == null ->
                    transaccion.add(R.id.contenedorPrincipal, crearFragmento(id), tag)

                id == tabId -> transaccion.show(fragmento!!)
                fragmento != null -> transaccion.hide(fragmento)
            }
        }
        // commitNow evita que dos toques rapidos agreguen fragmentos duplicados.
        transaccion.commitNow()
    }

    private fun crearFragmento(tabId: Int): Fragment = when (tabId) {
        R.id.navPedidos -> PedidosFragment()
        R.id.navPerfil -> PerfilFragment()
        else -> SalonFragment()
    }

    private fun tagDe(tabId: Int): String = "tab_$tabId"

    private companion object {
        const val KEY_TAB = "key_tab_actual"

        val TABS = listOf(R.id.navSalon, R.id.navPedidos, R.id.navPerfil)
    }
}
