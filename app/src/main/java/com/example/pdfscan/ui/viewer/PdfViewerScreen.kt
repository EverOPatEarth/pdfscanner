package com.example.pdfscan.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.pdfscan.features.CompressionPreset
import com.example.pdfscan.features.PdfCompressor
import com.example.pdfscan.features.OcrLanguages
import com.example.pdfscan.features.OcrService
import kotlinx.coroutines.launch
import com.example.pdfscan.domain.PdfEntry
import com.example.pdfscan.ui.openPdf
import com.example.pdfscan.ui.sharePdf
import com.example.pdfscan.ui.theme.ReviewCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(file: File, onBack: () -> Unit, onPdfChanged: suspend () -> Unit = {}) {
    val context = LocalContext.current
    val entry = remember(file) {
        PdfEntry(file, file.nameWithoutExtension, file.length(), file.lastModified())
    }

    val result by produceState<Result<PdfDocumentHandle>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDocumentHandle(file) } }
    }
    val handle = result?.getOrNull()
    DisposableEffect(handle) { onDispose { handle?.close() } }

    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var compressionDialog by remember { mutableStateOf(false) }
    var ocrDialog by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var ocrBusy by remember { mutableStateOf(false) }
    var compressing by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf(CompressionPreset.MEDIUM) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = ReviewCanvas,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (handle != null) Text(
                            "Page $currentPage of ${handle.pageCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }, enabled = zoom > 1f) {
                        Icon(Icons.Rounded.ZoomOut, "Zoom out")
                    }
                    IconButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(3f) }, enabled = zoom < 3f) {
                        Icon(Icons.Rounded.ZoomIn, "Zoom in")
                    }
                    IconButton(onClick = {
                        val pdfHandle = handle ?: return@IconButton
                        if (ocrBusy) return@IconButton
                        ocrBusy = true
                        scope.launch {
                            try {
                                val bitmap = withContext(Dispatchers.IO) { pdfHandle.render((currentPage - 1).coerceAtLeast(0), 2200) }
                                try { ocrText = OcrService(context).recognize(bitmap, OcrLanguages.BOTH) }
                                finally { bitmap.recycle() }
                                ocrDialog = true
                            } catch (e: Exception) { snackbar.showSnackbar("OCR failed: ${e.message}") }
                            finally { ocrBusy = false }
                        }
                    }, enabled = handle != null && !ocrBusy) { Icon(Icons.Rounded.TextFields, if (ocrBusy) "Recognizing text" else "OCR current page") }
                    IconButton(onClick = { compressionDialog = true }, enabled = !compressing) { Icon(Icons.Rounded.Compress, "Compress PDF") }
                    IconButton(onClick = { context.sharePdf(entry) }) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton(onClick = { context.openPdf(entry) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in another app")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).background(ReviewCanvas)) {
            val viewport = maxWidth
            val density = LocalDensity.current
            when {
                result == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                handle == null -> Text(
                    "This PDF could not be opened.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> {
                    val widthPx = with(density) { (viewport * zoom - 24.dp).roundToPx() }.coerceIn(200, 3000)
                    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.width(viewport * zoom),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(handle.pageCount) { index ->
                                val bitmap by produceState<ImageBitmap?>(null, index, widthPx) {
                                    value = runCatching { handle.render(index, widthPx).asImageBitmap() }
                                        .getOrNull() ?: value
                                }
                                Box(
                                    Modifier.fillMaxWidth()
                                        .aspectRatio(handle.aspect(index))
                                        .background(Color.White)
                                ) {
                                    bitmap?.let {
                                        Image(
                                            it, "Page ${index + 1}",
                                            Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (ocrDialog && ocrText != null) {
        AlertDialog(
            onDismissRequest = { ocrDialog = false },
            title = { Text("Recognized text (English + Myanmar)") },
            text = { Text(ocrText?.ifBlank { "No text recognized." } ?: "No text recognized.", modifier = Modifier.height(300.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = {
                val clip = android.content.ClipData.newPlainText("OCR text", ocrText ?: "")
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                ocrDialog = false
            }) { Text("Copy") } },
            dismissButton = { TextButton(onClick = { ocrDialog = false }) { Text("Close") } },
        )
    }

    if (compressionDialog) {
        AlertDialog(
            onDismissRequest = { if (!compressing) compressionDialog = false },
            title = { Text("Compress PDF") },
            text = {
                Column {
                    Text("Choose a balance between file size and image quality.")
                    CompressionPreset.values().forEach { option ->
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = preset == option, onClick = { preset = option }, enabled = !compressing)
                            Column {
                                Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
                                Text("${option.maxEdge}px · JPEG quality ${option.jpegQuality}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (compressing) CircularProgressIndicator()
                }
            },
            confirmButton = {
                TextButton(enabled = !compressing, onClick = {
                    compressing = true
                    scope.launch {
                        try {
                            val output = File(file.parentFile, "${file.nameWithoutExtension}_compressed.pdf")
                            val result = PdfCompressor.compress(file, output, preset)
                            val newEntry = PdfEntry(result, result.nameWithoutExtension, result.length(), result.lastModified())
                            onPdfChanged()
                            compressionDialog = false
                            snackbar.showSnackbar("Compressed: ${file.length()/1024} KB → ${result.length()/1024} KB")
                            context.sharePdf(newEntry)
                        } catch (e: Exception) { snackbar.showSnackbar("Compression failed: ${e.message}") }
                        finally { compressing = false }
                    }
                }) { Text("Compress & Share") }
            },
            dismissButton = { TextButton(enabled = !compressing, onClick = { compressionDialog = false }) { Text("Cancel") } },
        )
    }

}
