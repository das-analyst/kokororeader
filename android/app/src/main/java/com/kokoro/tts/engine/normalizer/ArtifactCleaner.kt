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

        // 3. Remove footnote citations like [1], [24], [a], [iv]
        t = t.replace(Regex("\\[(?:\\d+|[a-zA-Z]|[ivxIVX]+)\\]"), "")
        t = t.replace(Regex("(?<=\\w)[*†‡]"), "")

        // 4. Normalize curly quotes and em dashes
        t = t.replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace('—', '-')
            .replace('–', '-')

        // 5. Clean excessive spaces and multiple newlines
        t = t.replace(Regex("[ \\t]+"), " ")
        t = t.replace(Regex("\\n{3,}"), "\n\n")

        return t.trim()
    }
}
