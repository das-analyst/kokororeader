package com.kokoro.tts.reader.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kokoro.tts.KokoroTtsService
import com.kokoro.tts.engine.KokoroEngine
import com.kokoro.tts.engine.PhonemeConverter
import com.kokoro.tts.engine.Tokenizer
import com.kokoro.tts.engine.VoiceStyleLoader
import com.kokoro.tts.engine.director.SpeechDirector
import com.kokoro.tts.reader.manager.LocalBookManager
import com.kokoro.tts.reader.manager.PronunciationManager
import com.kokoro.tts.reader.model.Book
import com.kokoro.tts.reader.model.ReaderTheme
import com.kokoro.tts.reader.model.SampleBook
import com.kokoro.tts.reader.parser.TxtParser
import com.kokoro.tts.reader.player.BookPlayer
import com.kokoro.tts.reader.player.SleepTimerManager
import com.kokoro.tts.reader.service.BookPlaybackService
import com.kokoro.tts.reader.ui.compose.ReaderModalSheet
import com.kokoro.tts.reader.ui.compose.ReaderScreen
import com.kokoro.tts.reader.ui.compose.ReaderUiState
import com.kokoro.tts.ui.theme.KokoroTTSTheme
import java.io.File

class ReaderActivity : AppCompatActivity(), BookPlayer.PlaybackListener {

