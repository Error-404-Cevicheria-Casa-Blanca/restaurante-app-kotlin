package com.lacasablanca.mesero.ui.salon

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import com.google.android.material.tabs.TabLayout
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.DialogLiberarMesaBinding
import com.lacasablanca.mesero.databinding.DialogMesaAtendidaBinding
import com.lacasablanca.mesero.databinding.DialogParaLlevarBinding
import com.lacasablanca.mesero.databinding.FragmentMesasBinding
import com.lacasablanca.mesero.databinding.ItemLeyendaBinding
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.ui.bloqueo.BloqueoActivity
import com.lacasablanca.mesero.ui.cobro.CobroFragment
import com.lacasablanca.mesero.ui.comanda.ComandaFragment
import com.lacasablanca.mesero.ui.common.aparecerConDesplazamiento
import com.lacasablanca.mesero.ui.common.colorRes
import com.lacasablanca.mesero.ui.common.etiquetaPluralRes
import com.lacasablanca.mesero.ui.common.etiquetaRes
import com.lacasablanca.mesero.ui.common.iconoRes
import com.lacasablanca.mesero.ui.login.LoginActivity
import com.lacasablanca.mesero.ui.main.MainActivity
import com.lacasablanca.mesero.ui.mesas.AccionMesa
import com.lacasablanca.mesero.ui.mesas.AccionesMesaUi
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
                MesasViewModel(
                    mesaRepository = contenedor.mesaRepository,
                    authRepository = contenedor.authRepository,
                    pedidoRepository = contenedor.pedidoRepository
                )
            }
        )[MesasViewModel::class.java]
    }

    /** PRB-93: menu y dialogos de dividir, fusionar y transferir. */
    private lateinit var accionesUi: AccionesMesaUi

    /** Avisos y Snackbars del mapa (extraidos para reducir el tamano de la clase). */
    private lateinit var avisosUi: AvisosMesasUi

    private var actualizandoTabs = false
    private var firmaTabs = ""
    private var firmaChips = ""
    private var primeraReanudacion = true
    private var liberarPendiente = false
    private var iniciado = false
    private var dialogoAbierto: AlertDialog? = null
    private var dialogoLiberarMesa: AlertDialog? = null
    private var animacionInicialEjecutada = false

    /**
     * PRB-88: la comanda y el cobro ya no son Activities. Sus resultados
     * llegan por FragmentResult; el mapa se refresca cuando corresponde.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(
            ComandaFragment.REQUEST_KEY,
            this
        ) { _, datos -> manejarResultadoComanda(datos) }

        parentFragmentManager.setFragmentResultListener(
            CobroFragment.REQUEST_KEY,
            this
        ) { _, datos ->
            if (datos.getBoolean(CobroFragment.EXTRA_COBRO_EXITOSO, false)) {
                viewModel.recargarAlVolver()
            }
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

        accionesUi = AccionesMesaUi(
            fragment = this,
            eventos = object : AccionesMesaUi.Eventos {
                override fun alElegirAccion(accion: AccionMesa, mesa: Mesa) {
                    when (accion) {
                        AccionMesa.DIVIDIR -> viewModel.prepararDivision(mesa)
                        AccionMesa.FUSIONAR -> viewModel.prepararFusion(mesa)
                        AccionMesa.TRANSFERIR -> viewModel.prepararTransferencia(mesa)
                    }
                }

                override fun alConfirmar(
                    accion: AccionMesa,
                    mesa: Mesa,
                    destino: Mesa,
                    itemIds: List<Long>
                ) {
                    when (accion) {
                        AccionMesa.DIVIDIR ->
                            viewModel.dividirMesa(mesa, itemIds, destino)

                        AccionMesa.FUSIONAR ->
                            viewModel.fusionarMesas(mesaDestino = mesa, mesaOrigen = destino)

                        AccionMesa.TRANSFERIR ->
                            viewModel.transferirMesa(mesaOrigen = mesa, mesaDestino = destino)
                    }
                }
            }
        )

        avisosUi = AvisosMesasUi(
            fragment = this,
            alAceptarSesionExpirada = ::irALogin
        )

        adapter = MesaAdapter(
            alTocar = { mesa -> alSeleccionarMesa(mesa) },
            alAccionar = { mesa -> viewModel.abrirAcciones(mesa) }
        )
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
                launch { viewModel.eventosError.collect(avisosUi::mostrarErrorPolling) }
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
        iniciado = true
        actualizarPolling()
    }

    override fun onStop() {
        iniciado = false
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
        // No se consulta lifecycle.currentState: dentro de onStart el Fragment
        // aun puede figurar en CREATED. El flag iniciado refleja onStart/onStop.
        if (iniciado && !isHidden && !liberarPendiente) {
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
        accionesUi.cerrar()
        avisosUi.cerrar()
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

    /**
     * Resultado de la comanda (FragmentResult):
     * - pedido para llevar enviado: aviso y refresco;
     * - liberacion pendiente: reintenta el PATCH con el ViewModel;
     * - cualquier otro cierre: refresca el mapa.
     */
    private fun manejarResultadoComanda(datos: Bundle) {
        val pedidoParaLlevar = datos.getLong(
            ComandaFragment.EXTRA_PEDIDO_PARA_LLEVAR_ID,
            0L
        )
        if (pedidoParaLlevar > 0L) {
            avisosUi.mostrarSnackbar(
                getString(R.string.para_llevar_pedido_enviado, pedidoParaLlevar)
            )
            viewModel.recargarAlVolver()
            return
        }
        if (datos.getBoolean(ComandaFragment.EXTRA_LIBERAR_PENDIENTE, false)) {
            val id = datos.getLong(ComandaFragment.EXTRA_MESA_ID, -1L)
            if (id > 0L) {
                liberarPendiente = true
                viewModel.liberarMesa(id)
            }
            return
        }
        // Pedido enviado o salida normal: el mapa refleja el nuevo estado.
        viewModel.recargarAlVolver()
    }

    private fun manejarEvento(evento: MesasEvento) {
        when (evento) {
            is MesasEvento.IrAComanda -> abrirComanda(evento.mesa)
            is MesasEvento.IrAComandaLectura -> abrirComanda(evento.mesa, modoLectura = true)
            is MesasEvento.OpcionesMesaAtendida -> mostrarOpcionesMesaAtendida(evento.mesa)
            is MesasEvento.MesaLiberada -> {
                liberarPendiente = false
                cerrarDialogoLiberarMesa()
                avisosUi.mostrarSnackbar(getString(R.string.mesa_liberada))
                actualizarPolling()
            }

            is MesasEvento.MostrarAcciones,
            is MesasEvento.MostrarDivision,
            is MesasEvento.MostrarDestino,
            is MesasEvento.AccionMesaCompletada -> manejarEventoAccion(evento)

            is MesasEvento.Aviso -> {
                if (evento.tipo == AvisoMesas.ERROR_LIBERAR) {
                    liberarPendiente = false
                    cerrarDialogoLiberarMesa()
                    actualizarPolling()
                }
                mostrarAviso(evento)
            }
            MesasEvento.SesionExpirada -> {
                accionesUi.cerrar()
                cerrarDialogoLiberarMesa()
                mostrarSesionExpirada()
            }

            MesasEvento.SesionCerrada -> irALogin()
        }
    }

    /** PRB-93: eventos del menu de acciones, separados para bajar complejidad. */
    private fun manejarEventoAccion(evento: MesasEvento) {
        when (evento) {
            is MesasEvento.MostrarAcciones -> accionesUi.abrirMenu(evento.mesa)
            is MesasEvento.MostrarDivision -> {
                if (evento.destinos.isEmpty()) {
                    mostrarAviso(MesasEvento.Aviso(AvisoMesas.SIN_DESTINOS, evento.mesa))
                } else {
                    accionesUi.mostrarDivision(evento.mesa, evento.lineas, evento.destinos)
                }
            }

            is MesasEvento.MostrarDestino -> {
                if (evento.destinos.isEmpty()) {
                    mostrarAviso(MesasEvento.Aviso(AvisoMesas.SIN_DESTINOS, evento.mesa))
                } else {
                    accionesUi.mostrarDestinos(
                        accion = evento.accion,
                        mesa = evento.mesa,
                        destinos = evento.destinos
                    )
                }
            }

            is MesasEvento.AccionMesaCompletada -> {
                accionesUi.cerrar()
                avisosUi.mostrarExitoAccion(evento)
            }

            else -> Unit
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
        avisosUi.cerrar()
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
        avisosUi.cerrar()
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
     * PRB-93: antes de mostrar cualquier aviso se cierran los dialogos de
     * accion y el aviso anterior para no apilar ventanas.
     */
    private fun mostrarAviso(evento: MesasEvento.Aviso) {
        accionesUi.cerrar()
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        avisosUi.mostrarAviso(evento)
    }

    private fun mostrarSesionExpirada() {
        accionesUi.cerrar()
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        avisosUi.mostrarSesionExpirada()
    }

    private fun alSeleccionarMesa(mesa: Mesa) {
        if (mesa.estado == EstadoMesa.CUENTA_SOLICITADA) {
            abrirCobro(mesa)
            return
        }
        viewModel.seleccionarMesa(mesa)
    }

    private fun abrirCobro(mesa: Mesa) {
        (requireActivity() as? MainActivity)?.abrirCobroMesa(mesa.id, mesa.numero)
    }

    private fun abrirComanda(mesa: Mesa, modoLectura: Boolean = false) {
        (requireActivity() as? MainActivity)?.abrirComanda(
            mesaId = mesa.id,
            numeroMesa = mesa.numero,
            ubicacion = mesa.ubicacion,
            modoLectura = modoLectura
        )
    }

    /**
     * PRB-94: dialogo premium para identificar el pedido para llevar. El
     * nombre viaja a [ComandaFragment], que reutiliza catalogo, carrito,
     * modificadores y notas sin asociarse a ninguna mesa.
     */
    private fun mostrarDialogoParaLlevar() {
        avisosUi.cerrar()
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
        (requireActivity() as? MainActivity)?.abrirComandaParaLlevar(nombre)
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
