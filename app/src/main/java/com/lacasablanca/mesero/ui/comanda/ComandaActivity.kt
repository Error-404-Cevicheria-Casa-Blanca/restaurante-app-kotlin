package com.lacasablanca.mesero.ui.comanda

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ActivityComandaBinding

/**
 * Comanda base (HU-03). El catalogo y el carrito son la siguiente entrega (PE-13).
 * Al volver sin enviar devuelve EXTRA_MESA_ID para que el mapa libere la mesa (D11).
 */
class ComandaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComandaBinding

    private var mesaId = -1L
    private var yaCerrada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComandaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mesaId = intent.getLongExtra(EXTRA_MESA_ID, -1L)
        val numero = intent.getIntExtra(EXTRA_MESA_NUMERO, 0)
        val ubicacion = intent.getStringExtra(EXTRA_MESA_UBICACION).orEmpty()

        if (mesaId <= 0L || numero <= 0) {
            finish()
            return
        }

        binding.textoTitulo.text = getString(R.string.comanda_titulo, numero, ubicacion)
        binding.badgeEstado.text = getString(R.string.comanda_badge)
        binding.barraComanda.setNavigationOnClickListener { cerrarSinEnviar() }

        binding.btnConfirmar.isEnabled = false
        binding.btnConfirmar.contentDescription = getString(R.string.comanda_confirmar_deshabilitado)

        onBackPressedDispatcher.addCallback(this) { cerrarSinEnviar() }
    }

    private fun cerrarSinEnviar() {
        if (yaCerrada) return
        yaCerrada = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_MESA_ID, mesaId))
        finish()
    }

    companion object {
        const val EXTRA_MESA_ID = "extra_mesa_id"
        const val EXTRA_MESA_NUMERO = "extra_mesa_numero"
        const val EXTRA_MESA_UBICACION = "extra_mesa_ubicacion"
    }
}
