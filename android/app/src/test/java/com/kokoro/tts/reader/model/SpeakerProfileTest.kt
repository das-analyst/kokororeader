package com.kokoro.tts.reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerProfileTest {

    @Test
    fun testCuratedProfilesUniqueAndNonEmpty() {
        val profiles = SpeakerProfile.PROFILES
        assertTrue("Profiles list should contain at least 10 voices", profiles.size >= 10)

        val ids = profiles.map { it.id }
        assertEquals("All profile IDs must be unique", ids.distinct().size, ids.size)

        for (p in profiles) {
            assertTrue("ID must start with language prefix", p.id.matches(Regex("^[a-z]{2}_[a-z]+$")))
            assertTrue("Name must not be blank", p.name.isNotBlank())
            assertTrue("Gender must be Female or Male", p.gender == "Female" || p.gender == "Male")
            assertTrue("Accent must not be blank", p.accent.isNotBlank())
            assertTrue("Tone must not be blank", p.tone.isNotBlank())
        }
    }

    @Test
    fun testFindByIdAndDisplayNames() {
        val onyx = SpeakerProfile.findById("am_onyx")
        assertNotNull(onyx)
        assertEquals("Onyx", onyx?.name)
        assertEquals("Male", onyx?.gender)
        assertEquals("Deep Soft Bass", onyx?.tone)
        assertTrue(onyx!!.displayName.contains("Deep Soft Bass"))
        assertEquals("Onyx (Deep Soft Bass)", onyx.shortLabel)

        val heart = SpeakerProfile.findById("af_heart")
        assertNotNull(heart)
        assertEquals("Heart", heart?.name)
        assertEquals("Female", heart?.gender)
        assertEquals("Soft & Warm", heart?.tone)

        val unknownLabel = SpeakerProfile.getDisplayName("unknown_voice")
        assertEquals("unknown_voice", unknownLabel)
    }

    @Test
    fun testHarmoniousAndSoftMaleVoicesIncluded() {
        // User requested soft, harmonious, and soft bass male voices
        assertNotNull(SpeakerProfile.findById("am_onyx"))
        assertNotNull(SpeakerProfile.findById("am_michael"))
        assertNotNull(SpeakerProfile.findById("am_echo"))
        assertNotNull(SpeakerProfile.findById("bm_george"))
    }
}
