package com.example.core.services

import com.example.data.local.AlarmDao
import com.example.data.local.AlarmEntity
import com.example.data.local.AlarmType
import com.example.data.local.GoalRoutineDao
import com.example.data.local.RoutineEntity
import com.example.data.local.RoutineType
import com.example.data.preferences.VisionPreferences

/**
 * Initializes clean starter structure (empty routines ready for user logging, 1 default
 * wake-up alarm slot, and app candidates) on first launch without fabricating fake statistics
 * or completed task history.
 */
object VisionSeedHelper {
    suspend fun ensureInitialized(
        preferences: VisionPreferences,
        goalRoutineDao: GoalRoutineDao,
        alarmDao: AlarmDao,
        focusController: FocusController
    ) {
        val settings = preferences.getSettingsOnce()
        if (settings.isSeeded) return

        if (goalRoutineDao.getRoutinesOnce().isEmpty()) {
            goalRoutineDao.insertRoutine(
                RoutineEntity(
                    title = "Morning Yoga & Mobility",
                    routineType = RoutineType.YOGA,
                    targetMinutes = 20,
                    completedMinutesToday = 0,
                    streakDays = 0,
                    scheduledTime = "06:45"
                )
            )
            goalRoutineDao.insertRoutine(
                RoutineEntity(
                    title = "Strength & Conditioning",
                    routineType = RoutineType.EXERCISE,
                    targetMinutes = 30,
                    completedMinutesToday = 0,
                    streakDays = 0,
                    scheduledTime = "07:15"
                )
            )
            goalRoutineDao.insertRoutine(
                RoutineEntity(
                    title = "Skill Development Deep Practice",
                    routineType = RoutineType.SKILL_DEV,
                    targetMinutes = 45,
                    completedMinutesToday = 0,
                    streakDays = 0,
                    scheduledTime = "18:00"
                )
            )
        }

        if (alarmDao.getAllAlarmsOnce().isEmpty()) {
            alarmDao.insertAlarm(
                AlarmEntity(
                    label = "Morning Wake-Up",
                    hour = 6,
                    minute = 30,
                    repeatDaysMask = 31, // Weekdays
                    isEnabled = false,
                    alarmType = AlarmType.WAKE_UP,
                    snoozeMinutes = 5,
                    vibrate = true,
                    alfredAnnouncementEnabled = true
                )
            )
        }

        focusController.refreshInstalledApps()
        preferences.markSeeded()
    }
}
