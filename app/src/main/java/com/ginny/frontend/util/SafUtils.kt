package com.ginny.frontend.util

import android.content.Context
import android.net.Uri
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
}
