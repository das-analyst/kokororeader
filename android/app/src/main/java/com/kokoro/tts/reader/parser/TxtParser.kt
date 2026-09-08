package com.kokoro.tts.reader.parser

import com.kokoro.tts.engine.TextSplitter
import com.kokoro.tts.reader.model.Book
import com.kokoro.tts.reader.model.Chapter
import com.kokoro.tts.reader.model.SentenceItem
import java.io.File
import java.io.InputStream

object TxtParser {

    fun parse(file: File, fallbackTitle: String = file.nameWithoutExtension): Book {
        val text = file.readText(Charsets.UTF_8).trim()
        return parseText(text, fallbackTitle)
    }

    fun parse(inputStream: InputStream, title: String = "Plain Text Document"): Book {
        val fullText = inputStream.bufferedReader().use { it.readText() }.trim()
        return parseText(fullText, title)
    }

    fun parseText(text: String, title: String = "Sample Ebook"): Book {
        // Look for Chapter headers (e.g. "Chapter 1", "CHAPTER 1: Introduction", "CHAPTER 1 (Pages 1-10)", etc.)
        val chapterRegex = Regex(
            "(?i)(?:^|\\n\\n+)(?:#+\\s+|===\\s*)?(Chapter\\s+[0-9IVXLCDM]+[^\\n]*|Section\\s+[0-9IVXLCDM]+[^\\n]*|Part\\s+[0-9IVXLCDM]+[^\\n]*)",
            RegexOption.MULTILINE
        )
        val matches = chapterRegex.findAll(text).toList()

        val chapters = mutableListOf<Chapter>()

        if (matches.size > 1) {
            for (i in matches.indices) {
                val start = matches[i].range.first
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
                val chapterBlock = text.substring(start, end).trim()
                val chapterTitle = matches[i].groupValues[1].replace(Regex("[#=]+"), "").trim()

                val rawSentences = TextSplitter.splitIntoSentences(chapterBlock)
                val sentences = rawSentences.mapIndexed { idx, s ->
                    SentenceItem(idx, s, i)
                }

                if (sentences.isNotEmpty()) {
                    chapters.add(Chapter(i, chapterTitle, chapterBlock, sentences))
                }
            }
        } else {
            // If text is very long without explicit chapter headers, split by ~100 sentences per chapter
            val rawSentences = TextSplitter.splitIntoSentences(text)
            if (rawSentences.size > 120) {
                val chunkSize = 80
                val sentenceChunks = rawSentences.chunked(chunkSize)
                for ((idx, chunk) in sentenceChunks.withIndex()) {
                    val chapterTitle = "Section ${idx + 1}"
                    val chapterText = chunk.joinToString(" ")
                    val sentences = chunk.mapIndexed { sIdx, s ->
                        SentenceItem(sIdx, s, idx)
                    }
                    chapters.add(Chapter(idx, chapterTitle, chapterText, sentences))
                }
            } else {
                val sentences = rawSentences.mapIndexed { idx, s ->
                    SentenceItem(idx, s, 0)
                }
                chapters.add(Chapter(0, title, text, sentences))
            }
        }

        return Book(title, "Jane Austen", chapters)
    }
}
