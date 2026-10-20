package com.nexus.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class LocalLlmEngine(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    @Volatile var isLoaded: Boolean = false
        private set
    @Volatile var mediaPipelineEnabled: Boolean = false
        private set
    var modelPath: String? = null
        private set
    @Volatile private var languageFilter: Boolean =
        context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)
            .getBoolean("language_filter", false)

    fun loadModel(path: String, onDone: (Boolean, String) -> Unit) {
        scope.launch {
            closeInternal()
            val file = File(path)
            if (!file.exists()) {
                main.post { onDone(false, "Файл локальної моделі не знайдено.") }
                return@launch
            }
            try {
                val loaded = initialize(path, true)
                if (!loaded) initialize(path, false)
                isLoaded = true
                modelPath = path
                context.getSharedPreferences("nexus_model", Context.MODE_PRIVATE)
                    .edit().putString("model_path", path).apply()
                main.post {
                    onDone(
                        true,
                        if (mediaPipelineEnabled) "Локальна модель готова. Текст і мультимедійний канал увімкнені."
                        else "Локальна модель готова. Ця модель працює у текстовому режимі."
                    )
                }
            } catch (e: Throwable) {
                closeInternal()
                main.post { onDone(false, "Не вдалося запустити локальну модель: " + (e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    private suspend fun initialize(path: String, tryMedia: Boolean): Boolean {
        try {
            val config =
                if (tryMedia) {
                    EngineConfig(
                        modelPath = path,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                        audioBackend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath,
                    )
                } else {
                    EngineConfig(
                        modelPath = path,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath,
                    )
                }
            val newEngine = Engine(config)
            newEngine.initialize()
            val newConversation = newEngine.createConversation(
                ConversationConfig(systemInstruction = Contents.of(Content.Text(systemInstruction())))
            )
            engine = newEngine
            conversation = newConversation
            mediaPipelineEnabled = tryMedia
            return true
        } catch (e: Throwable) {
            if (tryMedia) {
                closeInternal()
                return false
            }
            throw e
        }
    }

    fun setLanguageFilter(enabled: Boolean) {
        languageFilter = enabled
        context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("language_filter", enabled).apply()
        val activeEngine = engine ?: return
        scope.launch {
            try {
                conversation?.close()
                conversation = activeEngine.createConversation(
                    ConversationConfig(systemInstruction = Contents.of(Content.Text(systemInstruction())))
                )
            } catch (_: Throwable) {}
        }
    }

    private fun systemInstruction(): String {
        val style =
            if (languageFilter)
                "Avoid profanity and unnecessarily explicit wording."
            else
                "Do not censor ordinary profanity or rude wording merely because it is offensive; use natural language when appropriate."
        return "You are NEXUS, a private on-device Android assistant. " +
            "Answer in the user's language. Be concise and useful. " +
            style + " " +
            "Do not provide instructions that meaningfully facilitate serious harm, malware, credential theft, illegal abuse, or bypassing safety protections. " +
            "Never claim an Android action was executed unless the app reports that it succeeded. " +
            "Every action that changes the phone must be confirmed by the user in the app before execution. " +
            "The user may attach screenshots, images, audio, video frames, and local files."
    }

    fun loadSaved(onDone: (Boolean, String) -> Unit) {
        val path = context.getSharedPreferences("nexus_model", Context.MODE_PRIVATE)
            .getString("model_path", null)
        if (path.isNullOrBlank() || !File(path).exists()) {
            onDone(false, "Локальна модель ще не вибрана.")
            return
        }
        loadModel(path, onDone)
    }

    fun send(
        prompt: String,
        attachments: List<NexusAttachment>,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val conv = conversation
        if (!isLoaded || conv == null) {
            onError("Локальна модель не завантажена.")
            return
        }
        scope.launch {
            try {
                val contents = mutableListOf<Content>()
                contents += Content.Text(prompt)
                if (mediaPipelineEnabled) {
                    attachments.forEach { a ->
                        when (a.kind) {
                            AttachmentKind.IMAGE, AttachmentKind.SCREEN -> contents += Content.ImageFile(a.path)
                            AttachmentKind.AUDIO -> contents += Content.AudioFile(a.path)
                            AttachmentKind.VIDEO -> a.derivedImagePath?.let { contents += Content.ImageFile(it) }
                            else -> Unit
                        }
                    }
                }
                conv.sendMessageAsync(
                    Contents.of(contents),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val token = message.toString()
                            if (token.isNotEmpty()) main.post { onChunk(token) }
                        }
                        override fun onDone() {
                            main.post { onDone() }
                        }
                        override fun onError(throwable: Throwable) {
                            main.post { onError(throwable.message ?: throwable.javaClass.simpleName) }
                        }
                    }
                )
            } catch (e: Throwable) {
                main.post { onError(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun close() {
        scope.launch { closeInternal() }
    }

    private fun closeInternal() {
        try { conversation?.close() } catch (_: Throwable) {}
        try { engine?.close() } catch (_: Throwable) {}
        conversation = null
        engine = null
        isLoaded = false
        mediaPipelineEnabled = false
    }
}
