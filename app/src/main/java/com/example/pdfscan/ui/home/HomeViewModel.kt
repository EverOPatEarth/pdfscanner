package com.example.pdfscan.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfscan.data.SettingsRepository
import com.example.pdfscan.domain.Compression
import com.example.pdfscan.domain.PdfEntry
import com.example.pdfscan.domain.PdfRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

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

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { viewModelScope.launch { repo.refresh() } }

    fun delete(entry: PdfEntry) { viewModelScope.launch { repo.delete(entry) } }
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)

    fun compress(entry: PdfEntry, level: Compression) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val result = repo.compress(entry, level)
                if (result.sizeBytes < entry.sizeBytes) {
                    val saved = 100 - (result.sizeBytes * 100 / entry.sizeBytes.coerceAtLeast(1))
                    _messages.emit(
                        "Compressed: ${human(entry.sizeBytes)} → ${human(result.sizeBytes)} ($saved% smaller)"
                    )
                } else {
                    repo.delete(result)   // not smaller: keep the list clean
                    _messages.emit("No size reduction. Try the \"Smallest\" level.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Compression failed")
            } finally {
                _busy.value = false
            }
        }
    }

    private fun human(bytes: Long): String =
        if (bytes >= 1_048_576) String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        else String.format(Locale.US, "%d KB", bytes / 1024)
}
