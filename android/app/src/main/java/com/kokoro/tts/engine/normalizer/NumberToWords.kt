package com.kokoro.tts.engine.normalizer

import java.util.regex.Pattern

/**
 * Converts numbers into spoken English words following NeMo TTS normalization guidelines.
 * Supports cardinal numbers, ordinals, decimals, fractions, and year verbalization.
 */
object NumberToWords {

    private val ONES = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    )

    private val TENS = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    private val ORDINAL_ONES = arrayOf(
        "", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth",
        "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth",
        "seventeenth", "eighteenth", "nineteenth"
    )

    private val ORDINAL_TENS = arrayOf(
        "", "", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth", "seventieth", "eightieth", "ninetieth"
    )

    /**
     * Converts a positive Long integer to spoken words (e.g. 42 -> "forty-two").
     */
    fun convertCardinal(number: Long): String {
        if (number == 0L) return "zero"
        if (number < 0L) return "minus ${convertCardinal(-number)}"

        return convertChunks(number).trim()
    }

    private fun convertChunks(number: Long): String {
        if (number == 0L) return ""

        return when {
            number < 20L -> ONES[number.toInt()]
            number < 100L -> {
                val ten = TENS[(number / 10).toInt()]
                val rem = number % 10
                if (rem > 0) "$ten-${ONES[rem.toInt()]}" else ten
            }
            number < 1000L -> {
                val hundred = "${ONES[(number / 100).toInt()]} hundred"
                val rem = number % 100
                if (rem > 0) "$hundred ${convertChunks(rem)}" else hundred
            }
            number < 1000000L -> {
                val thousand = "${convertChunks(number / 1000)} thousand"
                val rem = number % 1000
                if (rem > 0) "$thousand ${convertChunks(rem)}" else thousand
            }
            number < 1000000000L -> {
                val million = "${convertChunks(number / 1000000)} million"
                val rem = number % 1000000
                if (rem > 0) "$million ${convertChunks(rem)}" else million
            }
            else -> {
                val billion = "${convertChunks(number / 1000000000L)} billion"
                val rem = number % 1000000000L
                if (rem > 0) "$billion ${convertChunks(rem)}" else billion
            }
        }
    }

    /**
     * Converts an ordinal number (e.g. 1st -> "first", 22nd -> "twenty-second").
     */
    fun convertOrdinal(number: Long): String {
        if (number <= 0L) return convertCardinal(number)

        val rem100 = (number % 100).toInt()
        val rem10 = (number % 10).toInt()

        return when {
            rem100 in 1..19 -> {
                val prefix = if (number >= 100) "${convertCardinal(number - rem100)} " else ""
                "$prefix${ORDINAL_ONES[rem100]}"
            }
            rem10 == 0 && rem100 in 20..90 -> {
                val prefix = if (number >= 100) "${convertCardinal(number - rem100)} " else ""
                "$prefix${ORDINAL_TENS[rem100 / 10]}"
            }
            rem10 > 0 && rem100 in 21..99 -> {
                val prefix = if (number >= 100) "${convertCardinal(number - rem100)} " else ""
                val ten = TENS[rem100 / 10]
                "$prefix$ten-${ORDINAL_ONES[rem10]}"
            }
            number % 1000 == 0L -> "${convertCardinal(number)}th"
            number % 100 == 0L -> "${convertCardinal(number).removeSuffix("hundred")}hundredth"
            else -> "${convertCardinal(number)}th"
        }
    }

    /**
     * Verbalizes years like 1984 -> "nineteen eighty-four", 2024 -> "twenty twenty-four", 2005 -> "two thousand five".
     */
    fun convertYear(year: Int): String {
        if (year in 2000..2009) {
            return convertCardinal(year.toLong())
        }
        if (year in 1100..1999 || year in 2010..2099) {
            val high = year / 100
            val low = year % 100
            val highStr = convertCardinal(high.toLong())
            val lowStr = when {
                low == 0 -> "hundred"
                low < 10 -> "oh ${convertCardinal(low.toLong())}"
                else -> convertCardinal(low.toLong())
            }
            return "$highStr $lowStr"
        }
        return convertCardinal(year.toLong())
    }

    /**
     * Replaces decimal numbers: "3.14" -> "three point one four".
     */
    fun convertDecimal(integerPart: Long, decimalStr: String): String {
        val intWords = convertCardinal(integerPart)
        val decWords = decimalStr.map { digit ->
            ONES[digit.toString().toInt()]
        }.joinToString(" ")
        return "$intWords point $decWords"
    }

    /**
     * Normalizes standalone numbers and ordinals in text.
     */
    fun normalizeNumbersInText(text: String): String {
        var result = text

        // Decade years: 1980s, 1990's -> nineteen eighties, nineteen nineties
        result = Regex("\\b(1[89]\\d0|20\\d0)'?s\\b").replace(result) { m ->
            val year = m.groupValues[1].toInt()
            val high = year / 100
            val low = year % 100
            val highWords = convertCardinal(high.toLong())
            val lowWords = when (low) {
                0 -> "hundreds"
                10 -> "tens"
                20 -> "twenties"
                30 -> "thirties"
                40 -> "forties"
                50 -> "fifties"
                60 -> "sixties"
                70 -> "seventies"
                80 -> "eighties"
                90 -> "nineties"
                else -> "${convertCardinal(low.toLong())}s"
            }
            "$highWords $lowWords"
        }

        // 4-digit standalone years: (1700-2099) when following prepositions or in date context
        result = Regex("(?i)\\b(?:in|by|since|until|around|circa|year)\\s+(1[5-9]\\d{2}|20\\d{2})\\b").replace(result) { m ->
            val prefix = m.value.substringBefore(m.groupValues[1])
            val year = m.groupValues[1].toInt()
            "$prefix${convertYear(year)}"
        }

        // Ordinal numbers: 1st, 2nd, 3rd, 4th, 21st, 100th
        result = Regex("\\b(\\d+)(?:st|nd|rd|th)\\b", RegexOption.IGNORE_CASE).replace(result) { m ->
            val num = m.groupValues[1].toLongOrNull()
            if (num != null && num <= 999999L) {
                convertOrdinal(num)
            } else {
                m.value
            }
        }

        // Decimals: 3.14, 0.05
        result = Regex("(?<=\\s|^)(\\d+)\\.(\\d+)(?=[\\s,;!?]|$)").replace(result) { m ->
            val intVal = m.groupValues[1].toLongOrNull()
            val decStr = m.groupValues[2]
            if (intVal != null && decStr.length <= 6) {
                convertDecimal(intVal, decStr)
            } else {
                m.value
            }
        }

        // Simple fractions: 1/2, 1/4, 3/4, 2/3
        val fractionMap = mapOf(
            "1/2" to "one half",
            "1/3" to "one third",
            "2/3" to "two thirds",
            "1/4" to "one quarter",
            "3/4" to "three quarters"
        )
        for ((frac, spoken) in fractionMap) {
            result = result.replace(Regex("(?<=\\s|^)$frac(?=[\\s,;!?]|$)"), spoken)
        }

        // Comma-separated or regular integers up to 999,999,999
        result = Regex("(?<=\\s|^)(\\d{1,3}(?:,\\d{3})+|\\d+)(?=[\\s,;!?]|$)").replace(result) { m ->
            val raw = m.groupValues[1].replace(",", "")
            val num = raw.toLongOrNull()
            if (num != null && num in 0..999999999L) {
                // If it looks like a year (e.g. 1800-2099) and 4 digits, verbalize as year if reasonable
                if (raw.length == 4 && num in 1700..2035) {
                    convertYear(num.toInt())
                } else {
                    convertCardinal(num)
                }
            } else {
                m.value
            }
        }

        return result
    }
}
