package com.lacasablanca.mesero.ui.impresion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.remote.dto.TrabajoImpresionDto
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Estado de dominio de la cola de impresion que pinta la pantalla. */
enum class EstadoColaImpresion {
    PENDIENTE,
    IMPRESO,
    FALLIDO,
    DESCONOCIDO
}

/** Aviso que la pantalla traduce a un Snackbar. */
enum class AvisoImpresion {
    ENCOLADA,
    ERROR_CONEXION,
    SESION_EXPIRADA,
    ERROR
}

data class ImpresionUiState(
    val imprimiendo: Boolean = false,
    val consultando: Boolean = false,
    val estadoCola: EstadoColaImpresion? = null,
    val intentos: Int? = null,
    val mensajeError: String? = null,
    val sinConfirmacion: Boolean = false,
    val aviso: AvisoImpresion? = null
)

/**
 * Impresion y reimpresion de la pre-cuenta (PRB-73 / PRB-109).
 *
 * El backend Java centraliza la ticketera: la app encola el trabajo con
 * `POST /api/v1/mesas/{id}/precuenta` o `.../precuenta/reimprimir` y luego
 * consulta la cola real con `GET /api/v1/mesas/{id}/impresion` (ADR-02).
 *
 * ASSUMPTION(PRB-109): no se hace polling al abrir la pantalla; solo despues
 * de un POST pedido en esta sesion. Intervalo 2 s, timeout 30 s. El PENDIENTE
 * sintetico `job-mesa-*-pendiente` no se pinta como trabajo en curso.
 * Un 2xx del POST nunca significa IMPRESO: solo encolado.
 */
class ImpresionPrecuentaViewModel(
    private val mesaId: Long,
    private val cobroRepository: CobroRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val intervaloPollingMs: Long = INTERVALO_POLLING_MS,
    private val timeoutPollingMs: Long = TIMEOUT_POLLING_MS
) : ViewModel() {

    private val _estado = MutableStateFlow(ImpresionUiState())
    val estado: StateFlow<ImpresionUiState> = _estado.asStateFlow()

    private var trabajoPolling: Job? = null

    fun imprimirPrecuenta() {
        ejecutar { cobroRepository.imprimirPrecuenta(mesaId) }
    }

    fun reimprimirPrecuenta() {
        ejecutar { cobroRepository.reimprimirPrecuenta(mesaId) }
    }

    /** Reintenta la impresion usando el endpoint de reimpresion. */
    fun reintentar() {
        reimprimirPrecuenta()
    }

    /** La pantalla llama tras mostrar el Snackbar para no repetirlo al rotar. */
    fun consumirAviso() {
        _estado.update { it.copy(aviso = null) }
    }

    override fun onCleared() {
        detenerPolling()
        super.onCleared()
    }

    private fun ejecutar(llamada: suspend () -> Resultado<*>) {
        if (mesaId <= 0L || _estado.value.imprimiendo) return

        detenerPolling()
        _estado.update {
            it.copy(
                imprimiendo = true,
                consultando = false,
                estadoCola = null,
                intentos = null,
                mensajeError = null,
                sinConfirmacion = false,
                aviso = null
            )
        }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = llamada()) {
                is Resultado.Exito -> {
                    _estado.update {
                        it.copy(
                            imprimiendo = false,
                            aviso = AvisoImpresion.ENCOLADA,
                            estadoCola = EstadoColaImpresion.PENDIENTE,
                            intentos = 0
                        )
                    }
                    iniciarPolling()
                }

                is Resultado.Fallo -> _estado.update {
                    it.copy(imprimiendo = false, aviso = resultado.error.aAviso())
                }
            }
        }
    }

    private fun iniciarPolling() {
        detenerPolling()
        _estado.update { it.copy(consultando = true) }
        trabajoPolling = viewModelScope.launch(ioDispatcher) {
            var transcurrido = 0L
            while (isActive) {
                val seguir = consultarCola()
                if (!seguir) return@launch

                if (transcurrido >= timeoutPollingMs) {
                    _estado.update { it.copy(consultando = false, sinConfirmacion = true) }
                    return@launch
                }
                delay(intervaloPollingMs)
                transcurrido += intervaloPollingMs
            }
        }
    }

    /**
     * Devuelve `false` cuando el bucle debe cortarse (estado terminal o sesion
     * expirada). Un fallo de red conserva el ultimo estado conocido y sigue
     * consultando; nunca marca IMPRESO.
     */
    private suspend fun consultarCola(): Boolean {
        return when (val resultado = cobroRepository.obtenerEstadoImpresion(mesaId)) {
            is Resultado.Exito -> aplicarTrabajo(resultado.datos)
            is Resultado.Fallo -> aplicarFallo(resultado.error)
        }
    }

    private fun aplicarTrabajo(trabajo: TrabajoImpresionDto): Boolean {
        val estadoCola = trabajo.estado.aEstadoCola()
        _estado.update {
            it.copy(
                estadoCola = estadoCola,
                intentos = trabajo.intentos ?: it.intentos,
                mensajeError = trabajo.mensajeError,
                sinConfirmacion = false
            )
        }

        val terminal = estadoCola == EstadoColaImpresion.IMPRESO ||
            estadoCola == EstadoColaImpresion.FALLIDO
        if (terminal) _estado.update { it.copy(consultando = false) }
        return !terminal
    }

    private fun aplicarFallo(error: ErrorApp): Boolean {
        if (error == ErrorApp.SESION_EXPIRADA) {
            _estado.update {
                it.copy(consultando = false, aviso = AvisoImpresion.SESION_EXPIRADA)
            }
            return false
        }
        _estado.update {
            if (it.aviso == AvisoImpresion.ERROR_CONEXION) {
                it
            } else {
                it.copy(aviso = AvisoImpresion.ERROR_CONEXION)
            }
        }
        return true
    }

    private fun detenerPolling() {
        trabajoPolling?.cancel()
        trabajoPolling = null
        if (_estado.value.consultando) _estado.update { it.copy(consultando = false) }
    }

    private fun String?.aEstadoCola(): EstadoColaImpresion = when (this?.trim()?.uppercase()) {
        "PENDIENTE" -> EstadoColaImpresion.PENDIENTE
        "IMPRESO" -> EstadoColaImpresion.IMPRESO
        "FALLIDO" -> EstadoColaImpresion.FALLIDO
        else -> EstadoColaImpresion.DESCONOCIDO
    }

    private fun ErrorApp.aAviso(): AvisoImpresion = when (this) {
        ErrorApp.RED -> AvisoImpresion.ERROR_CONEXION
        ErrorApp.SESION_EXPIRADA -> AvisoImpresion.SESION_EXPIRADA
        else -> AvisoImpresion.ERROR
    }

    companion object {
        /** Mismo intervalo que el worker Django de PRB-107/108. */
        const val INTERVALO_POLLING_MS = 2_000L

        /** Tope de espera sin confirmacion de la cola. */
        const val TIMEOUT_POLLING_MS = 30_000L
    }
}
