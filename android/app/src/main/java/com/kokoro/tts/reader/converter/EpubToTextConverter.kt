package com.kokoro.tts.reader.converter

import android.util.Log
import com.kokoro.tts.engine.normalizer.ArtifactCleaner
import com.kokoro.tts.engine.normalizer.TextNormalizer
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ConvertedBookResult(
    val title: String,
    val author: String,
    val content: String,
    val chapterCount: Int,
    val originalFormat: String
)

object EpubToTextConverter {

    private const val TAG = "EpubToTextConverter"

    fun convert(
        inputStream: InputStream,
        fallbackTitle: String = "Converted Book",
        normalizeSpeech: Boolean = false
    ): ConvertedBookResult {
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

        val opfPath = findOpfPath(files["META-INF/container.xml"]) ?: "content.opf"
        Log.i(TAG, "Located OPF package file: $opfPath")

        val opfBytes = files[opfPath] ?: files.entries.firstOrNull { it.key.endsWith(".opf") }?.value
            ?: throw IllegalArgumentException("Could not find .opf package file in EPUB archive")

        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
        val opfXml = String(opfBytes, Charsets.UTF_8)
        val metadata = parseOpf(opfXml, opfDir)

        val bookTitle = metadata.title.ifBlank { fallbackTitle }
        val bookAuthor = metadata.author.ifBlank { "Unknown Author" }

        val textBuilder = StringBuilder()
        textBuilder.append("# ").append(bookTitle).append("\n")
        textBuilder.append("Author: ").append(bookAuthor).append("\n\n")

        var chapterCount = 0

        for (itemHref in metadata.spineHrefs) {
            val chapterBytes = files[itemHref] ?: files[itemHref.removePrefix("/")]
            if (chapterBytes == null) continue

            val html = String(chapterBytes, Charsets.UTF_8)
            val doc = Jsoup.parse(html)

            val chapterTitle = doc.select("h1, h2, title").firstOrNull()?.text()?.trim()
                ?: "Chapter ${chapterCount + 1}"

            val bodyElements = doc.select("h1, h2, h3, h4, p, li, blockquote")
            val chapterText = StringBuilder()

            if (bodyElements.isNotEmpty()) {
                for (el in bodyElements) {
                    val paragraphText = el.text().trim()
                    if (paragraphText.isNotBlank()) {
                        chapterText.append(paragraphText).append("\n\n")
                    }
                }
            } else {
                val fullBody = doc.body()?.text()?.trim() ?: ""
                if (fullBody.isNotBlank()) {
                    chapterText.append(fullBody).append("\n\n")
                }
            }

            val rawText = chapterText.toString().trim()
            if (rawText.length < 30) {
                // Skip empty or tiny cover / copyright pages
                continue
            }

            var processedText = ArtifactCleaner.clean(rawText)
            if (normalizeSpeech) {
                processedText = TextNormalizer.normalize(processedText)
            }

            chapterCount++
            textBuilder.append("CHAPTER ").append(chapterCount).append(": ").append(chapterTitle).append("\n\n")
            textBuilder.append(processedText).append("\n\n")
        }

        if (chapterCount == 0) {
            // Fallback: extract anything readable from all HTML files
            for ((name, data) in files) {
                if (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) {
                    val doc = Jsoup.parse(String(data, Charsets.UTF_8))
                    val body = doc.body()?.text()?.trim() ?: ""
                    if (body.length > 50) {
                        chapterCount++
                        var processedBody = ArtifactCleaner.clean(body)
                        if (normalizeSpeech) {
                            processedBody = TextNormalizer.normalize(processedBody)
                        }
                        textBuilder.append("CHAPTER ").append(chapterCount).append("\n\n")
                        textBuilder.append(processedBody).append("\n\n")
                    }
                }
            }
        }

        return ConvertedBookResult(
            title = bookTitle,
            author = bookAuthor,
            content = textBuilder.toString().trim(),
            chapterCount = maxOf(1, chapterCount),
            originalFormat = "EPUB"
        )
    }

    private fun findOpfPath(containerXmlBytes: ByteArray?): String? {
        if (containerXmlBytes == null) return null
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(String(containerXmlBytes, Charsets.UTF_8)))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    val fullPath = parser.getAttributeValue(null, "full-path")
                    if (!fullPath.isNullOrBlank()) return fullPath
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing container.xml", e)
            null
        }
    }

    private data class OpfMetadata(
        val title: String,
        val author: String,
        val spineHrefs: List<String>
    )

    private fun parseOpf(opfXml: String, opfDir: String): OpfMetadata {
        var title = ""
        var author = ""
        val manifest = mutableMapOf<String, String>() // id -> href
        val spineIdrefs = mutableListOf<String>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(opfXml))

            var eventType = parser.eventType
            var inTitle = false
            var inCreator = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "title" || name.endsWith(":title")) inTitle = true
                        if (name == "creator" || name.endsWith(":creator")) inCreator = true
                        if (name == "item") {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) {
                                manifest[id] = opfDir + href
                            }
                        }
                        if (name == "itemref") {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) {
                                spineIdrefs.add(idref)
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inTitle && title.isEmpty()) {
                            title = parser.text.trim()
                        }
                        if (inCreator && author.isEmpty()) {
                            author = parser.text.trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "title" || name.endsWith(":title")) inTitle = false
                        if (name == "creator" || name.endsWith(":creator")) inCreator = false
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OPF XML", e)
        }

        val spineHrefs = spineIdrefs.mapNotNull { manifest[it] }
        return OpfMetadata(title, author, spineHrefs)
    }
}
