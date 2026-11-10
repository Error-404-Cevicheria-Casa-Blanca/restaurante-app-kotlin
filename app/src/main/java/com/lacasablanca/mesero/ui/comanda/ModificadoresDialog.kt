package com.lacasablanca.mesero.ui.comanda

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.DialogModificadoresBinding
import com.lacasablanca.mesero.databinding.ItemModificadorBinding
import com.lacasablanca.mesero.domain.model.Modificador
import com.lacasablanca.mesero.domain.model.Producto
import com.lacasablanca.mesero.ui.common.formatearMoneda
import java.math.BigDecimal

/**
 * Selector premium de modificadores por plato (PRB-92).
 *
 * La Activity solo entrega el plato y el callback con la seleccion; el
 * dialogo arma las filas, lleva el total con BigDecimal y se cierra al
 * confirmar. Los modificadores se muestran como checkboxes porque el backend
 * no informa grupos mutuamente excluyentes: el mesero puede combinar varios
 * ("Sin cebolla" + "Extra queso").
 */
class ModificadoresDialog(
    private val activity: Activity,
    private val producto: Producto,
    private val alAgregar: (List<Modificador>) -> Unit
) {

    private var dialogo: AlertDialog? = null
    private lateinit var vista: DialogModificadoresBinding
    private val seleccionados = linkedSetOf<Modificador>()

    /** Muestra el selector; si ya estaba abierto, lo reemplaza. */
    fun mostrar() {
        cerrar()

        vista = DialogModificadoresBinding.inflate(
            LayoutInflater.from(activity),
            null,
            false
        )
        seleccionados.clear()

        vista.textoPlatoModificadores.text = producto.nombre.ifBlank {
            activity.getString(R.string.comanda_plato_sin_nombre, producto.id)
        }
        vista.listaModificadores.removeAllViews()
        producto.modificadores.forEach { modificador ->
            vista.listaModificadores.addView(crearFila(modificador))
        }
        actualizarTotal()
        limitarAltoLista()

        vista.btnCancelarModificadores.setOnClickListener { cerrar() }
        vista.btnAgregarModificadores.setOnClickListener {
            val elegidos = seleccionados.toList()
            cerrar()
            alAgregar(elegidos)
        }

        val nuevo = AlertDialog.Builder(activity)
            .setView(vista.root)
            .setCancelable(true)
            .create()

        nuevo.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val anchoMaximo = (ANCHO_MAXIMO_DP * activity.resources.displayMetrics.density).toInt()
            val anchoPantalla =
                (activity.resources.displayMetrics.widthPixels * FRACCION_ANCHO).toInt()
            setLayout(
                minOf(anchoPantalla, anchoMaximo),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        nuevo.setCanceledOnTouchOutside(true)
        nuevo.setOnDismissListener {
            if (dialogo === nuevo) dialogo = null
        }
        nuevo.show()
        dialogo = nuevo
    }

    /** Cierra el dialogo si sigue abierto (confirmacion o salida de pantalla). */
    fun cerrar() {
        dialogo?.dismiss()
        dialogo = null
    }

    /**
     * Arma una fila con checkbox y precio extra. El renglon completo es
     * tactil (48dp) y el precio solo aparece cuando el modificador recarga.
     */
    private fun crearFila(modificador: Modificador): android.view.View {
        val fila = ItemModificadorBinding.inflate(
            LayoutInflater.from(activity),
            vista.listaModificadores,
            false
        )
        val contexto = activity
        val precio = formatearMoneda(modificador.precioAdicional)

        fila.checkModificador.text = modificador.nombre
        fila.checkModificador.contentDescription = if (modificador.esGratuito) {
            modificador.nombre
        } else {
            contexto.getString(R.string.cd_modificador_precio, modificador.nombre, precio)
        }
        fila.textoPrecioModificador.isVisible = !modificador.esGratuito
        fila.textoPrecioModificador.text = contexto.getString(
            R.string.modificadores_precio_extra,
            precio
        )

        fila.checkModificador.setOnCheckedChangeListener { _, marcado ->
            if (marcado) seleccionados += modificador else seleccionados -= modificador
            actualizarTotal()
        }
        fila.root.setOnClickListener { fila.checkModificador.toggle() }

        return fila.root
    }

    /**
     * Precio unitario del plato = precio de carta + recargo de la seleccion.
     * Misma regla que el backend: el recargo se suma POR UNIDAD antes de
     * multiplicar por la cantidad en el carrito.
     */
    private fun actualizarTotal() {
        val total: BigDecimal = producto.precio
            .add(Modificador.recargoTotal(seleccionados.toList()))
        vista.textoTotalModificadores.text = formatearMoneda(total)
    }

    /**
     * Si la carta trae muchos modificadores, la lista se vuelve scrolleable y
     * el dialogo conserva visibles el total y los botones. Con pocos
     * modificadores se mantiene wrap_content para no dejar espacio vacio.
     */
    private fun limitarAltoLista() {
        vista.scrollModificadores.post {
            val altoMaximo =
                (activity.resources.displayMetrics.heightPixels * FRACCION_ALTO_LISTA).toInt()
            if (vista.scrollModificadores.height > altoMaximo) {
                vista.scrollModificadores.layoutParams =
                    vista.scrollModificadores.layoutParams.apply { height = altoMaximo }
            }
        }
    }

    private companion object {
        const val ANCHO_MAXIMO_DP = 480
        const val FRACCION_ANCHO = 0.92f
        const val FRACCION_ALTO_LISTA = 0.42f
    }
}
