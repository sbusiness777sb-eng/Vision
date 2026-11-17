package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.visionDataStore: DataStore<Preferences> by preferencesDataStore(name = "vision_settings")

data class VisionSettings(
    val isSeeded: Boolean = false,
    val studyModeEnabled: Boolean = false,
    val studyGracePeriodSeconds: Int = 30,
    val autoBlockEnabled: Boolean = true,
    val blockingDurationMinutes: Int = 45,
    val dailyScheduleNotificationsEnabled: Boolean = true,
    val alfredVoiceEnabled: Boolean = true,
    val alfredMorningBriefingEnabled: Boolean = true,
    val activeFocusRunning: Boolean = false,
    val activeFocusEndEpochMillis: Long = 0L,
    val activeFocusSessionId: Long = 0L,
    val pendingGracePackage: String = "",
    val pendingGraceExpiresMillis: Long = 0L
)

class VisionPreferences(private val context: Context) {
    private object Keys {
        val IS_SEEDED = booleanPreferencesKey("is_seeded")
        val STUDY_MODE_ENABLED = booleanPreferencesKey("study_mode_enabled")
        val STUDY_GRACE_SECONDS = intPreferencesKey("study_grace_seconds")
        val AUTO_BLOCK_ENABLED = booleanPreferencesKey("auto_block_enabled")
        val BLOCKING_DURATION_MIN = intPreferencesKey("blocking_duration_min")
        val DAILY_SCHEDULE_NOTIF = booleanPreferencesKey("daily_schedule_notif")
        val ALFRED_VOICE_ENABLED = booleanPreferencesKey("alfred_voice_enabled")
        val ALFRED_MORNING_BRIEFING = booleanPreferencesKey("alfred_morning_briefing")
        val ACTIVE_FOCUS_RUNNING = booleanPreferencesKey("active_focus_running")
        val ACTIVE_FOCUS_END_MS = androidx.datastore.preferences.core.longPreferencesKey("active_focus_end_ms")
        val ACTIVE_FOCUS_SESSION_ID = androidx.datastore.preferences.core.longPreferencesKey("active_focus_session_id")
        val PENDING_GRACE_PKG = stringPreferencesKey("pending_grace_pkg")
        val PENDING_GRACE_EXPIRES_MS = androidx.datastore.preferences.core.longPreferencesKey("pending_grace_expires_ms")
    }

    val settingsFlow: Flow<VisionSettings> = context.visionDataStore.data.map { prefs ->
        VisionSettings(
            isSeeded = prefs[Keys.IS_SEEDED] ?: false,
            studyModeEnabled = prefs[Keys.STUDY_MODE_ENABLED] ?: false,
            studyGracePeriodSeconds = prefs[Keys.STUDY_GRACE_SECONDS] ?: 30,
            autoBlockEnabled = prefs[Keys.AUTO_BLOCK_ENABLED] ?: true,
            blockingDurationMinutes = prefs[Keys.BLOCKING_DURATION_MIN] ?: 45,
            dailyScheduleNotificationsEnabled = prefs[Keys.DAILY_SCHEDULE_NOTIF] ?: true,
            alfredVoiceEnabled = prefs[Keys.ALFRED_VOICE_ENABLED] ?: true,
            alfredMorningBriefingEnabled = prefs[Keys.ALFRED_MORNING_BRIEFING] ?: true,
            activeFocusRunning = prefs[Keys.ACTIVE_FOCUS_RUNNING] ?: false,
            activeFocusEndEpochMillis = prefs[Keys.ACTIVE_FOCUS_END_MS] ?: 0L,
            activeFocusSessionId = prefs[Keys.ACTIVE_FOCUS_SESSION_ID] ?: 0L,
            pendingGracePackage = prefs[Keys.PENDING_GRACE_PKG] ?: "",
            pendingGraceExpiresMillis = prefs[Keys.PENDING_GRACE_EXPIRES_MS] ?: 0L
        )
    }

    suspend fun getSettingsOnce(): VisionSettings = settingsFlow.first()

    suspend fun markSeeded() {
        context.visionDataStore.edit { it[Keys.IS_SEEDED] = true }
    }

    suspend fun setStudyMode(enabled: Boolean) {
        context.visionDataStore.edit { it[Keys.STUDY_MODE_ENABLED] = enabled }
    }

    suspend fun setStudyGracePeriodSeconds(seconds: Int) {
        context.visionDataStore.edit { it[Keys.STUDY_GRACE_SECONDS] = seconds.coerceIn(10, 180) }
    }

    suspend fun setAutoBlock(enabled: Boolean) {
        context.visionDataStore.edit { it[Keys.AUTO_BLOCK_ENABLED] = enabled }
    }

    suspend fun setBlockingDurationMinutes(minutes: Int) {
        context.visionDataStore.edit { it[Keys.BLOCKING_DURATION_MIN] = minutes.coerceIn(5, 180) }
    }

    suspend fun setDailyScheduleNotifications(enabled: Boolean) {
        context.visionDataStore.edit { it[Keys.DAILY_SCHEDULE_NOTIF] = enabled }
    }

    suspend fun setAlfredVoiceEnabled(enabled: Boolean) {
        context.visionDataStore.edit { it[Keys.ALFRED_VOICE_ENABLED] = enabled }
    }

    suspend fun setAlfredMorningBriefing(enabled: Boolean) {
        context.visionDataStore.edit { it[Keys.ALFRED_MORNING_BRIEFING] = enabled }
    }

    suspend fun setActiveFocusState(running: Boolean, endEpochMillis: Long, sessionId: Long) {
        context.visionDataStore.edit {
            it[Keys.ACTIVE_FOCUS_RUNNING] = running
            it[Keys.ACTIVE_FOCUS_END_MS] = endEpochMillis
            it[Keys.ACTIVE_FOCUS_SESSION_ID] = sessionId
            if (!running) {
                it[Keys.PENDING_GRACE_PKG] = ""
                it[Keys.PENDING_GRACE_EXPIRES_MS] = 0L
            }
        }
    }

    suspend fun setPendingGrace(packageName: String, expiresMillis: Long) {
        context.visionDataStore.edit {
            it[Keys.PENDING_GRACE_PKG] = packageName
            it[Keys.PENDING_GRACE_EXPIRES_MS] = expiresMillis
        }
    }
}
