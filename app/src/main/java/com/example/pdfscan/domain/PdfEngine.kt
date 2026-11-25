package com.example.pdfscan.domain

import android.net.Uri
import java.io.File

/** Size/quality presets. Lower edge + lower JPEG quality = smaller PDF. */
enum class Compression(val label: String, val maxEdgePx: Int, val jpegQuality: Int, val hint: String) {
    HIGH("High quality", 2400, 90, "Sharpest, biggest file"),
    BALANCED("Balanced", 1800, 75, "Good for most documents"),
    SMALL("Smallest", 1200, 55, "Best for email and uploads"),
}

data class PdfOptions(
    val compression: Compression = Compression.BALANCED,
    val cleanStains: Boolean = false,   // remove stains, shadows and yellow paper tint
    val ocr: Boolean = false,           // add an invisible, searchable text layer
)

/** Seam for PDF generation. Swap the implementation in AppContainer. */
interface PdfEngine {
    /** Writes [pages] (in order) to [output]. Throws on failure; caller cleans up. */
    suspend fun create(pages: List<Uri>, output: File, options: PdfOptions = PdfOptions())
}
