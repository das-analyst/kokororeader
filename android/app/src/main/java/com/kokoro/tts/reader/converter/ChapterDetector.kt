package com.kokoro.tts.reader.converter

data class DetectedChapter(
    val chapterNumber: Int,
    val title: String,
    val content: String
)

data class ChapterDetectionResult(
    val frontMatter: String,
    val chapters: List<DetectedChapter>
)

/**
 * Intelligent semantic chapter detector for ebooks.
 * Identifies real literary chapter markers (explicit, numbered, and Roman numeral headings)
 * across EPUB and PDF extracted text, ensuring identical chapter segmentation regardless
 * of original file container format.
 */
object ChapterDetector {

    private val MONTHS_REGEX = Regex(
        "(?i)\\b(?:January|February|March|April|May|June|July|August|September|October|November|December)\\b"
    )

    private val UNITS_REGEX = Regex(
        "(?i)^\\d{1,3}\\s+(?:Metres?|Kilometers?|Miles?|Feet|Inches?|Hours?|Minutes?|Seconds?|Days?|Weeks?|Months?|Years?|Dollars?|Pounds?|Percent)\\b"
    )

    // Pattern 0: Prologue / Preface / Introduction / Epilogue
    private val PROLOGUE_PATTERN = Regex(
        "(?i)^[ \\t]*(?:PROLOGUE|Prologue|PREFACE|Preface|INTRODUCTION|Introduction|EPILOGUE|Epilogue)(?:[ \\t]*[:.\\-—][ \\t]*(.*))?$"
    )

    // Pattern 1: Explicit CHAPTER / Chapter / PART / Part / Book
    private val EXPLICIT_CHAPTER_PATTERN = Regex(
        "(?i)^[ \\t]*(?:CHAPTER|Chapter|Chapitre|PART|Part|Book|Livre|Act|Scene)\\s+([0-9IVXLCDM]+|[A-Za-z\\-]+)(?:[ \\t]*[:.\\-—][ \\t]*(.*))?$"
    )

    // Pattern 2: Numbered Title on its own line: '1  The Peculiar Second Marriage' or '12 Canines: ...' or '9 Mutiny!'
    private val NUMBERED_HEADING_PATTERN = Regex(
        "^[ \\t]*(\\d{1,3})[ \\t]+([A-Z][A-Za-z0-9 '\"‘’“”\\-—,:!?.()]{2,80})[ \\t]*$"
    )

    // Pattern 3: Roman numeral heading: 'I. It is a truth...' or 'IV. ...'
    private val ROMAN_HEADING_PATTERN = Regex(
        "^[ \\t]*([IVXLCDM]{1,6})\\.[ \\t]+([A-Z][A-Za-z0-9 '\"‘’“”\\-—,:!?.()]{2,80})[ \\t]*$"
    )

