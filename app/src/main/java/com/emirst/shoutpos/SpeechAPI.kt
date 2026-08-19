package com.emirst.shoutpos

/**
 * Stand-in for the real speech pipeline. For now it only counts.
 * Pure Kotlin, no Android imports, so it stays unit-testable on the JVM.
 */
class SpeechAPI {

    var counter: Int = DEFAULT_COUNT
        private set

    fun reset() {
        counter = DEFAULT_COUNT
    }

    fun increment(): Int {
        counter += 1
        return counter
    }

    companion object {
        private const val DEFAULT_COUNT = 0
    }
}
