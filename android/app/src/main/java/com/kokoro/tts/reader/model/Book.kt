package com.kokoro.tts.reader.model

import java.io.Serializable

data class SentenceItem(
    val index: Int,
    val text: String,
    val chapterIndex: Int
) : Serializable

data class Chapter(
    val index: Int,
    val title: String,
    val rawText: String,
    val sentences: List<SentenceItem>
) : Serializable

data class Book(
    val title: String,
    val author: String,
    val chapters: List<Chapter>
) : Serializable {
    val totalSentences: Int
        get() = chapters.sumOf { it.sentences.size }
}
