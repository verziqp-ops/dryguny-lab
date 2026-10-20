package com.nexus.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

data class ModelSpec(
    val id: String,
    val label: String,
    val description: String,
    val sizeLabel: String,
    val fileName: String,
    val url: String,
    val vision: Boolean = false,
    val recommended: Boolean = false,
)

data class ModelDownloadState(
    val status: Int,
    val downloaded: Long,
    val total: Long,
)

class ModelManager(private val context: Context) {
    val models = listOf(
        ModelSpec(
            id = "smart",
            label = "Smart",
            description = "Qwen3.5 2B INT8. Значно розумніша за старі Low/Medium, добра мультимовність і нормальна пам’ять діалогу.",
            sizeLabel = "≈1.97 ГБ",
            fileName = "Qwen3.5-2B_int8.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3.5-2B/resolve/main/Qwen3.5-2B_int8.litertlm?download=true",
            recommended = true,
        ),
        ModelSpec(
            id = "vision",
            label = "Vision",
            description = "Qwen3.5 2B VL. Та сама сильніша модель + реальне розуміння фото й скріншотів.",
            sizeLabel = "≈3.15 ГБ",
            fileName = "Qwen3.5-2B-VL_int8.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3.5-2B/resolve/main/Qwen3.5-2B-VL_int8.litertlm?download=true",
            vision = true,
        ),
        ModelSpec(
            id = "max",
            label = "Max",
            description = "Qwen3.5 4B Mixed INT4. Найрозумніша з безпечних для Pixel 7 Pro варіантів, але повільніша.",
            sizeLabel = "≈2.75 ГБ",
            fileName = "Qwen3.5-4B_mixed_int4.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm?download=true",
        )
    )

    private val prefs = context.getSharedPreferences("nexus_models", Context.MODE_PRIVATE)
    private val dm = context.getSystemService(DownloadManager::class.java)

    init {
        cleanupLegacyModelsOnce()
    }

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
        prefs.edit()
            .putString("selected_model", spec.id)
            .remove("pending_activate")
            .apply()
        context.getSharedPreferences("nexus_model", Context.MODE_PRIVATE)
            .edit().putString("model_path", modelFile(spec).absolutePath).apply()
    }

    fun clearSelected() {
        prefs.edit().remove("selected_model").remove("pending_activate").apply()
        context.getSharedPreferences("nexus_model", Context.MODE_PRIVATE).edit().remove("model_path").apply()
    }

    fun selected(): ModelSpec? = models.firstOrNull { it.id == selectedId() }

    fun byPath(path: String?): ModelSpec? =
        if (path.isNullOrBlank()) null else models.firstOrNull { modelFile(it).absolutePath == path }

    fun pendingActivationId(): String? = prefs.getString("pending_activate", null)

    fun clearPendingActivation() {
        prefs.edit().remove("pending_activate").apply()
    }

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
            .setDestinationInExternalFilesDir(context, "models", spec.fileName)

        val id = dm.enqueue(request)
        prefs.edit()
            .putLong("download_" + spec.id, id)
            .putString("pending_activate", spec.id)
            .apply()
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

    private fun cleanupLegacyModelsOnce() {
        if (prefs.getBoolean("smart_models_migrated", false)) return

        val dir = context.getExternalFilesDir("models")
        val legacy = listOf(
            "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        )
        legacy.forEach { name ->
            try { File(dir, name).delete() } catch (_: Exception) {}
        }

        val oldSelected = prefs.getString("selected_model", null)
        if (oldSelected == "low" || oldSelected == "medium" || oldSelected == "large") {
            clearSelected()
        }

        prefs.edit().putBoolean("smart_models_migrated", true).apply()
    }
}
