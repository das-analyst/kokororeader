package com.kokoro.tts.engine

import android.content.Context
import android.util.Log

/**
 * Converts English text to IPA phonemes compatible with the Kokoro model.
 * Handles text pre-normalization and post-processes raw eSpeak-ng IPA output.
 */
class PhonemeConverter(
    context: Context,
    private val tokenizer: Tokenizer
) {
    companion object {
        private const val TAG = "PhonemeConverter"
    }

    private val espeakBridge = EspeakBridge(context)

    init {
        espeakBridge.initialize()
    }

    fun release() {
        espeakBridge.terminate()
    }

    /**
     * Converts natural text into model-ready IPA phonemes.
     */
    fun convertTextToPhonemes(text: String): String {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return ""

        val rawIpa = espeakBridge.textToPhonemes(normalized)
        if (rawIpa.isBlank()) {
            Log.w(TAG, "eSpeak returned empty phonemes for: $normalized")
            return ""
        }
        return postProcess(rawIpa)
    }

    /**
     * Normalize numbers, times, and symbols that eSpeak may mispronounce.
     */
    fun normalizeText(text: String): String {
        var t = text.trim()

        // Time format: 3:30pm -> 3 30 pm, 12:00 -> 12 o'clock
        t = Regex("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?").replace(t) { match ->
            val hour = match.groupValues[1]
            val min = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3]
            val minPart = when {
                min == 0 -> "o'clock"
                min < 10 -> "oh $min"
                else -> "$min"
            }
            val suffix = if (ampm.isNotBlank()) " $ampm" else ""
            "$hour $minPart$suffix"
        }

        // Currency: $10.50 -> 10 dollars and 50 cents
        t = Regex("\\$\\s*(\\d+)\\.(\\d{2})\\b").replace(t) { match ->
            val dollars = match.groupValues[1]
            val cents = match.groupValues[2].toIntOrNull() ?: 0
            if (cents == 0) "$dollars dollars" else "$dollars dollars and $cents cents"
        }

        // Currency: $100 -> 100 dollars
        t = Regex("\\$\\s*(\\d+)\\b").replace(t) { match ->
            "${match.groupValues[1]} dollars"
        }

        // Number ranges: 5-10 -> 5 to 10
        t = Regex("(?<=\\d)-(?=\\d)").replace(t, " to ")

        return t
    }

    /**
     * Standardizes IPA phonemes for Kokoro compatibility.
     */
    private fun postProcess(phonemes: String): String {
        // Strip eSpeak tie character
        var result = phonemes.replace("^", "")

        // Kokoro IPA symbol alignments
        result = result.replace("r", "\u0279")           // r -> ɹ
        result = result.replace("x", "k")                // x -> k
        result = result.replace("\u00E7", "k")            // ç -> k
        result = result.replace("\u026C", "l")            // ɬ -> l
        result = result.replace("\u02B2", "j")            // ʲ -> j
        result = result.replace("\u025A", "\u0259\u0279") // ɚ -> əɹ
        result = result.replace("\u0250", "\u0259")       // ɐ -> ə
        result = result.replace("\u0303", "")             // Strip combining tilde
        result = result.replace("\u0329", "")             // Strip syllabic mark
        result = result.replace("$", "")                  // Strip pad character

        // Filter down to supported vocabulary characters
        result = tokenizer.filterKnown(result)

        return result.trim()
    }
}
