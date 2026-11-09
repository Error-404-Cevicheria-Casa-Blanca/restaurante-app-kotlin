package com.lacasablanca.mesero.ui.comanda

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.text.LineBreaker
import android.os.Bundle
import android.text.Layout
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.ActivityComandaBinding
import com.lacasablanca.mesero.databinding.DialogCuentaSolicitadaBinding
import com.lacasablanca.mesero.databinding.DialogLiberarMesaBinding
import com.lacasablanca.mesero.databinding.DialogNotaPlatoBinding
import com.lacasablanca.mesero.databinding.DialogPedidoEnviadoBinding
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.DestinoPedido
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.ui.bloqueo.BloqueoActivity
import com.lacasablanca.mesero.ui.common.aparecerConDesplazamiento
import com.lacasablanca.mesero.ui.common.formatearMoneda
import com.lacasablanca.mesero.ui.impresion.ImpresionPrecuentaViewModel
import com.lacasablanca.mesero.ui.impresion.ImpresionUiState
import com.lacasablanca.mesero.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Comanda del mesero (PRB-38 + PRB-39).
 *
 * - Catalogo y carrito: [CarritoViewModel] (GET /categorias, GET /productos,
 *   POST /pedidos). La Activity solo dibuja y reenvia eventos.
 * - Al salir sin enviar, [ComandaViewModel] libera la mesa (D11/PRB-39).
 * - Al enviar con exito se marca [EXTRA_PEDIDO_ENVIADO] para que la mesa NO
 *   se libere al volver al mapa.
 */
class ComandaActivity : AppCompatActivity(), CarritoBottomSheet.Listener {

    private lateinit var binding: ActivityComandaBinding

    private val contenedor get() = (application as MeseroApp).container

    private val carritoViewModel: CarritoViewModel by viewModels {
        GenericViewModelFactory {
            CarritoViewModel(
                catalogoRepository = contenedor.catalogoRepository,
                pedidoRepository = contenedor.pedidoRepository,
                destino = if (paraLlevar) {
                    DestinoPedido.ParaLlevar(clienteNombre)
                } else {
                    DestinoPedido.Mesa(mesaId)
                }
            )
        }
    }

    private val comandaViewModel: ComandaViewModel by viewModels {
        GenericViewModelFactory { ComandaViewModel(contenedor.mesaRepository) }
    }

    private val impresionViewModel: ImpresionPrecuentaViewModel by viewModels {
        GenericViewModelFactory {
            ImpresionPrecuentaViewModel(mesaId, contenedor.cobroRepository)
        }
    }

    private lateinit var catalogoAdapter: CatalogoAdapter

    private var mesaId = -1L
    private var numeroMesa = 0

    /** PRB-94: comanda sin mesa, identificada por el nombre del cliente. */
    private var paraLlevar = false
    private var clienteNombre = ""

    /** Id del pedido para llevar enviado; se devuelve al mapa para el aviso. */
    private var pedidoIdParaLlevar = 0L
    private var pedidoEnviado = false
    private var cuentaSolicitada = false
    private var modoLectura = false
    private var saliendo = false
    private var dialogoAbierto: AlertDialog? = null
    private var dialogoLiberar: AlertDialog? = null
    private var dialogoAnular: AnularPlatoDialog? = null

    /** PRB-92: selector de modificadores con un solo dialogo a la vez. */
    private val selectorModificadores: SelectorModificadores by lazy {
        SelectorModificadores(this) { producto, seleccionados ->
            carritoViewModel.agregarProducto(producto, seleccionados)
        }
    }
    private var pedidoExistenteMostrado = false
    private var errorPedidoExistenteMostrado = false

