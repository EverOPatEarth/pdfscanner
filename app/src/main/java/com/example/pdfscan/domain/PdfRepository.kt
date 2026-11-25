package com.example.pdfscan.domain

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface PdfRepository {
    val documents: Flow<List<PdfEntry>>
    suspend fun refresh()
    suspend fun export(pages: List<Uri>, baseName: String, options: PdfOptions = PdfOptions()): PdfEntry
    /** Creates a smaller copy of [entry] ("<name> (compressed).pdf"). Original is kept. */
    suspend fun compress(entry: PdfEntry, level: Compression): PdfEntry
    suspend fun delete(entry: PdfEntry)
}
