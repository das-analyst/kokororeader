package com.kokoro.tts.engine.normalizer

/**
 * Context-aware Roman Numeral normalizer.
 * Disambiguates between:
 * - Monarchs/Personages: "Henry VIII" -> "Henry the Eighth"
 * - Structural headings: "Chapter IV" -> "Chapter Four", "Act II" -> "Act Two"
 */
object RomanNumeralNormalizer {

    private val ROMAN_MAP = mapOf(
        'M' to 1000,
        'D' to 500,
        'C' to 100,
        'L' to 50,
        'X' to 10,
        'V' to 5,
        'I' to 1
    )

    fun romanToInt(roman: String): Int? {
        val s = roman.trim().uppercase()
        if (s.isEmpty() || !s.matches(Regex("^[IVXLCDM]+$"))) return null

        var total = 0
        var prevValue = 0

        for (i in s.length - 1 downTo 0) {
            val value = ROMAN_MAP[s[i]] ?: return null
            if (value < prevValue) {
                total -= value
            } else {
                total += value
                prevValue = value
            }
        }
        return if (total in 1..3999) total else null
    }

    /**
     * Normalizes Roman numerals in text.
     */
    fun normalize(text: String): String {
        var result = text

        // 1. Structural titles: Chapter IV, Act I, Scene III, Part II, Volume V, Book I, Section X
        val structurePattern = Regex(
            "(?i)\\b(Chapter|Act|Scene|Part|Volume|Vol\\.?|Book|Section)\\s+([IVXLCDM]+)\\b"
        )
        result = structurePattern.replace(result) { match ->
            val label = match.groupValues[1]
            val roman = match.groupValues[2]
            val intVal = romanToInt(roman)
            if (intVal != null && intVal <= 100) {
                val cardinalWord = NumberToWords.convertCardinal(intVal.toLong()).replaceFirstChar { it.uppercase() }
                val cleanLabel = if (label.startsWith("vol", ignoreCase = true)) "Volume" else label.lowercase().replaceFirstChar { it.uppercase() }
                "$cleanLabel $cardinalWord"
            } else {
                match.value
            }
        }

        // 2. Monarchs and Personages:
        // Proper names followed by Roman numeral: Henry VIII, Elizabeth II, Louis XIV, Pope Francis I
        val monarchPattern = Regex(
            "\\b([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)\\s+([IVXLCDM]{1,6})\\b"
        )
        val monarchNames = setOf(
            "henry", "elizabeth", "louis", "george", "charles", "richard", "edward", "william",
            "james", "philip", "alexander", "napoleon", "nicholas", "john", "paul", "pope",
            "peter", "catherine", "gregory", "benedict", "leo", "pius", "clemens", "urban",
            "innocent", "stephen", "alfred", "ferdinand", "constantine", "albert"
        )

        result = monarchPattern.replace(result) { match ->
            val name = match.groupValues[1]
            val roman = match.groupValues[2]
            val firstName = name.split(Regex("\\s+")).last().lowercase()

            if (monarchNames.contains(firstName)) {
                val intVal = romanToInt(roman)
                if (intVal != null && intVal <= 50) {
                    val ordinalWord = NumberToWords.convertOrdinal(intVal.toLong()).replaceFirstChar { it.uppercase() }
                    "$name the $ordinalWord"
                } else {
                    match.value
                }
            } else {
                match.value
            }
        }

        // 3. World Wars & Major Historical Events
        result = result.replace(Regex("(?i)\\bWorld War\\s+I\\b"), "World War One")
            .replace(Regex("(?i)\\bWorld War\\s+II\\b"), "World War Two")
            .replace(Regex("(?i)\\bWWI\\b"), "World War One")
            .replace(Regex("(?i)\\bWWII\\b"), "World War Two")

        return result
    }
}
