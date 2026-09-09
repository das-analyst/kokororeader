package com.kokoro.tts.engine.normalizer

/**
 * Master Text Normalization & Speech Refinement Engine for Kokoro TTS.
 * Follows NVIDIA NeMo's Text Normalization taxonomy to transform written English
 * into natural spoken forms with zero hallucinations and sub-millisecond latency.
 */
object TextNormalizer {

    /**
     * Complete speech normalization: artifacts -> Roman numerals -> currency/units -> abbreviations -> numbers.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return text

        // Stage 1: Clean PDF/EPUB artifacts, ligatures, citations
        var processed = ArtifactCleaner.clean(text)

        // Stage 2: Roman Numerals (Monarchs & Section headers)
        processed = RomanNumeralNormalizer.normalize(processed)

        // Stage 3: Currencies, Percentages, Temperatures, Units
        processed = MeasureCurrencyNormalizer.normalize(processed)

        // Stage 4: Abbreviations, Titles, Time, Saint/Street disambiguation
        processed = AbbreviationNormalizer.normalize(processed)

        // Stage 5: Cardinals, Ordinals, Decimals, Fractions, and Years
        processed = NumberToWords.normalizeNumbersInText(processed)

        // Clean up excessive horizontal whitespace while preserving newlines
        processed = processed.replace(Regex("[ \\t]+"), " ")
        // Collapse excessive newlines (3+ down to 2)
        processed = processed.replace(Regex("(\\r?\\n)[ \\t]*(\\r?\\n)+"), "\n\n")
        return processed.trim()
    }

    /**
     * Normalizes an entire book's text while strictly preserving document structure
     * (Title `# ...`, Author `Author: ...`, and chapter headings `CHAPTER X: ...`).
     */
    fun normalizeBookContent(bookContent: String): String {
        if (bookContent.isBlank()) return bookContent

        val lines = bookContent.lines()
        val result = StringBuilder()
        val paragraphBuilder = StringBuilder()

        fun flushParagraph() {
            if (paragraphBuilder.isNotEmpty()) {
                val normalized = normalize(paragraphBuilder.toString().trim())
                if (normalized.isNotEmpty()) {
                    result.append(normalized).append("\n\n")
                }
                paragraphBuilder.clear()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            // Preserve structural markers unchanged
            if (trimmed.startsWith("# ") ||
                trimmed.startsWith("Author:", ignoreCase = true) ||
                trimmed.matches(Regex("(?i)^CHAPTER\\s+\\d+.*"))) {
                flushParagraph()
                result.append(trimmed).append("\n\n")
            } else if (trimmed.isEmpty()) {
                flushParagraph()
            } else {
                if (paragraphBuilder.isNotEmpty()) {
                    paragraphBuilder.append(" ")
                }
                paragraphBuilder.append(trimmed)
            }
        }
        flushParagraph()
        return result.toString().trim()
    }
}
