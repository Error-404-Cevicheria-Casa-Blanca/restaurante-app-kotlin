package com.lacasablanca.mesero.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de conectividad para el banner offline (PRB-62).
 * Usa la red por defecto del sistema y se actualiza con un callback.
 */
class NetworkMonitor(contexto: Context) {

    private val gestor = contexto.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val _enLinea = MutableStateFlow(estaEnLineaAhora())
    val enLinea: StateFlow<Boolean> = _enLinea.asStateFlow()

    private var registrado = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _enLinea.value = true
        }

        override fun onLost(network: Network) {
            _enLinea.value = estaEnLineaAhora()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capacidades: NetworkCapabilities
        ) {
            _enLinea.value = capacidades.tieneInternet()
        }
    }

    fun iniciar() {
        if (registrado) return
        val conexiones = gestor ?: return
        runCatching { conexiones.registerDefaultNetworkCallback(callback) }
        registrado = true
    }

    fun detener() {
        if (!registrado) return
        val conexiones = gestor ?: return
        runCatching { conexiones.unregisterNetworkCallback(callback) }
        registrado = false
    }

    fun estaEnLineaAhora(): Boolean {
        val conexiones = gestor ?: return false
        val red = conexiones.activeNetwork ?: return false
        val capacidades = conexiones.getNetworkCapabilities(red) ?: return false
        return capacidades.tieneInternet()
    }

    private fun NetworkCapabilities.tieneInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
