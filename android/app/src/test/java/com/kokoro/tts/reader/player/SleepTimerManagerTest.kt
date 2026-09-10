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
}
