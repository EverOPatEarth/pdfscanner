package com.example.pdfscan.scanner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private val scannerOptions = GmsDocumentScannerOptions.Builder()
    .setGalleryImportAllowed(true)                                   // camera + gallery import
    .setPageLimit(20)
    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)  // we build the PDF ourselves
    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)     // crop, rotate, filters
    .build()

/**
 * Returns a lambda that launches the scanner. Result pages arrive as JPEG Uris via [onPages].
 * Swap point: a CameraX/photo-picker source only needs to call the same [onPages] consumer.
 */
@Composable
fun rememberDocumentScanner(
    onPages: (List<Uri>) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val latestOnPages by rememberUpdatedState(onPages)
    val latestOnError by rememberUpdatedState(onError)
    val client = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages.orEmpty().map { it.imageUri }
            if (uris.isNotEmpty()) latestOnPages(uris)
        } // RESULT_CANCELED: user backed out, nothing to do
    }

    return remember(launcher, client) {
        {
            val activity = context.findActivity()
            if (activity == null) {
                latestOnError("Scanner unavailable")
            } else {
                client.getStartScanIntent(activity)
                    .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                    .addOnFailureListener {
                        latestOnError(it.localizedMessage ?: "Scanner unavailable. Check Google Play services.")
                    }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
