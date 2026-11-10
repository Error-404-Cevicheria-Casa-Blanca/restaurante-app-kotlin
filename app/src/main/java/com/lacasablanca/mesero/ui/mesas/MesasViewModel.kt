package com.lacasablanca.mesero.ui.mesas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.repository.AuthRepository
import com.lacasablanca.mesero.data.repository.MesaRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Estado del mapa de mesas: carga, filtros por zona/estado, contadores,
 * apertura de una mesa libre (PATCH a ATENDIDA, D11) y refresco automatico
 * cada 4 segundos para que las demas tablets vean los cambios (PRB-42).
 */
class MesasViewModel(
    private val mesaRepository: MesaRepository,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * PRB-93: fuente de las lineas ya enviadas para el dialogo de division.
     * Es opcional para no romper construcciones antiguas; la app real siempre
     * lo inyecta desde AppContainer.
     */
    private val pedidoRepository: PedidoRepository? = null
) : ViewModel() {

    private val _estado = MutableStateFlow(MesasUiState())
    val estado: StateFlow<MesasUiState> = _estado.asStateFlow()

    private val _eventos = Channel<MesasEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    /**
     * Errores del polling que la pantalla muestra como Snackbar.
     * El mensaje viaja como codigo ([ERROR_RED]) para que el texto visible
     * siga viviendo en strings.xml; el ViewModel no tiene Context.
     */
    private val _eventosError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val eventosError: SharedFlow<String> = _eventosError.asSharedFlow()

    /** Se completa cuando el ViewModel ya sabe si hay sesion (exito o fallo). */
    private val sesionLista = CompletableDeferred<Boolean>()

    private var pollingJob: Job? = null
    private var pollingFallando = false

    init {
        iniciar()
    }

    fun refrescar() {
        cargar(mostrarAviso = true)
    }

    /**
     * Arranca el refresco automatico del mapa. Es idempotente: si el Job del
     * bucle sigue activo no se crea otro.
     *
     * El bucle consulta en silencio (sin spinner, sin aviso) y espera al final,
     * de modo que este primer ciclo es la unica carga inicial del mapa: el
     * arranque del ViewModel ya no pide mesas por su cuenta.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch(ioDispatcher) {
            if (!sesionLista.await()) return@launch
            while (isActive) {
                cargarMesas(mostrarAviso = false, mostrarCargando = false, silencioso = true)
                delay(INTERVALO_POLLING_MS)
            }
        }
    }

    /** Detiene el bucle. Se puede llamar varias veces sin efectos. */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        pollingFallando = false
    }

    /**
     * Refresco silencioso al volver a la pantalla (por ejemplo, desde la comanda).
     * No muestra el indicador de carga para no ocultar la cuadricula ya pintada.
     */
    fun recargarAlVolver() {
        cargar(mostrarAviso = false, mostrarCargando = false)
    }

    fun cargar(mostrarAviso: Boolean = false, mostrarCargando: Boolean = true) {
        viewModelScope.launch(ioDispatcher) {
            cargarMesas(mostrarAviso = mostrarAviso, mostrarCargando = mostrarCargando)
        }
    }

    private suspend fun cargarMesas(
        mostrarAviso: Boolean = false,
        mostrarCargando: Boolean = true,
        silencioso: Boolean = false
    ) {
        if (mostrarCargando) {
            _estado.update { it.copy(cargando = true, error = null) }
        } else if (!silencioso) {
            // El polling no limpia el error antes de consultar: si falla, la
            // pantalla conserva lo que ya mostraba en lugar de parpadear.
            _estado.update { it.copy(error = null) }
        }
        when (val resultado = mesaRepository.listarMesas()) {
            is Resultado.Exito -> {
                pollingFallando = false
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

            is Resultado.Fallo -> manejarFalloCarga(resultado.error, silencioso)
        }
    }

    fun filtrarZona(zona: String?) {
        _estado.update { it.copy(zonaSeleccionada = zona) }
    }

    fun filtrarEstado(estado: EstadoMesa?) {
        _estado.update { it.copy(estadoSeleccionado = estado) }
    }

    /**
     * Tocar una mesa LIBRE: PATCH ATENDIDA y abrir comanda.
     * Tocar una mesa OCUPADA: abrir el pedido existente en modo lectura (PRB-83).
     * Tocar una mesa ATENDIDA: ofrecer retomar la comanda o liberarla (PRB-83).
     * CUENTA_SOLICITADA se gestiona antes, en la Activity (abre el cobro).
     */
    fun seleccionarMesa(mesa: Mesa) {
        when (mesa.estado) {
            EstadoMesa.LIBRE -> abrirMesaLibre(mesa)
            EstadoMesa.OCUPADA -> _eventos.trySend(MesasEvento.IrAComandaLectura(mesa))
            EstadoMesa.ATENDIDA -> _eventos.trySend(MesasEvento.OpcionesMesaAtendida(mesa))
            else -> _eventos.trySend(MesasEvento.Aviso(AvisoMesas.MESA_NO_LIBRE, mesa))
        }
    }

    private fun abrirMesaLibre(mesa: Mesa) {
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
                is Resultado.Exito -> {
                    reemplazarMesa(resultado.datos)
                    _eventos.send(MesasEvento.MesaLiberada(id))
                }

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

    /** PRB-93: abre el menu premium de acciones de la mesa. */
    fun abrirAcciones(mesa: Mesa) {
        _eventos.trySend(MesasEvento.MostrarAcciones(mesa))
    }

    /**
     * PRB-93: carga las lineas ya enviadas para el dialogo de division.
     * Solo se pueden mover lineas con `itemId`: el backend exige los ids del
     * detalle y no acepta inventarlos.
     */
    fun prepararDivision(mesa: Mesa) {
        val repositorio = pedidoRepository
        if (repositorio == null) {
            _eventos.trySend(MesasEvento.Aviso(AvisoMesas.SIN_PLATOS_DIVIDIR, mesa))
            return
        }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = repositorio.obtenerPedidoDeMesa(mesa.id)) {
                is Resultado.Exito -> {
                    val lineas = resultado.datos.lineas.filter { (it.itemId ?: 0L) > 0L }
                    if (lineas.isEmpty()) {
                        _eventos.send(MesasEvento.Aviso(AvisoMesas.SIN_PLATOS_DIVIDIR, mesa))
                    } else {
                        _eventos.send(
                            MesasEvento.MostrarDivision(
                                mesa = mesa,
                                lineas = lineas,
                                destinos = destinosParaDivision(mesa)
                            )
                        )
                    }
                }

                is Resultado.Fallo -> manejarFalloPreparacion(resultado.error, mesa)
            }
        }
    }

    /** PRB-93: candidatas para unir a [mesa] (que actua como destino). */
    fun prepararFusion(mesa: Mesa) {
        val destinos = destinosParaFusion(mesa)
        if (destinos.isEmpty()) {
            _eventos.trySend(MesasEvento.Aviso(AvisoMesas.SIN_DESTINOS, mesa))
        } else {
            _eventos.trySend(MesasEvento.MostrarDestino(AccionMesa.FUSIONAR, mesa, destinos))
        }
    }

    /** PRB-93: candidatas que pueden recibir el pedido de [mesa]. */
    fun prepararTransferencia(mesa: Mesa) {
        val destinos = destinosParaTransferencia(mesa)
        if (destinos.isEmpty()) {
            _eventos.trySend(MesasEvento.Aviso(AvisoMesas.SIN_DESTINOS, mesa))
        } else {
            _eventos.trySend(MesasEvento.MostrarDestino(AccionMesa.TRANSFERIR, mesa, destinos))
        }
    }

    /** PRB-93: separa [itemIds] de [mesaOrigen] hacia [mesaDestino]. */
    fun dividirMesa(mesaOrigen: Mesa, itemIds: List<Long>, mesaDestino: Mesa) {
        if (itemIds.isEmpty()) return
        ejecutarAccion(
            accion = AccionMesa.DIVIDIR,
            mesa = mesaOrigen,
            operacion = { mesaRepository.dividirMesa(mesaOrigen.id, itemIds, mesaDestino.id) }
        ) { division ->
            notificarExito(
                accion = AccionMesa.DIVIDIR,
                origen = mesaOrigen,
                destino = mesaDestino,
                total = division.totalCombinado,
                platos = itemIds.size
            )
        }
    }

    /** PRB-93: une los pedidos de [mesaOrigen] en [mesaDestino]. */
    fun fusionarMesas(mesaDestino: Mesa, mesaOrigen: Mesa) {
        ejecutarAccion(
            accion = AccionMesa.FUSIONAR,
            mesa = mesaOrigen,
            operacion = { mesaRepository.fusionarMesas(mesaDestino.id, mesaOrigen.id) }
        ) { cuenta ->
            notificarExito(
                accion = AccionMesa.FUSIONAR,
                origen = mesaOrigen,
                destino = mesaDestino,
                total = cuenta.total,
                platos = null
            )
        }
    }

    /** PRB-93: mueve los pedidos de [mesaOrigen] a [mesaDestino]. */
    fun transferirMesa(mesaOrigen: Mesa, mesaDestino: Mesa) {
        ejecutarAccion(
            accion = AccionMesa.TRANSFERIR,
            mesa = mesaOrigen,
            operacion = { mesaRepository.transferirMesa(mesaOrigen.id, mesaDestino.id) }
        ) { cuenta ->
            notificarExito(
                accion = AccionMesa.TRANSFERIR,
                origen = mesaOrigen,
                destino = mesaDestino,
                total = cuenta.total,
                platos = null
            )
        }
    }

    /**
     * Ejecuta una operacion de salon con un unico vuelo a la vez. El estado
     * [MesasUiState.accionEnCurso] evita que un doble toque cree dos cuentas.
     */
    private fun <T> ejecutarAccion(
        accion: AccionMesa,
        mesa: Mesa,
        operacion: suspend () -> Resultado<T>,
        alExito: suspend (T) -> Unit
    ) {
        if (_estado.value.accionEnCurso != null) return
        _estado.update { it.copy(accionEnCurso = accion) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = operacion()) {
                is Resultado.Exito -> alExito(resultado.datos)
                is Resultado.Fallo -> manejarFalloAccion(resultado.error, mesa)
            }
        }
    }

    /** Exito: avisa el resultado y recarga el mapa (PRB-93, criterio 3). */
    private suspend fun notificarExito(
        accion: AccionMesa,
        origen: Mesa,
        destino: Mesa,
        total: BigDecimal?,
        platos: Int?
    ) {
        _estado.update { it.copy(accionEnCurso = null) }
        _eventos.send(
            MesasEvento.AccionMesaCompletada(
                accion = accion,
                mesaOrigen = origen,
                mesaDestino = destino,
                total = total,
                platos = platos
            )
        )
        // Refresco silencioso: el mapa ya no muestra el dialogo y la grilla no
        // debe parpadear. El polling sigue funcionando por su cuenta.
        cargarMesas(mostrarAviso = false, mostrarCargando = false, silencioso = true)
    }

    private suspend fun manejarFalloAccion(error: ErrorApp, mesa: Mesa) {
        _estado.update { it.copy(accionEnCurso = null) }
        when (error) {
            ErrorApp.SESION_EXPIRADA -> manejarSesionExpirada()
            ErrorApp.CONFLICTO -> {
                // Un 409 en estas operaciones significa que alguna mesa cambio
                // de estado. Se avisa y se refresca para que el mapa refleje la
                // realidad antes de reintentar.
                _eventos.send(MesasEvento.Aviso(AvisoMesas.CONFLICTO_ACCION, mesa))
                cargarMesas(mostrarCargando = false, silencioso = true)
            }

            ErrorApp.RED -> _eventos.send(MesasEvento.Aviso(AvisoMesas.ERROR_RED, mesa))
            else -> _eventos.send(MesasEvento.Aviso(AvisoMesas.ERROR_ACCION, mesa))
        }
    }

    private suspend fun manejarFalloPreparacion(error: ErrorApp, mesa: Mesa) {
        when (error) {
            ErrorApp.SESION_EXPIRADA -> manejarSesionExpirada()
            ErrorApp.RED -> _eventos.send(MesasEvento.Aviso(AvisoMesas.ERROR_RED, mesa))
            else -> _eventos.send(MesasEvento.Aviso(AvisoMesas.ERROR_ACCION, mesa))
        }
    }

    /** Destinos de division: mesa libre/atendida o la misma mesa (cuenta aparte). */
    private fun destinosParaDivision(origen: Mesa): List<Mesa> =
        _estado.value.mesas
            .filter {
                it.id == origen.id ||
                    it.estado == EstadoMesa.LIBRE ||
                    it.estado == EstadoMesa.ATENDIDA
            }
            .sortedBy { it.numero }

    /** Destinos de fusion: otra mesa con pedido activo (no libre ni cuenta). */
    private fun destinosParaFusion(origen: Mesa): List<Mesa> =
        _estado.value.mesas
            .filter {
                it.id != origen.id &&
                    (it.estado == EstadoMesa.OCUPADA || it.estado == EstadoMesa.ATENDIDA)
            }
            .sortedBy { it.numero }

    /** Destinos de transferencia: mesa libre o atendida que pueda recibir. */
    private fun destinosParaTransferencia(origen: Mesa): List<Mesa> =
        _estado.value.mesas
            .filter {
                it.id != origen.id &&
                    (it.estado == EstadoMesa.LIBRE || it.estado == EstadoMesa.ATENDIDA)
            }
            .sortedBy { it.numero }

    fun cerrarSesion() {
        viewModelScope.launch(ioDispatcher) {
            authRepository.cerrarSesion()
            _eventos.send(MesasEvento.SesionCerrada)
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    /**
     * Resuelve la sesion guardada, pero no pide mesas: la unica carga inicial
     * es el primer ciclo de [startPolling], para no lanzar dos GET al abrir.
     */
    private fun iniciar() {
        viewModelScope.launch(ioDispatcher) {
            val sesion = try {
                authRepository.sesionGuardada()
            } catch (e: Exception) {
                null
            }
            if (sesion == null) {
                _estado.update { it.copy(cargando = false) }
                sesionLista.complete(false)
                _eventos.send(MesasEvento.SesionExpirada)
                return@launch
            }
            _estado.update { it.copy(nombreUsuario = sesion.usuario.nombre, rolUsuario = sesion.usuario.rol) }
            sesionLista.complete(true)
        }
    }

    private suspend fun manejarFalloCarga(error: ErrorApp, silencioso: Boolean) {
        if (error == ErrorApp.SESION_EXPIRADA) {
            manejarSesionExpirada()
            return
        }
        if (silencioso) {
            // Un fallo del polling no debe vaciar el mapa ya pintado: solo se
            // muestra el error de pantalla cuando aun no hay mesas que conservar.
            if (_estado.value.mesas.isEmpty()) {
                _estado.update { it.copy(cargando = false, error = error.aErrorMesas()) }
            }
            // Un unico Snackbar por racha de fallos, para no repetirlo cada 4 s.
            if (error == ErrorApp.RED && !pollingFallando) {
                pollingFallando = true
                _eventosError.tryEmit(ERROR_RED)
            }
            return
        }
        _estado.update { it.copy(cargando = false, error = error.aErrorMesas()) }
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
        _estado.update { it.copy(cargando = false, mesaAbriendo = null, accionEnCurso = null) }
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

    companion object {
        /** Intervalo entre consultas del mapa (PRB-42: cada 4 segundos). */
        const val INTERVALO_POLLING_MS = 4_000L

        /** Codigo del error de red que la pantalla traduce a su Snackbar. */
        const val ERROR_RED = "RED"
    }
}
