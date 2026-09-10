package com.kokoro.tts.engine.director

import com.kokoro.tts.reader.model.SentenceItem

/**
 * Path A: "Smart Director + Fast Performer" Hybrid Architecture.
 *
 * Directs the performance attributes (cadence, tempo curve, pause duration, and voice modulation)
 * for each sentence before neural synthesis by KokoroEngine.
 * Executes in sub-millisecond time with zero memory or battery overhead.
 */
object SpeechDirector {

    enum class SentenceRole {
        NARRATION,
        DIALOGUE,
        EXCLAMATION,
        QUESTION,
        HEADING
    }

    data class DirectedPerformance(
        val role: SentenceRole,
        val effectiveSpeed: Float,
        val postSilenceMs: Long,
        val isExpressive: Boolean
    )

    // Base silence constants (in milliseconds)
    const val SILENCE_INTRA_PARAGRAPH_MS = 220L   // Breath pause between standard sentences
    const val SILENCE_DIALOGUE_MS = 340L          // Natural pause after spoken dialogue
    const val SILENCE_PARAGRAPH_BREAK_MS = 700L   // Thoughtful pause between paragraphs
    const val SILENCE_QUESTION_MS = 380L          // Deliberate pause after question
    const val SILENCE_CHAPTER_END_MS = 1400L      // Deep pause between chapters

    /**
     * Direct the performance of a sentence given its context.
     *
     * @param sentence The sentence item with text, dialogue, and paragraph flags
     * @param isLastSentenceInChapter True if this sentence ends the chapter
     * @param baseSpeed The user-configured playback speed (e.g. 1.0f)
     */
    fun direct(
        sentence: SentenceItem,
        isLastSentenceInChapter: Boolean = false,
        baseSpeed: Float = 1.0f
    ): DirectedPerformance {
        val text = sentence.text.trim()

        // 1. Classify role
        val role = when {
            text.startsWith("CHAPTER", ignoreCase = true) || text.startsWith("#") -> SentenceRole.HEADING
            sentence.isDialogue -> SentenceRole.DIALOGUE
            text.endsWith("!") -> SentenceRole.EXCLAMATION
            text.endsWith("?") -> SentenceRole.QUESTION
            else -> SentenceRole.NARRATION
        }

        // 2. Compute dynamic tempo modulation (micro-cadence)
        val speedMultiplier = when (role) {
            SentenceRole.HEADING -> 0.92f      // Headings spoken with deliberate gravitas
            SentenceRole.DIALOGUE -> 1.03f     // Spoken dialogue slightly quicker, animated
            SentenceRole.EXCLAMATION -> 1.05f  // Urgency / excitement
            SentenceRole.QUESTION -> 0.98f     // Thoughtful upward cadence
            SentenceRole.NARRATION -> 1.00f    // Steady natural pace
        }
        val effectiveSpeed = (baseSpeed * speedMultiplier).coerceIn(0.5f, 2.5f)

        // 3. Compute post-sentence silence duration
        val postSilenceMs = when {
            isLastSentenceInChapter -> SILENCE_CHAPTER_END_MS
            sentence.isParagraphEnd -> SILENCE_PARAGRAPH_BREAK_MS
            role == SentenceRole.QUESTION -> SILENCE_QUESTION_MS
            role == SentenceRole.DIALOGUE -> SILENCE_DIALOGUE_MS
            role == SentenceRole.EXCLAMATION -> SILENCE_DIALOGUE_MS
            role == SentenceRole.HEADING -> SILENCE_PARAGRAPH_BREAK_MS
            else -> SILENCE_INTRA_PARAGRAPH_MS
        }

        return DirectedPerformance(
            role = role,
            effectiveSpeed = effectiveSpeed,
            postSilenceMs = postSilenceMs,
            isExpressive = role == SentenceRole.DIALOGUE || role == SentenceRole.EXCLAMATION
        )
    }

    /**
     * Generate PCM 16-bit mono silence bytes at the specified sample rate.
     */
    fun generateSilencePcm(durationMs: Long, sampleRate: Int = 24000): ByteArray {
        if (durationMs <= 0) return ByteArray(0)
        val numSamples = ((sampleRate.toLong() * durationMs) / 1000).toInt()
        // 16-bit PCM = 2 bytes per sample (initialized to 0x00 silence)
        return ByteArray(numSamples * 2)
    }
}
