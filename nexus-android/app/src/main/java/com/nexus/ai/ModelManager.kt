package com.nexus.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

data class ModelSpec(
    val id: String,
    val label: String,
    val description: String,
    val sizeLabel: String,
    val fileName: String,
    val url: String,
)

data class ModelDownloadState(
    val status: Int,
    val downloaded: Long,
    val total: Long,
)

class ModelManager(private val context: Context) {
    val models = listOf(
        ModelSpec(
            id = "low",
            label = "Low",
            description = "Qwen3 0.6B INT4. Найшвидша і найлегша.",
            sizeLabel = "≈332 МБ",
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm?download=true"
        ),
        ModelSpec(
            id = "medium",
            label = "Medium",
            description = "Qwen2.5 1.5B Q8. Помітно розумніша, але важча.",
            sizeLabel = "≈1.6 ГБ",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true"
        ),
        ModelSpec(
            id = "large",
            label = "Large",
            description = "Qwen3.5 4B Mixed INT4. Найрозумніша з трьох, повільніша.",
            sizeLabel = "≈2.75 ГБ",
            fileName = "Qwen3.5-4B_mixed_int4.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm?download=true"
        )
    )

    private val prefs = context.getSharedPreferences("nexus_models", Context.MODE_PRIVATE)
    private val dm = context.getSystemService(DownloadManager::class.java)

    fun modelFile(spec: ModelSpec): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.mkdirs()
        return File(dir, spec.fileName)
    }

    fun isDownloaded(spec: ModelSpec): Boolean {
        val f = modelFile(spec)
        return f.exists() && f.length() > 10_000_000
    }

    fun selectedId(): String? = prefs.getString("selected_model", null)

    fun setSelected(spec: ModelSpec) {
        prefs.edit().putString("selected_model", spec.id).apply()
    }

    fun selected(): ModelSpec? = models.firstOrNull { it.id == selectedId() }

    fun downloadId(spec: ModelSpec): Long = prefs.getLong("download_" + spec.id, -1L)

    fun startDownload(spec: ModelSpec): Long {
        val file = modelFile(spec)
        if (file.exists()) file.delete()
        val request = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle("NEXUS " + spec.label)
            .setDescription(spec.description)
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
        val id = dm.enqueue(request)
        prefs.edit().putLong("download_" + spec.id, id).apply()
        return id
    }

    fun query(spec: ModelSpec): ModelDownloadState? {
        val id = downloadId(spec)
        if (id <= 0L) return null
        val cursor = dm.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return ModelDownloadState(status, done, total)
        }
    }
}
