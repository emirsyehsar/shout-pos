package com.emirst.shoutpos

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Asks the recognizer whether Indonesian is usable, and requests the on-device
 * model when it is supported but not yet installed.
 *
 * The support API landed in API 33. Below that the platform offers no way to
 * ask, so the answer is [SpeechLanguageAvailability.UNKNOWN] and the caller
 * should simply try to listen.
 */
class SpeechLanguageChecker(private val appContext: Context) {

    fun ensureAvailable(
        recognizer: SpeechRecognizer,
        intent: Intent,
        languageTag: String,
        onResult: (SpeechLanguageAvailability) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(SpeechLanguageAvailability.UNKNOWN)
            return
        }
        checkSupport(recognizer, intent, languageTag, onResult)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkSupport(
        recognizer: SpeechRecognizer,
        intent: Intent,
        languageTag: String,
        onResult: (SpeechLanguageAvailability) -> Unit
    ) {
        recognizer.checkRecognitionSupport(
            intent,
            ContextCompat.getMainExecutor(appContext),
            object : RecognitionSupportCallback {

                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    val installed = recognitionSupport.installedOnDeviceLanguages
                        .any { it.matchesLanguage(languageTag) }
                    val online = recognitionSupport.onlineLanguages
                        .any { it.matchesLanguage(languageTag) }
                    val pending = recognitionSupport.pendingOnDeviceLanguages
                        .any { it.matchesLanguage(languageTag) }
                    val downloadable = recognitionSupport.supportedOnDeviceLanguages
                        .any { it.matchesLanguage(languageTag) }

                    Log.i(
                        SpeechConstants.LOG_TAG,
                        "Support for $languageTag → installed=$installed online=$online " +
                            "pending=$pending downloadable=$downloadable"
                    )

                    val availability = when {
                        installed || online -> SpeechLanguageAvailability.AVAILABLE

                        // Trigger for "pending" too. A pending entry only means the
                        // system knows about the model, not that a download is running.
                        pending || downloadable -> {
                            requestDownload(recognizer, intent, onResult)
                            SpeechLanguageAvailability.DOWNLOADING
                        }

                        else -> SpeechLanguageAvailability.UNSUPPORTED
                    }
                    onResult(availability)
                }

                override fun onError(error: Int) {
                    Log.e(
                        SpeechConstants.LOG_TAG,
                        "Language support check failed: ${SpeechErrors.nameOf(error)} (code $error)"
                    )
                    onResult(SpeechLanguageAvailability.UNKNOWN)
                }
            }
        )
    }

    /**
     * API 34 added a listener overload; on API 33 the call is fire-and-forget with
     * no way to observe progress or even confirm it started.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestDownload(
        recognizer: SpeechRecognizer,
        intent: Intent,
        onResult: (SpeechLanguageAvailability) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.i(
                SpeechConstants.LOG_TAG,
                "Model download requested (API 33 — no progress signal exists)"
            )
            recognizer.triggerModelDownload(intent)
            return
        }

        Log.i(SpeechConstants.LOG_TAG, "Model download requested, listener attached")
        recognizer.triggerModelDownload(
            intent,
            ContextCompat.getMainExecutor(appContext),
            object : ModelDownloadListener {

                override fun onScheduled() {
                    Log.i(
                        SpeechConstants.LOG_TAG,
                        "Model download SCHEDULED — the system deferred it, " +
                            "it is not running yet"
                    )
                }

                override fun onProgress(completedPercent: Int) {
                    Log.i(SpeechConstants.LOG_TAG, "Model download progress: $completedPercent%")
                }

                override fun onSuccess() {
                    Log.i(SpeechConstants.LOG_TAG, "Model download COMPLETE")
                    onResult(SpeechLanguageAvailability.AVAILABLE)
                }

                override fun onError(error: Int) {
                    Log.e(
                        SpeechConstants.LOG_TAG,
                        "Model download FAILED: ${SpeechErrors.nameOf(error)} (code $error)"
                    )
                    onResult(SpeechLanguageAvailability.UNSUPPORTED)
                }
            }
        )
    }

    /**
     * Compares only the language subtag, so "id", "id-ID" and "id_ID" all match.
     * "in" is included because it is the legacy ISO code for Indonesian and still
     * surfaces from Java locale handling.
     */
    private fun String.matchesLanguage(languageTag: String): Boolean {
        val subtag = { tag: String -> tag.replace('_', '-').substringBefore('-').lowercase() }
        val wanted = subtag(languageTag)
        val actual = subtag(this)
        return actual == wanted || (wanted in LEGACY_INDONESIAN && actual in LEGACY_INDONESIAN)
    }

    private companion object {
        val LEGACY_INDONESIAN = setOf("id", "in")
    }
}
