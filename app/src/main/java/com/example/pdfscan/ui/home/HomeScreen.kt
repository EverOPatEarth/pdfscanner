package com.example.pdfscan.ui.home

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdfscan.domain.PdfEntry
import com.example.pdfscan.scanner.rememberDocumentScanner
import com.example.pdfscan.ui.openPdf
import com.example.pdfscan.ui.sharePdf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onScanned: (List<Uri>) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dynamic by vm.dynamicColor.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }

    val scan = rememberDocumentScanner(
        onPages = onScanned,
        onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Scanner", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Dynamic color") },
                            trailingIcon = { Switch(checked = dynamic, onCheckedChange = null) },
                            onClick = { vm.setDynamicColor(!dynamic) },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = scan,
                icon = { Icon(Icons.Rounded.DocumentScanner, null) },
                text = { Text("Scan") },
            )
        },
    ) { padding ->
        when (val s = state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            HomeUiState.Empty -> EmptyState(Modifier.fillMaxSize().padding(padding))
            is HomeUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(s.items, key = { it.file.name }) { entry ->
                    PdfRow(
                        entry,
                        onOpen = { context.openPdf(entry) },
                        onShare = { context.sharePdf(entry) },
                        onDelete = { vm.delete(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfRow(entry: PdfEntry, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(colors.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PictureAsPdf, null, tint = colors.onPrimaryContainer) }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        entry.modifiedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
            }
            Text(
                Formatter.formatShortFileSize(context, entry.sizeBytes),
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                modifier = Modifier
                    .background(colors.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.DocumentScanner, null, Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("No PDFs yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap Scan to capture or import pages. Your PDFs will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
