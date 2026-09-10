package com.kokoro.tts.reader.model

/**
 * Speaker profile metadata with human-readable descriptions of tone, gender, and accent.
 */
data class SpeakerProfile(
    val id: String,
    val name: String,
    val gender: String,
    val accent: String,
    val tone: String
) {
    val displayName: String
        get() = "$name ($accent $gender • $tone)"

    val shortLabel: String
        get() = "$name ($tone)"

    companion object {
        val PROFILES = listOf(
            // --- Soft, Harmonious & Bass Male Voices (User requested) ---
            SpeakerProfile("am_onyx", "Onyx", "Male", "US", "Deep Soft Bass"),
            SpeakerProfile("am_michael", "Michael", "Male", "US", "Soft & Harmonious"),
            SpeakerProfile("am_echo", "Echo", "Male", "US", "Calm & Mellow"),
            SpeakerProfile("bm_george", "George", "Male", "UK", "Deep Bass Classic"),
            SpeakerProfile("bm_fable", "Fable", "Male", "UK", "Warm Storyteller"),
            SpeakerProfile("bm_daniel", "Daniel", "Male", "UK", "Dignified Resonant"),
            SpeakerProfile("am_eric", "Eric", "Male", "US", "Conversational & Clear"),
            SpeakerProfile("am_liam", "Liam", "Male", "US", "Smooth & Youthful"),
            SpeakerProfile("am_adam", "Adam", "Male", "US", "Resonant & Dynamic"),

            // --- Soft & Expressive Female Voices ---
            SpeakerProfile("af_heart", "Heart", "Female", "US", "Soft & Warm"),
            SpeakerProfile("af_bella", "Bella", "Female", "US", "Gentle & Melodic"),
            SpeakerProfile("af_sarah", "Sarah", "Female", "US", "Calm Storyteller"),
            SpeakerProfile("af_nicole", "Nicole", "Female", "US", "Whisper Soft"),
            SpeakerProfile("af_sky", "Sky", "Female", "US", "Light & Airy"),
            SpeakerProfile("bf_emma", "Emma", "Female", "UK", "Refined & Gentle"),
            SpeakerProfile("bf_isabella", "Isabella", "Female", "UK", "Warm & Expressive"),
            SpeakerProfile("bf_alice", "Alice", "Female", "UK", "Classic Storyteller")
        )

        private val profileMap = PROFILES.associateBy { it.id }

        fun findById(id: String): SpeakerProfile? = profileMap[id]

        fun getDisplayName(id: String): String = profileMap[id]?.displayName ?: id

        fun getShortLabel(id: String): String = profileMap[id]?.name ?: id
    }
}
