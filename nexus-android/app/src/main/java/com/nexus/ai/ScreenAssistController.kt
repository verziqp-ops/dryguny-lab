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
    private var busy = false
    private var live = false
    private var lastScreenSignature = ""
    private var lastAnalyzeAt = 0L

    fun show() {
        if (root != null) return

        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = rounded(Color.rgb(39, 39, 39), 24)
            elevation = dp(10).toFloat()
        }

        val drag = TextView(service).apply {
            text = "NEXUS  ·  Screen Assist"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(7))
        }
        panel.addView(drag, LinearLayout.LayoutParams(-1, dp(34)))

        messageView = TextView(service).apply {
            text = "Бачу екран. Натисни AI, Переклад або Google."
            setTextColor(Color.rgb(238, 238, 238))
            textSize = 14f
            maxLines = 4
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = rounded(Color.rgb(24, 24, 24), 18)
        }
        panel.addView(messageView, LinearLayout.LayoutParams(-1, -2))

        val actions = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
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
        actions.addView(action("Google") { searchCurrentScreen() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(6)
        })
        actions.addView(action("Чат") {
            val i = Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            service.startActivity(i)
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            marginStart = dp(6)
        })
        actions.addView(action("×") { hide() }, LinearLayout.LayoutParams(dp(44), dp(42)).apply {
            marginStart = dp(6)
        })

        panel.addView(actions, LinearLayout.LayoutParams(-1, -2))

        val bounds = wm.currentWindowMetrics.bounds
        val p = WindowManager.LayoutParams(
            max(dp(260), bounds.width() - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = max(dp(60), bounds.height() - dp(240))
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
                    p.x = nx.coerceIn(0, max(0, b.width() - p.width))
                    p.y = ny.coerceIn(0, max(0, b.height() - dp(120)))
                    try { wm.updateViewLayout(panel, p) } catch (_: Exception) {}
                    true
                }
                else -> true
            }
        }

        root = panel
        params = p
        try {
            wm.addView(panel, p)
            panel.alpha = 0f
            panel.translationY = dp(24).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        } catch (_: Exception) {
            root = null
            params = null
        }
    }

    fun hide() {
        live = false
        root?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        root = null
        params = null
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
        val prompt = buildString {
            append("Ти екранний помічник Android. Подивись на поточний екран і дай ОДНУ коротку корисну підказку українською. ")
            append("Якщо користувачу варто натиснути конкретний видимий елемент, в кінці додай директиву [[tap:ТОЧНИЙ ТЕКСТ ЕЛЕМЕНТА]]. ")
            append("Якщо для корисної відповіді потрібна свіжа інформація з інтернету, додай [[google:КОРОТКИЙ ПОШУКОВИЙ ЗАПИТ]]. ")
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

    private fun searchCurrentScreen() {
        if (busy) return
        val visible = service.screenText().replace(" · ", " ").take(260).trim()
        if (visible.isBlank()) {
            message("Не бачу тексту для пошуку.")
            return
        }

        busy = true
        message("Шукаю в Google…")
        scope.launch {
            val result = GoogleWebSearch.search(visible)
            if (!result.ok) {
                busy = false
                message(result.text)
                return@launch
            }

            val prompt = "На екрані користувача є тема:\n" + visible +
                "\n\nОсь текст результатів Google:\n" + result.text.take(12000) +
                "\n\nДай коротку корисну відповідь українською лише на основі релевантних результатів."
            busy = false
            askModel(prompt, includeScreen = false)
        }
    }

    private fun askModel(prompt: String, includeScreen: Boolean) {
        if (busy) return
        val engine = NexusRuntime.engine(service)
        if (!engine.isLoaded) {
            message("Спочатку запусти локальну модель у NEXUS.")
            return
        }
        if (engine.isGenerating) {
            message("NEXUS зараз відповідає в чаті.")
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
                val full = out.toString().trim()
                val target = Regex("\\[\\[tap:(.+?)]]", RegexOption.IGNORE_CASE)
                    .find(full)?.groupValues?.getOrNull(1)?.trim()
                if (!target.isNullOrBlank()) service.highlightText(target)

                val googleQuery = Regex("\\[\\[google:(.+?)]]", RegexOption.IGNORE_CASE)
                    .find(full)?.groupValues?.getOrNull(1)?.trim()
                if (!googleQuery.isNullOrBlank()) {
                    searchForAssist(googleQuery)
                } else {
                    val clean = stripDirectives(full)
                    message(if (clean.isBlank()) "Готово." else clean)
                }
            },
            onError = { error ->
                busy = false
                message(error)
            }
        )
    }

    private fun stripDirectives(text: String): String =
        text
            .replace(Regex("\\s*\\[\\[tap:.+?]]\\s*", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s*\\[\\[google:.+?]]\\s*", RegexOption.IGNORE_CASE), " ")
            .trim()

    private fun searchForAssist(query: String) {
        if (busy) return
        busy = true
        message("Шукаю в Google…")
        scope.launch {
            val result = GoogleWebSearch.search(query)
            if (!result.ok) {
                busy = false
                message(result.text)
                return@launch
            }

            val prompt = "Пошуковий запит: " + query +
                "\n\nТекст результатів Google:\n" + result.text.take(12000) +
                "\n\nДай коротку точну відповідь українською. Не вигадуй нічого поза релевантними результатами."
            busy = false
            askModel(prompt, includeScreen = false)
        }
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
