package com.example.pdfscan.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.pdfscan.domain.PdfEngine
import com.example.pdfscan.domain.PdfOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Zero-dependency engine using android.graphics.pdf.PdfDocument. */
class AndroidPdfEngine(private val resolver: ContentResolver) : PdfEngine {

    override suspend fun create(pages: List<Uri>, output: File, options: PdfOptions) =
        withContext(Dispatchers.IO) {
            require(pages.isNotEmpty()) { "No pages to export" }
            val pdf = PdfDocument()
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            try {
                pages.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    val bitmap = decode(uri, options.maxEdgePx)
                    try {
                        val landscape = bitmap.width > bitmap.height
                        val pw = if (landscape) A4_LONG else A4_SHORT
                        val ph = if (landscape) A4_SHORT else A4_LONG
                        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, index + 1).create())
                        val scale = min(pw / bitmap.width.toFloat(), ph / bitmap.height.toFloat())
                        val w = bitmap.width * scale
                        val h = bitmap.height * scale
                        val left = (pw - w) / 2f
                        val top = (ph - h) / 2f
                        page.canvas.drawColor(Color.WHITE)
                        page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), paint)
                        pdf.finishPage(page)
                    } finally {
                        bitmap.recycle()
                    }
                }
                output.outputStream().use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
        }

    private fun decode(uri: Uri, maxEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "Cannot read image: $uri" }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2

        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot decode image: $uri")

        val longest = max(raw.width, raw.height)
        if (longest <= maxEdge) return raw
        val f = maxEdge / longest.toFloat()
        return Bitmap.createScaledBitmap(raw, (raw.width * f).toInt(), (raw.height * f).toInt(), true)
            .also { if (it !== raw) raw.recycle() }
    }

    private companion object {
        const val A4_SHORT = 595   // PostScript points
        const val A4_LONG = 842
    }
}
