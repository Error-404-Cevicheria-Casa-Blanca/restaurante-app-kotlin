package com.lacasablanca.mesero.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.ActivityLoginBinding
import com.lacasablanca.mesero.ui.mesas.MesasActivity
import kotlinx.coroutines.launch

/**
 * Pantalla de login. Solo dibuja el estado del ViewModel y reenvia eventos.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val contenedor get() = (application as MeseroApp).container

    private val viewModel: LoginViewModel by viewModels {
        GenericViewModelFactory { LoginViewModel(contenedor.authRepository) }
    }

    private var mostrarClavePintado: Boolean? = null
    private var navegando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionApp.text = getString(R.string.login_version, BuildConfig.VERSION_NAME)
        binding.avisoDemo.isVisible = BuildConfig.USE_FAKE_DATA

        binding.entradaUsuario.doAfterTextChanged { texto ->
            viewModel.onUsuarioCambiado(texto?.toString().orEmpty())
        }
        binding.entradaClave.doAfterTextChanged { texto ->
            viewModel.onClaveCambiada(texto?.toString().orEmpty())
        }
        binding.inputClave.setEndIconOnClickListener {
            viewModel.onMostrarClaveCambiado(!viewModel.estado.value.mostrarClave)
        }
        binding.btnIngresar.setOnClickListener { viewModel.ingresar() }
        binding.entradaClave.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) {
                viewModel.ingresar()
                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::render) }
                launch {
                    viewModel.eventos.collect { evento ->
                        if (evento == LoginEvento.IrAMesas) irAMesas()
                    }
                }
            }
        }
    }

    private fun render(estado: LoginUiState) {
        if (binding.entradaUsuario.text?.toString() != estado.usuario) {
            binding.entradaUsuario.setText(estado.usuario)
        }
        if (binding.entradaClave.text?.toString() != estado.clave) {
            binding.entradaClave.setText(estado.clave)
        }

        if (mostrarClavePintado != estado.mostrarClave) {
            mostrarClavePintado = estado.mostrarClave
            aplicarVisibilidadClave(estado.mostrarClave)
        }

        binding.btnIngresar.isEnabled = !estado.cargando
        binding.btnIngresar.text = getString(
            if (estado.cargando) R.string.login_cargando else R.string.login_ingresar
        )
        binding.progresoLogin.isVisible = estado.cargando

        binding.textoError.isVisible = estado.error != null
        estado.error?.let { binding.textoError.text = getString(it.mensajeRes()) }
    }

    private fun aplicarVisibilidadClave(mostrar: Boolean) {
        val seleccion = binding.entradaClave.selectionEnd
        binding.entradaClave.inputType = if (mostrar) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.entradaClave.setSelection(seleccion.coerceIn(0, binding.entradaClave.text?.length ?: 0))

        binding.inputClave.setEndIconDrawable(
            if (mostrar) R.drawable.ic_visibility_off else R.drawable.ic_visibility
        )
        binding.inputClave.endIconContentDescription = getString(
            if (mostrar) R.string.login_ocultar_clave else R.string.login_mostrar_clave
        )
    }

    private fun irAMesas() {
        if (navegando) return
        navegando = true
        startActivity(Intent(this, MesasActivity::class.java))
        finish()
    }

    @StringRes
    private fun ErrorLogin.mensajeRes(): Int = when (this) {
        ErrorLogin.CAMPOS_VACIOS -> R.string.login_error_campos
        ErrorLogin.CREDENCIALES -> R.string.login_error_credenciales
        ErrorLogin.RED -> R.string.login_error_red
        ErrorLogin.ROL_NO_PERMITIDO -> R.string.login_error_rol
        ErrorLogin.DESCONOCIDO -> R.string.login_error_generico
    }
}
