package com.lacasablanca.mesero.ui.comanda

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.BottomSheetCarritoBinding
import com.lacasablanca.mesero.domain.model.ItemCarrito
import com.lacasablanca.mesero.ui.common.aparecerConDesplazamiento
import com.lacasablanca.mesero.ui.common.formatearMoneda
import kotlinx.coroutines.launch

/**
 * Carrito en bottom sheet (rediseno PRB-38).
 *
 * Comparte el MISMO [CarritoViewModel] de [ComandaFragment] (mismo
 * ViewModelStoreOwner de la Activity), asi que no hay dos carritos ni estado
 * duplicado.
 * El ComandaFragment sigue siendo el unico que consume
 * [CarritoViewModel.eventos]: al confirmar, el envio exitoso lo maneja el
 * fragmento (cierra el sheet y muestra el dialogo de exito).
 */
class CarritoBottomSheet : BottomSheetDialogFragment() {

    /** La Activity implementa esto para abrir los dialogos de nota y anulacion. */
    interface Listener {
        fun onEditarNotas(item: ItemCarrito)

        /** PRB-77: confirmar la anulacion de un plato ya enviado a cocina. */
        fun onAnular(item: ItemCarrito)
    }

    private var _binding: BottomSheetCarritoBinding? = null
    private val binding get() = _binding!!

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
            alAumentar = { item -> viewModel.aumentarCantidad(item) },
            alDisminuir = { item -> viewModel.disminuirCantidad(item) },
            alEliminar = { item -> viewModel.eliminarProducto(item) },
            alEditarNotas = { item ->
                (parentFragment as? Listener)?.onEditarNotas(item)
            },
            alAnular = { item ->
                (parentFragment as? Listener)?.onAnular(item)
            }
        )

        binding.listaCarrito.layoutManager = LinearLayoutManager(requireContext())
        binding.listaCarrito.adapter = carritoAdapter

        binding.btnCerrarCarrito.setOnClickListener { dismiss() }
        // El ViewModel conoce el destino (mesa o para llevar); el sheet no
        // necesita distinguirlo (PRB-94).
        binding.btnConfirmar.setOnClickListener { viewModel.enviarPedido() }
        binding.btnAgregarMas.setOnClickListener { dismiss() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect(::render)
            }
        }

        // Aparicion sutil al abrir: el sheet ya sube; el contenido hace fade-in.
        binding.root.post {
            _binding?.root?.aparecerConDesplazamiento(
                desplazamientoDp = DESPLAZAMIENTO_APERTURA_DP,
                duracionMs = DURACION_APERTURA_MS
            )
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

    /**
     * Aviso dentro del sheet. El Snackbar de la Activity queda detras de la
     * ventana del bottom sheet, asi que el aviso de "Plato anulado" se pinta
     * aqui para que el mesero lo vea (PRB-77).
     */
    fun mostrarSnackbar(texto: String) {
        val vista = _binding?.root ?: return
        val snackbar = Snackbar.make(vista, texto, Snackbar.LENGTH_SHORT)
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        snackbar.show()
        snackbar.view.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_snackbar)
    }

    private fun render(estado: CarritoUiState) {
        carritoAdapter.submitList(estado.lineas)
        renderTitulo(estado)
        renderVacio(estado)
        renderTotales(estado)
        renderConfirmar(estado)
        binding.btnAgregarMas.isVisible = estado.modoLectura
    }

    private fun renderTitulo(estado: CarritoUiState) {
        val cantidad = resources.getQuantityString(
            R.plurals.comanda_contador_items,
            estado.cantidadItems,
            estado.cantidadItems
        )
        binding.textoSheetTitulo.text = when {
            estado.esParaLlevar -> getString(R.string.carrito_sheet_titulo_para_llevar)
            estado.modoLectura -> getString(R.string.carrito_sheet_titulo_lectura)
            else -> getString(R.string.carrito_sheet_titulo)
        }
        binding.textoSheetContador.text = if (estado.modoLectura) {
            getString(R.string.carrito_sheet_contador_lectura, cantidad)
        } else {
            getString(R.string.carrito_sheet_contador, cantidad)
        }
    }

    private fun renderVacio(estado: CarritoUiState) {
        binding.listaCarrito.isVisible = !estado.sinLineas
        binding.grupoCarritoVacio.isVisible = estado.sinLineas
        binding.textoCarritoVacio.text = if (estado.modoLectura) {
            getString(R.string.carrito_vacio_lectura)
        } else {
            getString(R.string.carrito_vacio_mensaje)
        }
    }

    private fun renderTotales(estado: CarritoUiState) {
        binding.textoSubtotal.text = formatearMoneda(estado.subtotal)
        binding.textoIgv.text = formatearMoneda(estado.igv)
        binding.textoTotal.text = formatearMoneda(estado.total)
    }

    private fun renderConfirmar(estado: CarritoUiState) {
        // En modo lectura solo se habilitan las lineas NUEVAS: lo ya pedido no
        // vuelve a viajar en el POST.
        val hayNuevos = estado.items.isNotEmpty()
        binding.btnConfirmar.isEnabled = hayNuevos && !estado.enviando
        binding.btnConfirmar.text = when {
            estado.enviando -> getString(R.string.comanda_enviando)
            estado.modoLectura -> getString(R.string.comanda_confirmar_ampliacion)
            else -> getString(R.string.comanda_confirmar)
        }
        binding.btnConfirmar.contentDescription = cuandoConfirmar(estado, hayNuevos)
    }

    private fun cuandoConfirmar(estado: CarritoUiState, hayNuevos: Boolean): String = when {
        estado.enviando -> getString(R.string.comanda_enviando)
        hayNuevos -> if (estado.modoLectura) {
            getString(R.string.comanda_confirmar_ampliacion)
        } else {
            getString(R.string.comanda_confirmar)
        }

        estado.modoLectura -> getString(R.string.comanda_confirmar_ampliacion_deshabilitado)
        else -> getString(R.string.comanda_confirmar_deshabilitado)
    }

    companion object {
        const val TAG = "CarritoBottomSheet"

        private const val ARG_MESA_ID = "arg_mesa_id"
        private const val ANCHO_TABLET_DP = 600
        private const val ANCHO_TABLET_FRACCION = 0.6f
        private const val DIM_AMOUNT = 0.45f
        private const val DESPLAZAMIENTO_APERTURA_DP = 18f
        private const val DURACION_APERTURA_MS = 240L

        fun nueva(mesaId: Long): CarritoBottomSheet = CarritoBottomSheet().apply {
            arguments = Bundle().apply { putLong(ARG_MESA_ID, mesaId) }
        }
    }
}
