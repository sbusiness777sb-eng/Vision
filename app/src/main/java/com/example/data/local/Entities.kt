package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskPriority { HIGH, MEDIUM, LOW }

enum class TaskCategory { WORK, PERSONAL, STUDY, HEALTH, SKILL }

enum class RoutineType { YOGA, EXERCISE, SKILL_DEV, DAILY_HABIT }

enum class AlarmType { WAKE_UP, CUSTOM_REMINDER, DAILY_SCHEDULE }

enum class FocusSessionStatus { RUNNING, COMPLETED, CANCELLED }

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val scheduledDate: Long,
    val timeSlot: String = "09:00",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val category: TaskCategory = TaskCategory.WORK,
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val isTopPriority: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val targetDescription: String = "",
    val currentValue: Int = 0,
    val targetValue: Int = 100,
    val unit: String = "%",
    val category: String = "Personal",
    val isCompleted: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val progressPercent: Int
        get() = if (targetValue <= 0) 0 else ((currentValue * 100f) / targetValue).toInt().coerceIn(0, 100)
}

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val routineType: RoutineType,
    val targetMinutes: Int = 20,
    val completedMinutesToday: Int = 0,
    val streakDays: Int = 0,
    val lastCompletedEpochDay: Long? = null,
    val scheduledTime: String = "07:00"
)

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val hour: Int,
    val minute: Int,
    /** Bitmask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64; 0 = One-time */
    val repeatDaysMask: Int = 0,
    val isEnabled: Boolean = true,
    val alarmType: AlarmType = AlarmType.WAKE_UP,
    val snoozeMinutes: Int = 5,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val alfredAnnouncementEnabled: Boolean = true
)

@Entity(tableName = "distracting_apps")
data class DistractingAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 30,
    val isSelectedForBlocking: Boolean = false,
    val isYouTubeSpecial: Boolean = false
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val plannedDurationMinutes: Int,
    val actualDurationSeconds: Int = 0,
    val isStudyMode: Boolean = false,
    val gracePeriodSeconds: Int = 30,
    val warningsIssued: Int = 0,
    val blocksTriggered: Int = 0,
    val status: FocusSessionStatus = FocusSessionStatus.RUNNING
)

@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey val epochDay: Long,
    val focusMinutes: Int = 0,
    val distractingMinutes: Int = 0,
    val studyWarningsCount: Int = 0,
    val blocksCount: Int = 0
)

@Entity(tableName = "alfred_logs")
data class AlfredLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long = System.currentTimeMillis(),
    val speaker: String, // "USER" or "ALFRED"
    val message: String,
    val actionSummary: String = "",
    val isAnnouncement: Boolean = false
)
