package com.lacasablanca.mesero.ui.cobro

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ActivityCobroBinding
import com.lacasablanca.mesero.databinding.DialogAplicarDescuentoBinding
import com.lacasablanca.mesero.databinding.DialogCobroExitosoBinding
import com.lacasablanca.mesero.databinding.DialogDividirCuentaBinding
import com.lacasablanca.mesero.databinding.DialogErrorPremiumBinding
import com.lacasablanca.mesero.databinding.DialogSalirCobroBinding
import com.lacasablanca.mesero.domain.model.MetodoPago
import com.lacasablanca.mesero.domain.model.TipoDescuento
import com.lacasablanca.mesero.ui.bloqueo.BloqueoActivity
import com.lacasablanca.mesero.ui.common.formatearMoneda
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Cobro en tablet y telefono (PRB-56). La Activity solo dibuja el estado
 * del ViewModel y reenvia las acciones del formulario.
 */
class CobroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCobroBinding
    private lateinit var adaptadorCuenta: CuentaLineaAdapter
    private lateinit var adaptadorPagos: PagoAdapter
    private lateinit var adaptadorCuotas: CuotaAdapter

    private val contenedor get() = (application as MeseroApp).container

    private val mesaId by lazy { intent.getLongExtra(EXTRA_MESA_ID, -1L) }
    private val numeroMesa by lazy { intent.getIntExtra(EXTRA_MESA_NUMERO, 0) }

    private val viewModel: CobroViewModel by viewModels {
        CobroViewModelFactory(mesaId, contenedor.cobroRepository)
    }

    private var actualizandoChips = false
    private var actualizandoDivision = false
    private var ultimoErrorMostrado: String? = null
    private var dialogoSalir: AlertDialog? = null
    private var dialogoExito: AlertDialog? = null
    private var dialogoError: AlertDialog? = null
    private var dialogoDivision: AlertDialog? = null
    private var dialogoDescuento: AlertDialog? = null
    private var dialogoExitoMostrado = false
    private var terminando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCobroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (mesaId <= 0L || numeroMesa <= 0) {
            finish()
            return
        }

        configurarInsets()
        configurarBarra()
        prepararListas()
        prepararAcciones()

        binding.barraCobro.title = getString(R.string.cobro_titulo, numeroMesa)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::render)
            }
        }
    }

    override fun onDestroy() {
        dialogoSalir?.dismiss()
        dialogoSalir = null
        dialogoExito?.dismiss()
        dialogoExito = null
        dialogoError?.dismiss()
        dialogoError = null
        dialogoDivision?.dismiss()
        dialogoDivision = null
        dialogoDescuento?.dismiss()
        dialogoDescuento = null
        super.onDestroy()
    }

    private fun configurarInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            vista.updatePadding(
                top = barras.top,
                bottom = maxOf(barras.bottom, teclado.bottom)
            )
            insets
        }
    }

    private fun configurarBarra() {
        binding.barraCobro.setNavigationOnClickListener { intentarSalir() }
        binding.barraCobro.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.accionBloquear -> {
                    abrirBloqueo()
                    true
                }

                else -> false
            }
        }
        onBackPressedDispatcher.addCallback(this) { intentarSalir() }
    }

    /**
     * Bloqueo rapido (PRB-81) desde el cobro.
     *
     * CobroActivity solo pasa a STOPPED: su ViewModelStore conserva la misma
     * instancia de [CobroViewModel] con la cuenta, la propina y los pagos ya
     * agregados. Al desbloquear se reanuda esta Activity y repeatOnLifecycle
     * vuelve a recolectar el estado sin llamar a `cargarCuenta()` de nuevo.
     */
    private fun abrirBloqueo() {
        startActivity(Intent(this, BloqueoActivity::class.java))
    }

    private fun prepararListas() {
        adaptadorCuenta = CuentaLineaAdapter()
        binding.recyclerCuenta.layoutManager = LinearLayoutManager(this)
        binding.recyclerCuenta.adapter = adaptadorCuenta

        adaptadorPagos = PagoAdapter(viewModel::eliminarPago)
        binding.recyclerPagos.layoutManager = LinearLayoutManager(this)
        binding.recyclerPagos.adapter = adaptadorPagos

        adaptadorCuotas = CuotaAdapter()
        binding.recyclerCuotas.layoutManager = LinearLayoutManager(this)
        binding.recyclerCuotas.adapter = adaptadorCuotas
    }

    private fun prepararAcciones() {
        binding.btnAgregarPago.setOnClickListener { viewModel.agregarPago() }
        binding.btnConfirmarCobro.setOnClickListener { viewModel.confirmarCobro() }
        binding.btnReintentarCobro.setOnClickListener { viewModel.cargarCuenta() }
        binding.btnAplicarDescuento.setOnClickListener { mostrarDialogoDescuento() }

        binding.grupoMediosPago.setOnCheckedStateChangeListener { _, ids ->
            if (actualizandoChips) return@setOnCheckedStateChangeListener
            ids.firstOrNull()?.let { id ->
                metodoDesdeChip(id)?.let(viewModel::seleccionarMetodo)
            }
        }

        binding.textoMontoPago.doAfterTextChanged { editable ->
            viewModel.actualizarMontoPago(editable?.toString().orEmpty())
        }
        binding.textoReferencia.doAfterTextChanged { editable ->
            viewModel.actualizarReferencia(editable?.toString().orEmpty())
        }
        binding.textoPropina.doAfterTextChanged { editable ->
            viewModel.actualizarPropina(editable?.toString().orEmpty())
        }

        binding.switchDividirCuenta.setOnCheckedChangeListener { _, activo ->
            if (actualizandoDivision) return@setOnCheckedChangeListener
            if (activo) mostrarDialogoDivision() else viewModel.desactivarDivision()
        }
    }

    private fun metodoDesdeChip(id: Int): MetodoPago? = when (id) {
        R.id.chipEfectivo -> MetodoPago.EFECTIVO
        R.id.chipYape -> MetodoPago.YAPE
        R.id.chipPlin -> MetodoPago.PLIN
        R.id.chipTarjeta -> MetodoPago.TARJETA
        else -> null
    }

    private fun render(estado: CobroUiState) {
        binding.progresoCobro.isVisible = estado.loading

        val hayCuenta = estado.cuenta != null
        binding.contenidoCobro.isVisible = hayCuenta
        binding.barraInferior.isVisible = hayCuenta
        binding.grupoErrorCobro.isVisible = !hayCuenta && estado.error != null

        if (!hayCuenta) {
            estado.error?.let { binding.textoErrorCobro.text = mensajeError(it) }
            return
        }

        renderCuenta(estado)
        renderFormulario(estado)
        renderPagos(estado)
        renderDivision(estado)
        renderBarra(estado)
        mostrarErrorDeAccion(estado)
        mostrarExito(estado)
    }

    private fun renderCuenta(estado: CobroUiState) {
        val cuenta = estado.cuenta ?: return
        val items = cuenta.items.orEmpty()
        adaptadorCuenta.submitList(items)
        binding.textoCuentaVacia.isVisible = items.isEmpty()
        binding.textoSubtotal.text = formatearMoneda(estado.subtotal)
        binding.textoIgv.text = formatearMoneda(estado.igv)
        binding.textoTotal.text = formatearMoneda(estado.total)
        binding.textoPropinaSugerida.text = getString(
            R.string.cobro_propina_sugerida,
            formatearMoneda(cuenta.propinaSugerida ?: BigDecimal.ZERO)
        )
        renderDescuento(estado)
    }

    private fun renderDescuento(estado: CobroUiState) {
        val descuento = estado.descuento
        binding.filaDescuento.isVisible = descuento != null
        binding.btnAplicarDescuento.text = getString(
            if (descuento == null) {
                R.string.cobro_aplicar_descuento
            } else {
                R.string.cobro_cambiar_descuento
            }
        )
        binding.btnAplicarDescuento.isEnabled = !estado.loading
        if (descuento == null) return
        val etiqueta = when (descuento.tipo) {
            TipoDescuento.PORCENTAJE -> getString(
                R.string.cobro_descuento_porcentaje,
                descuento.valor.stripTrailingZeros().toPlainString()
            )

            TipoDescuento.MONTO_FIJO -> getString(R.string.cobro_descuento_monto_fijo)
        }
        binding.textoDescuento.text = getString(
            R.string.cobro_descuento_linea,
            etiqueta,
            formatearMoneda(estado.montoDescuento)
        )
    }

    private fun renderFormulario(estado: CobroUiState) {
        renderChips(estado.metodoSeleccionado)
        val esEfectivo = estado.metodoSeleccionado == MetodoPago.EFECTIVO
        binding.campoMontoPago.isVisible = true
        binding.campoMontoPago.setHint(
            if (esEfectivo) R.string.cobro_monto_recibido else R.string.cobro_monto_pago
        )
        binding.campoReferencia.isVisible = !esEfectivo
        binding.textoVuelto.isVisible = esEfectivo
        binding.textoMontoACobrar.isVisible = !esEfectivo
        binding.textoMontoACobrar.text = getString(
            R.string.cobro_monto_a_cobrar,
            formatearMoneda(estado.saldoPendiente)
        )
        sincronizarCampo(binding.textoMontoPago, estado.montoPago)
        sincronizarCampo(binding.textoReferencia, estado.referencia)
        sincronizarCampo(binding.textoPropina, estado.propina.aTextoEditable())
        binding.textoVuelto.text = getString(
            R.string.cobro_vuelto,
            formatearMoneda(estado.vuelto)
        )
        binding.btnAgregarPago.isEnabled =
            estado.saldoPendiente > BigDecimal.ZERO && !estado.loading
    }

    private fun renderChips(metodo: MetodoPago) {
        actualizandoChips = true
        when (metodo) {
            MetodoPago.EFECTIVO -> binding.chipEfectivo.isChecked = true
            MetodoPago.YAPE -> binding.chipYape.isChecked = true
            MetodoPago.PLIN -> binding.chipPlin.isChecked = true
            MetodoPago.TARJETA -> binding.chipTarjeta.isChecked = true
            MetodoPago.TRANSFERENCIA -> Unit
        }
        actualizandoChips = false
    }

    private fun renderPagos(estado: CobroUiState) {
        adaptadorPagos.submitList(estado.pagos)
        binding.textoPagado.text = getString(
            R.string.cobro_pagado,
            formatearMoneda(estado.totalPagado)
        )
        binding.textoSaldoPendiente.text = getString(
            R.string.cobro_saldo_pendiente,
            formatearMoneda(estado.saldoPendiente)
        )
        binding.textoSinPagos.isVisible = estado.pagos.isEmpty()
        binding.recyclerPagos.isVisible = estado.pagos.isNotEmpty()
    }

    private fun renderDivision(estado: CobroUiState) {
        actualizandoDivision = true
        binding.switchDividirCuenta.isChecked = estado.dividirCuenta
        actualizandoDivision = false
        binding.textoResumenDivision.text = if (estado.dividirCuenta) {
            getString(
                R.string.cobro_dividir_resumen,
                estado.numeroComensales,
                formatearMoneda(estado.totalConPropina)
            )
        } else {
            getString(R.string.cobro_dividir_descripcion)
        }
        binding.recyclerCuotas.isVisible = estado.dividirCuenta && estado.cuotas.isNotEmpty()
        adaptadorCuotas.submitList(estado.cuotas)
    }

    private fun renderBarra(estado: CobroUiState) {
        binding.textoTotalAPagar.text = getString(
            R.string.cobro_total_a_pagar,
            formatearMoneda(estado.totalConPropina)
        )
        binding.btnConfirmarCobro.isEnabled = estado.confirmarHabilitado && !estado.loading
    }

    private fun sincronizarCampo(campo: TextInputEditText, valor: String) {
        if (campo.text?.toString() == valor) return
        if (campo.hasFocus() && valor.isNotEmpty()) return
        campo.setText(valor)
        campo.setSelection(valor.length)
    }

    private fun mostrarErrorDeAccion(estado: CobroUiState) {
        val error = estado.error ?: run {
            ultimoErrorMostrado = null
            return
        }
        if (error == ultimoErrorMostrado) return
        ultimoErrorMostrado = error
        dialogoError?.dismiss()
        val contenido = DialogErrorPremiumBinding.inflate(layoutInflater)
        contenido.textoErrorTitulo.text = getString(R.string.cobro_error_titulo)
        contenido.textoErrorMensaje.text = mensajeError(error)
        val dialogo = crearDialogo(contenido.root)
        contenido.btnEntendidoError.setOnClickListener { dialogo.dismiss() }
        dialogo.setCanceledOnTouchOutside(true)
        dialogo.setOnDismissListener {
            if (dialogoError === dialogo) dialogoError = null
            viewModel.limpiarError()
        }
        dialogo.show()
        dialogoError = dialogo
    }

    private fun mostrarExito(estado: CobroUiState) {
        if (!estado.exito || dialogoExitoMostrado) return
        dialogoExitoMostrado = true

        val contenido = DialogCobroExitosoBinding.inflate(layoutInflater)
        contenido.textoExitoCobroMensaje.text = getString(
            R.string.cobro_exito_mensaje,
            numeroMesa
        )
        contenido.textoExitoCobroTotal.text = getString(
            R.string.cobro_exito_total,
            formatearMoneda(estado.totalConPropina)
        )
        contenido.textoExitoCobroVuelto.isVisible = estado.vuelto > BigDecimal.ZERO
        contenido.textoExitoCobroVuelto.text = getString(
            R.string.cobro_vuelto,
            formatearMoneda(estado.vuelto)
        )

        val dialogo = crearDialogo(contenido.root)
        dialogo.setCancelable(false)

        contenido.btnVolverMapaCobro.setOnClickListener {
            dialogo.dismiss()
            devolverResultado()
        }
        dialogo.setCanceledOnTouchOutside(false)
        dialogo.setOnDismissListener { if (dialogoExito === dialogo) dialogoExito = null }
        dialogo.show()
        dialogoExito = dialogo
    }

    private fun intentarSalir() {
        if (terminando) return
        if (dialogoSalir?.isShowing == true) return
        mostrarDialogoSalir()
    }

    private fun mostrarDialogoSalir() {
        val contenido = DialogSalirCobroBinding.inflate(layoutInflater)
        val dialogo = crearDialogo(contenido.root)
        dialogo.setCancelable(true)

        contenido.btnSeguirCobrando.setOnClickListener { dialogo.dismiss() }
        contenido.btnSalirSinCobrar.setOnClickListener {
            dialogo.dismiss()
            finish()
        }
        dialogo.setCanceledOnTouchOutside(true)
        dialogo.setOnDismissListener { if (dialogoSalir === dialogo) dialogoSalir = null }
        dialogo.show()
        dialogoSalir = dialogo
    }

    private fun mostrarDialogoDivision() {
        dialogoDivision?.dismiss()
        val contenido = DialogDividirCuentaBinding.inflate(layoutInflater)
        val dialogo = crearDialogo(contenido.root)
        dialogo.setCancelable(true)
        dialogo.setCanceledOnTouchOutside(true)

        contenido.btnCancelarDivision.setOnClickListener { dialogo.dismiss() }
        contenido.btnConfirmarDivision.setOnClickListener {
            val personas = contenido.textoComensales.text
                ?.toString()
                ?.trim()
                ?.toIntOrNull()
            if (personas == null ||
                personas < CobroViewModel.MIN_COMENSALES ||
                personas > CobroViewModel.MAX_COMENSALES
            ) {
                contenido.campoComensales.error = getString(R.string.cobro_dividir_error)
            } else {
                contenido.campoComensales.error = null
                if (viewModel.establecerDivision(personas)) {
                    dialogo.dismiss()
                } else {
                    contenido.campoComensales.error = getString(R.string.cobro_dividir_error)
                }
            }
        }
        dialogo.setOnDismissListener {
            if (dialogoDivision === dialogo) dialogoDivision = null
            sincronizarSwitchDivision()
        }
        dialogo.show()
        dialogoDivision = dialogo
    }

    private fun sincronizarSwitchDivision() {
        actualizandoDivision = true
        binding.switchDividirCuenta.isChecked = viewModel.estado.value.dividirCuenta
        actualizandoDivision = false
    }

    private fun mostrarDialogoDescuento() {
        dialogoDescuento?.dismiss()
        val contenido = DialogAplicarDescuentoBinding.inflate(layoutInflater)
        val dialogo = crearDialogo(contenido.root)
        dialogo.setCancelable(true)
        dialogo.setCanceledOnTouchOutside(true)

        val descuentoActual = viewModel.estado.value.descuento
        var tipo = descuentoActual?.tipo ?: TipoDescuento.PORCENTAJE
        if (tipo == TipoDescuento.MONTO_FIJO) {
            contenido.btnTipoMonto.isChecked = true
        }
        actualizarEtiquetaDescuento(contenido, tipo)
        descuentoActual?.let {
            contenido.textoValorDescuento.setText(it.valor.stripTrailingZeros().toPlainString())
            contenido.textoValorDescuento.setSelection(contenido.textoValorDescuento.text?.length ?: 0)
        }

        contenido.grupoTipoDescuento.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            tipo = if (checkedId == R.id.btnTipoPorcentaje) {
                TipoDescuento.PORCENTAJE
            } else {
                TipoDescuento.MONTO_FIJO
            }
            actualizarEtiquetaDescuento(contenido, tipo)
        }

        contenido.btnQuitarDescuento.isVisible = descuentoActual != null
        contenido.btnQuitarDescuento.setOnClickListener {
            viewModel.quitarDescuento()
            dialogo.dismiss()
        }
        contenido.btnCancelarDescuento.setOnClickListener { dialogo.dismiss() }
        contenido.btnConfirmarDescuento.setOnClickListener {
            val texto = contenido.textoValorDescuento.text?.toString().orEmpty()
            if (viewModel.aplicarDescuento(tipo, texto)) {
                dialogo.dismiss()
            } else {
                val codigo = viewModel.estado.value.error
                contenido.campoValorDescuento.error = getString(
                    if (codigo == CobroViewModel.ERROR_DESCUENTO_PAGADO) {
                        R.string.cobro_descuento_error_pagado
                    } else {
                        R.string.cobro_descuento_error
                    }
                )
                viewModel.limpiarError()
            }
        }
        dialogo.setOnDismissListener {
            if (dialogoDescuento === dialogo) dialogoDescuento = null
        }
        dialogo.show()
        dialogoDescuento = dialogo
    }

    private fun actualizarEtiquetaDescuento(
        contenido: DialogAplicarDescuentoBinding,
        tipo: TipoDescuento
    ) {
        val esPorcentaje = tipo == TipoDescuento.PORCENTAJE
        contenido.campoValorDescuento.setHint(
            if (esPorcentaje) {
                R.string.cobro_descuento_valor_porcentaje
            } else {
                R.string.cobro_descuento_valor_monto
            }
        )
        contenido.campoValorDescuento.suffixText = if (esPorcentaje) "%" else "S/"
        contenido.campoValorDescuento.error = null
    }

    private fun crearDialogo(contenido: View): AlertDialog {
        val dialogo = AlertDialog.Builder(this)
            .setView(contenido)
            .create()
        dialogo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val anchoMaximo = (480 * resources.displayMetrics.density).toInt()
            val anchoPantalla = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            setLayout(minOf(anchoPantalla, anchoMaximo), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return dialogo
    }

    private fun devolverResultado() {
        if (terminando) return
        terminando = true
        val datos = Intent().apply {
            putExtra(EXTRA_MESA_ID, mesaId)
            putExtra(EXTRA_MESA_NUMERO, numeroMesa)
        }
        setResult(RESULT_OK, datos)
        finish()
    }

    private fun mensajeError(codigo: String): String =
        getString(MENSAJES_ERROR[codigo] ?: R.string.cobro_error_desconocido)

    private fun BigDecimal.aTextoEditable(): String =
        if (compareTo(BigDecimal.ZERO) == 0) "" else toPlainString()

    companion object {
        const val EXTRA_MESA_ID = "extra_mesa_id"
        const val EXTRA_MESA_NUMERO = "extra_mesa_numero"

        /** Codigo de error del ViewModel -> texto visible (siempre en strings.xml). */
        private val MENSAJES_ERROR: Map<String, Int> = mapOf(
            CobroViewModel.ERROR_RED to R.string.cobro_error_red,
            CobroViewModel.ERROR_SESION to R.string.cobro_error_sesion,
            CobroViewModel.ERROR_VALIDACION to R.string.cobro_error_validacion,
            CobroViewModel.ERROR_MONTO to R.string.cobro_error_monto,
            CobroViewModel.ERROR_MONTO_EXCEDE to R.string.cobro_error_monto_excede,
            CobroViewModel.ERROR_SIN_SALDO to R.string.cobro_error_sin_saldo,
            CobroViewModel.ERROR_REFERENCIA to R.string.cobro_error_referencia,
            CobroViewModel.ERROR_COMENSALES to R.string.cobro_error_comensales,
            CobroViewModel.ERROR_DESCUENTO to R.string.cobro_descuento_error,
            CobroViewModel.ERROR_DESCUENTO_PAGADO to R.string.cobro_descuento_error_pagado,
            CobroViewModel.ERROR_CONFLICTO to R.string.cobro_error_conflicto,
            CobroViewModel.ERROR_CONFLICTO_PAGADA to R.string.cobro_error_conflicto_pagada,
            CobroViewModel.ERROR_BLOQUEADO to R.string.cobro_error_bloqueado,
            CobroViewModel.ERROR_SERVIDOR to R.string.cobro_error_servidor
        )
    }
}
