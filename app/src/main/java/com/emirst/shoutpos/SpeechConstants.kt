package com.emirst.shoutpos

/** Fixed values for the speech pipeline. */
object SpeechConstants {

    /** BCP-47 tag for Indonesian. The only language the recognizer is asked for. */
    const val LANGUAGE_INDONESIAN = "id-ID"

    /** Logcat tag. Error codes only — never transcripts. */
    const val LOG_TAG = "ShoutPos"

    /**
     * Quantities are scaled integers in thousandths: 1 = 1000, 0.5 = 500.
     * Never a float — see the reasoning on SpeechLineItem.totalPrice.
     */
    const val QUANTITY_SCALE = 1_000

    /**
     * Minimum token-overlap score for a catalog match. A provisional value:
     * deriving the real threshold from recorded utterances is one of the
     * prototype's intended outputs.
     */
    const val MATCH_THRESHOLD = 0.5
}
