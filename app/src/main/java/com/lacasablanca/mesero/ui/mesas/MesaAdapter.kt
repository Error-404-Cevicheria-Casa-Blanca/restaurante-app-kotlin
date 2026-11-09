package com.lacasablanca.mesero.ui.mesas

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.databinding.ItemMesaBinding
import com.lacasablanca.mesero.domain.model.Mesa
import com.lacasablanca.mesero.ui.common.colorRes
import com.lacasablanca.mesero.ui.common.etiquetaRes
import com.lacasablanca.mesero.ui.common.iconoRes

/**
 * Cuadricula de mesas. Solo pinta; los eventos los reenvia al Fragment.
 * PRB-93: [alAccionar] abre el menu de dividir/fusionar/transferir desde el
 * boton de la tarjeta o con un long press.
 */
class MesaAdapter(
    private val alTocar: (Mesa) -> Unit,
    private val alAccionar: (Mesa) -> Unit
) : ListAdapter<Mesa, MesaAdapter.MesaViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MesaViewHolder {
        val binding = ItemMesaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MesaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MesaViewHolder, position: Int) {
        holder.pintar(getItem(position), alTocar, alAccionar)
    }

    class MesaViewHolder(private val binding: ItemMesaBinding) : RecyclerView.ViewHolder(binding.root) {

        fun pintar(mesa: Mesa, alTocar: (Mesa) -> Unit, alAccionar: (Mesa) -> Unit) {
            val contexto = binding.root.context
            val color = ContextCompat.getColor(contexto, mesa.estado.colorRes())
            val etiqueta = contexto.getString(mesa.estado.etiquetaRes())
            val capacidad = contexto.resources.getQuantityString(
                R.plurals.mesa_capacidad,
                mesa.capacidad,
                mesa.capacidad
            )

            binding.barraEstado.setBackgroundColor(color)

            binding.textoMesa.text = contexto.getString(R.string.mesa_titulo, mesa.numero)
            binding.textoCapacidad.text = capacidad

            binding.iconoEstado.setImageResource(mesa.estado.iconoRes())
            binding.iconoEstado.imageTintList = ColorStateList.valueOf(color)
            binding.iconoEstado.contentDescription = etiqueta

            binding.textoEstado.text = etiqueta
            // Texto oscuro sobre fondo tintado: garantiza contraste AA (el color va en barra + icono).
            binding.textoEstado.setTextColor(ContextCompat.getColor(contexto, R.color.texto_principal))
            binding.etiquetaEstado.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, ALPHA_ETIQUETA))

            binding.cardMesa.contentDescription = contexto.getString(
                R.string.mesa_descripcion,
                mesa.numero,
                etiqueta,
                capacidad
            )
            binding.cardMesa.setOnClickListener { alTocar(mesa) }
            binding.cardMesa.setOnLongClickListener {
                alAccionar(mesa)
                true
            }
            binding.btnAccionesMesa.contentDescription = contexto.getString(
                R.string.cd_acciones_mesa,
                mesa.numero
            )
            binding.btnAccionesMesa.setOnClickListener { alAccionar(mesa) }
        }
    }

    private companion object {
        const val ALPHA_ETIQUETA = 32
    }
}

private val DIFF = object : DiffUtil.ItemCallback<Mesa>() {
    override fun areItemsTheSame(anterior: Mesa, actual: Mesa): Boolean = anterior.id == actual.id
    override fun areContentsTheSame(anterior: Mesa, actual: Mesa): Boolean = anterior == actual
}
