package com.apexos.repoguardian.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class VoiceIntent {
    data class Review(val query: String) : VoiceIntent()
    data class CiCd(val query: String) : VoiceIntent()
    data class Chat(val prompt: String) : VoiceIntent()
    data class Dictation(val text: String) : VoiceIntent()
}

sealed class VoiceState {
    data object Idle : VoiceState()
    data class Listening(val rmsDb: Float = 0f) : VoiceState()
    data class Result(
        val text: String,
        val isTrigger: Boolean,
        val intent: VoiceIntent
    ) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

@Singleton
class VoiceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state

    companion object {
        private const val TAG = "VoiceService"
        private val REVIEW_KEYWORDS = listOf("review", "check", "analyze", "scan", "inspect", "diff", "bug")
        private val CICD_KEYWORDS = listOf("ci", "cd", "pipeline", "workflow", "action", "deploy")
        private val CHAT_KEYWORDS = listOf("ask", "chat", "explain", "why", "how", "what", "tell", "summarize")
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        mainHandler.post {
            try {
                if (!isAvailable()) {
                    _state.value = VoiceState.Error("Speech recognition is not available on this device")
                    return@post
                }

                stopInternal()

                recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _state.value = VoiceState.Listening(0f)
                            Log.d(TAG, "Voice recognition ready for speech")
                        }

                        override fun onBeginningOfSpeech() {
                            _state.value = VoiceState.Listening(1f)
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            if (_state.value is VoiceState.Listening) {
                                _state.value = VoiceState.Listening(rmsdB.coerceAtLeast(0f))
                            }
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition cancelled"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                                SpeechRecognizer.ERROR_NETWORK -> "Network required for speech parsing"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice service network timeout"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap mic to retry"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy"
                                SpeechRecognizer.ERROR_SERVER -> "Voice server error"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                                else -> "Speech recognition error ($error)"
                            }
                            Log.w(TAG, "Speech error: $message (code $error)")
                            // If client cancelled or no match, gracefully revert to idle or show soft error
                            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                                _state.value = VoiceState.Error("No speech detected. Tap mic to speak again.")
                            } else {
                                _state.value = VoiceState.Error(message)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim() ?: ""
                            Log.d(TAG, "Voice recognition result: $text")

                            if (text.isBlank()) {
                                _state.value = VoiceState.Error("No clear speech detected")
                                return
                            }

                            val matchedIntent = parseIntent(text)
                            val isTrigger = matchedIntent !is VoiceIntent.Dictation

                            _state.value = VoiceState.Result(
                                text = text,
                                isTrigger = isTrigger,
                                intent = matchedIntent
                            )
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim() ?: ""
                            if (text.isNotBlank()) {
                                Log.d(TAG, "Voice partial: $text")
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                recognizer?.startListening(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start speech recognition", e)
                _state.value = VoiceState.Error(e.message ?: "Failed to initialize microphone")
            }
        }
    }

    fun stop() {
        mainHandler.post {
            stopInternal()
            _state.value = VoiceState.Idle
        }
    }

    private fun stopInternal() {
        try {
            recognizer?.apply {
                stopListening()
                cancel()
                destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning recognizer: ${e.message}")
        }
        recognizer = null
    }

    fun resetState() {
        _state.value = VoiceState.Idle
    }

    private fun parseIntent(text: String): VoiceIntent {
        val lower = text.lowercase()

        return when {
            REVIEW_KEYWORDS.any { lower.contains(it) } -> {
                VoiceIntent.Review(text)
            }
            CICD_KEYWORDS.any { lower.contains(it) } -> {
                VoiceIntent.CiCd(text)
            }
            CHAT_KEYWORDS.any { lower.contains(it) } -> {
                VoiceIntent.Chat(text)
            }
            else -> {
                VoiceIntent.Dictation(text)
            }
        }
    }
}
