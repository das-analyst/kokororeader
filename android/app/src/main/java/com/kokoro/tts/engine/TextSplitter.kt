package com.kokoro.tts.engine

import java.util.regex.Pattern

/**
 * Splits text into sentences suitable for streaming TTS synthesis and ebook navigation.
 * Protects common abbreviations, titles, decimals, and name initials (e.g. "Mr. C. M. Park")
 * from causing premature sentence breaks.
 */
object TextSplitter {

    private const val PROTECTED_DOT = '\u2024' // Unicode one-dot leader

    // Comprehensive titles that never end a sentence when followed by a name or initial
    private val TITLES = listOf(
        "Mr", "Mrs", "Ms", "Miss", "Dr", "Prof", "Capt", "Col", "Gen", "Lt", "Lieut",
        "Sgt", "Rev", "Hon", "Messrs", "Mme", "Mlle", "Gov", "Sen", "Rep", "Pres", "Sir", "Lady"
    ).joinToString("|")

    // Common sentence starters indicating a new sentence when following an abbreviation or initial
    private val SENTENCE_STARTERS = listOf(
        "The", "This", "That", "These", "Those", "He", "She", "It", "They", "We", "You", "I",
        "There", "Then", "Now", "When", "Where", "Why", "What", "How", "If", "Although",
        "Because", "After", "Before", "Since", "While", "So", "And", "But", "In", "On",
        "At", "To", "From", "By", "With", "As", "An", "A"
    ).joinToString("|", prefix = "(?:", postfix = ")\\b")

    private val SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+")

    /**
     * Split a block of text into sentences with comprehensive abbreviation, title,
     * decimal, and name initial protection.
     */
    fun splitIntoSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Normalize whitespace and newlines
        var cleaned = trimmed.replace(Regex("[ \\t]+"), " ")
        cleaned = cleaned.replace(Regex("(\\r?\\n)[ \\t]*(\\r?\\n)+"), "\n\n")

        // 1. Protect numbers with decimals: 3.14 -> 3․14
        cleaned = cleaned.replace(Regex("(\\d)\\.(\\d)"), "$1$PROTECTED_DOT$2")

        // 2. Protect titles when followed by a capital letter / initial / name or quote
        cleaned = cleaned.replace(Regex("\\b($TITLES)\\.(?=\\s+[A-Z])"), "$1$PROTECTED_DOT")
        cleaned = cleaned.replace(Regex("\\b($TITLES)\\.(?=\\s*[\"'])"), "$1$PROTECTED_DOT")

        // 3. Saint / Street: "St. Jude" vs "Baker St. with..."
        cleaned = cleaned.replace(Regex("\\bSt\\.(?=\\s+[A-Z])"), "St$PROTECTED_DOT")
        cleaned = cleaned.replace(Regex("(?<=[A-Za-z0-9]\\s)St\\.(?=\\s+[a-z,;])"), "St$PROTECTED_DOT")

        // 4. Acronyms / initialisms (U.S., D.C., a.m., p.m.)
        cleaned = cleaned.replace(Regex("\\b([A-Za-z])\\.(?=[A-Za-z]\\.)"), "$1$PROTECTED_DOT")
        cleaned = cleaned.replace(Regex("\\b([A-Za-z]$PROTECTED_DOT[A-Za-z])\\.(?=\\s+[a-z,;])"), "$1$PROTECTED_DOT")

        // 5. Latin abbreviations
        cleaned = cleaned.replace(Regex("\\b[eE]\\.[gG]\\."), "e${PROTECTED_DOT}g$PROTECTED_DOT")
        cleaned = cleaned.replace(Regex("\\b[iI]\\.[eE]\\."), "i${PROTECTED_DOT}e$PROTECTED_DOT")
        cleaned = cleaned.replace(Regex("\\b[vV][sS]\\.(?=\\s+[A-Za-z0-9])"), "vs$PROTECTED_DOT")
        cleaned = cleaned.replace(Regex("\\b[aA]pprox\\.(?=\\s+[0-9])"), "approx$PROTECTED_DOT")
        cleaned = cleaned.replace(Regex("\\betc\\.(?=\\s*[,;a-z])"), "etc$PROTECTED_DOT")

        // 6. Single-letter initials in names:
        // A) Chained initials: "C. M." -> "C․ M."
        cleaned = cleaned.replace(Regex("\\b([A-Z])\\.(?=\\s*[A-Z]\\.)"), "$1$PROTECTED_DOT")
        // B) Initial followed by a capitalized name that is not a sentence starter: "M. Park" -> "M․ Park"
        cleaned = cleaned.replace(Regex("\\b([A-Z])\\.(?=\\s+(?!$SENTENCE_STARTERS)[A-Z][a-z]+)"), "$1$PROTECTED_DOT")

        // 7. Split on sentence boundaries (.!? followed by whitespace)
        val rawSentences = SENTENCE_BOUNDARY.split(cleaned)

        // 8. Restore protected dots
        return rawSentences.mapNotNull { sentence ->
            val restored = sentence.replace(PROTECTED_DOT, '.').trim()
            if (restored.isNotEmpty()) restored else null
        }
    }
}
