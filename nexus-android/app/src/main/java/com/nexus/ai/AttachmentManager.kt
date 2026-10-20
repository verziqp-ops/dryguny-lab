package com.nexus.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

enum class AttachmentKind { IMAGE, AUDIO, VIDEO, TEXT, FILE, SCREEN }

data class NexusAttachment(
    val name: String,
    val mime: String,
    val path: String,
    val kind: AttachmentKind,
    val originalUri: String? = null,
    val derivedImagePath: String? = null,
)

class AttachmentManager(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun importUri(uri: Uri): NexusAttachment {
        val name = displayName(uri)
        val mime = resolver.getType(uri) ?: guessMime(name)
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifBlank { "file.bin" }
        val out = File(dir, System.currentTimeMillis().toString() + "_" + safe)
        resolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        val kind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("audio/") -> AttachmentKind.AUDIO
            mime.startsWith("video/") -> AttachmentKind.VIDEO
            mime.startsWith("text/") || name.endsWith(".txt", true) || name.endsWith(".json", true) ||
                    name.endsWith(".xml", true) || name.endsWith(".md", true) || name.endsWith(".csv", true) -> AttachmentKind.TEXT
            else -> AttachmentKind.FILE
        }
        var frame: String? = null
        if (kind == AttachmentKind.VIDEO) frame = extractVideoFrame(out)
        return NexusAttachment(name, mime, out.absolutePath, kind, uri.toString(), frame)
    }

    fun fromScreen(path: String): NexusAttachment =
        NexusAttachment("screen.png", "image/png", path, AttachmentKind.SCREEN, derivedImagePath = path)

    fun readText(a: NexusAttachment, maxChars: Int = 40_000): String {
        if (a.kind != AttachmentKind.TEXT) return ""
        return try { File(a.path).readText().take(maxChars) } catch (_: Exception) { "" }
    }

    fun importModel(uri: Uri): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val out = File(dir, "nexus_model.litertlm")
        resolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output, 1024 * 1024) }
        }
        return out
    }

    private fun extractVideoFrame(file: File): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            if (bitmap == null) return null
            val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val out = File(dir, "video_frame_" + System.currentTimeMillis() + ".jpg")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            out.absolutePath
        } catch (_: Exception) { null }
    }

    private fun displayName(uri: Uri): String {
        var name = "file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        return name
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".txt", true) -> "text/plain"
        else -> "application/octet-stream"
    }
}
