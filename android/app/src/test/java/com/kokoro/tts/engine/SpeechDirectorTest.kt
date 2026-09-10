package com.kokoro.tts.engine

import com.kokoro.tts.engine.director.SpeechDirector
import com.kokoro.tts.reader.model.SentenceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechDirectorTest {

    @Test
    fun testDialogueClassificationAndCadence() {
        val dialogueSentence = SentenceItem(
            index = 0,
            text = "\"We have to go back!\" shouted Jack.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )

        val directed = SpeechDirector.direct(dialogueSentence, isLastSentenceInChapter = false, baseSpeed = 1.0f)
        assertEquals(SpeechDirector.SentenceRole.DIALOGUE, directed.role)
        assertTrue(directed.isExpressive)
        // Spoken dialogue slightly accelerated (1.03x)
        assertEquals(1.03f, directed.effectiveSpeed, 0.001f)
        assertEquals(SpeechDirector.SILENCE_DIALOGUE_MS, directed.postSilenceMs)
    }

    @Test
    fun testHeadingClassificationAndGravitas() {
        val headingSentence = SentenceItem(
            index = 0,
            text = "CHAPTER 1: The Beginning",
            chapterIndex = 0,
            isParagraphEnd = true,
            isDialogue = false
        )

        val directed = SpeechDirector.direct(headingSentence, isLastSentenceInChapter = false, baseSpeed = 1.0f)
        assertEquals(SpeechDirector.SentenceRole.HEADING, directed.role)
        // Headings slower and deliberate (0.92x)
        assertEquals(0.92f, directed.effectiveSpeed, 0.001f)
        assertEquals(SpeechDirector.SILENCE_PARAGRAPH_BREAK_MS, directed.postSilenceMs)
    }

    @Test
    fun testParagraphEndCadence() {
        val paragraphEndSentence = SentenceItem(
            index = 5,
            text = "And they walked into the quiet night.",
            chapterIndex = 0,
            isParagraphEnd = true,
            isDialogue = false
        )

        val directed = SpeechDirector.direct(paragraphEndSentence, isLastSentenceInChapter = false, baseSpeed = 1.0f)
        assertEquals(SpeechDirector.SentenceRole.NARRATION, directed.role)
        assertEquals(SpeechDirector.SILENCE_PARAGRAPH_BREAK_MS, directed.postSilenceMs)
    }

    @Test
    fun testChapterEndCadence() {
        val lastSentence = SentenceItem(
            index = 20,
            text = "End of Chapter One.",
            chapterIndex = 0,
            isParagraphEnd = true,
            isDialogue = false
        )

        val directed = SpeechDirector.direct(lastSentence, isLastSentenceInChapter = true, baseSpeed = 1.0f)
        assertEquals(SpeechDirector.SILENCE_CHAPTER_END_MS, directed.postSilenceMs)
    }

    @Test
    fun testSilencePcmGeneration() {
        val sampleRate = 24000
        val durationMs = 500L
        val pcm = SpeechDirector.generateSilencePcm(durationMs, sampleRate)

        // 24000 samples/sec * 0.5 sec = 12000 samples * 2 bytes/sample = 24000 bytes
        assertEquals(24000, pcm.size)
        // Every byte should be pure 0x00 silence
        for (b in pcm) {
            assertEquals(0.toByte(), b)
        }
    }
}
