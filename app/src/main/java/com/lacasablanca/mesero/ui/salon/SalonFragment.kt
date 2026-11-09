package com.lacasablanca.mesero.ui.salon

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.DialogAvisoMesaBinding
import com.lacasablanca.mesero.databinding.DialogLiberarMesaBinding
import com.lacasablanca.mesero.databinding.DialogMesaAtendidaBinding
import com.lacasablanca.mesero.databinding.DialogParaLlevarBinding
import com.lacasablanca.mesero.databinding.FragmentMesasBinding
import com.lacasablanca.mesero.databinding.ItemLeyendaBinding
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.ui.bloqueo.BloqueoActivity
import com.lacasablanca.mesero.ui.cobro.CobroActivity
import com.lacasablanca.mesero.ui.comanda.ComandaActivity
import com.lacasablanca.mesero.ui.common.aparecerConDesplazamiento
import com.lacasablanca.mesero.ui.common.colorRes
import com.lacasablanca.mesero.ui.common.etiquetaPluralRes
import com.lacasablanca.mesero.ui.common.etiquetaRes
import com.lacasablanca.mesero.ui.common.iconoRes
import com.lacasablanca.mesero.ui.login.LoginActivity
import com.lacasablanca.mesero.ui.main.MainActivity
import com.lacasablanca.mesero.ui.mesas.AvisoMesas
import com.lacasablanca.mesero.ui.mesas.ErrorMesas
import com.lacasablanca.mesero.ui.mesas.MesaAdapter
import com.lacasablanca.mesero.ui.mesas.MesasEvento
import com.lacasablanca.mesero.ui.mesas.MesasUiState
import com.lacasablanca.mesero.ui.mesas.MesasViewModel
import kotlinx.coroutines.launch

/**
 * Mapa de mesas dentro del tab Salón (PRB-88, Fase 2).
 *
 * Es la migración de MesasActivity a Fragment:
 * - Mantiene el mismo layout (barra, zonas, chips, leyenda, grilla, FAB).
 * - Comparte [MesasViewModel] con MainActivity (`activityViewModels`), así el
 *   mapa y sus filtros sobreviven al cambio de tab.
 * - Detiene el polling cuando el tab queda oculto (`onHiddenChanged`), no solo
 *   cuando la Activity pasa a segundo plano.
 * - Comanda y Cobro siguen siendo Activities de detalle.
 */
class SalonFragment : Fragment() {

    private var _binding: FragmentMesasBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MesaAdapter

    private val contenedor get() = (requireActivity().application as MeseroApp).container

