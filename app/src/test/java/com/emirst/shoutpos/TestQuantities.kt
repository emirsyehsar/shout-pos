package com.emirst.shoutpos

/**
 * Lets tests state quantities the way a person would: `qty(2)` is two packets,
 * `qty(0.5)` is setengah. Keeps the scaling detail out of every assertion.
 */
fun qty(units: Int): Int = units * SpeechConstants.QUANTITY_SCALE

fun qty(units: Double): Int = (units * SpeechConstants.QUANTITY_SCALE).toInt()
