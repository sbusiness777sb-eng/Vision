package com.example.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.core.services.AlarmSchedulerService
import com.example.data.local.VisionDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIGGER = "com.example.vision.ACTION_ALARM_TRIGGER"
        const val ACTION_DISMISS = "com.example.vision.ACTION_ALARM_DISMISS"
        const val ACTION_SNOOZE = "com.example.vision.ACTION_ALARM_SNOOZE"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "VISION Alarm"
        val timeText = intent.getStringExtra(EXTRA_ALARM_TIME) ?: ""

        when (intent.action) {
            ACTION_DISMISS -> {
                context.stopService(Intent(context, AlarmRingService::class.java))
                val closeUi = Intent(AlarmRingingActivity.ACTION_CLOSE_ALARM_UI)
                closeUi.setPackage(context.packageName)
                context.sendBroadcast(closeUi)
            }

            ACTION_SNOOZE -> {
                context.stopService(Intent(context, AlarmRingService::class.java))
                val closeUi = Intent(AlarmRingingActivity.ACTION_CLOSE_ALARM_UI)
                closeUi.setPackage(context.packageName)
                context.sendBroadcast(closeUi)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = VisionDatabase.getInstance(context)
                        val alarm = if (alarmId > 0L) db.alarmDao().getAlarmById(alarmId) else null
                        val snoozeMins = alarm?.snoozeMinutes ?: 5
                        val scheduler = AlarmSchedulerService(context, db.alarmDao())
                        scheduler.scheduleSnooze(alarmId.coerceAtLeast(1L), label, snoozeMins)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            else -> {
                val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
                    putExtra(EXTRA_ALARM_ID, alarmId)
                    putExtra(EXTRA_ALARM_LABEL, label)
                    putExtra(EXTRA_ALARM_TIME, timeText)
                }
                ContextCompat.startForegroundService(context, serviceIntent)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = VisionDatabase.getInstance(context)
                        val alarm = if (alarmId > 0L) db.alarmDao().getAlarmById(alarmId) else null
                        if (alarm != null) {
                            val scheduler = AlarmSchedulerService(context, db.alarmDao())
                            if (alarm.repeatDaysMask == 0) {
                                db.alarmDao().setAlarmEnabled(alarm.id, false)
                            } else {
                                scheduler.scheduleExactAlarm(alarm)
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
