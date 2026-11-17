package com.example.core.services

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.core.util.DateUtil
import com.example.data.local.DailyStatEntity
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusDao
import com.example.data.local.FocusSessionEntity
import com.example.data.local.FocusSessionStatus
import com.example.data.preferences.VisionPreferences
import com.example.services.FocusAccessibilityService
import com.example.services.FocusMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FocusController(
    private val context: Context,
    private val focusDao: FocusDao,
    private val preferences: VisionPreferences
) : FocusManagerContract {

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

    override suspend fun refreshInstalledApps() {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
            val apps = resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                    val isYt = pkg == "com.google.android.youtube" || pkg.contains("youtube")
                    DistractingAppEntity(
                        packageName = pkg,
                        appName = label,
                        dailyLimitMinutes = if (isYt) 45 else 30,
                        isSelectedForBlocking = isYt || isCommonSocialPackage(pkg),
                        isYouTubeSpecial = isYt
                    )
                }
                .distinctBy { it.packageName }

            val curatedDefaults = listOf(
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
                    isSelectedForBlocking = false,
                    isYouTubeSpecial = false
                ),
                DistractingAppEntity(
                    packageName = "com.zhiliaoapp.musically",
                    appName = "TikTok",
                    dailyLimitMinutes = 15,
                    isSelectedForBlocking = false,
                    isYouTubeSpecial = false
                )
            )
            focusDao.insertDistractingAppsIgnore((apps + curatedDefaults).distinctBy { it.packageName })
        }
    }

    override suspend fun startFocusSession(durationMinutes: Int, studyMode: Boolean) {
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

        val serviceIntent = Intent(context, FocusMonitorService::class.java).apply {
            action = FocusMonitorService.ACTION_START_FOCUS
            putExtra(FocusMonitorService.EXTRA_SESSION_ID, sessionId)
            putExtra(FocusMonitorService.EXTRA_DURATION_MINUTES, safeDuration)
            putExtra(FocusMonitorService.EXTRA_STUDY_MODE, studyMode)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    override suspend fun stopFocusSession(completedNormally: Boolean) {
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
            val addedMinutes = (elapsedSeconds / 60).coerceAtLeast(if (completedNormally) running.plannedDurationMinutes else 0)
            focusDao.upsertDailyStat(stat.copy(focusMinutes = stat.focusMinutes + addedMinutes))
        }
        preferences.setActiveFocusState(running = false, endEpochMillis = 0L, sessionId = 0L)
        val stopIntent = Intent(context, FocusMonitorService::class.java).apply {
            action = FocusMonitorService.ACTION_STOP_FOCUS
        }
        runCatching { context.stopService(stopIntent) }
    }

    override fun hasUsageAccessPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    override fun isAccessibilityGuardEnabled(): Boolean {
        if (FocusAccessibilityService.isServiceConnected) return true
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val expected = "${context.packageName}/${FocusAccessibilityService::class.java.name}"
            enabledServices.contains(expected) || enabledServices.contains(FocusAccessibilityService::class.java.simpleName)
        } catch (_: Exception) {
            false
        }
    }

    override fun queryTodayAppUsageMinutes(): Map<String, Int> {
        if (!hasUsageAccessPermission()) return emptyMap()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val start = DateUtil.epochDayToStartMillis(DateUtil.todayEpochDay())
            val end = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val result = mutableMapOf<String, Int>()
            stats?.forEach { usage ->
                val minutes = (usage.totalTimeInForeground / 60_000L).toInt()
                if (minutes > 0) {
                    result[usage.packageName] = (result[usage.packageName] ?: 0) + minutes
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun queryForegroundPackageName(): String? {
        if (!hasUsageAccessPermission()) return null
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 15_000L
            val events = usm.queryEvents(begin, end)
            val event = android.app.usage.UsageEvents.Event()
            var lastPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (isForegroundEvent) {
                    lastPkg = event.packageName
                }
            }
            lastPkg
        } catch (_: Exception) {
            null
        }
    }

    private fun isCommonSocialPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("instagram") ||
            lower.contains("tiktok") ||
            lower.contains("musically") ||
            lower.contains("facebook") ||
            lower.contains("reddit") ||
            lower.contains("twitter") ||
            lower.contains("snapchat")
    }
}
