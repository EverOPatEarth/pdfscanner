package com.example.pdfscan.domain

import android.net.Uri
import java.io.File

/** Options for creating a PDF from scanner-produced JPEG pages. */
data class PdfOptions(
    /** A4 page size is used while the source JPEG pixel dimensions are preserved. */
    val useA4Page: Boolean = true,
)

/** Abstraction for creating PDF files from scanned page URIs. */
interface PdfEngine {
    /** Writes [pages] (in order) to [output]. Throws on failure; caller cleans up. */
    suspend fun create(pages: List<Uri>, output: File, options: PdfOptions = PdfOptions())
}
