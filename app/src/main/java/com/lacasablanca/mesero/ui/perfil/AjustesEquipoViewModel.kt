package com.lacasablanca.mesero.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.repository.ConfiguracionEquipoRepository
import com.lacasablanca.mesero.data.repository.ResultadoConfiguracion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Logica del dialogo de Ajustes de equipo (PRB-99). Lee la configuracion
 * efectiva al abrir, valida y persiste, y expone errores tipados para la UI.
 */
class AjustesEquipoViewModel(
    private val configuracionRepository: ConfiguracionEquipoRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _estado = MutableStateFlow(AjustesEquipoUiState())
    val estado: StateFlow<AjustesEquipoUiState> = _estado.asStateFlow()

    fun abrir() {
        if (_estado.value.abierto) return
        _estado.update {
            it.copy(
                abierto = true,
                url = configuracionRepository.urlActual(),
                terminal = configuracionRepository.terminalActual(),
                guardando = false,
                guardado = false,
                errorUrl = null,
                errorTerminal = null,
                errorGeneral = false
            )
        }
    }

    fun cerrar() {
        _estado.update { it.copy(abierto = false) }
    }

    fun onUrlCambiada(valor: String) {
        _estado.update {
            it.copy(url = valor, errorUrl = null, errorGeneral = false, guardado = false)
        }
    }

    fun onTerminalCambiada(valor: String) {
        _estado.update {
            it.copy(terminal = valor, errorTerminal = null, errorGeneral = false, guardado = false)
        }
    }

    fun guardar() {
        val actual = _estado.value
        if (actual.guardando || !actual.abierto) return
        _estado.update {
            it.copy(
                guardando = true,
                guardado = false,
                errorUrl = null,
                errorTerminal = null,
                errorGeneral = false
            )
        }
        viewModelScope.launch(ioDispatcher) {
            val resultado = try {
                configuracionRepository.guardar(actual.url, actual.terminal)
            } catch (e: Exception) {
                null
            }
            aplicarResultado(resultado)
        }
    }

    private fun aplicarResultado(resultado: ResultadoConfiguracion?) {
        when (resultado) {
            is ResultadoConfiguracion.Guardado -> _estado.update {
                it.copy(
                    guardando = false,
                    guardado = true,
                    url = resultado.configuracion.serverUrl,
                    terminal = resultado.configuracion.terminalCodigo
                )
            }

            is ResultadoConfiguracion.Invalido -> _estado.update {
                it.copy(
                    guardando = false,
                    errorUrl = resultado.errorUrl,
                    errorTerminal = resultado.errorTerminal
                )
            }

            null -> _estado.update { it.copy(guardando = false, errorGeneral = true) }
        }
    }
}
