package com.kokoro.tts.engine.normalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun testCardinals() {
        assertEquals("zero", NumberToWords.convertCardinal(0))
        assertEquals("one", NumberToWords.convertCardinal(1))
        assertEquals("fourteen", NumberToWords.convertCardinal(14))
        assertEquals("twenty", NumberToWords.convertCardinal(20))
        assertEquals("forty-two", NumberToWords.convertCardinal(42))
        assertEquals("one hundred", NumberToWords.convertCardinal(100))
        assertEquals("one hundred five", NumberToWords.convertCardinal(105))
        assertEquals("one thousand two hundred thirty-four", NumberToWords.convertCardinal(1234))
        assertEquals("one million", NumberToWords.convertCardinal(1000000))
    }

    @Test
    fun testOrdinals() {
        assertEquals("first", NumberToWords.convertOrdinal(1))
        assertEquals("second", NumberToWords.convertOrdinal(2))
        assertEquals("third", NumberToWords.convertOrdinal(3))
        assertEquals("tenth", NumberToWords.convertOrdinal(10))
        assertEquals("twenty-first", NumberToWords.convertOrdinal(21))
        assertEquals("one hundredth", NumberToWords.convertOrdinal(100))
    }

    @Test
    fun testRomanNumerals() {
        assertEquals("Henry the Eighth", RomanNumeralNormalizer.normalize("Henry VIII"))
        assertEquals("Louis the Fourteenth", RomanNumeralNormalizer.normalize("Louis XIV"))
        assertEquals("Queen Elizabeth the Second", RomanNumeralNormalizer.normalize("Queen Elizabeth II"))
        assertEquals("Chapter Four", RomanNumeralNormalizer.normalize("Chapter IV"))
        assertEquals("Act Two, Scene One", RomanNumeralNormalizer.normalize("Act II, Scene I"))
        assertEquals("Volume Three", RomanNumeralNormalizer.normalize("Volume III"))
        assertEquals("World War Two", RomanNumeralNormalizer.normalize("World War II"))
    }

    @Test
    fun testCurrenciesAndMeasures() {
        assertEquals("ten dollars and fifty cents", MeasureCurrencyNormalizer.normalize("$10.50"))
        assertEquals("twenty-five dollars", MeasureCurrencyNormalizer.normalize("$25"))
        assertEquals("one dollar", MeasureCurrencyNormalizer.normalize("$1"))
        assertEquals("fifty pounds", MeasureCurrencyNormalizer.normalize("£50"))
        assertEquals("twenty euros and ninety-nine cents", MeasureCurrencyNormalizer.normalize("€20.99"))
        assertEquals("one point five million dollars", MeasureCurrencyNormalizer.normalize("$1.5M"))
        assertEquals("twenty-five percent", MeasureCurrencyNormalizer.normalize("25%"))
        assertEquals("ninety-eight point six degrees Fahrenheit", MeasureCurrencyNormalizer.normalize("98.6°F"))
        assertEquals("twenty-five degrees Celsius", MeasureCurrencyNormalizer.normalize("25°C"))
        assertEquals("ten kilometers", MeasureCurrencyNormalizer.normalize("10km"))
        assertEquals("seventy miles per hour", MeasureCurrencyNormalizer.normalize("70mph"))
    }

    @Test
    fun testAbbreviations() {
        assertEquals("Doctor Watson", AbbreviationNormalizer.normalize("Dr. Watson"))
        assertEquals("Mister Bennet", AbbreviationNormalizer.normalize("Mr. Bennet"))
        assertEquals("Missus Darcy", AbbreviationNormalizer.normalize("Mrs. Darcy"))
        assertEquals("Saint Jude", AbbreviationNormalizer.normalize("St. Jude"))
        assertEquals("Baker Street", AbbreviationNormalizer.normalize("Baker St."))
        assertEquals("for example, ", AbbreviationNormalizer.normalize("e.g. "))
        assertEquals("et cetera", AbbreviationNormalizer.normalize("etc."))
        assertEquals("three thirty p m", AbbreviationNormalizer.normalize("3:30pm"))
    }

    @Test
    fun testArtifactCleaner() {
        assertEquals("find flower", ArtifactCleaner.clean("ﬁnd ﬂower"))
        assertEquals("The fact was known.", ArtifactCleaner.clean("The fact was known.[1]"))
        assertEquals("information", ArtifactCleaner.clean("infor-\nmation"))
        assertEquals("\"quoted\"", ArtifactCleaner.clean("“quoted”"))
    }

    @Test
    fun testFullNormalizationPipeline() {
        val input = "King Henry VIII visited Baker St. on July 4th, 1984 with Dr. Watson."
        val normalized = TextNormalizer.normalize(input)

        assertTrue(normalized.contains("King Henry the Eighth"))
        assertTrue(normalized.contains("Baker Street"))
        assertTrue(normalized.contains("July fourth"))
        assertTrue(normalized.contains("nineteen eighty-four"))
        assertTrue(normalized.contains("Doctor Watson"))
    }

    @Test
    fun testPrideAndPrejudiceSnippet() {
        val input = "Chapter I. It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife."
        val normalized = TextNormalizer.normalize(input)

        assertTrue(normalized.startsWith("Chapter One."))
    }

    @Test
    fun testExactVerificationPrompt() {
        val input = "King Henry VIII visited Baker St. on July 4, 1984 with Dr. Watson. It cost $24.50 for 5km of travel (approx. 25% discount)."
        val normalized = TextNormalizer.normalize(input)

        assertEquals(
            "King Henry the Eighth visited Baker Street on July four, nineteen eighty-four with Doctor Watson. It cost twenty-four dollars and fifty cents for five kilometers of travel (approximately twenty-five percent discount).",
            normalized
        )
    }
}
