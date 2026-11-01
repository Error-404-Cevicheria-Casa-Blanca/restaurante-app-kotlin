package com.lacasablanca.mesero.ui.comanda

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.ActivityComandaBinding
import com.lacasablanca.mesero.databinding.DialogLiberarMesaBinding
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Comanda base (HU-03 / PRB-39). Si el mesero intenta salir sin enviar el
 * pedido, se pide confirmacion antes de devolver la mesa a LIBRE (D11).
 * El catalogo y el carrito son PRB-38; cuando existan, se pasara
 * [EXTRA_PEDIDO_ENVIADO] para no liberar una mesa con pedido.
 */
class ComandaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComandaBinding

    private val contenedor get() = (application as MeseroApp).container

    private val viewModel: ComandaViewModel by viewModels {
        GenericViewModelFactory { ComandaViewModel(contenedor.mesaRepository) }
    }

    private var mesaId = -1L
    private var numeroMesa = 0
    private var pedidoEnviado = false
    private var saliendo = false
    private var dialogoAbierto: AlertDialog? = null
    private var dialogoLiberar: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComandaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mesaId = intent.getLongExtra(EXTRA_MESA_ID, -1L)
        numeroMesa = intent.getIntExtra(EXTRA_MESA_NUMERO, 0)
        val ubicacion = intent.getStringExtra(EXTRA_MESA_UBICACION).orEmpty()
        pedidoEnviado = intent.getBooleanExtra(EXTRA_PEDIDO_ENVIADO, false)

        if (mesaId <= 0L || numeroMesa <= 0) {
            finish()
            return
        }

        binding.textoTitulo.text = getString(R.string.comanda_titulo, numeroMesa, ubicacion)
        binding.badgeEstado.text = getString(R.string.comanda_badge)

        binding.btnConfirmar.isEnabled = false
        binding.btnConfirmar.contentDescription = getString(R.string.comanda_confirmar_deshabilitado)

        // Atras y la flecha de la toolbar piden confirmacion antes de liberar.
        binding.barraComanda.setNavigationOnClickListener { intentarSalir() }
        onBackPressedDispatcher.addCallback(this) { intentarSalir() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::render) }
                launch { viewModel.eventos.collect(::manejarEvento) }
            }
        }
    }

    override fun onDestroy() {
        cerrarDialogoLiberar()
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        super.onDestroy()
    }

    /**
     * Atras y la flecha de la toolbar abren el dialogo de confirmacion.
     * Si el pedido ya se envio (PRB-38), la mesa no se libera.
     */
    private fun intentarSalir() {
        if (saliendo || dialogoLiberar?.isShowing == true) return
        if (pedidoEnviado) {
            // PRB-38: el pedido ya viajo al backend; la mesa debe quedar OCUPADA.
            terminar(mesaLiberada = false, liberarPendiente = false)
            return
        }
        mostrarDialogoLiberarMesa()
    }

    /**
     * Dialogo premium de confirmacion. Mientras el PATCH esta en vuelo se
     * bloquea la cancelacion y se muestra el ProgressBar integrado.
     */
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
            if (dialogoLiberar === dialogo) {
                dialogoLiberar = null
            }
            if (dialogoAbierto === dialogo) dialogoAbierto = null
        }
        dialogo.show()

        dialogoLiberar = dialogo
        dialogoAbierto = dialogo
    }

    private fun confirmarLiberacion(contenido: DialogLiberarMesaBinding) {
        if (saliendo) return
        saliendo = true

        // Una vez confirmado no se puede cerrar el dialogo hasta recibir respuesta.
        dialogoLiberar?.setCancelable(false)
        dialogoLiberar?.setCanceledOnTouchOutside(false)

        contenido.grupoBotones.isVisible = false
        contenido.progresoLiberar.isVisible = true
        contenido.textoProgreso.isVisible = true

        viewModel.liberarMesa(mesaId)
    }

    private fun render(estado: ComandaUiState) {
        val alpha = if (estado.liberandoMesa) ALPHA_DESHABILITADO else ALPHA_ACTIVO
        binding.barraComanda.navigationIcon?.alpha = alpha
    }

    private fun manejarEvento(evento: ComandaEvento) {
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
     */
    private fun terminar(mesaLiberada: Boolean, liberarPendiente: Boolean) {
        if (isFinishing) return
        val datos = Intent().apply {
            putExtra(EXTRA_MESA_ID, mesaId)
            putExtra(EXTRA_MESA_LIBERADA, mesaLiberada)
            putExtra(EXTRA_LIBERAR_PENDIENTE, liberarPendiente)
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

        /** PRB-38 lo pondra en true cuando la comanda se haya enviado. */
        const val EXTRA_PEDIDO_ENVIADO = "extra_pedido_enviado"

        const val EXTRA_MESA_LIBERADA = "extra_mesa_liberada"
        const val EXTRA_LIBERAR_PENDIENTE = "extra_liberar_pendiente"

        private const val ALPHA_ACTIVO = 255
        private const val ALPHA_DESHABILITADO = 120
    }
}
