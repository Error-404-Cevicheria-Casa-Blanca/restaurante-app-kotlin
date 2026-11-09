package com.lacasablanca.mesero.ui.mesas

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
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
import com.lacasablanca.mesero.databinding.ActivityMesasBinding
import com.lacasablanca.mesero.databinding.DialogAvisoMesaBinding
import com.lacasablanca.mesero.databinding.DialogLiberarMesaBinding
import com.lacasablanca.mesero.databinding.DialogMesaAtendidaBinding
import com.lacasablanca.mesero.databinding.DialogParaLlevarBinding
import com.lacasablanca.mesero.databinding.ItemLeyendaBinding
import com.lacasablanca.mesero.domain.model.EstadoMesa
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.ui.bloqueo.BloqueoActivity
import com.lacasablanca.mesero.ui.cobro.CobroActivity
import com.lacasablanca.mesero.ui.comanda.ComandaActivity
import com.lacasablanca.mesero.ui.perfil.PerfilActivity
import com.lacasablanca.mesero.ui.common.aparecerConDesplazamiento
import com.lacasablanca.mesero.ui.common.colorRes
import com.lacasablanca.mesero.ui.common.etiquetaPluralRes
import com.lacasablanca.mesero.ui.common.etiquetaRes
import com.lacasablanca.mesero.ui.common.iconoRes
import com.lacasablanca.mesero.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Mapa de mesas: toolbar, pestanas de zona, chips de estado con contadores,
 * cuadricula (2 columnas en telefono, 4 en tablet) y estados cargando/vacio/error.
 */
class MesasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMesasBinding
    private lateinit var adapter: MesaAdapter

    private val contenedor get() = (application as MeseroApp).container

    private val viewModel: MesasViewModel by viewModels {
        GenericViewModelFactory {
            MesasViewModel(contenedor.mesaRepository, contenedor.authRepository)
        }
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
        if (resultado.resultCode != RESULT_OK) return@registerForActivityResult
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
        if (resultado.resultCode == RESULT_OK) {
            viewModel.recargarAlVolver()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMesasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MesaAdapter { mesa -> alSeleccionarMesa(mesa) }
        binding.recyclerMesas.layoutManager =
            GridLayoutManager(this, resources.getInteger(R.integer.columnas_mesas))
        binding.recyclerMesas.adapter = adapter

        configurarPestanas()
        pintarLeyenda()

        binding.barra.btnActualizar.setOnClickListener { viewModel.refrescar() }
        binding.barra.btnPerfil.setOnClickListener { abrirPerfil() }
        binding.barra.btnBloquear.setOnClickListener { abrirBloqueo() }
        binding.barra.btnCerrarSesion.setOnClickListener { viewModel.cerrarSesion() }
        binding.btnReintentar.setOnClickListener { viewModel.cargar() }
        binding.fabParaLlevar.setOnClickListener { mostrarDialogoParaLlevar() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
     * El mapa se mantiene sincronizado mientras la pantalla esta visible:
     * el bucle se detiene en onStop para no consumir red en segundo plano.
     */
    override fun onStart() {
        super.onStart()
        viewModel.startPolling()
    }

    override fun onStop() {
        viewModel.stopPolling()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
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
        // con el repositorio: la mesa cerrada sin pedido debe verse en verde (LIBRE).
        viewModel.recargarAlVolver()
    }

    override fun onPause() {
        super.onPause()
        // El flag solo vale para la reanudacion inmediata; no debe saltarse
        // un refresco posterior si el resultado llego tarde.
        liberarPendiente = false
    }

    override fun onDestroy() {
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        dialogoLiberarMesa?.dismiss()
        dialogoLiberarMesa = null
        super.onDestroy()
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
        val firma = EstadoMesa.filtrables.joinToString(separator = ",") { "${it.name}=${contadores[it] ?: 0}" } +
            "#" + (estado.estadoSeleccionado?.name ?: "TODAS")
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
        val chip = Chip(this)
        chip.text = texto
        chip.isCheckable = true
        chip.isClickable = true
        chip.isCheckedIconVisible = false
        chip.isChecked = false
        // Colores exactos de Stitch: activo teal #003D48 / inactivo blanco (pill).
        chip.chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(this, R.color.chip_filtro_activo),
                ContextCompat.getColor(this, R.color.chip_filtro_inactivo)
            )
        )
        chip.setTextColor(
            ContextCompat.getColorStateList(this, R.color.selector_texto_chip)
        )
        chip.chipIconTint =
            ContextCompat.getColorStateList(this, R.color.selector_icono_chip)
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
            chip.chipIcon = ContextCompat.getDrawable(this, icono)
            chip.isChipIconVisible = true
        }
        chip.setOnClickListener { viewModel.filtrarEstado(etiquetaEstado) }
        return chip
    }

    private fun pintarLeyenda() {
        binding.leyendaEstados.removeAllViews()
        EstadoMesa.filtrables.forEach { estado ->
            val item = ItemLeyendaBinding.inflate(layoutInflater, binding.leyendaEstados, false)
            item.puntoEstado.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, estado.colorRes()))
            item.textoLeyenda.text = getString(estado.etiquetaRes())
            val margen = resources.getDimensionPixelSize(R.dimen.espacio_l)
            item.root.layoutParams = (item.root.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
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
     * Perfil del mesero (PRB-83): pantalla completa que resuelve el nombre y
     * el rol desde el SessionManager y concentra el cierre de sesion.
     */
    private fun abrirPerfil() {
        startActivity(Intent(this, PerfilActivity::class.java))
    }

    /**
     * Bloqueo rapido (PRB-81): la sesion no se cierra, solo se cubre el mapa
     * con un PIN. Al desbloquear, MesasActivity vuelve a primer plano.
     */
    private fun abrirBloqueo() {
        startActivity(Intent(this, BloqueoActivity::class.java))
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
            this,
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
            this,
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
        dialogo.setOnDismissListener { if (dialogoLiberarMesa === dialogo) dialogoLiberarMesa = null }
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

        val dialogo = MaterialAlertDialogBuilder(this)
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
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
        snackbar.show()
        snackbar.view.background = ContextCompat.getDrawable(this, R.drawable.bg_snackbar)
    }

    private fun alSeleccionarMesa(mesa: Mesa) {
        if (mesa.estado == EstadoMesa.CUENTA_SOLICITADA) {
            abrirCobro(mesa)
            return
        }
        viewModel.seleccionarMesa(mesa)
    }

    private fun abrirCobro(mesa: Mesa) {
        val intent = Intent(this, CobroActivity::class.java).apply {
            putExtra(CobroActivity.EXTRA_MESA_ID, mesa.id)
            putExtra(CobroActivity.EXTRA_MESA_NUMERO, mesa.numero)
        }
        lanzadorCobro.launch(intent)
    }

    private fun abrirComanda(mesa: Mesa, modoLectura: Boolean = false) {
        val intent = Intent(this, ComandaActivity::class.java).apply {
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
            this,
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
        val intent = Intent(this, ComandaActivity::class.java).apply {
            putExtra(ComandaActivity.EXTRA_PARA_LLEVAR, true)
            putExtra(ComandaActivity.EXTRA_CLIENTE_NOMBRE, nombre)
        }
        lanzadorComanda.launch(intent)
    }

    private fun irALogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    @StringRes
    private fun ErrorMesas.mensajeRes(): Int = when (this) {
        ErrorMesas.RED -> R.string.error_red
        ErrorMesas.SESION_EXPIRADA -> R.string.sesion_expirada
        ErrorMesas.SERVIDOR -> R.string.mesas_error
        ErrorMesas.DESCONOCIDO -> R.string.mesas_error
    }
}
