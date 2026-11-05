package com.lacasablanca.mesero.ui.comanda

import android.content.Intent
import android.graphics.text.LineBreaker
import android.os.Bundle
import android.text.Layout
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.ActivityComandaBinding
import com.lacasablanca.mesero.databinding.DialogLiberarMesaBinding
import com.lacasablanca.mesero.databinding.DialogNotaPlatoBinding
import com.lacasablanca.mesero.databinding.DialogPedidoEnviadoBinding
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.model.Categoria
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.ui.cobro.CobroActivity
import com.lacasablanca.mesero.ui.common.aparecerConDesplazamiento
import com.lacasablanca.mesero.ui.common.formatearMoneda
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
                pedidoRepository = contenedor.pedidoRepository
            )
        }
    }

    private val comandaViewModel: ComandaViewModel by viewModels {
        GenericViewModelFactory { ComandaViewModel(contenedor.mesaRepository) }
    }

    private lateinit var catalogoAdapter: CatalogoAdapter

    private var mesaId = -1L
    private var numeroMesa = 0
    private var pedidoEnviado = false
    private var saliendo = false
    private var dialogoAbierto: AlertDialog? = null
    private var dialogoLiberar: AlertDialog? = null

    private var categoriasRenderizadas: List<Categoria>? = null
    private var seleccionRenderizada: Long? = null
    private var animacionCatalogoEjecutada = false

    /** La pantalla de cobro devuelve RESULT_OK cuando el cobro se registro. */
    private val lanzadorCobro = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == RESULT_OK) {
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_MESA_ID, mesaId)
                putExtra(EXTRA_MESA_NUMERO, numeroMesa)
                putExtra(EXTRA_MESA_COBRADA, true)
            })
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComandaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mesaId = intent.getLongExtra(EXTRA_MESA_ID, -1L)
        numeroMesa = intent.getIntExtra(EXTRA_MESA_NUMERO, 0)
        val ubicacion = intent.getStringExtra(EXTRA_MESA_UBICACION).orEmpty()
        pedidoEnviado = savedInstanceState?.getBoolean(EXTRA_PEDIDO_ENVIADO, false)
            ?: intent.getBooleanExtra(EXTRA_PEDIDO_ENVIADO, false)

        if (mesaId <= 0L || numeroMesa <= 0) {
            finish()
            return
        }

        binding.textoTitulo.text = getString(R.string.comanda_titulo, numeroMesa, ubicacion)
        binding.badgeEstado.text = getString(R.string.comanda_badge)
        binding.textoModoDemo.isVisible = BuildConfig.USE_FAKE_DATA

        prepararListas()
        prepararAcciones()
        binding.btnCobrar.isVisible = pedidoEnviado

        // Atras y la flecha de la toolbar piden confirmacion antes de liberar.
        binding.barraComanda.setNavigationOnClickListener { intentarSalir() }
        onBackPressedDispatcher.addCallback(this) { intentarSalir() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { carritoViewModel.estado.collect(::renderCarrito) }
                launch { carritoViewModel.eventos.collect(::manejarEventoCarrito) }
                launch { comandaViewModel.estado.collect(::renderComanda) }
                launch { comandaViewModel.eventos.collect(::manejarEventoComanda) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Si el pedido ya viajo, una rotacion no debe permitir liberar la mesa.
        outState.putBoolean(EXTRA_PEDIDO_ENVIADO, pedidoEnviado)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cerrarDialogoLiberar()
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        super.onDestroy()
    }

    private fun prepararListas() {
        catalogoAdapter = CatalogoAdapter(
            alAgregar = carritoViewModel::agregarProducto,
            alAumentar = carritoViewModel::aumentarCantidad,
            alDisminuir = carritoViewModel::disminuirCantidad
        )

        binding.listaCatalogo.layoutManager = LinearLayoutManager(this)
        binding.listaCatalogo.adapter = catalogoAdapter
    }

    private fun prepararAcciones() {
        binding.barraCheckout.btnVerCarrito.setOnClickListener { mostrarCarrito() }
        binding.btnReintentarCatalogo.setOnClickListener { carritoViewModel.cargar() }
        binding.btnCobrar.setOnClickListener { abrirCobro() }
    }

    // ---------------------------------------------------------------------
    // Catalogo + carrito
    // ---------------------------------------------------------------------

    private fun renderCarrito(estado: CarritoUiState) {
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
        catalogoAdapter.actualizarCantidades(
            estado.items.associate { it.producto.id to it.cantidad }
        )

        // Fade + slide-up solo la primera vez que el catalogo tiene platos.
        if (!animacionCatalogoEjecutada && mostrarCatalogo && estado.filas.isNotEmpty()) {
            animacionCatalogoEjecutada = true
            binding.listaCatalogo.aparecerConDesplazamiento()
        }

        renderCheckout(estado)
    }

    /**
     * Barra flotante inferior. Solo aparece con items para no quitar espacio
     * al catalogo; al tocarla se abre [CarritoBottomSheet].
     */
    private fun renderCheckout(estado: CarritoUiState) {
        val barra = binding.barraCheckout
        barra.root.isVisible = !estado.carritoVacio && !pedidoEnviado
        binding.btnCobrar.isVisible = pedidoEnviado

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
        if (mesaId <= 0L) return
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

    private fun manejarEventoCarrito(evento: CarritoEvento) {
        when (evento) {
            is CarritoEvento.PedidoEnviadoOk -> {
                // A partir de aqui la mesa NO debe liberarse al salir.
                pedidoEnviado = true
                binding.btnCobrar.isVisible = true
                cerrarCarrito()
                mostrarDialogoPedidoEnviado(evento.pedido)
            }

            is CarritoEvento.ErrorAlEnviar -> mostrarDialogoErrorEnvio(evento.error)
            CarritoEvento.SesionExpirada -> {
                cerrarCarrito()
                mostrarDialogoSesionExpirada()
            }
        }
    }

    private fun mostrarDialogoPedidoEnviado(pedido: PedidoEnviado) {
        cerrarDialogoAbierto()

        val contenido = DialogPedidoEnviadoBinding.inflate(layoutInflater)
        contenido.textoExitoMensaje.text = getString(
            R.string.comanda_pedido_enviado_mensaje,
            numeroMesa,
            pedido.id
        )
        contenido.textoExitoTotal.text = getString(
            R.string.comanda_pedido_enviado_total,
            formatearMoneda(pedido.total)
        )

        val dialogo = MaterialAlertDialogBuilder(this)
            .setView(contenido.root)
            .setCancelable(false)
            .create()

        contenido.btnCobrarAhora.setOnClickListener {
            dialogo.dismiss()
            abrirCobro()
        }

        contenido.btnVolverMapa.setOnClickListener {
            dialogo.dismiss()
            terminar(mesaLiberada = false, liberarPendiente = false)
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
                    item.producto.id,
                    contenido.textoNota.text?.toString().orEmpty()
                )
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .create()

        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    private fun mostrarDialogoErrorEnvio(error: ErrorApp) {
        if (error == ErrorApp.SESION_EXPIRADA) {
            mostrarDialogoSesionExpirada()
            return
        }

        mostrarDialogo(
            titulo = getString(R.string.comanda_error_envio_titulo),
            mensaje = mensajeErrorEnvio(error),
            cancelable = true,
            textoPositivo = R.string.accion_reintentar,
            alAceptar = { carritoViewModel.enviarPedido(mesaId) },
            textoNegativo = R.string.accion_cancelar
        )
    }

    private fun mensajeErrorEnvio(error: ErrorApp): String = when (error) {
        ErrorApp.RED -> getString(R.string.comanda_error_envio_red)
        ErrorApp.CONFLICTO -> getString(R.string.comanda_error_envio_conflicto)
        ErrorApp.VALIDACION -> getString(R.string.comanda_error_envio_validacion)
        else -> getString(R.string.comanda_error_envio_generico)
    }

    // ---------------------------------------------------------------------
    // Liberacion de mesa (PRB-39)
    // ---------------------------------------------------------------------

    private fun renderComanda(estado: ComandaUiState) {
        val alpha = if (estado.liberandoMesa) ALPHA_DESHABILITADO else ALPHA_ACTIVO
        binding.barraComanda.navigationIcon?.alpha = alpha
    }

    /**
     * Atras y la flecha de la toolbar abren el dialogo de confirmacion.
     * Si el pedido ya se envio (PRB-38), la mesa no se libera.
     */
    private fun intentarSalir() {
        if (saliendo || dialogoLiberar?.isShowing == true) return
        if (pedidoEnviado) {
            terminar(mesaLiberada = false, liberarPendiente = false)
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

            ComandaEvento.SesionExpirada -> {
                saliendo = false
                cerrarDialogoLiberar()
                mostrarDialogoSesionExpirada()
            }
        }
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
    private fun terminar(mesaLiberada: Boolean, liberarPendiente: Boolean) {
        if (isFinishing) return
        val datos = Intent().apply {
            putExtra(EXTRA_MESA_ID, mesaId)
            putExtra(EXTRA_MESA_LIBERADA, mesaLiberada)
            putExtra(EXTRA_LIBERAR_PENDIENTE, liberarPendiente)
            putExtra(EXTRA_PEDIDO_ENVIADO, pedidoEnviado)
        }
        setResult(RESULT_OK, datos)
        finish()
    }

    /** Abre el cobro de la mesa actual y espera su resultado. */
    private fun abrirCobro() {
        if (mesaId <= 0L) return
        lanzadorCobro.launch(Intent(this, CobroActivity::class.java).apply {
            putExtra(CobroActivity.EXTRA_MESA_ID, mesaId)
            putExtra(CobroActivity.EXTRA_MESA_NUMERO, numeroMesa)
        })
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

        const val EXTRA_MESA_LIBERADA = "extra_mesa_liberada"
        const val EXTRA_LIBERAR_PENDIENTE = "extra_liberar_pendiente"

        /** true cuando el cobro termino y Mesas debe refrescar el mapa. */
        const val EXTRA_MESA_COBRADA = "extra_mesa_cobrada"

        private const val ALPHA_ACTIVO = 255
        private const val ALPHA_DESHABILITADO = 120
    }
}
