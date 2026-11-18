package com.example.alfred

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.core.services.AlarmManagerContract
import com.example.core.services.AlfredEventBus
import com.example.core.services.FocusManagerContract
import com.example.core.services.ProductivityAnalyzer
import com.example.core.services.ScheduleManager
import com.example.core.services.TaskManager
import com.example.core.util.DateUtil
import com.example.data.local.AlarmEntity
import com.example.data.local.AlarmType
import com.example.data.local.AlfredDao
import com.example.data.local.AlfredLogEntity
import com.example.data.local.TaskCategory
import com.example.data.local.TaskPriority
import com.example.data.preferences.VisionPreferences
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

enum class AlfredAssistantStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

data class AlfredUiState(
    val status: AlfredAssistantStatus = AlfredAssistantStatus.IDLE,
    val statusHeadline: String = "Standing by to manage your day, Master.",
    val partialTranscript: String = "",
    val isOnline: Boolean = false,
    val isVoiceInputAvailable: Boolean = true,
    val errorMessage: String? = null
)

class AlfredAssistantEngine(
    private val context: Context,
    private val taskManager: TaskManager,
    private val scheduleManager: ScheduleManager,
    private val alarmManager: AlarmManagerContract,
    private val focusManager: FocusManagerContract,
    private val productivityAnalyzer: ProductivityAnalyzer,
    private val alfredDao: AlfredDao,
    private val preferences: VisionPreferences,
    private val onNavigateTab: (String) -> Unit
) {
    companion object {
        private const val TAG = "AlfredAI"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(AlfredUiState(isOnline = checkNetworkOnline()))
    val uiState: StateFlow<AlfredUiState> = _uiState.asStateFlow()

    val logsFlow: Flow<List<AlfredLogEntity>> = alfredDao.observeLogs()

    private val offlineEngine = AlfredOfflineCommandEngine(
        taskManager = taskManager,
        scheduleManager = scheduleManager,
        alarmManager = alarmManager,
        focusManager = focusManager,
        productivityAnalyzer = productivityAnalyzer,
        preferences = preferences
    )

    private val ttsEngine = AlfredTtsEngine(context) { isSpeaking ->
        _uiState.value = _uiState.value.copy(
            status = if (isSpeaking) AlfredAssistantStatus.SPEAKING
            else if (_uiState.value.status == AlfredAssistantStatus.SPEAKING) AlfredAssistantStatus.IDLE
            else _uiState.value.status
        )
    }

    private val actionSchema = Schema.obj(
        properties = mapOf(
            "replyText" to Schema.string(description = "Polite, concise ALFRED butler response addressing the user as Master."),
            "actionType" to Schema.enumeration(
                values = listOf(
                    "NONE",
                    "CREATE_TASK",
                    "COMPLETE_TASK",
                    "RESCHEDULE_TASK",
                    "CREATE_ALARM",
                    "TOGGLE_ALARM",
                    "START_FOCUS",
                    "STOP_FOCUS",
                    "READ_DAILY_REPORT",
                    "READ_WEEKLY_REPORT",
                    "NAVIGATE_TAB"
                ),
                description = "Cross-tab action to execute on VISION data."
            ),
            "taskTitle" to Schema.string(description = "Task title for create/complete/reschedule", nullable = true),
            "taskTime" to Schema.string(description = "24-hour HH:mm time slot", nullable = true),
            "taskPriority" to Schema.string(description = "HIGH, MEDIUM, or LOW", nullable = true),
            "forTomorrow" to Schema.boolean(description = "True if task should be scheduled for tomorrow", nullable = true),
            "alarmHour" to Schema.integer(description = "0-23 alarm hour", nullable = true),
            "alarmMinute" to Schema.integer(description = "0-59 alarm minute", nullable = true),
            "alarmLabel" to Schema.string(description = "Label for alarm", nullable = true),
            "enableAlarm" to Schema.boolean(description = "Enable or disable alarm", nullable = true),
            "focusMinutes" to Schema.integer(description = "Duration in minutes for focus session", nullable = true),
            "studyMode" to Schema.boolean(description = "True for Study Mode (YouTube allowed)", nullable = true),
            "targetTab" to Schema.string(description = "dashboard, alarm, distraction, or report", nullable = true)
        ),
        optionalProperties = listOf(
            "taskTitle", "taskTime", "taskPriority", "forTomorrow",
            "alarmHour", "alarmMinute", "alarmLabel", "enableAlarm",
            "focusMinutes", "studyMode", "targetTab"
        )
    )

    // Lazy initialization per Firebase AI Logic checklist
    private val geminiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = actionSchema
            }
        )
    }

    init {
        _uiState.value = _uiState.value.copy(
            isOnline = checkNetworkOnline(),
            isVoiceInputAvailable = AlfredSpeechIntentHelper.isAvailable(context)
        )
        scope.launch {
            AlfredEventBus.announcements.collect { announcement ->
                announce(announcement, speakAloud = true, logAction = "System Announcement")
            }
        }
    }

    fun onVoiceListeningStarted() {
        _uiState.value = _uiState.value.copy(
            status = AlfredAssistantStatus.LISTENING,
            partialTranscript = "",
            errorMessage = null,
            isOnline = checkNetworkOnline(),
            statusHeadline = "Listening via system voice input..."
        )
    }

    fun onVoiceTranscript(transcript: String) {
        _uiState.value = _uiState.value.copy(partialTranscript = "")
        if (transcript.isNotBlank()) {
            submitCommand(transcript)
        } else {
            _uiState.value = _uiState.value.copy(status = AlfredAssistantStatus.IDLE)
        }
    }

    fun onVoiceCancelledOrError(message: String?) {
        if (message.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                status = AlfredAssistantStatus.IDLE,
                statusHeadline = "Standing by to manage your day, Master."
            )
        } else {
            _uiState.value = _uiState.value.copy(
                status = AlfredAssistantStatus.ERROR,
                errorMessage = message,
                statusHeadline = message
            )
        }
    }

    fun stopSpeaking() {
        ttsEngine.stop()
    }

    fun submitCommand(command: String) {
        val clean = command.trim()
        if (clean.isBlank()) return

        scope.launch {
            val online = checkNetworkOnline()
            _uiState.value = _uiState.value.copy(
                status = AlfredAssistantStatus.THINKING,
                isOnline = online,
                errorMessage = null,
                statusHeadline = "Processing request..."
            )
            alfredDao.insertLog(
                AlfredLogEntity(
                    speaker = "USER",
                    message = clean,
                    actionSummary = if (online) "Online" else "Offline"
                )
            )

            if (!online) {
                val outcome = offlineEngine.processOfflineCommand(clean)
                finishResponse(outcome, modeLabel = "Offline")
                return@launch
            }

            try {
                val contextSnapshot = buildContextSnapshot()
                val prompt = """
                    You are ALFRED, the refined, concise, loyal personal assistant inside the VISION Android productivity app.
                    Address the user respectfully as "Master". Base all statements strictly on the real saved data below—never invent tasks or numbers.
                    
                    LIVE VISION DATA:
                    $contextSnapshot
                    
                    USER COMMAND:
                    "$clean"
                """.trimIndent()

                val response = withTimeout(45_000L) {
                    geminiModel.generateContent(prompt)
                }
                val jsonText = response.text ?: throw IllegalStateException("Empty AI response.")
                val outcome = executeStructuredAiAction(jsonText)
                finishResponse(outcome, modeLabel = "Gemini AI")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini timed out, trying offline command fallback", e)
                val fallback = offlineEngine.processOfflineCommand(clean)
                _uiState.value = _uiState.value.copy(
                    status = AlfredAssistantStatus.ERROR,
                    errorMessage = "Cloud AI timed out; executed offline fallback.",
                    statusHeadline = fallback.replyText
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI call failed", e)
                // Surface authentic error transparently while checking if offline deterministic parser matches
                val offlineOutcome = offlineEngine.processOfflineCommand(clean)
                val errText = e.localizedMessage ?: "Cloud AI unavailable."
                val combinedReply = if (offlineOutcome.actionSummary != "Offline command help") {
                    "${offlineOutcome.replyText} (Handled via offline engine; cloud AI notice: $errText)"
                } else {
                    "Cloud AI error: $errText"
                }
                _uiState.value = _uiState.value.copy(
                    status = AlfredAssistantStatus.ERROR,
                    errorMessage = errText,
                    statusHeadline = combinedReply
                )
                alfredDao.insertLog(
                    AlfredLogEntity(
                        speaker = "ALFRED",
                        message = combinedReply,
                        actionSummary = offlineOutcome.actionSummary
                    )
                )
                offlineOutcome.navigateToRoute?.let(onNavigateTab)
            }
        }
    }

    private suspend fun finishResponse(outcome: AlfredActionOutcome, modeLabel: String) {
        alfredDao.insertLog(
            AlfredLogEntity(
                speaker = "ALFRED",
                message = outcome.replyText,
                actionSummary = "${outcome.actionSummary} • $modeLabel"
            )
        )
        _uiState.value = _uiState.value.copy(
            status = AlfredAssistantStatus.IDLE,
            statusHeadline = outcome.replyText,
            errorMessage = null
        )
        outcome.navigateToRoute?.let(onNavigateTab)
        val settings = preferences.getSettingsOnce()
        if (settings.alfredVoiceEnabled) {
            ttsEngine.speak(outcome.replyText)
        }
    }

    private suspend fun executeStructuredAiAction(jsonString: String): AlfredActionOutcome {
        val json = JSONObject(jsonString)
        val replyText = json.optString("replyText", "Certainly, Master.")
        val actionType = json.optString("actionType", "NONE")
        val targetTab = json.optString("targetTab", "").takeIf { it.isNotBlank() }
        var actionSummary = "Assistant Reply"

        when (actionType) {
            "CREATE_TASK" -> {
                val title = json.optString("taskTitle", "").ifBlank { "New Task" }
                val time = json.optString("taskTime", "10:00").ifBlank { "10:00" }
                val priority = runCatching {
                    TaskPriority.valueOf(json.optString("taskPriority", "MEDIUM").uppercase(Locale.getDefault()))
                }.getOrDefault(TaskPriority.MEDIUM)
                val date = if (json.optBoolean("forTomorrow", false)) {
                    DateUtil.epochDayOffset(1)
                } else {
                    DateUtil.todayEpochDay()
                }
                taskManager.createTask(
                    title = title,
                    scheduledDate = date,
                    timeSlot = time,
                    priority = priority,
                    category = TaskCategory.WORK,
                    isTopPriority = priority == TaskPriority.HIGH
                )
                actionSummary = "Created '$title'"
            }
            "COMPLETE_TASK" -> {
                val query = json.optString("taskTitle", "").lowercase(Locale.getDefault())
                val pending = taskManager.observePendingTasks().first()
                val target = pending.firstOrNull { query.isNotBlank() && it.title.lowercase(Locale.getDefault()).contains(query) }
                    ?: pending.firstOrNull()
                if (target != null) {
                    taskManager.toggleTaskCompletion(target.id, true)
                    actionSummary = "Completed '${target.title}'"
                }
            }
            "RESCHEDULE_TASK" -> {
                val query = json.optString("taskTitle", "").lowercase(Locale.getDefault())
                val pending = taskManager.observePendingTasks().first()
                val target = pending.firstOrNull { query.isNotBlank() && it.title.lowercase(Locale.getDefault()).contains(query) }
                    ?: pending.firstOrNull()
                if (target != null) {
                    val newTime = json.optString("taskTime", "").takeIf { it.isNotBlank() }
                    taskManager.rescheduleTaskToTomorrow(target.id, newTime)
                    actionSummary = "Rescheduled '${target.title}'"
                }
            }
            "CREATE_ALARM" -> {
                val hour = json.optInt("alarmHour", 7).coerceIn(0, 23)
                val minute = json.optInt("alarmMinute", 0).coerceIn(0, 59)
                val label = json.optString("alarmLabel", "ALFRED Alarm").ifBlank { "ALFRED Alarm" }
                alarmManager.upsertAlarm(
                    AlarmEntity(
                        label = label,
                        hour = hour,
                        minute = minute,
                        isEnabled = true,
                        alarmType = if (label.contains("wake", ignoreCase = true)) AlarmType.WAKE_UP else AlarmType.CUSTOM_REMINDER
                    )
                )
                actionSummary = "Alarm ${DateUtil.formatTime12Hour(hour, minute)}"
            }
            "TOGGLE_ALARM" -> {
                val enable = json.optBoolean("enableAlarm", true)
                val alarms = alarmManager.observeAlarms().first()
                alarms.firstOrNull()?.let {
                    alarmManager.setAlarmEnabled(it.id, enable)
                    actionSummary = if (enable) "Alarm enabled" else "Alarm disabled"
                }
            }
            "START_FOCUS" -> {
                val mins = json.optInt("focusMinutes", 25).coerceIn(1, 240)
                val study = json.optBoolean("studyMode", false)
                focusManager.startFocusSession(mins, study)
                actionSummary = if (study) "Study Mode ${mins}m" else "Focus ${mins}m"
            }
            "STOP_FOCUS" -> {
                focusManager.stopFocusSession(completedNormally = false)
                actionSummary = "Stopped Focus"
            }
            "READ_DAILY_REPORT" -> actionSummary = "Daily Report"
            "READ_WEEKLY_REPORT" -> actionSummary = "Weekly Report"
            "NAVIGATE_TAB" -> actionSummary = "Navigate $targetTab"
        }

        return AlfredActionOutcome(
            replyText = replyText,
            actionSummary = actionSummary,
            navigateToRoute = targetTab
        )
    }

    fun speakDailyBriefing() {
        scope.launch {
            val today = DateUtil.todayEpochDay()
            val daily = productivityAnalyzer.getDailyReportSnapshot(today)
            val next = scheduleManager.getNextSuggestedTask()
            val message = buildString {
                append("Hello Master. ")
                if (daily.totalTasks == 0) {
                    append("You have no tasks scheduled yet today. Would you like me to add your priorities?")
                } else {
                    append("You have completed ${daily.completedTasks} of ${daily.totalTasks} tasks today (${daily.dailyProgressPercent}% progress). ")
                    if (next != null) {
                        append("Your next recommended task is '${next.title}' at ${next.timeSlot}.")
                    } else {
                        append("All scheduled tasks for today are complete. Your daily report is ready.")
                    }
                }
            }
            announce(message, speakAloud = true, logAction = "Daily Briefing")
        }
    }

    fun announce(message: String, speakAloud: Boolean = true, logAction: String = "Announcement") {
        scope.launch {
            _uiState.value = _uiState.value.copy(statusHeadline = message)
            alfredDao.insertLog(
                AlfredLogEntity(
                    speaker = "ALFRED",
                    message = message,
                    actionSummary = logAction,
                    isAnnouncement = true
                )
            )
            if (speakAloud) {
                val settings = preferences.getSettingsOnce()
                if (settings.alfredVoiceEnabled) {
                    ttsEngine.speak(message)
                }
            }
        }
    }

    private suspend fun buildContextSnapshot(): String {
        val today = DateUtil.todayEpochDay()
        val tasks = scheduleManager.getTodayTasksSnapshot()
        val goals = scheduleManager.observeGoals().first()
        val routines = scheduleManager.observeRoutines().first()
        val alarms = alarmManager.observeAlarms().first()
        val daily = productivityAnalyzer.getDailyReportSnapshot(today)
        val weekly = productivityAnalyzer.getWeeklyReportSnapshot()
        val focusRuntime = AlfredEventBus.focusRuntime.value

        return buildString {
            appendLine("Date: ${DateUtil.formatTodayFullDate()}")
            appendLine("Daily Progress: ${daily.completedTasks}/${daily.totalTasks} tasks (${daily.dailyProgressPercent}%), Productivity: ${daily.productivityPercent}%, Focus Minutes Today: ${daily.focusMinutes}")
            appendLine("Weekly Summary: ${weekly.totalCompleted} completed, ${weekly.totalPending} pending, Avg Productivity: ${weekly.averageProductivityPercent}%")
            appendLine("Focus Active: ${focusRuntime.isRunning}, StudyMode: ${focusRuntime.isStudyMode}, RemainingSec: ${focusRuntime.remainingSeconds}")
            appendLine("Today Tasks: ${tasks.joinToString("; ") { "${it.title} [${it.timeSlot}, ${it.priority.name}, done=${it.isCompleted}]" }.ifBlank { "None" }}")
            appendLine("Routines: ${routines.joinToString("; ") { "${it.title} (${it.completedMinutesToday}/${it.targetMinutes}m, streak=${it.streakDays})" }}")
            appendLine("Goals: ${goals.joinToString("; ") { "${it.title} (${it.progressPercent}%)" }.ifBlank { "None" }}")
            appendLine("Alarms: ${alarms.joinToString("; ") { "${it.label} at ${DateUtil.formatTime12Hour(it.hour, it.minute)} (enabled=${it.isEnabled})" }.ifBlank { "None" }}")
        }
    }

    fun checkNetworkOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        ttsEngine.release()
    }
}
