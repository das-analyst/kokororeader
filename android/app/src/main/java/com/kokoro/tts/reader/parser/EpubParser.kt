package com.kokoro.tts.reader.parser

import android.util.Log
import com.kokoro.tts.engine.TextSplitter
import com.kokoro.tts.reader.model.Book
import com.kokoro.tts.reader.model.Chapter
import com.kokoro.tts.reader.model.SentenceItem
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Lightweight native EPUB 2 and EPUB 3 parser.
 * Unzips in-memory or from stream, parses package OPF, and extracts chapter text.
 */
object EpubParser {

    private const val TAG = "EpubParser"

    fun parse(inputStream: InputStream, fallbackTitle: String = "Untitled Book"): Book {
        val files = mutableMapOf<String, ByteArray>()
        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                files[entry.name] = zis.readBytes()
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        // 1. Locate rootfile OPF via META-INF/container.xml
        val opfPath = findOpfPath(files["META-INF/container.xml"]) ?: "content.opf"
        Log.i(TAG, "Located OPF package file: $opfPath")

        val opfBytes = files[opfPath] ?: files.entries.firstOrNull { it.key.endsWith(".opf") }?.value
        requireNotNull(opfBytes) { "Failed to find package document (.opf) in EPUB" }

        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        // 2. Parse OPF metadata, manifest, and spine
        val opfXml = String(opfBytes, Charsets.UTF_8)
        val metadata = parseOpf(opfXml, opfDir)

        val bookTitle = metadata.title.ifBlank { fallbackTitle }
        val bookAuthor = metadata.author.ifBlank { "Unknown Author" }

        // 3. Extract chapters in spine order
        val chapters = mutableListOf<Chapter>()
        var chapterIndex = 0

        for (itemHref in metadata.spineHrefs) {
            val chapterBytes = files[itemHref] ?: files[itemHref.removePrefix("/")]
            if (chapterBytes == null) {
                Log.w(TAG, "Spine item not found in archive: $itemHref")
                continue
            }

            val html = String(chapterBytes, Charsets.UTF_8)
            val doc = Jsoup.parse(html)

            // Extract headings or title
            val chapterTitle = doc.select("h1, h2, title").firstOrNull()?.text()?.trim()
                ?: "Chapter ${chapterIndex + 1}"

            // Extract readable text from paragraphs, headers, quotes, and lists
            var bodyElements = doc.select("h1, h2, h3, h4, h5, h6, p, li, blockquote")
            if (bodyElements.isEmpty()) {
                bodyElements = doc.select("div, h1, h2, h3, h4, h5, h6, p, li, blockquote")
            }
            val textBuilder = StringBuilder()
            if (bodyElements.isNotEmpty()) {
                for (el in bodyElements) {
                    val t = el.text().trim()
                    if (t.isNotBlank()) {
                        textBuilder.append(t).append("\n\n")
                    }
                }
            } else {
                textBuilder.append(doc.body()?.text() ?: "")
            }

            val fullText = textBuilder.toString().trim()
            if (fullText.isBlank() || fullText.length < 20) {
                // Skip empty or trivial pages (like cover wrappers or blank divider pages)
                continue
            }

            // Segment chapter text into sentences with paragraph and dialogue awareness
            val sentenceItems = buildSentences(fullText, chapterIndex)

            if (sentenceItems.isNotEmpty()) {
                chapters.add(
                    Chapter(
                        index = chapterIndex,
                        title = chapterTitle,
                        rawText = fullText,
                        sentences = sentenceItems
                    )
                )
                chapterIndex++
            }
        }

        Log.i(TAG, "Successfully parsed EPUB: '$bookTitle' by '$bookAuthor' (${chapters.size} chapters)")
        return Book(bookTitle, bookAuthor, chapters)
    }

    private fun findOpfPath(containerBytes: ByteArray?): String? {
        if (containerBytes == null) return null
        return try {
            val xml = String(containerBytes, Charsets.UTF_8)
            val match = Regex("""full-path\s*=\s*["']([^"']+)["']""").find(xml)
            match?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding OPF path from container.xml", e)
            null
        }
    }

    private data class OpfMetadata(
        val title: String,
        val author: String,
        val spineHrefs: List<String>
    )

    private fun parseOpf(xml: String, opfDir: String): OpfMetadata {
        var title = ""
        var author = ""
        val manifest = mutableMapOf<String, String>() // id -> href
        val spine = mutableListOf<String>()           // list of idrefs

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase()
                        when (currentTag) {
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                if (id != null && href != null) {
                                    manifest[id] = opfDir + href
                                }
                            }
                            "itemref" -> {
                                val idref = parser.getAttributeValue(null, "idref")
                                if (idref != null) {
                                    spine.add(idref)
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotBlank()) {
                            when (currentTag) {
                                "dc:title", "title" -> if (title.isBlank()) title = text
                                "dc:creator", "creator" -> if (author.isBlank()) author = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OPF XML", e)
        }

        val spineHrefs = spine.mapNotNull { manifest[it] }
        return OpfMetadata(title, author, if (spineHrefs.isNotEmpty()) spineHrefs else manifest.values.toList())
    }

    private fun buildSentences(content: String, chapterIndex: Int): List<SentenceItem> {
        val rawParagraphs = content.split(Regex("(\\r?\\n)[ \\t]*(\\r?\\n)+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val paragraphs = if (rawParagraphs.size <= 1 && content.lines().count { it.isNotBlank() } > 1) {
            content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            rawParagraphs
        }

        val result = mutableListOf<SentenceItem>()
        var globalIdx = 0

        for (paragraph in paragraphs) {
            val pSentences = TextSplitter.splitIntoSentences(paragraph)
            for ((sIdx, sText) in pSentences.withIndex()) {
                val isLastInParagraph = sIdx == pSentences.size - 1
                val isDialogue = isDialogue(sText)
                result.add(
                    SentenceItem(
                        index = globalIdx++,
                        text = sText,
                        chapterIndex = chapterIndex,
                        isParagraphEnd = isLastInParagraph,
                        isDialogue = isDialogue
                    )
                )
            }
        }
        return if (result.isNotEmpty()) result else {
            TextSplitter.splitIntoSentences(content).mapIndexed { idx, s ->
                SentenceItem(idx, s, chapterIndex, isParagraphEnd = true, isDialogue = isDialogue(s))
            }
        }
    }

    private fun isDialogue(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("\"") || t.startsWith("“") || t.startsWith("‘") ||
                t.endsWith("\"") || t.endsWith("”") || t.endsWith("’") ||
                (t.contains("\"") && t.indexOf("\"") != t.lastIndexOf("\"")) ||
                (t.contains("“") && t.contains("”"))
    }
}
