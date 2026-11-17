package com.example.alfred

import com.example.core.services.AlarmManagerContract
import com.example.core.services.FocusManagerContract
import com.example.core.services.ProductivityAnalyzer
import com.example.core.services.ScheduleManager
import com.example.core.services.TaskManager
import com.example.core.util.DateUtil
import com.example.data.local.AlarmEntity
import com.example.data.local.AlarmType
import com.example.data.local.TaskCategory
import com.example.data.local.TaskPriority
import com.example.data.preferences.VisionPreferences
import kotlinx.coroutines.flow.first
import java.util.Locale

data class AlfredActionOutcome(
    val replyText: String,
    val actionSummary: String,
    val navigateToRoute: String? = null
)

/**
 * Deterministic offline rule-based command processor for ALFRED.
 * Ensures core assistant actions work seamlessly even without internet access.
 */
class AlfredOfflineCommandEngine(
    private val taskManager: TaskManager,
    private val scheduleManager: ScheduleManager,
    private val alarmManager: AlarmManagerContract,
    private val focusManager: FocusManagerContract,
    private val productivityAnalyzer: ProductivityAnalyzer,
    private val preferences: VisionPreferences
) {
    suspend fun processOfflineCommand(rawInput: String): AlfredActionOutcome {
        val text = rawInput.trim()
        val lower = text.lowercase(Locale.getDefault())

        // 1. Navigation commands
        if (lower.startsWith("open ") || lower.startsWith("go to ") || lower.startsWith("show ")) {
            when {
                lower.contains("alarm") -> return AlfredActionOutcome(
                    "Opening the Alarm & Schedule tab, Master.",
                    "Navigated to Alarm",
                    "alarm"
                )
                lower.contains("distract") || lower.contains("focus tab") -> return AlfredActionOutcome(
                    "Opening Focus & Distraction Management, Master.",
                    "Navigated to Distraction",
                    "distraction"
                )
                lower.contains("report") || lower.contains("analytic") -> return AlfredActionOutcome(
                    "Opening your Productivity Reports, Master.",
                    "Navigated to Report",
                    "report"
                )
                lower.contains("dashboard") || lower.contains("home") -> return AlfredActionOutcome(
                    "Returning to your VISION Dashboard, Master.",
                    "Navigated to Dashboard",
                    "dashboard"
                )
            }
        }

        // 2. Focus / Study Mode commands
        if (lower.contains("stop focus") || lower.contains("end focus") || lower.contains("cancel focus")) {
            focusManager.stopFocusSession(completedNormally = false)
            return AlfredActionOutcome(
                "I have ended your active focus session, Master.",
                "Focus session ended",
                "distraction"
            )
        }

        if (lower.contains("start study") || lower.contains("enable study mode") || lower.contains("study session")) {
            val mins = extractMinutes(lower) ?: 45
            focusManager.startFocusSession(durationMinutes = mins, studyMode = true)
            return AlfredActionOutcome(
                "Your $mins-minute study session is now starting, Master. YouTube remains allowed for educational material with distraction warnings enabled.",
                "Started $mins min Study Mode",
                "distraction"
            )
        }

        if (lower.contains("start focus") || lower.contains("focus mode") || lower.contains("focus session")) {
            val mins = extractMinutes(lower) ?: 25
            focusManager.startFocusSession(durationMinutes = mins, studyMode = false)
            return AlfredActionOutcome(
                "Focus Mode engaged for $mins minutes, Master. Distracting applications are now monitored.",
                "Started $mins min Focus Mode",
                "distraction"
            )
        }

        if (lower.contains("enable blocking") || lower.contains("turn on blocking")) {
            preferences.setAutoBlock(true)
            return AlfredActionOutcome(
                "Automatic distraction blocking is now enabled, Master.",
                "Auto-blocking enabled",
                "distraction"
            )
        }

        if (lower.contains("disable blocking") || lower.contains("turn off blocking")) {
            preferences.setAutoBlock(false)
            return AlfredActionOutcome(
                "Automatic distraction blocking has been paused, Master.",
                "Auto-blocking disabled",
                "distraction"
            )
        }

        // 3. Alarm commands
        if (lower.contains("alarm") || lower.contains("wake me")) {
            val parsedTime = extractHourMinute(lower)
            if (parsedTime != null && (lower.contains("set") || lower.contains("add") || lower.contains("create") || lower.contains("wake"))) {
                val (hour, minute) = parsedTime
                val label = if (lower.contains("wake")) "Wake-Up Alarm" else "ALFRED Reminder Alarm"
                alarmManager.upsertAlarm(
                    AlarmEntity(
                        label = label,
                        hour = hour,
                        minute = minute,
                        repeatDaysMask = 0,
                        isEnabled = true,
                        alarmType = if (lower.contains("wake")) AlarmType.WAKE_UP else AlarmType.CUSTOM_REMINDER
                    )
                )
                val formatted = DateUtil.formatTime12Hour(hour, minute)
                return AlfredActionOutcome(
                    "I have scheduled your alarm for $formatted, Master.",
                    "Alarm set for $formatted",
                    "alarm"
                )
            }

            if (lower.contains("disable") || lower.contains("turn off")) {
                val alarms = alarmManager.observeAlarms().first()
                val enabled = alarms.filter { it.isEnabled }
                enabled.forEach { alarmManager.setAlarmEnabled(it.id, false) }
                return AlfredActionOutcome(
                    if (enabled.isEmpty()) "All alarms are already disabled, Master."
                    else "I have disabled ${enabled.size} active alarm(s), Master.",
                    "Alarms disabled",
                    "alarm"
                )
            }

            if (lower.contains("enable") || lower.contains("turn on")) {
                val alarms = alarmManager.observeAlarms().first()
                val first = alarms.firstOrNull()
                return if (first != null) {
                    alarmManager.setAlarmEnabled(first.id, true)
                    val formatted = DateUtil.formatTime12Hour(first.hour, first.minute)
                    AlfredActionOutcome(
                        "I have enabled your ${first.label} alarm for $formatted, Master.",
                        "Enabled alarm $formatted",
                        "alarm"
                    )
                } else {
                    AlfredActionOutcome(
                        "You have no saved alarms yet, Master. Say 'set alarm for 6:30 AM' to create one.",
                        "No alarms found",
                        "alarm"
                    )
                }
            }
        }

        // 4. Reports & Productivity summaries
        if (lower.contains("weekly report") || lower.contains("week report") || lower.contains("weekly productivity")) {
            val weekly = productivityAnalyzer.getWeeklyReportSnapshot()
            val deltaText = if (weekly.comparisonDeltaPercent >= 0) {
                "+${weekly.comparisonDeltaPercent}% compared to last week"
            } else {
                "${weekly.comparisonDeltaPercent}% compared to last week"
            }
            return AlfredActionOutcome(
                "Weekly Report: You completed ${weekly.totalCompleted} tasks with ${weekly.totalPending} pending, averaging ${weekly.averageProductivityPercent}% productivity ($deltaText) and ${weekly.totalFocusMinutes} focus minutes.",
                "Read Weekly Report",
                "report"
            )
        }

        if (lower.contains("daily report") || lower.contains("report") || lower.contains("productivity")) {
            val daily = productivityAnalyzer.getDailyReportSnapshot(DateUtil.todayEpochDay())
            return AlfredActionOutcome(
                "Your daily report is ready, Master. You have completed ${daily.completedTasks} of ${daily.totalTasks} tasks today, with ${daily.pendingTasks} pending and ${daily.productivityPercent}% overall productivity.",
                "Read Daily Report",
                "report"
            )
        }

        // 5. Task management commands
        if (lower.startsWith("add task ") || lower.startsWith("create task ") || lower.startsWith("new task ") || lower.contains("remind me to ")) {
            val cleanedTitle = text
                .replace(Regex("^(add task|create task|new task|remind me to)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+at\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { "Scheduled Task" }
            val timePair = extractHourMinute(lower)
            val timeSlot = if (timePair != null) {
                String.format(Locale.getDefault(), "%02d:%02d", timePair.first, timePair.second)
            } else {
                "10:00"
            }
            val priority = when {
                lower.contains("high") || lower.contains("urgent") -> TaskPriority.HIGH
                lower.contains("low") -> TaskPriority.LOW
                else -> TaskPriority.MEDIUM
            }
            val isTomorrow = lower.contains("tomorrow")
            val date = if (isTomorrow) DateUtil.epochDayOffset(1) else DateUtil.todayEpochDay()
            taskManager.createTask(
                title = cleanedTitle,
                scheduledDate = date,
                timeSlot = timeSlot,
                priority = priority,
                category = TaskCategory.WORK,
                isTopPriority = priority == TaskPriority.HIGH
            )
            val dayLabel = if (isTomorrow) "tomorrow" else "today"
            return AlfredActionOutcome(
                "I have added '$cleanedTitle' for $dayLabel at $timeSlot, Master.",
                "Created task '$cleanedTitle'",
                "dashboard"
            )
        }

        if (lower.contains("complete ") || lower.contains("finish ") || lower.contains("mark ") && lower.contains("done")) {
            val query = lower
                .replace("complete task", "")
                .replace("complete", "")
                .replace("finish", "")
                .replace("mark", "")
                .replace("as done", "")
                .replace("done", "")
                .trim()
            val pending = taskManager.observePendingTasks().first()
            val match = pending.firstOrNull { it.title.lowercase(Locale.getDefault()).contains(query) }
                ?: pending.firstOrNull()
            return if (match != null) {
                taskManager.toggleTaskCompletion(match.id, true)
                val todayTasks = scheduleManager.getTodayTasksSnapshot()
                val completedCount = todayTasks.count { it.isCompleted }
                AlfredActionOutcome(
                    "Well done, Master. I have marked '${match.title}' as completed. You have completed $completedCount tasks today.",
                    "Completed '${match.title}'",
                    "dashboard"
                )
            } else {
                AlfredActionOutcome(
                    "There are no pending tasks matching '$query', Master.",
                    "No matching pending task"
                )
            }
        }

        if (lower.contains("reschedule") || lower.contains("move") && lower.contains("tomorrow")) {
            val pending = taskManager.observePendingTasks().first()
            val query = lower.replace("reschedule", "").replace("to tomorrow", "").replace("tomorrow", "").trim()
            val match = pending.firstOrNull { query.isNotBlank() && it.title.lowercase(Locale.getDefault()).contains(query) }
                ?: pending.firstOrNull()
            return if (match != null) {
                taskManager.rescheduleTaskToTomorrow(match.id)
                AlfredActionOutcome(
                    "I have moved '${match.title}' to tomorrow's schedule, Master.",
                    "Rescheduled '${match.title}'",
                    "dashboard"
                )
            } else {
                AlfredActionOutcome("No pending task available to reschedule, Master.", "No task found")
            }
        }

        if (lower.contains("next task") || lower.contains("what should i do") || lower.contains("suggest")) {
            val next = scheduleManager.getNextSuggestedTask()
            return if (next != null) {
                AlfredActionOutcome(
                    "Master, I recommend focusing on '${next.title}' scheduled for ${next.timeSlot} (${next.priority.name} priority).",
                    "Suggested '${next.title}'",
                    "dashboard"
                )
            } else {
                AlfredActionOutcome(
                    "You have no pending tasks for today, Master. You may review your report or plan tomorrow's schedule.",
                    "Schedule clear"
                )
            }
        }

        if (lower.contains("morning") || lower.contains("hello") || lower.contains("brief") || lower.contains("status")) {
            val daily = productivityAnalyzer.getDailyReportSnapshot(DateUtil.todayEpochDay())
            val next = scheduleManager.getNextSuggestedTask()
            val nextHint = next?.let { " Your next priority is '${it.title}' at ${it.timeSlot}." } ?: ""
            return AlfredActionOutcome(
                "Hello Master. You have completed ${daily.completedTasks} of ${daily.totalTasks} tasks today (${daily.dailyProgressPercent}% progress).$nextHint",
                "Daily Briefing",
                "dashboard"
            )
        }

        return AlfredActionOutcome(
            "I am ready in offline mode, Master. Try: 'Add task Review notes at 3 PM', 'Set alarm for 6:30 AM', 'Start study mode for 45 minutes', 'What is my next task?', or 'Read daily report'.",
            "Offline command help"
        )
    }

    private fun extractMinutes(text: String): Int? {
        val match = Regex("(\\d+)\\s*(min|minute|minutes)").find(text)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractHourMinute(text: String): Pair<Int, Int>? {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val amPm = match.groupValues[3].lowercase(Locale.getDefault())
        if (amPm == "pm" && hour in 1..11) hour += 12
        if (amPm == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }
}
