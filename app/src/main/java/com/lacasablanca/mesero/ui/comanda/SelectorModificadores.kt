package com.lacasablanca.mesero.ui.comanda

import android.app.Activity
import com.lacasablanca.mesero.domain.model.Modificador
import com.lacasablanca.mesero.domain.model.Producto

/**
 * Orquesta el selector de modificadores de la comanda (PRB-92).
 *
 * Mantiene un solo dialogo abierto y decide si corresponde abrirlo: los
 * platos sin modificadores siguen su accion directa (agregar o aumentar), y
 * los que si tienen abren [ModificadoresDialog] antes de tocar el carrito.
 * La Activity aporta el callback que delega en el ViewModel.
 */
class SelectorModificadores(
    private val activity: Activity,
    private val alConfirmar: (Producto, List<Modificador>) -> Unit
) {

    private var dialogo: ModificadoresDialog? = null

    /**
     * Abre el selector si el plato tiene modificadores; si no, ejecuta
     * [accionDirecta] (alta o incremento sin seleccion).
     */
    fun abrirSiCorresponde(producto: Producto, accionDirecta: () -> Unit) {
        if (producto.modificadores.isEmpty()) {
            accionDirecta()
            return
        }

        cerrar()
        dialogo = ModificadoresDialog(activity, producto) { seleccionados ->
            alConfirmar(producto, seleccionados)
        }.also { it.mostrar() }
    }

    /** Cierra el dialogo si sigue abierto (salida de la pantalla). */
    fun cerrar() {
        dialogo?.cerrar()
        dialogo = null
    }
}
