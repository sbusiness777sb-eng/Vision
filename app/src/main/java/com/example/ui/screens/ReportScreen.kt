package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScheduleSend
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alfred.AlfredUiState
import com.example.core.services.DailyProductivityReport
import com.example.core.services.DayChartBar
import com.example.core.services.WeeklyProductivityReport
import com.example.core.util.DateUtil
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.ui.components.AlfredAssistantBar
import com.example.ui.components.VisionBrandHeader
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.VisionColors

private enum class ReportScope(val label: String) {
    DAILY("Daily Report"),
    WEEKLY("Weekly Report")
}

@Composable
fun ReportScreen(
    dailyReport: DailyProductivityReport,
    weeklyReport: WeeklyProductivityReport,
    pendingTasks: List<TaskEntity>,
    alfredState: AlfredUiState,
    onMicTap: () -> Unit,
    onStopVoiceOrSpeech: () -> Unit,
    onSpeakBriefing: () -> Unit,
    onOpenAlfredSheet: () -> Unit,
    onRescheduleToTomorrow: (Long) -> Unit,
    onCompleteTask: (TaskEntity) -> Unit,
    onCreateTomorrowTask: (String, String, String, TaskPriority, TaskCategory, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var reportScope by remember { mutableStateOf(ReportScope.DAILY) }
    var selectedBarIndex by remember { mutableIntStateOf(6) }
    var showTomorrowTaskDialog by remember { mutableStateOf(false) }

    val bars = weeklyReport.days
    val selectedBar = bars.getOrNull(selectedBarIndex) ?: bars.lastOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(VisionColors.ReportBackground)
            .testTag("report_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VisionBrandHeader(
                    titleColor = VisionColors.ReportOnSurface,
                    subtitle = "Productivity Analytics & Planning",
                    subtitleColor = VisionColors.ReportMuted
                )
                OutlinedButton(
                    onClick = { showTomorrowTaskDialog = true },
                    border = BorderStroke(1.dp, VisionColors.ReportPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plan tomorrow", tint = VisionColors.ReportPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tomorrow", color = VisionColors.ReportPrimary, fontSize = 12.sp)
                }
            }
        }

        item {
            AlfredAssistantBar(
                alfredState = alfredState,
                onMicTap = onMicTap,
                onStopVoiceOrSpeech = onStopVoiceOrSpeech,
                onSpeakBriefing = onSpeakBriefing,
                onExpandSheet = onOpenAlfredSheet
            )
        }

        // Daily / Weekly Segmented Switch
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReportScope.entries.forEach { scope ->
                    FilterChip(
                        selected = reportScope == scope,
                        onClick = { reportScope = scope },
                        label = { Text(scope.label, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VisionColors.ReportPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = VisionColors.ReportSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("report_scope_${scope.name.lowercase()}")
                    )
                }
            }
        }

        // KPI Summary Cards (Real Stored Data)
        item {
            if (reportScope == ReportScope.DAILY) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = VisionColors.ReportPrimary,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "DAILY PRODUCTIVITY",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.4.sp,
                                    color = Color(0xFFD7CCC8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${dailyReport.productivityPercent}%",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Progress: ${dailyReport.dailyProgressPercent}% • Routines: ${dailyReport.routineCompletionPercent}%",
                                    fontSize = 12.sp,
                                    color = Color(0xFFEFEBE9)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Focus: ${dailyReport.focusMinutes}m",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 13.sp,
                                    color = VisionColors.ReportAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${dailyReport.completedTasks}/${dailyReport.totalTasks} tasks",
                                    fontSize = 12.sp,
                                    color = Color(0xFFD7CCC8)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReportKpiTile("Total Tasks", "${dailyReport.totalTasks}", Modifier.weight(1f))
                        ReportKpiTile("Completed", "${dailyReport.completedTasks}", Modifier.weight(1f))
                        ReportKpiTile("Pending", "${dailyReport.pendingTasks}", Modifier.weight(1f))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = VisionColors.ReportPrimary,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "WEEKLY PRODUCTIVITY",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.4.sp,
                                    color = Color(0xFFD7CCC8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${weeklyReport.averageProductivityPercent}%",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                val deltaPrefix = if (weeklyReport.comparisonDeltaPercent >= 0) "+" else ""
                                Text(
                                    text = "$deltaPrefix${weeklyReport.comparisonDeltaPercent}% vs prior 7 days",
                                    fontSize = 12.sp,
                                    color = Color(0xFFEFEBE9)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${weeklyReport.totalFocusMinutes}m Focus",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 13.sp,
                                    color = VisionColors.ReportAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${weeklyReport.totalCompleted} done • ${weeklyReport.totalPending} pending",
                                    fontSize = 12.sp,
                                    color = Color(0xFFD7CCC8)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReportKpiTile("Done (7d)", "${weeklyReport.totalCompleted}", Modifier.weight(1f))
                        ReportKpiTile("Pending (7d)", "${weeklyReport.totalPending}", Modifier.weight(1f))
                        ReportKpiTile("Focus (7d)", "${weeklyReport.totalFocusMinutes}m", Modifier.weight(1f))
                    }
                }
            }
        }

        // Functional Column Chart (7-Day Completed vs Pending Performance)
        item {
            Surface(
                color = VisionColors.ReportSurfaceElevated,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, VisionColors.ReportOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_column_chart_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "7-Day Task Performance",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = VisionColors.ReportOnSurface
                            )
                            Text(
                                text = "Completed (Chocolate) vs Pending (Bronze)",
                                fontSize = 11.sp,
                                color = VisionColors.ReportMuted
                            )
                        }
                        if (selectedBar != null) {
                            Surface(
                                color = VisionColors.ReportSurface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${selectedBar.dayLabel}: ${selectedBar.completedTasks}✓ / ${selectedBar.pendingTasks}⏳",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = VisionColors.ReportPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (bars.all { it.completedTasks == 0 && it.pendingTasks == 0 }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No recorded task history yet. Create and complete tasks on Dashboard to populate live analytics.",
                                fontSize = 13.sp,
                                color = VisionColors.ReportMuted
                            )
                        }
                    } else {
                        ProductivityColumnChart(
                            bars = bars,
                            selectedIndex = selectedBarIndex,
                            onSelectBar = { selectedBarIndex = it }
                        )
                    }
                }
            }
        }

        // Daily Progress Breakdown Bars
        item {
            Surface(
                color = VisionColors.ReportSurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, VisionColors.ReportOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Daily Progress Breakdown",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = VisionColors.ReportOnSurface
                    )
                    BreakdownMeter("Task Completion", dailyReport.dailyProgressPercent)
                    BreakdownMeter("Yoga, Exercise & Skill Routines", dailyReport.routineCompletionPercent)
                    BreakdownMeter("Overall Productivity Score", dailyReport.productivityPercent)
                }
            }
        }

        // Next Day Planning
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Next Day Planning",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = VisionColors.ReportOnSurface
                    )
                    Text(
                        text = "Prepare tomorrow's schedule from pending tasks",
                        fontSize = 12.sp,
                        color = VisionColors.ReportMuted
                    )
                }
                Icon(Icons.Default.CalendarMonth, contentDescription = "Next day planning", tint = VisionColors.ReportPrimary)
            }
        }

        if (pendingTasks.isEmpty()) {
            item {
                Surface(
                    color = VisionColors.ReportSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, VisionColors.ReportOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No pending tasks left to reschedule. Tap '+ Tomorrow' to plan ahead.",
                        color = VisionColors.ReportMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(pendingTasks, key = { "nextday_${it.id}" }) { task ->
                Surface(
                    color = VisionColors.ReportSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, VisionColors.ReportOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                fontWeight = FontWeight.SemiBold,
                                color = VisionColors.ReportOnSurface
                            )
                            Text(
                                text = "${DateUtil.formatEpochDayLabel(task.scheduledDate)} • ${task.timeSlot} • ${task.priority.name}",
                                fontSize = 12.sp,
                                color = VisionColors.ReportMuted
                            )
                        }
                        IconButton(onClick = { onCompleteTask(task) }) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Complete task",
                                tint = VisionColors.ReportSecondary
                            )
                        }
                        Button(
                            onClick = { onRescheduleToTomorrow(task.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VisionColors.ReportPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("reschedule_tomorrow_${task.id}")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ScheduleSend,
                                contentDescription = "Schedule for tomorrow",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tomorrow", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    if (showTomorrowTaskDialog) {
        TaskEditorDialog(
            defaultForTomorrow = true,
            onDismiss = { showTomorrowTaskDialog = false },
            onSave = { title, desc, time, priority, category, isTop, forTomorrow ->
                onCreateTomorrowTask(title, desc, time, priority, category, isTop, forTomorrow)
                showTomorrowTaskDialog = false
            }
        )
    }
}

@Composable
private fun ReportKpiTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = VisionColors.ReportSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VisionColors.ReportOutline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = VisionColors.ReportPrimary
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = VisionColors.ReportMuted
            )
        }
    }
}

