package com.example.pdfscan.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import java.io.File

object PageImageActions {
    fun load(context: Context, uri: Uri): Bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: error("Unable to open scanned page")

    fun save(context: Context, bitmap: Bitmap, prefix: String): Uri {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        return Uri.fromFile(file)
    }

    fun addSignature(source: Bitmap, signature: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(result)
        val width = result.width * 0.34f
        val height = width * signature.height / signature.width.toFloat()
        val left = result.width * 0.58f
        val top = result.height * 0.86f - height
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(signature, null, RectF(left, top, left + width, top + height), paint)
        return result
    }

    fun signatureBitmap(points: List<List<Offset>>, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; style = AndroidPaint.Style.STROKE
            strokeWidth = 7f; strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND
        }
        points.forEach { line ->
            if (line.size > 1) {
                val path = android.graphics.Path()
                path.moveTo(line.first().x * 2f, line.first().y * 2f)
                line.drop(1).forEach { path.lineTo(it.x * 2f, it.y * 2f) }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }
}
