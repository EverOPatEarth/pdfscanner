package com.example.pdfscan.domain

import android.net.Uri
import java.io.File

data class PdfOptions(
    val maxEdgePx: Int = 2000,   // ~150-175 dpi on A4; lower = smaller file
)

/** Seam for compression / OCR text layer / better PDF libraries. */
interface PdfEngine {
    /** Writes [pages] (in order) to [output]. Throws on failure; caller cleans up. */
    suspend fun create(pages: List<Uri>, output: File, options: PdfOptions = PdfOptions())
}
