package com.example.pdfscan

import android.content.Context
import com.example.pdfscan.data.AndroidPageEditor
import com.example.pdfscan.data.JpegPdfEngine
import com.example.pdfscan.data.LocalPdfRepository
import com.example.pdfscan.data.PdfRasterizer
import com.example.pdfscan.data.SettingsRepository
import com.example.pdfscan.data.SignatureStore
import com.example.pdfscan.domain.PageEditor
import com.example.pdfscan.domain.PdfRepository
import java.io.File

/** The ONLY place that knows which implementations are used. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val settings = SettingsRepository(app)
    val signatures = SignatureStore(File(app.filesDir, "signatures"))
    val pageEditor: PageEditor = AndroidPageEditor(app.contentResolver, app.cacheDir)
    val pdfRepository: PdfRepository = LocalPdfRepository(
        dir = File(app.filesDir, "pdfs"),
        engine = JpegPdfEngine(app.contentResolver),
        rasterizer = PdfRasterizer(app.cacheDir),
    )
}
