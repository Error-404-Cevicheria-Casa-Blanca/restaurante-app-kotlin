package com.lacasablanca.mesero.ui.comanda

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ActivityComandaBinding

/**
 * Comanda base (HU-03). El catalogo y el carrito son la siguiente entrega (PE-13).
 * Al volver sin enviar, la mesa permanece en ATENDIDA (E3); PE-13 definira el cierre.
 */
class ComandaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComandaBinding

    private var mesaId = -1L

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
        binding.barraComanda.setNavigationOnClickListener { finish() }

        binding.btnConfirmar.isEnabled = false
        binding.btnConfirmar.contentDescription = getString(R.string.comanda_confirmar_deshabilitado)

        onBackPressedDispatcher.addCallback(this) { finish() }
    }

    companion object {
        const val EXTRA_MESA_ID = "extra_mesa_id"
        const val EXTRA_MESA_NUMERO = "extra_mesa_numero"
        const val EXTRA_MESA_UBICACION = "extra_mesa_ubicacion"
    }
}
