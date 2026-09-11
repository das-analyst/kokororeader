package com.kokoro.tts.engine

import com.kokoro.tts.engine.director.SpeechDirector
import com.kokoro.tts.reader.model.SentenceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechDirectorTest {

    @Test
    fun testDialogueClassificationAndCadence() {
        val standardDialogue = SentenceItem(
            index = 0,
            text = "\"We have to go back,\" said Jack.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )

        val directed = SpeechDirector.direct(standardDialogue, isLastSentenceInChapter = false, baseSpeed = 1.0f)
        assertEquals(SpeechDirector.SentenceRole.DIALOGUE, directed.role)
        assertTrue(directed.isExpressive)
        // Spoken dialogue briskly accelerated (1.08x)
        assertEquals(1.08f, directed.effectiveSpeed, 0.001f)
        assertEquals(SpeechDirector.SILENCE_DIALOGUE_MS, directed.postSilenceMs)
    }

    @Test
    fun testDialogueExclamationCadence() {
        val exclamationDialogue = SentenceItem(
            index = 0,
            text = "\"We have to go back!\" insisted Jack.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )

        val directed = SpeechDirector.direct(exclamationDialogue, isLastSentenceInChapter = false, baseSpeed = 1.0f)
        assertEquals(SpeechDirector.SentenceRole.DIALOGUE, directed.role)
        assertTrue(directed.isExpressive)
        // Urgent exclamation in dialogue (1.12x)
        assertEquals(1.12f, directed.effectiveSpeed, 0.001f)
        assertEquals(SpeechDirector.SILENCE_DIALOGUE_MS, directed.postSilenceMs)
    }

    @Test
    fun testQuestionCadence() {
        val questionSentence = SentenceItem(
            index = 1,
            text = "Could it really be true?",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = false
        )

        val directed = SpeechDirector.direct(questionSentence, isLastSentenceInChapter = false, baseSpeed = 1.0f)
        assertEquals(SpeechDirector.SentenceRole.QUESTION, directed.role)
        // Question deliberate and reflective (0.92x)
        assertEquals(0.92f, directed.effectiveSpeed, 0.001f)
        assertEquals(SpeechDirector.SILENCE_QUESTION_MS, directed.postSilenceMs)
        assertEquals(500L, directed.postSilenceMs)
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
        // Headings slower with solemn gravitas (0.85x)
        assertEquals(0.85f, directed.effectiveSpeed, 0.001f)
        assertEquals(SpeechDirector.SILENCE_PARAGRAPH_BREAK_MS, directed.postSilenceMs)
        assertEquals(1400L, directed.postSilenceMs)
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
        assertEquals(1400L, directed.postSilenceMs)
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
        assertEquals(2400L, directed.postSilenceMs)
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

    @Test
    fun testWhisperDialogueEmotionalProsody() {
        val whisperSentence = SentenceItem(
            index = 0,
            text = "\"Stay back,\" she whispered into the darkness.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )

        // Female base voice (e.g. af_heart) -> blends af_nicole
        val directedFemale = SpeechDirector.direct(whisperSentence, baseVoice = "af_heart")
        assertEquals(SpeechDirector.SentenceRole.DIALOGUE, directedFemale.role)
        assertEquals("af_nicole", directedFemale.donorVoice)
        assertEquals(0.50f, directedFemale.blendWeight, 0.001f)
        assertEquals(0.90f, directedFemale.effectiveSpeed, 0.001f)
        assertEquals(550L, directedFemale.postSilenceMs)

        // Male base voice (e.g. am_onyx) -> blends am_michael
        val directedMale = SpeechDirector.direct(whisperSentence, baseVoice = "am_onyx")
        assertEquals("am_michael", directedMale.donorVoice)
        assertEquals(0.50f, directedMale.blendWeight, 0.001f)
        assertEquals(0.90f, directedMale.effectiveSpeed, 0.001f)
    }

    @Test
    fun testShoutingDialogueEmotionalProsody() {
        val shoutSentence = SentenceItem(
            index = 0,
            text = "\"Thomas, answer me!\" she called frantically.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )

        // Female base voice -> blends af_bella with urgent tempo (1.14x)
        val directedFemale = SpeechDirector.direct(shoutSentence, baseVoice = "af_heart")
        assertEquals(SpeechDirector.SentenceRole.DIALOGUE, directedFemale.role)
        assertEquals("af_bella", directedFemale.donorVoice)
        assertEquals(0.45f, directedFemale.blendWeight, 0.001f)
        assertEquals(1.14f, directedFemale.effectiveSpeed, 0.001f)

        // Male base voice -> blends am_adam
        val directedMale = SpeechDirector.direct(shoutSentence, baseVoice = "am_onyx")
        assertEquals("am_adam", directedMale.donorVoice)
        assertEquals(0.45f, directedMale.blendWeight, 0.001f)
        assertEquals(1.14f, directedMale.effectiveSpeed, 0.001f)
    }

    @Test
    fun testSolemnCadenceEmotionalProsody() {
        val solemnSentence = SentenceItem(
            index = 0,
            text = "There was no answer, only the cold wind sighing mournfully.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = false
        )

        val directed = SpeechDirector.direct(solemnSentence, baseVoice = "af_heart")
        assertEquals(SpeechDirector.SentenceRole.NARRATION, directed.role)
        assertEquals("af_sarah", directed.donorVoice)
        assertEquals(0.35f, directed.blendWeight, 0.001f)
        assertEquals(650L, directed.postSilenceMs)
    }

    @Test
    fun testInterruptedUtteranceCadence() {
        val interruptedSentence = SentenceItem(
            index = 0,
            text = "\"Wait, I was just going to—\"",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )

        val directed = SpeechDirector.direct(interruptedSentence, baseVoice = "af_heart")
        // Interrupted speech abruptly cuts off with 200ms post-silence
        assertEquals(SpeechDirector.SILENCE_INTERRUPTED_MS, directed.postSilenceMs)
        assertEquals(200L, directed.postSilenceMs)
    }

    @Test
    fun testParentheticalAsideNuance() {
        val asideSentence = SentenceItem(
            index = 0,
            text = "She had never seen him (though she knew his reputation) in such spirits.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = false
        )

        val directed = SpeechDirector.direct(asideSentence, baseVoice = "af_heart")
        // Nuanced pacing (0.97x aside multiplier)
        assertEquals(0.97f, directed.effectiveSpeed, 0.001f)
    }
}
