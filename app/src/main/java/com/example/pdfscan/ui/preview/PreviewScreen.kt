package com.example.pdfscan.ui.preview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Columnpackage com.example.pdfscan.ui.preview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pdfscan.domain.PdfEntry
import com.example.pdfscan.scanner.rememberDocumentScanner
import com.example.pdfscan.ui.sharePdf
import com.example.pdfscan.ui.theme.ReviewCanvas
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(vm: ScanSessionViewModel, onExit: () -> Unit, onOpen: (PdfEntry) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pager = rememberPagerState { state.pages.size }
    var confirmDiscard by remember { mutableStateOf(false) }
    val done = state.export as? ExportState.Done

    val addPages = rememberDocumentScanner(
        onPages = vm::addPages,
        onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
    )

    // Jump to the newest page after "Add page"
    var lastCount by remember { mutableIntStateOf(state.pages.size) }
    LaunchedEffect(state.pages.size) {
        if (state.pages.size > lastCount) pager.animateScrollToPage(state.pages.lastIndex)
        lastCount = state.pages.size
    }
    // Empty session (all pages deleted, or process-death restore) -> leave
    LaunchedEffect(state.pages.isEmpty()) { if (state.pages.isEmpty()) onExit() }

    (state.export as? ExportState.Error)?.let { err ->
        LaunchedEffect(err) { snackbar.showSnackbar(err.message); vm.dismissError() }
    }

    val requestExit = { if (done != null) onExit() else confirmDiscard = true }
    BackHandler(onBack = requestExit)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (done != null) "Saved" else "Review", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Page ${pager.currentPage + 1} of ${state.pages.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestExit) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            ActionBar(
                state = state,
                page = pager.currentPage,
                onRename = vm::rename,
                onAdd = addPages,
                onMove = { d ->
                    val from = pager.currentPage
                    vm.move(from, d)
                    scope.launch { pager.animateScrollToPage(from + d) }
                },
                onDelete = { vm.delete(pager.currentPage) },
                onSave = vm::export,
                onOpen = { done?.let { onOpen(it.entry) } },
                onShare = { done?.let { context.sharePdf(it.entry) } },
                onDone = onExit,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(ReviewCanvas)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { state.pages.getOrNull(it)?.id ?: it },
            ) { index ->
                AsyncImage(
                    model = state.pages.getOrNull(index)?.uri,
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard scan?") },
            text = { Text("These pages haven't been saved as a PDF.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onExit() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun ActionBar(
    state: PreviewUiState, page: Int,
    onRename: (String) -> Unit, onAdd: () -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit,
    onSave: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit, onDone: () -> Unit,
) {
    val saved = state.export is ExportState.Done
    val working = state.export is ExportState.Working

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (!saved) {
                OutlinedTextField(
                    value = state.fileName, onValueChange = onRename,
                    singleLine = true, enabled = !working,
                    label = { Text("File name") }, suffix = { Text(".pdf") },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!saved) {
                    IconButton(onClick = onAdd, enabled = !working) {
                        Icon(Icons.Rounded.AddPhotoAlternate, "Add page")
                    }
                    IconButton(onClick = { onMove(-1) }, enabled = !working && page > 0) {
                        Icon(Icons.Rounded.ChevronLeft, "Move earlier")
                    }
                    IconButton(onClick = { onMove(1) }, enabled = !working && page < state.pages.lastIndex) {
                        Icon(Icons.Rounded.ChevronRight, "Move later")
                    }
                    IconButton(onClick = onDelete, enabled = !working) {
                        Icon(Icons.Rounded.Delete, "Delete page")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onSave, enabled = !working && state.fileName.isNotBlank()) {
                        if (working) CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current,
                        )
                        else Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (working) "Saving…" else "Save PDF")
                    }
                } else {
                    TextButton(onClick = onDone) { Text("Done") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("View")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onShare) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Share")
                    }
                }
            }
        }
    }
}

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pdfscan.scanner.rememberDocumentScanner
import com.example.pdfscan.ui.openPdf
import com.example.pdfscan.ui.sharePdf
import com.example.pdfscan.ui.theme.ReviewCanvas
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(vm: ScanSessionViewModel, onExit: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pager = rememberPagerState { state.pages.size }
    var confirmDiscard by remember { mutableStateOf(false) }
    val done = state.export as? ExportState.Done

    val addPages = rememberDocumentScanner(
        onPages = vm::addPages,
        onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
    )

    // Jump to the newest page after "Add page"
    var lastCount by remember { mutableIntStateOf(state.pages.size) }
    LaunchedEffect(state.pages.size) {
        if (state.pages.size > lastCount) pager.animateScrollToPage(state.pages.lastIndex)
        lastCount = state.pages.size
    }
    // Empty session (all pages deleted, or process-death restore) -> leave
    LaunchedEffect(state.pages.isEmpty()) { if (state.pages.isEmpty()) onExit() }

    (state.export as? ExportState.Error)?.let { err ->
        LaunchedEffect(err) { snackbar.showSnackbar(err.message); vm.dismissError() }
    }

    val requestExit = { if (done != null) onExit() else confirmDiscard = true }
    BackHandler(onBack = requestExit)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (done != null) "Saved" else "Review", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Page ${pager.currentPage + 1} of ${state.pages.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestExit) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            ActionBar(
                state = state,
                page = pager.currentPage,
                onRename = vm::rename,
                onAdd = addPages,
                onMove = { d ->
                    val from = pager.currentPage
                    vm.move(from, d)
                    scope.launch { pager.animateScrollToPage(from + d) }
                },
                onDelete = { vm.delete(pager.currentPage) },
                onSave = vm::export,
                onOpen = { done?.let { context.openPdf(it.entry) } },
                onShare = { done?.let { context.sharePdf(it.entry) } },
                onDone = onExit,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(ReviewCanvas)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { state.pages.getOrNull(it)?.id ?: it },
            ) { index ->
                AsyncImage(
                    model = state.pages.getOrNull(index)?.uri,
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard scan?") },
            text = { Text("These pages haven't been saved as a PDF.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onExit() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun ActionBar(
    state: PreviewUiState, page: Int,
    onRename: (String) -> Unit, onAdd: () -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit,
    onSave: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit, onDone: () -> Unit,
) {
    val saved = state.export is ExportState.Done
    val working = state.export is ExportState.Working

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (!saved) {
                OutlinedTextField(
                    value = state.fileName, onValueChange = onRename,
                    singleLine = true, enabled = !working,
                    label = { Text("File name") }, suffix = { Text(".pdf") },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!saved) {
                    IconButton(onClick = onAdd, enabled = !working) {
                        Icon(Icons.Rounded.AddPhotoAlternate, "Add page")
                    }
                    IconButton(onClick = { onMove(-1) }, enabled = !working && page > 0) {
                        Icon(Icons.Rounded.ChevronLeft, "Move earlier")
                    }
                    IconButton(onClick = { onMove(1) }, enabled = !working && page < state.pages.lastIndex) {
                        Icon(Icons.Rounded.ChevronRight, "Move later")
                    }
                    IconButton(onClick = onDelete, enabled = !working) {
                        Icon(Icons.Rounded.Delete, "Delete page")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onSave, enabled = !working && state.fileName.isNotBlank()) {
                        if (working) CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current,
                        )
                        else Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (working) "Saving…" else "Save PDF")
                    }
                } else {
                    TextButton(onClick = onDone) { Text("Done") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Open")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onShare) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Share")
                    }
                }
            }
        }
    }
}
