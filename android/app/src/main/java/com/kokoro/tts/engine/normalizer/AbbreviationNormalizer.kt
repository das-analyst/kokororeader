package com.kokoro.tts.engine.normalizer

/**
 * Normalizes common English abbreviations and titles with context-aware disambiguation.
 */
object AbbreviationNormalizer {

    private val TITLES = listOf(
        Pair(Regex("\\bDr\\.(?=\\s+[A-Z])"), "Doctor"),
        Pair(Regex("\\bMr\\.(?=\\s+[A-Z])"), "Mister"),
        Pair(Regex("\\bMrs\\.(?=\\s+[A-Z])"), "Missus"),
        Pair(Regex("\\bMs\\.(?=\\s+[A-Z])"), "Miz"),
        Pair(Regex("\\bProf\\.(?=\\s+[A-Z])"), "Professor"),
        Pair(Regex("\\bCapt\\.(?=\\s+[A-Z])"), "Captain"),
        Pair(Regex("\\bCol\\.(?=\\s+[A-Z])"), "Colonel"),
        Pair(Regex("\\bGen\\.(?=\\s+[A-Z])"), "General"),
        Pair(Regex("\\bLt\\.(?=\\s+[A-Z])"), "Lieutenant"),
        Pair(Regex("\\bSgt\\.(?=\\s+[A-Z])"), "Sergeant"),
        Pair(Regex("\\bRev\\.(?=\\s+[A-Z])"), "Reverend"),
        Pair(Regex("\\bSen\\.(?=\\s+[A-Z])"), "Senator"),
        Pair(Regex("\\bGov\\.(?=\\s+[A-Z])"), "Governor"),
        Pair(Regex("\\bJr\\.(?=[\\s,;!?]|$)"), "Junior"),
        Pair(Regex("\\bSr\\.(?=[\\s,;!?]|$)"), "Senior")
    )

    private val COMMON_SHORTHANDS = listOf(
        Pair(Regex("(?i)\\be\\.g\\.,?\\s*"), "for example, "),
        Pair(Regex("(?i)\\bi\\.e\\.,?\\s*"), "that is, "),
        Pair(Regex("(?i)\\betc\\.(?=[\\s,;!?]|$)"), "et cetera"),
        Pair(Regex("(?i)\\bvs\\.(?=[\\s,;!?]|$)"), "versus"),
        Pair(Regex("(?i)\\bapprox\\.(?=[\\s,;!?]|$)"), "approximately")
    )

    fun normalize(text: String): String {
        var result = text

        // 1. Time formats: 3:30pm -> 3 30 pm, 12:00 -> 12 o'clock
        result = Regex("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?").replace(result) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 12
            val min = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3]

            val hourWord = NumberToWords.convertCardinal(hour.toLong())
            val minWord = when {
                min == 0 -> "o'clock"
                min < 10 -> "oh ${NumberToWords.convertCardinal(min.toLong())}"
                else -> NumberToWords.convertCardinal(min.toLong())
            }
            val ampmPart = if (ampm.isNotBlank()) " ${ampm.lowercase().map { "$it " }.joinToString("").trim()}" else ""
            "$hourWord $minWord$ampmPart"
        }

        // 2. Disambiguate "St.":
        // A) "St. [Name]" -> "Saint [Name]" (when followed by a capitalized word and not preceded by street indicator)
        result = Regex("\\bSt\\.\\s+([A-Z][a-z]+)").replace(result) { m ->
            val nextWord = m.groupValues[1]
            "Saint $nextWord"
        }

        // B) "[Name/Number] St." -> "[Name/Number] Street"
        result = Regex("(?i)\\b([A-Za-z0-9]+(?:th|rd|nd|st)?)\\s+St\\.(?=[\\s,;!?]|$)").replace(result) { m ->
            val prevWord = m.groupValues[1]
            if (prevWord.equals("saint", ignoreCase = true)) {
                m.value
            } else {
                "$prevWord Street"
            }
        }

        // 3. Apply standard formal titles
        for ((regex, replacement) in TITLES) {
            result = regex.replace(result, replacement)
        }

        // 4. Common shorthands
        for ((regex, replacement) in COMMON_SHORTHANDS) {
            result = regex.replace(result, replacement)
        }

        // 5. Name initials speech normalization (e.g. "Mr. C. M. Park" -> "Mister C M Park", "Arthur C. Clarke" -> "Arthur C Clarke")
        // Removes periods after single-letter initials before names so Kokoro/eSpeak pronounces them fluidly without pause
        val chainedPattern = Regex("\\b([A-Z])\\.\\s*(?=[A-Z]\\.)")
        while (chainedPattern.containsMatchIn(result)) {
            result = chainedPattern.replace(result, "$1 ")
        }
        val surnameInitialPattern = Regex("\\b([A-Z])\\.\\s+(?!$SENTENCE_STARTERS)(?=[A-Z][a-z]+)")
        result = surnameInitialPattern.replace(result, "$1 ")

        return result
    }

    private val SENTENCE_STARTERS = listOf(
        "The", "This", "That", "These", "Those", "He", "She", "It", "They", "We", "You", "I",
        "There", "Then", "Now", "When", "Where", "Why", "What", "How", "If", "Although",
        "Because", "After", "Before", "Since", "While", "So", "And", "But", "In", "On",
        "At", "To", "From", "By", "With", "As", "An", "A"
    ).joinToString("|", prefix = "(?:", postfix = ")\\b")
}