    private val WORD_TO_NUMBER = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
        "twenty-one" to 21, "twenty-two" to 22, "twenty-three" to 23, "twenty-four" to 24, "twenty-five" to 25,
        "twenty-six" to 26, "twenty-seven" to 27, "twenty-eight" to 28, "twenty-nine" to 29, "thirty" to 30
    )

    private val ROMAN_MAP = mapOf(
        'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000
    )

    private fun romanToInt(s: String): Int {
        val upper = s.uppercase()
        if (!upper.matches(Regex("^[IVXLCDM]+$"))) return -1
        var total = 0
        var prev = 0
        for (i in upper.length - 1 downTo 0) {
            val v = ROMAN_MAP[upper[i]] ?: 0
            if (v < prev) {
                total -= v
            } else {
                total += v
                prev = v
            }
        }
        return total
    }

    private fun parseChapterNumber(str: String): Int {
        val s = str.trim()
        val intVal = s.toIntOrNull()
        if (intVal != null) return intVal

        val romVal = romanToInt(s)
        if (romVal > 0) return romVal

        return WORD_TO_NUMBER[s.lowercase()] ?: -1
    }

    private data class Candidate(
        val offset: Int,
        val rawLine: String,
        val displayTitle: String,
        val number: Int // 0 for prologue, -1 if explicit/non-numeric, >0 for numbered
    )

    /**
     * Detects semantic chapters and extracts front matter.
     * Returns empty ChapterDetectionResult if fewer than 2 valid chapters are detected.
     */
    fun detect(fullText: String): ChapterDetectionResult {
        if (fullText.isBlank()) return ChapterDetectionResult("", emptyList())

        val lines = fullText.lines()
        val candidates = mutableListOf<Candidate>()
        var currOffset = 0

        for (line in lines) {
            val trimmed = line.trim()
            val offset = currOffset
            currOffset += line.length + 1 // accounts for newline

            if (trimmed.length < 2 || trimmed.length > 90) continue

            // Reject date lines like "17 May 1957" or "28 December 1974"
            if (Regex("^\\d{1,2}\\s+").containsMatchIn(trimmed) && MONTHS_REGEX.containsMatchIn(trimmed)) {
                continue
            }

            // Reject measurement / quantity lines like "2 Metres..."
            if (UNITS_REGEX.containsMatchIn(trimmed)) {
                continue
            }

            // Check Pattern 0: Prologue / Preface / Introduction / Epilogue
            val mPro = PROLOGUE_PATTERN.matchEntire(trimmed)
            if (mPro != null) {
                candidates.add(Candidate(offset, trimmed, trimmed, 0))
                continue
            }

            // Check Pattern 1: Explicit CHAPTER / Chapter / PART / Book
            val mExp = EXPLICIT_CHAPTER_PATTERN.matchEntire(trimmed)
            if (mExp != null) {
                val numStr = mExp.groupValues[1]
                val num = parseChapterNumber(numStr)
                candidates.add(Candidate(offset, trimmed, trimmed, num))
                continue
            }

            // Check Pattern 2: Numbered headings (e.g. "1 The Peculiar Second Marriage")
            val mNum = NUMBERED_HEADING_PATTERN.matchEntire(trimmed)
            if (mNum != null) {
                val num = mNum.groupValues[1].toIntOrNull() ?: -1
                candidates.add(Candidate(offset, trimmed, trimmed, num))
                continue
            }

            // Check Pattern 3: Roman numeral heading (e.g. "I. The Beginning")
            val mRom = ROMAN_HEADING_PATTERN.matchEntire(trimmed)
            if (mRom != null) {
                val num = romanToInt(mRom.groupValues[1])
                candidates.add(Candidate(offset, trimmed, trimmed, num))
                continue
            }
        }

        // Filter numbered candidates to ensure ascending sequence
        val filtered = mutableListOf<Candidate>()
        var expectedNum = 1

        for (cand in candidates) {
            when {
                cand.number == 0 -> {
                    // Prologue only valid before chapters begin
                    if (filtered.isEmpty()) {
                        filtered.add(cand)
                    }
                }
                cand.number == -1 -> {
                    filtered.add(cand)
                }
                cand.number == expectedNum || cand.number == expectedNum + 1 -> {
                    filtered.add(cand)
                    expectedNum = cand.number
                }
            }
        }

        // We need at least 2 chapters to consider it a legitimate chapter-divided document
        if (filtered.size < 2) {
            return ChapterDetectionResult("", emptyList())
        }

        val frontMatter = fullText.substring(0, filtered[0].offset).trim()
        val chapters = mutableListOf<DetectedChapter>()

        for (i in filtered.indices) {
            val cand = filtered[i]
            val startOffset = cand.offset
            val endOffset = if (i + 1 < filtered.size) filtered[i + 1].offset else fullText.length

            val rawChunk = fullText.substring(startOffset, endOffset).trim()
            val contentBody = if (rawChunk.startsWith(cand.rawLine)) {
                rawChunk.removePrefix(cand.rawLine).trim()
            } else {
                rawChunk
            }

            chapters.add(
                DetectedChapter(
                    chapterNumber = i + 1,
                    title = cand.displayTitle,
                    content = contentBody
                )
            )
        }

        return ChapterDetectionResult(frontMatter, chapters)
    }

    /**
     * Convenience method returning only the list of detected chapters.
     */
    fun detectChapters(fullText: String): List<DetectedChapter> {
        return detect(fullText).chapters
    }
}
