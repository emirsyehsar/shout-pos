package com.emirst.shoutpos

/** Whether the recognizer can actually handle Indonesian on this device. */
enum class SpeechLanguageAvailability {

    /** Installed on-device, or reachable online. Safe to listen. */
    AVAILABLE,

    /** Not installed yet; a model download has been requested or is already pending. */
    DOWNLOADING,

    /** The recognizer does not offer Indonesian at all. Nothing to download. */
    UNSUPPORTED,

    /** Could not be determined — below API 33, or the platform refused to answer. */
    UNKNOWN
}
