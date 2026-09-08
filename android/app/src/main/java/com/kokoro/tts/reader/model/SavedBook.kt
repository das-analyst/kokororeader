package com.kokoro.tts.reader.model

import java.io.Serializable

data class SavedBook(
    val id: String,
    val title: String,
    val author: String = "Unknown Author",
    val originalFormat: String, // "EPUB", "PDF", "TXT"
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val chapterCount: Int = 1,
    val sentenceCount: Int = 0,
    var lastReadChapter: Int = 0,
    var lastReadSentence: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis()
) : Serializable
