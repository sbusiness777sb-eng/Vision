package com.example.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.core.util.DateUtil
import com.example.data.local.VisionDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class AlarmRingService : Service(), TextToSpeech.OnInitListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var pendingSpeechText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L) ?: -1L
        val label = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "VISION Alarm"
        val timeText = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_TIME).orEmpty()

        val dispatcher = NotificationDispatcher(this)
        val notification = dispatcher.buildAlarmNotification(alarmId, label, timeText)
        val notifId = (alarmId.toInt().takeIf { it > 0 } ?: 1001) + 4000

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notifId, notification)
        }

        startAlarmSoundAndVibration()
        prepareAlfredAnnouncement(alarmId, label)

        // Launch full-screen alarm screen
        val activityIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ALARM_TIME, timeText)
        }
        runCatching { startActivity(activityIntent) }

        return START_NOT_STICKY
    }

    private fun startAlarmSoundAndVibration() {
        try {
            mediaPlayer?.release()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmRingService", "Failed to play alarm audio", e)
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 400, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmRingService", "Vibration unavailable", e)
        }
    }

    private fun prepareAlfredAnnouncement(alarmId: Long, label: String) {
        serviceScope.launch {
            val db = VisionDatabase.getInstance(applicationContext)
            val alarm = if (alarmId > 0L) db.alarmDao().getAlarmById(alarmId) else null
            if (alarm == null || alarm.alfredAnnouncementEnabled) {
                val tasksToday = db.taskDao().getTasksForDateOnce(DateUtil.todayEpochDay())
                val pending = tasksToday.count { !it.isCompleted }
                val message = if (label.contains("wake", ignoreCase = true) || alarm?.alarmType?.name == "WAKE_UP") {
                    "Hello Master, good morning. It is time to wake up. You have $pending pending tasks scheduled today."
                } else {
                    "Master, reminder from VISION: $label."
                }
                pendingSpeechText = message
                tts = TextToSpeech(applicationContext, this@AlarmRingService)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            pendingSpeechText?.let { text ->
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "alarm_announcement")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        serviceScope.cancel()
    }
}
