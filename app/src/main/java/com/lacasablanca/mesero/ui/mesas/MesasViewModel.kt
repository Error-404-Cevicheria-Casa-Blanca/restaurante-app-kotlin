package com.lacasablanca.mesero.ui.mesas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
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
 * Estado del mapa de mesas: carga, filtros por zona/estado, contadores
 * y apertura de una mesa libre (PATCH a ATENDIDA, D11).
 */
class MesasViewModel(
    private val mesaRepository: MesaRepository,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _estado = MutableStateFlow(MesasUiState())
    val estado: StateFlow<MesasUiState> = _estado.asStateFlow()

    private val _eventos = Channel<MesasEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    init {
        iniciar()
    }

    fun refrescar() {
        cargar(mostrarAviso = true)
    }

    /**
     * Refresco silencioso al volver a la pantalla (por ejemplo, desde la comanda).
     * No muestra el indicador de carga para no ocultar la cuadricula ya pintada.
     */
    fun recargarAlVolver() {
        cargar(mostrarAviso = false, mostrarCargando = false)
    }

    fun cargar(mostrarAviso: Boolean = false, mostrarCargando: Boolean = true) {
        if (mostrarCargando) {
            _estado.update { it.copy(cargando = true, error = null) }
        } else {
            _estado.update { it.copy(error = null) }
        }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = mesaRepository.listarMesas()) {
                is Resultado.Exito -> {
                    val mesas = resultado.datos
                    _estado.update { actual ->
                        actual.copy(
                            cargando = false,
                            mesas = mesas,
                            error = null,
                            zonas = mesas.map { it.ubicacion }.filter { it.isNotBlank() }.distinct(),
                            zonaSeleccionada = actual.zonaSeleccionada
                                ?.takeIf { zona -> mesas.any { it.ubicacion == zona } }
                        )
                    }
                    if (mostrarAviso) {
                        _eventos.send(MesasEvento.Aviso(AvisoMesas.ACTUALIZADAS))
                    }
                }

                is Resultado.Fallo -> manejarFalloCarga(resultado.error)
            }
        }
    }

    fun filtrarZona(zona: String?) {
        _estado.update { it.copy(zonaSeleccionada = zona) }
    }

    fun filtrarEstado(estado: EstadoMesa?) {
        _estado.update { it.copy(estadoSeleccionado = estado) }
    }

    /** Tocar una mesa LIBRE: PATCH ATENDIDA y abrir comanda. Tocar otra: aviso (D12). */
    fun seleccionarMesa(mesa: Mesa) {
        if (mesa.estado != EstadoMesa.LIBRE) {
            _eventos.trySend(MesasEvento.Aviso(AvisoMesas.MESA_NO_LIBRE, mesa))
            return
        }
        if (_estado.value.mesaAbriendo != null) return

        _estado.update { it.copy(mesaAbriendo = mesa.id) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = mesaRepository.cambiarEstado(mesa.id, EstadoMesa.ATENDIDA)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(mesaAbriendo = null) }
                    reemplazarMesa(resultado.datos)
                    _eventos.send(MesasEvento.IrAComanda(resultado.datos))
                }

                is Resultado.Fallo -> {
                    _estado.update { it.copy(mesaAbriendo = null) }
                    manejarFalloApertura(resultado.error, mesa)
                }
            }
        }
    }

    /** Al volver de la comanda sin enviar, la mesa vuelve a LIBRE (D11). */
    fun liberarMesa(id: Long) {
        viewModelScope.launch(ioDispatcher) {
            val mesa = _estado.value.mesas.firstOrNull { it.id == id } ?: return@launch
            if (mesa.estado != EstadoMesa.ATENDIDA) return@launch

            when (val resultado = mesaRepository.cambiarEstado(id, EstadoMesa.LIBRE)) {
                is Resultado.Exito -> reemplazarMesa(resultado.datos)
                is Resultado.Fallo -> {
                    if (resultado.error == ErrorApp.SESION_EXPIRADA) {
                        manejarSesionExpirada()
                    } else {
                        _eventos.send(MesasEvento.Aviso(AvisoMesas.ERROR_LIBERAR, mesa))
                    }
                }
            }
        }
    }

    fun cerrarSesion() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.cerrarSesion()
            _eventos.send(MesasEvento.SesionCerrada)
        }
    }

    private fun iniciar() {
        viewModelScope.launch(ioDispatcher) {
            val sesion = try {
                authRepository.sesionGuardada()
            } catch (e: Exception) {
                null
            }
            if (sesion == null) {
                _estado.update { it.copy(cargando = false) }
                _eventos.send(MesasEvento.SesionExpirada)
                return@launch
            }
            _estado.update { it.copy(nombreUsuario = sesion.usuario.nombre) }
            cargar()
        }
    }

    private suspend fun manejarFalloCarga(error: ErrorApp) {
        if (error == ErrorApp.SESION_EXPIRADA) {
            manejarSesionExpirada()
        } else {
            _estado.update { it.copy(cargando = false, error = error.aErrorMesas()) }
        }
    }

    private suspend fun manejarFalloApertura(error: ErrorApp, mesa: Mesa) {
        when (error) {
            ErrorApp.SESION_EXPIRADA -> manejarSesionExpirada()
            ErrorApp.CONFLICTO -> {
                _eventos.send(MesasEvento.Aviso(AvisoMesas.MESA_TOMADA, mesa))
                cargar()
            }

            ErrorApp.RED -> _eventos.send(MesasEvento.Aviso(AvisoMesas.ERROR_RED, mesa))
            else -> _eventos.send(MesasEvento.Aviso(AvisoMesas.ERROR_ABRIR, mesa))
        }
    }

    private suspend fun manejarSesionExpirada() {
        authRepository.cerrarSesion()
        _estado.update { it.copy(cargando = false, mesaAbriendo = null) }
        _eventos.send(MesasEvento.SesionExpirada)
    }

    private fun reemplazarMesa(mesa: Mesa) {
        _estado.update { actual ->
            actual.copy(mesas = actual.mesas.map { if (it.id == mesa.id) mesa else it })
        }
    }

    private fun ErrorApp.aErrorMesas(): ErrorMesas = when (this) {
        ErrorApp.SESION_EXPIRADA -> ErrorMesas.SESION_EXPIRADA
        ErrorApp.RED -> ErrorMesas.RED
        ErrorApp.SERVIDOR -> ErrorMesas.SERVIDOR
        else -> ErrorMesas.DESCONOCIDO
    }
}
