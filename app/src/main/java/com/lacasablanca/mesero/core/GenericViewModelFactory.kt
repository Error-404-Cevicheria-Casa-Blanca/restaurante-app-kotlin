package com.lacasablanca.mesero.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Crea ViewModels con dependencias manuales (AppContainer), sin Hilt. */
class GenericViewModelFactory<T : ViewModel>(private val creador: () -> T) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creador() as VM
}
