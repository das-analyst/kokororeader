package com.kokoro.tts.engine

import java.util.regex.Pattern

/**
 * Splits text into sentences suitable for streaming TTS synthesis.
 * Protects common abbreviations from causing premature sentence breaks.
 */
object TextSplitter {

    private val ABBREVIATIONS = listOf(
        "Mr", "Mrs", "Ms", "Dr", "Prof", "Sr", "Jr", "vs", "etc", "i.e", "e.g",
        "St", "Capt", "Gen", "Col", "Lt", "Sgt", "Rev", "Hon"
    )

    private val SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+")

    /**
     * Split a block of text into sentences with abbreviation protection.
     */
    fun splitIntoSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Normalize whitespace and newlines into single spaces
        var cleaned = trimmed.replace(Regex("\\s+"), " ")

        // Replace common abbreviations with unique placeholders
        val placeholderMap = mutableMapOf<String, String>()
        for ((idx, abbrev) in ABBREVIATIONS.withIndex()) {
            val placeholder = "__ABBR_${idx}__"
            val pattern = Regex("\\b${Regex.escape(abbrev)}\\.")
            if (pattern.containsMatchIn(cleaned)) {
                placeholderMap[placeholder] = "$abbrev."
                cleaned = cleaned.replace(pattern, placeholder)
            }
        }

        // Split on sentence boundaries
        val rawSentences = SENTENCE_BOUNDARY.split(cleaned).filter { it.isNotBlank() }

        // Restore original abbreviations
        return rawSentences.map { sentence ->
            var restored = sentence
            for ((placeholder, original) in placeholderMap) {
                restored = restored.replace(placeholder, original)
            }
            restored.trim()
        }
    }
}
