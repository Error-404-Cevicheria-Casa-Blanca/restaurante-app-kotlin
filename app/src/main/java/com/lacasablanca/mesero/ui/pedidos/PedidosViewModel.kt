package com.lacasablanca.mesero.ui.pedidos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.PedidoActivo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Logica de la pantalla Pedidos (PRB-88, Fase 6).
 *
 * - Carga `GET /api/v1/pedidos` via PedidoRepository.
 * - Descarta COBRADO/ANULADO y estados desconocidos.
 * - Filtra por origen (Mesa / Para Llevar, que incluye Delivery).
 * - Emite el evento de cobro solo para pedidos sin mesa.
 */
class PedidosViewModel(
    private val pedidoRepository: PedidoRepository,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _estado = MutableStateFlow(PedidosUiState())
    val estado: StateFlow<PedidosUiState> = _estado.asStateFlow()

    private val _eventos = Channel<PedidosEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    init {
        cargar()
    }

    /**
     * [mostrarCargando] en false conserva la lista al volver a la pantalla.
     * [mostrarAviso] muestra el Snackbar de "Pedidos actualizados" solo cuando
     * lo pide el boton Actualizar.
     */
    fun cargar(mostrarCargando: Boolean = true, mostrarAviso: Boolean = false) {
        if (mostrarCargando) {
            _estado.update { it.copy(cargando = true, error = null) }
        } else {
            _estado.update { it.copy(error = null) }
        }

        viewModelScope.launch(ioDispatcher) {
            when (val resultado = pedidoRepository.listarPedidosActivos()) {
                is Resultado.Exito -> {
                    val activos = resultado.datos.filter { it.estaActivo() }
                    _estado.update {
                        it.copy(cargando = false, pedidos = activos, error = null)
                    }
                    if (mostrarAviso) _eventos.send(PedidosEvento.Actualizados)
                }

                is Resultado.Fallo -> manejarFallo(resultado.error, mostrarCargando)
            }
        }
    }

    /** Refresco silencioso al entrar al tab o volver de un detalle. */
    fun recargarAlVolver() {
        cargar(mostrarCargando = false)
    }

    fun filtrar(filtro: FiltroPedidos) {
        _estado.update { it.copy(filtro = filtro) }
    }

    /** Solo los pedidos sin mesa se pueden cobrar desde esta pantalla. */
    fun cobrar(pedido: PedidoActivo) {
        if (!pedido.puedeCobrar) return
        _eventos.trySend(
            PedidosEvento.IrACobroPedido(pedido.id, pedido.clienteNombre)
        )
    }

    private suspend fun manejarFallo(error: ErrorApp, mostrarCargando: Boolean) {
        if (error == ErrorApp.SESION_EXPIRADA) {
            authRepository.cerrarSesion()
            _estado.update { it.copy(cargando = false) }
            _eventos.send(PedidosEvento.SesionExpirada)
            return
        }

        _estado.update { actual ->
            // Un refresco silencioso no debe borrar una lista ya visible.
            if (!mostrarCargando && actual.pedidos.isNotEmpty()) {
                actual.copy(cargando = false)
            } else {
                actual.copy(cargando = false, error = error.aErrorPedidos())
            }
        }
    }

    private fun PedidoActivo.estaActivo(): Boolean =
        estado.trim().uppercase() in ESTADOS_ACTIVOS

    private fun ErrorApp.aErrorPedidos(): ErrorPedidos = when (this) {
        ErrorApp.RED -> ErrorPedidos.RED
        ErrorApp.SESION_EXPIRADA -> ErrorPedidos.SESION_EXPIRADA
        ErrorApp.SERVIDOR -> ErrorPedidos.SERVIDOR
        else -> ErrorPedidos.DESCONOCIDO
    }

    private companion object {
        val ESTADOS_ACTIVOS = setOf(
            "PENDIENTE",
            "EN_PREPARACION",
            "LISTO",
            "SERVIDO"
        )
    }
}
