package com.example.ui.license

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.license.LicenseRepository
import com.example.data.license.LicenseSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LicenseViewModel(private val repository: LicenseRepository) : ViewModel() {
  val snapshot: StateFlow<LicenseSnapshot> = repository.snapshot.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5_000), LicenseSnapshot("", "", "", 0L, 0L, 0L)
  )

  fun initialize() {
    viewModelScope.launch { repository.initialize() }
  }

  class Factory(private val repository: LicenseRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LicenseViewModel(repository) as T
  }
}
