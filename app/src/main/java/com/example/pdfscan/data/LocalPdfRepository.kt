package com.example.pdfscan.data

import android.net.Uri
import com.example.pdfscan.domain.PdfEngine
import com.example.pdfscan.domain.PdfEntry
import com.example.pdfscan.domain.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import java.io.File

class LocalPdfRepository(
    private val dir: File,
    private val engine: PdfEngine,
) : PdfRepository {

    private val state = MutableStateFlow<List<PdfEntry>?>(null)
    override val documents: Flow<List<PdfEntry>> = state.filterNotNull()

    override suspend fun refresh() = withContext(Dispatchers.IO) { state.value = list() }

    override suspend fun export(pages: List<Uri>, baseName: String): PdfEntry =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val file = uniqueFile(baseName)
            try {
                engine.create(pages, file)
            } catch (t: Throwable) {
                file.delete()
                throw t
            }
            state.value = list()
            file.toEntry()
        }

    override suspend fun delete(entry: PdfEntry) = withContext(Dispatchers.IO) {
        entry.file.delete()
        state.value = list()
    }

    private fun list(): List<PdfEntry> =
        dir.listFiles { f -> f.extension.equals("pdf", true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { it.toEntry() }

    private fun uniqueFile(baseName: String): File {
        val safe = baseName.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Scan" }
        var file = File(dir, "$safe.pdf")
        var n = 1
        while (file.exists()) file = File(dir, "$safe (${n++}).pdf")
        return file
    }

    private fun File.toEntry() = PdfEntry(this, nameWithoutExtension, length(), lastModified())
}
