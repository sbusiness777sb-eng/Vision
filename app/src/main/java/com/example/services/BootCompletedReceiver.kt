package com.example.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.services.AlarmSchedulerService
import com.example.data.local.VisionDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = VisionDatabase.getInstance(context)
                val scheduler = AlarmSchedulerService(context, db.alarmDao())
                scheduler.rescheduleAllEnabledAlarms()
                VisionScheduleWorker.enqueuePeriodic(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
