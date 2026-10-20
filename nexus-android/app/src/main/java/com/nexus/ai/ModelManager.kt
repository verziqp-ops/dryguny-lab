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
            id = "smart",
            label = "Fast",
            description = "Qwen3.5 2B INT8. Швидша й легша, але помітно слабша за Smart 4B.",
            sizeLabel = "≈1.97 ГБ",
            fileName = "Qwen3.5-2B_int8.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3.5-2B/resolve/main/Qwen3.5-2B_int8.litertlm?download=true",
            expectedBytes = 2_119_744_416L,
            recommended = false,
        ),
        ModelSpec(
            id = "vision",
            label = "Vision",
            description = "Qwen3.5 2B VL. Та сама сильніша модель + реальне розуміння фото й скріншотів.",
            sizeLabel = "≈3.15 ГБ",
            fileName = "Qwen3.5-2B-VL_int8.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3.5-2B/resolve/main/Qwen3.5-2B-VL_int8.litertlm?download=true",
            expectedBytes = 3_152_378_448L,
            vision = true,
        ),
        ModelSpec(
            id = "max",
            label = "Smart",
            description = "Qwen3.5 4B Mixed INT4. Основна рекомендована модель: помітно розумніша за Fast 2B.",
            sizeLabel = "≈2.75 ГБ",
            fileName = "Qwen3.5-4B_mixed_int4.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm?download=true",
            expectedBytes = 2_754_070_624L,
            recommended = true,
        )
    )

    private val prefs = context.getSharedPreferences("nexus_models", Context.MODE_PRIVATE)
    private val dm = context.getSystemService(DownloadManager::class.java)

    init {
        cleanupLegacyModelsOnce()
        promoteSmart4BOnce()
    }

    fun modelFile(spec: ModelSpec): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.mkdirs()
        return File(dir, spec.fileName)
    }

    fun isDownloaded(spec: ModelSpec): Boolean {
        val file = modelFile(spec)
        if (!file.exists()) return false

        // Never treat a partial DownloadManager destination as a valid model.
        val id = downloadId(spec)
        if (id > 0L) {
            val state = query(spec) ?: return false
            if (state.status != DownloadManager.STATUS_SUCCESSFUL) return false
        }

        // Allow a very small filesystem/reporting tolerance, but reject partial/corrupt files.
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

        prefs.edit()
            .remove("download_" + spec.id)
            .apply()

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

    private fun promoteSmart4BOnce() {
        if (prefs.getBoolean("promoted_4b_v09", false)) return

        val oldFastSelected = selectedId() == "smart"
        val smart4b = models.firstOrNull { it.id == "max" }

        if (oldFastSelected && smart4b != null && isDownloaded(smart4b)) {
            setSelected(smart4b)
        }

        prefs.edit().putBoolean("promoted_4b_v09", true).apply()
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
