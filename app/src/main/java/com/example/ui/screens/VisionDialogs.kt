package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AlarmEntity
import com.example.data.local.AlarmType
import com.example.data.local.RoutineType
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VisionColors
import java.util.Locale

@Composable
fun TaskEditorDialog(
    initialTask: TaskEntity? = null,
    defaultForTomorrow: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        timeSlot: String,
        priority: TaskPriority,
        category: TaskCategory,
        isTopPriority: Boolean,
        forTomorrow: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var timeSlot by remember { mutableStateOf(initialTask?.timeSlot ?: "09:30") }
    var priority by remember { mutableStateOf(initialTask?.priority ?: TaskPriority.MEDIUM) }
    var category by remember { mutableStateOf(initialTask?.category ?: TaskCategory.WORK) }
    var isTopPriority by remember { mutableStateOf(initialTask?.isTopPriority ?: false) }
    var forTomorrow by remember { mutableStateOf(defaultForTomorrow) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialTask == null) "New Task" else "Edit Task",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeSlot,
                    onValueChange = { timeSlot = it },
                    label = { Text("Time (HH:mm, e.g. 14:30)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_time_input")
                )
                Text("Priority", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name, fontSize = 11.sp) }
                        )
                    }
                }
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(TaskCategory.WORK, TaskCategory.STUDY, TaskCategory.HEALTH, TaskCategory.SKILL).forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.name, fontSize = 10.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pin to Today's Priorities", fontSize = 13.sp)
                    Switch(checked = isTopPriority, onCheckedChange = { isTopPriority = it })
                }
                if (initialTask == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Schedule for Tomorrow", fontSize = 13.sp)
                        Switch(checked = forTomorrow, onCheckedChange = { forTomorrow = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            title.trim(),
                            description.trim(),
                            timeSlot.ifBlank { "09:00" },
                            priority,
                            category,
                            isTopPriority,
                            forTomorrow
                        )
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VisionColors.DashboardPrimary),
                modifier = Modifier.testTag("save_task_button")
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GoalEditorDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, targetValue: Int, unit: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("10") }
    var unit by remember { mutableStateOf("sessions") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Personal Goal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_title_input")
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Milestone note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Target") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = targetText.toIntOrNull()?.coerceAtLeast(1) ?: 10
                    if (title.isNotBlank()) onSave(title.trim(), desc.trim(), target, unit.ifBlank { "steps" })
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VisionColors.DashboardPrimary),
                modifier = Modifier.testTag("save_goal_button")
            ) {
                Text("Create Goal", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RoutineEditorDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, type: RoutineType, targetMinutes: Int, scheduledTime: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RoutineType.YOGA) }
    var targetMins by remember { mutableStateOf("20") }
    var scheduledTime by remember { mutableStateOf("07:00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Daily Routine", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RoutineType.YOGA to "Yoga",
                        RoutineType.EXERCISE to "Exercise",
                        RoutineType.SKILL_DEV to "Skill"
                    ).forEach { (rType, label) ->
                        FilterChip(
                            selected = type == rType,
                            onClick = { type = rType },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetMins,
                        onValueChange = { targetMins = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Minutes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = scheduledTime,
                        onValueChange = { scheduledTime = it },
                        label = { Text("Time HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = targetMins.toIntOrNull()?.coerceAtLeast(5) ?: 20
                    if (title.isNotBlank()) onSave(title.trim(), type, mins, scheduledTime.ifBlank { "07:00" })
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VisionColors.DashboardPrimary)
            ) {
                Text("Add Routine", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AlarmEditorDialog(
    initialAlarm: AlarmEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        label: String,
        hour: Int,
        minute: Int,
        repeatDaysMask: Int,
        alarmType: AlarmType,
        snoozeMinutes: Int,
        alfredAnnouncement: Boolean
    ) -> Unit
) {
    var label by remember { mutableStateOf(initialAlarm?.label ?: "Wake-Up Alarm") }
    var hour by remember { mutableIntStateOf(initialAlarm?.hour ?: 6) }
    var minute by remember { mutableIntStateOf(initialAlarm?.minute ?: 30) }
    var repeatMask by remember { mutableIntStateOf(initialAlarm?.repeatDaysMask ?: 31) }
    var alarmType by remember { mutableStateOf(initialAlarm?.alarmType ?: AlarmType.WAKE_UP) }
    var snoozeMinutes by remember { mutableIntStateOf(initialAlarm?.snoozeMinutes ?: 5) }
    var alfredAnnounce by remember { mutableStateOf(initialAlarm?.alfredAnnouncementEnabled ?: true) }

    val dayBits = listOf(1 to "M", 2 to "T", 4 to "W", 8 to "T", 16 to "F", 32 to "S", 64 to "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VisionColors.AlarmSurfaceElevated,
        titleContentColor = VisionColors.AlarmOnSurface,
        textContentColor = VisionColors.AlarmOnSurface,
        title = {
            Text(
                text = if (initialAlarm == null) "Create Alarm" else "Edit Alarm",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Time stepper
                Surface(
                    color = VisionColors.AlarmSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedButton(onClick = { hour = (hour + 1) % 24 }) { Text("+") }
                            Text(
                                text = String.format(Locale.getDefault(), "%02d", hour),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = VisionColors.AlarmPrimary
                            )
                            OutlinedButton(onClick = { hour = (hour + 23) % 24 }) { Text("-") }
                        }
                        Text(
                            text = " : ",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = VisionColors.AlarmOnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedButton(onClick = { minute = (minute + 5) % 60 }) { Text("+5") }
                            Text(
                                text = String.format(Locale.getDefault(), "%02d", minute),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = VisionColors.AlarmPrimary
                            )
                            OutlinedButton(onClick = { minute = (minute + 55) % 60 }) { Text("-5") }
                        }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alarm_label_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        AlarmType.WAKE_UP to "Wake-Up",
                        AlarmType.CUSTOM_REMINDER to "Reminder",
                        AlarmType.DAILY_SCHEDULE to "Schedule"
                    ).forEach { (type, text) ->
                        FilterChip(
                            selected = alarmType == type,
                            onClick = { alarmType = type },
                            label = { Text(text, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VisionColors.AlarmPrimary,
                                selectedLabelColor = Color(0xFF141815)
                            )
                        )
                    }
                }

                Text("Repeat Days", fontSize = 12.sp, color = VisionColors.AlarmMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dayBits.forEach { (bit, letter) ->
                        val active = (repeatMask and bit) != 0
                        Surface(
                            color = if (active) VisionColors.AlarmPrimary else VisionColors.AlarmSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, VisionColors.AlarmOutline),
                            modifier = Modifier
                                .clickable {
                                    repeatMask = if (active) repeatMask and bit.inv() else repeatMask or bit
                                }
                        ) {
                            Text(
                                text = letter,
                                color = if (active) Color(0xFF141815) else VisionColors.AlarmOnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Snooze: ${snoozeMinutes}m", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15).forEach { s ->
                            FilterChip(
                                selected = snoozeMinutes == s,
                                onClick = { snoozeMinutes = s },
                                label = { Text("${s}m", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = alfredAnnounce,
                        onCheckedChange = { alfredAnnounce = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ALFRED spoken wake-up greeting", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(label.ifBlank { "VISION Alarm" }, hour, minute, repeatMask, alarmType, snoozeMinutes, alfredAnnounce)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VisionColors.AlarmPrimary,
                    contentColor = Color(0xFF141815)
                ),
                modifier = Modifier.testTag("save_alarm_button")
            ) {
                Text("Save Alarm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VisionColors.AlarmMuted)
            }
        }
    )
}
