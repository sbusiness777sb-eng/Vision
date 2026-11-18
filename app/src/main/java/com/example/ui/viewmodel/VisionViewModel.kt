package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VisionApplication
import com.example.alfred.AlfredAssistantEngine
import com.example.alfred.AlfredUiState
import com.example.core.services.AlfredEventBus
import com.example.core.services.DailyProductivityReport
import com.example.core.services.FocusRuntimeSnapshot
import com.example.core.services.WeeklyProductivityReport
import com.example.core.util.DateUtil
import com.example.data.local.AlarmEntity
import com.example.data.local.AlarmType
import com.example.data.local.AlfredLogEntity
import com.example.data.local.DailyStatEntity
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.GoalEntity
import com.example.data.local.RoutineEntity
import com.example.data.local.RoutineType
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.preferences.VisionSettings
import com.example.ui.theme.VisionTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VisionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VisionApplication
    private val repo = app.taskScheduleRepository
    private val alarmScheduler = app.alarmScheduler
    private val focusController = app.focusController
    private val preferences = app.preferences

    private val _selectedTab = MutableStateFlow(VisionTab.DASHBOARD)
    val selectedTab: StateFlow<VisionTab> = _selectedTab.asStateFlow()

    val alfredEngine = AlfredAssistantEngine(
        context = app.applicationContext,
        taskManager = repo,
        scheduleManager = repo,
        alarmManager = alarmScheduler,
        focusManager = focusController,
        productivityAnalyzer = repo,
        alfredDao = app.database.alfredDao(),
        preferences = preferences,
        onNavigateTab = { route ->
            VisionTab.entries.firstOrNull { it.route == route }?.let { _selectedTab.value = it }
        }
    )

    val alfredState: StateFlow<AlfredUiState> = alfredEngine.uiState
    val alfredLogs: StateFlow<List<AlfredLogEntity>> = alfredEngine.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val todayEpoch = DateUtil.todayEpochDay()

    val todayTasks: StateFlow<List<TaskEntity>> = repo.observeTasksForDate(todayEpoch)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<TaskEntity>> = repo.observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<TaskEntity>> = repo.observePendingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<GoalEntity>> = repo.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<RoutineEntity>> = repo.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alarms: StateFlow<List<AlarmEntity>> = alarmScheduler.observeAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distractingApps: StateFlow<List<DistractingAppEntity>> = focusController.observeDistractingApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusSessions: StateFlow<List<FocusSessionEntity>> = focusController.observeFocusSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyStats: StateFlow<List<DailyStatEntity>> = focusController.observeDailyStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<VisionSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VisionSettings())

    val focusRuntime: StateFlow<FocusRuntimeSnapshot> = AlfredEventBus.focusRuntime

    val dailyReport: StateFlow<DailyProductivityReport> = repo.observeDailyReport(todayEpoch)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DailyProductivityReport(todayEpoch, 0, 0, 0, 0, 0, 0, 0, 0)
        )

    val weeklyReport: StateFlow<WeeklyProductivityReport> = repo.observeWeeklyReport()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            WeeklyProductivityReport(emptyList(), 0, 0, 0, 0, 0)
        )

    fun selectTab(tab: VisionTab) {
        _selectedTab.value = tab
    }

    // --- Dashboard Actions ---
    fun createTask(
        title: String,
        description: String,
        timeSlot: String,
        priority: TaskPriority,
        category: TaskCategory,
        isTopPriority: Boolean,
        forTomorrow: Boolean = false
    ) {
        viewModelScope.launch {
            val targetDate = if (forTomorrow) DateUtil.epochDayOffset(1) else todayEpoch
            repo.createTask(
                title = title,
                description = description,
                scheduledDate = targetDate,
                timeSlot = timeSlot,
                priority = priority,
                category = category,
                isTopPriority = isTopPriority
            )
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch { repo.updateTask(task) }
    }

    fun toggleTaskCompleted(task: TaskEntity) {
        viewModelScope.launch {
            val newState = !task.isCompleted
            repo.toggleTaskCompletion(task.id, newState)
            if (newState) {
                val report = repo.getDailyReportSnapshot(todayEpoch)
                if (report.completedTasks > 0 && report.completedTasks % 3 == 0) {
                    alfredEngine.announce(
                        "You have completed ${report.completedTasks} tasks today, Master. Excellent momentum.",
                        speakAloud = true,
                        logAction = "Milestone"
                    )
                }
            }
        }
    }

    fun toggleTopPriority(taskId: Long) {
        viewModelScope.launch { repo.toggleTopPriority(taskId) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { repo.deleteTask(taskId) }
    }

    fun rescheduleTaskToTomorrow(taskId: Long, timeSlot: String? = null) {
        viewModelScope.launch { repo.rescheduleTaskToTomorrow(taskId, timeSlot) }
    }

    fun createGoal(title: String, description: String, targetValue: Int, unit: String) {
        viewModelScope.launch {
            repo.createGoal(title, description, targetValue, unit, "Personal")
        }
    }

    fun updateGoalProgress(goalId: Long, newValue: Int) {
        viewModelScope.launch { repo.updateGoalProgress(goalId, newValue) }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch { repo.deleteGoal(goalId) }
    }

    fun createRoutine(title: String, type: RoutineType, targetMinutes: Int, scheduledTime: String) {
        viewModelScope.launch { repo.createRoutine(title, type, targetMinutes, scheduledTime) }
    }

    fun logRoutineMinutes(routineId: Long, addedMinutes: Int) {
        viewModelScope.launch { repo.logRoutineMinutes(routineId, addedMinutes) }
    }

    fun deleteRoutine(routineId: Long) {
        viewModelScope.launch { repo.deleteRoutine(routineId) }
    }

    // --- Alarm Actions ---
    fun upsertAlarm(
        id: Long = 0L,
        label: String,
        hour: Int,
        minute: Int,
        repeatDaysMask: Int,
        alarmType: AlarmType,
        snoozeMinutes: Int,
        alfredAnnouncement: Boolean
    ) {
        viewModelScope.launch {
            alarmScheduler.upsertAlarm(
                AlarmEntity(
                    id = id,
                    label = label.ifBlank { "VISION Alarm" },
                    hour = hour,
                    minute = minute,
                    repeatDaysMask = repeatDaysMask,
                    isEnabled = true,
                    alarmType = alarmType,
                    snoozeMinutes = snoozeMinutes,
                    alfredAnnouncementEnabled = alfredAnnouncement
                )
            )
        }
    }

    fun toggleAlarmEnabled(alarmId: Long, enabled: Boolean) {
        viewModelScope.launch { alarmScheduler.setAlarmEnabled(alarmId, enabled) }
    }

    fun deleteAlarm(alarmId: Long) {
        viewModelScope.launch { alarmScheduler.deleteAlarm(alarmId) }
    }

    fun canScheduleExactAlarms(): Boolean = alarmScheduler.canScheduleExactAlarms()

    fun setDailyScheduleNotifications(enabled: Boolean) {
        viewModelScope.launch { preferences.setDailyScheduleNotifications(enabled) }
    }

    // --- Distraction & Focus Actions ---
    fun refreshCuratedApps() {
        viewModelScope.launch {
            focusController.refreshInstalledApps()
        }
    }

    fun addCustomDistractingApp(appName: String, dailyLimitMinutes: Int = 30) {
        viewModelScope.launch {
            focusController.addCustomDistractingApp(appName, dailyLimitMinutes)
        }
    }

    fun toggleAppBlocked(packageName: String, selected: Boolean) {
        viewModelScope.launch { focusController.toggleAppBlocked(packageName, selected) }
    }

    fun updateAppLimit(packageName: String, minutes: Int) {
        viewModelScope.launch { focusController.updateAppDailyLimit(packageName, minutes) }
    }

    fun setStudyMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setStudyMode(enabled) }
    }

    fun setStudyGraceSeconds(seconds: Int) {
        viewModelScope.launch { preferences.setStudyGracePeriodSeconds(seconds) }
    }

    fun setAutoBlockEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoBlock(enabled) }
    }

    fun setBlockingDurationMinutes(minutes: Int) {
        viewModelScope.launch { preferences.setBlockingDurationMinutes(minutes) }
    }

    fun startFocusSession(durationMinutes: Int, studyMode: Boolean) {
        viewModelScope.launch {
            focusController.startFocusSession(durationMinutes, studyMode)
            val msg = if (studyMode) {
                "Your $durationMinutes-minute study session is now starting, Master. YouTube is permitted for educational material."
            } else {
                "Focus Mode started for $durationMinutes minutes, Master. Distracting apps will be restricted."
            }
            alfredEngine.announce(msg, speakAloud = true, logAction = "Focus Started")
        }
    }

    fun stopFocusSession() {
        viewModelScope.launch {
            focusController.stopFocusSession(completedNormally = false)
        }
    }

    fun triggerStudyWarningCheck(appName: String, isYouTube: Boolean) {
        viewModelScope.launch {
            focusController.triggerStudyWarningCheck(appName, isYouTube)
        }
    }

    // --- ALFRED Actions ---
    fun onVoiceListeningStarted() = alfredEngine.onVoiceListeningStarted()
    fun onVoiceTranscript(transcript: String) = alfredEngine.onVoiceTranscript(transcript)
    fun onVoiceCancelledOrError(message: String?) = alfredEngine.onVoiceCancelledOrError(message)
    fun stopVoiceOrSpeech() {
        alfredEngine.onVoiceCancelledOrError(null)
        alfredEngine.stopSpeaking()
    }
    fun sendTextCommandToAlfred(cmd: String) = alfredEngine.submitCommand(cmd)
    fun speakAlfredBriefing() = alfredEngine.speakDailyBriefing()
    fun setAlfredVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAlfredVoiceEnabled(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        alfredEngine.release()
    }
}
