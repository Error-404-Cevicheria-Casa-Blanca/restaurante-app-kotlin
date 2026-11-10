package com.lacasablanca.mesero.ui.perfil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.FragmentPerfilBinding
import com.lacasablanca.mesero.ui.login.LoginActivity
import com.lacasablanca.mesero.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Perfil del mesero dentro del tab Perfil (PRB-88, Fase 3).
 *
 * La logica vive en [PerfilViewModel], que solo depende de AuthRepository. El
 * fragmento solo dibuja el estado y reenvia el cierre de sesion. Al cerrar
 * sesion (o si no hay sesion) vuelve al Login y termina MainActivity.
 */
class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    private val contenedor get() = (requireActivity().application as MeseroApp).container

    /**
     * ViewModel con scope del Fragment (sobrevive a la rotacion). Se usa
     * ViewModelProvider directo porque el proyecto no incluye fragment-ktx.
     */
    private val viewModel: PerfilViewModel by lazy {
        ViewModelProvider(
            this,
            GenericViewModelFactory { PerfilViewModel(contenedor.authRepository) }
        )[PerfilViewModel::class.java]
    }

    private var navegando = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // La flecha de la toolbar vuelve al tab Salón dentro del contenedor.
        binding.barraPerfil.setNavigationOnClickListener { volverASalon() }
        binding.btnCerrarSesionPerfil.setOnClickListener { viewModel.cerrarSesion() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::render) }
                launch { viewModel.eventos.collect(::manejarEvento) }
            }
        }
    }

    private fun render(estado: PerfilUiState) {
        binding.textoPerfilNombre.text = estado.nombre.ifBlank {
            getString(R.string.perfil_nombre_vacio)
        }
        binding.textoPerfilRol.text = estado.rol.ifBlank {
            getString(R.string.perfil_rol_mozo)
        }
        binding.textoPerfilUsuario.text = estado.usuario.ifBlank {
            getString(R.string.perfil_no_asignado)
        }
        binding.textoPerfilTerminal.text = estado.terminal.ifBlank {
            getString(R.string.perfil_no_asignado)
        }
        binding.textoPerfilTurno.text = estado.turno.ifBlank {
            getString(R.string.perfil_no_asignado)
        }
        binding.textoPerfilFecha.text = estado.fecha
        binding.btnCerrarSesionPerfil.isEnabled = !estado.cerrandoSesion
    }

    private fun manejarEvento(evento: PerfilEvento) {
        when (evento) {
            PerfilEvento.IrALogin, PerfilEvento.SesionCerrada -> irALogin()
        }
    }

    private fun volverASalon() {
        (requireActivity() as? MainActivity)?.seleccionarTab(R.id.navSalon)
    }

    private fun irALogin() {
        if (navegando) return
        navegando = true
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
