package com.example.pdfscan.ui.preview

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfscan.data.SavedSignature
import com.example.pdfscan.data.SignatureStore
import com.example.pdfscan.domain.Compression
import com.example.pdfscan.domain.PageEditor
import com.example.pdfscan.domain.PdfEntry
import com.example.pdfscan.domain.PdfOptions
import com.example.pdfscan.domain.PdfRepository
import com.example.pdfscan.domain.ScanPage
import com.example.pdfscan.domain.SignaturePlacement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Done(val entry: PdfEntry) : ExportState
    data class Error(val message: String) : ExportState
}

data class PreviewUiState(
    val pages: List<ScanPage> = emptyList(),
    val fileName: String = defaultFileName(),
    val options: PdfOptions = PdfOptions(),
    val editing: Boolean = false,                 // signature being applied
    val export: ExportState = ExportState.Idle,
)

private fun defaultFileName() =
    "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

class ScanSessionViewModel(
    private val repo: PdfRepository,
    private val editor: PageEditor,
    private val signatures: SignatureStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    val signature: StateFlow<SavedSignature?> = signatures.current

    fun startSession(uris: List<Uri>) {
        _state.value = PreviewUiState(pages = uris.map { ScanPage(it) })
    }

    /** Extension point: any page source (ML Kit, CameraX, photo picker) ends up here. */
    fun addPages(uris: List<Uri>) = _state.update {
        it.copy(pages = it.pages + uris.map { u -> ScanPage(u) }, export = ExportState.Idle)
    }

    fun move(index: Int, delta: Int) = _state.update { s ->
        val to = index + delta
        if (index !in s.pages.indices || to !in s.pages.indices) s
        else s.copy(pages = s.pages.toMutableList().apply { add(to, removeAt(index)) })
    }

    fun delete(index: Int) = _state.update { s ->
        s.copy(pages = s.pages.filterIndexed { i, _ -> i != index })
    }

    fun rename(name: String) = _state.update { it.copy(fileName = name) }
    fun dismissError() = _state.update { it.copy(export = ExportState.Idle) }
    fun reset() { _state.value = PreviewUiState() }

    // ---- export options ----
    fun setCompression(c: Compression) = _state.update { it.copy(options = it.options.copy(compression = c)) }
    fun setCleanStains(on: Boolean) = _state.update { it.copy(options = it.options.copy(cleanStains = on)) }
    fun setOcr(on: Boolean) = _state.update { it.copy(options = it.options.copy(ocr = on)) }

    /** Low-res preview used by the pager when "Remove stains" is on. */
    suspend fun cleanedPreview(uri: Uri): Bitmap = editor.cleanedPreview(uri)

    // ---- e-signature ----
    fun saveSignature(bitmap: Bitmap) { viewModelScope.launch { signatures.save(bitmap) } }
    fun clearSignature() { viewModelScope.launch { signatures.clear() } }

    fun applySignature(index: Int, placement: SignaturePlacement) {
        val page = _state.value.pages.getOrNull(index) ?: return
        val sig = signatures.current.value?.file ?: return
        _state.update { it.copy(editing = true) }
        viewModelScope.launch {
            runCatching { editor.applySignature(page.uri, sig, placement) }
                .onSuccess { newUri ->
                    _state.update { s ->
                        s.copy(
                            editing = false,
                            pages = s.pages.map { if (it.id == page.id) it.copy(uri = newUri) else it },
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update {
                        it.copy(editing = false, export = ExportState.Error(e.message ?: "Could not add signature"))
                    }
                }
        }
    }

    fun removeSignature(index: Int) = _state.update { s ->
        val page = s.pages.getOrNull(index) ?: return@update s
        s.copy(pages = s.pages.map { if (it.id == page.id) it.copy(uri = it.originalUri) else it })
    }

    fun export() {
        val s = _state.value
        if (s.export is ExportState.Working || s.editing || s.pages.isEmpty()) return
        _state.update { it.copy(export = ExportState.Working) }
        viewModelScope.launch {
            runCatching { repo.export(s.pages.map { it.uri }, s.fileName, s.options) }
                .onSuccess { entry -> _state.update { it.copy(export = ExportState.Done(entry)) } }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(export = ExportState.Error(e.message ?: "Export failed")) }
                }
        }
    }
}
