package com.example.core.services

import com.example.core.util.DateUtil
import com.example.data.local.DailyStatEntity
import com.example.data.local.FocusDao
import com.example.data.local.GoalEntity
import com.example.data.local.GoalRoutineDao
import com.example.data.local.RoutineEntity
import com.example.data.local.RoutineType
import com.example.data.local.TaskCategory
import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class VisionTaskScheduleRepository(
    private val taskDao: TaskDao,
    private val goalRoutineDao: GoalRoutineDao,
    private val focusDao: FocusDao
) : TaskManager, ScheduleManager, ProductivityAnalyzer {

    override fun observeAllTasks(): Flow<List<TaskEntity>> = taskDao.observeAllTasks()

    override fun observeTasksForDate(epochDay: Long): Flow<List<TaskEntity>> =
        taskDao.observeTasksByDate(epochDay)

    override fun observePendingTasks(): Flow<List<TaskEntity>> = taskDao.observePendingTasks()

    override suspend fun createTask(
        title: String,
        description: String,
        scheduledDate: Long,
        timeSlot: String,
        priority: TaskPriority,
        category: TaskCategory,
        isTopPriority: Boolean
    ): Long {
        return taskDao.insertTask(
            TaskEntity(
                title = title.trim(),
                description = description.trim(),
                scheduledDate = scheduledDate,
                timeSlot = timeSlot,
                priority = priority,
                category = category,
                isTopPriority = isTopPriority
            )
        )
    }

    override suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
    }

    override suspend fun toggleTaskCompletion(taskId: Long, completed: Boolean) {
        val completedAt = if (completed) System.currentTimeMillis() else null
        taskDao.setTaskCompleted(taskId, completed, completedAt)
    }

    override suspend fun toggleTopPriority(taskId: Long) {
        val all = taskDao.getAllTasksOnce()
        val target = all.firstOrNull { it.id == taskId } ?: return
        taskDao.updateTask(target.copy(isTopPriority = !target.isTopPriority))
    }

    override suspend fun deleteTask(taskId: Long) {
        taskDao.deleteTaskById(taskId)
    }

    override suspend fun rescheduleTaskToTomorrow(taskId: Long, newTimeSlot: String?) {
        val all = taskDao.getAllTasksOnce()
        val target = all.firstOrNull { it.id == taskId } ?: return
        taskDao.updateTask(
            target.copy(
                scheduledDate = DateUtil.epochDayOffset(1),
                timeSlot = newTimeSlot ?: target.timeSlot,
                isCompleted = false,
                completedAtMillis = null
            )
        )
    }

    override fun observeGoals(): Flow<List<GoalEntity>> = goalRoutineDao.observeGoals()

    override fun observeRoutines(): Flow<List<RoutineEntity>> = goalRoutineDao.observeRoutines()

    override suspend fun getNextSuggestedTask(): TaskEntity? {
        val today = DateUtil.todayEpochDay()
        val tasks = taskDao.getTasksForDateOnce(today)
            .filter { !it.isCompleted }
            .sortedWith(
                compareByDescending<TaskEntity> { it.isTopPriority }
                    .thenBy {
                        when (it.priority) {
                            TaskPriority.HIGH -> 0
                            TaskPriority.MEDIUM -> 1
                            TaskPriority.LOW -> 2
                        }
                    }
                    .thenBy { it.timeSlot }
            )
        return tasks.firstOrNull()
    }

    override suspend fun getTodayTasksSnapshot(): List<TaskEntity> {
        return taskDao.getTasksForDateOnce(DateUtil.todayEpochDay())
    }

    override suspend fun createGoal(
        title: String,
        targetDescription: String,
        targetValue: Int,
        unit: String,
        category: String
    ): Long {
        return goalRoutineDao.insertGoal(
            GoalEntity(
                title = title.trim(),
                targetDescription = targetDescription.trim(),
                currentValue = 0,
                targetValue = targetValue.coerceAtLeast(1),
                unit = unit.ifBlank { "%" },
                category = category.ifBlank { "Personal" }
            )
        )
    }

    override suspend fun updateGoalProgress(goalId: Long, currentValue: Int) {
        val goal = goalRoutineDao.getGoalsOnce().firstOrNull { it.id == goalId } ?: return
        val clamped = currentValue.coerceIn(0, goal.targetValue)
        goalRoutineDao.updateGoal(
            goal.copy(
                currentValue = clamped,
                isCompleted = clamped >= goal.targetValue,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteGoal(goalId: Long) {
        goalRoutineDao.deleteGoalById(goalId)
    }

    override suspend fun createRoutine(
        title: String,
        routineType: RoutineType,
        targetMinutes: Int,
        scheduledTime: String
    ): Long {
        return goalRoutineDao.insertRoutine(
            RoutineEntity(
                title = title.trim(),
                routineType = routineType,
                targetMinutes = targetMinutes.coerceAtLeast(5),
                scheduledTime = scheduledTime
            )
        )
    }

    override suspend fun logRoutineMinutes(routineId: Long, addedMinutes: Int) {
        val routine = goalRoutineDao.getRoutinesOnce().firstOrNull { it.id == routineId } ?: return
        val today = DateUtil.todayEpochDay()
        val isNewDay = routine.lastCompletedEpochDay != today
        val baseToday = if (isNewDay) 0 else routine.completedMinutesToday
        val updatedToday = (baseToday + addedMinutes).coerceAtLeast(0)
        val newStreak = if (updatedToday >= routine.targetMinutes && routine.lastCompletedEpochDay != today) {
            if (routine.lastCompletedEpochDay == today - 1) routine.streakDays + 1 else 1
        } else {
            routine.streakDays
        }
        goalRoutineDao.updateRoutine(
            routine.copy(
                completedMinutesToday = updatedToday,
                streakDays = newStreak,
                lastCompletedEpochDay = if (updatedToday > 0) today else routine.lastCompletedEpochDay
            )
        )
    }

    override suspend fun deleteRoutine(routineId: Long) {
        goalRoutineDao.deleteRoutineById(routineId)
    }

    override fun observeDailyReport(epochDay: Long): Flow<DailyProductivityReport> {
        return combine(
            taskDao.observeTasksByDate(epochDay),
            goalRoutineDao.observeRoutines(),
            focusDao.observeDailyStats()
        ) { tasks, routines, stats ->
            computeDailyReport(epochDay, tasks, routines, stats)
        }
    }

    override fun observeWeeklyReport(): Flow<WeeklyProductivityReport> {
        return combine(
            taskDao.observeAllTasks(),
            goalRoutineDao.observeRoutines(),
            focusDao.observeDailyStats()
        ) { allTasks, routines, stats ->
            computeWeeklyReport(allTasks, routines, stats)
        }
    }

    override suspend fun getDailyReportSnapshot(epochDay: Long): DailyProductivityReport {
        val tasks = taskDao.getTasksForDateOnce(epochDay)
        val routines = goalRoutineDao.getRoutinesOnce()
        val stats = focusDao.observeDailyStats().first()
        return computeDailyReport(epochDay, tasks, routines, stats)
    }

    override suspend fun getWeeklyReportSnapshot(): WeeklyProductivityReport {
        val allTasks = taskDao.getAllTasksOnce()
        val routines = goalRoutineDao.getRoutinesOnce()
        val stats = focusDao.observeDailyStats().first()
        return computeWeeklyReport(allTasks, routines, stats)
    }

    private fun computeDailyReport(
        epochDay: Long,
        tasks: List<TaskEntity>,
        routines: List<RoutineEntity>,
        stats: List<DailyStatEntity>
    ): DailyProductivityReport {
        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val pending = (total - completed).coerceAtLeast(0)
        val taskProgress = if (total == 0) 0 else ((completed * 100f) / total).toInt()

        val routineCompletion = if (routines.isEmpty()) {
            0
        } else {
            val sumPct = routines.sumOf { r ->
                val mins = if (r.lastCompletedEpochDay == epochDay) r.completedMinutesToday else 0
                if (r.targetMinutes <= 0) 0 else ((mins * 100) / r.targetMinutes).coerceIn(0, 100)
            }
            sumPct / routines.size
        }

        val dayStat = stats.firstOrNull { it.epochDay == epochDay }
        val focusMinutes = dayStat?.focusMinutes ?: 0
        val distractingMinutes = dayStat?.distractingMinutes ?: 0

        // Productivity percentage derives strictly from real tasks, routines, and focus ratio
        val productivity = when {
            total == 0 && routines.all { it.completedMinutesToday == 0 } && focusMinutes == 0 -> 0
            total > 0 && routines.isNotEmpty() -> {
                val focusBonus = if (focusMinutes >= 25) 10 else (focusMinutes * 10 / 25)
                val penalty = (distractingMinutes / 15).coerceAtMost(15)
                ((taskProgress * 0.7f) + (routineCompletion * 0.25f) + focusBonus - penalty)
                    .toInt()
                    .coerceIn(0, 100)
            }
            total > 0 -> taskProgress
            else -> routineCompletion
        }

        return DailyProductivityReport(
            epochDay = epochDay,
            totalTasks = total,
            completedTasks = completed,
            pendingTasks = pending,
            dailyProgressPercent = taskProgress,
            productivityPercent = productivity,
            focusMinutes = focusMinutes,
            distractingMinutes = distractingMinutes,
            routineCompletionPercent = routineCompletion
        )
    }

    private fun computeWeeklyReport(
        allTasks: List<TaskEntity>,
        routines: List<RoutineEntity>,
        stats: List<DailyStatEntity>
    ): WeeklyProductivityReport {
        val today = DateUtil.todayEpochDay()
        val weekDays = (6 downTo 0).map { offset -> today - offset }
        val bars = weekDays.map { day ->
            val dayTasks = allTasks.filter { it.scheduledDate == day }
            val completed = dayTasks.count { it.isCompleted }
            val pending = dayTasks.count { !it.isCompleted }
            val pct = if (dayTasks.isEmpty()) 0 else (completed * 100) / dayTasks.size
            DayChartBar(
                epochDay = day,
                dayLabel = DateUtil.formatShortWeekday(day),
                completedTasks = completed,
                pendingTasks = pending,
                productivityPercent = pct
            )
        }

        val totalCompleted = bars.sumOf { it.completedTasks }
        val totalPending = bars.sumOf { it.pendingTasks }
        val activeBars = bars.filter { it.completedTasks + it.pendingTasks > 0 }
        val avgProductivity = if (activeBars.isEmpty()) {
            0
        } else {
            activeBars.sumOf { it.productivityPercent } / activeBars.size
        }

        val previousWeekDays = (13 downTo 7).map { offset -> today - offset }
        val prevTasks = allTasks.filter { it.scheduledDate in previousWeekDays }
        val prevAvg = if (prevTasks.isEmpty()) {
            0
        } else {
            (prevTasks.count { it.isCompleted } * 100) / prevTasks.size
        }

        val focusMinutes = stats
            .filter { it.epochDay in weekDays }
            .sumOf { it.focusMinutes }

        // Also incorporate routine completion signal when no tasks exist yet
        val routineSignal = if (totalCompleted + totalPending == 0 && routines.any { it.completedMinutesToday > 0 }) {
            routines.sumOf { if (it.targetMinutes > 0) ((it.completedMinutesToday * 100) / it.targetMinutes).coerceIn(0, 100) else 0 } / routines.size
        } else {
            avgProductivity
        }

        return WeeklyProductivityReport(
            days = bars,
            totalCompleted = totalCompleted,
            totalPending = totalPending,
            averageProductivityPercent = routineSignal,
            totalFocusMinutes = focusMinutes,
            comparisonDeltaPercent = routineSignal - prevAvg
        )
    }
}
