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

        // Collapse any leftover spaces
        return processed.replace(Regex("\\s+"), " ").trim()
    }
}
