package com.example.pdfscan.domain

import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * A page in the current scan session.
 * [uri] is what is shown/exported; [originalUri] is the untouched scan (used to undo signatures).
 */
data class ScanPage(
    val uri: Uri,
    val id: String = UUID.randomUUID().toString(),
    val originalUri: Uri = uri,
) {
    val isSigned: Boolean get() = uri != originalUri
}

data class PdfEntry(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)
