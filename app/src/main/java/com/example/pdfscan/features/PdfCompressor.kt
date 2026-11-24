package com.example.pdfscan.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

enum class CompressionPreset(val maxEdge: Int, val jpegQuality: Int) { HIGH(1100,55), MEDIUM(1600,72), LOW(2200,86) }

/** Raster recompression for scanned/image PDFs. Text/vector layers in the source PDF are rasterized. */
object PdfCompressor {
    suspend fun compress(input: File, output: File, preset: CompressionPreset): File = withContext(Dispatchers.IO) {
        val descriptor = ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor); val pdf = PdfDocument()
        try {
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { sourcePage ->
                    val scale = min(1f, preset.maxEdge.toFloat() / maxOf(sourcePage.width, sourcePage.height))
                    val width = (sourcePage.width * scale).toInt().coerceAtLeast(1)
                    val height = (sourcePage.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
                    sourcePage.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    val compressedBytes = ByteArrayOutputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, stream)
                        stream.toByteArray()
                    }
                    val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                        ?: error("Could not encode compressed page ${i + 1}")
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(width,height,i+1).create())
                    page.canvas.drawBitmap(compressedBitmap,null,RectF(0f,0f,width.toFloat(),height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                    pdf.finishPage(page); compressedBitmap.recycle(); bitmap.recycle()
                }
            }
            output.outputStream().use { pdf.writeTo(it) }
            output
        } finally { pdf.close(); renderer.close(); descriptor.close() }
    }
}
