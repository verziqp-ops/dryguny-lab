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
                main.post { onDone(false, "Файл моделі не знайдено.") }
                return@launch
            }

            try {
                // The bundled Low/Medium/Large models are text models. Starting vision/audio
                // executors for them wastes memory and can leave large native allocations behind.
                initializeTextOnly(path, 1024)
                isLoaded = true
                modelPath = path
                context.getSharedPreferences("nexus_model", Context.MODE_PRIVATE)
                    .edit().putString("model_path", path).apply()
                main.post { onDone(true, "Модель готова") }
            } catch (first: Throwable) {
                try {
                    // Lower KV cache fallback for devices that are already under memory pressure.
                    initializeTextOnly(path, 640)
                    isLoaded = true
                    modelPath = path
                    context.getSharedPreferences("nexus_model", Context.MODE_PRIVATE)
                        .edit().putString("model_path", path).apply()
                    main.post { onDone(true, "Модель готова у легкому режимі") }
                } catch (second: Throwable) {
                    closeInternal()
                    main.post { onDone(false, friendlyError(second)) }
                }
            }
        }
    }

    private fun initializeTextOnly(path: String, maxTokens: Int) {
        closeInternal()

        var newEngine: Engine? = null
        var newConversation: Conversation? = null
        try {
            newEngine = Engine(
                EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(threadCount = 4),
                    maxNumTokens = maxTokens,
                    cacheDir = context.cacheDir.absolutePath,
                )
            )
            newEngine.initialize()

            newConversation = newEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(Content.Text(systemInstruction())),
                    maxOutputToken = 256,
                    enableSpeculativeDecoding = false,
                )
            )

            engine = newEngine
            conversation = newConversation
            mediaPipelineEnabled = false
        } catch (e: Throwable) {
            try { newConversation?.close() } catch (_: Throwable) {}
            try {
                if (newEngine?.isInitialized() == true) newEngine.close()
            } catch (_: Throwable) {}
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
                    ConversationConfig(
                        systemInstruction = Contents.of(Content.Text(systemInstruction())),
                        maxOutputToken = 256,
                        enableSpeculativeDecoding = false,
                    )
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
            "Every action that changes the phone must be confirmed by the user in the app before execution."
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
            onError("Модель ще не готова.")
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
                            main.post { onError(friendlyError(throwable)) }
                        }
                    }
                )
            } catch (e: Throwable) {
                main.post { onError(friendlyError(e)) }
            }
        }
    }

    fun close() {
        scope.launch { closeInternal() }
    }

    private fun closeInternal() {
        try { conversation?.close() } catch (_: Throwable) {}
        try {
            val e = engine
            if (e?.isInitialized() == true) e.close()
        } catch (_: Throwable) {}

        conversation = null
        engine = null
        isLoaded = false
        mediaPipelineEnabled = false
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            "allocate tensors" in lower || "out of memory" in lower || "memory" in lower ->
                "Не вистачило вільної пам’яті для запуску відповіді. Закрий важкі програми або вибери легшу модель."
            "model" in lower && ("invalid" in lower || "unsupported" in lower) ->
                "Ця модель несумісна з поточною версією NEXUS."
            else ->
                "Модель не змогла відповісти. Спробуй ще раз або перезапусти її."
        }
    }
}
