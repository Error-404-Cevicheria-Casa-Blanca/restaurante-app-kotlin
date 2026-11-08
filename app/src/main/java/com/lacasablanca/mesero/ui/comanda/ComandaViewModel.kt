package com.lacasablanca.mesero.ui.comanda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
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
 * Cierra la comanda base (HU-03 / PRB-39).
 *
 * Si el mesero sale sin enviar el pedido, la mesa vuelve a LIBRE con
 * `PATCH /api/v1/mesas/{id}/estado` (D11). El envio real del pedido llega
 * con PRB-38; entonces la Activity pasara `EXTRA_PEDIDO_ENVIADO = true`
 * y esta liberacion no se ejecutara.
 */
class ComandaViewModel(
    private val mesaRepository: MesaRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _estado = MutableStateFlow(ComandaUiState())
    val estado: StateFlow<ComandaUiState> = _estado.asStateFlow()

    private val _eventos = Channel<ComandaEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    /**
     * Libera la mesa. Es idempotente mientras el PATCH esta en vuelo: los
     * toques repetidos del boton Atras no disparan peticiones duplicadas.
     */
    fun liberarMesa(mesaId: Long) {
        if (mesaId <= 0L || _estado.value.liberandoMesa) return

        _estado.update { it.copy(liberandoMesa = true) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = mesaRepository.cambiarEstado(mesaId, EstadoMesa.LIBRE)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(liberandoMesa = false) }
                    _eventos.send(ComandaEvento.MesaLiberada)
                }

                is Resultado.Fallo -> {
                    _estado.update { it.copy(liberandoMesa = false) }
                    if (resultado.error == ErrorApp.SESION_EXPIRADA) {
                        _eventos.send(ComandaEvento.SesionExpirada)
                    } else {
                        _eventos.send(ComandaEvento.ErrorAlLiberar(resultado.error))
                    }
                }
            }
        }
    }

    /**
     * Pide la cuenta a caja (PRB-83): PATCH /api/v1/mesas/{id}/estado con
     * `CUENTA_SOLICITADA`. Es idempotente mientras el PATCH esta en vuelo.
     */
    fun pedirCuenta(mesaId: Long) {
        if (mesaId <= 0L || _estado.value.solicitandoCuenta) return

        _estado.update { it.copy(solicitandoCuenta = true) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = mesaRepository.cambiarEstado(mesaId, EstadoMesa.CUENTA_SOLICITADA)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(solicitandoCuenta = false) }
                    _eventos.send(ComandaEvento.CuentaSolicitada)
                }

                is Resultado.Fallo -> {
                    _estado.update { it.copy(solicitandoCuenta = false) }
                    if (resultado.error == ErrorApp.SESION_EXPIRADA) {
                        _eventos.send(ComandaEvento.SesionExpirada)
                    } else {
                        _eventos.send(ComandaEvento.ErrorAlPedirCuenta(resultado.error))
                    }
                }
            }
        }
    }
}
