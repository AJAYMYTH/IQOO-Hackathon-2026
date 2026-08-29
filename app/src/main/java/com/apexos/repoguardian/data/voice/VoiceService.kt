package com.apexos.repoguardian.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class VoiceState {
    data object Idle : VoiceState()
    data object Listening : VoiceState()
    data class Result(val text: String, val isTrigger: Boolean) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

@Singleton
class VoiceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: SpeechRecognizer? = null
    
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state

    companion object {
        private const val TAG = "VoiceService"
        private val TRIGGER_KEYWORDS = listOf(
            "review", "check", "analyze", "scan", "inspect"
        )
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable()) {
            _state.value = VoiceState.Error("Speech recognition not available on this device")
            return
        }

        stop() // Clean up any previous session

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = VoiceState.Listening
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error ($error)"
                    }
                    Log.w(TAG, "Speech error: $message")
                    _state.value = VoiceState.Error(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "Recognized: $text")
                    _state.value = VoiceState.Result(
                        text = text,
                        isTrigger = matchesTrigger(text)
                    )
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // On-device recognition
        }

        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        recognizer = null
        _state.value = VoiceState.Idle
    }

    fun resetState() {
        _state.value = VoiceState.Idle
    }

    private fun matchesTrigger(text: String): Boolean {
        val lower = text.lowercase()
        return TRIGGER_KEYWORDS.any { lower.contains(it) }
    }
}
