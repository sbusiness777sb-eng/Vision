package com.example.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.VisionApplication
import kotlinx.coroutines.launch

class FocusActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STOP_FOCUS = "com.example.vision.ACTION_STOP_FOCUS_FROM_NOTIFICATION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_FOCUS) return
        val pendingResult = goAsync()
        val app = context.applicationContext as? VisionApplication
        if (app == null) {
            pendingResult.finish()
            return
        }
        app.applicationScope.launch {
            try {
                app.focusController.stopFocusSession(completedNormally = false)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
