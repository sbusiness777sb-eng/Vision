package com.example.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.VisionLogo
import com.example.ui.theme.VisionColors
import com.example.ui.theme.VisionTheme

class AlarmRingingActivity : ComponentActivity() {
    companion object {
        const val ACTION_CLOSE_ALARM_UI = "com.example.vision.ACTION_CLOSE_ALARM_UI"
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val filter = IntentFilter(ACTION_CLOSE_ALARM_UI)
        ContextCompat.registerReceiver(this, closeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "VISION Alarm"
        val timeText = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_TIME).orEmpty()

        setContent {
            VisionTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VisionColors.AlarmBackground)
                        .systemBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VisionLogo(modifier = Modifier.size(36.dp))
                        Text(
                            text = "VISION",
                            color = VisionColors.AlarmOnSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeText.ifBlank { "ALARM" },
                            color = VisionColors.AlarmPrimary,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = label,
                            color = VisionColors.AlarmOnSurface,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Surface(
                            color = VisionColors.AlfredTeal,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "ALFRED: \"Hello Master, it is time to rise and begin your planned schedule.\"",
                                color = VisionColors.AlfredIvory,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val snooze = Intent(this@AlarmRingingActivity, AlarmReceiver::class.java).apply {
                                    action = AlarmReceiver.ACTION_SNOOZE
                                    putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                                    putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
                                }
                                sendBroadcast(snooze)
                                finish()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("alarm_ring_snooze_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VisionColors.AlarmOnSurface)
                        ) {
                            Icon(Icons.Default.Snooze, contentDescription = "Snooze alarm")
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Snooze")
                        }

                        Button(
                            onClick = {
                                val dismiss = Intent(this@AlarmRingingActivity, AlarmReceiver::class.java).apply {
                                    action = AlarmReceiver.ACTION_DISMISS
                                    putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                                }
                                sendBroadcast(dismiss)
                                finish()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("alarm_ring_dismiss_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VisionColors.AlarmPrimary,
                                contentColor = Color(0xFF141715)
                            )
                        ) {
                            Icon(Icons.Default.AlarmOff, contentDescription = "Dismiss alarm")
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Dismiss", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(closeReceiver) }
    }
}
