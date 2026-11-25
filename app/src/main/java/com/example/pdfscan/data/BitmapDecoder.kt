package com.example.pdfscan.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

/** Decodes [uri] with sub-sampling so the longest edge is at most [maxEdge] (avoids OOM). */
internal fun ContentResolver.decodeScaled(uri: Uri, maxEdge: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0) { "Cannot read image: $uri" }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2

    val raw = openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
        })
    } ?: error("Cannot decode image: $uri")

    val longest = max(raw.width, raw.height)
    if (longest <= maxEdge) return raw
    val f = maxEdge / longest.toFloat()
    return Bitmap.createScaledBitmap(raw, (raw.width * f).toInt(), (raw.height * f).toInt(), true)
        .also { if (it !== raw) raw.recycle() }
}
