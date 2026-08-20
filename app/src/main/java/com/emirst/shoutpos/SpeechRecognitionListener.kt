package com.emirst.shoutpos

import android.os.Bundle
import android.speech.RecognitionListener

/**
 * No-op implementation of the platform's twelve-method [RecognitionListener].
 *
 * It exists so that a collaborator can delegate away the ten callbacks it does
 * not care about and override only the two it does. Nothing here has behaviour,
 * which is the point — it carries the boilerplate so SpeechAPI does not.
 */
open class SpeechRecognitionListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onError(error: Int) = Unit
    override fun onResults(results: Bundle?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
