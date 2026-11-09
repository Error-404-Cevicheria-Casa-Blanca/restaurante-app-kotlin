package com.lacasablanca.mesero.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.lacasablanca.mesero.databinding.FragmentPlaceholderBinding

/**
 * Placeholder temporal de los destinos de PRB-88 (Fase 1).
 *
 * Mantiene el contrato de un Fragment con constructor sin argumentos para que
 * Android pueda restaurarlo. En las fases 2 y 3 cada destino reemplaza esta
 * base por su pantalla real (mapa de mesas, pedidos y perfil).
 */
abstract class PlaceholderFragment : Fragment() {

    private var _binding: FragmentPlaceholderBinding? = null
    private val binding get() = _binding!!

    @get:StringRes
    protected abstract val tituloRes: Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaceholderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textoPlaceholder.text = getString(tituloRes)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
