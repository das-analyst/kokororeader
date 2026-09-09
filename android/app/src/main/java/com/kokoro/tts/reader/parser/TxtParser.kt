package com.kokoro.tts.reader.parser

import com.kokoro.tts.engine.TextSplitter
import com.kokoro.tts.reader.converter.ChapterDetector
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

    fun parseText(text: String, fallbackTitle: String = "Sample Ebook"): Book {
        if (text.isBlank()) {
            return Book(fallbackTitle, "Unknown Author", emptyList())
        }

        // 1. Extract metadata from header if present (# Title, Author: ...)
        var bookTitle = fallbackTitle
        var bookAuthor = "Unknown Author"

        val lines = text.lines()
        for (line in lines.take(15)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                bookTitle = trimmed.removePrefix("# ").trim()
            } else if (trimmed.startsWith("Author:", ignoreCase = true)) {
                bookAuthor = trimmed.substringAfter(":").trim()
            }
        }

        // 2. Use ChapterDetector to detect semantic chapters across the book
        val detection = ChapterDetector.detect(text)
        val chapters = mutableListOf<Chapter>()

        if (detection.chapters.isNotEmpty()) {
            // If there is meaningful front matter (e.g. synopsis, preface, dedication)
            if (detection.frontMatter.isNotBlank()) {
                val cleanedFm = detection.frontMatter.lines()
                    .filterNot { it.trim().startsWith("# ") || it.trim().startsWith("Author:", ignoreCase = true) }
                    .joinToString("\n")
                    .trim()

                if (cleanedFm.length > 50) {
                    val fmSentences = TextSplitter.splitIntoSentences(cleanedFm).mapIndexed { sIdx, s ->
                        SentenceItem(sIdx, s, 0)
                    }
                    if (fmSentences.isNotEmpty()) {
                        chapters.add(Chapter(0, "Introduction / Front Matter", cleanedFm, fmSentences))
                    }
                }
            }

            for (ch in detection.chapters) {
                val chIndex = chapters.size
                val rawSentences = TextSplitter.splitIntoSentences(ch.content)
                val sentences = rawSentences.mapIndexed { sIdx, s ->
                    SentenceItem(sIdx, s, chIndex)
                }
                chapters.add(Chapter(chIndex, ch.title, ch.content, sentences))
            }

            return Book(bookTitle, bookAuthor, chapters)
        }

        // 3. Fallback: Check for classic chapter headers
        val chapterRegex = Regex(
            "(?i)(?:^|\\r?\\n+)(?:#+\\s+|===\\s*)?(Chapter\\s+[0-9IVXLCDM]+[^\\r\\n]*|Section\\s+[0-9IVXLCDM]+[^\\r\\n]*|Part\\s+[0-9IVXLCDM]+[^\\r\\n]*)",
            RegexOption.MULTILINE
        )
        val matches = chapterRegex.findAll(text).toList()

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
            return Book(bookTitle, bookAuthor, chapters)
        }

        // 4. Final Fallback: Unstructured text chunked into ~80 sentences per section
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
            chapters.add(Chapter(0, bookTitle, text, sentences))
        }

        return Book(bookTitle, bookAuthor, chapters)
    }
}
