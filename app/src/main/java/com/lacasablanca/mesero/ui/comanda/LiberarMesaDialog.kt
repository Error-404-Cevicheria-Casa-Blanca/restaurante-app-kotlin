package com.lacasablanca.mesero.ui.comanda

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogLiberarMesaBinding

/**
 * Confirmacion de liberacion de mesa (PRB-39) extraida de [ComandaFragment].
 * Al confirmar muestra el progreso y delega el PATCH en la Activity.
 */
class LiberarMesaDialog(
    private val activity: AppCompatActivity,
    private val alConfirmar: () -> Unit
) {

    private var dialogo: AlertDialog? = null

    fun mostrar() {
        val contenido = DialogLiberarMesaBinding.inflate(activity.layoutInflater)
        val nuevo = MaterialAlertDialogBuilder(
            activity,
            R.style.ThemeOverlay_LaCasaBlanca_Dialogo_LiberarMesa
        )
            .setView(contenido.root)
            .setCancelable(true)
            .create()

        contenido.btnCancelar.setOnClickListener { nuevo.dismiss() }
        contenido.btnLiberar.setOnClickListener {
            nuevo.setCancelable(false)
            nuevo.setCanceledOnTouchOutside(false)
            contenido.grupoBotones.isVisible = false
            contenido.progresoLiberar.isVisible = true
            contenido.textoProgreso.isVisible = true
            alConfirmar()
        }

        nuevo.setCanceledOnTouchOutside(true)
        nuevo.setOnDismissListener { if (dialogo === nuevo) dialogo = null }
        nuevo.show()
        dialogo = nuevo
    }

    fun estaMostrando(): Boolean = dialogo?.isShowing == true

    fun cerrar() {
        dialogo?.dismiss()
        dialogo = null
    }
}
