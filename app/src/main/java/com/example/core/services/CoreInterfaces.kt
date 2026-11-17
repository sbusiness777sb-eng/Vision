package com.example.core.services

import com.example.data.local.AlarmEntity
import com.example.data.local.AlarmType
import com.example.data.local.DailyStatEntity
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.GoalEntity
import com.example.data.local.RoutineEntity
import com.example.data.local.RoutineType
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import kotlinx.coroutines.flow.Flow

data class DailyProductivityReport(
    val epochDay: Long,
    val totalTasks: Int,
    val completedTasks: Int,
    val pendingTasks: Int,
    val dailyProgressPercent: Int,
    val productivityPercent: Int,
    val focusMinutes: Int,
    val distractingMinutes: Int,
    val routineCompletionPercent: Int
)

data class DayChartBar(
    val epochDay: Long,
    val dayLabel: String,
    val completedTasks: Int,
    val pendingTasks: Int,
    val productivityPercent: Int
)

data class WeeklyProductivityReport(
    val days: List<DayChartBar>,
    val totalCompleted: Int,
    val totalPending: Int,
    val averageProductivityPercent: Int,
    val totalFocusMinutes: Int,
    val comparisonDeltaPercent: Int
)

interface TaskManager {
    fun observeAllTasks(): Flow<List<TaskEntity>>
    fun observeTasksForDate(epochDay: Long): Flow<List<TaskEntity>>
    fun observePendingTasks(): Flow<List<TaskEntity>>
    suspend fun createTask(
        title: String,
        description: String = "",
        scheduledDate: Long,
        timeSlot: String = "09:00",
        priority: TaskPriority = TaskPriority.MEDIUM,
        category: TaskCategory = TaskCategory.WORK,
        isTopPriority: Boolean = false
    ): Long
    suspend fun updateTask(task: TaskEntity)
    suspend fun toggleTaskCompletion(taskId: Long, completed: Boolean)
    suspend fun toggleTopPriority(taskId: Long)
    suspend fun deleteTask(taskId: Long)
    suspend fun rescheduleTaskToTomorrow(taskId: Long, newTimeSlot: String? = null)
}

interface ScheduleManager {
    fun observeGoals(): Flow<List<GoalEntity>>
    fun observeRoutines(): Flow<List<RoutineEntity>>
    suspend fun getNextSuggestedTask(): TaskEntity?
    suspend fun getTodayTasksSnapshot(): List<TaskEntity>
    suspend fun createGoal(
        title: String,
        targetDescription: String,
        targetValue: Int,
        unit: String,
        category: String
    ): Long
    suspend fun updateGoalProgress(goalId: Long, currentValue: Int)
    suspend fun deleteGoal(goalId: Long)
    suspend fun createRoutine(
        title: String,
        routineType: RoutineType,
        targetMinutes: Int,
        scheduledTime: String
    ): Long
    suspend fun logRoutineMinutes(routineId: Long, addedMinutes: Int)
    suspend fun deleteRoutine(routineId: Long)
}

interface AlarmManagerContract {
    fun observeAlarms(): Flow<List<AlarmEntity>>
    suspend fun upsertAlarm(alarm: AlarmEntity): Long
    suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean)
    suspend fun deleteAlarm(alarmId: Long)
    suspend fun rescheduleAllEnabledAlarms()
    fun canScheduleExactAlarms(): Boolean
}

interface FocusManagerContract {
    fun observeDistractingApps(): Flow<List<DistractingAppEntity>>
    fun observeFocusSessions(): Flow<List<FocusSessionEntity>>
    fun observeDailyStats(): Flow<List<DailyStatEntity>>
    suspend fun toggleAppBlocked(packageName: String, selected: Boolean)
    suspend fun updateAppDailyLimit(packageName: String, minutes: Int)
    suspend fun refreshInstalledApps()
    suspend fun startFocusSession(durationMinutes: Int, studyMode: Boolean)
    suspend fun stopFocusSession(completedNormally: Boolean)
    fun hasUsageAccessPermission(): Boolean
    fun isAccessibilityGuardEnabled(): Boolean
    fun queryTodayAppUsageMinutes(): Map<String, Int>
}

interface ProductivityAnalyzer {
    fun observeDailyReport(epochDay: Long): Flow<DailyProductivityReport>
    fun observeWeeklyReport(): Flow<WeeklyProductivityReport>
    suspend fun getDailyReportSnapshot(epochDay: Long): DailyProductivityReport
    suspend fun getWeeklyReportSnapshot(): WeeklyProductivityReport
}
