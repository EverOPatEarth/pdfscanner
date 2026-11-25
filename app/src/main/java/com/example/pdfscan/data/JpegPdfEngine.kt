package com.example.pdfscan.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.example.pdfscan.domain.PdfEngine
import com.example.pdfscan.domain.PdfOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Hand-written PDF writer that embeds each page as a JPEG (DCTDecode).
 *  - Compression: JPEG quality + max resolution come from [PdfOptions.compression].
 *  - Stain removal: [StainCleaner] runs before encoding.
 *  - OCR: invisible text (render mode 3) is laid over the image, so the PDF is searchable/selectable.
 * No third-party PDF library needed.
 */
class JpegPdfEngine(private val resolver: ContentResolver) : PdfEngine {

    override suspend fun create(pages: List<Uri>, output: File, options: PdfOptions) =
        withContext(Dispatchers.IO) {
            require(pages.isNotEmpty()) { "No pages to export" }
            val ocr = if (options.ocr) TextOcr() else null
            try {
                output.outputStream().buffered().use { stream ->
                    val pdf = PdfWriter(stream)
                    pdf.header()
                    pages.forEachIndexed { index, uri ->
                        currentCoroutineContext().ensureActive()
                        var bitmap = resolver.decodeScaled(uri, options.compression.maxEdgePx)
                        try {
                            if (options.cleanStains) {
                                val cleaned = StainCleaner.clean(bitmap)
                                bitmap.recycle()
                                bitmap = cleaned
                            }
                            val lines = if (ocr == null) emptyList() else try {
                                ocr.recognize(bitmap)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                emptyList()      // OCR is best-effort; the PDF is still created
                            }
                            val jpeg = ByteArrayOutputStream().also {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, options.compression.jpegQuality, it)
                            }.toByteArray()
                            pdf.page(index, bitmap.width, bitmap.height, jpeg, lines)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    pdf.finish(pages.size)
                }
            } finally {
                ocr?.close()
            }
        }

    private class PdfWriter(private val out: OutputStream) {
        private var pos = 0L
        private val offsets = HashMap<Int, Long>()
        private val winAnsi: Charset = Charset.forName("windows-1252")

        private fun raw(b: ByteArray) { out.write(b); pos += b.size }
        private fun raw(s: String) = raw(s.toByteArray(Charsets.ISO_8859_1))
        private fun begin(id: Int) { offsets[id] = pos; raw("$id 0 obj\n") }
        private fun end() = raw("endobj\n")
        private fun f(v: Float) = String.format(Locale.US, "%.2f", v)   // never a decimal comma

        fun header() {
            raw("%PDF-1.4\n")
            raw(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 10))
        }

        fun page(index: Int, imgW: Int, imgH: Int, jpeg: ByteArray, lines: List<OcrLine>) {
            val landscape = imgW > imgH
            val pw = if (landscape) A4_LONG else A4_SHORT
            val ph = if (landscape) A4_SHORT else A4_LONG
            val s = min(pw / imgW.toFloat(), ph / imgH.toFloat())
            val w = imgW * s
            val h = imgH * s
            val x0 = (pw - w) / 2f
            val y0 = (ph - h) / 2f

            val pageId = 4 + 3 * index
            val contentId = pageId + 1
            val imageId = pageId + 2

            begin(imageId)
            raw("<< /Type /XObject /Subtype /Image /Width $imgW /Height $imgH /ColorSpace /DeviceRGB " +
                "/BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
            raw(jpeg)
            raw("\nendstream\n")
            end()

            val content = ByteArrayOutputStream()
            fun put(str: String) = content.write(str.toByteArray(Charsets.ISO_8859_1))
            put("q ${f(w)} 0 0 ${f(h)} ${f(x0)} ${f(y0)} cm /Im0 Do Q\n")
            val textLines = lines.filter { it.text.isNotBlank() }
            if (textLines.isNotEmpty()) {
                put("BT\n3 Tr\n")                         // 3 = invisible text
                for (l in textLines) {
                    val boxW = (l.right - l.left) * s
                    val boxH = (l.bottom - l.top) * s
                    val fs = max(2f, boxH * 0.8f)
                    val natural = l.text.length * 0.5f * fs      // Helvetica averages ~0.5 em per char
                    val tz = (boxW / natural * 100f).coerceIn(10f, 500f)
                    val px = x0 + l.left * s
                    val baseline = y0 + (imgH - l.bottom) * s + boxH * 0.2f
                    put("/F1 ${f(fs)} Tf ${f(tz)} Tz 1 0 0 1 ${f(px)} ${f(baseline)} Tm (")
                    content.write(escape(l.text))
                    put(") Tj\n")
                }
                put("ET\n")
            }
            val contentBytes = content.toByteArray()
            begin(contentId)
            raw("<< /Length ${contentBytes.size} >>\nstream\n")
            raw(contentBytes)
            raw("\nendstream\n")
            end()

            begin(pageId)
            raw("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pw $ph] " +
                "/Resources << /XObject << /Im0 $imageId 0 R >> /Font << /F1 3 0 R >> >> " +
                "/Contents $contentId 0 R >>\n")
            end()
        }

        fun finish(pageCount: Int) {
            begin(3)
            raw("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\n")
            end()

            begin(2)
            val kids = (0 until pageCount).joinToString(" ") { "${4 + 3 * it} 0 R" }
            raw("<< /Type /Pages /Kids [$kids] /Count $pageCount >>\n")
            end()

            begin(1)
            raw("<< /Type /Catalog /Pages 2 0 R >>\n")
            end()

            val objectCount = 3 + 3 * pageCount
            val xrefPos = pos
            raw("xref\n0 ${objectCount + 1}\n")
            raw("0000000000 65535 f \n")
            for (id in 1..objectCount) {
                raw(String.format(Locale.US, "%010d 00000 n \n", offsets.getValue(id)))
            }
            raw("trailer\n<< /Size ${objectCount + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
            out.flush()
        }

        /** Encodes as WinAnsi (covers French/Spanish accents) and escapes PDF string syntax. */
        private fun escape(text: String): ByteArray {
            val cleaned = text.replace(Regex("\\p{Cntrl}"), " ")
            val bytes = cleaned.toByteArray(winAnsi)       // unmappable characters become '?'
            val bos = ByteArrayOutputStream(bytes.size + 8)
            for (b in bytes) {
                when (b.toInt().toChar()) {
                    '(', ')', '\\' -> { bos.write('\\'.code); bos.write(b.toInt()) }
                    else -> bos.write(b.toInt())
                }
            }
            return bos.toByteArray()
        }
    }

    private companion object {
        const val A4_SHORT = 595   // PostScript points
        const val A4_LONG = 842
    }
}
