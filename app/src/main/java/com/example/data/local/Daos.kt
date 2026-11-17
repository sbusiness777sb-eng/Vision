package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY scheduledDate ASC, isCompleted ASC, timeSlot ASC, id DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduledDate = :epochDay ORDER BY isCompleted ASC, isTopPriority DESC, timeSlot ASC")
    fun observeTasksByDate(epochDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY scheduledDate ASC, isTopPriority DESC, timeSlot ASC")
    fun observePendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE scheduledDate = :epochDay")
    suspend fun getTasksForDateOnce(epochDay: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Query("UPDATE tasks SET isCompleted = :completed, completedAtMillis = :completedAtMillis WHERE id = :taskId")
    suspend fun setTaskCompleted(taskId: Long, completed: Boolean, completedAtMillis: Long?)
}

@Dao
interface GoalRoutineDao {
    @Query("SELECT * FROM goals ORDER BY isCompleted ASC, updatedAtMillis DESC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals")
    suspend fun getGoalsOnce(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteGoalById(goalId: Long)

    @Query("SELECT * FROM routines ORDER BY scheduledTime ASC, id ASC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines")
    suspend fun getRoutinesOnce(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutineById(routineId: Long)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun observeAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC")
    suspend fun getEnabledAlarmsOnce(): List<AlarmEntity>

    @Query("SELECT * FROM alarms")
    suspend fun getAllAlarmsOnce(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :alarmId")
    suspend fun getAlarmById(alarmId: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :alarmId")
    suspend fun deleteAlarmById(alarmId: Long)

    @Query("UPDATE alarms SET isEnabled = :enabled WHERE id = :alarmId")
    suspend fun setAlarmEnabled(alarmId: Long, enabled: Boolean)
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM distracting_apps ORDER BY isSelectedForBlocking DESC, isYouTubeSpecial DESC, appName ASC")
    fun observeDistractingApps(): Flow<List<DistractingAppEntity>>

    @Query("SELECT * FROM distracting_apps WHERE isSelectedForBlocking = 1")
    suspend fun getSelectedDistractingAppsOnce(): List<DistractingAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDistractingApp(app: DistractingAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDistractingAppsIgnore(apps: List<DistractingAppEntity>)

    @Query("UPDATE distracting_apps SET isSelectedForBlocking = :selected WHERE packageName = :packageName")
    suspend fun setAppSelected(packageName: String, selected: Boolean)

    @Query("UPDATE distracting_apps SET dailyLimitMinutes = :minutes WHERE packageName = :packageName")
    suspend fun updateAppDailyLimit(packageName: String, minutes: Int)

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis DESC")
    fun observeFocusSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE status = 'RUNNING' ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getRunningSession(): FocusSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateFocusSession(session: FocusSessionEntity)

    @Query("SELECT * FROM daily_stats ORDER BY epochDay DESC LIMIT 14")
    fun observeDailyStats(): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stats WHERE epochDay = :epochDay")
    suspend fun getDailyStatOnce(epochDay: Long): DailyStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStat(stat: DailyStatEntity)
}

@Dao
interface AlfredDao {
    @Query("SELECT * FROM alfred_logs ORDER BY timestampMillis DESC LIMIT 50")
    fun observeLogs(): Flow<List<AlfredLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AlfredLogEntity): Long

    @Query("DELETE FROM alfred_logs")
    suspend fun clearLogs()
}
