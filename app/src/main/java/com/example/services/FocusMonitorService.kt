package com.example.services

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import com.example.core.services.AlfredEventBus
import com.example.core.services.FocusController
import com.example.core.services.FocusRuntimeSnapshot
import com.example.core.util.DateUtil
import com.example.data.local.DailyStatEntity
import com.example.data.local.FocusSessionStatus
import com.example.data.local.VisionDatabase
import com.example.data.preferences.VisionPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class FocusMonitorService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START_FOCUS = "com.example.vision.ACTION_START_FOCUS"
        const val ACTION_STOP_FOCUS = "com.example.vision.ACTION_STOP_FOCUS"
        const val ACTION_SIMULATE_DISTRACTION = "com.example.vision.ACTION_SIMULATE_DISTRACTION"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_STUDY_MODE = "extra_study_mode"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_IS_YOUTUBE = "extra_is_youtube"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timerJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var dispatcher: NotificationDispatcher
    private lateinit var db: VisionDatabase
    private lateinit var preferences: VisionPreferences
    private lateinit var focusController: FocusController

    private var currentSessionId: Long = 0L
    private var isStudyMode: Boolean = false
    private var totalSeconds: Int = 0
    private var remainingSeconds: Int = 0
    private var warningPackage: String? = null
    private var warningAppName: String? = null
    private var graceCountdown: Int = 0
    private var youtubeForegroundTicks: Int = 0

    override fun onCreate() {
        super.onCreate()
        dispatcher = NotificationDispatcher(this)
        db = VisionDatabase.getInstance(this)
        preferences = VisionPreferences(this)
        focusController = FocusController(this, db.focusDao(), preferences)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            ttsReady = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_FOCUS -> {
                stopAndCompleteSession(completedNormally = false)
                return START_NOT_STICKY
            }

            ACTION_SIMULATE_DISTRACTION -> {
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "YouTube"
                val isYt = intent.getBooleanExtra(EXTRA_IS_YOUTUBE, false)
                scope.launch {
                    triggerWarningForPackage(
                        packageName = if (isYt) "com.google.android.youtube" else "com.distraction.app",
                        appName = appName,
                        isYouTube = isYt
                    )
                }
                return START_STICKY
            }

            else -> {
                currentSessionId = intent?.getLongExtra(EXTRA_SESSION_ID, 0L) ?: 0L
                val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 25) ?: 25
                isStudyMode = intent?.getBooleanExtra(EXTRA_STUDY_MODE, false) ?: false
                totalSeconds = (durationMinutes * 60).coerceAtLeast(60)
                remainingSeconds = totalSeconds

                val notif = dispatcher.buildFocusOngoingNotification(
                    DateUtil.formatDurationSeconds(remainingSeconds),
                    isStudyMode
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NotificationDispatcher.NOTIF_ID_FOCUS,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NotificationDispatcher.NOTIF_ID_FOCUS, notif)
                }

                startCountdownLoop()
                return START_STICKY
            }
        }
    }

    private fun startCountdownLoop() {
        timerJob?.cancel()
        timerJob = scope.launch {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (isActive && remainingSeconds > 0) {
                AlfredEventBus.updateFocusRuntime(
                    FocusRuntimeSnapshot(
                        isRunning = true,
                        remainingSeconds = remainingSeconds,
                        totalSeconds = totalSeconds,
                        isStudyMode = isStudyMode,
                        activeWarningAppName = warningAppName,
                        graceRemainingSeconds = graceCountdown
                    )
                )

                nm.notify(
                    NotificationDispatcher.NOTIF_ID_FOCUS,
                    dispatcher.buildFocusOngoingNotification(
                        DateUtil.formatDurationSeconds(remainingSeconds),
                        isStudyMode
                    )
                )

                // Check foreground app every 3 seconds when UsageStats is granted
                if (remainingSeconds % 3 == 0) {
                    evaluateForegroundUsage()
                }

                // Tick down active grace period if a warning was issued
                if (graceCountdown > 0) {
                    graceCountdown -= 1
                    if (graceCountdown == 0 && warningPackage != null) {
                        val stillInDistracting = checkStillInPackage(warningPackage!!)
                        if (stillInDistracting) {
                            applyFocusShield(warningAppName ?: "Distracting App", warningPackage!!)
                        }
                        warningPackage = null
                        warningAppName = null
                    }
                }

                delay(1000L)
                remainingSeconds -= 1
            }

            if (remainingSeconds <= 0) {
                stopAndCompleteSession(completedNormally = true)
            }
        }
    }

    private suspend fun evaluateForegroundUsage() {
        val foregroundPkg = focusController.queryForegroundPackageName() ?: return
        if (foregroundPkg == packageName) {
            youtubeForegroundTicks = 0
            if (graceCountdown > 0) {
                graceCountdown = 0
                warningPackage = null
                warningAppName = null
            }
            return
        }

        val selectedApps = db.focusDao().getSelectedDistractingAppsOnce()
        val match = selectedApps.firstOrNull { it.packageName == foregroundPkg } ?: return

        if (isStudyMode && match.isYouTubeSpecial) {
            // Study Mode allows YouTube for educational use, issuing a warning only after prolonged continuous usage
            youtubeForegroundTicks += 3
            val settings = preferences.getSettingsOnce()
            val educationalAllowanceSeconds = 180 // 3 minutes continuous check before warning reminder
            if (youtubeForegroundTicks >= educationalAllowanceSeconds && graceCountdown == 0) {
                triggerWarningForPackage(
                    packageName = match.packageName,
                    appName = match.appName,
                    isYouTube = true,
                    graceSeconds = settings.studyGracePeriodSeconds
                )
            }
        } else if (graceCountdown == 0) {
            val settings = preferences.getSettingsOnce()
            if (settings.autoBlockEnabled) {
                triggerWarningForPackage(
                    packageName = match.packageName,
                    appName = match.appName,
                    isYouTube = false,
                    graceSeconds = settings.studyGracePeriodSeconds
                )
            }
        }
    }

    private suspend fun triggerWarningForPackage(
        packageName: String,
        appName: String,
        isYouTube: Boolean,
        graceSeconds: Int? = null
    ) {
        val settings = preferences.getSettingsOnce()
        val grace = graceSeconds ?: settings.studyGracePeriodSeconds
        warningPackage = packageName
        warningAppName = appName
        graceCountdown = grace

        preferences.setPendingGrace(packageName, System.currentTimeMillis() + grace * 1000L)
        dispatcher.postWarningNotification(appName, grace, isYouTube)

        val spokenWarning = if (isYouTube) {
            "Master, you have been on YouTube during Study Mode. Please ensure it is educational or return to your task within $grace seconds."
        } else {
            "You are spending time on a distracting app, $appName. Please return to your task within $grace seconds."
        }
        AlfredEventBus.emitAnnouncement(spokenWarning)
        if (ttsReady && settings.alfredVoiceEnabled) {
            tts?.speak(spokenWarning, TextToSpeech.QUEUE_FLUSH, null, "focus_warning")
        }

        val session = db.focusDao().getRunningSession()
        if (session != null) {
            db.focusDao().updateFocusSession(session.copy(warningsIssued = session.warningsIssued + 1))
        }
        val today = DateUtil.todayEpochDay()
        val stat = db.focusDao().getDailyStatOnce(today) ?: DailyStatEntity(epochDay = today)
        db.focusDao().upsertDailyStat(stat.copy(studyWarningsCount = stat.studyWarningsCount + 1))
    }

    private fun checkStillInPackage(pkg: String): Boolean {
        val fg = focusController.queryForegroundPackageName()
        // If UsageAccess is unavailable, still respect explicit simulation/warning flow only if not back in VISION
        return fg == null || fg == pkg
    }

    private suspend fun applyFocusShield(appName: String, pkg: String) {
        val session = db.focusDao().getRunningSession()
        if (session != null) {
            db.focusDao().updateFocusSession(session.copy(blocksTriggered = session.blocksTriggered + 1))
        }
        val today = DateUtil.todayEpochDay()
        val stat = db.focusDao().getDailyStatOnce(today) ?: DailyStatEntity(epochDay = today)
        db.focusDao().upsertDailyStat(stat.copy(blocksCount = stat.blocksCount + 1))

        val shieldIntent = Intent(this, FocusShieldActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(FocusShieldActivity.EXTRA_BLOCKED_APP_NAME, appName)
            putExtra(FocusShieldActivity.EXTRA_BLOCKED_PKG, pkg)
        }
        runCatching { startActivity(shieldIntent) }
    }

    private fun stopAndCompleteSession(completedNormally: Boolean) {
        timerJob?.cancel()
        scope.launch {
            val running = db.focusDao().getRunningSession()
            val now = System.currentTimeMillis()
            if (running != null) {
                val elapsedSecs = ((now - running.startedAtMillis) / 1000L).toInt().coerceAtLeast(0)
                db.focusDao().updateFocusSession(
                    running.copy(
                        endedAtMillis = now,
                        actualDurationSeconds = elapsedSecs,
                        status = if (completedNormally) FocusSessionStatus.COMPLETED else FocusSessionStatus.CANCELLED
                    )
                )
                val today = DateUtil.todayEpochDay()
                val stat = db.focusDao().getDailyStatOnce(today) ?: DailyStatEntity(epochDay = today)
                val mins = (elapsedSecs / 60).coerceAtLeast(if (completedNormally) running.plannedDurationMinutes else 0)
                db.focusDao().upsertDailyStat(stat.copy(focusMinutes = stat.focusMinutes + mins))
            }
            preferences.setActiveFocusState(false, 0L, 0L)
            AlfredEventBus.updateFocusRuntime(FocusRuntimeSnapshot(isRunning = false))
            if (completedNormally) {
                val msg = "Focus session complete, Master. Well done."
                dispatcher.postScheduleNotification("VISION Focus Complete", msg)
                AlfredEventBus.emitAnnouncement(msg)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        scope.cancel()
    }
}
