package com.nexus.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {
    companion object {
        private const val REQ_FILES = 201
        private const val REQ_MODEL = 202
        private const val REQ_FOLDER = 203
        private const val REQ_AUDIO = 204
        private const val REQ_PHOTO = 205
        private const val REQ_VIDEO = 206
        private const val REQ_CAMERA = 207
    }

    private lateinit var root: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var attachmentScroll: HorizontalScrollView
    private lateinit var attachmentStrip: LinearLayout
    private lateinit var modelStatus: TextView
    private lateinit var screenButton: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val attachments = mutableListOf<NexusAttachment>()

    private lateinit var attachmentManager: AttachmentManager
    private lateinit var fileIndex: LocalFileIndex
    private lateinit var llm: LocalLlmEngine
    private lateinit var modelManager: ModelManager
    private lateinit var conversationStore: ConversationStore

    private var chats = mutableListOf<ChatSession>()
    private lateinit var activeChat: ChatSession

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceReplyPending = false
    private var pendingScreenAttachment = false
    private var pendingCameraUri: Uri? = null
    private var loadingModel = false

    private val prefs by lazy { getSharedPreferences("nexus_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        attachmentManager = AttachmentManager(this)
        fileIndex = LocalFileIndex(this)
        llm = LocalLlmEngine(this)
        modelManager = ModelManager(this)
        conversationStore = ConversationStore(this)

        chats = conversationStore.migrateLegacyIfNeeded()
        val wanted = conversationStore.activeId()
        activeChat = chats.firstOrNull { it.id == wanted } ?: chats.first()
        cleanLegacyNoise()
        llm.setHistory(activeChat.messages)

        root = buildUi()
        setContentView(root)
        configureInsets()
        renderActiveChat()
        handleIncomingIntent(intent)
        initTts()
        loadSelectedModel()
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
            handler.postDelayed({ attachLatestScreen() }, 650)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        llm.close()
        super.onDestroy()
    }

    private fun configureInsets() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(0, bars.top, 0, max(bars.bottom, ime.bottom))
                insets
            }
            root.requestApplyInsets()
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun buildUi(): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            clipToPadding = false
        }

        outer.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(60)))

        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
        }
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
        }
        chatScroll.addView(chatContainer, FrameLayout.LayoutParams(-1, -2))
        outer.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        attachmentScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        attachmentStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(3), dp(12), dp(3))
        }
        attachmentScroll.addView(attachmentStrip, FrameLayout.LayoutParams(-2, dp(42)))
        outer.addView(attachmentScroll, LinearLayout.LayoutParams(-1, dp(48)))

        outer.addView(buildComposer(), LinearLayout.LayoutParams(-1, dp(72)))
        return outer
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.BLACK)
        }

        val menu = iconButton(R.drawable.ic_menu, "Чати")
        menu.setOnClickListener { showChatDrawer() }
        row.addView(menu, LinearLayout.LayoutParams(dp(50), dp(50)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
        }
        val title = TextView(this).apply {
            text = "NEXUS"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        modelStatus = TextView(this).apply {
            text = "OFFLINE"
            setTextColor(Color.rgb(145, 145, 145))
            textSize = 11f
            setOnClickListener { showModelManager() }
        }
        titleBox.addView(title)
        titleBox.addView(modelStatus)
        row.addView(titleBox, LinearLayout.LayoutParams(0, -1, 1f))

        screenButton = iconButton(R.drawable.ic_screen, "Захоплення екрана")
        screenButton.setOnClickListener { onScreenButton() }
        row.addView(screenButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        val model = iconButton(R.drawable.ic_model, "Моделі")
        model.setOnClickListener { showModelManager() }
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
            setPadding(dp(10), dp(5), dp(10), dp(9))
            setBackgroundColor(Color.BLACK)
        }

        val attach = iconButton(R.drawable.ic_plus, "Додати")
        attach.background = rounded(Color.rgb(34, 34, 34), 24)
        attach.setPadding(dp(12), dp(12), dp(12), dp(12))
        attach.setOnClickListener { showAttachmentSheet() }
        outer.addView(attach, LinearLayout.LayoutParams(dp(48), dp(48)))

        input = EditText(this).apply {
            hint = "Повідомлення NEXUS"
            setHintTextColor(Color.rgb(145, 145, 145))
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 4
            minLines = 1
            setPadding(dp(16), 0, dp(10), 0)
            background = rounded(Color.rgb(31, 31, 31), 24)
        }
        outer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginStart = dp(8)
            marginEnd = dp(6)
        })

        val mic = iconButton(R.drawable.ic_mic, "Голос")
        mic.setOnClickListener { startVoice() }
        outer.addView(mic, LinearLayout.LayoutParams(dp(44), dp(44)))

        val send = ImageButton(this).apply {
            setImageResource(R.drawable.ic_send)
            contentDescription = "Надіслати"
            background = rounded(Color.WHITE, 23)
            setPadding(dp(11), dp(11), dp(11), dp(11))
        }
        send.setOnClickListener { sendCurrent() }
        outer.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)))
        return outer
    }

    private fun cleanLegacyNoise() {
        if (prefs.getBoolean("cleanup_041", false)) return

        val noisyFragments = listOf(
            "Повна локальна модель ще не вибрана",
            "Локальна модель готова.",
            "Локальна модель готова",
            "Обери й завантаж локальну модель",
            "Помилка локальної моделі: Status Code:",
            "Натисни кнопку моделі у верхній панелі"
        )

        var changed = false
        chats.forEach { chat ->
            val before = chat.messages.size
            chat.messages.removeAll { message ->
                message.role == "assistant" && noisyFragments.any { fragment ->
                    message.text.contains(fragment, ignoreCase = true)
                }
            }
            if (chat.messages.size != before) changed = true
        }

        if (changed) conversationStore.save(chats)
        prefs.edit().putBoolean("cleanup_041", true).apply()
    }

    private fun renderActiveChat() {
        chatContainer.removeAllViews()
        if (activeChat.messages.isEmpty()) {
            val welcome = TextView(this).apply {
                text = "Чим допомогти?"
                setTextColor(Color.WHITE)
                textSize = 26f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(120), dp(20), dp(24))
            }
            chatContainer.addView(welcome, LinearLayout.LayoutParams(-1, -2))
            val hint = TextView(this).apply {
                text = "Пиши, говори, додавай фото, відео, файли або екран."
                setTextColor(Color.rgb(150, 150, 150))
                textSize = 14f
                gravity = Gravity.CENTER
            }
            chatContainer.addView(hint, LinearLayout.LayoutParams(-1, -2))
        } else {
            activeChat.messages.forEach { addMessageView(it.role, it.text, it.attachmentNames) }
        }
        scrollBottom()
    }

    private fun newChat() {
        val chat = conversationStore.create()
        chats.add(0, chat)
        activeChat = chat
        conversationStore.setActive(chat.id)
        saveChats()
        llm.setHistory(activeChat.messages)
        renderActiveChat()
    }

    private fun switchChat(id: String) {
        chats.firstOrNull { it.id == id }?.let {
            activeChat = it
            conversationStore.setActive(it.id)
            llm.setHistory(activeChat.messages)
            renderActiveChat()
        }
    }

    private fun deleteChat(id: String) {
        if (chats.size <= 1) {
            val replacement = conversationStore.create()
            chats.clear()
            chats.add(replacement)
            activeChat = replacement
        } else {
            chats.removeAll { it.id == id }
            if (activeChat.id == id) activeChat = chats.first()
        }
        conversationStore.setActive(activeChat.id)
        saveChats()
        llm.setHistory(activeChat.messages)
        renderActiveChat()
    }

    private fun saveChats() {
        activeChat.updatedAt = System.currentTimeMillis()
        conversationStore.save(chats)
    }

    private fun showChatDrawer() {
        hideKeyboard()
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(12), dp(12))
            background = ColorDrawable(Color.rgb(31, 31, 31))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "NEXUS"
            setTextColor(Color.WHITE)
            textSize = 25f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        val close = iconButton(R.drawable.ic_close, "Закрити")
        close.setOnClickListener { dialog.dismiss() }
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(header)

        val create = drawerRow(R.drawable.ic_plus, "Новий чат")
        create.setOnClickListener {
            newChat()
            dialog.dismiss()
        }
        panel.addView(create, LinearLayout.LayoutParams(-1, dp(58)))

        val chatsTitle = TextView(this).apply {
            text = "Чати"
            setTextColor(Color.rgb(160, 160, 160))
            textSize = 12f
            setPadding(dp(10), dp(16), 0, dp(8))
        }
        panel.addView(chatsTitle)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chats.sortedByDescending { it.updatedAt }.forEach { chat ->
            val item = TextView(this).apply {
                text = chat.title
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(10), 0)
                background = if (chat.id == activeChat.id) rounded(Color.rgb(67, 67, 67), 12) else null
                setOnClickListener {
                    switchChat(chat.id)
                    dialog.dismiss()
                }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Видалити чат?")
                        .setMessage(chat.title)
                        .setPositiveButton("Видалити") { _, _ ->
                            deleteChat(chat.id)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Скасувати", null)
                        .show()
                    true
                }
            }
            list.addView(item, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(4) })
        }
        scroll.addView(list, FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val functions = drawerRow(R.drawable.ic_functions, "Функції NEXUS")
        functions.setOnClickListener {
            dialog.dismiss()
            showFeatureSettings()
        }
        panel.addView(functions, LinearLayout.LayoutParams(-1, dp(58)))

        val models = drawerRow(R.drawable.ic_model, "Моделі")
        models.setOnClickListener {
            dialog.dismiss()
            showModelManager()
        }
        panel.addView(models, LinearLayout.LayoutParams(-1, dp(58)))

        val settings = drawerRow(R.drawable.ic_settings, "Налаштування")
        settings.setOnClickListener {
            dialog.dismiss()
            showSettings()
        }
        panel.addView(settings, LinearLayout.LayoutParams(-1, dp(58)))

        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setDimAmount(0.55f)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setGravity(Gravity.START)
            w.setLayout((resources.displayMetrics.widthPixels * 0.84f).toInt(), WindowManager.LayoutParams.MATCH_PARENT)
        }
    }

    private fun drawerRow(icon: Int, label: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val image = ImageButton(this).apply {
            setImageResource(icon)
            background = null
            isClickable = false
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        row.addView(image, LinearLayout.LayoutParams(dp(48), dp(48)))
        val text = TextView(this).apply {
            this.text = label
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
        return row
    }

    private fun showAttachmentSheet() {
        hideKeyboard()
        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(18))
            background = rounded(Color.rgb(45, 45, 45), 28)
        }

        val title = TextView(this).apply {
            text = "Додати"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, dp(8))
        }
        box.addView(title)

        fun add(icon: Int, label: String, action: () -> Unit) {
            val row = drawerRow(icon, label)
            row.setOnClickListener {
                dialog.dismiss()
                action()
            }
            box.addView(row, LinearLayout.LayoutParams(-1, dp(62)))
        }

        add(R.drawable.ic_camera, "Камера") { openCamera() }
        add(R.drawable.ic_image, "Фото") { openPhotoPicker() }
        add(R.drawable.ic_video, "Відео") { openVideoPicker() }
        add(R.drawable.ic_file, "Файли") { openFilePicker() }
        add(R.drawable.ic_screen, "Екран") { onScreenButton() }

        dialog.setContentView(box)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setDimAmount(0.45f)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setGravity(Gravity.BOTTOM)
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            w.decorView.setPadding(dp(10), 0, dp(10), dp(8))
        }
    }

    private fun openPhotoPicker() {
        val intent =
            if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
            else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
        startActivityForResult(intent, REQ_PHOTO)
    }

    private fun openVideoPicker() {
        val intent =
            if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("video/*")
            else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }
        startActivityForResult(intent, REQ_VIDEO)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQ_FILES)
    }

    private fun openCamera() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "NEXUS_" + System.currentTimeMillis() + ".jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            addAssistant("Не вдалося створити файл для фото.")
            return
        }
        pendingCameraUri = uri
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQ_CAMERA)
        } catch (_: Exception) {
            addAssistant("На телефоні не знайдено доступної камери.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQ_FILES -> {
                if (data == null) return
                val uris = mutableListOf<Uri>()
                data.data?.let { uris += it }
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
                }
                importUris(uris.take(8))
            }
            REQ_PHOTO, REQ_VIDEO -> data?.data?.let { importUris(listOf(it)) }
            REQ_CAMERA -> pendingCameraUri?.let {
                importUris(listOf(it))
                pendingCameraUri = null
            }
            REQ_MODEL -> data?.data?.let { importModel(it) }
            REQ_FOLDER -> data?.data?.let {
                fileIndex.addFolder(it)
                addAssistant("Папку додано до локального пошуку.")
            }
        }
    }

    private fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        ioScope.launch {
            val imported = mutableListOf<NexusAttachment>()
            uris.forEach { uri ->
                try { imported += attachmentManager.importUri(uri) } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                attachments += imported
                renderAttachmentStrip()
            }
        }
    }

    private fun renderAttachmentStrip() {
        attachmentStrip.removeAllViews()
        attachmentScroll.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
        attachments.forEachIndexed { index, a ->
            val chip = TextView(this).apply {
                text = a.name
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(11), 0, dp(11), 0)
                background = rounded(Color.rgb(40, 40, 40), 15)
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
                marginEnd = dp(7)
            })
        }
    }

    private fun sendCurrent() {
        val text = input.text.toString().trim()
        if (text.isEmpty() && attachments.isEmpty()) return

        val sentAttachments = attachments.toList()
        val names = sentAttachments.map { it.name }
        val userText = if (text.isEmpty()) "Проаналізуй вкладення." else text

        addUser(userText, names)
        input.setText("")
        attachments.clear()
        renderAttachmentStrip()
        hideKeyboard()

        route(userText, sentAttachments)
    }

    private fun route(text: String, sentAttachments: List<NexusAttachment>) {
        val low = text.lowercase(Locale.ROOT)

        if (isFileSearch(low)) {
            if (!feature("file_search")) addAssistant("Пошук файлів вимкнений у функціях NEXUS.")
            else runFileSearch(text)
            return
        }

        if (isMemoryCommand(low)) {
            if (!feature("memory")) addAssistant("Локальна пам’ять вимкнена у функціях NEXUS.")
            else handleMemory(text)
            return
        }

        if (isScreenQuestion(low)) {
            if (!feature("screen_vision")) addAssistant("Аналіз екрана вимкнений у функціях NEXUS.")
            else handleScreenQuestion(text, sentAttachments)
            return
        }

        if (looksLikeExplicitAction(low)) {
            if (!feature("phone_control")) {
                addAssistant("Керування телефоном вимкнене у функціях NEXUS.")
                return
            }
            if (low.contains(" і потім ") && !feature("multistep")) {
                addAssistant("MultiStep вимкнений у функціях NEXUS.")
                return
            }
            confirmDeviceAction(text)
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
            addAssistant("Напиши назву або частину назви файла.")
            return
        }

        if (fileIndex.folderCount() == 0) {
            addAssistant("Спочатку додай папку в Налаштуваннях NEXUS.")
            return
        }

        ioScope.launch {
            val hits = fileIndex.search(query)
            val answer =
                if (hits.isEmpty()) "Нічого з «$query» не знайдено."
                else hits.joinToString(prefix = "Знайдено:\n", separator = "\n") { it.name }
            withContext(Dispatchers.Main) { addAssistant(answer) }
        }
    }

    private fun isMemoryCommand(s: String): Boolean =
        s.startsWith("запам'ятай ") || s.startsWith("запам’ятай ") || s.startsWith("запамятай ") ||
            s.contains("що ти пам")

    private fun handleMemory(original: String) {
        val low = original.lowercase(Locale.ROOT)
        val memory = getSharedPreferences("nexus_memory", MODE_PRIVATE)
        if (low.contains("що ти пам")) {
            val value = memory.getString("note", null)
            addAssistant(if (value.isNullOrBlank()) "Локальна пам’ять порожня." else "Пам’ятаю: $value")
        } else {
            memory.edit().putString("note", original.substringAfter(' ', "").trim()).apply()
            addAssistant("Зберіг локально.")
        }
    }

    private fun isScreenQuestion(s: String): Boolean =
        s.contains("що на екрані") || s.contains("шо на екрані") || s.contains("подивись екран") ||
            s.contains("подивися екран") || s.contains("переклади екран") || s.contains("розкажи що тут")

    private fun handleScreenQuestion(question: String, originalAttachments: List<NexusAttachment>) {
        val visibleText = NexusAccessibilityService.get()?.screenText().orEmpty()
        val media = originalAttachments.toMutableList()

        ScreenCaptureService.latestCapturePath?.let { path ->
            if (File(path).exists()) media += attachmentManager.fromScreen(path)
        }

        if (llm.isLoaded) {
            val prompt = buildString {
                append(question)
                if (visibleText.isNotBlank()) {
                    append("\n\nДоступний текст поточного екрана:\n")
                    append(visibleText)
                }
                if (media.none { it.kind == AttachmentKind.SCREEN }) {
                    append("\n\nСкріншот не підключений. Використовуй доступний текст екрана.")
                }
            }
            sendToLocalAi(prompt, media)
        } else if (visibleText.isNotBlank()) {
            addAssistant("На екрані:\n$visibleText")
        } else {
            addAssistant("Для візуального аналізу потрібна локальна мультимодальна модель.")
        }
    }

    private fun looksLikeExplicitAction(s: String): Boolean {
        val starts = listOf(
            "відкрий ", "відкрий", "зайди ", "натисни ", "нажми ", "прокрути ",
            "повернись назад", "назад", "додому", "головний екран", "знайди на екрані "
        )
        return starts.any { s.startsWith(it) } || s.contains(" і потім ")
    }

    private fun confirmDeviceAction(command: String) {
        AlertDialog.Builder(this)
            .setTitle("Підтвердити дію")
            .setMessage(command)
            .setPositiveButton("Підтвердити") { _, _ -> executeDeviceAction(command) }
            .setNegativeButton("Скасувати") { _, _ -> addAssistant("Дію скасовано.") }
            .show()
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
                addAssistant("Системний розділ відкрито.")
            } catch (_: Exception) {
                addAssistant("Android не дозволив виконати цю дію.")
            }
        } else {
            addAssistant("Для цієї дії увімкни Screen Control.")
        }
    }

    private fun simpleSettingsIntent(s: String): String? = when {
        s.contains("пам'ять телефона") || s.contains("пам’ять телефона") || s.contains("сховище") -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        s.contains("wifi") || s.contains("wi-fi") || s.contains("вайфай") -> Settings.ACTION_WIFI_SETTINGS
        s.contains("bluetooth") || s.contains("блютуз") -> Settings.ACTION_BLUETOOTH_SETTINGS
        s.contains("батаре") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        s.contains("яскрав") || s.contains("налаштування екрана") -> Settings.ACTION_DISPLAY_SETTINGS
        s.contains("додатк") -> Settings.ACTION_APPLICATION_SETTINGS
        s.contains("налаштуван") -> Settings.ACTION_SETTINGS
        else -> null
    }

    private fun sendToLocalAi(prompt: String, sentAttachments: List<NexusAttachment>) {
        if (!llm.isLoaded) {
            addAssistant("Обери й завантаж локальну модель через кнопку моделі зверху.")
            return
        }

        val fileContext = buildString {
            sentAttachments.filter { it.kind == AttachmentKind.TEXT }.forEach { a ->
                val txt = attachmentManager.readText(a)
                if (txt.isNotBlank()) {
                    append("\n\nФайл «" + a.name + "»:\n")
                    append(txt)
                }
            }
            sentAttachments.filter { it.kind == AttachmentKind.FILE }.forEach { a ->
                append("\n\nДодано файл «" + a.name + "» типу " + a.mime + ".")
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
                val finalText = buffer.toString().ifBlank { "Модель не повернула відповіді." }
                assistantView.text = finalText
                activeChat.messages += StoredMessage("assistant", finalText)
                saveChats()
                speakIfNeeded(finalText)
                scrollBottom()
            },
            onError = { error ->
                val finalText = error
                assistantView.text = finalText
                activeChat.messages += StoredMessage("assistant", finalText)
                saveChats()
                scrollBottom()
            }
        )
    }

    private fun showModelManager() {
        hideKeyboard()
        val dialog = Dialog(this)
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
            setBackgroundColor(Color.rgb(24, 24, 24))
        }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))

        fun render() {
            content.removeAllViews()

            val title = TextView(this).apply {
                text = "Локальна модель"
                setTextColor(Color.WHITE)
                textSize = 24f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            content.addView(title, LinearLayout.LayoutParams(-1, dp(48)))

            val subtitle = TextView(this).apply {
                text = "Завантаження потребує інтернету один раз. Після цього чат працює локально."
                setTextColor(Color.rgb(160, 160, 160))
                textSize = 13f
                setPadding(0, 0, 0, dp(14))
            }
            content.addView(subtitle)

            modelManager.models.forEach { spec ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = rounded(Color.rgb(37, 37, 37), 18)
                }

                val name = TextView(this).apply {
                    text = spec.label + (if (spec.recommended) " · Рекомендована" else "") + "  " + spec.sizeLabel
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                card.addView(name)

                val desc = TextView(this).apply {
                    text = spec.description
                    setTextColor(Color.rgb(175, 175, 175))
                    textSize = 13f
                    setPadding(0, dp(5), 0, dp(10))
                }
                card.addView(desc)

                val downloaded = modelManager.isDownloaded(spec)
                val selected = modelManager.selectedId() == spec.id
                if (downloaded && modelManager.pendingActivationId() == spec.id && !loadingModel) {
                    modelManager.clearPendingActivation()
                    content.post { useModel(spec, dialog) }
                }
                val state = modelManager.query(spec)
                val running = !downloaded && state != null &&
                    (state.status == DownloadManager.STATUS_RUNNING || state.status == DownloadManager.STATUS_PENDING)

                val status = TextView(this).apply {
                    setTextColor(Color.rgb(165, 165, 165))
                    textSize = 12f
                    text = when {
                        downloaded && selected -> "Завантажена · активна"
                        downloaded -> "Завантажена"
                        running -> {
                            val pct = if (state!!.total > 0) (state.downloaded * 100 / state.total) else -1
                            if (pct >= 0) "Завантаження $pct%" else "Завантаження"
                        }
                        state?.status == DownloadManager.STATUS_FAILED -> "Завантаження не вдалося"
                        else -> "Не завантажена"
                    }
                }
                card.addView(status, LinearLayout.LayoutParams(-1, dp(28)))

                val button = Button(this).apply {
                    isAllCaps = false
                    text = when {
                        downloaded && selected -> "Активна"
                        downloaded -> "Використовувати"
                        running -> "Завантажується"
                        else -> "Завантажити"
                    }
                    isEnabled = !(downloaded && selected) && !running
                    setOnClickListener {
                        if (downloaded) {
                            useModel(spec, dialog)
                        } else {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Завантажити " + spec.label + "?")
                                .setMessage(spec.sizeLabel + ". Модель буде збережена тільки для NEXUS.")
                                .setPositiveButton("Завантажити") { _, _ ->
                                    try {
                                        modelManager.startDownload(spec)
                                        render()
                                    } catch (e: Exception) {
                                        addAssistant("Не вдалося почати завантаження: " + (e.message ?: "помилка"))
                                    }
                                }
                                .setNegativeButton("Скасувати", null)
                                .show()
                        }
                    }
                }
                card.addView(button, LinearLayout.LayoutParams(-1, dp(50)))
                content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }

            val own = Button(this).apply {
                text = "Імпортувати свою .litertlm"
                isAllCaps = false
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(intent, REQ_MODEL)
                    dialog.dismiss()
                }
            }
            content.addView(own, LinearLayout.LayoutParams(-1, dp(54)))

            val close = Button(this).apply {
                text = "Закрити"
                isAllCaps = false
                setOnClickListener { dialog.dismiss() }
            }
            content.addView(close, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(6) })

            content.postDelayed({
                if (dialog.isShowing) render()
            }, 1200)
        }

        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.rgb(24, 24, 24)))
            w.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), (resources.displayMetrics.heightPixels * 0.82f).toInt())
            w.setGravity(Gravity.CENTER)
        }
        render()
    }

    private fun useModel(spec: ModelSpec, dialog: Dialog?) {
        val file = modelManager.modelFile(spec)
        if (!file.exists()) return
        loadingModel = true
        updateHeader()
        llm.loadModel(file.absolutePath) { ok, message ->
            loadingModel = false
            if (ok) {
                modelManager.setSelected(spec)
                Toast.makeText(this, spec.label + " активна", Toast.LENGTH_SHORT).show()
            } else {
                addAssistant(message)
            }
            updateHeader()
            if (ok) dialog?.dismiss()
        }
    }

    private fun importModel(uri: Uri) {
        loadingModel = true
        updateHeader()
        ioScope.launch {
            try {
                val file = attachmentManager.importModel(uri)
                withContext(Dispatchers.Main) {
                    llm.loadModel(file.absolutePath) { ok, message ->
                        loadingModel = false
                        updateHeader()
                        if (ok) Toast.makeText(this@MainActivity, "Модель активна", Toast.LENGTH_SHORT).show()
                        else addAssistant(message)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    loadingModel = false
                    updateHeader()
                    addAssistant("Не вдалося імпортувати модель.")
                }
            }
        }
    }

    private fun loadSelectedModel() {
        val selected = modelManager.selected()
        if (selected != null && modelManager.isDownloaded(selected)) {
            loadingModel = true
            llm.loadModel(modelManager.modelFile(selected).absolutePath) { _, _ ->
                loadingModel = false
                updateHeader()
            }
        } else {
            llm.loadSaved { _, _ -> updateHeader() }
        }
    }

    private fun onScreenButton() {
        if (!feature("screen_vision")) {
            addAssistant("Аналіз екрана вимкнений у функціях NEXUS.")
            return
        }
        if (!ScreenCaptureService.isRunning) {
            pendingScreenAttachment = true
            startActivity(Intent(this, CapturePermissionActivity::class.java))
        } else {
            pendingScreenAttachment = true
            ScreenCaptureService.requestFreshCapture()
            handler.postDelayed({ attachLatestScreen() }, 600)
        }
    }

    private fun attachLatestScreen() {
        val path = ScreenCaptureService.latestCapturePath
        if (path != null && File(path).exists()) {
            attachments.removeAll { it.kind == AttachmentKind.SCREEN }
            attachments += attachmentManager.fromScreen(path)
            renderAttachmentStrip()
            pendingScreenAttachment = false
            Toast.makeText(this, "Екран додано", Toast.LENGTH_SHORT).show()
        } else if (ScreenCaptureService.isRunning) {
            handler.postDelayed({ attachLatestScreen() }, 500)
        }
    }

    private fun showFeatureSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(8))
        }
        addFeatureSwitch(box, "Аналіз екрана", "screen_vision")
        addFeatureSwitch(box, "Керування телефоном", "phone_control")
        addFeatureSwitch(box, "MultiStep", "multistep")
        addFeatureSwitch(box, "Пошук локальних файлів", "file_search")
        addFeatureSwitch(box, "Локальна пам’ять", "memory")

        AlertDialog.Builder(this)
            .setTitle("Функції NEXUS")
            .setView(box)
            .setNegativeButton("Готово", null)
            .show()
    }

    private fun addFeatureSwitch(parent: LinearLayout, label: String, key: String) {
        val sw = Switch(this).apply {
            text = label
            isChecked = feature(key)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("feature_$key", checked).apply() }
        }
        parent.addView(sw, LinearLayout.LayoutParams(-1, dp(54)))
    }

    private fun feature(key: String): Boolean = prefs.getBoolean("feature_$key", true)

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        val languageFilter = Switch(this).apply {
            text = "Фільтр матів"
            isChecked = prefs.getBoolean("language_filter", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("language_filter", checked).apply()
                llm.setLanguageFilter(checked)
            }
        }
        box.addView(languageFilter, LinearLayout.LayoutParams(-1, dp(54)))

        val replyLanguage = Button(this).apply {
            text = "Мова відповідей: " + languageLabel(prefs.getString("reply_language", "auto") ?: "auto")
            isAllCaps = false
            setOnClickListener { chooseReplyLanguage() }
        }
        box.addView(replyLanguage, LinearLayout.LayoutParams(-1, dp(52)))

        val speechLanguage = Button(this).apply {
            text = "Мова голосу: " + languageLabel(prefs.getString("speech_language", "auto") ?: "auto")
            isAllCaps = false
            setOnClickListener { chooseSpeechLanguage() }
        }
        box.addView(speechLanguage, LinearLayout.LayoutParams(-1, dp(52)))

        val autoSendVoice = Switch(this).apply {
            text = "Одразу надсилати голосове питання"
            isChecked = prefs.getBoolean("auto_send_voice", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("auto_send_voice", checked).apply() }
        }
        box.addView(autoSendVoice, LinearLayout.LayoutParams(-1, dp(54)))

        val speak = Switch(this).apply {
            text = "Озвучувати всі відповіді"
            isChecked = prefs.getBoolean("speak_replies", false)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("speak_replies", checked).apply() }
        }
        box.addView(speak, LinearLayout.LayoutParams(-1, dp(54)))

        val voice = Button(this).apply {
            text = "Голос NEXUS"
            isAllCaps = false
            setOnClickListener { chooseTtsVoice() }
        }
        box.addView(voice, LinearLayout.LayoutParams(-1, dp(52)))

        val confirm = Switch(this).apply {
            text = "Підтверджувати дії на телефоні"
            isChecked = true
            isEnabled = false
        }
        box.addView(confirm, LinearLayout.LayoutParams(-1, dp(54)))

        fun addButton(label: String, action: () -> Unit) {
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            }
            box.addView(b, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(5) })
        }

        addButton("Функції NEXUS") { showFeatureSettings() }
        addButton("Screen Control") { showAccessibilityDisclosure() }
        addButton("Додати папку для пошуку") { chooseFolder() }
        addButton("Додати NEXUS Screen у шторку") { requestTile() }

        val info = TextView(this).apply {
            text = "Модель працює локально після завантаження. Мережа використовується тільки Model Manager для завантаження вибраної моделі."
            setTextColor(Color.DKGRAY)
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        }
        box.addView(info)

        AlertDialog.Builder(this)
            .setTitle("Налаштування")
            .setView(box)
            .setNegativeButton("Закрити", null)
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

    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Screen Control")
            .setMessage(
                "NEXUS зможе читати доступний текст інтерфейсу та виконувати натискання, скрол, Назад і Додому. " +
                    "Кожна дія все одно потребує твого підтвердження."
            )
            .setPositiveButton("Відкрити налаштування") { _, _ ->
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
                Toast.makeText(this, "Код: $result", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Додай NEXUS Screen через редагування швидких налаштувань.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }

        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            addAssistant("На цьому телефоні Android не дає доступного on-device розпізнавання голосу.")
            return
        }

        voiceReplyPending = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { input.hint = "Слухаю" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { input.hint = "Повідомлення NEXUS" }
            override fun onError(error: Int) {
                voiceReplyPending = false
                input.hint = "Повідомлення NEXUS"
                addAssistant("Не вдалося розпізнати голос.")
            }
            override fun onResults(results: Bundle?) {
                input.hint = "Повідомлення NEXUS"
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) {
                    input.setText(list[0])
                    input.setSelection(input.text.length)
                    if (prefs.getBoolean("auto_send_voice", true)) {
                        handler.postDelayed({ sendCurrent() }, 120)
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) {
                    input.setText(list[0])
                    input.setSelection(input.text.length)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            val tag = speechLanguageTag()
            if (tag != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        }
        recognizer?.startListening(intent)
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                configureTtsLanguage("uk")
                applyTtsVoice()
            }
        }
    }

    private fun availableVoices(language: String? = null): List<Voice> {
        val engine = tts ?: return emptyList()
        val all = engine.voices?.filter { !it.isNetworkConnectionRequired } ?: emptyList()
        val wanted = language ?: prefs.getString("reply_language", "auto")
        val localized =
            if (wanted.isNullOrBlank() || wanted == "auto") emptyList()
            else all.filter { it.locale.language.equals(wanted, true) }
        return (if (localized.isNotEmpty()) localized else all).sortedBy { it.name }.take(6)
    }

    private fun applyTtsVoice(language: String? = null) {
        val voices = availableVoices(language)
        if (voices.isEmpty()) return
        val index = prefs.getInt("tts_voice", 0).coerceIn(0, voices.lastIndex)
        tts?.voice = voices[index]
    }

    private fun chooseTtsVoice() {
        val voices = availableVoices()
        if (voices.isEmpty()) {
            Toast.makeText(this, "Немає доступних локальних голосів.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = voices.mapIndexed { i, voice ->
            "Голос " + (i + 1) + " · " + voice.locale.displayLanguage
        }.toTypedArray()
        val current = prefs.getInt("tts_voice", 0).coerceIn(0, voices.lastIndex)
        AlertDialog.Builder(this)
            .setTitle("Голос NEXUS")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.edit().putInt("tts_voice", which).apply()
                applyTtsVoice()
                tts?.speak("Перевірка голосу NEXUS", TextToSpeech.QUEUE_FLUSH, null, "voice_test")
                dialog.dismiss()
            }
            .show()
    }

    private fun speakIfNeeded(text: String) {
        val should = voiceReplyPending || prefs.getBoolean("speak_replies", false)
        voiceReplyPending = false
        if (!should) return

        if (!ttsReady) {
            handler.postDelayed({
                if (ttsReady) speakTextNow(text)
            }, 700)
            return
        }
        speakTextNow(text)
    }

    private fun speakTextNow(text: String) {
        val lang = detectTextLanguage(text)
        configureTtsLanguage(lang)
        applyTtsVoice(lang)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexus_reply")
    }

    private fun configureTtsLanguage(language: String) {
        val locale = when (language) {
            "uk" -> Locale("uk", "UA")
            "ru" -> Locale("ru", "RU")
            "pl" -> Locale("pl", "PL")
            "de" -> Locale.GERMANY
            "fr" -> Locale.FRANCE
            "es" -> Locale("es", "ES")
            else -> Locale.US
        }
        try { tts?.language = locale } catch (_: Exception) {}
    }

    private fun detectTextLanguage(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            Regex("[іїєґ]").containsMatchIn(lower) -> "uk"
            Regex("[ąćęłńóśźż]").containsMatchIn(lower) -> "pl"
            Regex("[äöüß]").containsMatchIn(lower) -> "de"
            Regex("[éèêëàâîïôùûüçœ]").containsMatchIn(lower) -> "fr"
            Regex("[ñáéíóúü¿¡]").containsMatchIn(lower) -> "es"
            Regex("[а-яё]").containsMatchIn(lower) -> "ru"
            else -> "en"
        }
    }

    private fun languageLabel(code: String): String = when (code) {
        "uk" -> "Українська"
        "en" -> "English"
        "ru" -> "Русский"
        "pl" -> "Polski"
        "de" -> "Deutsch"
        "fr" -> "Français"
        "es" -> "Español"
        else -> "Авто"
    }

    private fun chooseReplyLanguage() {
        val codes = arrayOf("auto", "uk", "en", "ru", "pl", "de", "fr", "es")
        val labels = codes.map { languageLabel(it) }.toTypedArray()
        val currentCode = prefs.getString("reply_language", "auto") ?: "auto"
        val current = codes.indexOf(currentCode).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Мова відповідей")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val code = codes[which]
                prefs.edit().putString("reply_language", code).apply()
                llm.setReplyLanguage(code)
                dialog.dismiss()
                Toast.makeText(this, "Мова: " + languageLabel(code), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun chooseSpeechLanguage() {
        val codes = arrayOf("auto", "uk", "en", "ru", "pl", "de", "fr", "es")
        val labels = codes.map { languageLabel(it) }.toTypedArray()
        val currentCode = prefs.getString("speech_language", "auto") ?: "auto"
        val current = codes.indexOf(currentCode).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Мова розпізнавання голосу")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val code = codes[which]
                prefs.edit().putString("speech_language", code).apply()
                dialog.dismiss()
                Toast.makeText(this, "Голос: " + languageLabel(code), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun speechLanguageTag(): String? = when (prefs.getString("speech_language", "auto") ?: "auto") {
        "uk" -> "uk-UA"
        "en" -> "en-US"
        "ru" -> "ru-RU"
        "pl" -> "pl-PL"
        "de" -> "de-DE"
        "fr" -> "fr-FR"
        "es" -> "es-ES"
        else -> null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQ_AUDIO && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice()
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
        if (activeChat.title == "Новий чат") {
            activeChat.title = text.replace("\n", " ").take(42).ifBlank { "Чат" }
        }
        addMessageView("user", text, attachmentNames)
        activeChat.messages += StoredMessage("user", text, attachmentNames)
        saveChats()
    }

    private fun addAssistant(text: String, persist: Boolean = true) {
        addMessageView("assistant", text, emptyList())
        if (persist) {
            activeChat.messages += StoredMessage("assistant", text)
            saveChats()
        }
        speakIfNeeded(text)
    }

    private fun addStreamingAssistant(): TextView {
        val row = messageRow(false)
        val bubble = TextView(this).apply {
            setTextColor(Color.rgb(238, 238, 238))
            textSize = 16f
            setLineSpacing(0f, 1.08f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.rgb(18, 18, 18), 18)
        }
        row.addView(bubble, LinearLayout.LayoutParams(0, -2, 0.94f))
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
            background = rounded(if (user) Color.rgb(47, 47, 47) else Color.rgb(18, 18, 18), 18)
        }

        attachmentNames.forEach { name ->
            val attachment = TextView(this).apply {
                this.text = name
                setTextColor(Color.rgb(190, 190, 190))
                textSize = 12f
                maxLines = 1
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file, 0, 0, 0)
                compoundDrawablePadding = dp(6)
            }
            box.addView(attachment, LinearLayout.LayoutParams(-1, dp(28)))
        }

        val body = TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(242, 242, 242))
            textSize = 16f
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.08f)
        }
        box.addView(body, LinearLayout.LayoutParams(-1, -2))

        row.addView(box, LinearLayout.LayoutParams(0, -2, if (user) 0.84f else 0.94f))
        chatContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        scrollBottom()
    }

    private fun messageRow(user: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = if (user) Gravity.END else Gravity.START
        if (user) setPadding(dp(52), 0, 0, 0) else setPadding(0, 0, dp(20), 0)
    }

    private fun updateHeader() {
        modelStatus.text = when {
            loadingModel -> "OFFLINE · завантаження моделі"
            llm.isLoaded -> {
                val selected = modelManager.selected()
                if (selected != null) "OFFLINE · " + selected.label.uppercase(Locale.ROOT)
                else "OFFLINE · LOCAL AI"
            }
            else -> "OFFLINE · обери модель"
        }
        screenButton.alpha = if (ScreenCaptureService.isRunning) 1f else 0.62f
    }

    private fun hideKeyboard() {
        if (::input.isInitialized) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
        }
    }

    private fun scrollBottom() {
        if (::chatScroll.isInitialized) handler.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
