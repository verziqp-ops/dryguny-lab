package com.nexus.ai

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class CapturePermissionActivity : Activity() {
    private val requestCode = 9101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ScreenCaptureService.isRunning) {
            ScreenCaptureService.requestFreshCapture()
            finish()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == requestCode && result == RESULT_OK && data != null) {
            val service = Intent(this, ScreenCaptureService::class.java)
                .putExtra("result_code", result)
                .putExtra("result_data", data)
            startForegroundService(service)
        }
        finish()
    }
}
