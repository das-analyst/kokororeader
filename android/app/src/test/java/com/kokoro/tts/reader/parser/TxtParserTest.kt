package com.kokoro.tts.reader.parser

import com.kokoro.tts.engine.normalizer.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtParserTest {

    @Test
    fun testParseConvertedBookWithMetadataAndChapters() {
        val convertedBook = """
            # White Teeth
            Author: Zadie Smith
            
            CHAPTER 1: 1 The Peculiar Second Marriage
            Early in the morning, late in the century.
            Archie Jones was sitting in his car.
            
            CHAPTER 2: 2 Teething Trouble
            Clara Bowden was nineteen.
            Archibald was forty-seven.
            
            CHAPTER 3: 3 Two Families
            The Joneses and the Iqbals had been friends for years.
        """.trimIndent()

        val book = TxtParser.parseText(convertedBook, "Fallback")
        assertEquals("White Teeth", book.title)
        assertEquals("Zadie Smith", book.author)
        assertEquals(3, book.chapters.size)
        assertEquals("CHAPTER 1: 1 The Peculiar Second Marriage", book.chapters[0].title)
        assertEquals("CHAPTER 2: 2 Teething Trouble", book.chapters[1].title)
        assertEquals("CHAPTER 3: 3 Two Families", book.chapters[2].title)
    }

    @Test
    fun testParseConvertedBookWithFrontMatter() {
        val convertedBook = """
            # White Teeth
            Author: Zadie Smith
            
            Synopsis:
            Zadie Smith's dazzling debut novel exploring multicultural London, faith, and identity.
            
            CHAPTER 1: 1 The Peculiar Second Marriage
            Early in the morning, late in the century.
            
            CHAPTER 2: 2 Teething Trouble
            Clara Bowden was nineteen.
        """.trimIndent()

        val book = TxtParser.parseText(convertedBook)
        assertEquals(3, book.chapters.size)
        assertEquals("Introduction / Front Matter", book.chapters[0].title)
        assertEquals("CHAPTER 1: 1 The Peculiar Second Marriage", book.chapters[1].title)
        assertEquals("CHAPTER 2: 2 Teething Trouble", book.chapters[2].title)
    }

    @Test
    fun testNormalizedBookPreservesChapterStructureAndNewlines() {
        val convertedBook = """
            # White Teeth
            Author: Zadie Smith
            
            CHAPTER 1: 1 The Peculiar Second Marriage
            He paid $25 for 5km of taxi on July 4, 1984.
            
            CHAPTER 2: 2 Teething Trouble
            Dr. Watson met King Henry VIII.
        """.trimIndent()

        val normalized = TextNormalizer.normalizeBookContent(convertedBook)
        // Ensure structural markers were NOT modified
        assertTrue(normalized.contains("CHAPTER 1: 1 The Peculiar Second Marriage"))
        assertTrue(normalized.contains("CHAPTER 2: 2 Teething Trouble"))
        // Ensure numbers and titles INSIDE paragraphs WERE normalized
        assertTrue(normalized.contains("twenty-five dollars for five kilometers"))
        assertTrue(normalized.contains("Doctor Watson met King Henry the Eighth"))

        // Now parse normalized book with TxtParser
        val book = TxtParser.parseText(normalized)
        assertEquals("White Teeth", book.title)
        assertEquals("Zadie Smith", book.author)
        assertEquals(2, book.chapters.size)
        assertEquals("CHAPTER 1: 1 The Peculiar Second Marriage", book.chapters[0].title)
        assertEquals("CHAPTER 2: 2 Teething Trouble", book.chapters[1].title)
    }
}
