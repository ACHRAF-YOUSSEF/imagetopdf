package tech.youssefachraf.image_to_pdf.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PDF_MIME = "application/pdf"
private const val JPEG_MIME = "image/jpeg"

private val pdfDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US)

fun openPdf(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No PDF viewer installed on this device.", Toast.LENGTH_SHORT).show()
    }
}

fun sharePdf(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = PDF_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
}

fun shareImages(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val shareIntent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = JPEG_MIME
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = JPEG_MIME
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Images"))
}

fun formatPdfDate(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(pdfDateFormatter)
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.US, "%d kB", bytes / 1_000L)
        else -> "$bytes B"
    }
}
