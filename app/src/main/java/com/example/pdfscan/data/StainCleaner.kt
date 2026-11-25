package com.example.pdfscan.data

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Removes stains, shadows and yellowed paper tint without OpenCV.
 *
 * 1. Shrink the page and estimate the "paper" colour per channel (max filter + blur).
 *    Text is thin, so the max filter skips it and sees only paper (including stains/shadows).
 * 2. Divide every pixel by that paper estimate -> uneven lighting/stains become white.
 * 3. Stretch levels so light residue turns pure white while text stays dark.
 *
 * Limits: large photos or big dark areas on a page are treated as background and may be lightened.
 */
object StainCleaner {
    private const val BLACK_POINT = 30
    private const val WHITE_POINT = 235
    private const val MIN_BG = 90

    fun clean(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val sw = max(8, w / 8)
        val sh = max(8, h / 8)

        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val sp = IntArray(sw * sh)
        small.getPixels(sp, 0, sw, 0, 0, sw, sh)
        if (small !== src) small.recycle()

        val bgSmall = IntArray(sw * sh) { 0xFF000000.toInt() }
        val ch = IntArray(sw * sh)
        for (shift in intArrayOf(16, 8, 0)) {
            for (i in sp.indices) ch[i] = (sp[i] shr shift) and 0xFF
            val bg = boxBlur(maxFilter(ch, sw, sh, 3), sw, sh, 2)
            for (i in bgSmall.indices) bgSmall[i] = bgSmall[i] or (bg[i] shl shift)
        }

        val bgBmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        bgBmp.setPixels(bgSmall, 0, sw, 0, 0, sw, sh)
        val bgFull = Bitmap.createScaledBitmap(bgBmp, w, h, true)
        if (bgFull !== bgBmp) bgBmp.recycle()
        val bgPx = IntArray(w * h)
        bgFull.getPixels(bgPx, 0, w, 0, 0, w, h)
        bgFull.recycle()

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            val b = bgPx[i]
            val r = level((p shr 16) and 0xFF, (b shr 16) and 0xFF)
            val g = level((p shr 8) and 0xFF, (b shr 8) and 0xFF)
            val bl = level(p and 0xFF, b and 0xFF)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun level(c: Int, bg: Int): Int {
        val b = max(bg, MIN_BG)
        val v = min(255, c * 255 / b)
        return ((v - BLACK_POINT) * 255 / (WHITE_POINT - BLACK_POINT)).coerceIn(0, 255)
    }

    private fun maxFilter(a: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(a.size)
        val out = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (k in max(0, x - r)..min(w - 1, x + r)) m = max(m, a[y * w + k])
            tmp[y * w + x] = m
        }
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (k in max(0, y - r)..min(h - 1, y + r)) m = max(m, tmp[k * w + x])
            out[y * w + x] = m
        }
        return out
    }

    private fun boxBlur(a: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(a.size)
        val out = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0; var n = 0
            for (k in max(0, x - r)..min(w - 1, x + r)) { sum += a[y * w + k]; n++ }
            tmp[y * w + x] = sum / n
        }
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0; var n = 0
            for (k in max(0, y - r)..min(h - 1, y + r)) { sum += tmp[k * w + x]; n++ }
            out[y * w + x] = sum / n
        }
        return out
    }
}
