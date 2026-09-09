package com.kokoro.tts.reader.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDetectorTest {

    @Test
    fun testWhiteTeethNumberedHeadings() {
        val bookText = """
            Synopsis:
            Zadie Smith's debut novel.
            
            1 The Peculiar Second Marriage
            Early in the morning, late in the century.
            Archie Jones was sitting in his car.
            
            2 Teething Trouble
            Clara Bowden was nineteen.
            Archibald was forty-seven.
            
            3 Two Families
            The Joneses and the Iqbals.
        """.trimIndent()

        val result = ChapterDetector.detect(bookText)
        assertEquals(3, result.chapters.size)
        assertTrue(result.frontMatter.contains("Zadie Smith's debut novel"))
        assertEquals("1 The Peculiar Second Marriage", result.chapters[0].title)
        assertTrue(result.chapters[0].content.contains("Archie Jones was sitting in his car"))
        assertEquals("2 Teething Trouble", result.chapters[1].title)
        assertEquals("3 Two Families", result.chapters[2].title)
    }

    @Test
    fun testExplicitChapterHeadings() {
        val bookText = """
            CHAPTER 1: The Boy Who Lived
            Mr. and Mrs. Dursley, of number four, Privet Drive, were proud to say.
            
            CHAPTER 2: The Vanishing Glass
            Nearly ten years had passed.
            
            CHAPTER 3: The Letters from No One
            The escape of the Brazilian boa constrictor earned Harry his longest-ever punishment.
        """.trimIndent()

        val result = ChapterDetector.detect(bookText)
        assertEquals(3, result.chapters.size)
        assertEquals("CHAPTER 1: The Boy Who Lived", result.chapters[0].title)
        assertEquals("CHAPTER 2: The Vanishing Glass", result.chapters[1].title)
        assertEquals("CHAPTER 3: The Letters from No One", result.chapters[2].title)
    }

    @Test
    fun testWordNumberedChapters() {
        val bookText = """
            Chapter One
            It was a dark and stormy night.
            
            Chapter Two
            The rain poured down in torrents.
            
            Chapter Three
            By morning the storm had passed.
        """.trimIndent()

        val result = ChapterDetector.detect(bookText)
        assertEquals(3, result.chapters.size)
        assertEquals("Chapter One", result.chapters[0].title)
        assertEquals("Chapter Two", result.chapters[1].title)
        assertEquals("Chapter Three", result.chapters[2].title)
    }

    @Test
    fun testRomanNumeralHeadings() {
        val bookText = """
            I. It is a truth universally acknowledged
            That a single man in possession of a good fortune, must be in want of a wife.
            
            II. Mr. Bennet was among the earliest of those who waited on Mr. Bingley
            He had always intended to visit him, though to the last always assuring his wife.
        """.trimIndent()

        val result = ChapterDetector.detect(bookText)
        assertEquals(2, result.chapters.size)
        assertEquals("I. It is a truth universally acknowledged", result.chapters[0].title)
        assertEquals("II. Mr. Bennet was among the earliest of those who waited on Mr. Bingley", result.chapters[1].title)
    }

    @Test
    fun testDateAndUnitRejections() {
        val bookText = """
            1 The Peculiar Second Marriage
            He remembered 17 May 1957 clearly.
            18 June 1960 was also memorable.
            He bought 2 Metres of cloth.
            
            2 Teething Trouble
            They bought 5 Metres of ribbon.
            On 28 December 1974 they met again.
        """.trimIndent()

        val result = ChapterDetector.detect(bookText)
        assertEquals(2, result.chapters.size)
        assertEquals("1 The Peculiar Second Marriage", result.chapters[0].title)
        assertEquals("2 Teething Trouble", result.chapters[1].title)
    }

    @Test
    fun testPrologueDetection() {
        val bookText = """
            PROLOGUE
            In the ancient times before the dawn of memory.
            
            CHAPTER 1: A New Dawn
            The sun rose over the mountains.
            
            CHAPTER 2: Shadows Lengthen
            Evening came swift and dark.
        """.trimIndent()

        val result = ChapterDetector.detect(bookText)
        assertEquals(3, result.chapters.size)
        assertEquals("PROLOGUE", result.chapters[0].title)
        assertEquals("CHAPTER 1: A New Dawn", result.chapters[1].title)
        assertEquals("CHAPTER 2: Shadows Lengthen", result.chapters[2].title)
    }

    @Test
    fun testInsufficientChaptersReturnsEmpty() {
        val bookText = """
            Just a single article without chapters.
            Some content here.
            Another paragraph here.
        """.trimIndent()

        val result = ChapterDetector.detect(bookText)
        assertEquals(0, result.chapters.size)
    }
}
