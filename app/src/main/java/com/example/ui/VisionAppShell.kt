package com.example.ui

import android.app.Activity
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alfred.AlfredSpeechIntentHelper
import com.example.ui.components.AlfredCommandSheet
import com.example.ui.screens.AlarmScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DistractionScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.theme.VisionTab
import com.example.ui.theme.VisionTheme
import com.example.ui.theme.paletteForTab
import com.example.ui.viewmodel.VisionViewModel

@Composable
fun VisionAppShell(viewModel: VisionViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val pendingTasks by viewModel.pendingTasks.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val distractingApps by viewModel.distractingApps.collectAsStateWithLifecycle()
    val focusSessions by viewModel.focusSessions.collectAsStateWithLifecycle()
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val focusRuntime by viewModel.focusRuntime.collectAsStateWithLifecycle()
    val dailyReport by viewModel.dailyReport.collectAsStateWithLifecycle()
    val weeklyReport by viewModel.weeklyReport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val alfredState by viewModel.alfredState.collectAsStateWithLifecycle()
    val alfredLogs by viewModel.alfredLogs.collectAsStateWithLifecycle()

    var showAlfredSheet by remember { mutableStateOf(false) }

    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcript = matches?.firstOrNull()?.trim().orEmpty()
            if (transcript.isNotBlank()) {
                viewModel.onVoiceTranscript(transcript)
            } else {
                viewModel.onVoiceCancelledOrError("No speech recognized. Please try again or type below.")
            }
        } else {
            viewModel.onVoiceCancelledOrError(null)
        }
    }

    val launchSystemVoiceInput: () -> Unit = {
        if (AlfredSpeechIntentHelper.isAvailable(context)) {
            viewModel.onVoiceListeningStarted()
            val intent = AlfredSpeechIntentHelper.createRecognizeSpeechIntent(
                preferOffline = !alfredState.isOnline
            )
            runCatching { voiceInputLauncher.launch(intent) }.onFailure {
                viewModel.onVoiceCancelledOrError("Unable to open system voice input. You can type commands below.")
            }
        } else {
            viewModel.onVoiceCancelledOrError("Voice input service is unavailable on this device. You can type commands below.")
        }
    }

    // Back gesture returns from secondary tabs to Dashboard
    BackHandler(enabled = selectedTab != VisionTab.DASHBOARD) {
        viewModel.selectTab(VisionTab.DASHBOARD)
    }

    val palette = paletteForTab(selectedTab)

    VisionTheme(activeTab = selectedTab) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 680.dp

            Scaffold(
                containerColor = palette.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = palette.surfaceElevated,
                            contentColor = palette.onSurface
                        ) {
                            VisionTab.entries.forEach { tab ->
                                val selected = selectedTab == tab
                                val icon = tabIcon(tab, selected)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(icon, contentDescription = tab.label) },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = palette.onPrimary,
                                        selectedTextColor = palette.primary,
                                        indicatorColor = palette.primary,
                                        unselectedIconColor = palette.muted,
                                        unselectedTextColor = palette.muted
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = palette.surfaceElevated
                        ) {
                            VisionTab.entries.forEach { tab ->
                                val selected = selectedTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab, selected), contentDescription = tab.label) },
                                    label = { Text(tab.label, fontSize = 11.sp) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = palette.onPrimary,
                                        selectedTextColor = palette.primary,
                                        indicatorColor = palette.primary,
                                        unselectedIconColor = palette.muted,
                                        unselectedTextColor = palette.muted
                                    ),
                                    modifier = Modifier.testTag("nav_rail_${tab.route}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 860.dp)
                        ) {
                            when (selectedTab) {
                                VisionTab.DASHBOARD -> DashboardScreen(
                                    todayTasks = todayTasks,
                                    allTasks = allTasks,
                                    goals = goals,
                                    routines = routines,
                                    dailyReport = dailyReport,
                                    alfredState = alfredState,
                                    onMicTap = launchSystemVoiceInput,
                                    onStopVoiceOrSpeech = { viewModel.stopVoiceOrSpeech() },
                                    onSpeakBriefing = { viewModel.speakAlfredBriefing() },
                                    onOpenAlfredSheet = { showAlfredSheet = true },
                                    onCreateTask = viewModel::createTask,
                                    onUpdateTask = viewModel::updateTask,
                                    onToggleTask = viewModel::toggleTaskCompleted,
                                    onToggleTopPriority = viewModel::toggleTopPriority,
                                    onDeleteTask = viewModel::deleteTask,
                                    onCreateGoal = viewModel::createGoal,
                                    onIncrementGoal = viewModel::updateGoalProgress,
                                    onDeleteGoal = viewModel::deleteGoal,
                                    onCreateRoutine = viewModel::createRoutine,
                                    onLogRoutineMinutes = viewModel::logRoutineMinutes,
                                    modifier = Modifier.statusBarsPadding()
                                )

                                VisionTab.ALARM -> AlarmScreen(
                                    alarms = alarms,
                                    todayTasks = todayTasks,
                                    settings = settings,
                                    canScheduleExactAlarms = viewModel.canScheduleExactAlarms(),
                                    alfredState = alfredState,
                                    onMicTap = launchSystemVoiceInput,
                                    onStopVoiceOrSpeech = { viewModel.stopVoiceOrSpeech() },
                                    onSpeakBriefing = { viewModel.speakAlfredBriefing() },
                                    onOpenAlfredSheet = { showAlfredSheet = true },
                                    onSaveAlarm = viewModel::upsertAlarm,
                                    onToggleAlarm = viewModel::toggleAlarmEnabled,
                                    onDeleteAlarm = viewModel::deleteAlarm,
                                    onToggleScheduleNotifications = viewModel::setDailyScheduleNotifications,
                                    onToggleAlfredVoice = viewModel::setAlfredVoiceEnabled,
                                    modifier = Modifier.statusBarsPadding()
                                )

                                VisionTab.DISTRACTION -> DistractionScreen(
                                    settings = settings,
                                    focusRuntime = focusRuntime,
                                    distractingApps = distractingApps,
                                    focusSessions = focusSessions,
                                    dailyStats = dailyStats,
                                    alfredState = alfredState,
                                    onMicTap = launchSystemVoiceInput,
                                    onStopVoiceOrSpeech = { viewModel.stopVoiceOrSpeech() },
                                    onSpeakBriefing = { viewModel.speakAlfredBriefing() },
                                    onOpenAlfredSheet = { showAlfredSheet = true },
                                    onRefreshApps = viewModel::refreshCuratedApps,
                                    onAddCustomApp = viewModel::addCustomDistractingApp,
                                    onToggleAppSelected = viewModel::toggleAppBlocked,
                                    onSetStudyMode = viewModel::setStudyMode,
                                    onSetGraceSeconds = viewModel::setStudyGraceSeconds,
                                    onSetAutoBlock = viewModel::setAutoBlockEnabled,
                                    onSetBlockingDuration = viewModel::setBlockingDurationMinutes,
                                    onStartFocus = viewModel::startFocusSession,
                                    onStopFocus = viewModel::stopFocusSession,
                                    onTriggerStudyWarning = viewModel::triggerStudyWarningCheck,
                                    modifier = Modifier.statusBarsPadding()
                                )

                                VisionTab.REPORT -> ReportScreen(
                                    dailyReport = dailyReport,
                                    weeklyReport = weeklyReport,
                                    pendingTasks = pendingTasks,
                                    alfredState = alfredState,
                                    onMicTap = launchSystemVoiceInput,
                                    onStopVoiceOrSpeech = { viewModel.stopVoiceOrSpeech() },
                                    onSpeakBriefing = { viewModel.speakAlfredBriefing() },
                                    onOpenAlfredSheet = { showAlfredSheet = true },
                                    onRescheduleToTomorrow = { id -> viewModel.rescheduleTaskToTomorrow(id) },
                                    onCompleteTask = viewModel::toggleTaskCompleted,
                                    onCreateTomorrowTask = viewModel::createTask,
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                    }
                }
            }

            if (showAlfredSheet) {
                AlfredCommandSheet(
                    alfredState = alfredState,
                    logs = alfredLogs,
                    onDismiss = { showAlfredSheet = false },
                    onSendCommand = viewModel::sendTextCommandToAlfred,
                    onMicTap = launchSystemVoiceInput,
                    onSpeakBriefing = viewModel::speakAlfredBriefing
                )
            }
        }
    }
}

private fun tabIcon(tab: VisionTab, selected: Boolean) = when (tab) {
    VisionTab.DASHBOARD -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
    VisionTab.ALARM -> if (selected) Icons.Filled.Alarm else Icons.Outlined.Alarm
    VisionTab.DISTRACTION -> if (selected) Icons.Filled.DoNotDisturbOn else Icons.Outlined.DoNotDisturbOn
    VisionTab.REPORT -> if (selected) Icons.Filled.Assessment else Icons.Outlined.Assessment
}
