package com.lacasablanca.mesero.ui.cobro

import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.ActivityCobroBinding
import com.lacasablanca.mesero.databinding.DialogCobroExitosoBinding
import com.lacasablanca.mesero.databinding.DialogSalirCobroBinding
import com.lacasablanca.mesero.domain.model.MetodoPago
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

    private val contenedor get() = (application as MeseroApp).container

    private val mesaId by lazy { intent.getLongExtra(EXTRA_MESA_ID, -1L) }
    private val numeroMesa by lazy { intent.getIntExtra(EXTRA_MESA_NUMERO, 0) }

    private val viewModel: CobroViewModel by viewModels {
        GenericViewModelFactory { CobroViewModel(mesaId, contenedor.cobroRepository) }
    }

    private var actualizandoChips = false
    private var ultimoErrorMostrado: String? = null
    private var dialogoSalir: AlertDialog? = null
    private var dialogoExito: AlertDialog? = null
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
        binding.textoModoDemo.isVisible = BuildConfig.USE_FAKE_DATA

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
        onBackPressedDispatcher.addCallback(this) { intentarSalir() }
    }

    private fun prepararListas() {
        adaptadorCuenta = CuentaLineaAdapter()
        binding.recyclerCuenta.layoutManager = LinearLayoutManager(this)
        binding.recyclerCuenta.adapter = adaptadorCuenta

        adaptadorPagos = PagoAdapter(viewModel::eliminarPago)
        binding.recyclerPagos.layoutManager = LinearLayoutManager(this)
        binding.recyclerPagos.adapter = adaptadorPagos
    }

    private fun prepararAcciones() {
        binding.btnAgregarPago.setOnClickListener { viewModel.agregarPago() }
        binding.btnConfirmarCobro.setOnClickListener { viewModel.confirmarCobro() }
        binding.btnReintentarCobro.setOnClickListener { viewModel.cargarCuenta() }

        binding.grupoMediosPago.setOnCheckedStateChangeListener { _, ids ->
            if (actualizandoChips) return@setOnCheckedStateChangeListener
            ids.firstOrNull()?.let { id ->
                metodoDesdeChip(id)?.let(viewModel::seleccionarMetodo)
            }
        }

        binding.textoMontoRecibido.doAfterTextChanged { editable ->
            viewModel.actualizarMontoRecibido(editable?.toString().orEmpty())
        }
        binding.textoReferencia.doAfterTextChanged { editable ->
            viewModel.actualizarReferencia(editable?.toString().orEmpty())
        }
        binding.textoPropina.doAfterTextChanged { editable ->
            viewModel.actualizarPropina(editable?.toString().orEmpty())
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
        binding.textoModoDemo.isVisible = BuildConfig.USE_FAKE_DATA

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
        renderBarra(estado)
        mostrarErrorDeAccion(estado)
        mostrarExito(estado)
    }

    private fun renderCuenta(estado: CobroUiState) {
        val cuenta = estado.cuenta ?: return
        val items = cuenta.items.orEmpty()
        adaptadorCuenta.submitList(items)
        binding.textoCuentaVacia.isVisible = items.isEmpty()
        binding.textoSubtotal.text = formatearMoneda(cuenta.subtotal ?: BigDecimal.ZERO)
        binding.textoIgv.text = formatearMoneda(cuenta.igv ?: BigDecimal.ZERO)
        binding.textoTotal.text = formatearMoneda(cuenta.total ?: BigDecimal.ZERO)
        binding.textoPropinaSugerida.text = getString(
            R.string.cobro_propina_sugerida,
            formatearMoneda(cuenta.propinaSugerida ?: BigDecimal.ZERO)
        )
    }

    private fun renderFormulario(estado: CobroUiState) {
        renderChips(estado.metodoSeleccionado)
        val esEfectivo = estado.metodoSeleccionado == MetodoPago.EFECTIVO
        binding.campoMontoRecibido.isVisible = esEfectivo
        binding.campoReferencia.isVisible = !esEfectivo
        binding.textoVuelto.isVisible = esEfectivo
        binding.textoMontoACobrar.isVisible = !esEfectivo
        val saldo = estado.totalConPropina
            .subtract(estado.totalPagado)
            .max(BigDecimal.ZERO)
        binding.textoMontoACobrar.text = getString(
            R.string.cobro_monto_a_cobrar,
            formatearMoneda(saldo)
        )
        sincronizarCampo(binding.textoMontoRecibido, estado.montoRecibido)
        sincronizarCampo(binding.textoReferencia, estado.referencia)
        sincronizarCampo(binding.textoPropina, estado.propina.aTextoEditable())
        binding.textoVuelto.text = getString(
            R.string.cobro_vuelto,
            formatearMoneda(estado.vuelto)
        )
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
        binding.textoSinPagos.isVisible = estado.pagos.isEmpty()
        binding.recyclerPagos.isVisible = estado.pagos.isNotEmpty()
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
        if (campo.hasFocus()) return
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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cobro_error_titulo)
            .setMessage(mensajeError(error))
            .setPositiveButton(R.string.accion_aceptar, null)
            .show()
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

        val dialogo = MaterialAlertDialogBuilder(this)
            .setView(contenido.root)
            .setCancelable(false)
            .create()

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
        val dialogo = MaterialAlertDialogBuilder(this)
            .setView(contenido.root)
            .setCancelable(true)
            .create()

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

    private fun mensajeError(codigo: String): String = when (codigo) {
        CobroViewModel.ERROR_RED -> getString(R.string.cobro_error_red)
        CobroViewModel.ERROR_SESION -> getString(R.string.cobro_error_sesion)
        CobroViewModel.ERROR_MONTO -> getString(R.string.cobro_error_monto)
        CobroViewModel.ERROR_SIN_SALDO -> getString(R.string.cobro_error_sin_saldo)
        CobroViewModel.ERROR_REFERENCIA -> getString(R.string.cobro_error_referencia)
        CobroViewModel.ERROR_CONFLICTO -> getString(R.string.cobro_error_conflicto)
        CobroViewModel.ERROR_CONFLICTO_PAGADA -> getString(R.string.cobro_error_conflicto_pagada)
        CobroViewModel.ERROR_BLOQUEADO -> getString(R.string.cobro_error_bloqueado)
        CobroViewModel.ERROR_SERVIDOR -> getString(R.string.cobro_error_servidor)
        CobroViewModel.ERROR_VALIDACION -> getString(R.string.cobro_error_validacion)
        else -> getString(R.string.cobro_error_desconocido)
    }

    private fun BigDecimal.aTextoEditable(): String =
        if (compareTo(BigDecimal.ZERO) == 0) "" else toPlainString()

    companion object {
        const val EXTRA_MESA_ID = "extra_mesa_id"
        const val EXTRA_MESA_NUMERO = "extra_mesa_numero"
    }
}
