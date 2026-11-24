package com.example.pdfscan

import android.content.Context
import com.example.pdfscan.data.AndroidPdfEngine
import com.example.pdfscan.data.LocalPdfRepository
import com.example.pdfscan.data.SettingsRepository
import com.example.pdfscan.domain.PdfRepository
import java.io.File

/** The ONLY place that knows which implementations are used. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val settings = SettingsRepository(app)
    val pdfRepository: PdfRepository =
        LocalPdfRepository(File(app.filesDir, "pdfs"), AndroidPdfEngine(app.contentResolver))
}
