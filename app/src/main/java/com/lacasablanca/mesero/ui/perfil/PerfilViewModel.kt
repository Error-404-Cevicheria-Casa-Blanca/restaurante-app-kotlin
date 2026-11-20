package com.lacasablanca.mesero.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.core.Sincronizacion
import com.lacasablanca.mesero.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Logica del perfil del mesero (PRB-88, Fase 3).
 *
 * Depende solo de [AuthRepository] (que a su vez usa SessionManager). No
 * conoce Fragment, Activity ni Context, por lo que es independiente de la
 * pantalla y se puede probar con un repositorio fake.
 *
 * El login ya entrega nombre visible, turno y terminal (PRB-85); si el backend
 * no asigna alguno, la UI los muestra como "No asignado".
 */
class PerfilViewModel(
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fechaActual: () -> LocalDate = { LocalDate.now() },
    private val sincronizacion: Sincronizacion? = null
) : ViewModel() {

    private val _estado = MutableStateFlow(PerfilUiState())
    val estado: StateFlow<PerfilUiState> = _estado.asStateFlow()

    private val _eventos = Channel<PerfilEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    init {
        cargarPerfil()
    }

    fun cargarPerfil() {
        _estado.update { it.copy(cargando = true) }
        viewModelScope.launch(ioDispatcher) {
            val sesion = try {
                authRepository.sesionGuardada()
            } catch (e: Exception) {
                null
            }
            if (sesion == null) {
                _estado.update { it.copy(cargando = false) }
                _eventos.send(PerfilEvento.IrALogin)
                return@launch
            }

            val usuario = sesion.usuario
            _estado.update {
                it.copy(
                    cargando = false,
                    nombre = usuario.nombreVisible.ifBlank { usuario.nombre },
                    rol = usuario.rol,
                    usuario = usuario.nombre,
                    terminal = usuario.terminal
                        ?.let { terminal -> terminal.nombre.ifBlank { terminal.codigo } }
                        .orEmpty(),
                    turno = usuario.turno?.nombre.orEmpty(),
                    fecha = fechaActual().format(FORMATO_FECHA)
                )
            }
        }
    }

    /**
     * Limpia la sesion persistida. Aunque el borrado local falle, la pantalla
     * vuelve al login: no se deja al usuario atrapado en el perfil.
     */
    fun cerrarSesion() {
        if (_estado.value.cerrandoSesion) return
        _estado.update { it.copy(cerrandoSesion = true) }
        viewModelScope.launch(ioDispatcher) {
            try {
                authRepository.cerrarSesion()
            } catch (e: Exception) {
                // La sesion local ya no es confiable; el login pedira de nuevo.
            }
            sincronizacion?.cancelar()
            _eventos.send(PerfilEvento.SesionCerrada)
        }
    }

    private companion object {
        val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
