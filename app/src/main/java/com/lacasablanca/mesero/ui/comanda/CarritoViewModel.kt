package com.lacasablanca.mesero.ui.comanda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lacasablanca.mesero.data.repository.CatalogoRepository
import com.lacasablanca.mesero.data.repository.PedidoRepository
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.Resultado
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.ItemPedido
import com.lacasablanca.mesero.domain.model.Producto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _estado = MutableStateFlow(CarritoUiState())
    val estado: StateFlow<CarritoUiState> = _estado.asStateFlow()

    private val _eventos = Channel<CarritoEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    private var trabajoCarga: Job? = null

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

    /** Agrega un plato; si ya existe, suma una unidad. Ignora platos no disponibles. */
    fun agregarProducto(producto: Producto) {
        if (producto.id <= 0L || !producto.disponible || _estado.value.enviando) return

        claveIdempotencia = null
        _estado.update { actual ->
            val existente = actual.items.firstOrNull { it.producto.id == producto.id }
            val items = if (existente == null) {
                actual.items + ItemCarrito(producto = producto, cantidad = 1)
            } else {
                actual.items.map { item ->
                    if (item.producto.id == producto.id) {
                        item.copy(cantidad = item.cantidad + 1)
                    } else {
                        item
                    }
                }
            }
            actual.copy(items = items)
        }
    }

    fun aumentarCantidad(productoId: Long) {
        if (_estado.value.enviando) return
        if (_estado.value.items.none { it.producto.id == productoId }) return

        claveIdempotencia = null
        _estado.update { actual ->
            actual.copy(
                items = actual.items.map { item ->
                    if (item.producto.id == productoId) {
                        item.copy(cantidad = item.cantidad + 1)
                    } else {
                        item
                    }
                }
            )
        }
    }

    /** Baja una unidad; al llegar a cero la linea se elimina del carrito. */
    fun disminuirCantidad(productoId: Long) {
        if (_estado.value.enviando) return
        val item = _estado.value.items.firstOrNull { it.producto.id == productoId } ?: return

        claveIdempotencia = null
        _estado.update { actual ->
            if (item.cantidad <= 1) {
                actual.copy(items = actual.items.filterNot { it.producto.id == productoId })
            } else {
                actual.copy(
                    items = actual.items.map {
                        if (it.producto.id == productoId) it.copy(cantidad = it.cantidad - 1) else it
                    }
                )
            }
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
     * Nota libre por plato (RF-06: "sin picante", "sin ají"...). Se envia en
     * el campo `notas` de ItemPedidoRequest. Se recorta y se limita a 120.
     */
    fun actualizarNotas(productoId: Long, notas: String) {
        if (_estado.value.enviando) return
        if (_estado.value.items.none { it.producto.id == productoId }) return

        claveIdempotencia = null
        val limpia = notas.trim().take(MAX_NOTAS)
        _estado.update { actual ->
            actual.copy(
                items = actual.items.map { item ->
                    if (item.producto.id == productoId) {
                        item.copy(notas = limpia)
                    } else {
                        item
                    }
                }
            )
        }
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
        val actual = _estado.value
        if (actual.enviando || actual.carritoVacio || mesaId <= 0L) return

        val clave = claveIdempotencia ?: UUID.randomUUID().toString().also {
            claveIdempotencia = it
        }
        val itemsParaEnviar = actual.items.map { item ->
            ItemPedido(
                productoId = item.producto.id,
                cantidad = item.cantidad,
                notas = item.notas,
                precioUnitario = item.producto.precio
            )
        }

        _estado.update { it.copy(enviando = true) }
        viewModelScope.launch(ioDispatcher) {
            when (val resultado = pedidoRepository.enviarPedido(mesaId, itemsParaEnviar, clave)) {
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
                        _eventos.send(CarritoEvento.ErrorAlEnviar(resultado.error))
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

    private fun ErrorApp.aErrorCarrito(): ErrorCarrito = when (this) {
        ErrorApp.RED -> ErrorCarrito.RED
        ErrorApp.SERVIDOR -> ErrorCarrito.SERVIDOR
        else -> ErrorCarrito.DESCONOCIDO
    }

    private companion object {
        const val MAX_NOTAS = 120
    }
}
