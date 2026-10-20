package com.nexus.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val REQ_ATTACH = 201
        private const val REQ_MODEL = 202
        private const val REQ_FOLDER = 203
        private const val REQ_AUDIO = 204
    }

    private lateinit var chatScroll: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var attachmentStrip: LinearLayout
    private lateinit var modelStatus: TextView
    private lateinit var screenButton: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val attachments = mutableListOf<NexusAttachment>()
    private val messages = mutableListOf<StoredMessage>()

    private lateinit var store: ChatStore
    private lateinit var attachmentManager: AttachmentManager
    private lateinit var fileIndex: LocalFileIndex
    private lateinit var llm: LocalLlmEngine

    private var recognizer: SpeechRecognizer? = null
    private var pendingScreenAttachment = false
    private var loadingModel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChatStore(this)
        attachmentManager = AttachmentManager(this)
        fileIndex = LocalFileIndex(this)
        llm = LocalLlmEngine(this)

        setContentView(buildUi())
        restoreChat()
        handleIncomingIntent(intent)
        loadSavedModel()
        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateHeader()
        if (pendingScreenAttachment && ScreenCaptureService.isRunning) {
            ScreenCaptureService.requestFreshCapture()
            handler.postDelayed({ attachLatestScreen() }, 850)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        llm.close()
        super.onDestroy()
    }

    private fun buildUi(): View {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        root.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(64)))

        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
        }
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        chatScroll.addView(chatContainer, android.widget.FrameLayout.LayoutParams(-1, -2))
        root.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val attachScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.BLACK)
        }
        attachmentStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        attachScroll.addView(attachmentStrip, android.widget.FrameLayout.LayoutParams(-2, dp(44)))
        root.addView(attachScroll, LinearLayout.LayoutParams(-1, dp(52)))

        root.addView(buildComposer(), LinearLayout.LayoutParams(-1, dp(78)))
        return root
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(10), dp(6))
            setBackgroundColor(Color.BLACK)
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "NEXUS"
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        modelStatus = TextView(this).apply {
            text = "OFFLINE"
            setTextColor(Color.rgb(150, 150, 150))
            textSize = 11f
        }
        titleBox.addView(title)
        titleBox.addView(modelStatus)
        row.addView(titleBox, LinearLayout.LayoutParams(0, -1, 1f))

        val screen = iconButton(R.drawable.ic_screen, "Захоплення екрана")
        screenButton = screen
        screen.setOnClickListener { onScreenButton() }
        row.addView(screen, LinearLayout.LayoutParams(dp(48), dp(48)))

        val model = iconButton(R.drawable.ic_model, "Локальна модель")
        model.setOnClickListener { chooseModel() }
        row.addView(model, LinearLayout.LayoutParams(dp(48), dp(48)))

        val settings = iconButton(R.drawable.ic_settings, "Налаштування")
        settings.setOnClickListener { showSettings() }
        row.addView(settings, LinearLayout.LayoutParams(dp(48), dp(48)))
        return row
    }

    private fun buildComposer(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            setBackgroundColor(Color.BLACK)
        }

        val attach = iconButton(R.drawable.ic_attach, "Додати файл")
        attach.setOnClickListener { chooseAttachments() }
        outer.addView(attach, LinearLayout.LayoutParams(dp(48), dp(48)))

        val mic = iconButton(R.drawable.ic_mic, "Голос")
        mic.setOnClickListener { startVoice() }
        outer.addView(mic, LinearLayout.LayoutParams(dp(48), dp(48)))

        input = EditText(this).apply {
            hint = "Повідомлення NEXUS"
            setHintTextColor(Color.rgb(130, 130, 130))
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 5
            setPadding(dp(16), 0, dp(12), 0)
            background = rounded(Color.rgb(28, 28, 28), 22)
        }
        outer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(8)
        })

        val send = ImageButton(this).apply {
            setImageResource(R.drawable.ic_send)
            contentDescription = "Надіслати"
            background = rounded(Color.WHITE, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        send.setOnClickListener { sendCurrent() }
        outer.addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
        return outer
    }

    private fun restoreChat() {
        messages.clear()
        messages += store.load()
        if (messages.isEmpty()) {
            addAssistant(
                "NEXUS працює локально. Для повного офлайн-чату вибери .litertlm модель кнопкою у верхній панелі. " +
                    "Системні дії, пошук файлів у дозволених папках і читання доступного тексту екрана працюють без моделі.",
                persist = true
            )
        } else {
            messages.forEach { addMessageView(it.role, it.text, it.attachmentNames) }
        }
    }

    private fun loadSavedModel() {
        modelStatus.text = "OFFLINE · модель перевіряється"
        llm.loadSaved { ok, message ->
            modelStatus.text = if (ok) "OFFLINE · LOCAL AI" else "OFFLINE · CORE"
            if (!ok && !message.contains("ще не вибрана")) addAssistant(message)
        }
    }

    private fun chooseAttachments() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQ_ATTACH)
    }

    private fun chooseModel() {
        if (loadingModel) return
        AlertDialog.Builder(this)
            .setTitle("Локальна модель")
            .setMessage(
                "NEXUS використовує локальні моделі LiteRT-LM (.litertlm). Файл залишається на телефоні. " +
                    "Сам додаток не має дозволу INTERNET."
            )
            .setPositiveButton("Вибрати модель") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQ_MODEL)
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun chooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQ_FOLDER)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_ATTACH -> importAttachments(data)
            REQ_MODEL -> data.data?.let { importModel(it) }
            REQ_FOLDER -> data.data?.let {
                fileIndex.addFolder(it)
                addAssistant("Папку додано до локального пошуку. NEXUS бачить лише папки, які ти явно дозволив.")
            }
        }
    }

    private fun importAttachments(data: Intent) {
        val uris = mutableListOf<Uri>()
        data.data?.let { uris += it }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
        }
        if (uris.isEmpty()) return
        ioScope.launch {
            val imported = mutableListOf<NexusAttachment>()
            uris.take(8).forEach { uri ->
                try { imported += attachmentManager.importUri(uri) } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                attachments += imported
                renderAttachmentStrip()
            }
        }
    }

    private fun importModel(uri: Uri) {
        loadingModel = true
        modelStatus.text = "OFFLINE · імпорт моделі"
        addAssistant("Імпортую локальну модель у приватне сховище NEXUS.")
        ioScope.launch {
            try {
                val file = attachmentManager.importModel(uri)
                withContext(Dispatchers.Main) {
                    modelStatus.text = "OFFLINE · запуск моделі"
                    llm.loadModel(file.absolutePath) { ok, msg ->
                        loadingModel = false
                        modelStatus.text = if (ok) "OFFLINE · LOCAL AI" else "OFFLINE · CORE"
                        addAssistant(msg)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingModel = false
                    modelStatus.text = "OFFLINE · CORE"
                    addAssistant("Не вдалося імпортувати модель: " + (e.message ?: "невідома помилка"))
                }
            }
        }
    }

    private fun renderAttachmentStrip() {
        attachmentStrip.removeAllViews()
        attachments.forEachIndexed { index, a ->
            val chip = TextView(this).apply {
                text = a.name
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                background = rounded(Color.rgb(35, 35, 35), 14)
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file, 0, R.drawable.ic_close, 0)
                compoundDrawablePadding = dp(6)
                setOnClickListener {
                    if (index < attachments.size) {
                        attachments.removeAt(index)
                        renderAttachmentStrip()
                    }
                }
            }
            attachmentStrip.addView(chip, LinearLayout.LayoutParams(-2, dp(36)).apply {
                marginEnd = dp(8)
            })
        }
    }

    private fun sendCurrent() {
        val text = input.text.toString().trim()
        if (text.isEmpty() && attachments.isEmpty()) return

        val sentAttachments = attachments.toList()
        val names = sentAttachments.map { it.name }
        addUser(if (text.isEmpty()) "Вкладення" else text, names)
        input.setText("")
        attachments.clear()
        renderAttachmentStrip()
        hideKeyboard()

        route(text, sentAttachments)
    }

    private fun route(raw: String, sentAttachments: List<NexusAttachment>) {
        val text = raw.ifBlank { "Проаналізуй вкладення." }
        val low = text.lowercase(Locale.ROOT)

        if (isFileSearch(low)) {
            runFileSearch(text)
            return
        }

        if (isMemoryCommand(low)) {
            handleMemory(text)
            return
        }

        if (isScreenQuestion(low)) {
            handleScreenQuestion(text, sentAttachments)
            return
        }

        if (looksLikeExplicitAction(low)) {
            executeDeviceAction(text)
            return
        }

        sendToLocalAi(text, sentAttachments)
    }

    private fun isFileSearch(s: String): Boolean =
        s.startsWith("знайди файл") || s.startsWith("знайди в телефоні") || s.startsWith("пошукай файл")

    private fun runFileSearch(original: String) {
        val query = original
            .replace(Regex("(?i)^знайди файл"), "")
            .replace(Regex("(?i)^знайди в телефоні"), "")
            .replace(Regex("(?i)^пошукай файл"), "")
            .trim()
        if (query.isEmpty()) {
            addAssistant("Напиши, яку назву або частину назви файла шукати.")
            return
        }
        if (fileIndex.folderCount() == 0) {
            addAssistant("Спочатку додай хоча б одну папку в Налаштуваннях NEXUS. Android не дозволяє тихо сканувати все сховище без твоєї згоди.")
            return
        }
        addAssistant("Шукаю локально у дозволених папках…", persist = false)
        ioScope.launch {
            val hits = fileIndex.search(query)
            val answer =
                if (hits.isEmpty()) "Нічого з «$query» у дозволених папках не знайдено."
                else hits.joinToString(prefix = "Знайдено:\n", separator = "\n") { "• " + it.name }
            withContext(Dispatchers.Main) { addAssistant(answer) }
        }
    }

    private fun isMemoryCommand(s: String): Boolean =
        s.startsWith("запам'ятай ") || s.startsWith("запам’ятай ") || s.startsWith("запамятай ") ||
            s.contains("що ти пам")

    private fun handleMemory(original: String) {
        val low = original.lowercase(Locale.ROOT)
        val prefs = getSharedPreferences("nexus_memory", MODE_PRIVATE)
        if (low.contains("що ти пам")) {
            val value = prefs.getString("note", null)
            addAssistant(if (value.isNullOrBlank()) "Локальна пам’ять порожня." else "Пам’ятаю локально: $value")
        } else {
            val value = original.substringAfter(' ', "").trim()
            prefs.edit().putString("note", value).apply()
            addAssistant("Зберіг це тільки на телефоні.")
        }
    }

    private fun isScreenQuestion(s: String): Boolean =
        s.contains("що на екрані") || s.contains("шо на екрані") || s.contains("подивись екран") ||
            s.contains("подивися екран") || s.contains("переклади екран") || s.contains("розкажи що тут")

    private fun handleScreenQuestion(question: String, originalAttachments: List<NexusAttachment>) {
        val service = NexusAccessibilityService.get()
        val visibleText = service?.screenText().orEmpty()

        val media = originalAttachments.toMutableList()
        ScreenCaptureService.latestCapturePath?.let { path ->
            if (File(path).exists()) media += attachmentManager.fromScreen(path)
        }

        if (llm.isLoaded) {
            val prompt = buildString {
                append(question)
                if (visibleText.isNotBlank()) {
                    append("\n\nТекст, який Android Accessibility бачить на поточному екрані:\n")
                    append(visibleText)
                }
                if (media.none { it.kind == AttachmentKind.SCREEN }) {
                    append("\n\nСкріншот не підключений. Аналізуй доступний текст екрана.")
                }
            }
            sendToLocalAi(prompt, media)
        } else {
            if (visibleText.isNotBlank()) {
                if (question.lowercase().contains("переклади")) addAssistant(basicTranslate(visibleText))
                else addAssistant("На екрані доступний такий текст:\n$visibleText")
            } else {
                addAssistant("Для візуального аналізу скріншота потрібна локальна мультимодальна .litertlm модель. Screen Capture можна ввімкнути кнопкою екрана або плиткою NEXUS Screen.")
            }
        }
    }

    private fun looksLikeExplicitAction(s: String): Boolean {
        val starts = listOf(
            "відкрий ", "відкрий", "зайди ", "натисни ", "нажми ", "прокрути ",
            "повернись назад", "назад", "додому", "головний екран", "знайди на екрані "
        )
        return starts.any { s.startsWith(it) } || s.contains(" і потім ")
    }

    private fun executeDeviceAction(command: String) {
        val service = NexusAccessibilityService.get()
        if (service != null) {
            service.executeCommand(command) { result -> runOnUiThread { addAssistant(result) } }
            return
        }

        val action = simpleSettingsIntent(command.lowercase(Locale.ROOT))
        if (action != null) {
            try {
                startActivity(Intent(action))
                addAssistant("Відкрив системний розділ. Для натискань, скролу та Show Me увімкни Screen Control у Налаштуваннях NEXUS.")
            } catch (_: Exception) {
                addAssistant("Android не дозволив відкрити цей розділ.")
            }
        } else {
            addAssistant("Для цієї дії потрібен Screen Control. Увімкнути його можна в Налаштуваннях NEXUS.")
        }
    }

    private fun simpleSettingsIntent(s: String): String? = when {
        s.contains("пам'ять телефона") || s.contains("пам’ять телефона") || s.contains("сховище") -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        s.contains("wifi") || s.contains("wi-fi") || s.contains("вайфай") -> Settings.ACTION_WIFI_SETTINGS
        s.contains("bluetooth") || s.contains("блютуз") -> Settings.ACTION_BLUETOOTH_SETTINGS
        s.contains("батаре") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        s.contains("екран") || s.contains("яскрав") -> Settings.ACTION_DISPLAY_SETTINGS
        s.contains("додатк") -> Settings.ACTION_APPLICATION_SETTINGS
        s.contains("налаштуван") -> Settings.ACTION_SETTINGS
        else -> null
    }

    private fun sendToLocalAi(prompt: String, sentAttachments: List<NexusAttachment>) {
        if (!llm.isLoaded) {
            addAssistant(
                "Повна локальна модель ще не вибрана. Натисни кнопку моделі у верхній панелі та імпортуй .litertlm. " +
                    "Після цього чат працюватиме без інтернету."
            )
            return
        }

        val fileContext = buildString {
            sentAttachments.filter { it.kind == AttachmentKind.TEXT }.forEach { a ->
                val txt = attachmentManager.readText(a)
                if (txt.isNotBlank()) {
                    append("\n\nЛокальний файл «${a.name}»:\n")
                    append(txt)
                }
            }
            sentAttachments.filter { it.kind == AttachmentKind.FILE }.forEach { a ->
                append("\n\nДодано локальний файл «${a.name}» типу ${a.mime}.")
            }
        }

        val assistantView = addStreamingAssistant()
        val buffer = StringBuilder()
        llm.send(
            prompt + fileContext,
            sentAttachments,
            onChunk = { chunk ->
                buffer.append(chunk)
                assistantView.text = buffer.toString()
                scrollBottom()
            },
            onDone = {
                val finalText = buffer.toString().ifBlank { "Модель не повернула текстової відповіді." }
                assistantView.text = finalText
                messages += StoredMessage("assistant", finalText)
                store.save(messages)
                scrollBottom()
            },
            onError = { error ->
                val finalText = "Помилка локальної моделі: $error"
                assistantView.text = finalText
                messages += StoredMessage("assistant", finalText)
                store.save(messages)
                scrollBottom()
            }
        )
    }

    private fun onScreenButton() {
        if (!ScreenCaptureService.isRunning) {
            pendingScreenAttachment = true
            startActivity(Intent(this, CapturePermissionActivity::class.java))
            return
        }
        pendingScreenAttachment = true
        ScreenCaptureService.requestFreshCapture()
        handler.postDelayed({ attachLatestScreen() }, 650)
    }

    private fun attachLatestScreen() {
        val path = ScreenCaptureService.latestCapturePath
        if (path != null && File(path).exists()) {
            val existing = attachments.indexOfFirst { it.kind == AttachmentKind.SCREEN }
            if (existing >= 0) attachments.removeAt(existing)
            attachments += attachmentManager.fromScreen(path)
            renderAttachmentStrip()
            pendingScreenAttachment = false
            Toast.makeText(this, "Скріншот додано до повідомлення", Toast.LENGTH_SHORT).show()
        } else if (ScreenCaptureService.isRunning) {
            handler.postDelayed({ attachLatestScreen() }, 600)
        }
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }

        fun addButton(label: String, action: () -> Unit) {
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            }
            box.addView(b, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(6) })
        }

        val info = TextView(this).apply {
            setTextColor(Color.DKGRAY)
            textSize = 14f
            text = buildString {
                append("Режим: повністю локальний\n")
                append("Internet permission: відсутній\n")
                append("Local AI: ").append(if (llm.isLoaded) "активний" else "модель не вибрана").append("\n")
                append("Screen Control: ").append(if (NexusAccessibilityService.isEnabled()) "увімкнений" else "вимкнений").append("\n")
                append("Screen Capture: ").append(if (ScreenCaptureService.isRunning) "активний" else "вимкнений").append("\n")
                append("Папок для пошуку: ").append(fileIndex.folderCount())
            }
        }
        box.addView(info, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        addButton("Screen Control") { showAccessibilityDisclosure() }
        addButton("Додати папку для пошуку") { chooseFolder() }
        addButton("Додати NEXUS Screen у шторку") { requestTile() }
        addButton(if (ScreenCaptureService.isRunning) "Зупинити захоплення екрана" else "Запустити захоплення екрана") {
            if (ScreenCaptureService.isRunning) ScreenCaptureService.stop(this)
            else {
                pendingScreenAttachment = false
                startActivity(Intent(this, CapturePermissionActivity::class.java))
            }
        }
        addButton("Очистити чат") {
            messages.clear()
            store.clear()
            chatContainer.removeAllViews()
            addAssistant("Новий локальний чат.", persist = true)
        }

        AlertDialog.Builder(this)
            .setTitle("NEXUS")
            .setView(box)
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Screen Control")
            .setMessage(
                "Якщо ти ввімкнеш Screen Control, NEXUS зможе читати доступний текст поточного інтерфейсу " +
                    "та виконувати натискання, скрол, Назад і Додому лише після твоєї команди. " +
                    "NEXUS не має дозволу INTERNET і не надсилає вміст екрана на сервер."
            )
            .setPositiveButton("Розумію, відкрити налаштування") { _, _ ->
                getSharedPreferences("nexus_security", MODE_PRIVATE).edit()
                    .putBoolean("accessibility_disclosure", true).apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun requestTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val component = ComponentName(this, NexusTileService::class.java)
            val statusBar = getSystemService(StatusBarManager::class.java)
            statusBar.requestAddTileService(
                component,
                "NEXUS Screen",
                Icon.createWithResource(this, R.drawable.ic_screen),
                mainExecutor
            ) { result: Int ->
                Toast.makeText(this, "Результат додавання плитки: $result", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Відкрий редагування швидких налаштувань і додай NEXUS Screen.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            addAssistant("На цьому пристрої Android не повідомляє про доступний on-device розпізнавач мовлення. Текстовий чат працює повністю офлайн.")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { input.hint = "Слухаю…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { input.hint = "Повідомлення NEXUS" }
            override fun onError(error: Int) {
                input.hint = "Повідомлення NEXUS"
                addAssistant("Не вдалося розпізнати голос локально.")
            }
            override fun onResults(results: Bundle?) {
                input.hint = "Повідомлення NEXUS"
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) input.setText(list[0])
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) input.setText(list[0])
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQ_AUDIO && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 299)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_PROCESS_TEXT == intent.action) {
            val selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!selected.isNullOrBlank()) {
                input.setText("Поясни цей текст:\n$selected")
                input.setSelection(input.text.length)
            }
        }
    }

    private fun addUser(text: String, attachmentNames: List<String> = emptyList()) {
        addMessageView("user", text, attachmentNames)
        messages += StoredMessage("user", text, attachmentNames)
        store.save(messages)
    }

    private fun addAssistant(text: String, persist: Boolean = true) {
        addMessageView("assistant", text, emptyList())
        if (persist) {
            messages += StoredMessage("assistant", text)
            store.save(messages)
        }
    }

    private fun addStreamingAssistant(): TextView {
        val row = messageRow(false)
        val bubble = TextView(this).apply {
            setTextColor(Color.rgb(235, 235, 235))
            textSize = 16f
            setLineSpacing(0f, 1.08f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.rgb(16, 16, 16), 17)
        }
        row.addView(bubble, LinearLayout.LayoutParams(0, -2, 0.92f))
        chatContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        scrollBottom()
        return bubble
    }

    private fun addMessageView(role: String, text: String, attachmentNames: List<String>) {
        val user = role == "user"
        val row = messageRow(user)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(if (user) Color.rgb(42, 42, 42) else Color.rgb(16, 16, 16), 17)
        }

        if (attachmentNames.isNotEmpty()) {
            attachmentNames.forEach { name ->
                val attachment = TextView(this).apply {
                    setTextColor(Color.rgb(190, 190, 190))
                    textSize = 12f
                    this.text = name
                    setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file, 0, 0, 0)
                    compoundDrawablePadding = dp(6)
                }
                box.addView(attachment, LinearLayout.LayoutParams(-1, dp(28)))
            }
        }

        val body = TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(240, 240, 240))
            textSize = 16f
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.08f)
        }
        box.addView(body, LinearLayout.LayoutParams(-1, -2))

        row.addView(box, LinearLayout.LayoutParams(0, -2, if (user) 0.82f else 0.92f))
        chatContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        scrollBottom()
    }

    private fun messageRow(user: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = if (user) Gravity.END else Gravity.START
        if (user) setPadding(dp(54), 0, 0, 0) else setPadding(0, 0, dp(28), 0)
    }

    private fun updateHeader() {
        modelStatus.text = when {
            llm.isLoaded -> "OFFLINE · LOCAL AI"
            loadingModel -> "OFFLINE · модель завантажується"
            else -> "OFFLINE · CORE"
        }
        screenButton.alpha = if (ScreenCaptureService.isRunning) 1f else 0.65f
    }

    private fun basicTranslate(text: String): String {
        val dictionary = linkedMapOf(
            "settings" to "налаштування",
            "storage" to "пам’ять",
            "battery" to "батарея",
            "display" to "екран",
            "sound" to "звук",
            "notifications" to "сповіщення",
            "privacy" to "конфіденційність",
            "security" to "безпека",
            "network" to "мережа",
            "apps" to "додатки",
            "permissions" to "дозволи",
            "camera" to "камера",
            "microphone" to "мікрофон",
            "files" to "файли",
            "delete" to "видалити",
            "save" to "зберегти",
            "open" to "відкрити",
            "back" to "назад",
            "search" to "пошук"
        )
        var out = text
        dictionary.forEach { (en, uk) ->
            out = out.replace(Regex("(?i)\\b" + Regex.escape(en) + "\\b"), uk)
        }
        return if (out == text) "Для повного перекладу екрана завантаж локальну мовну модель.\n\n$text" else out
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun scrollBottom() {
        handler.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun iconButton(drawable: Int, description: String): ImageButton = ImageButton(this).apply {
        setImageResource(drawable)
        contentDescription = description
        background = null
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
