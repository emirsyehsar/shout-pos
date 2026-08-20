package com.emirst.shoutpos

import android.speech.SpeechRecognizer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SpeechViewModel(
    private val speechApi: SpeechAPI
) : ViewModel() {

    /** Transcript for txt_speech. Empty until the first utterance comes back. */
    private val _speechText = MutableLiveData("")
    val speechText: LiveData<String> = _speechText

    /**
     * String resource for whatever the transcript cannot say for itself —
     * "listening", or a recognizer error. Null once a transcript is showing.
     * Kept as a resource id so no Context has to live in the ViewModel.
     */
    private val _statusRes = MutableLiveData<Int?>(null)
    val statusRes: LiveData<Int?> = _statusRes

    init {
        speechApi.onTranscript = { transcript ->
            _statusRes.value = if (transcript.isEmpty()) R.string.speech_error_no_match else null
            _speechText.value = transcript
        }
        speechApi.onFailure = { error ->
            _speechText.value = ""
            _statusRes.value = errorMessageRes(error)
            if (error.isLanguageError()) checkLanguageAvailability()
        }
        checkLanguageAvailability()
    }

    /**
     * Verifies Indonesian is usable before the first press, and again whenever
     * the recognizer rejects the language. A supported-but-missing model is
     * requested for download as part of this check.
     */
    private fun checkLanguageAvailability() {
        if (!speechApi.isRecognitionAvailable()) {
            _statusRes.value = R.string.speech_error_unavailable
            return
        }
        speechApi.ensureLanguageAvailable { availability ->
            _statusRes.value = when (availability) {
                SpeechLanguageAvailability.AVAILABLE,
                SpeechLanguageAvailability.UNKNOWN -> null

                SpeechLanguageAvailability.DOWNLOADING ->
                    R.string.speech_status_downloading

                SpeechLanguageAvailability.UNSUPPORTED ->
                    R.string.speech_error_language_unsupported
            }
        }
    }

    /** Button went down. */
    fun onPressStart() {
        if (!speechApi.isRecognitionAvailable()) {
            _statusRes.value = R.string.speech_error_unavailable
            return
        }
        _speechText.value = ""
        _statusRes.value = R.string.speech_status_listening
        speechApi.startListening()
    }

    /** Button came up. The transcript lands a moment later via the callbacks above. */
    fun onPressEnd() {
        speechApi.stopListening()
    }

    /** The microphone permission was refused, so listening never started. */
    fun onPermissionDenied() {
        _speechText.value = ""
        _statusRes.value = R.string.speech_error_permission
    }

    override fun onCleared() {
        super.onCleared()
        speechApi.onTranscript = null
        speechApi.onFailure = null
        speechApi.release()
    }

    private fun errorMessageRes(error: Int): Int = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.speech_error_no_match

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.speech_error_permission

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.speech_error_network

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.speech_error_busy

        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.speech_error_language_unsupported

        else -> R.string.speech_error_generic
    }

    private fun Int.isLanguageError(): Boolean =
        this == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
            this == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
}
