package com.kokoro.tts.reader.parser

import com.kokoro.tts.engine.TextSplitter
import com.kokoro.tts.reader.model.Book
import com.kokoro.tts.reader.model.Chapter
import com.kokoro.tts.reader.model.SentenceItem
import java.io.InputStream

object TxtParser {

    fun parse(inputStream: InputStream, title: String = "Plain Text Document"): Book {
        val fullText = inputStream.bufferedReader().use { it.readText() }.trim()
        return parseText(fullText, title)
    }

    fun parseText(text: String, title: String = "Sample Ebook"): Book {
        // Look for Chapter headers (e.g. "Chapter 1", "CHAPTER II", etc.)
        val chapterRegex = Regex("(?i)(?:^|\\n\\n+)(Chapter\\s+[0-9IVXLCDM]+[^\\n]*)", RegexOption.MULTILINE)
        val matches = chapterRegex.findAll(text).toList()

        val chapters = mutableListOf<Chapter>()

        if (matches.size > 1) {
            for (i in matches.indices) {
                val start = matches[i].range.first
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
                val chapterBlock = text.substring(start, end).trim()
                val chapterTitle = matches[i].groupValues[1].trim()

                val rawSentences = TextSplitter.splitIntoSentences(chapterBlock)
                val sentences = rawSentences.mapIndexed { idx, s ->
                    SentenceItem(idx, s, i)
                }

                if (sentences.isNotEmpty()) {
                    chapters.add(Chapter(i, chapterTitle, chapterBlock, sentences))
                }
            }
        } else {
            // Single chapter
            val rawSentences = TextSplitter.splitIntoSentences(text)
            val sentences = rawSentences.mapIndexed { idx, s ->
                SentenceItem(idx, s, 0)
            }
            chapters.add(Chapter(0, title, text, sentences))
        }

        return Book(title, "Jane Austen", chapters)
    }
}
