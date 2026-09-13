package com.kokoro.tts.reader.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerManagerTest {

    @Test
    fun testTimeFormatting() {
        assertEquals("15:00", SleepTimerManager.formatTime(15 * 60 * 1000L))
        assertEquals("01:30", SleepTimerManager.formatTime(90 * 1000L))
        assertEquals("00:05", SleepTimerManager.formatTime(5 * 1000L))
        assertEquals("", SleepTimerManager.formatTime(0L))
    }

    @Test
    fun testModeInitialState() {
        var expired = false
        val manager = SleepTimerManager(object : SleepTimerManager.Listener {
            override fun onTick(remainingMs: Long, formattedTime: String) {}
            override fun onTimerExpired() { expired = true }
            override fun onModeChanged(mode: SleepTimerManager.SleepTimerMode) {}
        })

        assertEquals(SleepTimerManager.SleepTimerMode.OFF, manager.getMode())
        assertFalse(manager.isRunning())
        assertFalse(expired)
    }

    @Test
    fun testStopTimer() {
        val manager = SleepTimerManager(object : SleepTimerManager.Listener {
            override fun onTick(remainingMs: Long, formattedTime: String) {}
            override fun onTimerExpired() {}
            override fun onModeChanged(mode: SleepTimerManager.SleepTimerMode) {}
        })

        manager.startTimer(SleepTimerManager.SleepTimerMode.END_OF_CHAPTER)
        assertTrue(manager.isRunning())
        assertEquals(SleepTimerManager.SleepTimerMode.END_OF_CHAPTER, manager.getMode())

        manager.stopTimer()
        assertFalse(manager.isRunning())
        assertEquals(SleepTimerManager.SleepTimerMode.OFF, manager.getMode())
    }

    @Test
    fun testOnChapterFinished() {
        var expired = false
        val manager = SleepTimerManager(object : SleepTimerManager.Listener {
            override fun onTick(remainingMs: Long, formattedTime: String) {}
            override fun onTimerExpired() { expired = true }
            override fun onModeChanged(mode: SleepTimerManager.SleepTimerMode) {}
        })

        // When OFF, onChapterFinished returns false
        assertFalse(manager.onChapterFinished())
        assertFalse(expired)

        // When END_OF_CHAPTER, onChapterFinished returns true and fires onTimerExpired
        manager.startTimer(SleepTimerManager.SleepTimerMode.END_OF_CHAPTER)
        assertTrue(manager.onChapterFinished())
        assertTrue(expired)
        assertEquals(SleepTimerManager.SleepTimerMode.OFF, manager.getMode())
    }

    @Test
    fun testWindDownProgressAcrossFullSpan() {
        val manager = SleepTimerManager(object : SleepTimerManager.Listener {
            override fun onTick(remainingMs: Long, formattedTime: String) {}
            override fun onTimerExpired() {}
            override fun onModeChanged(mode: SleepTimerManager.SleepTimerMode) {}
        })

        // 1. Off -> progress 0.0f
        assertEquals(0.0f, manager.getWindDownProgress(), 0.001f)

        // 2. 30-minute timer mode (1,800,000 ms)
        manager.setModeForTesting(SleepTimerManager.SleepTimerMode.MIN_30)
        manager.isWindDownEnabled = true

        // At start (100% remaining) -> progress 0.0f
        manager.setRemainingMsForTesting(30 * 60 * 1000L)
        assertEquals(0.0f, manager.getWindDownProgress(), 0.001f)

        // At 15 min remaining (50% elapsed) -> progress 0.5f
        manager.setRemainingMsForTesting(15 * 60 * 1000L)
        assertEquals(0.5f, manager.getWindDownProgress(), 0.001f)

        // At 3 min remaining (90% elapsed) -> progress 0.9f
        manager.setRemainingMsForTesting(3 * 60 * 1000L)
        assertEquals(0.9f, manager.getWindDownProgress(), 0.001f)

        // At 0 ms -> progress 0.0f (inactive)
        manager.setRemainingMsForTesting(0L)
        assertEquals(0.0f, manager.getWindDownProgress(), 0.001f)

        // 3. End of Chapter mode
        manager.setModeForTesting(SleepTimerManager.SleepTimerMode.END_OF_CHAPTER)
        manager.chapterProgress = 0.75f
        assertEquals(0.75f, manager.getWindDownProgress(), 0.001f)

        // 4. When wind-down is disabled
        manager.isWindDownEnabled = false
        assertEquals(0.0f, manager.getWindDownProgress(), 0.001f)
    }
}