    private var categoriasRenderizadas: List<Categoria>? = null
    private var seleccionRenderizada: Long? = null
    private var animacionCatalogoEjecutada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComandaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mesaId = intent.getLongExtra(EXTRA_MESA_ID, -1L)
        numeroMesa = intent.getIntExtra(EXTRA_MESA_NUMERO, 0)
        val ubicacion = intent.getStringExtra(EXTRA_MESA_UBICACION).orEmpty()
        paraLlevar = savedInstanceState?.getBoolean(EXTRA_PARA_LLEVAR, false)
            ?: intent.getBooleanExtra(EXTRA_PARA_LLEVAR, false)
        clienteNombre = intent.getStringExtra(EXTRA_CLIENTE_NOMBRE).orEmpty().trim()
        pedidoIdParaLlevar = savedInstanceState?.getLong(EXTRA_PEDIDO_PARA_LLEVAR_ID, 0L) ?: 0L
        pedidoEnviado = savedInstanceState?.getBoolean(EXTRA_PEDIDO_ENVIADO, false)
            ?: intent.getBooleanExtra(EXTRA_PEDIDO_ENVIADO, false)
        cuentaSolicitada = savedInstanceState?.getBoolean(EXTRA_CUENTA_SOLICITADA, false)
            ?: false
        modoLectura = savedInstanceState?.getBoolean(EXTRA_MODO_LECTURA, false)
            ?: intent.getBooleanExtra(EXTRA_MODO_LECTURA, false)

        if (paraLlevar) {
            if (clienteNombre.isBlank()) {
                finish()
                return
            }
        } else if (mesaId <= 0L || numeroMesa <= 0) {
            finish()
            return
        }

