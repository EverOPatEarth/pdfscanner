package com.example.pdfscan.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper over android.graphics.pdf.PdfRenderer (built into Android, no dependency).
 * PdfRenderer allows only ONE open page at a time, so renders are serialized with a mutex.
 * Seam: replace with another renderer (e.g. PDFium) without touching the viewer UI.
 */
class PdfDocumentHandle(file: File) {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** width / height of every page, so the list can reserve the right space before rendering. */
    private val aspects: List<Float> = List(pageCount) { i ->
        renderer.openPage(i).use { it.width.toFloat() / it.height }
    }

    fun aspect(index: Int): Float = aspects.getOrElse(index) { 0.707f }

    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) throw CancellationException("Document closed")
            renderer.openPage(index).use { page ->
                val height = (widthPx * page.height / page.width.toFloat()).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    fun close() {
        runBlocking {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    renderer.close()
                    pfd.close()
                }
            }
        }
    }
}
