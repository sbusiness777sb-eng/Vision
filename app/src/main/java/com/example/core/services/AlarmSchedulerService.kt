package com.example.core.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.core.util.DateUtil
import com.example.data.local.AlarmDao
import com.example.data.local.AlarmEntity
import com.example.services.AlarmReceiver
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class AlarmSchedulerService(
    private val context: Context,
    private val alarmDao: AlarmDao
) : AlarmManagerContract {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun observeAlarms(): Flow<List<AlarmEntity>> = alarmDao.observeAlarms()

    override suspend fun upsertAlarm(alarm: AlarmEntity): Long {
        val id = if (alarm.id == 0L) {
            alarmDao.insertAlarm(alarm)
        } else {
            alarmDao.updateAlarm(alarm)
            alarm.id
        }
        val saved = alarm.copy(id = id)
        if (saved.isEnabled) {
            scheduleExactAlarm(saved)
        } else {
            cancelAlarm(saved.id)
        }
        return id
    }

    override suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean) {
        alarmDao.setAlarmEnabled(alarmId, enabled)
        val alarm = alarmDao.getAlarmById(alarmId) ?: return
        if (enabled) {
            scheduleExactAlarm(alarm)
        } else {
            cancelAlarm(alarmId)
        }
    }

    override suspend fun deleteAlarm(alarmId: Long) {
        cancelAlarm(alarmId)
        alarmDao.deleteAlarmById(alarmId)
    }

    override suspend fun rescheduleAllEnabledAlarms() {
        val enabledAlarms = alarmDao.getEnabledAlarmsOnce()
        enabledAlarms.forEach { scheduleExactAlarm(it) }
    }

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleSnooze(alarmId: Long, label: String, snoozeMinutes: Int) {
        val triggerAt = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val pending = createAlarmPendingIntent(alarmId, label, "Snoozed +${snoozeMinutes}m")
        scheduleTrigger(triggerAt, pending)
    }

    fun scheduleExactAlarm(alarm: AlarmEntity) {
        val triggerAtMillis = computeNextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask)
        val timeFormatted = DateUtil.formatTime12Hour(alarm.hour, alarm.minute)
        val pending = createAlarmPendingIntent(alarm.id, alarm.label, timeFormatted)
        scheduleTrigger(triggerAtMillis, pending)
    }

    private fun scheduleTrigger(triggerAtMillis: Long, pending: PendingIntent) {
        try {
            val showIntent = Intent(context, com.example.MainActivity::class.java)
            val showPending = PendingIntent.getActivity(
                context,
                9900,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (canScheduleExactAlarms()) {
                val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPending)
                alarmManager.setAlarmClock(info, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Exact alarm permission not granted; falling back", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    fun cancelAlarm(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER
        }
        val pending = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    private fun createAlarmPendingIntent(
        alarmId: Long,
        label: String,
        timeFormatted: String
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ALARM_TIME, timeFormatted)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        fun computeNextTriggerMillis(hour: Int, minute: Int, repeatDaysMask: Int): Long {
            val now = Calendar.getInstance()
            val candidate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (repeatDaysMask == 0) {
                if (candidate.timeInMillis <= now.timeInMillis) {
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
                return candidate.timeInMillis
            }
            for (dayOffset in 0..7) {
                val check = (candidate.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }
                if (check.timeInMillis <= now.timeInMillis) continue
                val bit = calendarDayToMaskBit(check.get(Calendar.DAY_OF_WEEK))
                if ((repeatDaysMask and bit) != 0) {
                    return check.timeInMillis
                }
            }
            if (candidate.timeInMillis <= now.timeInMillis) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            return candidate.timeInMillis
        }

        fun calendarDayToMaskBit(dayOfWeek: Int): Int = when (dayOfWeek) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 4
            Calendar.THURSDAY -> 8
            Calendar.FRIDAY -> 16
            Calendar.SATURDAY -> 32
            Calendar.SUNDAY -> 64
            else -> 0
        }

        fun formatRepeatMask(mask: Int): String {
            if (mask == 0) return "Once"
            if (mask == 127) return "Every day"
            if (mask == 31) return "Weekdays"
            if (mask == 96) return "Weekends"
            val labels = listOf(1 to "Mon", 2 to "Tue", 4 to "Wed", 8 to "Thu", 16 to "Fri", 32 to "Sat", 64 to "Sun")
            return labels.filter { (bit, _) -> (mask and bit) != 0 }.joinToString(", ") { it.second }
        }
    }
}
