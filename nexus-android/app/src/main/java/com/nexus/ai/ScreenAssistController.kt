package com.nexus.ai

import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

class ScreenAssistController(
    private val service: NexusAccessibilityService,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val attachmentManager = AttachmentManager(service)

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var messageView: TextView? = null
    private var liveButton: TextView? = null
    private var input: EditText? = null
    private var sendButton: TextView? = null
    private var pendingQuestion: String? = null
    private var busy = false
    private var live = false
    private var lastScreenSignature = ""
    private var lastAnalyzeAt = 0L

    fun show() {
        if (root != null) return

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = null
        }

        messageView = TextView(service).apply {
            text = "Бачу екран."
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 6
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(Color.rgb(38, 38, 38), 22)
            elevation = dp(8).toFloat()
        }
        container.addView(
            messageView,
            LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                bottomMargin = dp(8)
            }
        )

        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            background = rounded(Color.rgb(30, 30, 30), 24)
            elevation = dp(10).toFloat()
        }

        val drag = TextView(service).apply {
            text = "NEXUS  ·  Screen Assist"
            setTextColor(Color.rgb(205, 205, 205))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(8), dp(5))
        }
        panel.addView(drag, LinearLayout.LayoutParams(-1, dp(30)))

        val inputRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, 0, 0, dp(7))
        }

        input = EditText(service).apply {
            hint = "Запитай про екран…"
            setHintTextColor(Color.rgb(165, 165, 165))
            setTextColor(Color.WHITE)
            textSize = 14f
            minLines = 1
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            setHorizontallyScrolling(false)
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(Color.rgb(47, 47, 47), 18)
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        sendButton = TextView(service).apply {
            text = "↑"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 20)
            setOnClickListener {
                val q = input?.text?.toString()?.trim().orEmpty()

                if (busy) {
                    if (q.isNotEmpty()) {
                        pendingQuestion = q
                        input?.setText("")
                    }
                    cancelOverlayGeneration()
                } else if (q.isNotEmpty()) {
                    input?.setText("")
                    ask(q)
                }
            }
        }
        inputRow.addView(sendButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            marginStart = dp(7)
        })
        panel.addView(inputRow, LinearLayout.LayoutParams(-1, -2))

        val actions = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        liveButton = action("AI") {
            live = !live
            liveButton?.text = if (live) "AI · LIVE" else "AI"
            if (live) {
                message("Live аналіз увімкнено")
                analyzeAssist()
            } else {
                message("Live аналіз вимкнено")
            }
        }
        actions.addView(liveButton, LinearLayout.LayoutParams(0, dp(42), 1f))

        actions.addView(action("Переклад") { translateScreen() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(6)
        })
        actions.addView(action("×") { hide() }, LinearLayout.LayoutParams(dp(44), dp(42)).apply {
            marginStart = dp(6)
        })

        panel.addView(actions, LinearLayout.LayoutParams(-1, -2))
        container.addView(panel, LinearLayout.LayoutParams(-1, -2))

        val bounds = wm.currentWindowMetrics.bounds
        val width = max(dp(280), bounds.width() - dp(24))
        val p = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = max(dp(60), bounds.height() - dp(300))
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        drag.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = p.x
                    startY = p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val nx = startX + (event.rawX - downX).toInt()
                    val ny = startY + (event.rawY - downY).toInt()
                    val b = wm.currentWindowMetrics.bounds
                    p.x = nx.coerceIn(0, max(0, b.width() - width))
                    p.y = ny.coerceIn(0, max(0, b.height() - dp(130)))
                    try { wm.updateViewLayout(container, p) } catch (_: Exception) {}
                    true
                }
                else -> true
            }
        }

        root = container
        params = p
        try {
            wm.addView(container, p)
            container.alpha = 0f
            container.translationY = dp(24).toFloat()
            container.animate().alpha(1f).translationY(0f).setDuration(180).start()

            input?.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    handler.postDelayed({
                        val imm = service.getSystemService(InputMethodManager::class.java)
                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)

                        handler.postDelayed({
                            try {
                                val imeBottom = container.rootWindowInsets
                                    ?.getInsets(android.view.WindowInsets.Type.ime())
                                    ?.bottom ?: 0
                                if (imeBottom > 0) {
                                    val b = wm.currentWindowMetrics.bounds
                                    val h = container.height.coerceAtLeast(dp(180))
                                    p.y = max(dp(48), b.height() - imeBottom - h - dp(10))
                                    wm.updateViewLayout(container, p)
                                }
                            } catch (_: Exception) {}
                        }, 180)
                    }, 100)
                }
            }
        } catch (_: Exception) {
            root = null
            params = null
        }
    }

    fun ask(question: String) {
        show()
        if (question.isBlank()) return

        if (busy) {
            pendingQuestion = question
            cancelOverlayGeneration()
            return
        }

        val visible = service.screenText().take(6000)
        val online = NetworkState.hasInternet(service)
        val prompt = buildString {
            append("Користувач зараз показує екран і питає: ")
            append(question)
            append("\n\nВідповідай українською тільки у Screen Assist. ")
            append("Враховуй помилки в написанні й відновлюй найімовірніший зміст. ")
            append("Якщо треба натиснути видимий елемент, додай [[tap:ТОЧНИЙ ТЕКСТ ЕЛЕМЕНТА]]. ")
            if (online) {
                append("Інтернет доступний. Якщо питання явно потребує свіжих/актуальних даних або користувач просить пошук, ОБОВ'ЯЗКОВО додай [[google:КОРОТКИЙ ПОШУКОВИЙ ЗАПИТ]] замість вигадування. ")
            } else {
                append("Інтернет недоступний, тому не проси веб-пошук і відповідай лише з локальних знань та екрана. ")
            }
            append("\n\n")
            if (visible.isNotBlank()) {
                append("Текст поточного екрана:\n")
                append(visible)
            }
        }
        askModel(prompt, includeScreen = true)
    }

    fun hide() {
        live = false
        handler.removeCallbacks(liveRunnable)
        pendingQuestion = null
        if (NexusRuntime.engine(service).generationOwner == "screen") {
            NexusRuntime.engine(service).cancelGeneration("screen")
        }
        busy = false
        resetSendButton()
        hideIme()
        root?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        root = null
        params = null
        input = null
        sendButton = null
        messageView = null
        liveButton = null
    }

    fun onScreenChanged() {
        if (!live || busy || root == null) return
        val text = service.screenText().take(1200)
        val signature = text.hashCode().toString()
        val now = System.currentTimeMillis()
        if (signature == lastScreenSignature || now - lastAnalyzeAt < 2200L) return
        lastScreenSignature = signature
        handler.removeCallbacks(liveRunnable)
        handler.postDelayed(liveRunnable, 650)
    }

    private val liveRunnable = Runnable {
        if (live && !busy && root != null) analyzeAssist()
    }

    private fun analyzeAssist() {
        val text = service.screenText().take(5000)
        val online = NetworkState.hasInternet(service)
        val prompt = buildString {
            append("Ти екранний помічник Android. Подивись на поточний екран і дай ОДНУ коротку корисну підказку українською. ")
            append("Якщо користувачу варто натиснути конкретний видимий елемент, в кінці додай [[tap:ТОЧНИЙ ТЕКСТ ЕЛЕМЕНТА]]. ")
            if (online) {
                append("Якщо потрібна свіжа інформація з інтернету, додай [[google:КОРОТКИЙ ПОШУКОВИЙ ЗАПИТ]]. ")
            }
            append("Не вигадуй елементи, яких нема. Якщо підказка не потрібна — скажи це коротко.\n\n")
            append("Текст інтерфейсу:\n")
            append(text)
        }
        askModel(prompt, includeScreen = true)
    }

    private fun translateScreen() {
        val text = service.screenText().take(6500)
        val prompt = "Переклади видимий текст екрана українською. Не пояснюй, дай короткий структурований переклад.\n\n" + text
        askModel(prompt, includeScreen = true)
    }

    private fun askModel(prompt: String, includeScreen: Boolean) {
        if (busy) return
        val engine = NexusRuntime.engine(service)
        if (!engine.isLoaded) {
            message("Спочатку запусти локальну модель у NEXUS.")
            return
        }
        if (engine.isGenerating) {
            if (engine.generationOwner == "screen") {
                pendingQuestion = prompt
                engine.cancelGeneration("screen")
            } else {
                message("NEXUS зараз відповідає в основному чаті.")
            }
            return
        }

        val attachments = mutableListOf<NexusAttachment>()
        if (includeScreen) {
            ScreenCaptureService.requestFreshCapture()
            val path = ScreenCaptureService.latestCapturePath
            if (path != null && File(path).exists()) {
                attachments += attachmentManager.fromScreen(path)
            }
        }

        busy = true
        sendButton?.text = "■"
        sendButton?.textSize = 16f
        lastAnalyzeAt = System.currentTimeMillis()
        message("●  ●  ●")
        val out = StringBuilder()

        engine.sendStateless(
            prompt = prompt,
            attachments = attachments,
            onChunk = { chunk ->
                out.append(chunk)
                val clean = stripDirectives(out.toString())
                if (clean.isNotBlank()) message(clean)
            },
            onDone = {
                busy = false
                resetSendButton()
                val full = out.toString().trim()

                val target = Regex("\\[\\[tap:(.+?)]]", RegexOption.IGNORE_CASE)
                    .find(full)?.groupValues?.getOrNull(1)?.trim()
                if (!target.isNullOrBlank()) service.highlightText(target)

                val googleQuery = Regex("\\[\\[google:(.+?)]]", RegexOption.IGNORE_CASE)
                    .find(full)?.groupValues?.getOrNull(1)?.trim()

                if (!googleQuery.isNullOrBlank() && NetworkState.hasInternet(service)) {
                    searchForAssist(googleQuery)
                } else {
                    val clean = stripDirectives(full)
                    message(if (clean.isBlank()) "Готово." else clean)
                }
            },
            onCancelled = {
                busy = false
                resetSendButton()
                val clean = stripDirectives(out.toString())
                if (clean.isNotBlank()) message(clean) else message("Зупинено.")

                val next = pendingQuestion
                pendingQuestion = null
                if (!next.isNullOrBlank() && root != null) {
                    handler.postDelayed({ ask(next) }, 100)
                }
            },
            onError = { error ->
                busy = false
                resetSendButton()
                message(error)

                val next = pendingQuestion
                pendingQuestion = null
                if (!next.isNullOrBlank() && root != null) {
                    handler.postDelayed({ ask(next) }, 120)
                }
            }
        )
    }

    private fun stripDirectives(text: String): String =
        text
            .replace(Regex("\\s*\\[\\[tap:.+?]]\\s*", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s*\\[\\[google:.+?]]\\s*", RegexOption.IGNORE_CASE), " ")
            .trim()

    private fun searchForAssist(query: String) {
        if (busy || !NetworkState.hasInternet(service)) return

        busy = true
        message("Шукаю…")
        scope.launch {
            val result = GoogleWebSearch.search(query)
            if (!result.ok) {
                busy = false
                message("Не вдалося отримати веб-дані. Відповідаю локально.")
                return@launch
            }

            val prompt = "Пошуковий запит: " + query +
                "\n\nТекст актуальних результатів пошуку:\n" + result.text.take(12000) +
                "\n\nДай коротку точну відповідь українською. Не вигадуй нічого поза релевантними результатами."
            busy = false
            askModel(prompt, includeScreen = false)
        }
    }

    private fun cancelOverlayGeneration() {
        val cancelled = NexusRuntime.engine(service).cancelGeneration("screen")
        if (!cancelled) {
            busy = false
            resetSendButton()
        }
    }

    private fun resetSendButton() {
        sendButton?.text = "↑"
        sendButton?.textSize = 24f
    }

    private fun hideIme() {
        val v = input ?: root ?: return
        try {
            service.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(v.windowToken, 0)
        } catch (_: Exception) {}
        input?.clearFocus()
    }

    private fun message(text: String) {
        messageView?.text = text
    }

    private fun action(label: String, click: () -> Unit): TextView =
        TextView(service).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(55, 55, 55), 16)
            setOnClickListener { click() }
        }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density + 0.5f).toInt()
}
