package com.lacasablanca.mesero.ui.cobro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.remote.dto.CobrarRequest
import com.lacasablanca.mesero.data.remote.dto.PagoRequest
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.MetodoPago
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Cobro de una mesa (PRB-56). Carga la cuenta, arma los pagos y confirma
 * contra el repositorio; con USE_FAKE_DATA responde FakeCobroRepository.
 * El identificador de idempotencia se genera una sola vez por pantalla.
 */
class CobroViewModel(
    private val mesaId: Long,
    private val cobroRepository: CobroRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val clientRequestId = UUID.randomUUID().toString()

    private val _estado = MutableStateFlow(CobroUiState())
    val estado: StateFlow<CobroUiState> = _estado.asStateFlow()

    init {
        cargarCuenta()
    }

    fun cargarCuenta() {
        _estado.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = cobroRepository.obtenerCuenta(mesaId)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(
                        cuenta = resultado.datos,
                        loading = false,
                        error = null
                    ).calcularTotales()
                }

                is Resultado.Fallo -> _estado.update {
                    it.copy(loading = false, error = resultado.error.aCodigoCobro())
                }
            }
        }
    }

    fun seleccionarMetodo(metodo: MetodoPago) {
        _estado.update {
            val vuelto = if (metodo == MetodoPago.EFECTIVO) {
                (it.montoPago.aBigDecimal() - it.saldoPendiente).max(BigDecimal.ZERO)
            } else {
                BigDecimal.ZERO
            }
            it.copy(
                metodoSeleccionado = metodo,
                referencia = if (metodo == MetodoPago.EFECTIVO) "" else it.referencia,
                vuelto = vuelto
            ).calcularTotales()
        }
    }

    fun actualizarMontoPago(texto: String) {
        val limpio = texto.aTextoMonto()
        _estado.update {
            val monto = limpio.aBigDecimal()
            val vuelto = if (it.metodoSeleccionado == MetodoPago.EFECTIVO) {
                (monto - it.saldoPendiente).max(BigDecimal.ZERO)
            } else {
                BigDecimal.ZERO
            }
            it.copy(montoPago = limpio, vuelto = vuelto).calcularTotales()
        }
    }

    fun actualizarReferencia(texto: String) {
        _estado.update { it.copy(referencia = texto) }
    }

    fun actualizarPropina(texto: String) {
        val limpio = texto.aTextoMonto()
        _estado.update {
            val nuevaPropina = limpio.aBigDecimal()
            val nuevoTotal = (it.cuenta?.total ?: BigDecimal.ZERO).add(nuevaPropina)
            val saldo = (nuevoTotal - it.totalPagado).max(BigDecimal.ZERO)
            val monto = it.montoPago.aBigDecimal()
            val vuelto = if (it.metodoSeleccionado == MetodoPago.EFECTIVO) {
                (monto - saldo).max(BigDecimal.ZERO)
            } else {
                BigDecimal.ZERO
            }
            it.copy(propina = nuevaPropina, vuelto = vuelto).calcularTotales()
        }
    }

    fun agregarPago() {
        val actual = _estado.value
        if (actual.cuenta == null) return

        val saldo = actual.saldoPendiente
        if (saldo <= BigDecimal.ZERO) {
            _estado.update { it.copy(error = ERROR_SIN_SALDO) }
            return
        }

        val montoIngresado = actual.montoPago.aBigDecimal()
            .setScale(2, RoundingMode.HALF_UP)
        if (montoIngresado <= BigDecimal.ZERO) {
            _estado.update { it.copy(error = ERROR_MONTO) }
            return
        }

        val esEfectivo = actual.metodoSeleccionado == MetodoPago.EFECTIVO
        if (!esEfectivo && montoIngresado > saldo) {
            _estado.update { it.copy(error = ERROR_MONTO_EXCEDE) }
            return
        }

        if (!esEfectivo && actual.referencia.isBlank()) {
            _estado.update { it.copy(error = ERROR_REFERENCIA) }
            return
        }

        val montoRegistrado = if (esEfectivo) {
            montoIngresado.min(saldo)
        } else {
            montoIngresado
        }
        val vuelto = if (esEfectivo) {
            (montoIngresado - montoRegistrado).max(BigDecimal.ZERO)
        } else {
            BigDecimal.ZERO
        }

        _estado.update {
            it.copy(
                pagos = it.pagos + PagoRequest(
                    metodo = actual.metodoSeleccionado.valorApi,
                    monto = montoRegistrado,
                    referencia = if (esEfectivo) null else actual.referencia.trim()
                ),
                montoPago = "",
                referencia = "",
                vuelto = vuelto,
                error = null
            ).calcularTotales()
        }
    }

    fun eliminarPago(indice: Int) {
        _estado.update {
            if (indice !in it.pagos.indices) return@update it
            it.copy(
                pagos = it.pagos.filterIndexed { posicion, _ -> posicion != indice },
                vuelto = BigDecimal.ZERO,
                error = null
            ).calcularTotales()
        }
    }

    fun establecerDivision(personas: Int): Boolean {
        if (personas < MIN_COMENSALES || personas > MAX_COMENSALES) {
            _estado.update { it.copy(error = ERROR_COMENSALES) }
            return false
        }
        _estado.update {
            it.copy(
                dividirCuenta = true,
                numeroComensales = personas,
                error = null
            ).calcularTotales()
        }
        return true
    }

    fun desactivarDivision() {
        _estado.update {
            it.copy(
                dividirCuenta = false,
                numeroComensales = 0
            ).calcularTotales()
        }
    }

    fun confirmarCobro() {
        val actual = _estado.value
        if (actual.loading) return
        if (!actual.confirmarHabilitado || actual.cuenta == null) {
            _estado.update { it.copy(error = ERROR_VALIDACION) }
            return
        }

        val request = CobrarRequest(
            pagos = actual.pagos,
            propina = actual.propina,
            observacion = null,
            clientRequestId = clientRequestId
        )
        _estado.update { it.copy(loading = true, error = null) }

        viewModelScope.launch(ioDispatcher) {
            when (val resultado = cobroRepository.cobrar(mesaId, request)) {
                is Resultado.Exito -> {
                    val vuelto = actual.vuelto.max(resultado.datos.vuelto ?: BigDecimal.ZERO)
                    _estado.update {
                        it.copy(loading = false, exito = true, vuelto = vuelto, error = null)
                    }
                }

                is Resultado.Fallo -> _estado.update {
                    it.copy(loading = false, error = resultado.error.aCodigoCobro())
                }
            }
        }
    }

    private fun ErrorApp.aCodigoCobro(): String = when (this) {
        ErrorApp.RED -> ERROR_RED
        ErrorApp.SESION_EXPIRADA -> ERROR_SESION
        ErrorApp.VALIDACION -> ERROR_VALIDACION
        ErrorApp.CONFLICTO -> ERROR_CONFLICTO
        ErrorApp.CONFLICTO_COBRO -> ERROR_CONFLICTO_PAGADA
        ErrorApp.BLOQUEADO -> ERROR_BLOQUEADO
        ErrorApp.SERVIDOR -> ERROR_SERVIDOR
        else -> ERROR_DESCONOCIDO
    }

    /** Normaliza el texto a un monto con un solo separador decimal. */
    private fun String.aTextoMonto(): String {
        val filtrado = replace(",", ".").filter { it.isDigit() || it == '.' }
        val primerPunto = filtrado.indexOf('.')
        if (primerPunto < 0) return filtrado
        val enteros = filtrado.substring(0, primerPunto)
        val decimales = filtrado.substring(primerPunto + 1).replace(".", "")
        return "$enteros.$decimales"
    }

    private fun String.aBigDecimal(): BigDecimal {
        if (isBlank()) return BigDecimal.ZERO
        return try {
            BigDecimal(this)
        } catch (e: NumberFormatException) {
            BigDecimal.ZERO
        }
    }

    companion object {
        const val MIN_COMENSALES = 2
        const val MAX_COMENSALES = 50

        const val ERROR_RED = "RED"
        const val ERROR_SESION = "SESION"
        const val ERROR_VALIDACION = "VALIDACION"
        const val ERROR_MONTO = "MONTO"
        const val ERROR_MONTO_EXCEDE = "MONTO_EXCEDE"
        const val ERROR_SIN_SALDO = "SIN_SALDO"
        const val ERROR_REFERENCIA = "REFERENCIA"
        const val ERROR_COMENSALES = "COMENSALES"
        const val ERROR_CONFLICTO = "CONFLICTO"
        const val ERROR_CONFLICTO_PAGADA = "CONFLICTO_PAGADA"
        const val ERROR_BLOQUEADO = "BLOQUEADO"
        const val ERROR_SERVIDOR = "SERVIDOR"
        const val ERROR_DESCONOCIDO = "DESCONOCIDO"
    }
}
