package com.ginny.frontend.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/** Small helpers around the Storage Access Framework tree API used for the ROM and User folders. */
object SafUtils {

    fun treeDocument(context: Context, treeUri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, treeUri)

    fun findChild(parent: DocumentFile, name: String): DocumentFile? =
        parent.listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Finds an existing child directory by name, or creates it if missing. */
    fun getOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        findChild(parent, name)?.let { if (it.isDirectory) return it }
        return parent.createDirectory(name)
    }

    fun findChildFileIgnoreCase(parent: DocumentFile, fileName: String): DocumentFile? =
        parent.listFiles().firstOrNull { it.isFile && it.name.equals(fileName, ignoreCase = true) }

    /**
     * Best-effort translation of a SAF document Uri backed by Android's own
     * ExternalStorageProvider into the real absolute filesystem path it represents. Returns null
     * for anything not on local/removable external storage (e.g. a cloud-backed SAF provider),
     * where no real path exists — callers should fall back to the content:// Uri in that case.
     */
    fun realPathOrNull(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val (volume, relativePath) = docId.split(":", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return null
        }
        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().path
        } else {
            "/storage/$volume"
        }
        return "$root/$relativePath"
    }
}
