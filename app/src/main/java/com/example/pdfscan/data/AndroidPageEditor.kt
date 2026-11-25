package com.example.pdfscan.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.example.pdfscan.domain.PageEditor
import com.example.pdfscan.domain.SignaturePlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AndroidPageEditor(
    private val resolver: ContentResolver,
    private val cacheDir: File,
) : PageEditor {

    override suspend fun cleanedPreview(page: Uri): Bitmap = withContext(Dispatchers.Default) {
        val src = resolver.decodeScaled(page, 1400)
        StainCleaner.clean(src).also { src.recycle() }
    }

    override suspend fun applySignature(page: Uri, signature: File, placement: SignaturePlacement): Uri =
        withContext(Dispatchers.IO) {
            val decoded = resolver.decodeScaled(page, 3000)
            val bmp = if (decoded.isMutable) decoded
            else decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
            val sig = BitmapFactory.decodeFile(signature.path) ?: error("Signature file missing")

            val destW = placement.width * bmp.width
            val destH = destW * sig.height / sig.width
            val left = placement.x * bmp.width
            val top = placement.y * bmp.height
            Canvas(bmp).drawBitmap(
                sig, null, RectF(left, top, left + destW, top + destH),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )

            val outDir = File(cacheDir, "signed").apply { mkdirs() }
            val file = File(outDir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmp.recycle()
            sig.recycle()
            Uri.fromFile(file)
        }
}
