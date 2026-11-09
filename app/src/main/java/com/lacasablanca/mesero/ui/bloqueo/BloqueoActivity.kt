package com.lacasablanca.mesero.ui.bloqueo

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.data.repository.FakeAuthRepository
import com.lacasablanca.mesero.databinding.ActivityBloqueoBinding
import com.lacasablanca.mesero.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Pantalla completa de bloqueo rapido (PRB-81).
 *
 * Solo dibuja el estado del [BloqueoViewModel] y reenvia las pulsaciones del
 * teclado. El boton Atras queda interceptado: la unica salida es un PIN valido
 * o el regreso al login si la sesion dejo de existir.
 */
class BloqueoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBloqueoBinding
    private var botonesTeclado: List<MaterialButton> = emptyList()
    private var navegando = false

    private val contenedor get() = (application as MeseroApp).container

    private val viewModel: BloqueoViewModel by viewModels {
        GenericViewModelFactory {
            BloqueoViewModel(contenedor.authRepository, contenedor.sessionManager)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBloqueoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bloquearBotonAtras()
        configurarTeclado()
        configurarModoDemo()
        render(BloqueoUiState())

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::render) }
                launch { viewModel.eventos.collect(::manejarEvento) }
            }
        }
    }

    /** El boton fisico Atras no cierra esta pantalla (requisito PRB-81). */
    private fun bloquearBotonAtras() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@BloqueoActivity,
                    R.string.bloqueo_atras_bloqueado,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun configurarTeclado() {
        botonesTeclado = listOf(
            binding.btnTecla1,
            binding.btnTecla2,
            binding.btnTecla3,
            binding.btnTecla4,
            binding.btnTecla5,
            binding.btnTecla6,
            binding.btnTecla7,
            binding.btnTecla8,
            binding.btnTecla9,
            binding.btnTecla0
        )
        val digitos = mapOf(
            binding.btnTecla0 to '0',
            binding.btnTecla1 to '1',
            binding.btnTecla2 to '2',
            binding.btnTecla3 to '3',
            binding.btnTecla4 to '4',
            binding.btnTecla5 to '5',
            binding.btnTecla6 to '6',
            binding.btnTecla7 to '7',
            binding.btnTecla8 to '8',
            binding.btnTecla9 to '9'
        )
        digitos.forEach { (boton, digito) ->
            boton.setOnClickListener { viewModel.agregarDigito(digito) }
        }
        binding.btnBorrar.setOnClickListener { viewModel.borrarDigito() }
    }

    /** En modo demostracion se rotula el PIN fake para poder probar el flujo. */
    private fun configurarModoDemo() {
        binding.textoPinDemo.isVisible = BuildConfig.USE_FAKE_DATA
        if (BuildConfig.USE_FAKE_DATA) {
            binding.textoPinDemo.text = getString(
                R.string.bloqueo_pin_demo,
                FakeAuthRepository.PIN_DEMO
            )
        }
    }

    private fun render(estado: BloqueoUiState) {
        actualizarPuntos(estado.digitosIngresados)

        binding.textoUsuario.isVisible = estado.usuario.isNotBlank()
        if (estado.usuario.isNotBlank()) {
            binding.textoUsuario.text = getString(R.string.bloqueo_sesion, estado.usuario)
        }

        binding.textoError.isVisible = estado.error != null
        estado.error?.let { binding.textoError.text = getString(it.mensajeRes()) }

        binding.progresoVerificando.isVisible = estado.cargando
        binding.textoVerificando.isVisible = estado.cargando
        botonesTeclado.forEach { it.isEnabled = !estado.cargando }
        binding.btnBorrar.isEnabled = !estado.cargando
    }

    private fun actualizarPuntos(cantidad: Int) {
        val puntos = listOf(binding.punto1, binding.punto2, binding.punto3, binding.punto4)
        puntos.forEachIndexed { indice, punto ->
            val color = if (indice < cantidad) {
                R.color.primary
            } else {
                R.color.surface_container_highest
            }
            punto.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, color)
            )
        }
        binding.puntosPin.contentDescription = getString(
            R.string.bloqueo_puntos_cd,
            cantidad,
            BloqueoUiState.LONGITUD_PIN
        )
    }

    private fun manejarEvento(evento: BloqueoEvento) {
        when (evento) {
            BloqueoEvento.Desbloqueado -> desbloquear()
            BloqueoEvento.SesionExpirada -> irALogin()
        }
    }

    private fun desbloquear() {
        if (navegando) return
        navegando = true
        setResult(RESULT_OK)
        finish()
    }

    private fun irALogin() {
        if (navegando) return
        navegando = true
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    @StringRes
    private fun ErrorBloqueo.mensajeRes(): Int = when (this) {
        ErrorBloqueo.PIN_INCORRECTO -> R.string.bloqueo_error_pin
        ErrorBloqueo.RED -> R.string.bloqueo_error_red
        ErrorBloqueo.BLOQUEADO -> R.string.bloqueo_error_bloqueado
        ErrorBloqueo.SESION -> R.string.bloqueo_error_sesion
        ErrorBloqueo.DESCONOCIDO -> R.string.bloqueo_error_generico
    }
}
