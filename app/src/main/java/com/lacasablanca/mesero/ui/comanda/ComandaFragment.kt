package com.lacasablanca.mesero.ui.comanda

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.FragmentComandaBinding
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.domain.model.DestinoPedido
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.domain.model.PedidoEnviado
import com.lacasablanca.mesero.ui.bloqueo.BloqueoActivity
import com.lacasablanca.mesero.ui.common.aparecerConDesplazamiento
import com.lacasablanca.mesero.ui.common.formatearMoneda
import com.lacasablanca.mesero.ui.impresion.ImpresionPrecuentaViewModel
import com.lacasablanca.mesero.ui.impresion.ImpresionUiState
import com.lacasablanca.mesero.ui.login.LoginActivity
import com.lacasablanca.mesero.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Comanda del mesero dentro del contenedor principal (PRB-88, Fase 4).
 *
 * - Es la migracion de la antigua pantalla de comanda: mismo layout y mismo flujo de
 *   catalogo, carrito, modificadores, notas, envio, anulacion, cuenta y
 *   liberacion de mesa.
 * - [CarritoViewModel] vive en el scope de MainActivity (`activityViewModels`):
 *   cambiar de tab oculta la comanda pero no destruye el carrito.
 * - El destino (mesa o para llevar) se fija en [onCreate] y se aplica con
 *   [CarritoViewModel.configurarDestino]; si se abre otra mesa, el carrito
 *   anterior se descarta.
 * - Al terminar (enviar, liberar o salir) publica el resultado por
 *   `FragmentResult`, limpia el carrito y pide a MainActivity volver al mapa.
 */
// Migracion directa de la pantalla de comanda; dividir en renderer/controller
// en un refactor posterior.
@Suppress("LargeClass")
class ComandaFragment : Fragment(), CarritoBottomSheet.Listener {

    private var _binding: FragmentComandaBinding? = null
    private val binding get() = _binding!!

    private val contenedor get() = (requireActivity().application as MeseroApp).container

    /** Los dialogos de la comanda piden un AppCompatActivity. */
    private val activityHost: AppCompatActivity get() = requireActivity() as AppCompatActivity

    private lateinit var destino: DestinoPedido

    private var mesaId = -1L
    private var numeroMesa = 0
    private var ubicacionMesa = ""
    private var paraLlevar = false
    private var clienteNombre = ""
    private var pedidoIdParaLlevar = 0L
    private var pedidoEnviado = false
    private var cuentaSolicitada = false
    private var modoLectura = false
    private var argumentosInvalidos = false
    private var saliendo = false
    private var yendoALogin = false

    private lateinit var catalogoAdapter: CatalogoAdapter
    private lateinit var catalogoChips: CatalogoChips

    private val selectorModificadores: SelectorModificadores by lazy {
        SelectorModificadores(activityHost) { producto, seleccionados ->
            carritoViewModel.agregarProducto(producto, seleccionados)
        }
    }

    private var callbackAtras: OnBackPressedCallback? = null

    private var dialogoAbierto: AlertDialog? = null
    private var dialogoLiberar: LiberarMesaDialog? = null
    private var dialogoAnular: AnularPlatoDialog? = null
    private var dialogoPedidoEnviado: PedidoEnviadoDialog? = null
    private var dialogoNota: NotaPlatoDialog? = null
    private var dialogoCuentaSolicitada: CuentaSolicitadaDialog? = null

    private var pedidoExistenteMostrado = false
    private var errorPedidoExistenteMostrado = false
    private var animacionCatalogoEjecutada = false

