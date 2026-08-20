package com.emirst.shoutpos

/** Fixed values for the speech pipeline. */
object SpeechConstants {

    /** BCP-47 tag for Indonesian. The only language the recognizer is asked for. */
    const val LANGUAGE_INDONESIAN = "id-ID"

    /** Logcat tag. Error codes only — never transcripts. */
    const val LOG_TAG = "ShoutPos"
}