    /**
     * Mismo MesasViewModel que el resto de MainActivity: se guarda en el
     * ViewModelStore de la Activity para sobrevivir al cambio de tab. Se usa
     * ViewModelProvider directo porque el proyecto no incluye fragment-ktx.
     */
    private val viewModel: MesasViewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            GenericViewModelFactory {
                MesasViewModel(contenedor.mesaRepository, contenedor.authRepository)
            }
        )[MesasViewModel::class.java]
    }

    private var actualizandoTabs = false
    private var firmaTabs = ""
    private var firmaChips = ""
    private var primeraReanudacion = true
    private var liberarPendiente = false
    private var dialogoAbierto: AlertDialog? = null
    private var dialogoLiberarMesa: AlertDialog? = null
    private var animacionInicialEjecutada = false

    /**
     * La comanda puede devolver la mesa sin liberar (el PATCH fallo y el mozo
     * eligio salir). En ese caso se reintenta aqui con [MesasViewModel.liberarMesa].
     */
    private val lanzadorComanda = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val datos = resultado.data
        // PRB-94: pedido para llevar enviado. Ninguna mesa cambio de estado;
        // solo se muestra el aviso de exito y se sincroniza el mapa.
        val pedidoParaLlevar = datos
            ?.getLongExtra(ComandaActivity.EXTRA_PEDIDO_PARA_LLEVAR_ID, 0L)
            ?: 0L
        if (pedidoParaLlevar > 0L) {
            mostrarSnackbar(getString(R.string.para_llevar_pedido_enviado, pedidoParaLlevar))
            viewModel.recargarAlVolver()
            return@registerForActivityResult
        }
        if (datos?.getBooleanExtra(ComandaActivity.EXTRA_LIBERAR_PENDIENTE, false) == true) {
            val id = datos.getLongExtra(ComandaActivity.EXTRA_MESA_ID, -1L)
            if (id > 0L) {
                liberarPendiente = true
                viewModel.liberarMesa(id)
            }
            return@registerForActivityResult
        }
        // Pedido enviado: el mapa debe reflejar el nuevo estado de la mesa.
        viewModel.recargarAlVolver()
    }

    /** La mesa amarilla abre el cobro; las demas siguen el flujo del mapa. */
    private val lanzadorCobro = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == Activity.RESULT_OK) {
            viewModel.recargarAlVolver()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMesasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MesaAdapter { mesa -> alSeleccionarMesa(mesa) }
        binding.recyclerMesas.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.columnas_mesas))
        binding.recyclerMesas.adapter = adapter

        configurarPestanas()
        pintarLeyenda()

        binding.barra.btnActualizar.setOnClickListener { viewModel.refrescar() }
        binding.barra.btnPerfil.setOnClickListener { abrirPerfil() }
        binding.barra.btnBloquear.setOnClickListener { abrirBloqueo() }
        binding.barra.btnCerrarSesion.setOnClickListener { viewModel.cerrarSesion() }
        binding.btnReintentar.setOnClickListener { viewModel.cargar() }
        binding.fabParaLlevar.setOnClickListener { mostrarDialogoParaLlevar() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::render) }
                launch { viewModel.eventos.collect(::manejarEvento) }
                launch { viewModel.eventosError.collect(::mostrarErrorPolling) }
                launch {
                    contenedor.networkMonitor.enLinea.collect { enLinea ->
                        binding.bannerOffline.isVisible = !enLinea
                    }
                }
            }
        }
    }

    /**
     * El mapa se mantiene sincronizado mientras el tab esta visible. Al cambiar
     * de tab, `onHiddenChanged` detiene el bucle; al volver, lo reanuda con una
     * primera consulta inmediata.
     */
    override fun onStart() {
        super.onStart()
        actualizarPolling()
    }

    override fun onStop() {
        viewModel.stopPolling()
        super.onStop()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            viewModel.stopPolling()
        } else {
            actualizarPolling()
        }
    }

    private fun actualizarPolling() {
        if (!isHidden && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.startPolling()
        } else {
            viewModel.stopPolling()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isHidden) return
        if (primeraReanudacion) {
            primeraReanudacion = false
            return
        }
        if (liberarPendiente) {
            // MesasViewModel.liberarMesa ya actualiza el StateFlow con la
            // respuesta del PATCH; no se lanza un GET que pueda pisarla.
            liberarPendiente = false
            return
        }
        // Al volver de la comanda (o de segundo plano) el mapa se sincroniza
        // con el repositorio: la mesa cerrada sin pedido debe verse en verde.
        viewModel.recargarAlVolver()
    }

    override fun onPause() {
        super.onPause()
        // El flag solo vale para la reanudacion inmediata; no debe saltarse
        // un refresco posterior si el resultado llego tarde.
        liberarPendiente = false
    }

    override fun onDestroyView() {
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        dialogoLiberarMesa?.dismiss()
        dialogoLiberarMesa = null
        // La vista puede recrearse (rotacion o back stack) con la misma
        // instancia del Fragment: se invalidan las firmas para volver a pintar
        // tabs/chips en el nuevo TabLayout.
        firmaTabs = ""
        firmaChips = ""
        animacionInicialEjecutada = false
        _binding = null
        super.onDestroyView()
    }

    private fun configurarPestanas() {
        binding.tabsZonas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!actualizandoTabs) viewModel.filtrarZona(tab.tag as? String)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun render(estado: MesasUiState) {
        binding.barra.textoSaludo.isVisible = estado.nombreUsuario.isNotBlank()
        binding.barra.textoSaludo.text = getString(R.string.mesas_saludo, estado.nombreUsuario)

        binding.progresoCarga.isVisible = estado.cargando
        binding.barra.btnActualizar.isEnabled = !estado.cargando

        val hayError = estado.error != null && !estado.cargando
        val vacio = estado.sinMesas || estado.sinResultados
        val mostrarCuadricula = !estado.cargando && !hayError && !vacio
        binding.grupoError.isVisible = hayError
        binding.grupoVacio.isVisible = !hayError && vacio
        binding.recyclerMesas.isVisible = mostrarCuadricula

        estado.error?.let { binding.textoErrorMesas.text = getString(it.mensajeRes()) }

        renderTabs(estado.zonas, estado.zonaSeleccionada)
        renderChips(estado)
        adapter.submitList(estado.mesasVisibles)

        // Fade + slide-up solo la primera vez que la cuadricula tiene datos.
        if (!animacionInicialEjecutada && mostrarCuadricula &&
            estado.mesasVisibles.isNotEmpty()
        ) {
            animacionInicialEjecutada = true
            binding.recyclerMesas.aparecerConDesplazamiento()
        }
    }

    private fun renderTabs(zonas: List<String>, seleccionada: String?) {
        val firma = zonas.joinToString(separator = "|") + "#" + (seleccionada ?: "")
        if (firma == firmaTabs) return
        firmaTabs = firma

        actualizandoTabs = true
        binding.tabsZonas.removeAllTabs()
        binding.tabsZonas.addTab(
            binding.tabsZonas.newTab().setText(R.string.zona_todas).setTag(null)
        )
        zonas.forEach { zona ->
            binding.tabsZonas.addTab(binding.tabsZonas.newTab().setText(zona).setTag(zona))
        }
        val indice = zonas.indexOf(seleccionada).let { if (it < 0) 0 else it + 1 }
        binding.tabsZonas.getTabAt(indice)?.select()
        actualizandoTabs = false
    }

    private fun renderChips(estado: MesasUiState) {
        val contadores = estado.contadores
        val firma = EstadoMesa.filtrables.joinToString(separator = ",") {
            "${it.name}=${contadores[it] ?: 0}"
        } + "#" + (estado.estadoSeleccionado?.name ?: "TODAS")
        if (firma == firmaChips) return
        firmaChips = firma

        binding.chipsEstados.removeAllViews()
        val chipTodas = crearChip(
            texto = getString(R.string.zona_todas),
            etiquetaEstado = null,
            icono = null
        )
        binding.chipsEstados.addView(chipTodas)

        val chipsPorEstado = mutableMapOf<EstadoMesa, Chip>()
        EstadoMesa.filtrables.forEach { estadoMesa ->
            val cantidad = contadores[estadoMesa] ?: 0
            val chip = crearChip(
                texto = getString(
                    R.string.chip_estado_con_contador,
                    getString(estadoMesa.etiquetaPluralRes()),
                    cantidad
                ),
                etiquetaEstado = estadoMesa,
                icono = estadoMesa.iconoRes()
            )
            chipsPorEstado[estadoMesa] = chip
            binding.chipsEstados.addView(chip)
        }

        // Se marca despues de agregarlos: el grupo aplica singleSelection al anadir.
        val chipSeleccionado = estado.estadoSeleccionado?.let { chipsPorEstado[it] } ?: chipTodas
        chipSeleccionado.isChecked = true
    }

    private fun crearChip(
        texto: String,
        etiquetaEstado: EstadoMesa?,
        icono: Int?
    ): Chip {
        val chip = Chip(requireContext())
        chip.text = texto
        chip.isCheckable = true
        chip.isClickable = true
        chip.isCheckedIconVisible = false
        chip.isChecked = false
        // Colores exactos de Stitch: activo teal #003D48 / inactivo blanco (pill).
        chip.chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(requireContext(), R.color.chip_filtro_activo),
                ContextCompat.getColor(requireContext(), R.color.chip_filtro_inactivo)
            )
        )
        chip.setTextColor(
            ContextCompat.getColorStateList(requireContext(), R.color.selector_texto_chip)
        )
        chip.chipIconTint =
            ContextCompat.getColorStateList(requireContext(), R.color.selector_icono_chip)
        chip.shapeAppearanceModel = chip.shapeAppearanceModel
            .toBuilder()
            .setAllCornerSizes(resources.getDimension(R.dimen.radio_pill))
            .build()
        chip.chipMinHeight = resources.getDimension(R.dimen.alto_chip_filtro)
        chip.contentDescription = buildString {
            append(texto)
            if (etiquetaEstado != null) {
                append(", ")
                append(getString(etiquetaEstado.etiquetaRes()))
            }
        }
        if (icono != null) {
            chip.chipIcon = ContextCompat.getDrawable(requireContext(), icono)
            chip.isChipIconVisible = true
        }
        chip.setOnClickListener { viewModel.filtrarEstado(etiquetaEstado) }
        return chip
    }

    private fun pintarLeyenda() {
        binding.leyendaEstados.removeAllViews()
        EstadoMesa.filtrables.forEach { estado ->
            val item = ItemLeyendaBinding.inflate(
                layoutInflater,
                binding.leyendaEstados,
                false
            )
            item.puntoEstado.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), estado.colorRes())
            )
            item.textoLeyenda.text = getString(estado.etiquetaRes())
            val margen = resources.getDimensionPixelSize(R.dimen.espacio_l)
            item.root.layoutParams =
                (item.root.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                    marginEnd = margen
                }
            binding.leyendaEstados.addView(item.root)
        }
    }

    private fun manejarEvento(evento: MesasEvento) {
        when (evento) {
            is MesasEvento.IrAComanda -> abrirComanda(evento.mesa)
            is MesasEvento.IrAComandaLectura -> abrirComanda(evento.mesa, modoLectura = true)
            is MesasEvento.OpcionesMesaAtendida -> mostrarOpcionesMesaAtendida(evento.mesa)
            is MesasEvento.MesaLiberada -> {
                cerrarDialogoLiberarMesa()
                mostrarSnackbar(getString(R.string.mesa_liberada))
            }

            is MesasEvento.Aviso -> mostrarAviso(evento)
            MesasEvento.SesionExpirada -> {
                cerrarDialogoLiberarMesa()
                mostrarDialogoSesionExpirada()
            }

            MesasEvento.SesionCerrada -> irALogin()
        }
    }

    /**
     * La barra del mapa conserva el boton de perfil, pero ahora selecciona el
     * tab Perfil del contenedor principal en vez de abrir una Activity aparte.
     */
    private fun abrirPerfil() {
        (requireActivity() as? MainActivity)?.seleccionarTab(R.id.navPerfil)
    }

    /**
     * Bloqueo rapido (PRB-81): la sesion no se cierra, solo se cubre el mapa
     * con un PIN. Al desbloquear, MainActivity vuelve a primer plano.
     */
    private fun abrirBloqueo() {
        startActivity(Intent(requireContext(), BloqueoActivity::class.java))
    }

    /**
     * Mesa ATENDIDA (azul): esta abierta pero sin pedido enviado a cocina.
     * Se ofrece retomar la comanda editable o liberar la mesa con confirmacion
     * (PRB-83), evitando dejar mesas bloqueadas.
     */
    private fun mostrarOpcionesMesaAtendida(mesa: Mesa) {
        dialogoAbierto?.dismiss()
        dialogoAbierto = null

        val contenido = DialogMesaAtendidaBinding.inflate(layoutInflater)
        val dialogo = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_LaCasaBlanca_Dialogo_LiberarMesa
        )
            .setView(contenido.root)
            .setCancelable(true)
            .create()

        contenido.btnRetomarComanda.setOnClickListener {
            dialogo.dismiss()
            abrirComanda(mesa)
        }
        contenido.btnLiberarMesaAtendida.setOnClickListener {
            dialogo.dismiss()
            mostrarConfirmacionLiberar(mesa)
        }

        dialogo.setCanceledOnTouchOutside(true)
        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    /** Confirma la liberacion avisando que se perdera el carrito de la mesa. */
    private fun mostrarConfirmacionLiberar(mesa: Mesa) {
        dialogoAbierto?.dismiss()
        dialogoAbierto = null

        val contenido = DialogLiberarMesaBinding.inflate(layoutInflater)
        contenido.mensajeDialogo.text = getString(R.string.dialogo_atendida_liberar_mensaje)

        val dialogo = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_LaCasaBlanca_Dialogo_LiberarMesa
        )
            .setView(contenido.root)
            .setCancelable(true)
            .create()

        contenido.btnCancelar.setOnClickListener { dialogo.dismiss() }
        contenido.btnLiberar.setOnClickListener {
            dialogo.setCancelable(false)
            dialogo.setCanceledOnTouchOutside(false)
            contenido.grupoBotones.isVisible = false
            contenido.progresoLiberar.isVisible = true
            contenido.textoProgreso.isVisible = true
            viewModel.liberarMesa(mesa.id)
        }

        dialogo.setCanceledOnTouchOutside(true)
        dialogo.setOnDismissListener {
            if (dialogoLiberarMesa === dialogo) dialogoLiberarMesa = null
        }
        dialogo.show()
        dialogoLiberarMesa = dialogo
    }

    private fun cerrarDialogoLiberarMesa() {
        dialogoLiberarMesa?.dismiss()
        dialogoLiberarMesa = null
    }

    /**
     * Avisos del mapa: los estados de mesa y los errores se explican con un
     * MaterialAlertDialog; la confirmacion de refresco usa un Snackbar de marca.
     */
    private fun mostrarAviso(evento: MesasEvento.Aviso) {
        val mesa = evento.mesa
        when (evento.tipo) {
            AvisoMesas.ACTUALIZADAS -> mostrarSnackbar(getString(R.string.mesas_actualizadas))

            AvisoMesas.MESA_NO_LIBRE -> if (mesa == null) {
                mostrarDialogo(
                    titulo = getString(R.string.mesas_titulo),
                    mensaje = getString(R.string.error_generico)
                )
            } else {
                mostrarDialogo(
                    titulo = getString(R.string.dialogo_mesa_titulo, mesa.numero),
                    mensaje = getString(mesa.mensajeEstadoRes(), mesa.numero),
                    iconoRes = mesa.estado.iconoRes()
                )
            }

            AvisoMesas.MESA_TOMADA -> mostrarDialogo(
                titulo = getString(R.string.mesa_tomada_titulo),
                mensaje = mesa?.let { getString(R.string.mesa_tomada, it.numero) }
                    ?: getString(R.string.error_generico),
                iconoRes = R.drawable.ic_error_outline
            )

            AvisoMesas.ERROR_ABRIR -> mostrarDialogo(
                titulo = getString(R.string.error_abrir_mesa_titulo),
                mensaje = getString(R.string.error_abrir_mesa)
            )

            AvisoMesas.ERROR_LIBERAR -> {
                cerrarDialogoLiberarMesa()
                mostrarDialogo(
                    titulo = getString(R.string.error_liberar_mesa_titulo),
                    mensaje = mesa?.let { getString(R.string.error_liberar_mesa, it.numero) }
                        ?: getString(R.string.error_generico)
                )
            }

            AvisoMesas.ERROR_RED -> mostrarDialogo(
                titulo = getString(R.string.error_red_titulo),
                mensaje = getString(R.string.error_red)
            )
        }
    }

    @StringRes
    private fun Mesa.mensajeEstadoRes(): Int = when (estado) {
        EstadoMesa.OCUPADA -> R.string.mesa_dialogo_ocupada
        EstadoMesa.ATENDIDA -> R.string.mesa_dialogo_atendida
        EstadoMesa.CUENTA_SOLICITADA -> R.string.mesa_dialogo_cuenta
        else -> R.string.mesa_dialogo_no_libre
    }

    private fun mostrarDialogo(
        titulo: CharSequence,
        mensaje: CharSequence,
        cancelable: Boolean = true,
        @DrawableRes iconoRes: Int = R.drawable.ic_error_outline,
        alAceptar: (() -> Unit)? = null
    ) {
        dialogoAbierto?.dismiss()
        val contenido = DialogAvisoMesaBinding.inflate(layoutInflater)
        contenido.iconoAviso.setImageResource(iconoRes)
        contenido.iconoAviso.contentDescription = titulo
        contenido.tituloAviso.text = titulo
        contenido.mensajeAviso.text = mensaje

        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setView(contenido.root)
            .setCancelable(cancelable)
            .create()

        contenido.btnEntendido.setOnClickListener {
            dialogo.dismiss()
            alAceptar?.invoke()
        }
        dialogo.setCanceledOnTouchOutside(cancelable)
        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()
        dialogoAbierto = dialogo
    }

    private fun mostrarDialogoSesionExpirada() {
        mostrarDialogo(
            titulo = getString(R.string.sesion_expirada_titulo),
            mensaje = getString(R.string.sesion_expirada),
            cancelable = false,
            iconoRes = R.drawable.ic_lock,
            alAceptar = ::irALogin
        )
    }

    /** Fallo de red del refresco automatico: aviso discreto, sin tapar el mapa. */
    private fun mostrarErrorPolling(codigo: String) {
        val texto = when (codigo) {
            MesasViewModel.ERROR_RED -> getString(R.string.mesas_polling_sin_conexion)
            else -> getString(R.string.mesas_polling_error)
        }
        mostrarSnackbar(texto)
    }

    private fun mostrarSnackbar(texto: String) {
        val snackbar = Snackbar.make(binding.root, texto, Snackbar.LENGTH_SHORT)
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        snackbar.show()
        snackbar.view.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_snackbar)
    }

    private fun alSeleccionarMesa(mesa: Mesa) {
        if (mesa.estado == EstadoMesa.CUENTA_SOLICITADA) {
            abrirCobro(mesa)
            return
        }
        viewModel.seleccionarMesa(mesa)
    }

    private fun abrirCobro(mesa: Mesa) {
        val intent = Intent(requireContext(), CobroActivity::class.java).apply {
            putExtra(CobroActivity.EXTRA_MESA_ID, mesa.id)
            putExtra(CobroActivity.EXTRA_MESA_NUMERO, mesa.numero)
        }
        lanzadorCobro.launch(intent)
    }

    private fun abrirComanda(mesa: Mesa, modoLectura: Boolean = false) {
        val intent = Intent(requireContext(), ComandaActivity::class.java).apply {
            putExtra(ComandaActivity.EXTRA_MESA_ID, mesa.id)
            putExtra(ComandaActivity.EXTRA_MESA_NUMERO, mesa.numero)
            putExtra(ComandaActivity.EXTRA_MESA_UBICACION, mesa.ubicacion)
            putExtra(ComandaActivity.EXTRA_MODO_LECTURA, modoLectura)
        }
        lanzadorComanda.launch(intent)
    }

    /**
     * PRB-94: dialogo premium para identificar el pedido para llevar. El
     * nombre viaja a [ComandaActivity], que reutiliza catalogo, carrito,
     * modificadores y notas sin asociarse a ninguna mesa.
     */
    private fun mostrarDialogoParaLlevar() {
        dialogoAbierto?.dismiss()
        dialogoAbierto = null

        val contenido = DialogParaLlevarBinding.inflate(layoutInflater)
        val dialogo = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_LaCasaBlanca_Dialogo_LiberarMesa
        )
            .setView(contenido.root)
            .setCancelable(true)
            .create()

        contenido.btnCancelarParaLlevar.setOnClickListener { dialogo.dismiss() }
        contenido.btnContinuarParaLlevar.setOnClickListener {
            val nombre = contenido.textoNombreCliente.text?.toString().orEmpty().trim()
            if (nombre.isEmpty()) {
                contenido.campoNombreCliente.error = getString(R.string.para_llevar_campo_error)
                return@setOnClickListener
            }
            contenido.campoNombreCliente.error = null
            dialogo.dismiss()
            abrirComandaParaLlevar(nombre)
        }

        dialogo.setCanceledOnTouchOutside(true)
        dialogo.setOnDismissListener { if (dialogoAbierto === dialogo) dialogoAbierto = null }
        dialogo.show()

        // Ancho premium tambien en tablet y teclado listo para escribir.
        val anchoPantalla = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        val anchoMaximo = (480 * resources.displayMetrics.density).toInt()
        dialogo.window?.setLayout(
            minOf(anchoPantalla, anchoMaximo),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        contenido.textoNombreCliente.requestFocus()
        dialogoAbierto = dialogo
    }

    private fun abrirComandaParaLlevar(nombre: String) {
        val intent = Intent(requireContext(), ComandaActivity::class.java).apply {
            putExtra(ComandaActivity.EXTRA_PARA_LLEVAR, true)
            putExtra(ComandaActivity.EXTRA_CLIENTE_NOMBRE, nombre)
        }
        lanzadorComanda.launch(intent)
    }

    private fun irALogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    @StringRes
    private fun ErrorMesas.mensajeRes(): Int = when (this) {
        ErrorMesas.RED -> R.string.error_red
        ErrorMesas.SESION_EXPIRADA -> R.string.sesion_expirada
        ErrorMesas.SERVIDOR -> R.string.mesas_error
        ErrorMesas.DESCONOCIDO -> R.string.mesas_error
    }
}
