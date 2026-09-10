package com.kokoro.tts.engine

import android.content.Context
import android.util.Log
import com.kokoro.tts.engine.normalizer.TextNormalizer
import com.kokoro.tts.reader.manager.PronunciationManager

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

    private val appContext = context.applicationContext
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
     * Normalize written text into natural spoken words (NeMo TN architecture),
     * with custom pronunciation dictionary rules applied first.
     */
    fun normalizeText(text: String): String {
        val withPronunciationRules = PronunciationManager.getInstance(appContext).applyRules(text)
        return TextNormalizer.normalize(withPronunciationRules)
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
