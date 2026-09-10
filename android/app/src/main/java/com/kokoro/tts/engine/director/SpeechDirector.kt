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
    const val SILENCE_INTRA_PARAGRAPH_MS = 250L   // Breath pause between standard sentences (0.25s)
    const val SILENCE_DIALOGUE_MS = 450L          // Natural pause after spoken dialogue (0.45s)
    const val SILENCE_QUESTION_MS = 500L          // Deliberate pause after question (0.50s)
    const val SILENCE_PARAGRAPH_BREAK_MS = 1400L  // Prominent, clearly noticeable pause between paragraphs (1.4s)
    const val SILENCE_CHAPTER_END_MS = 2400L      // Deep dramatic pause between chapters (2.4s)

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
        val cleanEnd = text.trimEnd('"', '”', '’', '\'', ' ', '\t')

        val isHeading = text.startsWith("CHAPTER", ignoreCase = true) ||
                text.startsWith("#") ||
                text.matches(Regex("(?i)^(?:Chapter|Section|Part|Book)\\s+\\d+.*"))

        val isExclamation = cleanEnd.endsWith("!") || text.contains("!\"") || text.contains("!”") || text.contains("!'")
        val isQuestion = cleanEnd.endsWith("?") || text.contains("?\"") || text.contains("?”") || text.contains("?'")

        // 1. Classify role
        val role = when {
            isHeading -> SentenceRole.HEADING
            sentence.isDialogue -> SentenceRole.DIALOGUE
            isExclamation -> SentenceRole.EXCLAMATION
            isQuestion -> SentenceRole.QUESTION
            else -> SentenceRole.NARRATION
        }

        // 2. Compute dynamic tempo modulation (micro-cadence with expressive room)
        val speedMultiplier = when (role) {
            SentenceRole.HEADING -> 0.85f      // Headings spoken with deliberate gravitas (-15%)
            SentenceRole.DIALOGUE -> when {
                isExclamation -> 1.12f          // Urgent / excited dialogue (+12%)
                isQuestion -> 0.94f             // Inquiring dialogue question (-6%)
                else -> 1.08f                  // Animated, conversational spoken dialogue (+8%)
            }
            SentenceRole.EXCLAMATION -> 1.12f  // Urgency / excitement in narrative (+12%)
            SentenceRole.QUESTION -> 0.92f     // Thoughtful, reflective upward cadence (-8%)
            SentenceRole.NARRATION -> 1.00f    // Steady natural pace
        }

        // Nuanced pacing based on clause weight / sentence length
        val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val lengthMultiplier = when {
            wordCount in 1..4 && role != SentenceRole.HEADING -> 1.03f  // Short punchy sentence (+3%)
            wordCount >= 28 && role != SentenceRole.HEADING -> 0.96f   // Long complex narrative sentence (-4%)
            else -> 1.00f
        }

        val effectiveSpeed = (baseSpeed * speedMultiplier * lengthMultiplier).coerceIn(0.5f, 2.5f)

        // 3. Compute post-sentence silence duration
        val postSilenceMs = when {
            isLastSentenceInChapter -> SILENCE_CHAPTER_END_MS
            sentence.isParagraphEnd -> SILENCE_PARAGRAPH_BREAK_MS
            role == SentenceRole.HEADING -> SILENCE_PARAGRAPH_BREAK_MS
            isQuestion -> SILENCE_QUESTION_MS
            isExclamation || role == SentenceRole.DIALOGUE -> SILENCE_DIALOGUE_MS
            else -> SILENCE_INTRA_PARAGRAPH_MS
        }

        return DirectedPerformance(
            role = role,
            effectiveSpeed = effectiveSpeed,
            postSilenceMs = postSilenceMs,
            isExpressive = role == SentenceRole.DIALOGUE || role == SentenceRole.EXCLAMATION || isExclamation
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
