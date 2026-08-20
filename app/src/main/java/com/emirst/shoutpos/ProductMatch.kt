package com.emirst.shoutpos

/**
 * A catalog hit and how good it was.
 *
 * The score is what lets a caller compare two readings of the same segment —
 * see SpeechAPI.resolveSegment — and is the number a confidence threshold will
 * eventually be derived from.
 */
data class ProductMatch(
    val product: ProductData,
    val score: Double
)
