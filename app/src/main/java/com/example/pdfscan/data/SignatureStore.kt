package com.example.pdfscan.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** [version] changes on every save so the UI reloads the image even though the path is the same. */
data class SavedSignature(val file: File, val version: Long)

/** Keeps one transparent PNG signature in app-private storage. Never leaves the device. */
class SignatureStore(private val dir: File) {
    private val file = File(dir, "signature.png")
    private val _current = MutableStateFlow(
        if (file.exists()) SavedSignature(file, file.lastModified()) else null
    )
    val current: StateFlow<SavedSignature?> = _current.asStateFlow()

    suspend fun save(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _current.value = SavedSignature(file, System.currentTimeMillis())
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        _current.value = null
    }
}
