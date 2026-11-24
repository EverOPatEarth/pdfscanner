package com.example.pdfscan.features

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Lossless PDF rewrite/optimization.
 *
 * PDFBox rewrites the PDF object structure without rasterizing pages or re-encoding
 * embedded images. Text, vector content, page dimensions and image pixel dimensions
 * are retained. A rewritten file is used only when it is smaller than the source;
 * otherwise the original bytes are copied so this operation never makes the PDF larger.
 *
 * This does not promise a smaller file for every PDF. Already-optimized PDFs may not
 * have any safe size reduction available without changing image quality or resolution.
 */
object PdfCompressor {

    suspend fun compress(context: Context, input: File, output: File): File =
        withContext(Dispatchers.IO) {
            require(input.isFile) { "The source PDF does not exist." }
            require(input.length() > 0L) { "The source PDF is empty." }
            require(input.canonicalFile != output.canonicalFile) {
                "Choose a different output file so the original stays unchanged."
            }

            output.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Could not create the output folder.")
                }
            }

            PDFBoxResourceLoader.init(context.applicationContext)

            val temp = File(output.parentFile, output.name + ".optimizing")
            if (temp.exists() && !temp.delete()) {
                throw IOException("Could not prepare a temporary output file.")
            }

            try {
                PDDocument.load(input).use { document ->
                    document.save(temp)
                }

                if (temp.length() in 1 until input.length()) {
                    if (output.exists() && !output.delete()) {
                        throw IOException("Could not replace the previous compressed file.")
                    }
                    if (!temp.renameTo(output)) {
                        temp.inputStream().use { source ->
                            output.outputStream().use { destination -> source.copyTo(destination) }
                        }
                        temp.delete()
                    }
                } else {
                    // No safe size reduction was achieved. Keep the original PDF bytes.
                    if (output.exists() && !output.delete()) {
                        throw IOException("Could not replace the previous compressed file.")
                    }
                    input.inputStream().use { source ->
                        output.outputStream().use { destination -> source.copyTo(destination) }
                    }
                    temp.delete()
                }

                output
            } catch (error: Exception) {
                temp.delete()
                output.delete()
                throw error
            }
        }
}
