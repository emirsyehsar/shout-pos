package com.emirst.shoutpos

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Owns the platform recognizer and its callbacks, locked to Indonesian.
 *
 * Press-and-hold drives it: [startListening] while the finger is down,
 * [stopListening] on release. The transcript arrives asynchronously on
 * [onTranscript] some time after release — the recognizer needs a moment to
 * finalise, so release is not the same instant as "text is ready".
 *
 * The injected [RecognitionListener] absorbs the callbacks this class ignores;
 * only [onResults] and [onError] are overridden here. Being an interface, it is
 * trivially mocked in tests.
 *
 * Must be used from the main thread; [SpeechRecognizer] requires it.
 */
class SpeechAPI(
    private val appContext: Context,
    recognitionListener: RecognitionListener,
    private val languageChecker: SpeechLanguageChecker,
    private val tokenizer: SpeechTokenizer,
    private val model: SpeechModel
) : RecognitionListener by recognitionListener {

    /** Top hypothesis for the last utterance. */
    var onTranscript: ((String) -> Unit)? = null

    /** Resolved bill lines for the last utterance. */
    var onLineItems: ((List<SpeechLineItem>) -> Unit)? = null

    /** One of the SpeechRecognizer.ERROR_* codes. */
    var onFailure: ((Int) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    /**
     * Asks whether Indonesian is usable here, requesting the on-device model if
     * the recognizer supports the language but has not installed it yet.
     */
    fun ensureLanguageAvailable(onResult: (SpeechLanguageAvailability) -> Unit) {
        languageChecker.ensureAvailable(
            recognizer = activeRecognizer(),
            intent = buildIntent(),
            languageTag = SpeechConstants.LANGUAGE_INDONESIAN,
            onResult = onResult
        )
    }

    fun startListening() {
        if (listening) return
        listening = true
        activeRecognizer().startListening(buildIntent())
    }

    /** Finger lifted: tell the recognizer the utterance is over and wait for results. */
    fun stopListening() {
        if (!listening) return
        recognizer?.stopListening()
    }

    fun release() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val transcript = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        onTranscript?.invoke(transcript)
        onLineItems?.invoke(resolve(transcript))
    }

    /**
     * transcript → tokenizer → catalog. The two collaborators never talk to each
     * other; this class is the only place the pipeline is assembled.
     */
    private fun resolve(transcript: String): List<SpeechLineItem> =
        tokenizer.tokenize(transcript).map { segment -> resolveSegment(segment) }

    /**
     * Scores both readings of a segment and keeps the better one.
     *
     * "gudang garam surya 16" is the case that forces this. Reading the trailing
     * 16 as a quantity leaves "gudang garam surya", which still matches the real
     * product weakly — so falling back only on total failure would silently sell
     * sixteen packs. Comparing scores picks the exact whole-phrase match instead.
     */
    private fun resolveSegment(segment: SpeechSegment): SpeechLineItem {
        val asQuantified = model.findBestMatch(segment.productPhrase)
        val asWholePhrase = if (segment.fullPhrase != segment.productPhrase) {
            model.findBestMatch(segment.fullPhrase)
        } else {
            null
        }

        val wholePhraseWins = asWholePhrase != null &&
            (asQuantified == null || asWholePhrase.score > asQuantified.score)

        return when {
            wholePhraseWins -> SpeechLineItem(
                spokenPhrase = segment.fullPhrase,
                product = asWholePhrase.product,
                quantityMilli = SpeechConstants.QUANTITY_SCALE
            )

            asQuantified != null -> SpeechLineItem(
                spokenPhrase = segment.productPhrase,
                product = asQuantified.product,
                quantityMilli = segment.quantityMilli
            )

            else -> SpeechLineItem(
                spokenPhrase = segment.fullPhrase,
                product = null,
                quantityMilli = segment.quantityMilli
            )
        }
    }

    override fun onError(error: Int) {
        listening = false
        Log.e(
            SpeechConstants.LOG_TAG,
            "Recognition failed: ${SpeechErrors.nameOf(error)} (code $error)"
        )
        onFailure?.invoke(error)
    }

    /** Lazily builds the recognizer and keeps it for the life of this instance. */
    private fun activeRecognizer(): SpeechRecognizer =
        recognizer ?: createRecognizer().also {
            it.setRecognitionListener(this)
            recognizer = it
        }

    /**
     * Prototype tier policy per CLAUDE.md: on-device when the platform offers it,
     * otherwise the ordinary (online-capable) recognizer. No deeper fallback chain.
     */
    private fun createRecognizer(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            Log.i(SpeechConstants.LOG_TAG, "Recognizer tier: ON-DEVICE")
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            // triggerModelDownload against this tier will not fetch an on-device model.
            Log.i(SpeechConstants.LOG_TAG, "Recognizer tier: ONLINE (on-device unavailable)")
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechConstants.LANGUAGE_INDONESIAN)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                SpeechConstants.LANGUAGE_INDONESIAN
            )
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
}
