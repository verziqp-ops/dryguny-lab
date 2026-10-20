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
    val expectedBytes: Long,
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
            id = "gemma4_e2b",
            label = "Fast Vision",
            description = "Gemma 4 E2B. Значно розумніша за стару Vision 2B, підтримує текст, фото й екран.",
            sizeLabel = "≈2.58 ГБ",
            fileName = "gemma-4-E2B-it.litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            expectedBytes = 2_583_085_056L,
            vision = true,
        ),
        ModelSpec(
            id = "gemma4_e4b",
            label = "Smart Vision",
            description = "Gemma 4 E4B. Найрозумніша локальна модель NEXUS для Pixel 7 Pro: текст, фото, екран і thinking.",
            sizeLabel = "≈3.65 ГБ",
            fileName = "gemma-4-E4B-it.litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
            expectedBytes = 3_654_467_584L,
            vision = true,
            recommended = true,
        ),
        ModelSpec(
            id = "max",
            label = "Smart Text",
            description = "Qwen3.5 4B Mixed INT4. Хороша текстова модель без повного multimodal режиму.",
            sizeLabel = "≈2.75 ГБ",
            fileName = "Qwen3.5-4B_mixed_int4.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm?download=true",
            expectedBytes = 2_754_070_624L,
        )
    )

    private val prefs = context.getSharedPreferences("nexus_models", Context.MODE_PRIVATE)
    private val dm = context.getSystemService(DownloadManager::class.java)

    init {
        cleanupLegacyModelsOnce()
        migrateToSmarterModelsOnce()
    }

    fun modelFile(spec: ModelSpec): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.mkdirs()
        return File(dir, spec.fileName)
    }

    fun isDownloaded(spec: ModelSpec): Boolean {
        val file = modelFile(spec)
        if (!file.exists()) return false

        val id = downloadId(spec)
        if (id > 0L) {
            val state = query(spec) ?: return false
            if (state.status != DownloadManager.STATUS_SUCCESSFUL) return false
        }

        val minimumValidBytes = (spec.expectedBytes * 0.995).toLong()
        return file.length() >= minimumValidBytes
    }

    fun downloadProgress(spec: ModelSpec): Int? {
        val state = query(spec) ?: return null
        if (state.status == DownloadManager.STATUS_SUCCESSFUL) return 100
        if (state.total <= 0L) return null
        return ((state.downloaded * 100L) / state.total).toInt().coerceIn(0, 100)
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

    fun deleteModel(spec: ModelSpec): Boolean {
        val id = downloadId(spec)
        if (id > 0L) {
            try { dm.remove(id) } catch (_: Exception) {}
        }

        val wasSelected = selectedId() == spec.id
        val file = modelFile(spec)
        val deleted = try {
            !file.exists() || file.delete()
        } catch (_: Exception) {
            false
        }

        prefs.edit().remove("download_" + spec.id).apply()

        if (pendingActivationId() == spec.id) clearPendingActivation()
        if (wasSelected) clearSelected()
        return deleted
    }

    fun cancelDownload(spec: ModelSpec) {
        val id = downloadId(spec)
        if (id > 0L) {
            try { dm.remove(id) } catch (_: Exception) {}
        }
        prefs.edit()
            .remove("download_" + spec.id)
            .remove("pending_activate")
            .apply()
        try { modelFile(spec).delete() } catch (_: Exception) {}
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
        val oldId = downloadId(spec)
        if (oldId > 0L) {
            try { dm.remove(oldId) } catch (_: Exception) {}
        }

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

        return try {
            val cursor = dm.query(DownloadManager.Query().setFilterById(id))
            cursor.use {
                if (!it.moveToFirst()) return null
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                ModelDownloadState(status, done, total)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun validateExistingDownloads() {
        models.forEach { spec ->
            val file = modelFile(spec)
            val id = downloadId(spec)
            if (!file.exists() || id <= 0L) return@forEach

            val state = query(spec)
            if (state?.status == DownloadManager.STATUS_FAILED) {
                try { file.delete() } catch (_: Exception) {}
                if (selectedId() == spec.id) clearSelected()
            }
        }
    }

    private fun migrateToSmarterModelsOnce() {
        if (prefs.getBoolean("smart_models_v12", false)) return

        val dir = context.getExternalFilesDir("models")
        val weak = listOf(
            "Qwen3.5-2B_int8.litertlm",
            "Qwen3.5-2B-VL_int8.litertlm",
        )
        weak.forEach { name ->
            try { File(dir, name).delete() } catch (_: Exception) {}
        }

        val oldSelected = selectedId()
        if (oldSelected == "smart" || oldSelected == "vision") {
            clearSelected()
        }

        prefs.edit()
            .remove("download_smart")
            .remove("download_vision")
            .putBoolean("smart_models_v12", true)
            .apply()
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
