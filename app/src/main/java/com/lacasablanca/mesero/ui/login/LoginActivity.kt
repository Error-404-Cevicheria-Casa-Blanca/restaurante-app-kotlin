package com.lacasablanca.mesero.ui.login

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.lacasablanca.mesero.BuildConfig
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.ActivityLoginBinding
import com.lacasablanca.mesero.ui.main.MainActivity
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
        configurarBarraEstadoHero()
        configurarInsetsTeclado()

        binding.versionApp.text = getString(R.string.marca_footer_version, BuildConfig.VERSION_NAME)
        binding.avisoDemo.isVisible = BuildConfig.USE_FAKE_DATA

        configurarCampos()
        binding.btnIngresar.setOnClickListener { viewModel.ingresar() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::render) }
                launch {
                    viewModel.eventos.collect { evento ->
                        if (evento == LoginEvento.IrAMesas) irAInicio()
                    }
                }
            }
        }
    }

    private fun configurarBarraEstadoHero() {
        val esTablet = resources.configuration.smallestScreenWidthDp >= UMBRAL_TABLET_DP
        if (!esTablet) {
            WindowInsetsControllerCompat(window, window.decorView)
                .isAppearanceLightStatusBars = false
        }
    }

    private fun configurarCampos() {
        binding.entradaUsuario.doAfterTextChanged { texto ->
            viewModel.onUsuarioCambiado(texto?.toString().orEmpty())
        }
        binding.entradaClave.doAfterTextChanged { texto ->
            viewModel.onClaveCambiada(texto?.toString().orEmpty())
        }
        vincularColorIconoFoco(binding.inputUsuario, binding.entradaUsuario)
        vincularColorIconoFoco(binding.inputClave, binding.entradaClave)
        binding.inputClave.setEndIconOnClickListener {
            viewModel.onMostrarClaveCambiado(!viewModel.estado.value.mostrarClave)
        }
        binding.entradaClave.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) {
                viewModel.ingresar()
                true
            } else {
                false
            }
        }
    }

    private fun vincularColorIconoFoco(
        caja: TextInputLayout,
        entrada: TextInputEditText
    ) {
        val actualizar = { tieneFoco: Boolean ->
            val color = if (tieneFoco) R.color.marea_cyh else R.color.marea_tx3
            caja.setStartIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, color)))
        }
        actualizar(entrada.hasFocus())
        entrada.setOnFocusChangeListener { _, tieneFoco -> actualizar(tieneFoco) }
    }

    /**
     * Con targetSdk 37 Android 15 aplica edge-to-edge: `adjustResize` deja de
     * encoger la ventana y el teclado puede tapar los campos.
     */
    private fun configurarInsetsTeclado() {
        val paddingTopHeroBase = binding.heroLogin.paddingTop
        val paddingBottomHeroBase = binding.heroLogin.paddingBottom
        val paddingTopScrollBase = binding.scrollLogin.paddingTop
        val paddingBottomScrollBase = binding.scrollLogin.paddingBottom
        val esTablet = resources.configuration.smallestScreenWidthDp >= UMBRAL_TABLET_DP
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            val margenInferior = maxOf(barras.bottom, teclado.bottom)
            vista.updatePadding(
                left = barras.left,
                right = barras.right,
                bottom = if (esTablet) 0 else margenInferior
            )
            binding.heroLogin.updatePadding(
                top = paddingTopHeroBase + barras.top,
                bottom = paddingBottomHeroBase + if (esTablet) barras.bottom else 0
            )
            if (esTablet) {
                binding.scrollLogin.updatePadding(
                    top = paddingTopScrollBase + barras.top,
                    bottom = paddingBottomScrollBase + margenInferior
                )
            }
            insets
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
        binding.btnIngresar.icon = if (estado.cargando) {
            null
        } else {
            ContextCompat.getDrawable(this, R.drawable.ic_arrow_forward)
        }
        binding.progresoLogin.isVisible = estado.cargando

        val hayError = estado.error != null
        binding.textoError.isVisible = hayError
        estado.error?.let { binding.textoError.text = getString(it.mensajeRes()) }
        aplicarBordeErrorCampos(hayError)
    }

    private fun aplicarBordeErrorCampos(hayError: Boolean) {
        val colorLista = ContextCompat.getColorStateList(
            this,
            if (hayError) R.color.selector_error_campo_marea else R.color.selector_borde_campo_marea
        ) ?: return
        binding.inputUsuario.setBoxStrokeColorStateList(colorLista)
        binding.inputClave.setBoxStrokeColorStateList(colorLista)
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

    /** PRB-88: el inicio ahora es el contenedor con la navegacion principal. */
    private fun irAInicio() {
        if (navegando) return
        navegando = true
        startActivity(Intent(this, MainActivity::class.java))
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

    private companion object {
        const val UMBRAL_TABLET_DP = 600
    }
}
