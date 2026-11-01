package com.lacasablanca.mesero.ui.comanda

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.ActivityComandaBinding
import com.lacasablanca.mesero.domain.ErrorApp
import com.lacasablanca.mesero.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Comanda base (HU-03 / PRB-39). Si el mesero sale sin enviar el pedido,
 * la mesa vuelve a LIBRE antes de cerrar la pantalla (D11). El catalogo y
 * el carrito son PRB-38; cuando existan, se pasara [EXTRA_PEDIDO_ENVIADO]
 * para no liberar una mesa con pedido.
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
        dialogoAbierto?.dismiss()
        dialogoAbierto = null
        super.onDestroy()
    }

    /** Atras y la flecha de la toolbar hacen lo mismo: cerrar (y liberar) la mesa. */
    private fun intentarSalir() {
        if (saliendo) return
        if (pedidoEnviado) {
            // PRB-38: el pedido ya viajo al backend; la mesa debe quedar OCUPADA.
            terminar(mesaLiberada = false, liberarPendiente = false)
            return
        }
        saliendo = true
        viewModel.liberarMesa(mesaId)
    }

    private fun render(estado: ComandaUiState) {
        val alpha = if (estado.liberandoMesa) ALPHA_DESHABILITADO else ALPHA_ACTIVO
        binding.barraComanda.navigationIcon?.alpha = alpha
    }

    private fun manejarEvento(evento: ComandaEvento) {
        when (evento) {
            ComandaEvento.MesaLiberada -> terminar(mesaLiberada = true, liberarPendiente = false)

            is ComandaEvento.ErrorAlLiberar -> {
                saliendo = false
                mostrarDialogoErrorLiberar(evento.error)
            }

            ComandaEvento.SesionExpirada -> {
                saliendo = false
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
