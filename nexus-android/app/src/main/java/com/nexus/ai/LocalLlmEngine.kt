package com.nexus.ai

import android.content.Context
import android.os.Build
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
import com.google.ai.edge.litertlm.NoRepeatNgramConfig
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class LocalLlmEngine(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var historySnapshot: MutableList<StoredMessage> = mutableListOf()
    private var supportsThinkingModel: Boolean = false
    private val loadLock = Any()
    private var loadingPath: String? = null
    private val loadingCallbacks = mutableListOf<(Boolean, String) -> Unit>()

    @Volatile var isGenerating: Boolean = false
        private set

    @Volatile private var cancelRequested: Boolean = false

    @Volatile var isLoaded: Boolean = false
        private set

    @Volatile var mediaPipelineEnabled: Boolean = false
        private set

    var modelPath: String? = null
        private set

    @Volatile private var languageFilter: Boolean =
        context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)
            .getBoolean("language_filter", false)

    @Volatile private var replyLanguage: String =
        context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)
            .getString("reply_language", "auto") ?: "auto"

    fun isLoading(path: String): Boolean =
        synchronized(loadLock) { loadingPath == path }

    fun loadModel(path: String, onDone: (Boolean, String) -> Unit) {
        if (isLoaded && modelPath == path && conversation != null) {
            main.post { onDone(true, "Модель вже активна") }
            return
        }

        synchronized(loadLock) {
            if (loadingPath == path) {
                loadingCallbacks += onDone
                return
            }
            if (loadingPath != null) {
                main.post { onDone(false, "Інша модель ще запускається. Дочекайся завершення.") }
                return
            }
            loadingPath = path
            loadingCallbacks += onDone
        }

        scope.launch {
            val file = File(path)
            if (!file.exists()) {
                finishModelLoad(path, false, "Файл моделі не знайдено.")
                return@launch
            }

            closeInternal()
            val wantsVision = isVisionModel(path)
            val lowerName = file.name.lowercase(Locale.ROOT)
            supportsThinkingModel = lowerName.contains("qwen3.5")

            val preferredTokens = when {
                lowerName.contains("gemma-4-e4b") -> 2048
                lowerName.contains("gemma-4-e2b") -> 2048
                file.name.contains("4B", ignoreCase = true) -> 1536
                file.name.contains("2B", ignoreCase = true) -> 1280
                else -> 1024
            }

            fun markLoaded(message: String) {
                isLoaded = true
                modelPath = path
                context.getSharedPreferences("nexus_model", Context.MODE_PRIVATE)
                    .edit().putString("model_path", path).apply()
                finishModelLoad(path, true, message)
            }

            val isPixel7Pro = Build.MODEL.contains("Pixel 7 Pro", ignoreCase = true)
            val preferGpu = lowerName.contains("gemma-4") && !isPixel7Pro

            try {
                initialize(path, preferredTokens, wantsVision, useGpu = preferGpu)
                markLoaded(if (preferGpu) "GPU" else "CPU")
            } catch (first: Throwable) {
                try {
                    closeInternal()
                    supportsThinkingModel = lowerName.contains("qwen3.5")
                    val fallbackTokens =
                        if (lowerName.contains("gemma-4-e4b")) 1280
                        else if (lowerName.contains("gemma-4-e2b")) 1536
                        else 768
                    initialize(path, fallbackTokens, wantsVision, useGpu = false)
                    markLoaded("CPU · сумісний режим")
                } catch (second: Throwable) {
                    closeInternal()
                    finishModelLoad(path, false, friendlyError(second))
                }
            }
        }
    }

    private fun finishModelLoad(path: String, ok: Boolean, message: String) {
        val callbacks = synchronized(loadLock) {
            if (loadingPath != path) return
            loadingPath = null
            loadingCallbacks.toList().also { loadingCallbacks.clear() }
        }
        main.post {
            callbacks.forEach { callback ->
                try { callback(ok, message) } catch (_: Throwable) {}
            }
        }
    }

    private fun initialize(path: String, maxTokens: Int, vision: Boolean, useGpu: Boolean) {
        var newEngine: Engine? = null
        var newConversation: Conversation? = null
        try {
            val cache = File(context.cacheDir, "litert_" + File(path).name.hashCode()).apply { mkdirs() }
            val mainBackend: Backend =
                if (useGpu) Backend.GPU() else Backend.CPU(threadCount = 4)
            val visualBackend: Backend? =
                if (!vision) null
                else if (useGpu) Backend.GPU() else Backend.CPU(threadCount = 4)

            val config = EngineConfig(
                modelPath = path,
                backend = mainBackend,
                visionBackend = visualBackend,
                maxNumTokens = maxTokens,
                cacheDir = cache.absolutePath,
            )

            newEngine = Engine(config)
            newEngine.initialize()
            newConversation = newEngine.createConversation(conversationConfig())

            engine = newEngine
            conversation = newConversation
            mediaPipelineEnabled = vision
        } catch (e: Throwable) {
            try { newConversation?.close() } catch (_: Throwable) {}
            try {
                if (newEngine?.isInitialized() == true) newEngine.close()
            } catch (_: Throwable) {}
            throw e
        }
    }

    private fun isVisionModel(path: String): Boolean {
        val name = File(path).name.lowercase(Locale.ROOT)
        return "-vl_" in name ||
            "-vl." in name ||
            "vision" in name ||
            "gemma-4-" in name ||
            "gemma-3n-" in name
    }

    fun setHistory(messages: List<StoredMessage>) {
        historySnapshot = compactHistory(messages).toMutableList()
        val activeEngine = engine ?: return
        scope.launch {
            try {
                conversation?.close()
                conversation = activeEngine.createConversation(conversationConfig())
            } catch (_: Throwable) {}
        }
    }

    fun setLanguageFilter(enabled: Boolean) {
        languageFilter = enabled
        context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("language_filter", enabled).apply()
        rebuildConversation()
    }

    fun setReplyLanguage(mode: String) {
        replyLanguage = mode
        context.getSharedPreferences("nexus_settings", Context.MODE_PRIVATE)
            .edit().putString("reply_language", mode).apply()
        rebuildConversation()
    }

    private fun rebuildConversation() {
        val activeEngine = engine ?: return
        scope.launch {
            try {
                conversation?.close()
                conversation = activeEngine.createConversation(conversationConfig())
            } catch (_: Throwable) {}
        }
    }

    private fun conversationConfig(): ConversationConfig {
        val initial = compactHistory(historySnapshot).mapNotNull { m ->
            when (m.role.lowercase(Locale.ROOT)) {
                "user" -> Message.user(m.text)
                "assistant", "model" -> Message.model(m.text)
                else -> null
            }
        }
        val modelName = modelPath?.let { File(it).name.lowercase(Locale.ROOT) }.orEmpty()
        val maxOutput = when {
            modelName.contains("gemma-4-e4b") -> 896
            modelName.contains("gemma-4-e2b") -> 768
            modelName.contains("qwen3.5-4b") -> 768
            supportsThinkingModel -> 640
            else -> 512
        }

        return ConversationConfig(
            systemInstruction = Contents.of(Content.Text(systemInstruction())),
            initialMessages = initial,
            maxOutputToken = maxOutput,
            thinkingConfig =
                if (supportsThinkingModel)
                    ThinkingConfig(enableThinking = true, thinkingTokenBudget = 128)
                else null,
            enableSpeculativeDecoding = false,
        )
    }

    private fun compactHistory(messages: List<StoredMessage>): List<StoredMessage> {
        if (messages.isEmpty()) return emptyList()

        val selected = mutableListOf<StoredMessage>()
        var remainingChars = 4200
        var lastAssistantNormalized: String? = null

        for (m in messages.asReversed()) {
            if (m.role != "user" && m.role != "assistant" && m.role != "model") continue
            if (remainingChars <= 0 || selected.size >= 10) break

            val cleaned = m.text.trim()
            if (cleaned.isEmpty()) continue

            val isAssistant = m.role == "assistant" || m.role == "model"
            if (isAssistant) {
                val normalized = cleaned.lowercase(Locale.ROOT)
                    .replace(Regex("\\s+"), " ")
                    .take(220)
                if (normalized == lastAssistantNormalized) continue
                lastAssistantNormalized = normalized
            }

            val take = minOf(cleaned.length, 900, remainingChars)
            selected += StoredMessage(m.role, cleaned.take(take))
            remainingChars -= take
        }

        return selected.asReversed()
    }

    private fun systemInstruction(): String {
        val style =
            if (languageFilter) {
                "Avoid profanity and unnecessarily explicit wording."
            } else {
                "Ordinary profanity, slang, and rude language are allowed. You may naturally use profanity when it fits the conversation or when the user asks for it. " +
                    "Never refuse a harmless request merely because it contains swear words, insults, or vulgar wording. " +
                    "Do not replace ordinary swear words with asterisks or euphemisms merely because they are rude. " +
                    "Do not add moralizing warnings about ordinary profanity."
            }

        val languageRule = when (replyLanguage) {
            "uk" -> "Always answer in Ukrainian unless the user explicitly asks for another language."
            "en" -> "Always answer in English unless the user explicitly asks for another language."
            "ru" -> "Always answer in Russian unless the user explicitly asks for another language."
            "pl" -> "Always answer in Polish unless the user explicitly asks for another language."
            "de" -> "Always answer in German unless the user explicitly asks for another language."
            "fr" -> "Always answer in French unless the user explicitly asks for another language."
            "es" -> "Always answer in Spanish unless the user explicitly asks for another language."
            else -> "Detect the language of the user's latest message and answer in that same language. Never default to English when the user wrote in another language."
        }

        return "You are NEXUS, a private on-device Android assistant. " +
            languageRule + " " +
            "Be useful and naturally detailed when the request benefits from detail. Do not answer with a one-line fragment when the user clearly expects an explanation. " +
            "The user often types quickly on a phone and may make heavy spelling mistakes, missing letters, swapped letters, or phonetic typos. " +
            "Infer the most likely intended words from context instead of treating every typo literally. Reconstruct misspelled Ukrainian words, omitted letters, nearby-key mistakes, and short abbreviations from the surrounding turns. Ask a clarification only when there are genuinely multiple plausible meanings. " +
            "If the user asks what they wrote before, use the recent user messages and answer with the actual previous message or a faithful summary of it. " +
            "If the user asks to explain more, write in more detail, continue, or expand, substantially expand the previous answer with new explanation, steps, and examples instead of repeating it. " +
            style + " " +
            "Answer the actual latest request directly. Never invent permissions, device state, features, or actions you did not receive from the app. " +
            "For capability questions, describe only real NEXUS capabilities from the provided context; do not produce placeholder lists or repetitive filler. " +
            "Keep conversational continuity using the provided chat history, but treat the latest user message as the current task. " +
            "If the user changes subject, answer the new subject normally and never repeat an earlier refusal just because it appeared in history. " +
            "Do not confuse benign programming requests such as ordinary games, utilities, or simulations with attacks. " +
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
        historyBeforePrompt: List<StoredMessage> = emptyList(),
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onCancelled: () -> Unit = {},
        onError: (String) -> Unit,
    ) {
        if (!isLoaded || engine == null) {
            onError("Модель ще не готова.")
            return
        }
        if (isGenerating) {
            onError("NEXUS уже генерує відповідь.")
            return
        }

        scope.launch {
            val generated = StringBuilder()
            val finished = AtomicBoolean(false)
            cancelRequested = false
            isGenerating = true

            try {
                if (historyBeforePrompt.isNotEmpty()) {
                    historySnapshot = compactHistory(historyBeforePrompt).toMutableList()
                    conversation?.close()
                    conversation = engine?.createConversation(conversationConfig())
                }

                val conv = conversation
                if (conv == null) {
                    isGenerating = false
                    main.post { onError("Модель ще не готова.") }
                    return@launch
                }

                val contents = mutableListOf<Content>()
                contents += Content.Text(languageHint(prompt) + prompt)

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

                fun finishCancelled() {
                    if (!finished.compareAndSet(false, true)) return
                    isGenerating = false

                    historySnapshot += StoredMessage("user", prompt)
                    if (generated.isNotBlank()) {
                        historySnapshot += StoredMessage("assistant", generated.toString())
                    }
                    historySnapshot = compactHistory(historySnapshot).toMutableList()

                    scope.launch {
                        try {
                            conversation?.close()
                            conversation = engine?.createConversation(conversationConfig())
                        } catch (_: Throwable) {}
                        main.post { onCancelled() }
                    }
                }

                fun finishNormally() {
                    if (!finished.compareAndSet(false, true)) return
                    isGenerating = false

                    historySnapshot += StoredMessage("user", prompt)
                    if (generated.isNotBlank()) {
                        historySnapshot += StoredMessage("assistant", generated.toString())
                    }
                    historySnapshot = compactHistory(historySnapshot).toMutableList()
                    main.post { onDone() }
                }

                conv.sendMessageAsync(
                    Contents.of(contents),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val token = message.toString()
                            if (token.isNotEmpty() && !cancelRequested) {
                                generated.append(token)
                                main.post { onChunk(token) }
                            }
                        }

                        override fun onDone() {
                            if (cancelRequested) finishCancelled() else finishNormally()
                        }

                        override fun onError(throwable: Throwable) {
                            if (cancelRequested ||
                                throwable.message.orEmpty().contains("cancel", ignoreCase = true)
                            ) {
                                finishCancelled()
                            } else if (finished.compareAndSet(false, true)) {
                                isGenerating = false
                                main.post { onError(friendlyError(throwable)) }
                            }
                        }
                    },
                    repetitionPenaltyConfig = RepetitionPenaltyConfig(
                        repetitionPenalty = 1.08f,
                        presencePenalty = 0.05f,
                        frequencyPenalty = 0.05f,
                        windowSize = 256,
                    ),
                    noRepeatNgramConfig = NoRepeatNgramConfig(
                        noRepeatNgramSize = 4,
                        windowSize = 256,
                    ),
                )
            } catch (e: Throwable) {
                if (cancelRequested) {
                    isGenerating = false
                    main.post { onCancelled() }
                } else {
                    isGenerating = false
                    main.post { onError(friendlyError(e)) }
                }
            }
        }
    }

    fun sendStateless(
        prompt: String,
        attachments: List<NexusAttachment> = emptyList(),
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val activeEngine = engine
        if (!isLoaded || activeEngine == null) {
            onError("Модель ще не готова.")
            return
        }
        if (isGenerating) {
            onError("NEXUS зараз відповідає в чаті.")
            return
        }

        isGenerating = true
        scope.launch {
            var temp: Conversation? = null
            try {
                temp = activeEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(Content.Text(systemInstruction())),
                        maxOutputToken = 220,
                        thinkingConfig =
                            if (supportsThinkingModel)
                                ThinkingConfig(enableThinking = true, thinkingTokenBudget = 64)
                            else null,
                        enableSpeculativeDecoding = false,
                    )
                )

                val parts = mutableListOf<Content>(Content.Text(prompt))
                if (mediaPipelineEnabled) {
                    attachments.forEach { a ->
                        when (a.kind) {
                            AttachmentKind.IMAGE, AttachmentKind.SCREEN -> parts += Content.ImageFile(a.path)
                            AttachmentKind.VIDEO -> a.derivedImagePath?.let { parts += Content.ImageFile(it) }
                            else -> Unit
                        }
                    }
                }

                val conv = temp
                conv.sendMessageAsync(
                    Contents.of(parts),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val token = message.toString()
                            if (token.isNotEmpty()) main.post { onChunk(token) }
                        }

                        override fun onDone() {
                            isGenerating = false
                            try { conv.close() } catch (_: Throwable) {}
                            main.post { onDone() }
                        }

                        override fun onError(throwable: Throwable) {
                            isGenerating = false
                            try { conv.close() } catch (_: Throwable) {}
                            main.post { onError(friendlyError(throwable)) }
                        }
                    },
                    repetitionPenaltyConfig = RepetitionPenaltyConfig(
                        repetitionPenalty = 1.08f,
                        presencePenalty = 0.05f,
                        frequencyPenalty = 0.05f,
                        windowSize = 192,
                    ),
                    noRepeatNgramConfig = NoRepeatNgramConfig(
                        noRepeatNgramSize = 4,
                        windowSize = 192,
                    ),
                )
            } catch (e: Throwable) {
                isGenerating = false
                try { temp?.close() } catch (_: Throwable) {}
                main.post { onError(friendlyError(e)) }
            }
        }
    }

    fun cancelGeneration() {
        if (!isGenerating) return
        cancelRequested = true
        try {
            conversation?.cancelProcess()
        } catch (_: Throwable) {}
    }

    private fun languageHint(text: String): String {
        if (replyLanguage != "auto") return ""
        val language = detectLanguage(text)
        return when (language) {
            "uk" -> "[Reply in Ukrainian.]\n"
            "ru" -> "[Reply in Russian.]\n"
            "pl" -> "[Reply in Polish.]\n"
            "de" -> "[Reply in German.]\n"
            "fr" -> "[Reply in French.]\n"
            "es" -> "[Reply in Spanish.]\n"
            "en" -> "[Reply in English.]\n"
            else -> "[Reply in the same language as the user.]\n"
        }
    }

    private fun detectLanguage(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            Regex("[іїєґ]").containsMatchIn(lower) -> "uk"
            Regex("[ąćęłńóśźż]").containsMatchIn(lower) -> "pl"
            Regex("[äöüß]").containsMatchIn(lower) -> "de"
            Regex("[éèêëàâîïôùûüçœ]").containsMatchIn(lower) -> "fr"
            Regex("[ñáéíóúü¿¡]").containsMatchIn(lower) -> "es"
            Regex("[а-яё]").containsMatchIn(lower) -> "ru"
            Regex("[a-z]").containsMatchIn(lower) -> "en"
            else -> "auto"
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
        supportsThinkingModel = false
        isGenerating = false
        cancelRequested = false
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
