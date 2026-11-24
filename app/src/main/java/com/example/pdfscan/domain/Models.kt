package com.example.pdfscan.domain

import android.net.Uri
import java.io.File
import java.util.UUID

data class ScanPage(val uri: Uri, val id: String = UUID.randomUUID().toString())

data class PdfEntry(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)