    /**
     * PRB-88: el carrito vive en MainActivity. La primera comanda crea la
     * instancia con su destino; las siguientes reutilizan la misma y llaman a
     * `configurarDestino` para no mezclar mesas/clientes.
     */
    private val carritoViewModel: CarritoViewModel by activityViewModels {
        GenericViewModelFactory {
            CarritoViewModel(
                catalogoRepository = contenedor.catalogoRepository,
                pedidoRepository = contenedor.pedidoRepository,
                destino = destino
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        mesaId = args.getLong(EXTRA_MESA_ID, -1L)
        numeroMesa = args.getInt(EXTRA_MESA_NUMERO, 0)
        ubicacionMesa = args.getString(EXTRA_MESA_UBICACION).orEmpty()
        paraLlevar = savedInstanceState?.getBoolean(EXTRA_PARA_LLEVAR, false)
            ?: args.getBoolean(EXTRA_PARA_LLEVAR, false)
        clienteNombre = savedInstanceState?.getString(EXTRA_CLIENTE_NOMBRE)
            ?: args.getString(EXTRA_CLIENTE_NOMBRE).orEmpty().trim()
        pedidoIdParaLlevar = savedInstanceState?.getLong(EXTRA_PEDIDO_PARA_LLEVAR_ID, 0L) ?: 0L
        pedidoEnviado = savedInstanceState?.getBoolean(EXTRA_PEDIDO_ENVIADO, false)
            ?: args.getBoolean(EXTRA_PEDIDO_ENVIADO, false)
        cuentaSolicitada = savedInstanceState?.getBoolean(EXTRA_CUENTA_SOLICITADA, false)
            ?: false
        modoLectura = savedInstanceState?.getBoolean(EXTRA_MODO_LECTURA, false)
            ?: args.getBoolean(EXTRA_MODO_LECTURA, false)
        destino = if (paraLlevar) {
            DestinoPedido.ParaLlevar(clienteNombre)
        } else {
            DestinoPedido.Mesa(mesaId)
        }
        argumentosInvalidos = if (paraLlevar) {
            clienteNombre.isBlank()
        } else {
            mesaId <= 0L || numeroMesa <= 0
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComandaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (argumentosInvalidos) {
            view.post { (requireActivity() as? MainActivity)?.cerrarComanda() }
            return
        }

        configurarCabecera()
        binding.textoModoDemo.isVisible = BuildConfig.USE_FAKE_DATA
        prepararListas()
        prepararAcciones()
        configurarAtras()

        carritoViewModel.configurarDestino(destino)
        if (modoLectura) carritoViewModel.activarModoLecura(mesaId)

        observarEstado()
    }

    /**
     * PRB-90: refresca el pedido existente solo mientras la comanda esta
     * visible en modo lectura. Al cambiar de tab se detiene; al volver, se
     * reanuda sin perder el carrito.
     */
    override fun onStart() {
        super.onStart()
        actualizarPolling()
    }

    override fun onStop() {
        if (!argumentosInvalidos) carritoViewModel.stopPolling()
        super.onStop()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        callbackAtras?.isEnabled = !hidden
        actualizarPolling()
    }

    private fun actualizarPolling() {
        if (argumentosInvalidos) return
        if (!isHidden && modoLectura && !pedidoEnviado) {
            carritoViewModel.startPolling(mesaId)
        } else {
            carritoViewModel.stopPolling()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_PEDIDO_ENVIADO, pedidoEnviado)
        outState.putBoolean(EXTRA_CUENTA_SOLICITADA, cuentaSolicitada)
        outState.putBoolean(EXTRA_MODO_LECTURA, modoLectura)
        outState.putBoolean(EXTRA_PARA_LLEVAR, paraLlevar)
        outState.putString(EXTRA_CLIENTE_NOMBRE, clienteNombre)
        outState.putLong(EXTRA_PEDIDO_PARA_LLEVAR_ID, pedidoIdParaLlevar)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        cerrarDialogoLiberar()
        cerrarDialogoAnular()
        dialogoPedidoEnviado?.cerrar()
        dialogoPedidoEnviado = null
        dialogoNota?.cerrar()
        dialogoNota = null
        dialogoCuentaSolicitada?.cerrar()
        dialogoCuentaSolicitada = null
        selectorModificadores.cerrar()
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        callbackAtras = null
        pedidoExistenteMostrado = false
        errorPedidoExistenteMostrado = false
        animacionCatalogoEjecutada = false
        _binding = null
        super.onDestroyView()
    }

    private fun configurarAtras() {
        binding.barraComanda.setNavigationOnClickListener { intentarSalir() }
        callbackAtras = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            intentarSalir()
        }
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { carritoViewModel.estado.collect(::renderCarrito) }
                launch { carritoViewModel.eventos.collect(::manejarEventoCarrito) }
                launch { comandaViewModel.estado.collect(::renderComanda) }
                launch { comandaViewModel.eventos.collect(::manejarEventoComanda) }
                launch { impresionViewModel.estado.collect(::renderImpresion) }
            }
        }
    }

