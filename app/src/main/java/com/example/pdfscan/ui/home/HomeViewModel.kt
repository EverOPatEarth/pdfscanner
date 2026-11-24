package com.example.pdfscan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfscan.data.SettingsRepository
import com.example.pdfscan.domain.PdfEntry
import com.example.pdfscan.domain.PdfRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(val items: List<PdfEntry>) : HomeUiState
}

class HomeViewModel(
    private val repo: PdfRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = repo.documents
        .map { if (it.isEmpty()) HomeUiState.Empty else HomeUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor

    init { viewModelScope.launch { repo.refresh() } }

    fun delete(entry: PdfEntry) { viewModelScope.launch { repo.delete(entry) } }
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
}
