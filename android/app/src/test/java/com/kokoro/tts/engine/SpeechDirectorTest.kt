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

    @Test
    fun testExpressionIntensityScaling() {
        val whisperSentence = SentenceItem(
            index = 0,
            text = "\"Stay back,\" she whispered into the darkness.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )

        // 1. Intensity 0.0: Flat cadence (drone-resistant predictability), 0 donor blend
        val configFlat = SpeechDirector.DirectorConfig(expressionIntensity = 0.0f)
        val directedFlat = SpeechDirector.direct(whisperSentence, baseVoice = "af_heart", config = configFlat)
        assertEquals(1.00f, directedFlat.effectiveSpeed, 0.001f)
        assertEquals(null, directedFlat.donorVoice)
        assertEquals(0.0f, directedFlat.blendWeight, 0.001f)

        // 2. Intensity 0.5: Half variance (0.95x speed, 0.25 blend weight)
        val configHalf = SpeechDirector.DirectorConfig(expressionIntensity = 0.5f)
        val directedHalf = SpeechDirector.direct(whisperSentence, baseVoice = "af_heart", config = configHalf)
        assertEquals(0.95f, directedHalf.effectiveSpeed, 0.001f)
        assertEquals("af_nicole", directedHalf.donorVoice)
        assertEquals(0.25f, directedHalf.blendWeight, 0.001f)

        // 3. Intensity 1.0: Full dynamic drama (0.90x speed, 0.50 blend weight)
        val configFull = SpeechDirector.DirectorConfig(expressionIntensity = 1.0f)
        val directedFull = SpeechDirector.direct(whisperSentence, baseVoice = "af_heart", config = configFull)
        assertEquals(0.90f, directedFull.effectiveSpeed, 0.001f)
        assertEquals("af_nicole", directedFull.donorVoice)
        assertEquals(0.50f, directedFull.blendWeight, 0.001f)
    }

    @Test
    fun testTemperamentPresets() {
        val standardSentence = SentenceItem(
            index = 0,
            text = "It was a dark and stormy night.",
            chapterIndex = 0,
            isParagraphEnd = true,
            isDialogue = false
        )

        // Calm: 0.94x base speed, +35% pauses (1400 * 1.35 = 1890ms), persona donor af_sarah
        val calmConfig = SpeechDirector.DirectorConfig(
            temperament = SpeechDirector.TemperamentPreset.CALM,
            expressionIntensity = 0.50f
        )
        val directedCalm = SpeechDirector.direct(standardSentence, baseSpeed = 1.0f, baseVoice = "af_heart", config = calmConfig)
        assertEquals(0.94f, directedCalm.effectiveSpeed, 0.01f)
        assertEquals(1890L, directedCalm.postSilenceMs)
        assertEquals("af_sarah", directedCalm.donorVoice)
        assertEquals(0.4625f, directedCalm.blendWeight, 0.001f)
        assertEquals(1.00f, directedCalm.gain, 0.01f)

        // Sprint: 1.14x base speed, -50% pauses (1400 * 0.50 = 700ms), persona donor af_sky
        val sprintConfig = SpeechDirector.DirectorConfig(
            temperament = SpeechDirector.TemperamentPreset.SPRINT,
            expressionIntensity = 0.40f
        )
        val directedSprint = SpeechDirector.direct(standardSentence, baseSpeed = 1.0f, baseVoice = "af_heart", config = sprintConfig)
        assertEquals(1.14f, directedSprint.effectiveSpeed, 0.01f)
        assertEquals(700L, directedSprint.postSilenceMs)
        assertEquals("af_sky", directedSprint.donorVoice)
        assertEquals(0.4095f, directedSprint.blendWeight, 0.001f)
        assertEquals(1.00f, directedSprint.gain, 0.01f)

        // Theatrical: 1.02x base speed, +35% pauses (1890ms), persona donor af_bella, +0.5dB gain (1.05x)
        val theatricalConfig = SpeechDirector.DirectorConfig(
            temperament = SpeechDirector.TemperamentPreset.THEATRICAL,
            expressionIntensity = 0.85f
        )
        val directedTheatrical = SpeechDirector.direct(standardSentence, baseSpeed = 1.0f, baseVoice = "af_heart", config = theatricalConfig)
        assertEquals(1.02f, directedTheatrical.effectiveSpeed, 0.01f)
        assertEquals(1890L, directedTheatrical.postSilenceMs)
        assertEquals("af_bella", directedTheatrical.donorVoice)
        assertEquals(1.05f, directedTheatrical.gain, 0.01f)

        // Bedtime: Shouts are suppressed to prevent jarring wakeups, persona donor af_nicole, -1.8dB gentle gain (0.82x)
        val shoutSentence = SentenceItem(
            index = 1,
            text = "\"Wake up right now!\" she screamed.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )
        val bedtimeConfig = SpeechDirector.DirectorConfig(
            temperament = SpeechDirector.TemperamentPreset.BEDTIME,
            expressionIntensity = 0.40f
        )
        val directedBedtime = SpeechDirector.direct(shoutSentence, baseSpeed = 1.0f, baseVoice = "af_heart", config = bedtimeConfig)
        // Shouting suppressed: no Bella donor voice, no urgent speedup
        assertTrue(directedBedtime.donorVoice != "af_bella")
        assertTrue(directedBedtime.effectiveSpeed <= 1.0f)
        assertEquals("af_nicole", directedBedtime.donorVoice)
        assertEquals(0.82f, directedBedtime.gain, 0.01f)
    }

    @Test
    fun testDualToneDialogueDistinction() {
        val dialogueSentence = SentenceItem(
            index = 0,
            text = "\"Let's head toward the harbor,\" Jack suggested.",
            chapterIndex = 0,
            isParagraphEnd = false,
            isDialogue = true
        )

        // Without Dual-Tone: Dialogue has no emotional donor
        val configNoDual = SpeechDirector.DirectorConfig(enableDualTone = false)
        val directedNoDual = SpeechDirector.direct(dialogueSentence, baseVoice = "af_heart", config = configNoDual)
        assertEquals(null, directedNoDual.donorVoice)

        // With Dual-Tone: Subtle conversational donor is blended
        val configWithDual = SpeechDirector.DirectorConfig(enableDualTone = true, expressionIntensity = 1.0f)
        val directedWithDual = SpeechDirector.direct(dialogueSentence, baseVoice = "af_heart", config = configWithDual)
        assertEquals("af_bella", directedWithDual.donorVoice)
        assertEquals(0.18f, directedWithDual.blendWeight, 0.001f)
    }

    @Test
    fun testWindDownProsodyCurve() {
        val sentence = SentenceItem(
            index = 0,
            text = "She closed her eyes and listened to the gentle rain.",
            chapterIndex = 0,
            isParagraphEnd = true,
            isDialogue = false
        )

        // At 0% progress: Standard speed (1.0x), paragraph pause (1400ms), gain 1.0x
        val directedStart = SpeechDirector.direct(sentence, baseSpeed = 1.0f, baseVoice = "af_heart", config = SpeechDirector.DirectorConfig(windDownProgress = 0.0f))
        assertEquals(1.00f, directedStart.effectiveSpeed, 0.01f)
        assertEquals(1400L, directedStart.postSilenceMs)
        assertEquals(1.00f, directedStart.gain, 0.01f)
        assertEquals(null, directedStart.donorVoice)

        // At 50% progress: Decelerated by 10% (0.90x), pauses widened by 50% (2100ms), gain 0.825x, breathy Nicole blend
        val directedMid = SpeechDirector.direct(sentence, baseSpeed = 1.0f, baseVoice = "af_heart", config = SpeechDirector.DirectorConfig(windDownProgress = 0.50f))
        assertEquals(0.90f, directedMid.effectiveSpeed, 0.01f)
        assertEquals(2100L, directedMid.postSilenceMs)
        assertEquals(0.825f, directedMid.gain, 0.01f)
        assertEquals("af_nicole", directedMid.donorVoice)
        assertEquals(0.275f, directedMid.blendWeight, 0.001f)

        // At 100% progress: Decelerated by 20% (0.80x), pauses doubled (2800ms), gain 0.65x (-3.7dB), deep Nicole whisper (55%)
        val directedEnd = SpeechDirector.direct(sentence, baseSpeed = 1.0f, baseVoice = "af_heart", config = SpeechDirector.DirectorConfig(windDownProgress = 1.0f))
        assertEquals(0.80f, directedEnd.effectiveSpeed, 0.01f)
        assertEquals(2800L, directedEnd.postSilenceMs)
        assertEquals(0.65f, directedEnd.gain, 0.01f)
        assertEquals("af_nicole", directedEnd.donorVoice)
        assertEquals(0.55f, directedEnd.blendWeight, 0.001f)
    }
}
