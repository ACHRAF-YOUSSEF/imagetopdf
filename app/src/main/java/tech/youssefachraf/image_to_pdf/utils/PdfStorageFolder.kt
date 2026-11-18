package tech.youssefachraf.image_to_pdf.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.io.OutputStream

/**
 * Manages optional persistent Storage Access Framework (SAF) access to the `Download/ImageToPdf`
 * folder so generated PDFs remain visible across APK rebuilds, key changes, or reinstalls.
 */
object PdfStorageFolder {
    const val SUBFOLDER_NAME = "ImageToPdf"
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val INITIAL_DOC_ID = "primary:Download/ImageToPdf"

    @Volatile
    private var preferredTreeUri: Uri? = null

    fun setPreferredTreeUri(uri: Uri?) {
        preferredTreeUri = uri
    }

    fun buildInitialFolderUri(): Uri =
        DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, INITIAL_DOC_ID)

    fun formatTreeUriDisplayName(treeUri: Uri): String {
        val rawId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull().orEmpty()
        if (rawId.isBlank()) return "Linked folder"
        val cleaned = rawId.substringAfter(':', rawId).trim('/')
        return cleaned.ifBlank { "Internal Storage" }
    }

    fun hasActivePermission(context: Context, treeUri: Uri, requireWrite: Boolean = false): Boolean {
        val permissions = safePersistedPermissions(context.contentResolver)
        return permissions.any { perm ->
            perm.uri == treeUri && perm.isReadPermission && (!requireWrite || perm.isWritePermission)
        }
    }

    fun getActiveTreeUri(context: Context, requireWrite: Boolean = false): Uri? {
        val permissions = safePersistedPermissions(context.contentResolver)
        if (permissions.isEmpty()) return null

        val preferred = preferredTreeUri
        if (preferred != null && hasMatchingPermission(permissions, preferred, requireWrite)) {
            return preferred
        }
        return permissions.firstOrNull { perm ->
            DocumentsContract.isTreeUri(perm.uri) &&
                perm.isReadPermission &&
                (!requireWrite || perm.isWritePermission)
        }?.uri
    }

    private fun hasMatchingPermission(
        permissions: List<android.content.UriPermission>,
        uri: Uri,
        requireWrite: Boolean
    ): Boolean = permissions.any { perm ->
        perm.uri == uri && perm.isReadPermission && (!requireWrite || perm.isWritePermission)
    }

    private fun safePersistedPermissions(
        resolver: ContentResolver
    ): List<android.content.UriPermission> = try {
        resolver.persistedUriPermissions
    } catch (_: Exception) {
        emptyList()
    }

    fun takePersistablePermission(context: Context, treeUri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun releasePersistablePermission(context: Context, treeUri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(treeUri, flags)
        } catch (_: Exception) {
            // Permission may already be revoked by the user or system.
        }
    }

    fun queryTreePdfs(
        context: Context,
        treeUri: Uri,
        pageCountProvider: (Context, Uri, Long, Long) -> Int
    ): List<PdfFileInfo> {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val results = mutableListOf<PdfFileInfo>()
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                collectPdfRows(context, treeUri, cursor, pageCountProvider, results)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return results.sortedByDescending { it.dateModified }
    }

    private fun collectPdfRows(
        context: Context,
        treeUri: Uri,
        cursor: Cursor,
        pageCountProvider: (Context, Uri, Long, Long) -> Int,
        out: MutableList<PdfFileInfo>
    ) {
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        if (idCol < 0 || nameCol < 0) return

        while (cursor.moveToNext()) {
            val childId = cursor.getString(idCol) ?: continue
            val name = cursor.getString(nameCol) ?: continue
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
            if (!isPdfEntry(mime, name)) continue

            val sizeBytes = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
            val modified = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            val pageCount = pageCountProvider(context, docUri, sizeBytes, modified)
            out.add(PdfFileInfo(docUri, name, pageCount, sizeBytes, modified))
        }
    }

    private fun isPdfEntry(mime: String?, name: String): Boolean =
        mime == PdfConverter.PDF_MIME_TYPE || name.endsWith(PdfConverter.PDF_EXTENSION, ignoreCase = true)

    fun findChildPdfUri(context: Context, treeUri: Uri, fileName: String): Uri? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        return try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                findMatchingDocumentUri(treeUri, cursor, fileName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findMatchingDocumentUri(treeUri: Uri, cursor: Cursor, fileName: String): Uri? {
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        if (idCol < 0 || nameCol < 0) return null

        while (cursor.moveToNext()) {
            val name = cursor.getString(nameCol) ?: continue
            if (name.equals(fileName, ignoreCase = true)) {
                val childId = cursor.getString(idCol) ?: continue
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            }
        }
        return null
    }

    fun writePdfToTree(
        context: Context,
        treeUri: Uri,
        normalizedName: String,
        overwriteExisting: Boolean,
        writer: (OutputStream) -> Unit
    ): Uri? {
        val resolver = context.contentResolver
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

        if (overwriteExisting) {
            findChildPdfUri(context, treeUri, normalizedName)?.let { existingUri ->
                runCatching { DocumentsContract.deleteDocument(resolver, existingUri) }
                PdfThumbnailCache.invalidateUri(existingUri)
            }
        }

        val createdUri = try {
            DocumentsContract.createDocument(resolver, parentDocUri, PdfConverter.PDF_MIME_TYPE, normalizedName)
        } catch (_: Exception) {
            return null
        } ?: return null

        return try {
            resolver.openOutputStream(createdUri)?.use { out ->
                writer(out)
            } ?: throw IOException("Could not open SAF output stream")
            PdfThumbnailCache.invalidateUri(createdUri)
            createdUri
        } catch (e: CancellationException) {
            runCatching { DocumentsContract.deleteDocument(resolver, createdUri) }
            throw e
        } catch (exception: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, createdUri) }
            if (exception is IOException) {
                throw exception
            }
            null
        }
    }
}