    companion object {
        private const val TAG = "ReaderActivity"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_BOOK_ID = "extra_book_id"
        private const val PREFS_APPEARANCE = "kokoro_reader_appearance_prefs"
        private const val KEY_THEME = "pref_theme_id"
        private const val KEY_FONT_SIZE = "pref_font_size_sp"
        private const val KEY_LINE_SPACING = "pref_line_spacing"
        private const val KEY_VOICE = "pref_reading_voice"
        private const val KEY_SPEED = "pref_reading_speed"
        private const val KEY_EXPRESSION_INTENSITY = "pref_expression_intensity"
        private const val KEY_TEMPERAMENT = "pref_temperament"
        private const val KEY_DUAL_TONE = "pref_dual_tone"
        private const val KEY_SLEEP_WIND_DOWN = "pref_sleep_wind_down"

        fun start(context: Context, filePath: String? = null, bookId: String? = null) {
            val intent = Intent(context, ReaderActivity::class.java)
            if (filePath != null) {
                intent.putExtra(EXTRA_FILE_PATH, filePath)
            }
            if (bookId != null) {
                intent.putExtra(EXTRA_BOOK_ID, bookId)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var kokoroEngine: KokoroEngine
    private lateinit var phonemeConverter: PhonemeConverter
    private lateinit var tokenizer: Tokenizer
    private lateinit var voiceLoader: VoiceStyleLoader
    private var player: BookPlayer? = null

    private lateinit var book: Book
    private var currentChapterIndex = 0
    private var bookId: String? = null
    private lateinit var localBookManager: LocalBookManager
    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var pronManager: PronunciationManager

    private var availableVoiceIds: List<String> = emptyList()

    // Compose Reactive State
    private var uiState by mutableStateOf(ReaderUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        localBookManager = LocalBookManager(this)
        pronManager = PronunciationManager.getInstance(this)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)

        initSleepTimer()
        initPlaybackController()
        initEngine()
        loadAppearancePreferences()

        checkNotificationPermission()

        setContent {
            KokoroTTSTheme(
                readerTheme = uiState.theme
            ) {
                ReaderScreen(
                    uiState = uiState,
                    availableVoiceIds = availableVoiceIds,
                    onBackClick = { finish() },
                    onPlayPauseClick = { handlePlayPause() },
                    onPrevClick = { handlePreviousSentence() },
                    onNextClick = { handleNextSentence() },
                    onSentenceClick = { clickedPos -> handleSentenceClick(clickedPos) },
                    onToggleChrome = {
                        uiState = uiState.copy(isControlsVisible = !uiState.isControlsVisible)
                    },
                    onOpenModal = { modal ->
                        uiState = uiState.copy(activeModal = modal)
                    },
                    onCloseModal = {
                        uiState = uiState.copy(activeModal = null)
                    },
                    onThemeSelected = { theme -> applyTheme(theme) },
                    onFontSizeSelected = { sizeSp -> applyFontSize(sizeSp) },
                    onLineSpacingSelected = { spacing -> applyLineSpacing(spacing) },
                    onVoiceSelected = { voiceId -> applyVoice(voiceId) },
                    onSpeedSelected = { speed -> applySpeed(speed) },
                    onExpressionIntensitySelected = { intensity -> applyExpressionIntensity(intensity) },
                    onTemperamentSelected = { temperament -> applyTemperament(temperament) },
                    onDualToneToggled = { enabled -> applyDualTone(enabled) },
                    onChapterSelected = { chIndex ->
                        val isPlaying = player?.isCurrentlyPlaying() ?: false
                        player?.pause()
                        loadChapter(chIndex, startSentenceIndex = 0, autoPlay = isPlaying)
                    },
                    onSleepTimerSelected = { mode -> applySleepTimer(mode) },
                    onWindDownToggled = { enabled -> applyWindDown(enabled) },
                    onAddPronunciationRule = { word, spoken ->
                        pronManager.addRule(word, spoken)
                        uiState = uiState.copy(pronunciationRules = pronManager.getRules())
                        Toast.makeText(this, "Added: $word ➔ $spoken", Toast.LENGTH_SHORT).show()
                    },
                    onDeletePronunciationRule = { word ->
                        pronManager.removeRule(word)
                        uiState = uiState.copy(pronunciationRules = pronManager.getRules())
                    }
                )
            }
        }

        if (filePath != null) {
            loadBookFromDisk(filePath)
        } else {
            book = SampleBook.SAMPLE_BOOK
            onBookReady(book)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun initSleepTimer() {
        sleepTimerManager = SleepTimerManager(object : SleepTimerManager.Listener {
            override fun onTick(remainingMs: Long, formattedTime: String) {
                runOnUiThread {
                    val badge = when {
                        remainingMs > 0 -> {
                            val mins = remainingMs / 60000
                            if (mins > 0) "${mins}m" else "${remainingMs / 1000}s"
                        }
                        remainingMs == -1L -> "Ch"
                        else -> null
                    }
                    uiState = uiState.copy(sleepTimerBadge = badge)
                }
            }

            override fun onTimerExpired() {
                runOnUiThread {
                    player?.pause()
                    uiState = uiState.copy(sleepTimerBadge = null)
                    Toast.makeText(this@ReaderActivity, "Sleep timer finished. Playback paused.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onModeChanged(mode: SleepTimerManager.SleepTimerMode) {
                runOnUiThread {
                    uiState = uiState.copy(
                        sleepTimerMode = mode,
                        sleepTimerBadge = if (mode == SleepTimerManager.SleepTimerMode.OFF) null else uiState.sleepTimerBadge
                    )
                }
            }
        })
    }

    private fun initPlaybackController() {
        BookPlaybackService.playbackController = object : BookPlaybackService.PlaybackController {
            override fun play() {
                runOnUiThread { player?.play() }
            }

            override fun pause() {
                runOnUiThread { player?.pause() }
            }

            override fun nextSentence() {
                runOnUiThread { handleNextSentence() }
            }

            override fun previousSentence() {
                runOnUiThread { handlePreviousSentence() }
            }

            override fun isPlaying(): Boolean = player?.isCurrentlyPlaying() ?: false
        }
    }

    private fun initEngine() {
        tokenizer = Tokenizer.fromAssets(this, "vocab.json")
        voiceLoader = VoiceStyleLoader(this)
        phonemeConverter = PhonemeConverter(this, tokenizer)
        kokoroEngine = KokoroEngine(this, "model_quantized.onnx")
        availableVoiceIds = voiceLoader.getAvailableVoices()

        // Pre-warm ONNX Runtime neural engine asynchronously in background
        Thread({
            Log.i(TAG, "Pre-warming KokoroEngine in background...")
            kokoroEngine.initialize()
            Log.i(TAG, "KokoroEngine pre-warmed successfully!")
        }, "Reader-EnginePreWarmer").start()
    }

    private fun loadAppearancePreferences() {
        val prefs = getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
        val theme = ReaderTheme.fromId(prefs.getString(KEY_THEME, ReaderTheme.CLEAN_PAPER.id))
        val fontSize = prefs.getFloat(KEY_FONT_SIZE, 18f)
        val lineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.45f)
        val savedVoice = prefs.getString(KEY_VOICE, KokoroTtsService.DEFAULT_VOICE) ?: KokoroTtsService.DEFAULT_VOICE
        val savedSpeed = prefs.getFloat(KEY_SPEED, 1.0f)
        val savedIntensity = prefs.getFloat(KEY_EXPRESSION_INTENSITY, 0.60f)
        val savedTemperament = SpeechDirector.TemperamentPreset.fromId(prefs.getString(KEY_TEMPERAMENT, SpeechDirector.TemperamentPreset.NATURAL.id))
        val savedDualTone = prefs.getBoolean(KEY_DUAL_TONE, true)
        val savedWindDown = prefs.getBoolean(KEY_SLEEP_WIND_DOWN, true)

        sleepTimerManager.isWindDownEnabled = savedWindDown

        uiState = uiState.copy(
            theme = theme,
            fontSizeSp = fontSize,
            lineSpacing = lineSpacing,
            voiceId = savedVoice,
            speed = savedSpeed,
            expressionIntensity = savedIntensity,
            temperament = savedTemperament,
            enableDualTone = savedDualTone,
            isWindDownEnabled = savedWindDown,
            pronunciationRules = pronManager.getRules()
        )
    }

    private fun applyTheme(theme: ReaderTheme) {
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.id).apply()
        uiState = uiState.copy(theme = theme)
    }

    private fun applyFontSize(sizeSp: Float) {
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_FONT_SIZE, sizeSp).apply()
        uiState = uiState.copy(fontSizeSp = sizeSp)
    }

    private fun applyLineSpacing(spacing: Float) {
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_LINE_SPACING, spacing).apply()
        uiState = uiState.copy(lineSpacing = spacing)
    }

    private fun applyVoice(voiceId: String) {
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit().putString(KEY_VOICE, voiceId).apply()
        player?.updateVoiceAndSpeed(voiceId, uiState.speed)
        uiState = uiState.copy(voiceId = voiceId)
    }

    private fun applySpeed(speed: Float) {
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SPEED, speed).apply()
        player?.updateVoiceAndSpeed(uiState.voiceId, speed)
        uiState = uiState.copy(speed = speed)
    }

    private fun applyExpressionIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.0f, 1.0f)
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_EXPRESSION_INTENSITY, clamped).apply()
        val newConfig = player?.directorConfig?.copy(expressionIntensity = clamped)
            ?: SpeechDirector.DirectorConfig(expressionIntensity = clamped)
        player?.updateDirectorConfig(newConfig)
        uiState = uiState.copy(expressionIntensity = clamped)
    }

    private fun applyTemperament(temperament: SpeechDirector.TemperamentPreset) {
        val targetIntensity = temperament.defaultIntensity
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEMPERAMENT, temperament.id)
            .putFloat(KEY_EXPRESSION_INTENSITY, targetIntensity)
            .apply()
        val newConfig = player?.directorConfig?.copy(
            temperament = temperament,
            expressionIntensity = targetIntensity
        ) ?: SpeechDirector.DirectorConfig(
            temperament = temperament,
            expressionIntensity = targetIntensity
        )
        player?.updateDirectorConfig(newConfig)
        uiState = uiState.copy(
            temperament = temperament,
            expressionIntensity = targetIntensity
        )
    }

    private fun applyDualTone(enabled: Boolean) {
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DUAL_TONE, enabled).apply()
        val newConfig = player?.directorConfig?.copy(enableDualTone = enabled)
            ?: SpeechDirector.DirectorConfig(enableDualTone = enabled)
        player?.updateDirectorConfig(newConfig)
        uiState = uiState.copy(enableDualTone = enabled)
    }

    private fun applyWindDown(enabled: Boolean) {
        getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SLEEP_WIND_DOWN, enabled).apply()
        sleepTimerManager.isWindDownEnabled = enabled
        uiState = uiState.copy(isWindDownEnabled = enabled)
    }

    private fun applySleepTimer(mode: SleepTimerManager.SleepTimerMode) {
        sleepTimerManager.startTimer(mode)
        uiState = uiState.copy(sleepTimerMode = mode)
        val msg = if (mode == SleepTimerManager.SleepTimerMode.OFF) {
            "Sleep timer turned off"
        } else {
            "Sleep timer set: ${mode.label}"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun handlePlayPause() {
        val p = player ?: return
        if (p.isCurrentlyPlaying()) {
            p.pause()
        } else {
            p.play()
        }
    }

    private fun handleSentenceClick(position: Int) {
        player?.seekToSentence(position)
        if (player?.isCurrentlyPlaying() != true) {
            player?.play()
        }
    }

    private fun handleNextSentence() {
        val p = player ?: return
        val curr = p.getCurrentIndex()
        val total = if (::book.isInitialized && currentChapterIndex in book.chapters.indices) {
            book.chapters[currentChapterIndex].sentences.size
        } else 0

        if (curr + 1 < total) {
            p.seekToSentence(curr + 1)
        } else if (::book.isInitialized && currentChapterIndex + 1 < book.chapters.size) {
            val isPlaying = p.isCurrentlyPlaying()
            loadChapter(currentChapterIndex + 1, startSentenceIndex = 0, autoPlay = isPlaying)
        }
    }

    private fun handlePreviousSentence() {
        val p = player ?: return
        val curr = p.getCurrentIndex()
        if (curr > 0) {
            p.seekToSentence(curr - 1)
        } else if (::book.isInitialized && currentChapterIndex > 0) {
            val isPlaying = p.isCurrentlyPlaying()
            val prevChapter = currentChapterIndex - 1
            val lastSentence = (book.chapters[prevChapter].sentences.size - 1).coerceAtLeast(0)
            loadChapter(prevChapter, startSentenceIndex = lastSentence, autoPlay = isPlaying)
        }
    }

    private fun loadBookFromDisk(filePath: String) {
        uiState = uiState.copy(isLoadingBook = true, bookTitle = "Loading book...")

        Thread {
            try {
                val file = File(filePath)
                val loadedBook = TxtParser.parse(file)
                runOnUiThread {
                    uiState = uiState.copy(isLoadingBook = false)
                    book = loadedBook
                    onBookReady(loadedBook)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load book from $filePath", e)
                runOnUiThread {
                    uiState = uiState.copy(isLoadingBook = false)
                    Toast.makeText(this, "Failed to load book: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }

    private fun onBookReady(readyBook: Book) {
        player = BookPlayer(
            kokoroEngine = kokoroEngine,
            phonemeConverter = phonemeConverter,
            tokenizer = tokenizer,
            voiceLoader = voiceLoader,
            listener = this
        )

        player?.currentVoice = uiState.voiceId
        player?.currentSpeed = uiState.speed
        player?.sleepTimerManager = sleepTimerManager
        player?.directorConfig = SpeechDirector.DirectorConfig(
            expressionIntensity = uiState.expressionIntensity,
            temperament = uiState.temperament,
            enableDualTone = uiState.enableDualTone
        )

        val saved = bookId?.let { id -> localBookManager.getAllSavedBooks().find { it.id == id } }
        val startChapter = (saved?.lastReadChapter ?: 0).coerceIn(0, maxOf(0, readyBook.chapters.size - 1))
        val startSentence = (saved?.lastReadSentence ?: 0).coerceIn(0, maxOf(0, (readyBook.chapters.getOrNull(startChapter)?.sentences?.size ?: 1) - 1))

        uiState = uiState.copy(
            bookTitle = readyBook.title,
            chapters = readyBook.chapters,
            totalChapters = readyBook.chapters.size
        )

        loadChapter(startChapter, startSentenceIndex = startSentence, autoPlay = false)
    }

    private fun loadChapter(chapterIndex: Int, startSentenceIndex: Int = 0, autoPlay: Boolean = false) {
        if (!::book.isInitialized || chapterIndex !in book.chapters.indices) return
        currentChapterIndex = chapterIndex
        val chapter = book.chapters[chapterIndex]

        val safeSentenceIndex = startSentenceIndex.coerceIn(0, maxOf(0, chapter.sentences.size - 1))
        player?.setSentences(chapter.sentences, startIndex = safeSentenceIndex)

        uiState = uiState.copy(
            currentChapterIndex = chapterIndex,
            chapterTitle = chapter.title,
            sentences = chapter.sentences,
            activeSentenceIndex = safeSentenceIndex
        )

        // Record reading position
        bookId?.let { id ->
            localBookManager.updateReadingPosition(id, chapterIndex, safeSentenceIndex)
        }
        updatePlaybackService()

        if (autoPlay) {
            player?.play()
        }
    }

    private fun updatePlaybackService() {
        if (!::book.isInitialized || currentChapterIndex !in book.chapters.indices) return
        val chapter = book.chapters[currentChapterIndex]
        val currIdx = player?.getCurrentIndex() ?: 0
        val total = chapter.sentences.size
        val progress = "Sentence ${currIdx + 1} of $total"
        val isPlaying = player?.isCurrentlyPlaying() ?: false

        BookPlaybackService.updateState(
            context = this,
            isPlaying = isPlaying,
            bookTitle = book.title,
            chapterTitle = chapter.title,
            progressText = progress
        )
    }

    // --- BookPlayer.PlaybackListener Callbacks ---

    override fun onSentenceStarted(sentenceIndex: Int) {
        runOnUiThread {
            uiState = uiState.copy(activeSentenceIndex = sentenceIndex)
            bookId?.let { id ->
                localBookManager.updateReadingPosition(id, currentChapterIndex, sentenceIndex)
            }
            updatePlaybackService()
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            uiState = uiState.copy(isPlaying = isPlaying)
            updatePlaybackService()
        }
    }

    override fun onBuffering(isBuffering: Boolean) {
        runOnUiThread {
            uiState = uiState.copy(isBuffering = isBuffering)
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onChapterFinished(lastIndex: Int) {
        runOnUiThread {
            val timerStoppedPlayback = sleepTimerManager.onChapterFinished()
            if (timerStoppedPlayback) {
                player?.pause()
                return@runOnUiThread
            }

            if (::book.isInitialized && currentChapterIndex + 1 < book.chapters.size) {
                val nextChapter = currentChapterIndex + 1
                Toast.makeText(
                    this@ReaderActivity,
                    "Starting Chapter ${nextChapter + 1}: ${book.chapters[nextChapter].title}",
                    Toast.LENGTH_SHORT
                ).show()
                loadChapter(nextChapter, startSentenceIndex = 0, autoPlay = true)
            } else {
                Toast.makeText(this@ReaderActivity, "Finished book!", Toast.LENGTH_SHORT).show()
                updatePlaybackService()
            }
        }
    }

    override fun onDestroy() {
        sleepTimerManager.stopTimer()
        BookPlaybackService.stop(this)
        BookPlaybackService.playbackController = null
        player?.release()
        phonemeConverter.release()
        kokoroEngine.release()
        super.onDestroy()
    }
}
