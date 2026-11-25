package com.example.pdfscan.domain

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/** Position/size of a signature as fractions (0..1) of the page image. x,y = top-left corner. */
data class SignaturePlacement(val x: Float, val y: Float, val width: Float)

/** Seam for per-page image edits (filters, signature, later: crop, rotate...). */
interface PageEditor {
    /** Low-res preview of the page with stains/shadows removed. */
    suspend fun cleanedPreview(page: Uri): Bitmap

    /** Burns [signature] (transparent PNG) into [page]; returns the Uri of a new image file. */
    suspend fun applySignature(page: Uri, signature: File, placement: SignaturePlacement): Uri
}