@Composable
private fun ProductivityColumnChart(
    bars: List<DayChartBar>,
    selectedIndex: Int,
    onSelectBar: (Int) -> Unit
) {
    val maxTotal = (bars.maxOfOrNull { it.completedTasks + it.pendingTasks } ?: 1).coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val w = size.width
            val h = size.height
            val slotCount = bars.size.coerceAtLeast(1)
            val slotWidth = w / slotCount
            val barWidth = (slotWidth * 0.42f).coerceAtMost(36f)

            // Horizontal baseline & midlines
            for (step in 0..2) {
                val y = h - (h * step / 2f)
                drawLine(
                    color = VisionColors.ReportOutline,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f
                )
            }

            bars.forEachIndexed { index, bar ->
                val centerX = slotWidth * index + slotWidth / 2f
                val left = centerX - barWidth / 2f
                val completedHeight = (bar.completedTasks.toFloat() / maxTotal) * (h * 0.88f)
                val pendingHeight = (bar.pendingTasks.toFloat() / maxTotal) * (h * 0.88f)

                if (index == selectedIndex) {
                    drawRoundRect(
                        color = VisionColors.ReportSurface,
                        topLeft = Offset(centerX - slotWidth * 0.38f, 0f),
                        size = Size(slotWidth * 0.76f, h),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                }

                // Pending top segment
                if (pendingHeight > 0f) {
                    drawRoundRect(
                        color = VisionColors.ReportAccent.copy(alpha = 0.7f),
                        topLeft = Offset(left, h - completedHeight - pendingHeight),
                        size = Size(barWidth, pendingHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }

                // Completed bottom segment
                if (completedHeight > 0f) {
                    drawRoundRect(
                        color = VisionColors.ReportPrimary,
                        topLeft = Offset(left, h - completedHeight),
                        size = Size(barWidth, completedHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            bars.forEachIndexed { idx, bar ->
                Text(
                    text = bar.dayLabel,
                    fontSize = 11.sp,
                    fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (idx == selectedIndex) VisionColors.ReportPrimary else VisionColors.ReportMuted,
                    modifier = Modifier
                        .clickable { onSelectBar(idx) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BreakdownMeter(label: String, percent: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, color = VisionColors.ReportOnSurface)
            Text(
                text = "$percent%",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = VisionColors.ReportPrimary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = VisionColors.ReportPrimary,
            trackColor = VisionColors.ReportSurface
        )
    }
}
