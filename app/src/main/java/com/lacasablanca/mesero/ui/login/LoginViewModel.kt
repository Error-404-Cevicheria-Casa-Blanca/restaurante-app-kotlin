package com.lacasablanca.mesero.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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

    fun ingresar() {
        val actual = _estado.value
        if (actual.cargando) return

        if (actual.usuario.isBlank() || actual.clave.isBlank()) {
            _estado.update { it.copy(error = ErrorLogin.CAMPOS_VACIOS) }
            return
        }

        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = authRepository.iniciarSesion(actual.usuario, actual.clave)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(cargando = false) }
                    _eventos.send(LoginEvento.IrAMesas)
                }

                is Resultado.Fallo -> {
                    _estado.update { it.copy(cargando = false, error = resultado.error.aErrorLogin()) }
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
}
