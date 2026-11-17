package com.example.alfred

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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

class AlfredSpeechController(
    private val context: Context,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStopped: () -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(preferOffline: Boolean) {
        if (!isAvailable()) {
            onError("Speech recognition service is not installed on this device. You can type commands below.")
            return
        }
        stopListening()
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        onListeningStopped()
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Speech recognition network unavailable; check offline language pack."
                            SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try speaking again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                            else -> "Voice input unavailable (code $error). You can type your command."
                        }
                        onListeningStopped()
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull()?.trim()
                        onListeningStopped()
                        if (!best.isNullOrBlank()) {
                            onFinalText(best)
                        } else {
                            onError("Could not recognize speech.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            onPartialText(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("AlfredSpeech", "Failed to start listening", e)
            onListeningStopped()
            onError("Unable to start microphone input: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
    }
}
