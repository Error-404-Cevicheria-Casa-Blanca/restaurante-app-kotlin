package com.lacasablanca.mesero.ui.mesas

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.BottomSheetPerfilBinding

/**
 * Perfil del mesero (PRB-83). Muestra el nombre y el rol de la sesion activa
 * y concentra el cierre de sesion en un boton grande.
 *
 * La Activity le pasa los datos ya resueltos por [MesasViewModel] (que a su vez
 * los lee del repositorio de autenticacion / SessionManager); el sheet no
 * consulta fuentes de datos ni conoce tokens.
 */
class PerfilBottomSheet : BottomSheetDialogFragment() {

    /** La Activity implementa esto para cerrar la sesion real. */
    interface Listener {
        fun onCerrarSesionDesdePerfil()
    }

    private var _binding: BottomSheetPerfilBinding? = null
    private val binding get() = _binding!!

    private val nombre: String get() = requireArguments().getString(ARG_NOMBRE).orEmpty()
    private val rol: String get() = requireArguments().getString(ARG_ROL).orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textoPerfilNombre.text = nombre.ifBlank {
            getString(R.string.perfil_nombre_vacio)
        }
        binding.textoPerfilRol.text = rol.ifBlank {
            getString(R.string.perfil_rol_mozo)
        }

        binding.btnCerrarSesionPerfil.setOnClickListener {
            (activity as? Listener)?.onCerrarSesionDesdePerfil()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val dialogo = dialog as? BottomSheetDialog ?: return

        dialogo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(DIM_AMOUNT)
            val ancho = if (resources.configuration.screenWidthDp >= ANCHO_TABLET_DP) {
                (resources.displayMetrics.widthPixels * ANCHO_TABLET_FRACCION).toInt()
            } else {
                ViewGroup.LayoutParams.MATCH_PARENT
            }
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayout(ancho, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        dialogo.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)

        dialogo.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = true
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "PerfilBottomSheet"

        private const val ARG_NOMBRE = "arg_perfil_nombre"
        private const val ARG_ROL = "arg_perfil_rol"
        private const val ANCHO_TABLET_DP = 600
        private const val ANCHO_TABLET_FRACCION = 0.5f
        private const val DIM_AMOUNT = 0.45f

        fun nueva(nombre: String, rol: String): PerfilBottomSheet =
            PerfilBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_NOMBRE, nombre)
                    putString(ARG_ROL, rol)
                }
            }
    }
}
