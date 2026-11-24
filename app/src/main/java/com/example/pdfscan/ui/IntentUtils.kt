package com.example.pdfscan.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.pdfscan.domain.PdfEntry

private fun Context.uriFor(entry: PdfEntry): Uri =
    FileProvider.getUriForFile(this, "$packageName.fileprovider", entry.file)

fun Context.openPdf(entry: PdfEntry) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(entry), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No PDF viewer installed", Toast.LENGTH_SHORT).show()
    }
}

fun Context.sharePdf(entry: PdfEntry) {
    val uri = uriFor(entry)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, entry.name)
        clipData = ClipData.newRawUri(entry.name, uri)   // makes the grant reliable on the chooser
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, null))
}
