package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.alfred.AlfredAssistantStatus
import com.example.alfred.AlfredUiState
import com.example.data.local.AlfredLogEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.VisionColors

/**
 * Consistent Deep Teal + Polished Copper ALFRED AI Personal Assistant bar shown
 * across all four VISION tabs.
 */
@Composable
fun AlfredAssistantBar(
    alfredState: AlfredUiState,
    onMicTap: () -> Unit,
    onStopVoiceOrSpeech: () -> Unit,
    onSpeakBriefing: () -> Unit,
    onExpandSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingMicAfterGrant by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingMicAfterGrant) {
            pendingMicAfterGrant = false
            onMicTap()
        } else {
            pendingMicAfterGrant = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "alfred_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val isActive = alfredState.status == AlfredAssistantStatus.LISTENING ||
        alfredState.status == AlfredAssistantStatus.THINKING ||
        alfredState.status == AlfredAssistantStatus.SPEAKING

    val micContainerColor by animateColorAsState(
        targetValue = when (alfredState.status) {
            AlfredAssistantStatus.LISTENING -> VisionColors.AlfredCopperBright
            AlfredAssistantStatus.SPEAKING -> VisionColors.AlfredCopper
            AlfredAssistantStatus.THINKING -> VisionColors.AlfredTealElevated
            else -> VisionColors.AlfredCopper
        },
        label = "mic_bg"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("alfred_assistant_bar")
            .clickable { onExpandSheet() },
        color = VisionColors.AlfredTeal,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, VisionColors.AlfredCopper.copy(alpha = 0.55f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ALFRED Emblem
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VisionColors.AlfredTealElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "ALFRED AI Assistant",
                    tint = VisionColors.AlfredCopperBright,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ALFRED",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.1.sp,
                        color = VisionColors.AlfredIvory
                    )
                    Surface(
                        color = VisionColors.AlfredCopper.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = when (alfredState.status) {
                                AlfredAssistantStatus.LISTENING -> "LISTENING"
                                AlfredAssistantStatus.THINKING -> "THINKING"
                                AlfredAssistantStatus.SPEAKING -> "SPEAKING"
                                AlfredAssistantStatus.ERROR -> "NOTICE"
                                AlfredAssistantStatus.IDLE -> if (alfredState.isOnline) "ONLINE" else "OFFLINE"
                            },
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VisionColors.AlfredCopperBright,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = alfredState.partialTranscript.ifBlank { alfredState.statusHeadline },
                    color = VisionColors.AlfredMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Voice Briefing / Announce button
            IconButton(
                onClick = {
                    if (alfredState.status == AlfredAssistantStatus.SPEAKING) {
                        onStopVoiceOrSpeech()
                    } else {
                        onSpeakBriefing()
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("alfred_announce_button")
            ) {
                Icon(
                    imageVector = if (alfredState.status == AlfredAssistantStatus.SPEAKING) {
                        Icons.Default.Stop
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = "Speak ALFRED daily briefing",
                    tint = VisionColors.AlfredIvory
                )
            }

            // Voice Interaction Mic Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(if (isActive) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(micContainerColor)
                    .clickable {
                        if (alfredState.status == AlfredAssistantStatus.LISTENING ||
                            alfredState.status == AlfredAssistantStatus.SPEAKING
                        ) {
                            onStopVoiceOrSpeech()
                        } else {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                onMicTap()
                            } else {
                                pendingMicAfterGrant = true
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    .testTag("alfred_mic_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (alfredState.status == AlfredAssistantStatus.LISTENING) {
                        Icons.Default.Stop
                    } else {
                        Icons.Default.Mic
                    },
                    contentDescription = "Activate ALFRED voice assistant",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlfredCommandSheet(
    alfredState: AlfredUiState,
    logs: List<AlfredLogEntity>,
    onDismiss: () -> Unit,
    onSendCommand: (String) -> Unit,
    onMicTap: () -> Unit,
    onSpeakBriefing: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by remember { mutableStateOf("") }
    val quickPrompts = listOf(
        "What is my next task?",
        "Read daily report",
        "Start study mode for 45 minutes",
        "Set alarm for 6:30 AM",
        "Add task Review notes at 4 PM"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VisionColors.AlfredTeal,
        contentColor = VisionColors.AlfredIvory
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ALFRED Personal Assistant",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = VisionColors.AlfredIvory
                    )
                    Text(
                        text = if (alfredState.isOnline) {
                            "Gemini Cloud AI + Local Offline Engine Ready"
                        } else {
                            "Offline Rule-Based Assistant Active"
                        },
                        fontSize = 12.sp,
                        color = VisionColors.AlfredCopperBright
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close ALFRED",
                        tint = VisionColors.AlfredIvory
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickPrompts) { prompt ->
                    AssistChip(
                        onClick = { onSendCommand(prompt) },
                        label = {
                            Text(prompt, fontSize = 12.sp, color = VisionColors.AlfredIvory)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = VisionColors.AlfredTealElevated
                        ),
                        border = BorderStroke(1.dp, VisionColors.AlfredCopper.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 300.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    val isUser = log.speaker == "USER"
                    Surface(
                        color = if (isUser) {
                            VisionColors.AlfredCopper.copy(alpha = 0.25f)
                        } else {
                            VisionColors.AlfredTealElevated
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isUser) "YOU" else "ALFRED",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VisionColors.AlfredCopperBright
                                )
                                if (log.actionSummary.isNotBlank()) {
                                    Text(
                                        text = log.actionSummary,
                                        fontSize = 11.sp,
                                        color = VisionColors.AlfredMuted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.message,
                                fontSize = 14.sp,
                                color = VisionColors.AlfredIvory
                            )
                        }
                    }
                }
            }

            if (!alfredState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = alfredState.errorMessage,
                    color = VisionColors.AlfredCopperBright,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Command ALFRED across all tabs...",
                            color = VisionColors.AlfredMuted
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("alfred_command_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VisionColors.AlfredIvory,
                        unfocusedTextColor = VisionColors.AlfredIvory,
                        focusedBorderColor = VisionColors.AlfredCopperBright,
                        unfocusedBorderColor = VisionColors.AlfredMuted.copy(alpha = 0.5f),
                        cursorColor = VisionColors.AlfredCopperBright
                    )
                )

                IconButton(
                    onClick = onMicTap,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(VisionColors.AlfredCopper)
                        .testTag("alfred_sheet_mic_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice command", tint = Color.White)
                }

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendCommand(inputText)
                            inputText = ""
                        } else {
                            onSpeakBriefing()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(VisionColors.AlfredTealElevated)
                        .testTag("alfred_send_command_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command to ALFRED",
                        tint = VisionColors.AlfredCopperBright
                    )
                }
            }
        }
    }
}
