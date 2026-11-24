package com.example.pdfscan.features

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/** English uses ML Kit; Myanmar uses Tesseract's mya traineddata, downloaded once and then offline. */
class OcrService(private val context: Context) {
    suspend fun recognize(bitmap: Bitmap, languages: OcrLanguages): String = withContext(Dispatchers.IO) {
        val parts = mutableListOf<String>()
        if (languages != OcrLanguages.MYANMAR) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val english = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                if (english.isNotBlank()) parts += "English OCR\n$english"
            } finally { recognizer.close() }
        }
        if (languages != OcrLanguages.ENGLISH) {
            try {
                val myanmar = recognizeMyanmar(bitmap)
                if (myanmar.isNotBlank()) parts += "Myanmar OCR\n$myanmar"
            } catch (e: Exception) {
                if (languages == OcrLanguages.MYANMAR) throw e
                parts += "Myanmar OCR unavailable: ${e.message ?: "language model could not be loaded"}"
            }
        }
        parts.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun recognizeMyanmar(bitmap: Bitmap): String {
        val root = File(context.filesDir, "tesseract").apply { mkdirs() }
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        val trained = File(tessdata, "mya.traineddata")
        if (!trained.exists() || trained.length() < 1024) {
            val temporary = File(tessdata, "mya.traineddata.download")
            val connection = URL("https://github.com/tesseract-ocr/tessdata_best/raw/main/mya.traineddata").openConnection().apply {
                connectTimeout = 20_000; readTimeout = 60_000
            }
            connection.getInputStream().use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            check(temporary.length() > 1024) { "Downloaded Myanmar OCR model is incomplete" }
            if (trained.exists()) trained.delete()
            check(temporary.renameTo(trained)) { "Could not install Myanmar OCR language model" }
        }
        val api = TessBaseAPI()
        if (!api.init(root.absolutePath, "mya")) {
            api.recycle()
            error("Myanmar OCR initialization failed. Check the mya.traineddata language model.")
        }
        val scaled = if (maxOf(bitmap.width, bitmap.height) > 2400) {
            val factor = 2400f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
        } else bitmap
        return try { api.setImage(scaled); api.getUTF8Text() ?: "" } finally {
            api.recycle()
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}

enum class OcrLanguages { ENGLISH, MYANMAR, BOTH }
