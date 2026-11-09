package com.lacasablanca.mesero.ui.comanda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.repository.CatalogoRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.DestinoPedido
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.Modificador
import com.lacasablanca.mesero.domain.model.Producto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Catalogo + carrito de la comanda (PRB-38).
 *
 * Reglas:
 * - El carrito es la unica fuente de verdad de las lineas.
 * - Subtotal/IGV/Total se recalculan solos con [CalculoIgv]: total = suma de
 *   lineas; subtotal = total / 1.18; igv = total - subtotal (igual que Max).
 * - Al enviar con exito el carrito se vacia y se emite [CarritoEvento.PedidoEnviadoOk].
 * - La clave de idempotencia se crea al primer intento, se conserva si el
 *   envio falla y se descarta al recibir 201 o al modificar el carrito
 *   (PRB-2). Asi un reintento tras un corte de Wi-Fi no duplica el pedido.
 */
class CarritoViewModel(
    private val catalogoRepository: CatalogoRepository,
    private val pedidoRepository: PedidoRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * PRB-94: destino de la comanda. Por defecto [DestinoPedido.Mesa] con id 0
     * para conservar la firma historica de los tests; la Activity lo configura
     * con la mesa elegida o con el nombre del cliente para llevar.
     */
    private val destino: DestinoPedido = DestinoPedido.Mesa(0L)
) : ViewModel() {

    private val _estado = MutableStateFlow(CarritoUiState(destino = destino))
    val estado: StateFlow<CarritoUiState> = _estado.asStateFlow()

    private val _eventos = Channel<CarritoEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    private var trabajoCarga: Job? = null

    /** Job del refresco automatico del pedido existente (PRB-90). */
    private var pollingJob: Job? = null

    /**
     * true mientras la pantalla pidio el refresco automatico (onStart) y aun
     * no lo detuvo (onStop o envio de una comanda). Evita reanudar el bucle
     * desde un envio que termina tarde, cuando la pantalla ya no esta visible.
     */
    private var pollingHabilitado = false

    /**
     * true entre onStart y onStop. Se mantiene aparte de [pollingHabilitado]
     * para no reanudar el bucle desde un envio que falla despues de que la
     * pantalla dejo de estar visible.
     */
    private var pantallaVisible = false

    /**
     * Cambia al anular una linea ya enviada. El polling descarta una respuesta
     * que salio antes de esa mutacion para no reinsertar el plato anulado.
     */
    private val versionPedidoExistente = AtomicLong(0L)

    /**
     * Clave de idempotencia del pedido en curso. null = el proximo envio
     * necesitara una clave nueva. No se expone en la UI: es un detalle de red.
     */
    private var claveIdempotencia: String? = null

    init {
        cargar()
    }

    /** Carga (o recarga) categorias y productos. No duplica peticiones en vuelo. */
    fun cargar() {
        if (trabajoCarga?.isActive == true) return

        _estado.update { it.copy(cargando = true, errorCatalogo = null) }
        trabajoCarga = viewModelScope.launch(ioDispatcher) {
            when (val categorias = catalogoRepository.listarCategorias()) {
                is Resultado.Fallo -> falloCatalogo(categorias.error)
                is Resultado.Exito -> {
                    when (val productos = catalogoRepository.listarProductos()) {
                        is Resultado.Fallo -> falloCatalogo(productos.error)
                        is Resultado.Exito -> _estado.update { actual ->
                            actual.copy(
                                cargando = false,
                                categorias = categorias.datos,
                                productos = productos.datos,
                                filas = calcularFilas(
                                    productos = productos.datos,
                                    categorias = categorias.datos,
                                    seleccionada = actual.categoriaSeleccionada
                                ),
                                errorCatalogo = null
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Activa el modo lectura de una mesa OCUPADA (PRB-83) y consulta su pedido.
     * Es idempotente: una rotacion no vuelve a pedir el pedido al backend.
     */
    fun activarModoLecura(mesaId: Long) {
        if (_estado.value.modoLectura) return
        _estado.update { it.copy(modoLectura = true) }
        cargarPedidoExistente(mesaId)
    }

    /**
     * GET /api/v1/pedidos?mesa_id=X. Un pedido inexistente (404) llega como
     * exito vacio desde el repositorio real; el error de red si se muestra y
     * la pantalla permite reintentar.
     */
    fun cargarPedidoExistente(mesaId: Long) {
        if (mesaId <= 0L || _estado.value.cargandoPedidoExistente) return

        _estado.update { it.copy(cargandoPedidoExistente = true, errorPedidoExistente = null) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = pedidoRepository.obtenerPedidoDeMesa(mesaId)) {
                is Resultado.Exito -> _estado.update {
                    it.copy(
                        cargandoPedidoExistente = false,
                        pedidoExistenteCargado = true,
                        pedidoExistenteId = resultado.datos.pedidoId,
                        itemsExistentes = resultado.datos.lineas,
                        errorPedidoExistente = null
                    )
                }

                is Resultado.Fallo -> {
                    if (resultado.error == ErrorApp.SESION_EXPIRADA) {
                        _estado.update {
                            it.copy(
                                cargandoPedidoExistente = false,
                                pedidoExistenteCargado = true
                            )
                        }
                        _eventos.send(CarritoEvento.SesionExpirada)
                    } else {
                        _estado.update {
                            it.copy(
                                cargandoPedidoExistente = false,
                                pedidoExistenteCargado = true,
                                errorPedidoExistente = resultado.error.aErrorCarrito()
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * PRB-90: refresca cada [INTERVALO_POLLING_MS] el estado de los platos ya
     * enviados mientras la comanda esta en modo lectura y visible.
     *
     * Solo reemplaza [CarritoUiState.itemsExistentes]; el carrito de platos
     * nuevos ([CarritoUiState.items]) nunca se toca. Es idempotente: si el
     * bucle ya esta activo no crea otro. El primer ciclo espera el intervalo
     * porque la carga inicial la hace [activarModoLecura].
     */
    fun startPolling(mesaId: Long) {
        if (mesaId <= 0L || !_estado.value.modoLectura) return

        pantallaVisible = true
        pollingHabilitado = true
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch(ioDispatcher) {
            while (isActive) {
                delay(INTERVALO_POLLING_MS)
                refrescarPedidoExistente(mesaId)
            }
        }
    }

    /** Detiene el refresco automatico. Se puede llamar varias veces sin efectos. */
    fun stopPolling() {
        pantallaVisible = false
        detenerBuclePolling()
    }

    /**
     * Corta el bucle sin marcar la pantalla como oculta. Lo usa el envio: si
     * la red falla con la comanda aun visible, el refresco puede reanudarse.
     */
    private fun detenerBuclePolling() {
        pollingHabilitado = false
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Un ciclo silencioso del polling: no muestra spinner ni dialogos de error.
     * Si la consulta falla se conserva lo ya pintado; solo una sesion expirada
     * corta el bucle. Se descarta si hay un envio/anulacion en vuelo o si la
     * respuesta salio antes de una anulacion local.
     */
    private suspend fun refrescarPedidoExistente(mesaId: Long) {
        if (!_estado.value.puedeRefrescarPedido()) return

        val version = versionPedidoExistente.get()
        when (val resultado = pedidoRepository.obtenerPedidoDeMesa(mesaId)) {
            is Resultado.Exito -> {
                if (version != versionPedidoExistente.get()) return
                _estado.update { estado ->
                    if (!estado.modoLectura ||
                        estado.enviando ||
                        estado.anulandoItemId != null
                    ) {
                        estado
                    } else {
                        estado.copy(
                            pedidoExistenteId = resultado.datos.pedidoId
                                .takeIf { it > 0L }
                                ?: estado.pedidoExistenteId,
                            // Solo los platos ya enviados: `items` (los nuevos)
                            // queda intacto por este copy.
                            itemsExistentes = resultado.datos.lineas
                        )
                    }
                }
            }

            is Resultado.Fallo -> {
                if (resultado.error == ErrorApp.SESION_EXPIRADA) {
                    _eventos.send(CarritoEvento.SesionExpirada)
                    stopPolling()
                }
            }
        }
    }

    /** Filtra por categoria. null = "Todas". */
    fun filtrarCategoria(categoriaId: Long?) {
        _estado.update { actual ->
            if (actual.categoriaSeleccionada == categoriaId) {
                actual
            } else {
                actual.copy(
                    categoriaSeleccionada = categoriaId,
                    filas = calcularFilas(actual.productos, actual.categorias, categoriaId)
                )
            }
        }
    }

    /** Agrega un plato sin modificadores; si ya existe, suma una unidad. */
    fun agregarProducto(producto: Producto) {
        agregarProducto(producto, emptyList())
    }

    /**
     * PRB-92: agrega un plato con los modificadores elegidos en el dialogo.
     *
     * La linea se identifica por (producto, modificadores): agregar el mismo
     * plato con otra combinacion crea una linea nueva para no mezclar
     * preferencias. Los precios se toman SIEMPRE del catalogo ([Producto]) y
     * no de lo que entregue la UI; asi un modificador inventado o con un
     * precio manipulado no llega al pedido. El recargo lo vuelve a calcular el
     * backend con su propia carta.
     */
    fun agregarProducto(producto: Producto, modificadores: List<Modificador>) {
        if (producto.id <= 0L || !producto.disponible || _estado.value.enviando) return

        val elegidos = normalizarModificadores(producto, modificadores)
        claveIdempotencia = null
        _estado.update { actual ->
            val existente = actual.items.firstOrNull {
                it.producto.id == producto.id && it.modificadores.mismaSeleccion(elegidos)
            }
            val items = if (existente == null) {
                actual.items + ItemCarrito(
                    producto = producto,
                    cantidad = 1,
                    modificadores = elegidos
                )
            } else {
                actual.items.map { item ->
                    if (item.esLaMismaLineaQue(existente)) {
                        item.copy(cantidad = item.cantidad + 1)
                    } else {
                        item
                    }
                }
            }
            actual.copy(items = items)
        }
    }

    /**
     * Aumenta la cantidad de una linea concreta del carrito. Se usa desde el
     * detalle, donde puede haber dos lineas del mismo plato con modificadores
     * distintos.
     */
    fun aumentarCantidad(item: ItemCarrito) {
        if (_estado.value.enviando) return
        val indice = _estado.value.items.indiceDe(item)
        if (indice < 0) return

        claveIdempotencia = null
        _estado.update { actual ->
            actual.copy(
                items = actual.items.mapIndexed { posicion, linea ->
                    if (posicion == indice) linea.copy(cantidad = linea.cantidad + 1) else linea
                }
            )
        }
    }

    fun aumentarCantidad(productoId: Long) {
        if (_estado.value.enviando) return
        val item = _estado.value.items.firstOrNull { it.producto.id == productoId } ?: return
        aumentarCantidad(item)
    }

    /** Baja una unidad de una linea concreta; al llegar a cero se elimina. */
    fun disminuirCantidad(item: ItemCarrito) {
        if (_estado.value.enviando) return
        val indice = _estado.value.items.indiceDe(item)
        if (indice < 0) return

        claveIdempotencia = null
        _estado.update { actual ->
            val linea = actual.items[indice]
            val items = if (linea.cantidad <= 1) {
                actual.items.filterIndexed { posicion, _ -> posicion != indice }
            } else {
                actual.items.mapIndexed { posicion, otra ->
                    if (posicion == indice) otra.copy(cantidad = otra.cantidad - 1) else otra
                }
            }
            actual.copy(items = items)
        }
    }

    fun disminuirCantidad(productoId: Long) {
        if (_estado.value.enviando) return
        val item = _estado.value.items.firstOrNull { it.producto.id == productoId } ?: return
        disminuirCantidad(item)
    }

    /** Elimina una linea concreta (incluida su seleccion de modificadores). */
    fun eliminarProducto(item: ItemCarrito) {
        if (_estado.value.enviando) return
        val indice = _estado.value.items.indiceDe(item)
        if (indice < 0) return

        claveIdempotencia = null
        _estado.update { actual ->
            actual.copy(items = actual.items.filterIndexed { posicion, _ -> posicion != indice })
        }
    }

    fun eliminarProducto(productoId: Long) {
        if (_estado.value.enviando) return
        if (_estado.value.items.none { it.producto.id == productoId }) return

        claveIdempotencia = null
        _estado.update { actual ->
            actual.copy(items = actual.items.filterNot { it.producto.id == productoId })
        }
    }

    /**
     * Nota libre de una linea (RF-06: "sin picante", "sin ají"...). Se envia
     * en el campo `notas` de ItemPedidoRequest. Se recorta y se limita a 120.
     */
    fun actualizarNotas(item: ItemCarrito, notas: String) {
        if (_estado.value.enviando) return
        val indice = _estado.value.items.indiceDe(item)
        if (indice < 0) return

        claveIdempotencia = null
        val limpia = notas.trim().take(MAX_NOTAS)
        _estado.update { actual ->
            actual.copy(
                items = actual.items.mapIndexed { posicion, linea ->
                    if (posicion == indice) linea.copy(notas = limpia) else linea
                }
            )
        }
    }

    fun actualizarNotas(productoId: Long, notas: String) {
        if (_estado.value.enviando) return
        val item = _estado.value.items.firstOrNull { it.producto.id == productoId } ?: return
        actualizarNotas(item, notas)
    }

    /**
     * Envia POST /api/v1/pedidos. Bloquea dobles toques mientras el envio
     * esta en vuelo y conserva el carrito si falla (para reintentar).
     *
     * La misma [claveIdempotencia] viaja en `client_request_id` y en el header
     * `Idempotency-Key`. Si el envio falla se conserva para el reintento; si
     * responde 201 se descarta. Cualquier cambio del carrito la invalida.
     */
    fun enviarPedido(mesaId: Long) {
        enviarPedidoA(DestinoPedido.Mesa(mesaId))
    }

    /**
     * PRB-94: envia usando el destino configurado en el constructor. Es el
     * punto de entrada del bottom sheet, que no conoce si la comanda es de
     * mesa o para llevar.
     */
    fun enviarPedido() {
        enviarPedidoA(destino)
    }

    private fun enviarPedidoA(destino: DestinoPedido) {
        val actual = _estado.value
        if (actual.enviando || actual.carritoVacio) return
        if (destino is DestinoPedido.Mesa && destino.mesaId <= 0L) return
        if (destino is DestinoPedido.ParaLlevar && destino.clienteNombre.isBlank()) return

        // PRB-90: al enviar una nueva comanda se detiene el refresco. Si el
        // envio falla y la pantalla sigue visible, se reanuda mas abajo.
        val reanudarPolling = pollingHabilitado
        detenerBuclePolling()

        val clave = claveIdempotencia ?: UUID.randomUUID().toString().also {
            claveIdempotencia = it
        }
        val itemsParaEnviar = actual.items.map { item ->
            ItemPedido(
                productoId = item.producto.id,
                cantidad = item.cantidad,
                notas = item.notas,
                precioUnitario = item.producto.precio,
                modificadores = item.modificadores
            )
        }
        // Solo una comanda de mesa refresca su pedido existente (PRB-90).
        val mesaIdPolling = (destino as? DestinoPedido.Mesa)?.mesaId

        _estado.update { it.copy(enviando = true) }
        viewModelScope.launch(ioDispatcher) {
            val resultado = when (destino) {
                is DestinoPedido.Mesa -> pedidoRepository.enviarPedido(
                    destino.mesaId,
                    itemsParaEnviar,
                    clave
                )

                is DestinoPedido.ParaLlevar -> pedidoRepository.enviarPedidoParaLlevar(
                    items = itemsParaEnviar,
                    clientRequestId = clave,
                    clienteNombre = destino.clienteNombre.trim()
                )
            }

            when (resultado) {
                is Resultado.Exito -> {
                    claveIdempotencia = null
                    _estado.update { it.copy(enviando = false, items = emptyList()) }
                    _eventos.send(CarritoEvento.PedidoEnviadoOk(resultado.datos))
                }

                is Resultado.Fallo -> {
                    _estado.update { it.copy(enviando = false) }
                    if (resultado.error == ErrorApp.SESION_EXPIRADA) {
                        _eventos.send(CarritoEvento.SesionExpirada)
                    } else {
                        if (reanudarPolling &&
                            pantallaVisible &&
                            _estado.value.modoLectura &&
                            mesaIdPolling != null
                        ) {
                            startPolling(mesaIdPolling)
                        }
                        _eventos.send(CarritoEvento.ErrorAlEnviar(resultado.error))
                    }
                }
            }
        }
    }

    /**
     * Anula un plato ya enviado a cocina (PRB-77).
     *
     * Solo aplica en modo lectura a lineas con `itemId`; las lineas nuevas se
     * quitan con el carrito local. Al exito se elimina la linea del estado y
     * los getters de [CarritoUiState] recalculan Subtotal, IGV y Total con
     * BigDecimal. Un 409 llega como [ErrorApp.PLATO_YA_SERVIDO].
     */
    fun anularItem(item: ItemCarrito) {
        val itemId = item.itemId ?: return
        val actual = _estado.value
        if (!actual.modoLectura || actual.enviando || actual.anulandoItemId != null) return

        val pedidoId = actual.pedidoExistenteId
        if (pedidoId <= 0L) return
        if (actual.itemsExistentes.none { it.itemId == itemId }) return

        _estado.update { it.copy(anulandoItemId = itemId) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = pedidoRepository.anularItem(pedidoId, itemId)) {
                is Resultado.Exito -> {
                    // Invalida una respuesta del polling que haya salido antes
                    // de que el backend confirmara la anulacion.
                    versionPedidoExistente.incrementAndGet()
                    _estado.update { estado ->
                        estado.copy(
                            anulandoItemId = null,
                            itemsExistentes = estado.itemsExistentes
                                .filterNot { it.itemId == itemId }
                        )
                    }
                    _eventos.send(CarritoEvento.ItemAnulado(item.producto.nombre))
                }

                is Resultado.Fallo -> {
                    _estado.update { it.copy(anulandoItemId = null) }
                    if (resultado.error == ErrorApp.SESION_EXPIRADA) {
                        _eventos.send(CarritoEvento.SesionExpirada)
                    } else {
                        _eventos.send(CarritoEvento.ErrorAlAnular(resultado.error))
                    }
                }
            }
        }
    }

    private suspend fun falloCatalogo(error: ErrorApp) {
        _estado.update {
            it.copy(
                cargando = false,
                errorCatalogo = error.aErrorCarrito()
            )
        }
        if (error == ErrorApp.SESION_EXPIRADA) {
            _eventos.send(CarritoEvento.SesionExpirada)
        }
    }

    /**
     * Arma las filas del RecyclerView: encabezado por categoria (en el orden
     * que entrega el backend) y sus platos. Los platos de una categoria
     * desconocida caen en un encabezado "Otros" (solo cuando no hay filtro).
     */
    private fun calcularFilas(
        productos: List<Producto>,
        categorias: List<Categoria>,
        seleccionada: Long?
    ): List<FilaCatalogo> {
        val filas = mutableListOf<FilaCatalogo>()
        val categoriasVisibles = if (seleccionada == null) {
            categorias
        } else {
            categorias.filter { it.id == seleccionada }
        }

        for (categoria in categoriasVisibles) {
            val platos = productos.filter { it.categoriaId == categoria.id }
            if (platos.isEmpty()) continue

            filas += FilaCatalogo.Encabezado(categoria.id, categoria.nombre)
            platos.forEach { filas += FilaCatalogo.Fila(it) }
        }

        if (seleccionada == null) {
            val idsConocidos = categorias.map { it.id }.toSet()
            val huerfanos = productos.filter { it.categoriaId !in idsConocidos }
            if (huerfanos.isNotEmpty()) {
                filas += FilaCatalogo.Encabezado(categoriaId = null, nombre = null)
                huerfanos.forEach { filas += FilaCatalogo.Fila(it) }
            }
        }

        return filas
    }

    /**
     * Toma los modificadores elegidos y se queda solo con los que ofrece el
     * plato, usando el nombre y el precio del catalogo. Evita que la UI (o un
     * dato manipulado) envie un modificador o recargo que la carta no tiene.
     */
    private fun normalizarModificadores(
        producto: Producto,
        seleccionados: List<Modificador>
    ): List<Modificador> {
        if (seleccionados.isEmpty() || producto.modificadores.isEmpty()) return emptyList()

        val disponibles = producto.modificadores.associateBy { it.nombre.trim() }
        return seleccionados
            .mapNotNull { elegido -> disponibles[elegido.nombre.trim()] }
            .map { disponible ->
                Modificador(
                    nombre = disponible.nombre,
                    precioAdicional = disponible.precioAdicional.max(BigDecimal.ZERO)
                )
            }
            .distinctBy { it.nombre }
    }

    /** Dos selecciones son la misma linea si coinciden nombre y recargo. */
    private fun List<Modificador>.mismaSeleccion(otra: List<Modificador>): Boolean =
        claveSeleccion() == otra.claveSeleccion()

    private fun List<Modificador>.claveSeleccion(): String =
        sortedBy { it.nombre }.joinToString("|") {
            "${it.nombre}=${it.precioAdicional.toPlainString()}"
        }

    /**
     * Identidad de una linea: el id de detalle si ambos lo tienen (platos ya
     * enviados) o la pareja (producto, modificadores) para las lineas nuevas.
     */
    private fun ItemCarrito.esLaMismaLineaQue(otra: ItemCarrito): Boolean {
        if (itemId != null && otra.itemId != null) return itemId == otra.itemId
        return producto.id == otra.producto.id &&
            modificadores.mismaSeleccion(otra.modificadores)
    }

    private fun List<ItemCarrito>.indiceDe(buscado: ItemCarrito): Int =
        indexOfFirst { it.esLaMismaLineaQue(buscado) }

    /** true si el polling puede consultar sin pisar un envio o anulacion en curso. */
    private fun CarritoUiState.puedeRefrescarPedido(): Boolean =
        modoLectura && !enviando && anulandoItemId == null && !cargandoPedidoExistente

    private fun ErrorApp.aErrorCarrito(): ErrorCarrito = when (this) {
        ErrorApp.RED -> ErrorCarrito.RED
        ErrorApp.SERVIDOR -> ErrorCarrito.SERVIDOR
        else -> ErrorCarrito.DESCONOCIDO
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        /** Intervalo entre refrescos del pedido existente (PRB-90: 5 segundos). */
        const val INTERVALO_POLLING_MS = 5_000L

        private const val MAX_NOTAS = 120
    }
}
