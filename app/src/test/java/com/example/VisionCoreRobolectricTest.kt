package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alfred.AlfredOfflineCommandEngine
import com.example.core.services.AlarmSchedulerService
import com.example.core.services.FocusController
import com.example.core.services.VisionTaskScheduleRepository
import com.example.core.util.DateUtil
import com.example.data.local.AlarmEntity
import com.example.data.local.AlarmType
import com.example.data.local.RoutineType
import com.example.data.local.TaskCategory
import com.example.data.local.TaskPriority
import com.example.data.local.VisionDatabase
import com.example.data.preferences.VisionPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisionCoreRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: VisionDatabase
    private lateinit var repo: VisionTaskScheduleRepository
    private lateinit var alarmScheduler: AlarmSchedulerService
    private lateinit var focusController: FocusController
    private lateinit var preferences: VisionPreferences
    private lateinit var offlineAlfred: AlfredOfflineCommandEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = VisionPreferences(context)
        repo = VisionTaskScheduleRepository(db.taskDao(), db.goalRoutineDao(), db.focusDao())
        alarmScheduler = AlarmSchedulerService(context, db.alarmDao())
        focusController = FocusController(context, db.focusDao(), preferences)
        offlineAlfred = AlfredOfflineCommandEngine(
            taskManager = repo,
            scheduleManager = repo,
            alarmManager = alarmScheduler,
            focusManager = focusController,
            productivityAnalyzer = repo,
            preferences = preferences
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `task creation completion and daily report progress update accurately`() = runTest {
        val today = DateUtil.todayEpochDay()
        val id1 = repo.createTask("Morning Architecture Review", "", today, "09:00", TaskPriority.HIGH, TaskCategory.WORK, true)
        val id2 = repo.createTask("Evening Reading", "", today, "20:00", TaskPriority.LOW, TaskCategory.STUDY, false)

        var daily = repo.getDailyReportSnapshot(today)
        assertEquals(2, daily.totalTasks)
        assertEquals(0, daily.completedTasks)
        assertEquals(2, daily.pendingTasks)
        assertEquals(0, daily.dailyProgressPercent)

        repo.toggleTaskCompletion(id1, true)
        daily = repo.getDailyReportSnapshot(today)
        assertEquals(1, daily.completedTasks)
        assertEquals(1, daily.pendingTasks)
        assertEquals(50, daily.dailyProgressPercent)

        repo.rescheduleTaskToTomorrow(id2, "10:00")
        val tomorrowTasks = db.taskDao().getTasksForDateOnce(DateUtil.epochDayOffset(1))
        assertEquals(1, tomorrowTasks.size)
        assertEquals("Evening Reading", tomorrowTasks.first().title)
    }

    @Test
    fun `routine yoga and skill logging updates streak and weekly report`() = runTest {
        val rId = repo.createRoutine("Morning Yoga", RoutineType.YOGA, 20, "06:45")
        repo.logRoutineMinutes(rId, 20)
        val routines = repo.observeRoutines().first()
        assertEquals(1, routines.size)
        assertEquals(20, routines.first().completedMinutesToday)
        assertEquals(1, routines.first().streakDays)

        val weekly = repo.getWeeklyReportSnapshot()
        assertEquals(7, weekly.days.size)
    }

    @Test
    fun `alarm scheduling and repeat mask formatting work properly`() = runTest {
        val alarmId = alarmScheduler.upsertAlarm(
            AlarmEntity(
                label = "Wake Up",
                hour = 6,
                minute = 30,
                repeatDaysMask = 31,
                isEnabled = true,
                alarmType = AlarmType.WAKE_UP
            )
        )
        assertTrue(alarmId > 0L)
        assertEquals("Weekdays", AlarmSchedulerService.formatRepeatMask(31))
        val triggerMs = AlarmSchedulerService.computeNextTriggerMillis(6, 30, 31)
        assertTrue(triggerMs > System.currentTimeMillis())

        alarmScheduler.setAlarmEnabled(alarmId, false)
        val saved = db.alarmDao().getAlarmById(alarmId)
        assertNotNull(saved)
        assertEquals(false, saved?.isEnabled)
    }

    @Test
    fun `alfred offline engine executes cross-tab commands on real database`() = runTest {
        val addOutcome = offlineAlfred.processOfflineCommand("Add task Prepare quarterly slides at 11:00")
        assertEquals("dashboard", addOutcome.navigateToRoute)
        val tasks = repo.getTodayTasksSnapshot()
        assertTrue(tasks.any { it.title.contains("Prepare quarterly slides") })

        val alarmOutcome = offlineAlfred.processOfflineCommand("Set alarm for 7:15 AM")
        assertEquals("alarm", alarmOutcome.navigateToRoute)
        val alarms = db.alarmDao().getAllAlarmsOnce()
        assertTrue(alarms.any { it.hour == 7 && it.minute == 15 })

        val completeOutcome = offlineAlfred.processOfflineCommand("Complete Prepare quarterly slides")
        assertTrue(completeOutcome.replyText.contains("completed"))

        val reportOutcome = offlineAlfred.processOfflineCommand("Read daily report")
        assertEquals("report", reportOutcome.navigateToRoute)
        assertTrue(reportOutcome.replyText.contains("1 of 1"))
    }
}