        if (paraLlevar) {
            binding.barraComanda.title = getString(R.string.comanda_barra_titulo_para_llevar)
            binding.textoTitulo.text = getString(
                R.string.comanda_titulo_para_llevar,
                clienteNombre
            )
            binding.badgeEstado.text = getString(R.string.comanda_badge_para_llevar)
            binding.badgeEstado.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_para_llevar,
                0,
                0,
                0
            )
            binding.badgeEstado.compoundDrawableTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tertiary))
            // Sin mesa no hay "Pedir Cuenta" ni pre-cuenta por mesa.
            binding.btnPedirCuenta.isVisible = false
            binding.grupoImpresion.isVisible = false
        } else {
            binding.textoTitulo.text = getString(R.string.comanda_titulo, numeroMesa, ubicacion)
            binding.badgeEstado.text = if (modoLectura) {
                getString(R.string.comanda_badge_lectura)
            } else {
                getString(R.string.comanda_badge)
            }
        }
        binding.textoModoDemo.isVisible = BuildConfig.USE_FAKE_DATA

        prepararListas()
        prepararAcciones()

        // Mesa OCUPADA (PRB-83): consultar el pedido ya registrado y mostrarlo
        // en modo lectura. La mesa NO se libera al salir.
        if (modoLectura) carritoViewModel.activarModoLecura(mesaId)

        // Atras y la flecha de la toolbar piden confirmacion antes de liberar.
        binding.barraComanda.setNavigationOnClickListener { intentarSalir() }
        onBackPressedDispatcher.addCallback(this) { intentarSalir() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { carritoViewModel.estado.collect(::renderCarrito) }
                launch { carritoViewModel.eventos.collect(::manejarEventoCarrito) }
                launch { comandaViewModel.estado.collect(::renderComanda) }
                launch { comandaViewModel.eventos.collect(::manejarEventoComanda) }
                launch { impresionViewModel.estado.collect(::renderImpresion) }
            }
        }
    }

    /**
     * PRB-90: en una comanda en modo lectura, refrescar el estado de los
     * platos mientras la pantalla esta visible. Se detiene en onStop para no
     * consultar en segundo plano (mismo criterio que el mapa de mesas).
     * Tras enviar una nueva comanda no se reanuda.
     */
    override fun onStart() {
        super.onStart()
        if (modoLectura && !pedidoEnviado) carritoViewModel.startPolling(mesaId)
    }

    override fun onStop() {
        carritoViewModel.stopPolling()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Si el pedido ya viajo, una rotacion no debe permitir liberar la mesa.
        outState.putBoolean(EXTRA_PEDIDO_ENVIADO, pedidoEnviado)
        // Si la cuenta ya se pidio, tampoco se debe liberar la mesa al salir.
        outState.putBoolean(EXTRA_CUENTA_SOLICITADA, cuentaSolicitada)
        // El modo lectura es parte del contexto de la pantalla.
        outState.putBoolean(EXTRA_MODO_LECTURA, modoLectura)
        // PRB-94: el destino para llevar y el id ya enviado sobreviven a la
        // recreacion para no perder el aviso al volver al mapa.
        outState.putBoolean(EXTRA_PARA_LLEVAR, paraLlevar)
        outState.putString(EXTRA_CLIENTE_NOMBRE, clienteNombre)
        outState.putLong(EXTRA_PEDIDO_PARA_LLEVAR_ID, pedidoIdParaLlevar)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cerrarDialogoLiberar()
        cerrarDialogoAnular()
        selectorModificadores.cerrar()
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        super.onDestroy()
    }

    private fun prepararListas() {
        catalogoAdapter = CatalogoAdapter(
            // PRB-92: con modificadores, "+" abre el selector; sin ellos,
            // conserva el alta directa de PRB-38.
            alAgregar = { producto ->
                selectorModificadores.abrirSiCorresponde(producto) {
                    carritoViewModel.agregarProducto(producto)
                }
            },
            alAumentar = { producto ->
                selectorModificadores.abrirSiCorresponde(producto) {
                    carritoViewModel.aumentarCantidad(producto.id)
                }
            },
            // El "-" baja una unidad de la primera linea de ese plato; el
            // detalle del carrito permite ajustar cada combinacion.
            alDisminuir = { producto -> carritoViewModel.disminuirCantidad(producto.id) }
        )

        binding.listaCatalogo.layoutManager = LinearLayoutManager(this)
        binding.listaCatalogo.adapter = catalogoAdapter
    }

    private fun prepararAcciones() {
        binding.barraCheckout.btnVerCarrito.setOnClickListener { mostrarCarrito() }
        binding.btnReintentarCatalogo.setOnClickListener { carritoViewModel.cargar() }
        binding.btnPedirCuenta.setOnClickListener { comandaViewModel.pedirCuenta(mesaId) }
        binding.btnImprimir.setOnClickListener { impresionViewModel.imprimirPrecuenta() }
        binding.btnReimprimir.setOnClickListener { impresionViewModel.reimprimirPrecuenta() }
        binding.barraComanda.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.accionBloquear -> {
                    abrirBloqueo()
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Bloqueo rapido (PRB-81) desde la comanda.
     *
     * ComandaActivity solo pasa a STOPPED: su ViewModelStore conserva la misma
     * instancia de [CarritoViewModel] con los platos. Al desbloquear se reanuda
     * esta Activity y repeatOnLifecycle vuelve a recolectar el estado, sin
     * recargar el catalogo ni vaciar el carrito.
     */
    private fun abrirBloqueo() {
        startActivity(Intent(this, BloqueoActivity::class.java))
    }

    // ---------------------------------------------------------------------
    // Catalogo + carrito
    // ---------------------------------------------------------------------

    private fun renderCarrito(estado: CarritoUiState) {
        renderCatalogo(estado)
        renderCheckout(estado)
        renderModoLecura(estado)
    }

    private fun renderCatalogo(estado: CarritoUiState) {
        renderCategorias(estado.categorias, estado.categoriaSeleccionada)

        val hayErrorCatalogo = estado.errorCatalogo != null
        val mostrarCatalogo = !estado.cargando && !hayErrorCatalogo && !estado.catalogoVacio

        binding.textoModoDemo.isVisible = BuildConfig.USE_FAKE_DATA
        binding.progresoCatalogo.isVisible = estado.cargando
        binding.contenedorErrorCatalogo.isVisible = !estado.cargando && hayErrorCatalogo
        binding.textoCatalogoVacio.isVisible = estado.catalogoVacio
        binding.listaCatalogo.isVisible = mostrarCatalogo

        if (estado.errorCatalogo != null) {
            binding.textoErrorCatalogo.text = when (estado.errorCatalogo) {
                ErrorCarrito.RED -> getString(R.string.error_red)
                ErrorCarrito.SERVIDOR -> getString(R.string.error_generico)
                ErrorCarrito.DESCONOCIDO -> getString(R.string.comanda_catalogo_error)
            }
        }

        catalogoAdapter.submitList(estado.filas)
        // Puede haber dos lineas del mismo plato con modificadores distintos:
        // la tarjeta del catalogo muestra la cantidad total de ese plato.
        catalogoAdapter.actualizarCantidades(
            estado.items
                .groupBy { it.producto.id }
                .mapValues { entrada -> entrada.value.sumOf { it.cantidad } }
        )

        // Fade + slide-up solo la primera vez que el catalogo tiene platos.
        if (!animacionCatalogoEjecutada && mostrarCatalogo && estado.filas.isNotEmpty()) {
            animacionCatalogoEjecutada = true
            binding.listaCatalogo.aparecerConDesplazamiento()
        }
    }

    /**
     * Modo lectura (PRB-83): cuando termina la carga del pedido existente se
     * abre el carrito una sola vez; si el GET falla, se avisa sin bloquear el
     * catalogo.
     */
    private fun renderModoLecura(estado: CarritoUiState) {
        if (!estado.modoLectura || !estado.pedidoExistenteCargado) return

        val error = estado.errorPedidoExistente
        if (error != null) {
            if (!errorPedidoExistenteMostrado) {
                errorPedidoExistenteMostrado = true
                mostrarDialogoErrorPedidoExistente(error)
            }
            return
        }

        if (pedidoExistenteMostrado) return
        pedidoExistenteMostrado = true
        binding.root.post {
            if (!isFinishing && !isDestroyed) mostrarCarrito()
        }
    }

    /**
     * Barra flotante inferior. Solo aparece con items para no quitar espacio
     * al catalogo; al tocarla se abre [CarritoBottomSheet].
     */
    private fun renderCheckout(estado: CarritoUiState) {
        val barra = binding.barraCheckout
        barra.root.isVisible = !estado.sinLineas

        val contador = resources.getQuantityString(
            R.plurals.comanda_contador_items,
            estado.cantidadItems,
            estado.cantidadItems
        )
        barra.textoBarraItems.text = contador
        barra.textoBarraTotal.text = getString(
            R.string.checkout_total,
            formatearMoneda(estado.total)
        )
        barra.btnVerCarrito.text = if (estado.modoLectura) {
            getString(R.string.checkout_ver_pedido)
        } else {
            getString(R.string.checkout_ver_carrito)
        }
        barra.btnVerCarrito.contentDescription = getString(
            R.string.checkout_abrir_carrito_cd,
            contador,
            formatearMoneda(estado.total)
        )
    }

    /**
     * Reconstruye los chips solo cuando cambian las categorias o la seleccion.
     * Evita parpadeos y llamadas al ViewModel durante cada cambio del carrito.
     */
    private fun renderCategorias(categorias: List<Categoria>, seleccionada: Long?) {
        if (categorias == categoriasRenderizadas &&
            seleccionada == seleccionRenderizada &&
            binding.grupoCategorias.childCount > 0
        ) {
            return
        }

        categoriasRenderizadas = categorias
        seleccionRenderizada = seleccionada
        binding.grupoCategorias.removeAllViews()

        binding.grupoCategorias.addView(
            crearChip(
                texto = getString(R.string.comanda_categoria_todas),
                categoriaId = null,
                marcado = seleccionada == null
            )
        )
        categorias.forEach { categoria ->
            binding.grupoCategorias.addView(
                crearChip(
                    texto = categoria.nombre,
                    categoriaId = categoria.id,
                    marcado = seleccionada == categoria.id
                )
            )
        }
    }

    private fun crearChip(texto: String, categoriaId: Long?, marcado: Boolean): Chip {
        val chip = Chip(this)
        chip.id = View.generateViewId()
        chip.text = texto
        chip.isCheckable = true
        chip.isChecked = marcado
        chip.isClickable = true
        chip.setEnsureMinTouchTargetSize(true)
        chip.minHeight = resources.getDimensionPixelSize(R.dimen.toque_minimo)
        chip.chipStrokeWidth = 0f
        chip.setChipBackgroundColorResource(R.color.selector_fondo_chip_categoria)
        chip.setTextColor(
            ContextCompat.getColorStateList(this, R.color.selector_texto_chip_categoria)
        )
        chip.setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
        chip.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        chip.contentDescription = getString(R.string.cd_categoria, texto)
        chip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) carritoViewModel.filtrarCategoria(categoriaId)
        }
        return chip
    }

    /** Abre el carrito en el bottom sheet (un solo fragmento a la vez). */
    private fun mostrarCarrito() {
        if (!paraLlevar && mesaId <= 0L) return
        if (supportFragmentManager.findFragmentByTag(CarritoBottomSheet.TAG) != null) return
        CarritoBottomSheet.nueva(mesaId)
            .show(supportFragmentManager, CarritoBottomSheet.TAG)
    }

    private fun cerrarCarrito() {
        (supportFragmentManager.findFragmentByTag(CarritoBottomSheet.TAG) as? CarritoBottomSheet)
            ?.dismissAllowingStateLoss()
    }

    /** La nota se edita desde la linea del carrito del bottom sheet. */
    override fun onEditarNotas(item: ItemCarrito) {
        mostrarDialogoNota(item)
    }

    /** PRB-77: el icono de anular pide confirmacion antes de llamar al backend. */
    override fun onAnular(item: ItemCarrito) {
        cerrarDialogoAbierto()
        dialogoAnular?.cerrar()
        dialogoAnular = AnularPlatoDialog(this, item) { elegido ->
            carritoViewModel.anularItem(elegido)
        }.also { it.mostrar() }
    }

    private fun manejarEventoCarrito(evento: CarritoEvento) {
        when (evento) {
            is CarritoEvento.PedidoEnviadoOk -> {
                // A partir de aqui la mesa NO debe liberarse al salir.
                pedidoEnviado = true
                if (paraLlevar) pedidoIdParaLlevar = evento.pedido.id
                // PRB-90: el pedido ya viajo; no seguir refrescando esta vista.
                carritoViewModel.stopPolling()
                cerrarCarrito()
                mostrarDialogoPedidoEnviado(evento.pedido)
            }

            is CarritoEvento.ErrorAlEnviar -> mostrarDialogoErrorEnvio(evento.error)

            is CarritoEvento.ItemAnulado -> {
                cerrarDialogoAnular()
                mostrarSnackbar(getString(R.string.anular_plato_exito))
            }

            is CarritoEvento.ErrorAlAnular -> {
                cerrarDialogoAnular()
                mostrarDialogoErrorAnular(evento.error)
            }

            CarritoEvento.SesionExpirada -> {
                cerrarCarrito()
                mostrarDialogoSesionExpirada()
            }
        }
    }

    private fun mostrarDialogoPedidoEnviado(pedido: PedidoEnviado) {
        cerrarDialogoAbierto()

        val contenido = DialogPedidoEnviadoBinding.inflate(layoutInflater)
        contenido.textoExitoMensaje.text = if (paraLlevar) {
            getString(
                R.string.comanda_pedido_enviado_mensaje_llevar,
                clienteNombre,
                pedido.id
            )
        } else {
            getString(
                R.string.comanda_pedido_enviado_mensaje,
                numeroMesa,
                pedido.id
            )
        }
        contenido.textoExitoTotal.text = getString(
            R.string.comanda_pedido_enviado_total,
            formatearMoneda(pedido.total)
        )

        val dialogo = MaterialAlertDialogBuilder(this)
            .setView(contenido.root)
            .setCancelable(false)
            .create()

        contenido.btnVolverMapa.setOnClickListener {
            dialogo.dismiss()
            terminar(
                mesaLiberada = false,
                liberarPendiente = false,
                pedidoParaLlevarId = pedidoIdParaLlevar
            )
        }

        dialogo.setCanceledOnTouchOutside(false)
        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    private fun mostrarDialogoNota(item: ItemCarrito) {
        cerrarDialogoAbierto()

        val contenido = DialogNotaPlatoBinding.inflate(layoutInflater)
        contenido.textoNota.setText(item.notas)
        contenido.textoNota.setSelection(item.notas.length)

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.comanda_nota_titulo, item.producto.nombre))
            .setView(contenido.root)
            .setPositiveButton(R.string.comanda_nota_guardar) { _, _ ->
                carritoViewModel.actualizarNotas(
                    item,
                    contenido.textoNota.text?.toString().orEmpty()
                )
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .create()

        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    // ---------------------------------------------------------------------
    // Anulacion de platos enviados (PRB-77)
    // ---------------------------------------------------------------------

    private fun mostrarDialogoErrorAnular(error: ErrorApp) {
        if (error == ErrorApp.SESION_EXPIRADA) {
            mostrarDialogoSesionExpirada()
            return
        }

        mostrarDialogo(
            titulo = getString(R.string.anular_plato_error_titulo),
            mensaje = ComandaMensajes.errorAnular(this, error),
            cancelable = true,
            textoPositivo = R.string.accion_entendido
        )
    }

    /**
     * El Snackbar se pinta dentro del bottom sheet cuando esta abierto; si no,
     * se usa el Snackbar de marca de la Activity.
     */
    private fun mostrarSnackbar(texto: String) {
        val sheet = supportFragmentManager
            .findFragmentByTag(CarritoBottomSheet.TAG) as? CarritoBottomSheet
        if (sheet?.view != null) {
            sheet.mostrarSnackbar(texto)
            return
        }

        val snackbar = Snackbar.make(binding.root, texto, Snackbar.LENGTH_SHORT)
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
        snackbar.show()
        snackbar.view.background = ContextCompat.getDrawable(this, R.drawable.bg_snackbar)
    }

    /** Botones de impresion (PRB-73) y Snackbar del resultado. */
    private fun renderImpresion(estado: ImpresionUiState) {
        binding.btnImprimir.isEnabled = !estado.imprimiendo
        binding.btnReimprimir.isEnabled = !estado.imprimiendo

        val aviso = estado.aviso ?: return
        impresionViewModel.consumirAviso()
        mostrarSnackbar(ComandaMensajes.avisoImpresion(this, aviso))
    }

    private fun cerrarDialogoAnular() {
        dialogoAnular?.cerrar()
        dialogoAnular = null
    }

    private fun mostrarDialogoErrorEnvio(error: ErrorApp) {
        if (error == ErrorApp.SESION_EXPIRADA) {
            mostrarDialogoSesionExpirada()
            return
        }

        mostrarDialogo(
            titulo = getString(R.string.comanda_error_envio_titulo),
            mensaje = ComandaMensajes.errorEnvio(this, error),
            cancelable = true,
            textoPositivo = R.string.accion_reintentar,
            alAceptar = { carritoViewModel.enviarPedido(mesaId) },
            textoNegativo = R.string.accion_cancelar
        )
    }

    // ---------------------------------------------------------------------
    // Liberacion de mesa (PRB-39)
    // ---------------------------------------------------------------------

    private fun renderComanda(estado: ComandaUiState) {
        val alpha = if (estado.liberandoMesa) ALPHA_DESHABILITADO else ALPHA_ACTIVO
        binding.barraComanda.navigationIcon?.alpha = alpha

        binding.btnPedirCuenta.isEnabled = !estado.solicitandoCuenta && !estado.liberandoMesa
        binding.btnPedirCuenta.text = if (estado.solicitandoCuenta) {
            getString(R.string.comanda_cuenta_solicitando)
        } else {
            getString(R.string.comanda_pedir_cuenta)
        }
    }

    /**
     * Atras y la flecha de la toolbar abren el dialogo de confirmacion.
     * Si el pedido ya se envio (PRB-38), la cuenta ya se pidio (PRB-83) o la
     * pantalla es de solo lectura (mesa de otro mozo), la mesa no se libera.
     */
    private fun intentarSalir() {
        if (saliendo || dialogoLiberar?.isShowing == true) return
        // PRB-94: un pedido para llevar no tiene mesa que liberar.
        if (paraLlevar || pedidoEnviado || cuentaSolicitada || modoLectura) {
            terminar(
                mesaLiberada = false,
                liberarPendiente = false,
                pedidoParaLlevarId = if (paraLlevar) pedidoIdParaLlevar else 0L
            )
            return
        }
        mostrarDialogoLiberarMesa()
    }

    private fun mostrarDialogoLiberarMesa() {
        cerrarDialogoAbierto()

        val contenido = DialogLiberarMesaBinding.inflate(layoutInflater)
        val dialogo = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_LaCasaBlanca_Dialogo_LiberarMesa
        )
            .setView(contenido.root)
            .setCancelable(true)
            .create()

        contenido.btnCancelar.setOnClickListener { dialogo.dismiss() }
        contenido.btnLiberar.setOnClickListener { confirmarLiberacion(contenido) }

        dialogo.setCanceledOnTouchOutside(true)
        dialogo.setOnDismissListener {
            if (dialogoLiberar === dialogo) dialogoLiberar = null
            if (dialogoAbierto === dialogo) dialogoAbierto = null
        }
        dialogo.show()

        dialogoLiberar = dialogo
        dialogoAbierto = dialogo
    }

    private fun confirmarLiberacion(contenido: DialogLiberarMesaBinding) {
        if (saliendo) return
        saliendo = true

        dialogoLiberar?.setCancelable(false)
        dialogoLiberar?.setCanceledOnTouchOutside(false)

        contenido.grupoBotones.isVisible = false
        contenido.progresoLiberar.isVisible = true
        contenido.textoProgreso.isVisible = true

        comandaViewModel.liberarMesa(mesaId)
    }

    private fun manejarEventoComanda(evento: ComandaEvento) {
        when (evento) {
            ComandaEvento.MesaLiberada -> {
                cerrarDialogoLiberar()
                terminar(mesaLiberada = true, liberarPendiente = false)
            }

            is ComandaEvento.ErrorAlLiberar -> {
                saliendo = false
                cerrarDialogoLiberar()
                mostrarDialogoErrorLiberar(evento.error)
            }

            ComandaEvento.CuentaSolicitada -> {
                // A partir de aqui la mesa queda en amarillo: no se libera al salir.
                cuentaSolicitada = true
                mostrarDialogoCuentaSolicitada()
            }

            is ComandaEvento.ErrorAlPedirCuenta -> mostrarDialogoErrorCuenta(evento.error)

            ComandaEvento.SesionExpirada -> {
                saliendo = false
                cerrarDialogoLiberar()
                mostrarDialogoSesionExpirada()
            }
        }
    }

    /** Exito de "Pedir Cuenta" (PRB-83): confirma y vuelve al mapa. */
    private fun mostrarDialogoCuentaSolicitada() {
        cerrarDialogoAbierto()

        val contenido = DialogCuentaSolicitadaBinding.inflate(layoutInflater)
        contenido.textoCuentaMensaje.text = getString(R.string.comanda_cuenta_mensaje)
        contenido.textoCuentaDetalle.text = getString(R.string.comanda_cuenta_detalle, numeroMesa)

        val dialogo = MaterialAlertDialogBuilder(this)
            .setView(contenido.root)
            .setCancelable(false)
            .create()

        contenido.btnVolverMapaCuenta.setOnClickListener {
            dialogo.dismiss()
            terminar(mesaLiberada = false, liberarPendiente = false)
        }

        dialogo.setCanceledOnTouchOutside(false)
        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    private fun mostrarDialogoErrorCuenta(error: ErrorApp) {
        if (error == ErrorApp.SESION_EXPIRADA) {
            mostrarDialogoSesionExpirada()
            return
        }

        mostrarDialogo(
            titulo = getString(R.string.comanda_cuenta_error_titulo),
            mensaje = ComandaMensajes.errorCuenta(this, error),
            cancelable = true,
            textoPositivo = R.string.accion_reintentar,
            alAceptar = { comandaViewModel.pedirCuenta(mesaId) },
            textoNegativo = R.string.accion_cancelar
        )
    }

    /** Fallo del GET del pedido existente: se puede reintentar o seguir en el catalogo. */
    private fun mostrarDialogoErrorPedidoExistente(error: ErrorCarrito) {
        val mensaje = if (error == ErrorCarrito.RED) {
            getString(R.string.comanda_pedido_existente_error_red)
        } else {
            getString(R.string.comanda_pedido_existente_error_generico)
        }

        mostrarDialogo(
            titulo = getString(R.string.comanda_pedido_existente_error_titulo),
            mensaje = mensaje,
            cancelable = true,
            textoPositivo = R.string.accion_reintentar,
            alAceptar = {
                errorPedidoExistenteMostrado = false
                carritoViewModel.cargarPedidoExistente(mesaId)
            },
            textoNegativo = R.string.comanda_pedido_existente_seguir
        )
    }

    private fun mostrarDialogoErrorLiberar(error: ErrorApp) {
        val mensaje = if (error == ErrorApp.RED) {
            getString(R.string.error_red)
        } else {
            getString(R.string.error_liberar_mesa, numeroMesa)
        }
        mostrarDialogo(
            titulo = getString(R.string.error_liberar_mesa_titulo),
            mensaje = mensaje,
            cancelable = false,
            textoPositivo = R.string.accion_reintentar,
            alAceptar = ::intentarSalir,
            textoNegativo = R.string.comanda_salir_sin_liberar,
            alCancelar = { terminar(mesaLiberada = false, liberarPendiente = true) }
        )
    }

    private fun mostrarDialogoSesionExpirada() {
        mostrarDialogo(
            titulo = getString(R.string.sesion_expirada_titulo),
            mensaje = getString(R.string.sesion_expirada),
            cancelable = false,
            alAceptar = ::irALogin
        )
    }

    private fun mostrarDialogo(
        titulo: CharSequence,
        mensaje: CharSequence,
        cancelable: Boolean,
        textoPositivo: Int = R.string.accion_aceptar,
        alAceptar: (() -> Unit)? = null,
        textoNegativo: Int? = null,
        alCancelar: (() -> Unit)? = null
    ) {
        cerrarDialogoLiberar()
        dialogoAbierto?.dismiss()
        val constructor = MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setCancelable(cancelable)
            .setPositiveButton(textoPositivo) { dialog, _ ->
                dialog.dismiss()
                alAceptar?.invoke()
            }
        if (textoNegativo != null) {
            constructor.setNegativeButton(textoNegativo) { dialog, _ ->
                dialog.dismiss()
                alCancelar?.invoke()
            }
        }
        val dialogo = constructor.create()
        dialogo.setCanceledOnTouchOutside(cancelable)
        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    private fun cerrarDialogoLiberar() {
        dialogoLiberar?.dismiss()
        dialogoLiberar = null
    }

    private fun cerrarDialogoAbierto() {
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
    }

    /**
     * Devuelve el control al mapa de mesas.
     * [liberarPendiente] pide a MesasActivity un ultimo intento con
     * `MesasViewModel.liberarMesa()` si el PATCH fallo aqui.
     * [EXTRA_PEDIDO_ENVIADO] queda en true cuando el POST /pedidos fue exitoso:
     * la mesa se queda en su estado de atencion y no se libera.
     */
    private fun terminar(
        mesaLiberada: Boolean,
        liberarPendiente: Boolean,
        pedidoParaLlevarId: Long = 0L
    ) {
        if (isFinishing) return
        val datos = Intent().apply {
            putExtra(EXTRA_MESA_ID, mesaId)
            putExtra(EXTRA_MESA_LIBERADA, mesaLiberada)
            putExtra(EXTRA_LIBERAR_PENDIENTE, liberarPendiente)
            putExtra(EXTRA_PEDIDO_ENVIADO, pedidoEnviado)
            putExtra(EXTRA_PEDIDO_PARA_LLEVAR_ID, pedidoParaLlevarId)
        }
        setResult(RESULT_OK, datos)
        finish()
    }

    private fun irALogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_MESA_ID = "extra_mesa_id"
        const val EXTRA_MESA_NUMERO = "extra_mesa_numero"
        const val EXTRA_MESA_UBICACION = "extra_mesa_ubicacion"

        /** true cuando POST /api/v1/pedidos respondio 201: la mesa no se libera. */
        const val EXTRA_PEDIDO_ENVIADO = "extra_pedido_enviado"

        /** true para mesas OCUPADA: se consulta y muestra el pedido existente (PRB-83). */
        const val EXTRA_MODO_LECTURA = "extra_modo_lectura"

        /** true cuando el PATCH CUENTA_SOLICITADA respondio 200: la mesa no se libera. */
        const val EXTRA_CUENTA_SOLICITADA = "extra_cuenta_solicitada"

        const val EXTRA_MESA_LIBERADA = "extra_mesa_liberada"
        const val EXTRA_LIBERAR_PENDIENTE = "extra_liberar_pendiente"

        /** PRB-94: comanda para llevar (sin mesa). */
        const val EXTRA_PARA_LLEVAR = "extra_para_llevar"

        /** PRB-94: nombre del cliente que identifica el pedido para llevar. */
        const val EXTRA_CLIENTE_NOMBRE = "extra_cliente_nombre"

        /** PRB-94: id del pedido enviado, para el aviso al volver al mapa. */
        const val EXTRA_PEDIDO_PARA_LLEVAR_ID = "extra_pedido_para_llevar_id"

        private const val ALPHA_ACTIVO = 255
        private const val ALPHA_DESHABILITADO = 120
    }
}
