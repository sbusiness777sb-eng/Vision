package com.example

import android.app.Application
import android.util.Log
import com.example.core.services.AlarmSchedulerService
import com.example.core.services.FocusController
import com.example.core.services.VisionSeedHelper
import com.example.core.services.VisionTaskScheduleRepository
import com.example.data.local.VisionDatabase
import com.example.data.preferences.VisionPreferences
import com.example.services.NotificationDispatcher
import com.example.services.VisionScheduleWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VisionApplication : Application() {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("VisionApplication", "Background initialization warning", throwable)
    }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    val database: VisionDatabase by lazy { VisionDatabase.getInstance(this) }
    val preferences: VisionPreferences by lazy { VisionPreferences(this) }
    val notificationDispatcher: NotificationDispatcher by lazy { NotificationDispatcher(this) }

    val taskScheduleRepository: VisionTaskScheduleRepository by lazy {
        VisionTaskScheduleRepository(
            taskDao = database.taskDao(),
            goalRoutineDao = database.goalRoutineDao(),
            focusDao = database.focusDao()
        )
    }

    val alarmScheduler: AlarmSchedulerService by lazy {
        AlarmSchedulerService(this, database.alarmDao())
    }

    val focusController: FocusController by lazy {
        FocusController(this, database.focusDao(), preferences)
    }

    override fun onCreate() {
        super.onCreate()
        // Note: App Check is configured strictly in MainActivity.onCreate(savedInstanceState)
        // with the launch intent, never in Application.onCreate().
        notificationDispatcher.ensureChannels()
        applicationScope.launch {
            runCatching {
                VisionSeedHelper.ensureInitialized(
                    preferences = preferences,
                    goalRoutineDao = database.goalRoutineDao(),
                    alarmDao = database.alarmDao(),
                    focusController = focusController
                )
                alarmScheduler.rescheduleAllEnabledAlarms()
                VisionScheduleWorker.enqueuePeriodic(this@VisionApplication)
            }.onFailure {
                Log.e("VisionApplication", "Startup initialization failed", it)
            }
        }
    }
}
