package com.example.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class NotificationDispatcher(private val context: Context) {
    companion object {
        const val CHANNEL_ALARMS = "vision_alarms_channel"
        const val CHANNEL_FOCUS = "vision_focus_channel"
        const val CHANNEL_WARNINGS = "vision_warnings_channel"
        const val CHANNEL_SCHEDULE = "vision_schedule_channel"

        const val NOTIF_ID_FOCUS = 2001
        const val NOTIF_ID_WARNING = 2002
        const val NOTIF_ID_SCHEDULE = 2003
        const val NOTIF_ID_REPORT = 2004
    }

    init {
        ensureChannels()
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARMS,
                "VISION Alarms & Wake-Up",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority wake-up alarms and custom reminders"
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val focusChannel = NotificationChannel(
                CHANNEL_FOCUS,
                "VISION Focus Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing focus session and study mode countdown"
                setShowBadge(false)
            }

            val warningChannel = NotificationChannel(
                CHANNEL_WARNINGS,
                "Distraction & Study Mode Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when distracting apps or extended YouTube usage are detected"
                enableVibration(true)
            }

            val scheduleChannel = NotificationChannel(
                CHANNEL_SCHEDULE,
                "Daily Schedule & ALFRED Briefings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Upcoming tasks, daily progress, and ALFRED announcements"
            }

            nm.createNotificationChannels(
                listOf(alarmChannel, focusChannel, warningChannel, scheduleChannel)
            )
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildAlarmNotification(
        alarmId: Long,
        label: String,
        timeFormatted: String
    ): Notification {
        val fullScreenIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ALARM_TIME, timeFormatted)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context,
            alarmId.toInt() + 10_000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            alarmId.toInt() + 20_000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            alarmId.toInt() + 30_000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("VISION • $label")
            .setContentText("Scheduled for $timeFormatted — Tap to open")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .addAction(0, "Snooze", snoozePending)
            .addAction(0, "Dismiss", dismissPending)
            .build()
    }

    fun buildFocusOngoingNotification(
        remainingFormatted: String,
        isStudyMode: Boolean
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            5001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, FocusMonitorService::class.java).apply {
            action = FocusMonitorService.ACTION_STOP_FOCUS
        }
        val stopPending = PendingIntent.getService(
            context,
            5002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = if (isStudyMode) "Study Mode Active (YouTube Allowed)" else "Focus Mode Active"
        return NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("VISION • $modeLabel")
            .setContentText("Remaining time: $remainingFormatted")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(0, "End Session", stopPending)
            .build()
    }

    fun postWarningNotification(appName: String, graceSeconds: Int, isStudyModeYouTube: Boolean) {
        if (!hasNotificationPermission()) return
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            5003,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (isStudyModeYouTube) {
            "Extended YouTube usage detected during Study Mode. Return to educational work within ${graceSeconds}s or Focus Shield will activate."
        } else {
            "$appName is marked as distracting. Return to your task within ${graceSeconds}s before Focus Shield activates."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ALFRED Focus Warning")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_WARNING, notification)
    }

    fun postScheduleNotification(title: String, message: String, notificationId: Int = NOTIF_ID_SCHEDULE) {
        if (!hasNotificationPermission()) return
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
