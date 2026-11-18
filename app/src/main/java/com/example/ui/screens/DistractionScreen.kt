package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.core.services.FocusRuntimeSnapshot
import com.example.core.util.DateUtil
import com.example.data.local.DailyStatEntity
import com.example.data.local.DistractingAppEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.preferences.VisionSettings
import com.example.ui.components.AlfredAssistantBar
import com.example.ui.components.VisionBrandHeader
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.VisionColors

@Composable
fun DistractionScreen(
    settings: VisionSettings,
    focusRuntime: FocusRuntimeSnapshot,
    distractingApps: List<DistractingAppEntity>,
    focusSessions: List<FocusSessionEntity>,
    dailyStats: List<DailyStatEntity>,
    alfredState: AlfredUiState,
    onMicTap: () -> Unit,
    onStopVoiceOrSpeech: () -> Unit,
    onSpeakBriefing: () -> Unit,
    onOpenAlfredSheet: () -> Unit,
    onRefreshApps: () -> Unit,
    onAddCustomApp: (String, Int) -> Unit,
    onToggleAppSelected: (String, Boolean) -> Unit,
    onSetStudyMode: (Boolean) -> Unit,
    onSetGraceSeconds: (Int) -> Unit,
    onSetAutoBlock: (Boolean) -> Unit,
    onSetBlockingDuration: (Int) -> Unit,
    onStartFocus: (Int, Boolean) -> Unit,
    onStopFocus: () -> Unit,
    onTriggerStudyWarning: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedDuration by remember { mutableIntStateOf(25) }
    var showAddAppDialog by remember { mutableStateOf(false) }

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
        onRefreshApps()
    }

    val todayStat = remember(dailyStats) {
        val today = DateUtil.todayEpochDay()
        dailyStats.firstOrNull { it.epochDay == today }
    }
    val totalFocusMins = todayStat?.focusMinutes ?: 0
    val warningsToday = todayStat?.studyWarningsCount ?: 0
    val blocksToday = todayStat?.blocksCount ?: 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(VisionColors.DistractionBackground)
            .testTag("distraction_screen"),
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
                    titleColor = VisionColors.DistractionOnSurface,
                    subtitle = "Focus & Distraction Control",
                    subtitleColor = VisionColors.DistractionMuted
                )
                IconButton(
                    onClick = onRefreshApps,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh focus list",
                        tint = VisionColors.DistractionPrimary
                    )
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

        // Focus Mode & Focus Session Timer
        item {
            Surface(
                color = VisionColors.DistractionPrimary,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("focus_timer_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (focusRuntime.isRunning) {
                            if (focusRuntime.isStudyMode) "STUDY MODE ACTIVE" else "FOCUS MODE ACTIVE"
                        } else {
                            "FOCUS SESSION TIMER"
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        color = Color(0xFFF3D5DC)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (focusRuntime.isRunning) {
                            DateUtil.formatDurationSeconds(focusRuntime.remainingSeconds)
                        } else {
                            DateUtil.formatDurationSeconds(selectedDuration * 60)
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (focusRuntime.graceRemainingSeconds > 0 && focusRuntime.activeWarningAppName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = VisionColors.DistractionWarning,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Warning: ${focusRuntime.activeWarningAppName} • Grace ${focusRuntime.graceRemainingSeconds}s",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (!focusRuntime.isRunning) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25, 45, 60, 90).forEach { mins ->
                                FilterChip(
                                    selected = selectedDuration == mins,
                                    onClick = { selectedDuration = mins },
                                    label = { Text("${mins}m", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.White,
                                        selectedLabelColor = VisionColors.DistractionPrimary,
                                        containerColor = VisionColors.DistractionSecondary,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onStartFocus(selectedDuration, settings.studyModeEnabled) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = VisionColors.DistractionPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_focus_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start focus")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (settings.studyModeEnabled) "Start Study Session (${selectedDuration}m)" else "Start Focus Session (${selectedDuration}m)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = onStopFocus,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = VisionColors.DistractionPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("stop_focus_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "End focus session")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("End Focus Session", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Study Mode Card (Explicit YouTube Educational Allowance + Warning & Grace Period)
        item {
            Surface(
                color = VisionColors.DistractionSurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, VisionColors.DistractionPrimary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("study_mode_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = "Study mode",
                                tint = VisionColors.DistractionPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Study Mode",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = VisionColors.DistractionOnSurface
                                )
                                Text(
                                    text = "Educational YouTube allowance with distraction warnings",
                                    fontSize = 12.sp,
                                    color = VisionColors.DistractionMuted
                                )
                            }
                        }
                        Switch(
                            checked = settings.studyModeEnabled,
                            onCheckedChange = onSetStudyMode,
                            colors = SwitchDefaults.colors(checkedTrackColor = VisionColors.DistractionPrimary),
                            modifier = Modifier.testTag("study_mode_switch")
                        )
                    }

                    Surface(
                        color = VisionColors.DistractionSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = "YouTube Study Mode notice",
                                tint = VisionColors.DistractionWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "YouTube is allowed during Study Mode for lectures & tutorials. Distraction checks issue a warning and start a ${settings.studyGracePeriodSeconds}s grace period before Focus Shield activates.",
                                fontSize = 12.sp,
                                color = VisionColors.DistractionOnSurface
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Grace Period Before Shield: ${settings.studyGracePeriodSeconds}s",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VisionColors.DistractionOnSurface
                        )
                        Slider(
                            value = settings.studyGracePeriodSeconds.toFloat(),
                            onValueChange = { onSetGraceSeconds(it.toInt()) },
                            valueRange = 10f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = VisionColors.DistractionPrimary,
                                activeTrackColor = VisionColors.DistractionPrimary
                            )
                        )
                    }

                    if (focusRuntime.isRunning && settings.studyModeEnabled) {
                        OutlinedButton(
                            onClick = { onTriggerStudyWarning("YouTube", true) },
                            border = BorderStroke(1.dp, VisionColors.DistractionWarning),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Test YouTube Study Warning & Grace Timer",
                                color = VisionColors.DistractionPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Privacy-First Focus Notifications Card
        item {
            Surface(
                color = VisionColors.DistractionSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, VisionColors.DistractionOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = "Privacy-first focus guard", tint = VisionColors.DistractionPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy-First Focus Guard",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = VisionColors.DistractionOnSurface
                        )
                    }
                    Text(
                        text = "VISION does not read your app usage history or screen content via accessibility services. All focus goals, timers, and distraction limits stay strictly private on your device.",
                        fontSize = 12.sp,
                        color = VisionColors.DistractionMuted
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Focus Timer Alerts: ${if (hasNotifPermission) "Allowed" else "Permission Needed"}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = VisionColors.DistractionOnSurface
                                )
                                Text(
                                    text = "Used only to display your active focus countdown and completion alerts.",
                                    fontSize = 11.sp,
                                    color = VisionColors.DistractionMuted
                                )
                            }
                            if (!hasNotifPermission) {
                                OutlinedButton(
                                    onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                ) {
                                    Text("Allow", color = VisionColors.DistractionPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Automatic Focus Shield Settings
        item {
            Surface(
                color = VisionColors.DistractionSurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, VisionColors.DistractionOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Focus Shield Reminders", fontWeight = FontWeight.Bold, color = VisionColors.DistractionOnSurface)
                            Text("Engage Focus Shield overlay after grace period expires", fontSize = 12.sp, color = VisionColors.DistractionMuted)
                        }
                        Switch(
                            checked = settings.autoBlockEnabled,
                            onCheckedChange = onSetAutoBlock,
                            colors = SwitchDefaults.colors(checkedTrackColor = VisionColors.DistractionPrimary)
                        )
                    }
                    Text(
                        text = "Configured Focus Goal Duration: ${settings.blockingDurationMinutes} min",
                        fontSize = 12.sp,
                        color = VisionColors.DistractionOnSurface
                    )
                    Slider(
                        value = settings.blockingDurationMinutes.toFloat(),
                        onValueChange = { onSetBlockingDuration(it.toInt()) },
                        valueRange = 15f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = VisionColors.DistractionPrimary,
                            activeTrackColor = VisionColors.DistractionPrimary
                        )
                    )
                }
            }
        }

        // Distraction Statistics & Focus History
        item {
            Surface(
                color = VisionColors.DistractionSurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, VisionColors.DistractionOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCell("Focus Today", "${totalFocusMins}m", VisionColors.DistractionPrimary)
                    StatCell("Warnings", "$warningsToday", VisionColors.DistractionWarning)
                    StatCell("Shields", "$blocksToday", VisionColors.DistractionPrimary)
                    StatCell("Sessions", "${focusSessions.size}", VisionColors.DistractionOnSurface)
                }
            }
        }

        // Selected Distracting Applications
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Distraction Guard List",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = VisionColors.DistractionOnSurface
                )
                OutlinedButton(
                    onClick = { showAddAppDialog = true },
                    border = BorderStroke(1.dp, VisionColors.DistractionPrimary)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add distracting app",
                        tint = VisionColors.DistractionPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add App", color = VisionColors.DistractionPrimary, fontSize = 12.sp)
                }
            }
        }

        items(distractingApps, key = { it.packageName }) { app ->
            Surface(
                color = VisionColors.DistractionSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VisionColors.DistractionOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = app.isSelectedForBlocking,
                        onCheckedChange = { checked -> onToggleAppSelected(app.packageName, checked) },
                        colors = CheckboxDefaults.colors(checkedColor = VisionColors.DistractionPrimary)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = app.appName,
                                fontWeight = FontWeight.SemiBold,
                                color = VisionColors.DistractionOnSurface
                            )
                            if (app.isYouTubeSpecial) {
                                Surface(
                                    color = VisionColors.DistractionWarning.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Study-Aware",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VisionColors.DistractionPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (app.isYouTubeSpecial) {
                                "Daily limit: ${app.dailyLimitMinutes}m • Educational use allowed in Study Mode"
                            } else {
                                "Daily self-limit: ${app.dailyLimitMinutes}m during focus hours"
                            },
                            fontSize = 11.sp,
                            color = VisionColors.DistractionMuted
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    if (showAddAppDialog) {
        AddDistractingAppDialog(
            onDismiss = { showAddAppDialog = false },
            onAdd = { name, limit ->
                onAddCustomApp(name, limit)
                showAddAppDialog = false
            }
        )
    }
}

@Composable
private fun AddDistractingAppDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("20") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add App or Site to Guard", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("App or platform name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Daily limit (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = limit.toIntOrNull()?.coerceIn(5, 240) ?: 20
                    if (name.isNotBlank()) onAdd(name.trim(), mins)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VisionColors.DistractionPrimary)
            ) {
                Text("Add", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StatCell(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = VisionColors.DistractionMuted
        )
    }
}
