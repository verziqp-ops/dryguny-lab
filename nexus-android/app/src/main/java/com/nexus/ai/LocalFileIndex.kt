package com.nexus.ai

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class FileHit(val name: String, val uri: Uri, val type: String?)

class LocalFileIndex(private val context: Context) {
    private val prefs = context.getSharedPreferences("nexus_folders", Context.MODE_PRIVATE)

    fun addFolder(uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { context.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
        val current = prefs.getStringSet("trees", emptySet())?.toMutableSet() ?: mutableSetOf()
        current += uri.toString()
        prefs.edit().putStringSet("trees", current).apply()
    }

    fun folderCount(): Int = prefs.getStringSet("trees", emptySet())?.size ?: 0

    fun search(query: String, limit: Int = 20): List<FileHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val hits = mutableListOf<FileHit>()
        val trees = prefs.getStringSet("trees", emptySet()) ?: emptySet()
        for (raw in trees) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(raw)) ?: continue
            walk(root, q, hits, limit, 0)
            if (hits.size >= limit) break
        }
        return hits
    }

    private fun walk(node: DocumentFile, q: String, out: MutableList<FileHit>, limit: Int, depth: Int) {
        if (out.size >= limit || depth > 12) return
        val children = try { node.listFiles() } catch (_: Exception) { emptyArray() }
        for (child in children) {
            if (out.size >= limit) return
            val name = child.name ?: ""
            if (name.lowercase().contains(q)) out += FileHit(name, child.uri, child.type)
            if (child.isDirectory) walk(child, q, out, limit, depth + 1)
        }
    }
}
