package com.lacasablanca.mesero.ui.impresion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Aviso que la pantalla traduce a un Snackbar. */
enum class AvisoImpresion {
    ENVIADA,
    ERROR_CONEXION,
    SESION_EXPIRADA,
    ERROR
}

data class ImpresionUiState(
    val imprimiendo: Boolean = false,
    val aviso: AvisoImpresion? = null
)

/**
 * Impresion y reimpresion de la pre-cuenta (PRB-73).
 *
 * El backend Java centraliza la ticketera: la app solo encola el trabajo con
 * `POST /api/v1/mesas/{id}/precuenta` o `.../precuenta/reimprimir` (ADR-02).
 * Se comparte entre CobroFragment y ComandaFragment para no duplicar estado.
 */
class ImpresionPrecuentaViewModel(
    private val mesaId: Long,
    private val cobroRepository: CobroRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _estado = MutableStateFlow(ImpresionUiState())
    val estado: StateFlow<ImpresionUiState> = _estado.asStateFlow()

    fun imprimirPrecuenta() {
        ejecutar { cobroRepository.imprimirPrecuenta(mesaId) }
    }

    fun reimprimirPrecuenta() {
        ejecutar { cobroRepository.reimprimirPrecuenta(mesaId) }
    }

    /** La pantalla llama tras mostrar el Snackbar para no repetirlo al rotar. */
    fun consumirAviso() {
        _estado.update { it.copy(aviso = null) }
    }

    private fun ejecutar(llamada: suspend () -> Resultado<*>) {
        if (mesaId <= 0L || _estado.value.imprimiendo) return

        _estado.update { it.copy(imprimiendo = true, aviso = null) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = llamada()) {
                is Resultado.Exito -> _estado.update {
                    it.copy(imprimiendo = false, aviso = AvisoImpresion.ENVIADA)
                }

                is Resultado.Fallo -> _estado.update {
                    it.copy(imprimiendo = false, aviso = resultado.error.aAviso())
                }
            }
        }
    }

    private fun ErrorApp.aAviso(): AvisoImpresion = when (this) {
        ErrorApp.RED -> AvisoImpresion.ERROR_CONEXION
        ErrorApp.SESION_EXPIRADA -> AvisoImpresion.SESION_EXPIRADA
        else -> AvisoImpresion.ERROR
    }
}
