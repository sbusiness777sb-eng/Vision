package com.example.alfred

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class AlfredTtsEngine(
    context: Context,
    private val onSpeakingChanged: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    private var pendingUtterance: String? = null

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext, this)
        }.onFailure {
            Log.e("AlfredTTS", "TextToSpeech init failed", it)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = tts?.setLanguage(Locale.getDefault())
            isInitialized = langResult != TextToSpeech.LANG_MISSING_DATA &&
                langResult != TextToSpeech.LANG_NOT_SUPPORTED
            if (!isInitialized) {
                tts?.language = Locale.US
                isInitialized = true
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeakingChanged(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeakingChanged(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeakingChanged(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onSpeakingChanged(false)
                }
            })
            pendingUtterance?.let { queued ->
                pendingUtterance = null
                speak(queued)
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!isInitialized) {
            pendingUtterance = text
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        runCatching { tts?.stop() }
        onSpeakingChanged(false)
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}

/**
 * Zero-permission system speech recognition helper.
 * Delegates microphone capture to the OS speech recognition activity via
 * RecognizerIntent.ACTION_RECOGNIZE_SPEECH so VISION never requests RECORD_AUDIO.
 */
object AlfredSpeechIntentHelper {
    fun createRecognizeSpeechIntent(preferOffline: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command to ALFRED")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        }
    }

    fun isAvailable(context: Context): Boolean {
        val intent = createRecognizeSpeechIntent(preferOffline = false)
        return intent.resolveActivity(context.packageManager) != null
    }
}
