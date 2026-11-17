package com.example.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.core.services.AlfredEventBus
import com.example.data.local.VisionDatabase
import com.example.data.preferences.VisionPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional, user-consented AccessibilityService that enforces Focus Shield when a
 * selected distracting application enters the foreground during an active Focus Mode session.
 */
class FocusAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var isServiceConnected: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.startsWith("com.android.systemui")) return

        scope.launch {
            val prefs = VisionPreferences(applicationContext)
            val settings = prefs.getSettingsOnce()
            if (!settings.activeFocusRunning || !settings.autoBlockEnabled) return@launch

            val db = VisionDatabase.getInstance(applicationContext)
            val blockedApps = db.focusDao().getSelectedDistractingAppsOnce()
            val match = blockedApps.firstOrNull { it.packageName == pkg } ?: return@launch

            // Study Mode allows YouTube initially until grace expires
            if (settings.studyModeEnabled && match.isYouTubeSpecial) {
                val now = System.currentTimeMillis()
                if (settings.pendingGracePackage != pkg || now < settings.pendingGraceExpiresMillis) {
                    return@launch
                }
            }

            AlfredEventBus.emitAnnouncement(
                "Focus Shield engaged for ${match.appName}. Returning you to VISION."
            )
            val shieldIntent = Intent(applicationContext, FocusShieldActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(FocusShieldActivity.EXTRA_BLOCKED_APP_NAME, match.appName)
                putExtra(FocusShieldActivity.EXTRA_BLOCKED_PKG, pkg)
            }
            runCatching { startActivity(shieldIntent) }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceConnected = false
        scope.cancel()
    }
}
