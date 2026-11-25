package com.example.pdfscan.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.pdfscan.domain.Compression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

class RasterResult(val uris: List<Uri>, val dir: File)

/** Renders an existing PDF to JPEG files so it can be re-encoded smaller by the PdfEngine. */
class PdfRasterizer(private val cacheDir: File) {

    suspend fun rasterize(pdf: File, level: Compression): RasterResult = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "raster/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val uris = ArrayList<Uri>()
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        currentCoroutineContext().ensureActive()
                        renderer.openPage(i).use { page ->
                            val scale = level.maxEdgePx / max(page.width, page.height).toFloat()
                            val bw = (page.width * scale).toInt().coerceAtLeast(1)
                            val bh = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val file = File(dir, "p$i.jpg")
                            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                            bmp.recycle()
                            uris += Uri.fromFile(file)
                        }
                    }
                }
            }
            RasterResult(uris, dir)
        } catch (t: Throwable) {
            dir.deleteRecursively()
            throw t
        }
    }
}
