package com.lacasablanca.mesero.ui.perfil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ActivityPerfilBinding
import com.lacasablanca.mesero.ui.login.LoginActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Perfil del mesero a pantalla completa (PRB-83). Sustituye al bottom sheet.
 *
 * El nombre y el rol se resuelven desde [com.lacasablanca.mesero.data.repository.AuthRepository],
 * que lee la sesion persistida por [com.lacasablanca.mesero.data.local.SessionManager].
 * Terminal y turno aun no existen en la API: se muestran como "No asignado"
 * hasta que cierren las tareas de backend (PRB-84 y PRB-85).
 */
class PerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilBinding

    private val contenedor get() = (application as MeseroApp).container

    private var cerrandoSesion = false
    private var navegando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.barraPerfil.setNavigationOnClickListener { finish() }
        binding.btnCerrarSesionPerfil.setOnClickListener { cerrarSesion() }

        cargarPerfil()
    }

    /** Nombre y rol salen del SessionManager; terminal y turno quedan pendientes. */
    private fun cargarPerfil() {
        lifecycleScope.launch {
            val sesion = contenedor.authRepository.sesionGuardada()
            if (sesion == null) {
                irALogin()
                return@launch
            }

            val usuario = sesion.usuario
            binding.textoPerfilNombre.text = usuario.nombre.ifBlank {
                getString(R.string.perfil_nombre_vacio)
            }
            binding.textoPerfilRol.text = usuario.rol.ifBlank {
                getString(R.string.perfil_rol_mozo)
            }
            binding.textoPerfilUsuario.text = usuario.nombre.ifBlank {
                getString(R.string.perfil_no_asignado)
            }
            binding.textoPerfilTerminal.text = getString(R.string.perfil_no_asignado)
            binding.textoPerfilTurno.text = getString(R.string.perfil_no_asignado)
            binding.textoPerfilFecha.text = LocalDate.now().format(FORMATO_FECHA)
        }
    }

    /** Limpia la sesion persistida y vuelve al login sin dejar la pantalla en el historial. */
    private fun cerrarSesion() {
        if (cerrandoSesion) return
        cerrandoSesion = true
        binding.btnCerrarSesionPerfil.isEnabled = false

        lifecycleScope.launch {
            contenedor.authRepository.cerrarSesion()
            irALogin()
        }
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

    private companion object {
        val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
