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

    enum class TemperamentPreset(
        val id: String,
        val title: String,
        val description: String,
        val baseSpeedFactor: Float,
        val varianceFactor: Float,
        val defaultIntensity: Float,
        val pauseMultiplier: Float,
        val narratorBlendWeight: Float,
        val acousticGain: Float = 1.0f
    ) {
        NATURAL("natural", "Natural Storyteller", "Balanced, authentic audiobook cadence", 1.00f, 1.00f, 0.60f, 1.00f, 0.00f, 1.00f),
        CALM("calm", "Calm & Measured", "Grounded, warm chest-resonance & unhurried pauses", 0.94f, 0.40f, 0.50f, 1.35f, 0.50f, 1.00f),
        THEATRICAL("theatrical", "Theatrical & Vivid", "Dramatic dynamic range, vivid projection & suspense pauses", 1.02f, 1.60f, 0.85f, 1.35f, 0.50f, 1.05f),
        BEDTIME("bedtime", "Bedtime / Drift Off", "Soft breathy whisper, gentle volume & deep soothing pauses", 0.88f, 0.25f, 0.40f, 1.60f, 0.60f, 0.82f),
        SPRINT("sprint", "Sprint / Study", "Crisp clarity, rapid throughput & snappy tight pauses", 1.14f, 0.20f, 0.40f, 0.50f, 0.45f, 1.00f);

        companion object {
            fun fromId(id: String?): TemperamentPreset = values().find { it.id.equals(id, ignoreCase = true) } ?: NATURAL
        }
    }

    data class DirectorConfig(
        val expressionIntensity: Float = 1.0f,
        val temperament: TemperamentPreset = TemperamentPreset.NATURAL,
        val enableDualTone: Boolean = false,
        val windDownProgress: Float = 0.0f
    )

    data class DirectedPerformance(
        val role: SentenceRole,
        val effectiveSpeed: Float,
        val postSilenceMs: Long,
        val isExpressive: Boolean,
        val donorVoice: String? = null,
        val blendWeight: Float = 0.0f,
        val gain: Float = 1.0f
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
     * @param config The performance and cadence configuration (intensity, temperament, dual-tone, wind-down)
     */
    fun direct(
        sentence: SentenceItem,
        isLastSentenceInChapter: Boolean = false,
        baseSpeed: Float = 1.0f,
        baseVoice: String = "af_heart",
        config: DirectorConfig = DirectorConfig()
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

        // 2. Classify emotional prosody cues (suppressed during bedtime or late wind-down)
        val isBedtimeOrWindDown = config.temperament == TemperamentPreset.BEDTIME || config.windDownProgress > 0.30f
        val isWhisper = sentence.isDialogue && WHISPER_REGEX.containsMatchIn(text)
        val rawIsShout = (sentence.isDialogue && (SHOUT_REGEX.containsMatchIn(text) || (isExclamation && text.uppercase() == text && text.length > 3))) ||
                (isExclamation && SHOUT_REGEX.containsMatchIn(text))
        val isShout = rawIsShout && !isBedtimeOrWindDown
        val isSolemn = SOLEMN_REGEX.containsMatchIn(text)

        val isFemale = baseVoice.startsWith("af_") || baseVoice.startsWith("bf_")

        // 2b. Determine the distinct baseline narrator persona donor for this temperament (colors standard narration)
        val (personaDonorVoice, personaBlendWeight) = when (config.temperament) {
            TemperamentPreset.CALM -> Pair(
                if (isFemale) (if (baseVoice == "af_sarah") "af_heart" else "af_sarah")
                else (if (baseVoice == "am_echo") "am_onyx" else "am_echo"),
                config.temperament.narratorBlendWeight
            )
            TemperamentPreset.THEATRICAL -> Pair(
                if (isFemale) (if (baseVoice == "af_bella") "af_sky" else "af_bella")
                else (if (baseVoice == "am_adam") "am_eric" else "am_adam"),
                config.temperament.narratorBlendWeight
            )
            TemperamentPreset.BEDTIME -> Pair(
                if (isFemale) (if (baseVoice == "af_nicole") "af_heart" else "af_nicole")
                else (if (baseVoice == "am_michael") "am_onyx" else "am_michael"),
                config.temperament.narratorBlendWeight
            )
            TemperamentPreset.SPRINT -> Pair(
                if (isFemale) (if (baseVoice == "af_sky") "af_bella" else "af_sky")
                else (if (baseVoice == "am_eric") "am_adam" else "am_eric"),
                config.temperament.narratorBlendWeight
            )
            TemperamentPreset.NATURAL -> Pair(null, 0.0f)
        }

        // 2c. Select complementary emotional donor voice
        var (donorVoice, rawBlendWeight) = when {
            isWhisper -> Pair(if (isFemale) "af_nicole" else "am_michael", 0.50f)
            isShout -> Pair(if (isFemale) "af_bella" else "am_adam", 0.45f)
            isSolemn -> Pair(if (isFemale) "af_sarah" else "am_onyx", 0.35f)
            role == SentenceRole.DIALOGUE && isExclamation && !isBedtimeOrWindDown -> Pair(if (isFemale) "af_bella" else "am_adam", 0.30f)
            else -> Pair(null, 0.0f)
        }

        // Dual-Tone Dialogue distinction: if no emotional cue donor, apply conversational character lift
        if (donorVoice == null && config.enableDualTone && role == SentenceRole.DIALOGUE) {
            donorVoice = if (isFemale) "af_bella" else "am_adam"
            rawBlendWeight = 0.18f
        }

        // 2d. Progressive Wind-Down breathy whisper transition:
        // As windDownProgress advances across the session, progressively infuse soft breathy tones (af_nicole / am_michael)
        val windDownDonor = if (isFemale) (if (baseVoice == "af_nicole") "af_heart" else "af_nicole")
                            else (if (baseVoice == "am_michael") "am_onyx" else "am_michael")
        val windDownBreathyWeight = 0.55f * config.windDownProgress.coerceIn(0.0f, 1.0f)

        // If no cue donor (standard narration), apply the temperament persona or wind-down breathy donor
        val isPersonaNarrator = donorVoice == null && (personaDonorVoice != null || windDownBreathyWeight > 0.05f)
        if (isPersonaNarrator) {
            if (config.windDownProgress > 0.35f || personaDonorVoice == null) {
                donorVoice = windDownDonor
                rawBlendWeight = maxOf(personaBlendWeight, windDownBreathyWeight)
            } else {
                donorVoice = personaDonorVoice
                rawBlendWeight = personaBlendWeight
            }
        }

        // For emotional cues and dual-tone, scale blend weight directly with expression intensity.
        // For baseline temperament persona coloring, preserve the signature vocal timbre identity (with gentle nuance).
        val blendWeight = if (isPersonaNarrator) {
            (rawBlendWeight * (0.85f + 0.15f * config.expressionIntensity)).coerceIn(0.0f, 1.0f)
        } else {
            (rawBlendWeight * config.expressionIntensity).coerceIn(0.0f, 1.0f)
        }
        val finalDonorVoice = if (blendWeight > 0.01f) donorVoice else null

        // 3. Compute dynamic tempo modulation (micro-cadence with expressive room)
        val rawSpeedMultiplier = when {
            role == SentenceRole.HEADING -> 0.85f      // Headings spoken with deliberate gravitas (-15%)
            isWhisper -> 0.90f                         // Whispered dialogue: slowed, cautious, intimate (-10%)
            isShout -> 1.14f                           // Shouting / panicked urgency (+14%)
            role == SentenceRole.DIALOGUE -> when {
                isExclamation && !isBedtimeOrWindDown -> 1.12f // Urgent / excited dialogue (+12%)
                isQuestion -> 0.94f                    // Inquiring dialogue question (-6%)
                else -> 1.08f                         // Animated, conversational spoken dialogue (+8%)
            }
            role == SentenceRole.EXCLAMATION && !isBedtimeOrWindDown -> 1.12f // Urgency / excitement in narrative (+12%)
            role == SentenceRole.QUESTION -> 0.92f    // Thoughtful, reflective upward cadence (-8%)
            isSolemn -> 0.93f                         // Somber, mourning reflection (-7%)
            else -> 1.00f                             // Steady natural pace
        }

        // Scale cadence variance according to expression intensity, temperament, and wind-down flattening
        val windDownVarianceSuppression = 1.0f - (0.60f * config.windDownProgress.coerceIn(0.0f, 1.0f))
        val varianceScale = (config.expressionIntensity * config.temperament.varianceFactor * windDownVarianceSuppression).coerceIn(0.0f, 2.0f)
        val scaledSpeedMultiplier = 1.0f + (rawSpeedMultiplier - 1.0f) * varianceScale

        // Nuanced pacing based on clause weight / sentence length
        val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val lengthMultiplier = when {
            wordCount in 1..4 && role != SentenceRole.HEADING -> 1.0f + 0.03f * varianceScale // Short punchy sentence
            wordCount >= 28 && role != SentenceRole.HEADING -> 1.0f - 0.04f * varianceScale  // Long complex narrative sentence
            else -> 1.00f
        }

        // Parenthetical aside and em-dash reflective room
        val hasAsideOrEmDash = (text.contains("—") || text.contains("(")) && role != SentenceRole.HEADING && !isWhisper && !isShout
        val asideMultiplier = if (hasAsideOrEmDash) (1.0f - 0.03f * varianceScale) else 1.00f

        // Base speed adjustments: Temperament baseline + Sleep timer wind-down progressive deceleration (up to -20%)
        val windDownSpeedFactor = 1.0f - (0.20f * config.windDownProgress.coerceIn(0.0f, 1.0f))
        val effectiveBaseSpeed = baseSpeed * config.temperament.baseSpeedFactor * windDownSpeedFactor

        val effectiveSpeed = (effectiveBaseSpeed * scaledSpeedMultiplier * lengthMultiplier * asideMultiplier).coerceIn(0.5f, 2.5f)

        // 4. Compute post-sentence silence duration (progressively widening up to +100% during wind-down)
        val rawPostSilenceMs = when {
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

        val windDownPauseFactor = 1.0f + (1.00f * config.windDownProgress.coerceIn(0.0f, 1.0f))
        val postSilenceMs = (rawPostSilenceMs * config.temperament.pauseMultiplier * windDownPauseFactor).toLong()

        val isExpressive = (role == SentenceRole.DIALOGUE || role == SentenceRole.EXCLAMATION || isExclamation || isWhisper || isShout) &&
                (config.expressionIntensity > 0.05f)

        // 5. Compute acoustic gain factor (dynamics shaping + progressive wind-down decrescendo down to 0.65x)
        val baseGain = config.temperament.acousticGain
        val windDownGainFactor = 1.0f - (0.35f * config.windDownProgress.coerceIn(0.0f, 1.0f))
        val rawEffectiveGain = baseGain * windDownGainFactor
        val effectiveGain = when {
            isWhisper -> (rawEffectiveGain * 0.85f).coerceIn(0.35f, 1.2f)
            isShout -> (rawEffectiveGain * 1.08f).coerceIn(0.35f, 1.2f)
            else -> rawEffectiveGain.coerceIn(0.35f, 1.2f)
        }

        return DirectedPerformance(
            role = role,
            effectiveSpeed = effectiveSpeed,
            postSilenceMs = postSilenceMs,
            isExpressive = isExpressive,
            donorVoice = finalDonorVoice,
            blendWeight = blendWeight,
            gain = effectiveGain
        )
    }

    /**
     * Concise delivery & persona description for display in UI.
     */
    fun getPersonaDescription(baseVoice: String, temperament: TemperamentPreset): String {
        val isFemale = baseVoice.startsWith("af_") || baseVoice.startsWith("bf_")
        return when (temperament) {
            TemperamentPreset.NATURAL -> "Pure Voice • Natural Flow"
            TemperamentPreset.CALM -> if (isFemale) "Sarah Blend (50%) • Warm & Grounded Resonance" else "Echo Blend (50%) • Calm & Mellow Resonance"
            TemperamentPreset.THEATRICAL -> if (isFemale) "Bella Blend (50%) • Vivid Stage Projection (+0.5dB)" else "Adam Blend (50%) • Dynamic Actor Presence (+0.5dB)"
            TemperamentPreset.BEDTIME -> if (isFemale) "Nicole Blend (60%) • Soft Breathy Whisper (-1.8dB)" else "Michael Blend (60%) • Hushed Pillow-Talk (-1.8dB)"
            TemperamentPreset.SPRINT -> if (isFemale) "Sky Blend (45%) • Crisp & Articulate Diction" else "Eric Blend (45%) • Clear Conversational Diction"
        }
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
