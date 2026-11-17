package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.alfred.AlfredUiState
import com.example.core.services.AlarmSchedulerService
import com.example.core.util.DateUtil
import com.example.data.local.AlarmEntity
import com.example.data.local.AlarmType
import com.example.data.local.TaskEntity
import com.example.data.preferences.VisionSettings
import com.example.services.AlarmReceiver
import com.example.ui.components.AlfredAssistantBar
import com.example.ui.components.VisionBrandHeader
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.VisionColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlarmScreen(
    alarms: List<AlarmEntity>,
    todayTasks: List<TaskEntity>,
    settings: VisionSettings,
    canScheduleExactAlarms: Boolean,
    alfredState: AlfredUiState,
    onMicTap: () -> Unit,
    onStopVoiceOrSpeech: () -> Unit,
    onSpeakBriefing: () -> Unit,
    onOpenAlfredSheet: () -> Unit,
    onSaveAlarm: (Long, String, Int, Int, Int, AlarmType, Int, Boolean) -> Unit,
    onToggleAlarm: (Long, Boolean) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onToggleScheduleNotifications: (Boolean) -> Unit,
    onToggleAlfredVoice: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val liveClockText = remember(currentMillis) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(currentMillis))
    }
    val nextEnabledAlarm = remember(alarms) { alarms.firstOrNull { it.isEnabled } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(VisionColors.AlarmBackground)
            .testTag("alarm_screen"),
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
                    titleColor = VisionColors.AlarmOnSurface,
                    subtitle = "Alarms & Daily Reminders",
                    subtitleColor = VisionColors.AlarmMuted
                )
                Button(
                    onClick = {
                        editingAlarm = null
                        showAlarmDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VisionColors.AlarmPrimary,
                        contentColor = Color(0xFF141815)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("alarm_add_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add alarm", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alarm", fontWeight = FontWeight.Bold)
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

        // Live Smoked Marble Clock Hero
        item {
            Surface(
                color = VisionColors.AlarmSurface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, VisionColors.AlarmOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CURRENT TIME",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                        color = VisionColors.AlarmMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = liveClockText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = VisionColors.AlarmPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (nextEnabledAlarm != null) {
                            "Next Alarm: ${DateUtil.formatTime12Hour(nextEnabledAlarm.hour, nextEnabledAlarm.minute)} • ${nextEnabledAlarm.label}"
                        } else {
                            "No active alarms armed"
                        },
                        fontSize = 13.sp,
                        color = VisionColors.AlarmSecondary
                    )
                }
            }
        }

        // Permission & Reliability Controls (Notifications, Exact Alarms, Battery)
        item {
            Surface(
                color = VisionColors.AlarmSurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, VisionColors.AlarmOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Background & Lock-Screen Reliability",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = VisionColors.AlarmOnSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OutlinedButton(
                                onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Enable Notifications", color = VisionColors.AlarmPrimary, fontSize = 12.sp)
                            }
                        }
                        if (!canScheduleExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    runCatching { context.startActivity(intent) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Allow Exact Alarms", color = VisionColors.AlarmPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exact Alarms: ${if (canScheduleExactAlarms) "Active" else "Permission Recommended"}",
                                fontSize = 12.sp,
                                color = VisionColors.AlarmMuted
                            )
                            Text(
                                text = "Tap Battery Settings if your device restricts background wake-ups.",
                                fontSize = 11.sp,
                                color = VisionColors.AlarmMuted
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        ) {
                            Text("Battery", color = VisionColors.AlarmSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Notification Management
        item {
            Surface(
                color = VisionColors.AlarmSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, VisionColors.AlarmOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = "Notification management",
                            tint = VisionColors.AlarmPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Management",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = VisionColors.AlarmOnSurface
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily Schedule Notifications", color = VisionColors.AlarmOnSurface, fontSize = 14.sp)
                            Text(
                                "${todayTasks.count { !it.isCompleted }} pending tasks monitored today",
                                color = VisionColors.AlarmMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.dailyScheduleNotificationsEnabled,
                            onCheckedChange = onToggleScheduleNotifications,
                            colors = SwitchDefaults.colors(checkedTrackColor = VisionColors.AlarmPrimary)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ALFRED Spoken Announcements", color = VisionColors.AlarmOnSurface, fontSize = 14.sp)
                            Text("Speak wake-up & task alerts aloud", color = VisionColors.AlarmMuted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = settings.alfredVoiceEnabled,
                            onCheckedChange = onToggleAlfredVoice,
                            colors = SwitchDefaults.colors(checkedTrackColor = VisionColors.AlarmPrimary)
                        )
                    }
                }
            }
        }

        // Multiple Alarms List
        item {
            Text(
                text = "Scheduled Alarms & Custom Reminders",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = VisionColors.AlarmOnSurface
            )
        }

        if (alarms.isEmpty()) {
            item {
                Surface(
                    color = VisionColors.AlarmSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No alarms scheduled. Tap '+ Alarm' or ask ALFRED to set a wake-up alarm.",
                        color = VisionColors.AlarmMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(alarms, key = { it.id }) { alarm ->
                AlarmItemCard(
                    alarm = alarm,
                    onToggle = { enabled -> onToggleAlarm(alarm.id, enabled) },
                    onEdit = {
                        editingAlarm = alarm
                        showAlarmDialog = true
                    },
                    onDelete = { onDeleteAlarm(alarm.id) },
                    onTestRing = {
                        val trigger = Intent(context, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_TRIGGER
                            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
                            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
                            putExtra(
                                AlarmReceiver.EXTRA_ALARM_TIME,
                                DateUtil.formatTime12Hour(alarm.hour, alarm.minute)
                            )
                        }
                        context.sendBroadcast(trigger)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    if (showAlarmDialog) {
        AlarmEditorDialog(
            initialAlarm = editingAlarm,
            onDismiss = { showAlarmDialog = false },
            onSave = { label, hour, minute, mask, type, snooze, alfredAnnounce ->
                onSaveAlarm(
                    editingAlarm?.id ?: 0L,
                    label,
                    hour,
                    minute,
                    mask,
                    type,
                    snooze,
                    alfredAnnounce
                )
                showAlarmDialog = false
            }
        )
    }
}

@Composable
private fun AlarmItemCard(
    alarm: AlarmEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestRing: () -> Unit
) {
    Surface(
        color = VisionColors.AlarmSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (alarm.isEnabled) VisionColors.AlarmPrimary.copy(alpha = 0.65f) else VisionColors.AlarmOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = "Alarm",
                            tint = if (alarm.isEnabled) VisionColors.AlarmPrimary else VisionColors.AlarmMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Surface(
                            color = VisionColors.AlarmSurface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = alarm.alarmType.name.replace("_", " "),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = VisionColors.AlarmSecondary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = DateUtil.formatTime12Hour(alarm.hour, alarm.minute),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) VisionColors.AlarmOnSurface else VisionColors.AlarmMuted
                    )
                    Text(
                        text = "${alarm.label} • ${AlarmSchedulerService.formatRepeatMask(alarm.repeatDaysMask)}",
                        fontSize = 13.sp,
                        color = VisionColors.AlarmMuted
                    )
                }
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = VisionColors.AlarmPrimary),
                    modifier = Modifier.testTag("alarm_switch_${alarm.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTestRing,
                    border = BorderStroke(1.dp, VisionColors.AlarmOutline),
                    modifier = Modifier.testTag("alarm_test_ring_${alarm.id}")
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Test alarm ring",
                        tint = VisionColors.AlarmPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Ring", color = VisionColors.AlarmPrimary, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit alarm", tint = VisionColors.AlarmMuted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete alarm", tint = VisionColors.AlarmMuted)
                }
            }
        }
    }
}
