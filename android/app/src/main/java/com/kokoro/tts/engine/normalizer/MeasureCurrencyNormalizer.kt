package com.kokoro.tts.engine.normalizer

/**
 * Normalizes currencies, measurements, percentages, and temperatures into natural spoken English.
 */
object MeasureCurrencyNormalizer {

    fun normalize(text: String): String {
        var result = text

        // 1. Shorthand currency amounts: $1.5M -> one point five million dollars, $50K -> fifty thousand dollars
        result = Regex("\\$\\s*(\\d+(?:\\.\\d+)?)\\s*([kKmMbB])\\b").replace(result) { m ->
            val numStr = m.groupValues[1]
            val scale = when (m.groupValues[2].lowercase()) {
                "k" -> "thousand"
                "m" -> "million"
                "b" -> "billion"
                else -> ""
            }
            val verbalizedNum = if (numStr.contains(".")) {
                val parts = numStr.split(".")
                NumberToWords.convertDecimal(parts[0].toLong(), parts[1])
            } else {
                NumberToWords.convertCardinal(numStr.toLong())
            }
            "$verbalizedNum $scale dollars"
        }

        // 2. Currencies with decimals: $10.50, £1.20, €9.99
        result = Regex("([\\$£€¥])\\s*(\\d+)\\.(\\d{2})\\b").replace(result) { m ->
            val symbol = m.groupValues[1]
            val whole = m.groupValues[2].toLongOrNull() ?: 0L
            val cents = m.groupValues[3].toIntOrNull() ?: 0

            val wholeWords = NumberToWords.convertCardinal(whole)
            val centsWords = NumberToWords.convertCardinal(cents.toLong())

            when (symbol) {
                "$" -> {
                    val unit = if (whole == 1L) "dollar" else "dollars"
                    if (cents == 0) "$wholeWords $unit" else "$wholeWords $unit and $centsWords cents"
                }
                "£" -> {
                    val unit = if (whole == 1L) "pound" else "pounds"
                    if (cents == 0) "$wholeWords $unit" else "$wholeWords $unit and $centsWords pence"
                }
                "€" -> {
                    val unit = "euros"
                    if (cents == 0) "$wholeWords $unit" else "$wholeWords $unit and $centsWords cents"
                }
                "¥" -> "$wholeWords yen"
                else -> m.value
            }
        }

        // 3. Whole currencies: $100, £50, €20, ¥1000
        result = Regex("([\\$£€¥])\\s*(\\d+)\\b").replace(result) { m ->
            val symbol = m.groupValues[1]
            val whole = m.groupValues[2].toLongOrNull() ?: 0L
            val wholeWords = NumberToWords.convertCardinal(whole)

            when (symbol) {
                "$" -> if (whole == 1L) "$wholeWords dollar" else "$wholeWords dollars"
                "£" -> if (whole == 1L) "$wholeWords pound" else "$wholeWords pounds"
                "€" -> "$wholeWords euros"
                "¥" -> "$wholeWords yen"
                else -> m.value
            }
        }

        // 4. Percentages: 25%, 3.5%
        result = Regex("(\\d+(?:\\.\\d+)?)\\s*%").replace(result) { m ->
            val numStr = m.groupValues[1]
            val verbalized = if (numStr.contains(".")) {
                val parts = numStr.split(".")
                NumberToWords.convertDecimal(parts[0].toLong(), parts[1])
            } else {
                NumberToWords.convertCardinal(numStr.toLong())
            }
            "$verbalized percent"
        }

        // 5. Temperatures: 98.6°F, 25°C, 100°
        result = Regex("(\\d+(?:\\.\\d+)?)\\s*°\\s*([FfCc])\\b").replace(result) { m ->
            val numStr = m.groupValues[1]
            val scale = if (m.groupValues[2].equals("F", ignoreCase = true)) "Fahrenheit" else "Celsius"
            val verbalized = if (numStr.contains(".")) {
                val parts = numStr.split(".")
                NumberToWords.convertDecimal(parts[0].toLong(), parts[1])
            } else {
                NumberToWords.convertCardinal(numStr.toLong())
            }
            "$verbalized degrees $scale"
        }
        result = Regex("(\\d+)\\s*°").replace(result) { m ->
            "${NumberToWords.convertCardinal(m.groupValues[1].toLong())} degrees"
        }

        // 6. Common units of speed and distance
        result = Regex("(\\d+)\\s*mph\\b", RegexOption.IGNORE_CASE).replace(result) { m ->
            val num = m.groupValues[1].toLongOrNull()
            val words = if (num != null) NumberToWords.convertCardinal(num) else m.groupValues[1]
            "$words miles per hour"
        }
        result = Regex("(\\d+)\\s*kph\\b", RegexOption.IGNORE_CASE).replace(result) { m ->
            val num = m.groupValues[1].toLongOrNull()
            val words = if (num != null) NumberToWords.convertCardinal(num) else m.groupValues[1]
            "$words kilometers per hour"
        }

        // Distance / Weight units with singular / plural handling
        val unitReplacements = listOf(
            Pair(Regex("(\\d+)\\s*km\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one kilometer" else "${NumberToWords.convertCardinal(n)} kilometers" },
            Pair(Regex("(\\d+)\\s*m\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one meter" else "${NumberToWords.convertCardinal(n)} meters" },
            Pair(Regex("(\\d+)\\s*cm\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one centimeter" else "${NumberToWords.convertCardinal(n)} centimeters" },
            Pair(Regex("(\\d+)\\s*mm\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one millimeter" else "${NumberToWords.convertCardinal(n)} millimeters" },
            Pair(Regex("(\\d+)\\s*kg\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one kilogram" else "${NumberToWords.convertCardinal(n)} kilograms" },
            Pair(Regex("(\\d+)\\s*(?:lbs|lb)\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one pound" else "${NumberToWords.convertCardinal(n)} pounds" },
            Pair(Regex("(\\d+)\\s*oz\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one ounce" else "${NumberToWords.convertCardinal(n)} ounces" },
            Pair(Regex("(\\d+)\\s*ft\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one foot" else "${NumberToWords.convertCardinal(n)} feet" },
            Pair(Regex("(\\d+)\\s*in\\b", RegexOption.IGNORE_CASE)) { n: Long -> if (n == 1L) "one inch" else "${NumberToWords.convertCardinal(n)} inches" }
        )

        for ((regex, transform) in unitReplacements) {
            result = regex.replace(result) { match ->
                val num = match.groupValues[1].toLongOrNull()
                if (num != null && num in 0..999999) {
                    transform(num)
                } else {
                    match.value
                }
            }
        }

        return result
    }
}
