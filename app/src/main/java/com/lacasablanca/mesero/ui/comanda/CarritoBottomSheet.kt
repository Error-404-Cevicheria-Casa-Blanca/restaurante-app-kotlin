package com.lacasablanca.mesero.ui.comanda

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.BottomSheetCarritoBinding
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.ui.common.formatearMoneda
import kotlinx.coroutines.launch

/**
 * Carrito en bottom sheet (rediseno PRB-38).
 *
 * Comparte el MISMO [CarritoViewModel] de [ComandaActivity] (mismo
 * ViewModelStoreOwner), asi que no hay dos carritos ni estado duplicado.
 * La Activity sigue siendo la unica que consume [CarritoViewModel.eventos]:
 * al confirmar, el envio exitoso lo maneja ella (cierra el sheet y muestra
 * el dialogo de exito).
 */
class CarritoBottomSheet : BottomSheetDialogFragment() {

    /** La Activity implementa esto para abrir el dialogo de nota del plato. */
    interface Listener {
        fun onEditarNotas(item: ItemCarrito)
    }

    private var _binding: BottomSheetCarritoBinding? = null
    private val binding get() = _binding!!

    private val mesaId: Long get() = requireArguments().getLong(ARG_MESA_ID)

    private val viewModel: CarritoViewModel by lazy {
        val contenedor = (requireActivity().application as MeseroApp).container
        ViewModelProvider(
            requireActivity(),
            GenericViewModelFactory {
                CarritoViewModel(
                    catalogoRepository = contenedor.catalogoRepository,
                    pedidoRepository = contenedor.pedidoRepository
                )
            }
        )[CarritoViewModel::class.java]
    }

    private lateinit var carritoAdapter: CarritoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCarritoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        carritoAdapter = CarritoAdapter(
            alAumentar = viewModel::aumentarCantidad,
            alDisminuir = viewModel::disminuirCantidad,
            alEliminar = viewModel::eliminarProducto,
            alEditarNotas = { item ->
                (activity as? Listener)?.onEditarNotas(item)
            }
        )

        binding.listaCarrito.layoutManager = LinearLayoutManager(requireContext())
        binding.listaCarrito.adapter = carritoAdapter

        binding.btnCerrarCarrito.setOnClickListener { dismiss() }
        binding.btnConfirmar.setOnClickListener { viewModel.enviarPedido(mesaId) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::render)
            }
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
        binding.listaCarrito.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(estado: CarritoUiState) {
        carritoAdapter.submitList(estado.items)

        val cantidad = resources.getQuantityString(
            R.plurals.comanda_contador_items,
            estado.cantidadItems,
            estado.cantidadItems
        )
        binding.textoSheetContador.text =
            getString(R.string.carrito_sheet_contador, cantidad)

        binding.listaCarrito.isVisible = !estado.carritoVacio
        binding.textoCarritoVacio.isVisible = estado.carritoVacio

        binding.textoSubtotal.text = formatearMoneda(estado.subtotal)
        binding.textoIgv.text = formatearMoneda(estado.igv)
        binding.textoTotal.text = formatearMoneda(estado.total)

        binding.btnConfirmar.isEnabled = !estado.carritoVacio && !estado.enviando
        binding.btnConfirmar.text = if (estado.enviando) {
            getString(R.string.comanda_enviando)
        } else {
            getString(R.string.comanda_confirmar)
        }
        binding.btnConfirmar.contentDescription = when {
            estado.enviando -> getString(R.string.comanda_enviando)
            estado.carritoVacio -> getString(R.string.comanda_confirmar_deshabilitado)
            else -> getString(R.string.comanda_confirmar)
        }
    }

    companion object {
        const val TAG = "CarritoBottomSheet"

        private const val ARG_MESA_ID = "arg_mesa_id"
        private const val ANCHO_TABLET_DP = 600
        private const val ANCHO_TABLET_FRACCION = 0.6f
        private const val DIM_AMOUNT = 0.45f

        fun nueva(mesaId: Long): CarritoBottomSheet = CarritoBottomSheet().apply {
            arguments = Bundle().apply { putLong(ARG_MESA_ID, mesaId) }
        }
    }
}
