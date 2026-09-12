package com.kokoro.tts.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSplitterTest {

    @Test
    fun testNameWithInitialsStayInSingleSentence() {
        val input = "Mr. C. M. Park was an upright man."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(listOf("Mr. C. M. Park was an upright man."), sentences)
    }

    @Test
    fun testUnspacedInitialsStayInSingleSentence() {
        val input = "I saw Mr. C.M. Park yesterday."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(listOf("I saw Mr. C.M. Park yesterday."), sentences)
    }

    @Test
    fun testFamousAuthorsAndInitials() {
        val input = "The book was written by E. M. Forster. It was a masterpiece."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(
            listOf(
                "The book was written by E. M. Forster.",
                "It was a masterpiece."
            ),
            sentences
        )
    }

    @Test
    fun testMultipleSentencesWithInitials() {
        val input = "Arthur C. Clarke wrote 2001. Isaac Asimov wrote Foundation."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(
            listOf(
                "Arthur C. Clarke wrote 2001.",
                "Isaac Asimov wrote Foundation."
            ),
            sentences
        )
    }

    @Test
    fun testChainedInitialsAuthors() {
        val input = "J. K. Rowling wrote Harry Potter. J. R. R. Tolkien wrote Lord of the Rings."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(
            listOf(
                "J. K. Rowling wrote Harry Potter.",
                "J. R. R. Tolkien wrote Lord of the Rings."
            ),
            sentences
        )
    }

    @Test
    fun testMiddleInitials() {
        val input = "John F. Kennedy gave an inspiring speech. He was the president."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(
            listOf(
                "John F. Kennedy gave an inspiring speech.",
                "He was the president."
            ),
            sentences
        )
    }

    @Test
    fun testTitlesWithInitials() {
        val input = "Dr. J. Watson examined the clue. Mrs. M. A. Bennett had five daughters."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(
            listOf(
                "Dr. J. Watson examined the clue.",
                "Mrs. M. A. Bennett had five daughters."
            ),
            sentences
        )
    }

    @Test
    fun testDecimalsAndAcronyms() {
        val input = "The train arrives at 3:30 p.m. Don't be late. It was Washington, D.C. where they met."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(
            listOf(
                "The train arrives at 3:30 p.m.",
                "Don't be late.",
                "It was Washington, D.C. where they met."
            ),
            sentences
        )
    }

    @Test
    fun testLatinAbbreviations() {
        val input = "He bought apples, oranges, etc. Then he left. Use standard methods (e.g. linear regression) for analysis."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(
            listOf(
                "He bought apples, oranges, etc.",
                "Then he left.",
                "Use standard methods (e.g. linear regression) for analysis."
            ),
            sentences
        )
    }

    @Test
    fun testStreetAndSaintDisambiguation() {
        val input = "King Henry VIII visited Baker St. with Dr. Watson. St. Jude was revered."
        val sentences = TextSplitter.splitIntoSentences(input)
        assertEquals(
            listOf(
                "King Henry VIII visited Baker St. with Dr. Watson.",
                "St. Jude was revered."
            ),
            sentences
        )
    }
}
