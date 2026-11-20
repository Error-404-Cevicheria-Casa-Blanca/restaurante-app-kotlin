package com.lacasablanca.mesero.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.core.Sincronizacion
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

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sincronizacion: Sincronizacion? = null
) : ViewModel() {

    private val _estado = MutableStateFlow(LoginUiState())
    val estado: StateFlow<LoginUiState> = _estado.asStateFlow()

    private val _eventos = Channel<LoginEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    init {
        comprobarSesionExistente()
    }

    fun onUsuarioCambiado(valor: String) {
        _estado.update { it.copy(usuario = valor, error = null) }
    }

    fun onClaveCambiada(valor: String) {
        _estado.update { it.copy(clave = valor, error = null) }
    }

    fun onMostrarClaveCambiado(mostrar: Boolean) {
        _estado.update { it.copy(mostrarClave = mostrar) }
    }

    /** Alterna entre el formulario de credenciales y el teclado PIN (PRB-101). */
    fun alternarModoPin() {
        _estado.update { it.copy(modoPin = !it.modoPin, pin = "", error = null) }
    }

    /** Agrega un digito y valida automaticamente al completar los cuatro. */
    fun agregarDigitoPin(digito: Char) {
        val actual = _estado.value
        if (!actual.modoPin || actual.cargando) return
        if (actual.pin.length >= LoginUiState.LONGITUD_PIN) return
        if (digito !in '0'..'9') return

        val nuevoPin = actual.pin + digito
        _estado.value = actual.copy(pin = nuevoPin, error = null)
        if (nuevoPin.length == LoginUiState.LONGITUD_PIN) {
            iniciarSesionPorPin(nuevoPin)
        }
    }

    /** Borra el ultimo digito del PIN y limpia el error anterior. */
    fun borrarDigitoPin() {
        val actual = _estado.value
        if (!actual.modoPin || actual.cargando) return
        _estado.value = actual.copy(pin = actual.pin.dropLast(1), error = null)
    }

    fun ingresar() {
        val actual = _estado.value
        if (actual.cargando || actual.modoPin) return

        if (actual.usuario.isBlank() || actual.clave.isBlank()) {
            _estado.update { it.copy(error = ErrorLogin.CAMPOS_VACIOS) }
            return
        }

        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = authRepository.iniciarSesion(actual.usuario, actual.clave)) {
                is Resultado.Exito -> {
                    sincronizacion?.programar()
                    _estado.update { it.copy(cargando = false) }
                    _eventos.send(LoginEvento.IrAMesas)
                }

                is Resultado.Fallo -> {
                    _estado.update { it.copy(cargando = false, error = resultado.error.aErrorLogin()) }
                }
            }
        }
    }

    private fun iniciarSesionPorPin(pin: String) {
        val actual = _estado.value
        if (actual.usuario.isBlank()) {
            _estado.update { it.copy(pin = "", error = ErrorLogin.USUARIO_REQUERIDO) }
            return
        }

        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = authRepository.iniciarSesionPorPin(actual.usuario, pin)) {
                is Resultado.Exito -> {
                    sincronizacion?.programar()
                    _estado.update { it.copy(cargando = false, pin = "") }
                    _eventos.send(LoginEvento.IrAMesas)
                }

                is Resultado.Fallo -> {
                    _estado.update {
                        it.copy(cargando = false, pin = "", error = resultado.error.aErrorLoginPin())
                    }
                }
            }
        }
    }

    private fun comprobarSesionExistente() {
        viewModelScope.launch(ioDispatcher) {
            val sesion = try {
                authRepository.sesionGuardada()
            } catch (e: Exception) {
                null
            }
            if (sesion != null) {
                _eventos.send(LoginEvento.IrAMesas)
            }
        }
    }

    private fun ErrorApp.aErrorLogin(): ErrorLogin = when (this) {
        ErrorApp.CREDENCIALES -> ErrorLogin.CREDENCIALES
        ErrorApp.RED -> ErrorLogin.RED
        ErrorApp.ROL_NO_PERMITIDO -> ErrorLogin.ROL_NO_PERMITIDO
        else -> ErrorLogin.DESCONOCIDO
    }

    private fun ErrorApp.aErrorLoginPin(): ErrorLogin = when (this) {
        ErrorApp.CREDENCIALES -> ErrorLogin.PIN_INCORRECTO
        ErrorApp.BLOQUEADO -> ErrorLogin.BLOQUEADO
        ErrorApp.VALIDACION -> ErrorLogin.VALIDACION
        ErrorApp.RED -> ErrorLogin.RED
        ErrorApp.ROL_NO_PERMITIDO -> ErrorLogin.ROL_NO_PERMITIDO
        else -> ErrorLogin.DESCONOCIDO
    }
}
