package com.example.pdfscan.data

import android.content.ContentResolver
import android.net.Uri
import com.example.pdfscan.domain.PdfEngine
import com.example.pdfscan.domain.PdfOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.min

/**
 * Creates PDFs from the JPEG pages returned by ML Kit Document Scanner.
 * JPEGs are embedded directly through PDFBox instead of decoding them into a Bitmap,
 * resizing them to 2000 px and drawing a newly encoded page image. This avoids that
 * unnecessary decode/downscale/re-encode path and preserves the source JPEG pixel data.
 */
class AndroidPdfEngine(private val resolver: ContentResolver) : PdfEngine {

    override suspend fun create(pages: List<Uri>, output: File, options: PdfOptions) =
        withContext(Dispatchers.IO) {
            require(pages.isNotEmpty()) { "No pages to export" }
            output.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Could not create the PDF output folder.")
                }
            }
            val temp = File(output.parentFile, output.name + ".creating")
            if (temp.exists() && !temp.delete()) {
                throw IOException("Could not prepare a temporary PDF file.")
            }

            try {
                PDDocument().use { document ->
                    pages.forEachIndexed { index, uri ->
                        currentCoroutineContext().ensureActive()
                        val input = resolver.openInputStream(uri)
                            ?: throw IOException("Cannot open scanned image: $uri")
                        val image = input.use { JPEGFactory.createFromStream(document, it) }

                        val imageWidth = image.width.toFloat()
                        val imageHeight = image.height.toFloat()
                        require(imageWidth > 0f && imageHeight > 0f) {
                            "Invalid scanned image dimensions on page ${index + 1}."
                        }

                        val pageSize = if (options.useA4Page) {
                            if (imageWidth > imageHeight) PDRectangle(842f, 595f)
                            else PDRectangle.A4
                        } else {
                            PDRectangle(imageWidth, imageHeight)
                        }
                        val page = PDPage(pageSize)
                        document.addPage(page)

                        val scale = min(
                            pageSize.width / imageWidth,
                            pageSize.height / imageHeight
                        )
                        val drawWidth = imageWidth * scale
                        val drawHeight = imageHeight * scale
                        val x = (pageSize.width - drawWidth) / 2f
                        val y = (pageSize.height - drawHeight) / 2f

                        PDPageContentStream(document, page).use { stream ->
                            stream.drawImage(image, x, y, drawWidth, drawHeight)
                        }
                    }
                    document.save(temp)
                }

                if (!temp.isFile || temp.length() <= 0L) {
                    throw IOException("PDF creation produced an empty file.")
                }
                if (output.exists() && !output.delete()) {
                    throw IOException("Could not replace the previous PDF file.")
                }
                if (!temp.renameTo(output)) {
                    temp.inputStream().use { source ->
                        output.outputStream().use { destination -> source.copyTo(destination) }
                    }
                    temp.delete()
                }
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        }
}
