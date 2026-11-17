package com.example.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.services.VisionTaskScheduleRepository
import com.example.core.util.DateUtil
import com.example.data.local.AlfredLogEntity
import com.example.data.local.VisionDatabase
import com.example.data.preferences.VisionPreferences
import java.util.concurrent.TimeUnit

class VisionScheduleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = VisionDatabase.getInstance(applicationContext)
        val prefs = VisionPreferences(applicationContext).getSettingsOnce()
        if (!prefs.dailyScheduleNotificationsEnabled) return Result.success()

        val repo = VisionTaskScheduleRepository(
            db.taskDao(),
            db.goalRoutineDao(),
            db.focusDao()
        )
        val today = DateUtil.todayEpochDay()
        val tasksToday = db.taskDao().getTasksForDateOnce(today)
        val pendingToday = tasksToday.filter { !it.isCompleted }
        val (hour, minute) = DateUtil.currentHourMinute()
        val currentMinutes = hour * 60 + minute
        val dispatcher = NotificationDispatcher(applicationContext)

        // Check for tasks starting within 20 minutes
        val upcomingSoon = pendingToday.firstOrNull { task ->
            val parts = task.timeSlot.split(":")
            val taskHour = parts.getOrNull(0)?.toIntOrNull() ?: return@firstOrNull false
            val taskMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val diff = (taskHour * 60 + taskMin) - currentMinutes
            diff in 0..20
        }

        if (upcomingSoon != null) {
            val msg = "Master, your next task '${upcomingSoon.title}' is scheduled at ${upcomingSoon.timeSlot}."
            dispatcher.postScheduleNotification("ALFRED • Task Starting Soon", msg)
            db.alfredDao().insertLog(
                AlfredLogEntity(
                    speaker = "ALFRED",
                    message = msg,
                    actionSummary = "Scheduled Reminder",
                    isAnnouncement = true
                )
            )
        }

        // Evening daily report notification around 20:00-22:00 if tasks exist
        if (hour in 20..21 && tasksToday.isNotEmpty()) {
            val report = repo.getDailyReportSnapshot(today)
            val msg = "Your daily report is ready: ${report.completedTasks} of ${report.totalTasks} tasks completed (${report.productivityPercent}% productivity)."
            dispatcher.postScheduleNotification(
                "ALFRED • Daily Report Ready",
                msg,
                NotificationDispatcher.NOTIF_ID_REPORT
            )
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "vision_schedule_monitor_work"

        fun enqueuePeriodic(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<VisionScheduleWorker>(30, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}
