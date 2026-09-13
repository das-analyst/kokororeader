package com.kokoro.tts.reader.ui.compose

import com.kokoro.tts.engine.director.SpeechDirector
import com.kokoro.tts.reader.model.Chapter
import com.kokoro.tts.reader.model.ReaderTheme
import com.kokoro.tts.reader.model.SentenceItem
import com.kokoro.tts.reader.player.SleepTimerManager

enum class ReaderModalSheet {
    APPEARANCE,
    VOICE_SPEED,
    SLEEP_TIMER,
    TABLE_OF_CONTENTS,
    PRONUNCIATION
}

data class ReaderUiState(
    val bookTitle: String = "Loading...",
    val chapterTitle: String = "",
    val currentChapterIndex: Int = 0,
    val totalChapters: Int = 1,
    val chapters: List<Chapter> = emptyList(),
    val sentences: List<SentenceItem> = emptyList(),
    val activeSentenceIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLoadingBook: Boolean = false,
    val voiceId: String = "af_heart",
    val speed: Float = 1.0f,
    val expressionIntensity: Float = 0.60f,
    val temperament: SpeechDirector.TemperamentPreset = SpeechDirector.TemperamentPreset.NATURAL,
    val enableDualTone: Boolean = true,
    val isWindDownEnabled: Boolean = true,
    val sleepTimerBadge: String? = null,
    val sleepTimerMode: SleepTimerManager.SleepTimerMode = SleepTimerManager.SleepTimerMode.OFF,
    val theme: ReaderTheme = ReaderTheme.CLEAN_PAPER,
    val fontSizeSp: Float = 18f,
    val lineSpacing: Float = 1.45f,
    val isControlsVisible: Boolean = true,
    val activeModal: ReaderModalSheet? = null,
    val pronunciationRules: Map<String, String> = emptyMap()
)
