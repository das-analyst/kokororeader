package com.kokoro.tts.engine.normalizer

/**
 * Cleans formatting artifacts, unicode ligatures, footnotes, and line breaks from EPUB and PDF text.
 */
object ArtifactCleaner {

    private val LIGATURES = mapOf(
        "ﬁ" to "fi",
        "ﬂ" to "fl",
        "ﬀ" to "ff",
        "ﬃ" to "ffi",
        "ﬄ" to "ffl",
        "œ" to "oe",
        "Œ" to "Oe",
        "æ" to "ae",
        "Æ" to "Ae"
    )

    fun clean(text: String): String {
        var t = text

        // 1. Rejoin hyphenated line breaks (word-\nword -> wordword)
        t = t.replace(Regex("([a-zA-Z]+)-\\s*\\r?\\n\\s*([a-zA-Z]+)"), "$1$2")

        // 2. Unwrap ligatures
        for ((ligature, replacement) in LIGATURES) {
            t = t.replace(ligature, replacement)
        }

        // 3. Remove footnote citations like [1], [24], [a], [iv] (numeric or citation markers only)
        t = t.replace(Regex("\\[(?:\\d{1,4}|[ivxIVX]{1,4}|[a-c]|\\*|†|‡)\\]"), "")
        t = t.replace(Regex("(?<=\\w)[*†‡]"), "")

        // Map remaining square brackets around words/phrases to parentheses for native TTS parenthetical tokens
        t = t.replace('[', '(').replace(']', ')')

        // 4. Normalize curly quotes, em-dashes, and ellipses
        t = t.replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')

        // Normalize ASCII double/triple hyphens to true Unicode em-dash
        t = t.replace(Regex("(?<=\\S)\\s*--+\\s*(?=\\S)"), " — ")
        t = t.replace(Regex("--+"), " — ")

        // Normalize en-dashes used as clause separators to em-dash
        t = t.replace(Regex("\\s+–\\s+"), " — ")
        t = t.replace(Regex("(?<=[a-zA-Z])–(?=[a-zA-Z])"), "—")

        // Normalize ellipsis (... -> …)
        t = t.replace(Regex("\\.{3,}"), "…")

        // 5. Clean up name initial spacing and missing spaces after titles
        // E.g. "Mr.Park" -> "Mr. Park", "Mr.C.M." -> "Mr. C. M."
        t = t.replace(Regex("\\b(Mr|Mrs|Ms|Dr|Prof|Capt|Col|Gen|Lt|Sgt|Rev|Hon)\\.([A-Z])"), "$1. $2")

        // E.g. "C.M." -> "C. M.", "J.R.R." -> "J. R. R."
        while (Regex("\\b([A-Z])\\.([A-Z])\\.").containsMatchIn(t)) {
            t = t.replace(Regex("\\b([A-Z])\\.([A-Z])\\."), "$1. $2.")
        }
        t = t.replace(Regex("(?<=\\b[A-Z]\\.)\\s{2,}(?=[A-Z])"), " ")

        // 6. Clean excessive spaces and multiple newlines
        t = t.replace(Regex("[ \\t]+"), " ")
        t = t.replace(Regex("\\n{3,}"), "\n\n")

        return t.trim()
    }
}
