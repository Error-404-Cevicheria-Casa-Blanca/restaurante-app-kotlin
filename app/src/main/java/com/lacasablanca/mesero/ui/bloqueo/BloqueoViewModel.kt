package com.lacasablanca.mesero.ui.bloqueo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.local.SessionManager
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
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
 * Bloqueo rapido con PIN de 4 digitos (PRB-81).
 *
 * El ViewModel acumula los digitos, valida automaticamente al completar los
 * cuatro y confirma la identidad contra [AuthRepository.validarPin] usando el
 * usuario de la sesion activa. No modifica ni reemplaza el token actual.
 */
class BloqueoViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _estado = MutableStateFlow(BloqueoUiState())
    val estado: StateFlow<BloqueoUiState> = _estado.asStateFlow()

    private val _eventos = Channel<BloqueoEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    init {
        resolverUsuario()
    }

    /** Agrega un digito y dispara la validacion al llegar a cuatro. */
    fun agregarDigito(digito: Char) {
        val actual = _estado.value
        if (actual.cargando || actual.pin.length >= BloqueoUiState.LONGITUD_PIN) return
        if (digito !in '0'..'9') return

        val nuevoPin = actual.pin + digito
        _estado.value = actual.copy(pin = nuevoPin, error = null)
        if (nuevoPin.length == BloqueoUiState.LONGITUD_PIN) {
            validarPin(nuevoPin)
        }
    }

    /** Borra el ultimo digito y limpia el error anterior. */
    fun borrarDigito() {
        val actual = _estado.value
        if (actual.cargando) return
        _estado.value = actual.copy(pin = actual.pin.dropLast(1), error = null)
    }

    private fun validarPin(pin: String) {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            val usuario = resolverUsuarioActual()
            if (usuario.isBlank()) {
                _estado.update { it.copy(cargando = false, pin = "", error = ErrorBloqueo.SESION) }
                _eventos.send(BloqueoEvento.SesionExpirada)
                return@launch
            }

            val resultado = try {
                authRepository.validarPin(usuario, pin)
            } catch (e: Exception) {
                // Un fallo inesperado del almacen o del repositorio no debe cerrar la app.
                Resultado.Fallo(ErrorApp.DESCONOCIDO)
            }

            when (resultado) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(cargando = false, pin = "") }
                    _eventos.send(BloqueoEvento.Desbloqueado)
                }

                is Resultado.Fallo -> {
                    val error = resultado.error.aErrorBloqueo()
                    _estado.update { it.copy(cargando = false, pin = "", error = error) }
                    if (resultado.error == ErrorApp.SESION_EXPIRADA) {
                        _eventos.send(BloqueoEvento.SesionExpirada)
                    }
                }
            }
        }
    }

    /** Lee la sesion de memoria; si el proceso fue recreado, la recupera del almacen. */
    private suspend fun resolverUsuarioActual(): String {
        if (sessionManager.sesion == null) {
            try {
                sessionManager.cargar()
            } catch (e: Exception) {
                return ""
            }
        }
        return sessionManager.sesion?.usuario?.nombre?.trim().orEmpty()
    }

    private fun resolverUsuario() {
        viewModelScope.launch(ioDispatcher) {
            val usuario = resolverUsuarioActual()
            _estado.update { it.copy(usuario = usuario) }
        }
    }

    private fun ErrorApp.aErrorBloqueo(): ErrorBloqueo = when (this) {
        ErrorApp.CREDENCIALES -> ErrorBloqueo.PIN_INCORRECTO
        ErrorApp.RED -> ErrorBloqueo.RED
        ErrorApp.BLOQUEADO -> ErrorBloqueo.BLOQUEADO
        ErrorApp.SESION_EXPIRADA -> ErrorBloqueo.SESION
        else -> ErrorBloqueo.DESCONOCIDO
    }
}
