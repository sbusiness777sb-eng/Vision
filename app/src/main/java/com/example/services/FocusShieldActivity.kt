package com.example.services

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
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
import com.example.MainActivity
import com.example.ui.components.VisionLogo
import com.example.ui.theme.VisionColors
import com.example.ui.theme.VisionTheme

class FocusShieldActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name"
        const val EXTRA_BLOCKED_PKG = "extra_blocked_pkg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedApp = intent.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: "Distracting App"

        setContent {
            VisionTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VisionColors.DistractionBackground)
                        .systemBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    VisionLogo(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Focus shield active",
                        tint = VisionColors.DistractionPrimary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Focus Shield Active",
                        color = VisionColors.DistractionPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$blockedApp is restricted during your active VISION Focus session.",
                        color = VisionColors.DistractionOnSurface,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        color = VisionColors.AlfredTeal,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ALFRED: \"Stay committed to your goal, Master. Let us return to your scheduled priority.\"",
                            color = VisionColors.AlfredIvory,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(
                        onClick = {
                            startActivity(
                                Intent(this@FocusShieldActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                            )
                            finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("shield_return_vision_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VisionColors.DistractionPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Return to VISION", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val stopIntent = Intent(this@FocusShieldActivity, FocusMonitorService::class.java).apply {
                                action = FocusMonitorService.ACTION_STOP_FOCUS
                            }
                            startService(stopIntent)
                            finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("shield_end_focus_button")
                    ) {
                        Text("End Focus Session", color = VisionColors.DistractionPrimary)
                    }
                }
            }
        }
    }
}
