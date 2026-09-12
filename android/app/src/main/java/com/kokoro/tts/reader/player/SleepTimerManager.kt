package com.kokoro.tts.reader.player

import android.os.CountDownTimer
import java.util.Locale

/**
 * Manages sleep timers for audiobook reading sessions.
 * Supports standard durations (15m, 30m, 45m, 60m) and "End of Chapter" mode.
 */
class SleepTimerManager(
    private val listener: Listener
) {
    enum class SleepTimerMode(val label: String, val durationMs: Long) {
        OFF("Off", 0L),
        MIN_15("15 Minutes", 15 * 60 * 1000L),
        MIN_30("30 Minutes", 30 * 60 * 1000L),
        MIN_45("45 Minutes", 45 * 60 * 1000L),
        MIN_60("60 Minutes", 60 * 60 * 1000L),
        END_OF_CHAPTER("End of Chapter", -1L)
    }

    interface Listener {
        fun onTick(remainingMs: Long, formattedTime: String)
        fun onTimerExpired()
        fun onModeChanged(mode: SleepTimerMode)
    }

    private var currentMode: SleepTimerMode = SleepTimerMode.OFF
    private var countDownTimer: CountDownTimer? = null
    private var remainingMs: Long = 0L

    fun getMode(): SleepTimerMode = currentMode

    fun isRunning(): Boolean = currentMode != SleepTimerMode.OFF

    fun getRemainingMs(): Long = remainingMs

    fun startTimer(mode: SleepTimerMode) {
        stopTimer()
        currentMode = mode
        listener.onModeChanged(mode)

        when (mode) {
            SleepTimerMode.OFF -> {
                remainingMs = 0L
                listener.onTick(0L, "")
            }
            SleepTimerMode.END_OF_CHAPTER -> {
                remainingMs = -1L
                listener.onTick(-1L, "End of Chapter")
            }
            else -> {
                val totalMs = mode.durationMs
                remainingMs = totalMs
                countDownTimer = object : CountDownTimer(totalMs, 1000L) {
                    override fun onTick(millisUntilFinished: Long) {
                        remainingMs = millisUntilFinished
                        listener.onTick(millisUntilFinished, formatTime(millisUntilFinished))
                    }

                    override fun onFinish() {
                        remainingMs = 0L
                        currentMode = SleepTimerMode.OFF
                        listener.onModeChanged(SleepTimerMode.OFF)
                        listener.onTimerExpired()
                    }
                }.start()
            }
        }
    }

    fun onChapterFinished(): Boolean {
        if (currentMode == SleepTimerMode.END_OF_CHAPTER) {
            currentMode = SleepTimerMode.OFF
            remainingMs = 0L
            listener.onModeChanged(SleepTimerMode.OFF)
            listener.onTimerExpired()
            return true
        }
        return false
    }

    fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        currentMode = SleepTimerMode.OFF
        remainingMs = 0L
        listener.onModeChanged(SleepTimerMode.OFF)
    }

    companion object {
        fun formatTime(millis: Long): String {
            if (millis <= 0) return ""
            val totalSeconds = millis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
