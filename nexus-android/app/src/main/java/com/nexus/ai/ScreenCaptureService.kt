package com.nexus.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureService : Service() {
    companion object {
        private const val CHANNEL = "nexus_screen_capture"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_STOP = "com.nexus.ai.STOP_SCREEN"
        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var latestCapturePath: String? = null
            private set
        @Volatile private var captureRequested: Boolean = false

        fun requestFreshCapture() { captureRequested = true }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP))
        }
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var lastSave = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        thread = HandlerThread("NexusScreenCapture").also { it.start() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (isRunning) {
            captureRequested = true
            return START_STICKY
        }

        val resultCode = intent?.getIntExtra("result_code", Int.MIN_VALUE) ?: Int.MIN_VALUE
        val data: Intent? =
            if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("result_data", Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra("result_data")
        if (resultCode == Int.MIN_VALUE || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture()
                stopSelf()
            }
        }, Handler(thread!!.looper))

        setupVirtualDisplay()
        isRunning = true
        captureRequested = true
        updateTile()
        Handler(Looper.getMainLooper()).postDelayed({
            NexusAccessibilityService.get()?.showAssistantOverlay()
        }, 250)
        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        val wm = getSystemService(WindowManager::class.java)
        val metrics = resources.displayMetrics
        val bounds = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds else {
            @Suppress("DEPRECATION")
            android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val width = bounds.width().coerceAtLeast(1)
        val height = bounds.height().coerceAtLeast(1)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ imageReader ->
            val image = try { imageReader.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (!captureRequested && now - lastSave < 1500) return@setOnImageAvailableListener
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val paddedWidth = width + rowPadding / pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                if (cropped !== padded) padded.recycle()
                val dir = File(cacheDir, "screen").apply { mkdirs() }
                val out = File(dir, "screen_latest.png")
                FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
                latestCapturePath = out.absolutePath
                lastSave = now
                captureRequested = false
            } finally {
                image.close()
            }
        }, Handler(thread!!.looper))

        display = projection?.createVirtualDisplay(
            "NEXUS Screen",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface,
            null,
            Handler(thread!!.looper)
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            42,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("NEXUS Screen")
            .setContentText("Захоплення екрана активне лише локально")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Зупинити", stopIntent).build())
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "NEXUS screen capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun stopCapture() {
        isRunning = false
        try { display?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        display = null
        reader = null
        projection = null
        captureRequested = false
        NexusAccessibilityService.get()?.hideAssistantOverlay()
        updateTile()
    }

    private fun updateTile() {
        try {
            TileService.requestListeningState(this, ComponentName(this, NexusTileService::class.java))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopCapture()
        thread?.quitSafely()
        thread = null
        super.onDestroy()
    }
}
