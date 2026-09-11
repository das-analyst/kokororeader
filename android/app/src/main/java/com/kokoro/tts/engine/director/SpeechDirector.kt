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
        val isExpressive: Boolean,
        val donorVoice: String? = null,
        val blendWeight: Float = 0.0f
    )

    // Base silence constants (in milliseconds)
    const val SILENCE_INTRA_PARAGRAPH_MS = 250L   // Breath pause between standard sentences (0.25s)
    const val SILENCE_DIALOGUE_MS = 450L          // Natural pause after spoken dialogue (0.45s)
    const val SILENCE_QUESTION_MS = 500L          // Deliberate pause after question (0.50s)
    const val SILENCE_INTERRUPTED_MS = 200L       // Abrupt cut-off pause when speech ends with em-dash (0.20s)
    const val SILENCE_PARAGRAPH_BREAK_MS = 1400L  // Prominent, clearly noticeable pause between paragraphs (1.4s)
    const val SILENCE_CHAPTER_END_MS = 2400L      // Deep dramatic pause between chapters (2.4s)

    // Dialogue emotional cue detectors
    private val WHISPER_REGEX = Regex("(?i)\\b(whispered|whispering|whisper|murmured|murmuring|breathed|muttered|gasped|softly|quietly|hushed)\\b")
    private val SHOUT_REGEX = Regex("(?i)\\b(shouted|shouting|screamed|screaming|yelled|yelling|cried|roared|bellowed|called out|demanded|frantically|angrily|desperately)\\b")
    private val SOLEMN_REGEX = Regex("(?i)\\b(sighed|sighing|mourned|groaned|wept|weeping|grimly|solemnly|gravely|sadly)\\b")

    /**
     * Direct the performance of a sentence given its context and active speaker profile.
     *
     * @param sentence The sentence item with text, dialogue, and paragraph flags
     * @param isLastSentenceInChapter True if this sentence ends the chapter
     * @param baseSpeed The user-configured playback speed (e.g. 1.0f)
     * @param baseVoice The currently active voice ID (e.g. "af_heart", "am_onyx")
     */
    fun direct(
        sentence: SentenceItem,
        isLastSentenceInChapter: Boolean = false,
        baseSpeed: Float = 1.0f,
        baseVoice: String = "af_heart"
    ): DirectedPerformance {
        val text = sentence.text.trim()
        val cleanEnd = text.trimEnd('"', '”', '’', '\'', ' ', '\t')

        val isHeading = text.startsWith("CHAPTER", ignoreCase = true) ||
                text.startsWith("#") ||
                text.matches(Regex("(?i)^(?:Chapter|Section|Part|Book)\\s+\\d+.*"))

        val isExclamation = cleanEnd.endsWith("!") || text.contains("!\"") || text.contains("!”") || text.contains("!'")
        val isQuestion = cleanEnd.endsWith("?") || text.contains("?\"") || text.contains("?”") || text.contains("?'")
        val isInterrupted = cleanEnd.endsWith("—") || cleanEnd.endsWith("--") || text.contains("—\"") || text.contains("—”")

        // 1. Classify role
        val role = when {
            isHeading -> SentenceRole.HEADING
            sentence.isDialogue -> SentenceRole.DIALOGUE
            isExclamation -> SentenceRole.EXCLAMATION
            isQuestion -> SentenceRole.QUESTION
            else -> SentenceRole.NARRATION
        }

        // 2. Classify emotional prosody cues
        val isWhisper = sentence.isDialogue && WHISPER_REGEX.containsMatchIn(text)
        val isShout = (sentence.isDialogue && (SHOUT_REGEX.containsMatchIn(text) || (isExclamation && text.uppercase() == text && text.length > 3))) ||
                (isExclamation && SHOUT_REGEX.containsMatchIn(text))
        val isSolemn = SOLEMN_REGEX.containsMatchIn(text)

        // Select complementary emotional donor voice based on gender
        val isFemale = baseVoice.startsWith("af_") || baseVoice.startsWith("bf_")
        val (donorVoice, blendWeight) = when {
            isWhisper -> Pair(if (isFemale) "af_nicole" else "am_michael", 0.50f)
            isShout -> Pair(if (isFemale) "af_bella" else "am_adam", 0.45f)
            isSolemn -> Pair(if (isFemale) "af_sarah" else "am_onyx", 0.35f)
            role == SentenceRole.DIALOGUE && isExclamation -> Pair(if (isFemale) "af_bella" else "am_adam", 0.30f)
            else -> Pair(null, 0.0f)
        }

        // 3. Compute dynamic tempo modulation (micro-cadence with expressive room)
        val speedMultiplier = when {
            role == SentenceRole.HEADING -> 0.85f      // Headings spoken with deliberate gravitas (-15%)
            isWhisper -> 0.90f                         // Whispered dialogue: slowed, cautious, intimate (-10%)
            isShout -> 1.14f                           // Shouting / panicked urgency (+14%)
            role == SentenceRole.DIALOGUE -> when {
                isExclamation -> 1.12f                 // Urgent / excited dialogue (+12%)
                isQuestion -> 0.94f                    // Inquiring dialogue question (-6%)
                else -> 1.08f                         // Animated, conversational spoken dialogue (+8%)
            }
            role == SentenceRole.EXCLAMATION -> 1.12f // Urgency / excitement in narrative (+12%)
            role == SentenceRole.QUESTION -> 0.92f    // Thoughtful, reflective upward cadence (-8%)
            isSolemn -> 0.93f                         // Somber, mourning reflection (-7%)
            else -> 1.00f                             // Steady natural pace
        }

        // Nuanced pacing based on clause weight / sentence length
        val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val lengthMultiplier = when {
            wordCount in 1..4 && role != SentenceRole.HEADING -> 1.03f // Short punchy sentence (+3%)
            wordCount >= 28 && role != SentenceRole.HEADING -> 0.96f  // Long complex narrative sentence (-4%)
            else -> 1.00f
        }

        // Parenthetical aside and em-dash reflective room
        val hasAsideOrEmDash = (text.contains("—") || text.contains("(")) && role != SentenceRole.HEADING && !isWhisper && !isShout
        val asideMultiplier = if (hasAsideOrEmDash) 0.97f else 1.00f

        val effectiveSpeed = (baseSpeed * speedMultiplier * lengthMultiplier * asideMultiplier).coerceIn(0.5f, 2.5f)

        // 4. Compute post-sentence silence duration
        val postSilenceMs = when {
            isLastSentenceInChapter -> SILENCE_CHAPTER_END_MS
            isInterrupted -> SILENCE_INTERRUPTED_MS
            sentence.isParagraphEnd -> SILENCE_PARAGRAPH_BREAK_MS
            role == SentenceRole.HEADING -> SILENCE_PARAGRAPH_BREAK_MS
            isSolemn -> 650L
            isWhisper -> 550L
            isQuestion -> SILENCE_QUESTION_MS
            isShout || isExclamation || role == SentenceRole.DIALOGUE -> SILENCE_DIALOGUE_MS
            else -> SILENCE_INTRA_PARAGRAPH_MS
        }

        return DirectedPerformance(
            role = role,
            effectiveSpeed = effectiveSpeed,
            postSilenceMs = postSilenceMs,
            isExpressive = role == SentenceRole.DIALOGUE || role == SentenceRole.EXCLAMATION || isExclamation || isWhisper || isShout,
            donorVoice = donorVoice,
            blendWeight = blendWeight
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
