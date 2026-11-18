package com.example.core.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.example.core.util.DateUtil
import com.example.data.local.DailyStatEntity
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusDao
import com.example.data.local.FocusSessionEntity
import com.example.data.local.FocusSessionStatus
import com.example.data.preferences.VisionPreferences
import com.example.services.FocusCompletionWorker
import com.example.services.FocusShieldActivity
import com.example.services.NotificationDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

class FocusController(
    private val context: Context,
    private val focusDao: FocusDao,
    private val preferences: VisionPreferences
) : FocusManagerContract {

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private val dispatcher by lazy { NotificationDispatcher(context) }

    private var timerJob: Job? = null
    private var warningGraceCountdown: Int = 0
    private var warningAppName: String? = null
    private var warningPackageName: String? = null

    override fun observeDistractingApps(): Flow<List<DistractingAppEntity>> =
        focusDao.observeDistractingApps()

    override fun observeFocusSessions(): Flow<List<FocusSessionEntity>> =
        focusDao.observeFocusSessions()

    override fun observeDailyStats(): Flow<List<DailyStatEntity>> =
        focusDao.observeDailyStats()

    override suspend fun toggleAppBlocked(packageName: String, selected: Boolean) {
        focusDao.setAppSelected(packageName, selected)
    }

    override suspend fun updateAppDailyLimit(packageName: String, minutes: Int) {
        focusDao.updateAppDailyLimit(packageName, minutes.coerceIn(5, 240))
    }

    override suspend fun addCustomDistractingApp(appName: String, dailyLimitMinutes: Int) {
        val cleanName = appName.trim()
        if (cleanName.isBlank()) return
        val slug = cleanName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), ".").trim('.')
        val pkg = "custom.distraction.$slug"
        focusDao.insertDistractingAppsIgnore(
            listOf(
                DistractingAppEntity(
                    packageName = pkg,
                    appName = cleanName,
                    dailyLimitMinutes = dailyLimitMinutes.coerceIn(5, 240),
                    isSelectedForBlocking = true,
                    isYouTubeSpecial = cleanName.contains("youtube", ignoreCase = true)
                )
            )
        )
    }

    override suspend fun refreshInstalledApps() {
        withContext(Dispatchers.IO) {
            val curatedCatalog = listOf(
                DistractingAppEntity(
                    packageName = "com.google.android.youtube",
                    appName = "YouTube",
                    dailyLimitMinutes = 45,
                    isSelectedForBlocking = true,
                    isYouTubeSpecial = true
                ),
                DistractingAppEntity(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    dailyLimitMinutes = 20,
                    isSelectedForBlocking = true,
                    isYouTubeSpecial = false
                ),
                DistractingAppEntity(
                    packageName = "com.zhiliaoapp.musically",
                    appName = "TikTok",
                    dailyLimitMinutes = 15,
                    isSelectedForBlocking = false,
                    isYouTubeSpecial = false
                ),
                DistractingAppEntity(
                    packageName = "com.twitter.android",
                    appName = "X (Twitter)",
                    dailyLimitMinutes = 20,
                    isSelectedForBlocking = false,
                    isYouTubeSpecial = false
                ),
                DistractingAppEntity(
                    packageName = "com.reddit.frontpage",
                    appName = "Reddit",
                    dailyLimitMinutes = 25,
                    isSelectedForBlocking = false,
                    isYouTubeSpecial = false
                ),
                DistractingAppEntity(
                    packageName = "com.facebook.katana",
                    appName = "Facebook",
                    dailyLimitMinutes = 20,
                    isSelectedForBlocking = false,
                    isYouTubeSpecial = false
                ),
                DistractingAppEntity(
                    packageName = "com.discord",
                    appName = "Discord",
                    dailyLimitMinutes = 30,
                    isSelectedForBlocking = false,
                    isYouTubeSpecial = false
                ),
                DistractingAppEntity(
                    packageName = "com.netflix.mediaclient",
                    appName = "Netflix",
                    dailyLimitMinutes = 45,
                    isSelectedForBlocking = false,
                    isYouTubeSpecial = false
                )
            )
            focusDao.insertDistractingAppsIgnore(curatedCatalog)
        }
    }

    suspend fun resumeIfActive() {
        val settings = preferences.getSettingsOnce()
        if (!settings.activeFocusRunning) return
        val now = System.currentTimeMillis()
        val remainingSec = ((settings.activeFocusEndEpochMillis - now) / 1000L).toInt()
        if (remainingSec <= 0) {
            stopFocusSession(completedNormally = true)
        } else {
            val running = focusDao.getRunningSession()
            val totalSec = ((running?.plannedDurationMinutes ?: 25) * 60).coerceAtLeast(remainingSec)
            launchCountdownLoop(totalSec, settings.activeFocusEndEpochMillis, settings.studyModeEnabled)
        }
    }

    override suspend fun startFocusSession(durationMinutes: Int, studyMode: Boolean) {
        sessionMutex.withLock {
            val safeDuration = durationMinutes.coerceIn(1, 240)
            preferences.setStudyMode(studyMode)
            val settings = preferences.getSettingsOnce()
            val now = System.currentTimeMillis()

            val existingRunning = focusDao.getRunningSession()
            if (existingRunning != null) {
                focusDao.updateFocusSession(
                    existingRunning.copy(
                        status = FocusSessionStatus.CANCELLED,
                        endedAtMillis = now
                    )
                )
            }

            val sessionId = focusDao.insertFocusSession(
                FocusSessionEntity(
                    startedAtMillis = now,
                    plannedDurationMinutes = safeDuration,
                    isStudyMode = studyMode,
                    gracePeriodSeconds = settings.studyGracePeriodSeconds,
                    status = FocusSessionStatus.RUNNING
                )
            )
            val endMillis = now + safeDuration * 60_000L
            preferences.setActiveFocusState(running = true, endEpochMillis = endMillis, sessionId = sessionId)
            warningGraceCountdown = 0
            warningAppName = null
            warningPackageName = null

            FocusCompletionWorker.schedule(context, safeDuration)
            launchCountdownLoop(safeDuration * 60, endMillis, studyMode)
        }
    }

    override suspend fun stopFocusSession(completedNormally: Boolean) {
        sessionMutex.withLock {
            timerJob?.cancel()
            timerJob = null
            warningGraceCountdown = 0
            warningAppName = null
            warningPackageName = null
            FocusCompletionWorker.cancel(context)

            val running = focusDao.getRunningSession()
            val now = System.currentTimeMillis()
            if (running != null) {
                val elapsedSeconds = ((now - running.startedAtMillis) / 1000L).toInt().coerceAtLeast(0)
                focusDao.updateFocusSession(
                    running.copy(
                        endedAtMillis = now,
                        actualDurationSeconds = elapsedSeconds,
                        status = if (completedNormally) FocusSessionStatus.COMPLETED else FocusSessionStatus.CANCELLED
                    )
                )
                val today = DateUtil.todayEpochDay()
                val stat = focusDao.getDailyStatOnce(today) ?: DailyStatEntity(epochDay = today)
                val addedMinutes = (elapsedSeconds / 60).coerceAtLeast(
                    if (completedNormally) running.plannedDurationMinutes else 0
                )
                focusDao.upsertDailyStat(stat.copy(focusMinutes = stat.focusMinutes + addedMinutes))
            }

            preferences.setActiveFocusState(running = false, endEpochMillis = 0L, sessionId = 0L)
            AlfredEventBus.updateFocusRuntime(FocusRuntimeSnapshot(isRunning = false))
            cancelFocusNotification()

            if (completedNormally) {
                val msg = "Focus session complete, Master. Well done."
                dispatcher.postScheduleNotification("VISION Focus Complete", msg)
                AlfredEventBus.emitAnnouncement(msg)
            }
        }
    }

    suspend fun triggerStudyWarningCheck(appName: String, isYouTube: Boolean) {
        val settings = preferences.getSettingsOnce()
        val grace = settings.studyGracePeriodSeconds
        val pkg = if (isYouTube) "com.google.android.youtube" else "custom.distraction.app"
        warningPackageName = pkg
        warningAppName = appName
        warningGraceCountdown = grace

        preferences.setPendingGrace(pkg, System.currentTimeMillis() + grace * 1000L)
        dispatcher.postWarningNotification(appName, grace, isYouTube)

        val spokenWarning = if (isYouTube) {
            "Master, please keep YouTube strictly for educational study or return to your task within $grace seconds."
        } else {
            "Reminder: $appName is on your distraction list. Return to your focus task within $grace seconds."
        }
        AlfredEventBus.emitAnnouncement(spokenWarning)

        val session = focusDao.getRunningSession()
        if (session != null) {
            focusDao.updateFocusSession(session.copy(warningsIssued = session.warningsIssued + 1))
        }
        val today = DateUtil.todayEpochDay()
        val stat = focusDao.getDailyStatOnce(today) ?: DailyStatEntity(epochDay = today)
        focusDao.upsertDailyStat(stat.copy(studyWarningsCount = stat.studyWarningsCount + 1))
    }

    private fun launchCountdownLoop(totalSeconds: Int, endEpochMillis: Long, studyMode: Boolean) {
        timerJob?.cancel()
        timerJob = controllerScope.launch {
            while (isActive) {
                val remaining = ((endEpochMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                AlfredEventBus.updateFocusRuntime(
                    FocusRuntimeSnapshot(
                        isRunning = true,
                        remainingSeconds = remaining,
                        totalSeconds = totalSeconds,
                        isStudyMode = studyMode,
                        activeWarningAppName = warningAppName,
                        graceRemainingSeconds = warningGraceCountdown
                    )
                )

                postOrUpdateFocusNotification(
                    DateUtil.formatDurationSeconds(remaining),
                    studyMode
                )

                if (remaining <= 0) {
                    stopFocusSession(completedNormally = true)
                    break
                }

                if (warningGraceCountdown > 0) {
                    warningGraceCountdown -= 1
                    if (warningGraceCountdown == 0 && warningAppName != null) {
                        val settings = preferences.getSettingsOnce()
                        if (settings.autoBlockEnabled) {
                            applyFocusShield(
                                appName = warningAppName ?: "Distracting App",
                                pkg = warningPackageName ?: ""
                            )
                        }
                        warningAppName = null
                        warningPackageName = null
                    }
                }

                delay(1000L)
            }
        }
    }

    private suspend fun applyFocusShield(appName: String, pkg: String) {
        val session = focusDao.getRunningSession()
        if (session != null) {
            focusDao.updateFocusSession(session.copy(blocksTriggered = session.blocksTriggered + 1))
        }
        val today = DateUtil.todayEpochDay()
        val stat = focusDao.getDailyStatOnce(today) ?: DailyStatEntity(epochDay = today)
        focusDao.upsertDailyStat(stat.copy(blocksCount = stat.blocksCount + 1))

        val shieldIntent = Intent(context, FocusShieldActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(FocusShieldActivity.EXTRA_BLOCKED_APP_NAME, appName)
            putExtra(FocusShieldActivity.EXTRA_BLOCKED_PKG, pkg)
        }
        runCatching { context.startActivity(shieldIntent) }
    }

    private fun postOrUpdateFocusNotification(remainingFormatted: String, isStudyMode: Boolean) {
        if (!dispatcher.hasNotificationPermission()) return
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                NotificationDispatcher.NOTIF_ID_FOCUS,
                dispatcher.buildFocusOngoingNotification(remainingFormatted, isStudyMode)
            )
        }
    }

    private fun cancelFocusNotification() {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NotificationDispatcher.NOTIF_ID_FOCUS)
        }
    }
}