    /** Cabecera y acciones propias de una comanda de mesa o para llevar. */
    private fun configurarCabecera() {
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
            TextViewCompat.setCompoundDrawableTintList(
                binding.badgeEstado,
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.tertiary))
            )
            // Sin mesa no hay "Pedir Cuenta" ni pre-cuenta por mesa.
            binding.btnPedirCuenta.isVisible = false
            binding.grupoImpresion.isVisible = false
        } else {
            binding.textoTitulo.text = getString(
                R.string.comanda_titulo,
                numeroMesa,
                ubicacionMesa
            )
            binding.badgeEstado.text = if (modoLectura) {
                getString(R.string.comanda_badge_lectura)
            } else {
                getString(R.string.comanda_badge)
            }
        }
    }

    private fun prepararListas() {
        catalogoAdapter = CatalogoAdapter(
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
            alDisminuir = { producto -> carritoViewModel.disminuirCantidad(producto.id) }
        )

        binding.listaCatalogo.layoutManager = LinearLayoutManager(requireContext())
        binding.listaCatalogo.adapter = catalogoAdapter

        catalogoChips = CatalogoChips(activityHost, binding.grupoCategorias) { categoriaId ->
            carritoViewModel.filtrarCategoria(categoriaId)
        }
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

    private fun abrirBloqueo() {
        startActivity(Intent(requireContext(), BloqueoActivity::class.java))
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
        catalogoChips.render(estado.categorias, estado.categoriaSeleccionada)

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
            estado.items
                .groupBy { it.producto.id }
                .mapValues { entrada -> entrada.value.sumOf { it.cantidad } }
        )

        if (!animacionCatalogoEjecutada && mostrarCatalogo && estado.filas.isNotEmpty()) {
            animacionCatalogoEjecutada = true
            binding.listaCatalogo.aparecerConDesplazamiento()
        }
    }

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
            if (isAdded && _binding != null) mostrarCarrito()
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

    private fun mostrarCarrito() {
        if (!paraLlevar && mesaId <= 0L) return
        if (childFragmentManager.findFragmentByTag(CarritoBottomSheet.TAG) != null) return
        CarritoBottomSheet.nueva(mesaId)
            .show(childFragmentManager, CarritoBottomSheet.TAG)
    }

    private fun cerrarCarrito() {
        (childFragmentManager.findFragmentByTag(CarritoBottomSheet.TAG) as? CarritoBottomSheet)
            ?.dismissAllowingStateLoss()
    }

    override fun onEditarNotas(item: ItemCarrito) {
        cerrarDialogoAbierto()
        dialogoNota?.cerrar()
        dialogoNota = NotaPlatoDialog(activityHost, item) { notas ->
            carritoViewModel.actualizarNotas(item, notas)
        }.also { it.mostrar() }
    }

    override fun onAnular(item: ItemCarrito) {
        cerrarDialogoAbierto()
        dialogoAnular?.cerrar()
        dialogoAnular = AnularPlatoDialog(activityHost, item) { elegido ->
            carritoViewModel.anularItem(elegido)
        }.also { it.mostrar() }
    }

    private fun manejarEventoCarrito(evento: CarritoEvento) {
        when (evento) {
            is CarritoEvento.PedidoEnviadoOk -> {
                pedidoEnviado = true
                if (paraLlevar) pedidoIdParaLlevar = evento.pedido.id
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
        dialogoPedidoEnviado?.cerrar()
        dialogoPedidoEnviado = PedidoEnviadoDialog(
            activity = activityHost,
            paraLlevar = paraLlevar,
            clienteNombre = clienteNombre,
            numeroMesa = numeroMesa,
            pedido = pedido
        ) {
            terminar(
                mesaLiberada = false,
                liberarPendiente = false,
                pedidoParaLlevarId = pedidoIdParaLlevar
            )
        }.also { it.mostrar() }
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
            mensaje = ComandaMensajes.errorAnular(requireContext(), error),
            cancelable = true,
            textoPositivo = R.string.accion_entendido
        )
    }

    /**
     * El Snackbar se pinta dentro del bottom sheet cuando esta abierto; si no,
     * se usa el Snackbar de marca de la comanda.
     */
    private fun mostrarSnackbar(texto: String) {
        val sheet = childFragmentManager
            .findFragmentByTag(CarritoBottomSheet.TAG) as? CarritoBottomSheet
        if (sheet?.view != null) {
            sheet.mostrarSnackbar(texto)
            return
        }

        val snackbar = Snackbar.make(binding.root, texto, Snackbar.LENGTH_SHORT)
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        snackbar.show()
        snackbar.view.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_snackbar)
    }

    private fun renderImpresion(estado: ImpresionUiState) {
        binding.btnImprimir.isEnabled = !estado.imprimiendo
        binding.btnReimprimir.isEnabled = !estado.imprimiendo

        val aviso = estado.aviso ?: return
        impresionViewModel.consumirAviso()
        mostrarSnackbar(ComandaMensajes.avisoImpresion(requireContext(), aviso))
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
            mensaje = ComandaMensajes.errorEnvio(requireContext(), error),
            cancelable = true,
            textoPositivo = R.string.accion_reintentar,
            alAceptar = { carritoViewModel.enviarPedido() },
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

    private fun intentarSalir() {
        if (saliendo || dialogoLiberar?.estaMostrando() == true) return
        if (noDebeLiberarMesa()) {
            terminar(
                mesaLiberada = false,
                liberarPendiente = false,
                pedidoParaLlevarId = if (paraLlevar) pedidoIdParaLlevar else 0L
            )
            return
        }
        mostrarDialogoLiberarMesa()
    }

    /** Un pedido enviado, cobrado, de otro mozo o para llevar no libera mesa. */
    private fun noDebeLiberarMesa(): Boolean {
        if (paraLlevar || pedidoEnviado) return true
        return cuentaSolicitada || modoLectura
    }

    private fun mostrarDialogoLiberarMesa() {
        cerrarDialogoAbierto()
        dialogoLiberar?.cerrar()
        dialogoLiberar = LiberarMesaDialog(activityHost) { confirmarLiberacion() }
            .also { it.mostrar() }
    }

    private fun confirmarLiberacion() {
        if (saliendo) return
        saliendo = true
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

    private fun mostrarDialogoCuentaSolicitada() {
        cerrarDialogoAbierto()
        dialogoCuentaSolicitada?.cerrar()
        dialogoCuentaSolicitada = CuentaSolicitadaDialog(activityHost, numeroMesa) {
            terminar(mesaLiberada = false, liberarPendiente = false)
        }.also { it.mostrar() }
    }

    private fun mostrarDialogoErrorCuenta(error: ErrorApp) {
        if (error == ErrorApp.SESION_EXPIRADA) {
            mostrarDialogoSesionExpirada()
            return
        }

        mostrarDialogo(
            titulo = getString(R.string.comanda_cuenta_error_titulo),
            mensaje = ComandaMensajes.errorCuenta(requireContext(), error),
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
        val dialogo = ComandaDialogos.crear(
            activity = activityHost,
            titulo = titulo,
            mensaje = mensaje,
            cancelable = cancelable,
            textoPositivo = textoPositivo,
            alAceptar = alAceptar,
            textoNegativo = textoNegativo,
            alCancelar = alCancelar
        )
        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    private fun cerrarDialogoLiberar() {
        dialogoLiberar?.cerrar()
        dialogoLiberar = null
    }

    private fun cerrarDialogoAbierto() {
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        dialogoNota?.cerrar()
        dialogoNota = null
        dialogoCuentaSolicitada?.cerrar()
        dialogoCuentaSolicitada = null
    }

    /**
     * Cierra la comanda: limpia el carrito (scope de MainActivity), publica el
     * resultado para que SalonFragment refresque el mapa y pide volver al
     * contenedor. MainActivity hace el popBackStack del detalle.
     */
    private fun terminar(
        mesaLiberada: Boolean,
        liberarPendiente: Boolean,
        pedidoParaLlevarId: Long = 0L
    ) {
        if (!isAdded) return
        carritoViewModel.limpiarCarrito()
        val datos = Bundle().apply {
            putLong(EXTRA_MESA_ID, mesaId)
            putBoolean(EXTRA_MESA_LIBERADA, mesaLiberada)
            putBoolean(EXTRA_LIBERAR_PENDIENTE, liberarPendiente)
            putBoolean(EXTRA_PEDIDO_ENVIADO, pedidoEnviado)
            putLong(EXTRA_PEDIDO_PARA_LLEVAR_ID, pedidoParaLlevarId)
        }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, datos)
        (requireActivity() as? MainActivity)?.cerrarComanda()
    }

    private fun irALogin() {
        if (yendoALogin) return
        yendoALogin = true
        carritoViewModel.limpiarCarrito()
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    companion object {
        /** Clave del FragmentResult que escucha SalonFragment. */
        const val REQUEST_KEY = "comanda_result"

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

        fun nuevaMesa(
            mesaId: Long,
            numeroMesa: Int,
            ubicacion: String,
            modoLectura: Boolean
        ): ComandaFragment = ComandaFragment().apply {
            arguments = Bundle().apply {
                putLong(EXTRA_MESA_ID, mesaId)
                putInt(EXTRA_MESA_NUMERO, numeroMesa)
                putString(EXTRA_MESA_UBICACION, ubicacion)
                putBoolean(EXTRA_MODO_LECTURA, modoLectura)
            }
        }

        fun nuevaParaLlevar(clienteNombre: String): ComandaFragment = ComandaFragment().apply {
            arguments = Bundle().apply {
                putBoolean(EXTRA_PARA_LLEVAR, true)
                putString(EXTRA_CLIENTE_NOMBRE, clienteNombre)
            }
        }
    }
}
