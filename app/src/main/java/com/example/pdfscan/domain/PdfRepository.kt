package com.example.pdfscan.domain

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface PdfRepository {
    val documents: Flow<List<PdfEntry>>
    suspend fun refresh()
    suspend fun export(pages: List<Uri>, baseName: String): PdfEntry
    suspend fun delete(entry: PdfEntry)
}
