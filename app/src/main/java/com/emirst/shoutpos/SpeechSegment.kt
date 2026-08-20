package com.emirst.shoutpos

/**
 * One spoken item, as the tokenizer understood it.
 *
 * [productPhrase] and [quantityMilli] are the preferred reading. [fullPhrase]
 * keeps every token including the trailing number, so the caller can fall back
 * when that number turns out to be part of a product name rather than a count —
 * "gudang garam surya 16".
 */
data class SpeechSegment(
    val tokens: List<String>,
    val productPhrase: String,
    val quantityMilli: Int,
    val fullPhrase: String
)
