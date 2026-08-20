package com.emirst.shoutpos

import android.speech.SpeechRecognizer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.text.NumberFormat
import java.util.Locale

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

    /**
     * Bill total, already formatted as Rupiah. Null when there is nothing to
     * total, so the UI can leave the line out rather than show "Total: Rp0".
     */
    private val _totalPrice = MutableLiveData<String?>(null)
    val totalPrice: LiveData<String?> = _totalPrice

    init {
        speechApi.onLineItems = { items ->
            _statusRes.value = if (items.isEmpty()) R.string.speech_error_no_match else null
            _speechText.value = items.joinToString(separator = "\n") { it.display() }
            // Unmatched lines contribute 0, so they lower nothing but are still
            // visible above — the total never silently absorbs a missed item.
            _totalPrice.value = items
                .takeIf { line -> line.any { it.isMatched } }
                ?.sumOf { it.totalPrice }
                ?.let { formatRupiah(it) }
        }
        speechApi.onFailure = { error ->
            _speechText.value = ""
            _totalPrice.value = null
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
        _totalPrice.value = null
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
        _totalPrice.value = null
        _statusRes.value = R.string.speech_error_permission
    }

    override fun onCleared() {
        super.onCleared()
        speechApi.onLineItems = null
        speechApi.onFailure = null
        speechApi.release()
    }

    /** "indomie goreng - 2 - Rp7.000", or the raw phrase with "?" when unmatched. */
    private fun SpeechLineItem.display(): String {
        val name = product?.name ?: spokenPhrase
        val total = if (isMatched) formatRupiah(totalPrice) else UNMATCHED_MARKER
        return "$name - ${formatQuantity(quantityMilli)} - $total"
    }

    private fun formatQuantity(quantityMilli: Int): String =
        if (quantityMilli % SpeechConstants.QUANTITY_SCALE == 0) {
            (quantityMilli / SpeechConstants.QUANTITY_SCALE).toString()
        } else {
            (quantityMilli.toDouble() / SpeechConstants.QUANTITY_SCALE)
                .toString().trimEnd('0').trimEnd('.')
        }

    private fun formatRupiah(amount: Int): String = "Rp" + rupiahFormat.format(amount)

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

    private companion object {
        const val UNMATCHED_MARKER = "?"

        /** Indonesian grouping: 7000 renders as 7.000. */
        val rupiahFormat: NumberFormat = NumberFormat.getIntegerInstance(
            Locale.forLanguageTag(SpeechConstants.LANGUAGE_INDONESIAN)
        )
    }
}
