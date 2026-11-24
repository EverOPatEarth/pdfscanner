package com.example.pdfscan.features

import android.graphics.Bitmap
import android.graphics.Color

/** Conservative paper cleanup: background whitening, shadow normalization and isolated speck removal. */
object DocumentImageProcessor {
    fun clean(source: Bitmap, removeStains: Boolean = true, removeShadows: Boolean = true): Bitmap {
        val w = source.width; val h = source.height
        val input = IntArray(w * h); source.getPixels(input, 0, w, 0, 0, w, h)
        val output = IntArray(input.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val c = input[i]
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            val neighborhood = if (removeShadows) localBackground(input, w, h, x, y) else 255
            val normalized = if (removeShadows) (gray * 255f / neighborhood.coerceAtLeast(90)).toInt().coerceIn(0,255) else gray
            val value = when {
                removeStains && normalized > 205 -> 255
                removeStains && normalized in 175..205 -> 245
                else -> normalized
            }
            output[i] = Color.rgb(value, value, value)
        }
        if (removeStains) {
            val copy = output.clone()
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                if (Color.red(copy[i]) < 180) {
                    var dark = 0
                    for (dy in -1..1) for (dx in -1..1) if (Color.red(copy[i + dy*w + dx]) < 180) dark++
                    if (dark <= 2) output[i] = Color.WHITE
                }
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(output,0,w,0,0,w,h) }
    }

    private fun localBackground(p: IntArray, w: Int, h: Int, x: Int, y: Int): Int {
        var sum = 0; var count = 0
        for (dy in -8..8 step 8) for (dx in -8..8 step 8) {
            val xx=(x+dx).coerceIn(0,w-1); val yy=(y+dy).coerceIn(0,h-1); val c=p[yy*w+xx]
            sum += (Color.red(c)+Color.green(c)+Color.blue(c))/3; count++
        }
        return (sum/count).coerceIn(90,255)
    }
}
