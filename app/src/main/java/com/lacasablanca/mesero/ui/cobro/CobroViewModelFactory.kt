package com.lacasablanca.mesero.ui.cobro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.lacasablanca.mesero.data.repository.CobroRepository
import com.lacasablanca.mesero.domain.model.DestinoCobro

/**
 * Crea [CobroViewModel] con inyeccion manual y un [SavedStateHandle] real.
 * El handle conserva el `client_request_id` ante rotaciones y muerte del
 * proceso; sin el, el UUID se regeneraria y un reintento podria duplicar
 * el cobro.
 */
class CobroViewModelFactory(
    private val destino: DestinoCobro,
    private val cobroRepository: CobroRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>, extras: CreationExtras): VM =
        CobroViewModel(
            destino = destino,
            cobroRepository = cobroRepository,
            savedStateHandle = extras.createSavedStateHandle()
        ) as VM
}
