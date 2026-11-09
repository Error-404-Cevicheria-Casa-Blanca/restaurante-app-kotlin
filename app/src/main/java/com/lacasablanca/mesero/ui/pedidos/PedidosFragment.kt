package com.lacasablanca.mesero.ui.pedidos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.lacasablanca.mesero.MeseroApp
import com.lacasablanca.mesero.R
import com.lacasablanca.mesero.core.GenericViewModelFactory
import com.lacasablanca.mesero.databinding.FragmentPedidosBinding
import com.lacasablanca.mesero.ui.login.LoginActivity
import com.lacasablanca.mesero.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Pedidos activos del salon y sin mesa (PRB-88, Fase 6).
 *
 * - Consume `GET /api/v1/pedidos` (o el fake) via [PedidosViewModel].
 * - Se recarga al entrar al tab y con el boton Actualizar; sin polling.
 * - El boton Cobrar solo aparece en pedidos sin mesa y delega en
 *   [MainActivity.abrirCobroPedido], que abre [com.lacasablanca.mesero.ui.cobro.CobroFragment].
 */
class PedidosFragment : Fragment() {

    private var _binding: FragmentPedidosBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PedidosAdapter

    private val contenedor get() = (requireActivity().application as MeseroApp).container

    private val viewModel: PedidosViewModel by viewModels {
        GenericViewModelFactory {
            PedidosViewModel(contenedor.pedidoRepository, contenedor.authRepository)
        }
    }

    private var actualizandoFiltro = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PedidosAdapter { pedido -> viewModel.cobrar(pedido) }
        binding.recyclerPedidos.layoutManager = GridLayoutManager(
            requireContext(),
            resources.getInteger(R.integer.columnas_pedidos)
        )
        binding.recyclerPedidos.adapter = adapter

        binding.btnActualizarPedidos.setOnClickListener {
            viewModel.cargar(mostrarAviso = true)
        }
        binding.btnReintentarPedidos.setOnClickListener { viewModel.cargar() }
        binding.chipsPedidos.setOnCheckedStateChangeListener { _, ids ->
            if (actualizandoFiltro) return@setOnCheckedStateChangeListener
            viewModel.filtrar(
                when (ids.firstOrNull()) {
                    R.id.chipMesa -> FiltroPedidos.MESA
                    R.id.chipParaLlevar -> FiltroPedidos.PARA_LLEVAR
                    else -> FiltroPedidos.TODOS
                }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect(::render) }
                launch { viewModel.eventos.collect(::manejarEvento) }
            }
        }
    }

    /**
     * Recarga al entrar al tab o volver de CobroFragment. Es silenciosa:
     * conserva la lista ya pintada mientras consulta.
     */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) viewModel.recargarAlVolver()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render(estado: PedidosUiState) {
        binding.progresoPedidos.isVisible = estado.cargando
        binding.btnActualizarPedidos.isEnabled = !estado.cargando

        val hayError = estado.error != null && !estado.cargando
        val mostrarLista = !estado.cargando && !hayError && !estado.vacio
        binding.recyclerPedidos.isVisible = mostrarLista
        binding.grupoVacioPedidos.isVisible = !estado.cargando && !hayError && estado.vacio
        binding.grupoErrorPedidos.isVisible = hayError

        estado.error?.let { binding.textoErrorPedidos.text = getString(it.mensajeRes()) }
        adapter.submitList(estado.pedidosVisibles)
        sincronizarChips(estado.filtro)
    }

    private fun sincronizarChips(filtro: FiltroPedidos) {
        val id = when (filtro) {
            FiltroPedidos.TODOS -> R.id.chipTodos
            FiltroPedidos.MESA -> R.id.chipMesa
            FiltroPedidos.PARA_LLEVAR -> R.id.chipParaLlevar
        }
        if (binding.chipsPedidos.checkedChipId == id) return

        actualizandoFiltro = true
        binding.chipsPedidos.check(id)
        actualizandoFiltro = false
    }

    private fun manejarEvento(evento: PedidosEvento) {
        when (evento) {
            is PedidosEvento.IrACobroPedido -> {
                (requireActivity() as? MainActivity)?.abrirCobroPedido(
                    evento.pedidoId,
                    evento.clienteNombre
                )
            }

            PedidosEvento.SesionExpirada -> irALogin()
            PedidosEvento.Actualizados -> mostrarSnackbar(
                getString(R.string.pedidos_actualizados)
            )
        }
    }

    private fun irALogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    private fun mostrarSnackbar(texto: String) {
        val snackbar = Snackbar.make(binding.root, texto, Snackbar.LENGTH_SHORT)
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        snackbar.show()
        snackbar.view.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_snackbar)
    }

    @StringRes
    private fun ErrorPedidos.mensajeRes(): Int = when (this) {
        ErrorPedidos.RED -> R.string.error_red
        ErrorPedidos.SESION_EXPIRADA -> R.string.sesion_expirada
        ErrorPedidos.SERVIDOR -> R.string.pedidos_error
        ErrorPedidos.DESCONOCIDO -> R.string.pedidos_error
    }
}
