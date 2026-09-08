package com.kokoro.tts.reader.converter

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

object PdfToTextConverter {

    private const val TAG = "PdfToTextConverter"
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isInitialized = true
                Log.i(TAG, "PDFBoxResourceLoader initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PDFBoxResourceLoader", e)
            }
        }
    }

    fun convert(
        context: Context,
        inputStream: InputStream,
        fallbackTitle: String = "Converted PDF Document"
    ): ConvertedBookResult {
        init(context)

        var document: PDDocument? = null
        try {
            document = PDDocument.load(inputStream)
            val numberOfPages = document.numberOfPages
            Log.i(TAG, "Opened PDF with $numberOfPages pages")

            val bookTitle = document.documentInformation?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val bookAuthor = document.documentInformation?.author?.takeIf { it.isNotBlank() } ?: "Unknown Author"

            val stripper = PDFTextStripper()
            val textBuilder = StringBuilder()
            textBuilder.append("# ").append(bookTitle).append("\n")
            textBuilder.append("Author: ").append(bookAuthor).append("\n\n")

            // Determine chapter grouping: for large PDFs group ~10-15 pages per chapter
            val pagesPerChapter = when {
                numberOfPages <= 15 -> numberOfPages
                numberOfPages <= 50 -> 10
                else -> 15
            }

            var chapterIndex = 0
            var currentPage = 1

            while (currentPage <= numberOfPages) {
                chapterIndex++
                val endPage = minOf(currentPage + pagesPerChapter - 1, numberOfPages)
                stripper.startPage = currentPage
                stripper.endPage = endPage

                val rawPageText = stripper.getText(document)
                val cleanedText = cleanPdfText(rawPageText)

                if (cleanedText.isNotBlank()) {
                    textBuilder.append("CHAPTER ").append(chapterIndex)
                    if (numberOfPages > 1) {
                        textBuilder.append(" (Pages ").append(currentPage).append("-").append(endPage).append(")")
                    }
                    textBuilder.append("\n\n")
                    textBuilder.append(cleanedText).append("\n\n")
                }

                currentPage = endPage + 1
            }

            return ConvertedBookResult(
                title = bookTitle,
                author = bookAuthor,
                content = textBuilder.toString().trim(),
                chapterCount = maxOf(1, chapterIndex),
                originalFormat = "PDF"
            )
        } finally {
            try {
                document?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing PDF document", e)
            }
        }
    }

    /**
     * Cleans common PDF text extraction artifacts:
     * - Fixes hyphenated line breaks: "infor-\nmation" -> "information"
     * - Removes single line page numbers: "\n 42 \n"
     * - Normalizes irregular spaces
     */
    private fun cleanPdfText(raw: String): String {
        var text = raw

        // Fix hyphenation at line breaks: word-\nword -> wordword
        text = text.replace(Regex("([a-zA-Z]+)-\\s*\\r?\\n\\s*([a-zA-Z]+)"), "$1$2")

        val lines = text.lines()
        val cleanedLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            // Skip standalone page numbers or blank lines
            if (trimmed.matches(Regex("^\\d+$"))) continue
            cleanedLines.add(line)
        }

        // Collapse excessive newlines
        val joined = cleanedLines.joinToString("\n")
        return joined.replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
