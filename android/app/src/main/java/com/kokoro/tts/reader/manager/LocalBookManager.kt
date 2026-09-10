package com.kokoro.tts.reader.manager

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.kokoro.tts.reader.model.SampleBook
import com.kokoro.tts.reader.model.SavedBook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID

class LocalBookManager(private val context: Context) {

    companion object {
        private const val TAG = "LocalBookManager"
        private const val PREFS_NAME = "kokoro_saved_books_meta"
        private const val KEY_BOOKS_JSON = "saved_books_list"
    }

    private val booksDir: File by lazy {
        File(context.filesDir, "books").apply {
            if (!exists()) mkdirs()
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        seedSampleBookIfEmpty()
    }

    @Synchronized
    fun getAllSavedBooks(): List<SavedBook> {
        val jsonStr = prefs.getString(KEY_BOOKS_JSON, null) ?: return emptyList()
        val list = mutableListOf<SavedBook>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val filePath = obj.optString("filePath")
                val file = File(filePath)
                if (file.exists()) {
                    list.add(
                        SavedBook(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            author = obj.optString("author", "Unknown Author"),
                            originalFormat = obj.optString("originalFormat", "TXT"),
                            fileName = obj.optString("fileName", file.name),
                            filePath = filePath,
                            fileSizeBytes = file.length(),
                            chapterCount = obj.optInt("chapterCount", 1),
                            sentenceCount = obj.optInt("sentenceCount", 0),
                            lastReadChapter = obj.optInt("lastReadChapter", 0),
                            lastReadSentence = obj.optInt("lastReadSentence", 0),
                            createdTimestamp = obj.optLong("createdTimestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse saved books metadata", e)
        }
        return list.sortedByDescending { it.createdTimestamp }
    }

    @Synchronized
    fun saveBook(
        title: String,
        author: String,
        originalFormat: String,
        content: String,
        chapterCount: Int = 1
    ): SavedBook {
        val id = UUID.randomUUID().toString()
        val safeBaseName = title.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").take(30)
        val fileName = "${safeBaseName}_${System.currentTimeMillis() % 10000}.txt"
        val file = File(booksDir, fileName)

        file.writeText(content, Charsets.UTF_8)

        val savedBook = SavedBook(
            id = id,
            title = title.trim(),
            author = author.trim(),
            originalFormat = originalFormat.uppercase(),
            fileName = fileName,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            chapterCount = chapterCount,
            createdTimestamp = System.currentTimeMillis()
        )

        val currentList = getAllSavedBooks().toMutableList()
        currentList.add(0, savedBook)
        saveMetadata(currentList)

        Log.i(TAG, "Saved book '${savedBook.title}' to ${file.absolutePath} (${file.length()} bytes)")
        return savedBook
    }

    @Synchronized
    fun updateReadingPosition(bookId: String, chapter: Int, sentence: Int) {
        val currentList = getAllSavedBooks().toMutableList()
        val book = currentList.find { it.id == bookId }
        if (book != null) {
            book.lastReadChapter = chapter
            book.lastReadSentence = sentence
            saveMetadata(currentList)
        }
    }

    @Synchronized
    fun deleteBook(bookId: String): Boolean {
        val currentList = getAllSavedBooks().toMutableList()
        val book = currentList.find { it.id == bookId } ?: return false
        val file = File(book.filePath)
        if (file.exists()) {
            file.delete()
        }
        currentList.remove(book)
        saveMetadata(currentList)
        Log.i(TAG, "Deleted book id $bookId (${book.title})")
        return true
    }

    fun exportBook(savedBook: SavedBook, outputStream: OutputStream) {
        val file = File(savedBook.filePath)
        if (!file.exists()) throw IllegalArgumentException("Book file not found")
        file.inputStream().use { input ->
            input.copyTo(outputStream)
        }
        outputStream.flush()
    }

    /**
     * Saves a text book directly to the device's public Downloads folder.
     * Uses MediaStore on Android 10+ (API 29+) with zero special permissions needed.
     */
    fun saveBookToDownloads(title: String, content: String): Uri? {
        val safeFileName = "${title.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")}.txt"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeFileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                    Log.i(TAG, "Saved '$safeFileName' to Downloads via MediaStore: $uri")
                }
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, safeFileName)
                file.writeText(content, Charsets.UTF_8)
                Log.i(TAG, "Saved '$safeFileName' to Downloads via File: ${file.absolutePath}")
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save book to Downloads folder", e)
            null
        }
    }

    private fun saveMetadata(books: List<SavedBook>) {
        val arr = JSONArray()
        for (b in books) {
            val obj = JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("author", b.author)
                put("originalFormat", b.originalFormat)
                put("fileName", b.fileName)
                put("filePath", b.filePath)
                put("chapterCount", b.chapterCount)
                put("sentenceCount", b.sentenceCount)
                put("lastReadChapter", b.lastReadChapter)
                put("lastReadSentence", b.lastReadSentence)
                put("createdTimestamp", b.createdTimestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_BOOKS_JSON, arr.toString()).apply()
    }

    private fun seedSampleBookIfEmpty() {
        val existing = prefs.getString(KEY_BOOKS_JSON, null)
        if (existing.isNullOrBlank() || existing == "[]") {
            try {
                val sampleBook = SampleBook.SAMPLE_BOOK
                val textBuilder = StringBuilder()
                textBuilder.append("# ").append(sampleBook.title).append("\n")
                textBuilder.append("Author: ").append(sampleBook.author).append("\n\n")

                for (ch in sampleBook.chapters) {
                    textBuilder.append("CHAPTER ").append(ch.index + 1).append(": ").append(ch.title).append("\n\n")
                    textBuilder.append(ch.rawText).append("\n\n")
                }

                saveBook(
                    title = sampleBook.title,
                    author = sampleBook.author,
                    originalFormat = "TXT",
                    content = textBuilder.toString(),
                    chapterCount = sampleBook.chapters.size
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to seed sample book", e)
            }
        }
    }
}
