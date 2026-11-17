package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alfred.AlfredUiState
import com.example.core.services.DailyProductivityReport
import com.example.core.util.DateUtil
import com.example.data.local.GoalEntity
import com.example.data.local.RoutineEntity
import com.example.data.local.RoutineType
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.ui.components.AlfredAssistantBar
import com.example.ui.components.VisionBrandHeader
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.VisionColors

private enum class TaskFilterTab(val label: String) {
    TODAY("Today"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    UPCOMING("Upcoming")
}

@Composable
fun DashboardScreen(
    todayTasks: List<TaskEntity>,
    allTasks: List<TaskEntity>,
    goals: List<GoalEntity>,
    routines: List<RoutineEntity>,
    dailyReport: DailyProductivityReport,
    alfredState: AlfredUiState,
    onMicTap: () -> Unit,
    onStopVoiceOrSpeech: () -> Unit,
    onSpeakBriefing: () -> Unit,
    onOpenAlfredSheet: () -> Unit,
    onCreateTask: (String, String, String, TaskPriority, TaskCategory, Boolean, Boolean) -> Unit,
    onUpdateTask: (TaskEntity) -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onToggleTopPriority: (Long) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onCreateGoal: (String, String, Int, String) -> Unit,
    onIncrementGoal: (Long, Int) -> Unit,
    onDeleteGoal: (Long) -> Unit,
    onCreateRoutine: (String, RoutineType, Int, String) -> Unit,
    onLogRoutineMinutes: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(TaskFilterTab.TODAY) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showRoutineDialog by remember { mutableStateOf(false) }

    val todayEpoch = DateUtil.todayEpochDay()
    val animatedProgress by animateFloatAsState(
        targetValue = (dailyReport.dailyProgressPercent / 100f).coerceIn(0f, 1f),
        label = "dashboard_progress"
    )

    val topPriorities = remember(todayTasks) {
        todayTasks.filter { !it.isCompleted && (it.isTopPriority || it.priority == TaskPriority.HIGH) }
            .take(3)
    }

    val filteredTasks = remember(todayTasks, allTasks, selectedFilter) {
        when (selectedFilter) {
            TaskFilterTab.TODAY -> todayTasks
            TaskFilterTab.PENDING -> todayTasks.filter { !it.isCompleted }
            TaskFilterTab.COMPLETED -> todayTasks.filter { it.isCompleted }
            TaskFilterTab.UPCOMING -> allTasks.filter { it.scheduledDate > todayEpoch && !it.isCompleted }
        }
    }

    val yogaExerciseRoutines = remember(routines) {
        routines.filter { it.routineType == RoutineType.YOGA || it.routineType == RoutineType.EXERCISE || it.routineType == RoutineType.DAILY_HABIT }
    }
    val skillRoutines = remember(routines) {
        routines.filter { it.routineType == RoutineType.SKILL_DEV }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(VisionColors.DashboardBackground)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header: VISION Logo + Add Task
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VisionBrandHeader(
                    titleColor = VisionColors.DashboardOnSurface,
                    subtitle = DateUtil.formatTodayFullDate(),
                    subtitleColor = VisionColors.DashboardMuted
                )
                Button(
                    onClick = {
                        editingTask = null
                        showTaskDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VisionColors.DashboardPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("dashboard_add_task_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add task", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Task", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Central ALFRED AI Personal Assistant Bar
        item {
            AlfredAssistantBar(
                alfredState = alfredState,
                onMicTap = onMicTap,
                onStopVoiceOrSpeech = onStopVoiceOrSpeech,
                onSpeakBriefing = onSpeakBriefing,
                onExpandSheet = onOpenAlfredSheet
            )
        }

        // Daily Progress Hero Card
        item {
            Surface(
                color = VisionColors.DashboardPrimary,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_daily_progress_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DAILY PROGRESS",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            letterSpacing = 1.4.sp,
                            color = Color(0xFFDCE5C9)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${dailyReport.completedTasks} of ${dailyReport.totalTasks} tasks completed",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Pending: ${dailyReport.pendingTasks} • Focus: ${dailyReport.focusMinutes}m • Routines: ${dailyReport.routineCompletionPercent}%",
                            fontSize = 12.sp,
                            color = Color(0xFFE9E4D8)
                        )
                    }

                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(72.dp),
                            color = VisionColors.LogoOrange,
                            trackColor = Color.White.copy(alpha = 0.22f),
                            strokeWidth = 7.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = "${dailyReport.dailyProgressPercent}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Today's Priorities
        item {
            SectionHeaderRow(
                title = "Today's Priorities",
                subtitle = "Pinned & high-impact focus",
                textColor = VisionColors.DashboardOnSurface,
                mutedColor = VisionColors.DashboardMuted
            )
        }

        if (topPriorities.isEmpty()) {
            item {
                Surface(
                    color = VisionColors.DashboardSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, VisionColors.DashboardOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No high-priority tasks pinned for today. Star any task or create one to prioritize your day.",
                        color = VisionColors.DashboardMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(topPriorities, key = { "prio_${it.id}" }) { task ->
                Surface(
                    color = VisionColors.DashboardSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, VisionColors.DashboardPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onToggleTask(task) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RadioButtonUnchecked,
                                contentDescription = "Complete priority task",
                                tint = VisionColors.DashboardPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                fontWeight = FontWeight.SemiBold,
                                color = VisionColors.DashboardOnSurface,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${task.timeSlot} • ${task.category.name}",
                                fontSize = 12.sp,
                                color = VisionColors.DashboardMuted
                            )
                        }
                        Surface(
                            color = VisionColors.DashboardPrimary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = task.priority.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Today's Schedule / Upcoming / Completed / Pending Tasks
        item {
            Column {
                SectionHeaderRow(
                    title = "Today's Schedule & Tasks",
                    subtitle = "Tap checkbox to update progress",
                    textColor = VisionColors.DashboardOnSurface,
                    mutedColor = VisionColors.DashboardMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskFilterTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { selectedFilter = tab },
                            label = { Text(tab.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VisionColors.DashboardPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = VisionColors.DashboardSurface
                            ),
                            modifier = Modifier.testTag("filter_chip_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredTasks.isEmpty()) {
            item {
                Surface(
                    color = VisionColors.DashboardSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, VisionColors.DashboardOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No tasks in ${selectedFilter.label}.",
                            fontWeight = FontWeight.SemiBold,
                            color = VisionColors.DashboardOnSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Task' above or ask ALFRED to schedule your day.",
                            fontSize = 12.sp,
                            color = VisionColors.DashboardMuted
                        )
                    }
                }
            }
        } else {
            items(filteredTasks, key = { "task_${it.id}" }) { task ->
                TaskRowCard(
                    task = task,
                    onToggleComplete = { onToggleTask(task) },
                    onToggleStar = { onToggleTopPriority(task.id) },
                    onEdit = {
                        editingTask = task
                        showTaskDialog = true
                    },
                    onDelete = { onDeleteTask(task.id) }
                )
            }
        }

        // Morning Yoga & Exercise
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeaderRow(
                    title = "Morning Yoga & Exercise",
                    subtitle = "Daily vitality routines",
                    textColor = VisionColors.DashboardOnSurface,
                    mutedColor = VisionColors.DashboardMuted
                )
                TextButton(onClick = { showRoutineDialog = true }) {
                    Text("+ Routine", color = VisionColors.DashboardPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        items(yogaExerciseRoutines, key = { "routine_${it.id}" }) { routine ->
            RoutineTrackerCard(
                routine = routine,
                todayEpoch = todayEpoch,
                onAddMinutes = { mins -> onLogRoutineMinutes(routine.id, mins) }
            )
        }

        // Skill Development
        item {
            SectionHeaderRow(
                title = "Skill Development",
                subtitle = "Deliberate daily mastery",
                textColor = VisionColors.DashboardOnSurface,
                mutedColor = VisionColors.DashboardMuted
            )
        }

        items(skillRoutines, key = { "skill_${it.id}" }) { skill ->
            RoutineTrackerCard(
                routine = skill,
                todayEpoch = todayEpoch,
                onAddMinutes = { mins -> onLogRoutineMinutes(skill.id, mins) }
            )
        }

        // Personal Goals
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeaderRow(
                    title = "Personal Goals",
                    subtitle = "Long-term milestones",
                    textColor = VisionColors.DashboardOnSurface,
                    mutedColor = VisionColors.DashboardMuted
                )
                OutlinedButton(
                    onClick = { showGoalDialog = true },
                    border = BorderStroke(1.dp, VisionColors.DashboardPrimary),
                    modifier = Modifier.testTag("dashboard_add_goal_button")
                ) {
                    Text("+ Goal", color = VisionColors.DashboardPrimary)
                }
            }
        }

        if (goals.isEmpty()) {
            item {
                Surface(
                    color = VisionColors.DashboardSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, VisionColors.DashboardOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No personal goals created yet. Tap '+ Goal' to track measurable milestones.",
                        color = VisionColors.DashboardMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(goals, key = { "goal_${it.id}" }) { goal ->
                GoalProgressCard(
                    goal = goal,
                    onIncrement = { onIncrementGoal(goal.id, goal.currentValue + 1) },
                    onDelete = { onDeleteGoal(goal.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    if (showTaskDialog) {
        TaskEditorDialog(
            initialTask = editingTask,
            onDismiss = { showTaskDialog = false },
            onSave = { title, desc, time, priority, category, isTop, forTomorrow ->
                val existing = editingTask
                if (existing == null) {
                    onCreateTask(title, desc, time, priority, category, isTop, forTomorrow)
                } else {
                    onUpdateTask(
                        existing.copy(
                            title = title,
                            description = desc,
                            timeSlot = time,
                            priority = priority,
                            category = category,
                            isTopPriority = isTop
                        )
                    )
                }
                showTaskDialog = false
            }
        )
    }

    if (showGoalDialog) {
        GoalEditorDialog(
            onDismiss = { showGoalDialog = false },
            onSave = { title, desc, target, unit ->
                onCreateGoal(title, desc, target, unit)
                showGoalDialog = false
            }
        )
    }

    if (showRoutineDialog) {
        RoutineEditorDialog(
            onDismiss = { showRoutineDialog = false },
            onSave = { title, type, mins, time ->
                onCreateRoutine(title, type, mins, time)
                showRoutineDialog = false
            }
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    subtitle: String,
    textColor: Color,
    mutedColor: Color
) {
    Column {
        Text(
            text = title,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = textColor
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = mutedColor
        )
    }
}

@Composable
private fun TaskRowCard(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onToggleStar: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = VisionColors.DashboardSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VisionColors.DashboardOutline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("task_toggle_${task.id}")
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "Mark pending" else "Mark completed",
                    tint = if (task.isCompleted) VisionColors.DashboardPrimary else VisionColors.DashboardMuted
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (task.isCompleted) VisionColors.DashboardMuted else VisionColors.DashboardOnSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${DateUtil.formatEpochDayLabel(task.scheduledDate)} • ${task.timeSlot}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = VisionColors.DashboardMuted
                    )
                    Text(
                        text = task.category.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = VisionColors.DashboardPrimary
                    )
                }
            }

            IconButton(onClick = onToggleStar, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (task.isTopPriority) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Pin priority",
                    tint = if (task.isTopPriority) VisionColors.LogoOrange else VisionColors.DashboardMuted
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit task",
                    tint = VisionColors.DashboardMuted
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete task",
                    tint = VisionColors.DashboardMuted
                )
            }
        }
    }
}

@Composable
private fun RoutineTrackerCard(
    routine: RoutineEntity,
    todayEpoch: Long,
    onAddMinutes: (Int) -> Unit
) {
    val minsToday = if (routine.lastCompletedEpochDay == todayEpoch) routine.completedMinutesToday else 0
    val progress = if (routine.targetMinutes <= 0) 0f else (minsToday.toFloat() / routine.targetMinutes).coerceIn(0f, 1f)
    val icon = when (routine.routineType) {
        RoutineType.YOGA -> Icons.Default.SelfImprovement
        RoutineType.EXERCISE -> Icons.Default.FitnessCenter
        RoutineType.SKILL_DEV -> Icons.Default.School
        RoutineType.DAILY_HABIT -> Icons.Default.CheckCircle
    }

    Surface(
        color = VisionColors.DashboardSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VisionColors.DashboardOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(VisionColors.DashboardSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = routine.title, tint = VisionColors.DashboardPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.title,
                        fontWeight = FontWeight.SemiBold,
                        color = VisionColors.DashboardOnSurface
                    )
                    Text(
                        text = "${routine.scheduledTime} • $minsToday / ${routine.targetMinutes} min • Streak: ${routine.streakDays}d",
                        fontSize = 12.sp,
                        color = VisionColors.DashboardMuted
                    )
                }
                Surface(
                    color = VisionColors.DashboardPrimary,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clickable { onAddMinutes(10) }
                        .testTag("routine_log_10m_${routine.id}")
                ) {
                    Text(
                        text = "+10m",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = VisionColors.DashboardPrimary,
                trackColor = VisionColors.DashboardSurface
            )
        }
    }
}

@Composable
private fun GoalProgressCard(
    goal: GoalEntity,
    onIncrement: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = VisionColors.DashboardSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VisionColors.DashboardOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Flag, contentDescription = "Goal", tint = VisionColors.LogoOrange)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        fontWeight = FontWeight.SemiBold,
                        color = VisionColors.DashboardOnSurface
                    )
                    Text(
                        text = "${goal.currentValue} / ${goal.targetValue} ${goal.unit} (${goal.progressPercent}%)",
                        fontSize = 12.sp,
                        color = VisionColors.DashboardMuted
                    )
                }
                OutlinedButton(
                    onClick = onIncrement,
                    border = BorderStroke(1.dp, VisionColors.DashboardPrimary),
                    modifier = Modifier.testTag("goal_increment_${goal.id}")
                ) {
                    Text("+1", color = VisionColors.DashboardPrimary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete goal",
                        tint = VisionColors.DashboardMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { goal.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = VisionColors.LogoOrange,
                trackColor = VisionColors.DashboardSurface
            )
        }
    }
}
