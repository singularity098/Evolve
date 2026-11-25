package io.github.singularity098.evolve

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Files in the folder the person chose (a document tree), through the storage framework. */
object Exports {
    private fun parent(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    fun write(context: Context, tree: Uri, name: String, mime: String, bytes: ByteArray): Boolean {
        val r = context.contentResolver
        val doc = DocumentsContract.createDocument(r, parent(tree), mime, name) ?: return false
        r.openOutputStream(doc, "wt")?.use { it.write(bytes) } ?: return false
        return true
    }

    /** Deletes all but the newest [keep] files whose names start with [prefix]. */
    fun prune(context: Context, tree: Uri, prefix: String, keep: Int) {
        val r = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>()
        r.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(prefix)) found += c.getString(0) to name
            }
        }
        // The names carry the time, so newest sorts last.
        found.sortedByDescending { it.second }.drop(keep).forEach { (id, _) ->
            try {
                DocumentsContract.deleteDocument(r, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            } catch (_: Exception) {
            }
        }
    }

    fun read(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("The file could not be opened")
}
