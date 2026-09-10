package com.kokoro.tts.reader.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.kokoro.tts.R
import com.kokoro.tts.engine.KokoroEngine
import com.kokoro.tts.engine.PhonemeConverter
import com.kokoro.tts.engine.Tokenizer
import com.kokoro.tts.engine.VoiceStyleLoader
import com.kokoro.tts.reader.manager.LocalBookManager
import com.kokoro.tts.reader.manager.PronunciationManager
import com.kokoro.tts.reader.model.Book
import com.kokoro.tts.reader.model.ReaderTheme
import com.kokoro.tts.reader.model.SampleBook
import com.kokoro.tts.reader.parser.TxtParser
import com.kokoro.tts.reader.player.BookPlayer
import com.kokoro.tts.reader.player.SleepTimerManager
import com.kokoro.tts.reader.service.BookPlaybackService
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

    private var currentTheme: ReaderTheme = ReaderTheme.CLEAN_PAPER
    private var currentFontSize: Float = 18f
    private var currentLineSpacing: Float = 1.45f

    private lateinit var readerRoot: View
    private lateinit var topBarContainer: LinearLayout
    private lateinit var bottomBarContainer: LinearLayout
    private lateinit var tvBookTitle: TextView
    private lateinit var tvChapterTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvVoiceSpeed: TextView
    private lateinit var tvSleepBadge: TextView
    private lateinit var rvSentences: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var fabPlayPause: FloatingActionButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnSleepTimer: ImageButton
    private lateinit var btnAppearance: ImageButton
    private lateinit var btnToc: ImageButton
    private lateinit var btnSettings: ImageButton

    private lateinit var sentenceAdapter: SentenceAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        localBookManager = LocalBookManager(this)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)

        readerRoot = findViewById(R.id.readerRoot)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(readerRoot) { v, insets ->
            val systemBars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        initSleepTimer()
        initPlaybackController()
        initEngine()
        loadAppearancePreferences()

        checkNotificationPermission()

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

    private fun initViews() {
        topBarContainer = findViewById(R.id.topBarContainer)
        bottomBarContainer = findViewById(R.id.bottomBarContainer)
        tvBookTitle = findViewById(R.id.tvBookTitle)
        tvChapterTitle = findViewById(R.id.tvChapterTitle)
        tvProgress = findViewById(R.id.tvProgress)
        tvVoiceSpeed = findViewById(R.id.tvVoiceSpeed)
        tvSleepBadge = findViewById(R.id.tvSleepBadge)
        rvSentences = findViewById(R.id.rvSentences)
        pbLoading = findViewById(R.id.pbLoading)
        fabPlayPause = findViewById(R.id.fabPlayPause)
        btnPrev = findViewById(R.id.btnPrevSentence)
        btnNext = findViewById(R.id.btnNextSentence)
        btnBack = findViewById(R.id.btnBack)
        btnSleepTimer = findViewById(R.id.btnSleepTimer)
        btnAppearance = findViewById(R.id.btnAppearance)
        btnToc = findViewById(R.id.btnToc)
        btnSettings = findViewById(R.id.btnSettings)

        layoutManager = LinearLayoutManager(this)
        rvSentences.layoutManager = layoutManager

        sentenceAdapter = SentenceAdapter(
            currentTheme = currentTheme,
            textSizeSp = currentFontSize,
            lineSpacingMultiplier = currentLineSpacing
        ) { clickedPosition ->
            player?.seekToSentence(clickedPosition)
            if (player?.isCurrentlyPlaying() != true) {
                player?.play()
            }
        }
        rvSentences.adapter = sentenceAdapter

        fabPlayPause.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isCurrentlyPlaying()) {
                p.pause()
            } else {
                p.play()
            }
        }

        btnPrev.setOnClickListener { handlePreviousSentence() }
        btnNext.setOnClickListener { handleNextSentence() }
        btnBack.setOnClickListener { finish() }
        btnSleepTimer.setOnClickListener { showSleepTimerDialog() }
        btnAppearance.setOnClickListener { showAppearanceDialog() }
        btnToc.setOnClickListener { showTableOfContents() }
        btnSettings.setOnClickListener { showVoiceSpeedDialog() }
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

    private fun initSleepTimer() {
        sleepTimerManager = SleepTimerManager(object : SleepTimerManager.Listener {
            override fun onTick(remainingMs: Long, formattedTime: String) {
                runOnUiThread {
                    if (remainingMs > 0) {
                        tvSleepBadge.visibility = View.VISIBLE
                        val mins = remainingMs / 60000
                        tvSleepBadge.text = if (mins > 0) "${mins}m" else "${remainingMs / 1000}s"
                    } else if (remainingMs == -1L) {
                        tvSleepBadge.visibility = View.VISIBLE
                        tvSleepBadge.text = "Ch"
                    } else {
                        tvSleepBadge.visibility = View.GONE
                    }
                }
            }

            override fun onTimerExpired() {
                runOnUiThread {
                    player?.pause()
                    tvSleepBadge.visibility = View.GONE
                    Toast.makeText(this@ReaderActivity, "Sleep timer finished. Playback paused.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onModeChanged(mode: SleepTimerManager.SleepTimerMode) {
                runOnUiThread {
                    if (mode == SleepTimerManager.SleepTimerMode.OFF) {
                        tvSleepBadge.visibility = View.GONE
                    }
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

        // Pre-warm ONNX Runtime neural engine asynchronously in background so first sentence starts instantly
        Thread({
            Log.i(TAG, "Pre-warming KokoroEngine in background...")
            kokoroEngine.initialize()
            Log.i(TAG, "KokoroEngine pre-warmed successfully!")
        }, "Reader-EnginePreWarmer").start()

        updateVoiceSpeedLabel()
    }

    private fun loadAppearancePreferences() {
        val prefs = getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
        currentTheme = ReaderTheme.fromId(prefs.getString(KEY_THEME, ReaderTheme.CLEAN_PAPER.id))
        currentFontSize = prefs.getFloat(KEY_FONT_SIZE, 18f)
        currentLineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.45f)

        applyTheme(currentTheme, save = false)
        applyFontSize(currentFontSize, save = false)
        applyLineSpacing(currentLineSpacing, save = false)
    }

    private fun applyTheme(theme: ReaderTheme, save: Boolean = true) {
        currentTheme = theme
        if (save) {
            getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, theme.id).apply()
        }

        readerRoot.setBackgroundColor(theme.backgroundColor)
        topBarContainer.setBackgroundColor(theme.surfaceColor)
        bottomBarContainer.setBackgroundColor(theme.surfaceColor)

        tvBookTitle.setTextColor(theme.primaryTextColor)
        tvChapterTitle.setTextColor(theme.secondaryTextColor)
        tvProgress.setTextColor(theme.secondaryTextColor)

        val iconColor = theme.primaryTextColor
        btnBack.setColorFilter(iconColor)
        btnSleepTimer.setColorFilter(iconColor)
        btnAppearance.setColorFilter(iconColor)
        btnToc.setColorFilter(iconColor)
        btnSettings.setColorFilter(iconColor)
        btnPrev.setColorFilter(iconColor)
        btnNext.setColorFilter(iconColor)

        sentenceAdapter.setTheme(theme)
    }

    private fun applyFontSize(sizeSp: Float, save: Boolean = true) {
        currentFontSize = sizeSp
        if (save) {
            getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_FONT_SIZE, sizeSp).apply()
        }
        sentenceAdapter.setTextSize(sizeSp)
    }

    private fun applyLineSpacing(spacing: Float, save: Boolean = true) {
        currentLineSpacing = spacing
        if (save) {
            getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_LINE_SPACING, spacing).apply()
        }
        sentenceAdapter.setLineSpacing(spacing)
    }

    private fun loadBookFromDisk(filePath: String) {
        pbLoading.visibility = View.VISIBLE
        rvSentences.visibility = View.INVISIBLE
        tvBookTitle.text = "Loading book..."

        Thread {
            try {
                val file = File(filePath)
                val loadedBook = TxtParser.parse(file)
                runOnUiThread {
                    pbLoading.visibility = View.GONE
                    rvSentences.visibility = View.VISIBLE
                    book = loadedBook
                    onBookReady(loadedBook)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load book from $filePath", e)
                runOnUiThread {
                    pbLoading.visibility = View.GONE
                    Toast.makeText(this, "Failed to load book: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }

    private fun onBookReady(readyBook: Book) {
        tvBookTitle.text = readyBook.title

        player = BookPlayer(
            kokoroEngine = kokoroEngine,
            phonemeConverter = phonemeConverter,
            tokenizer = tokenizer,
            voiceLoader = voiceLoader,
            listener = this
        )

        val saved = bookId?.let { id -> localBookManager.getAllSavedBooks().find { it.id == id } }
        val startChapter = (saved?.lastReadChapter ?: 0).coerceIn(0, maxOf(0, readyBook.chapters.size - 1))
        val startSentence = (saved?.lastReadSentence ?: 0).coerceIn(0, maxOf(0, (readyBook.chapters.getOrNull(startChapter)?.sentences?.size ?: 1) - 1))
        loadChapter(startChapter, startSentenceIndex = startSentence, autoPlay = false)
    }

    private fun loadChapter(chapterIndex: Int, startSentenceIndex: Int = 0, autoPlay: Boolean = false) {
        if (!::book.isInitialized || chapterIndex !in book.chapters.indices) return
        currentChapterIndex = chapterIndex
        val chapter = book.chapters[chapterIndex]

        tvChapterTitle.text = chapter.title
        sentenceAdapter.updateSentences(chapter.sentences)

        val safeSentenceIndex = startSentenceIndex.coerceIn(0, maxOf(0, chapter.sentences.size - 1))
        player?.setSentences(chapter.sentences, startIndex = safeSentenceIndex)
        sentenceAdapter.setActiveIndex(safeSentenceIndex)
        updateProgress(safeSentenceIndex)
        rvSentences.post {
            layoutManager.scrollToPositionWithOffset(safeSentenceIndex, 120)
        }

        // Record reading position
        bookId?.let { id ->
            localBookManager.updateReadingPosition(id, chapterIndex, safeSentenceIndex)
        }
        updatePlaybackService()

        if (autoPlay) {
            player?.play()
        }
    }

    private fun updateProgress(sentenceIndex: Int) {
        if (!::book.isInitialized || currentChapterIndex !in book.chapters.indices) return
        val total = book.chapters[currentChapterIndex].sentences.size
        tvProgress.text = "Sentence ${sentenceIndex + 1} of $total"

        bookId?.let { id ->
            localBookManager.updateReadingPosition(id, currentChapterIndex, sentenceIndex)
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

    private fun updateVoiceSpeedLabel() {
        val voice = player?.currentVoice ?: "af_heart"
        val speed = player?.currentSpeed ?: 1.0f
        tvVoiceSpeed.text = "$voice • ${String.format("%.2fx", speed)}"
    }

    private fun showTableOfContents() {
        if (!::book.isInitialized) return
        val chapterTitles = book.chapters.mapIndexed { idx, ch ->
            if (idx == currentChapterIndex) "▶ ${ch.title}" else "   ${ch.title}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Table of Contents")
            .setItems(chapterTitles) { _, which ->
                if (which != currentChapterIndex) {
                    val isPlaying = player?.isCurrentlyPlaying() ?: false
                    player?.pause()
                    loadChapter(which, startSentenceIndex = 0, autoPlay = isPlaying)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSleepTimerDialog() {
        val modes = SleepTimerManager.SleepTimerMode.values()
        val options = modes.map {
            if (it == sleepTimerManager.getMode()) "✓ ${it.label}" else "   ${it.label}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Sleep Timer")
            .setItems(options) { _, which ->
                val selected = modes[which]
                sleepTimerManager.startTimer(selected)
                val msg = if (selected == SleepTimerManager.SleepTimerMode.OFF) {
                    "Sleep timer turned off"
                } else {
                    "Sleep timer set: ${selected.label}"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppearanceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reader_appearance, null)

        val rgTheme = dialogView.findViewById<RadioGroup>(R.id.rgTheme)
        val rbPaper = dialogView.findViewById<RadioButton>(R.id.rbThemePaper)
        val rbSepia = dialogView.findViewById<RadioButton>(R.id.rbThemeSepia)
        val rbDark = dialogView.findViewById<RadioButton>(R.id.rbThemeDark)

        when (currentTheme) {
            ReaderTheme.CLEAN_PAPER -> rbPaper.isChecked = true
            ReaderTheme.WARM_SEPIA -> rbSepia.isChecked = true
            ReaderTheme.OLED_DARK -> rbDark.isChecked = true
        }

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.rbThemeSepia -> ReaderTheme.WARM_SEPIA
                R.id.rbThemeDark -> ReaderTheme.OLED_DARK
                else -> ReaderTheme.CLEAN_PAPER
            }
            applyTheme(theme)
        }

        val rgFontSize = dialogView.findViewById<RadioGroup>(R.id.rgFontSize)
        when (currentFontSize) {
            16f -> dialogView.findViewById<RadioButton>(R.id.rbSizeSmall).isChecked = true
            21f -> dialogView.findViewById<RadioButton>(R.id.rbSizeLarge).isChecked = true
            24f -> dialogView.findViewById<RadioButton>(R.id.rbSizeXLarge).isChecked = true
            else -> dialogView.findViewById<RadioButton>(R.id.rbSizeMedium).isChecked = true
        }

        rgFontSize.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rbSizeSmall -> 16f
                R.id.rbSizeLarge -> 21f
                R.id.rbSizeXLarge -> 24f
                else -> 18f
            }
            applyFontSize(size)
        }

        val rgLineSpacing = dialogView.findViewById<RadioGroup>(R.id.rgLineSpacing)
        when (currentLineSpacing) {
            1.25f -> dialogView.findViewById<RadioButton>(R.id.rbSpacingCompact).isChecked = true
            1.70f -> dialogView.findViewById<RadioButton>(R.id.rbSpacingRelaxed).isChecked = true
            else -> dialogView.findViewById<RadioButton>(R.id.rbSpacingNormal).isChecked = true
        }

        rgLineSpacing.setOnCheckedChangeListener { _, checkedId ->
            val spacing = when (checkedId) {
                R.id.rbSpacingCompact -> 1.25f
                R.id.rbSpacingRelaxed -> 1.70f
                else -> 1.45f
            }
            applyLineSpacing(spacing)
        }

        val btnOpenPronunciation = dialogView.findViewById<Button>(R.id.btnOpenPronunciation)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .create()

        btnOpenPronunciation.setOnClickListener {
            dialog.dismiss()
            showPronunciationDialog()
        }

        dialog.show()
    }

    private fun showPronunciationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pronunciation, null)
        val etOriginal = dialogView.findViewById<TextInputEditText>(R.id.etOriginalWord)
        val etSpoken = dialogView.findViewById<TextInputEditText>(R.id.etSpokenAs)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAddRule)
        val container = dialogView.findViewById<LinearLayout>(R.id.llRulesContainer)

        val pronManager = PronunciationManager.getInstance(this)

        fun refreshRules() {
            container.removeAllViews()
            val rules = pronManager.getRules()
            if (rules.isEmpty()) {
                val tvEmpty = TextView(this).apply {
                    text = "No custom pronunciation rules added yet."
                    textSize = 13f
                    setTextColor(0xFF888888.toInt())
                    setPadding(0, 16, 0, 16)
                }
                container.addView(tvEmpty)
                return
            }

            for ((word, replacement) in rules) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 8, 0, 8)
                }

                val tvRule = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = "$word  ➔  $replacement"
                    textSize = 14f
                    setTextColor(0xFF222222.toInt())
                }

                val btnDelete = ImageButton(this).apply {
                    setImageResource(android.R.drawable.ic_menu_delete)
                    setBackgroundResource(android.R.drawable.screen_background_light_transparent)
                    setOnClickListener {
                        pronManager.removeRule(word)
                        refreshRules()
                    }
                }

                row.addView(tvRule)
                row.addView(btnDelete)
                container.addView(row)
            }
        }

        refreshRules()

        btnAdd.setOnClickListener {
            val orig = etOriginal.text?.toString()?.trim() ?: ""
            val spoken = etSpoken.text?.toString()?.trim() ?: ""
            if (orig.isNotEmpty() && spoken.isNotEmpty()) {
                pronManager.addRule(orig, spoken)
                etOriginal.text?.clear()
                etSpoken.text?.clear()
                refreshRules()
                Toast.makeText(this, "Added: $orig ➔ $spoken", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter both word and pronunciation", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showVoiceSpeedDialog() {
        val p = player ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_voice_speed, null)
        val spVoice = dialogView.findViewById<android.widget.Spinner>(R.id.dialogVoiceSpinner)
        val spSpeed = dialogView.findViewById<android.widget.Spinner>(R.id.dialogSpeedSpinner)

        val voices = arrayOf("af_heart", "am_adam")
        val voiceAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)
        spVoice.adapter = voiceAdapter
        spVoice.setSelection(voices.indexOf(p.currentVoice).coerceAtLeast(0))

        val speeds = arrayOf(0.75f, 1.0f, 1.25f, 1.5f)
        val speedLabels = arrayOf("0.75x (Slow)", "1.0x (Normal)", "1.25x (Fast)", "1.5x (Very Fast)")
        val speedAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, speedLabels)
        spSpeed.adapter = speedAdapter
        val speedIndex = speeds.indexOfFirst { kotlin.math.abs(it - p.currentSpeed) < 0.05f }.coerceAtLeast(1)
        spSpeed.setSelection(speedIndex)

        AlertDialog.Builder(this)
            .setTitle("Reading Voice & Speed")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val selectedVoice = voices[spVoice.selectedItemPosition]
                val selectedSpeed = speeds[spSpeed.selectedItemPosition]

                p.currentVoice = selectedVoice
                p.currentSpeed = selectedSpeed
                updateVoiceSpeedLabel()

                if (p.isCurrentlyPlaying()) {
                    p.seekToSentence(p.getCurrentIndex())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- BookPlayer.PlaybackListener Callbacks ---

    override fun onSentenceStarted(sentenceIndex: Int) {
        runOnUiThread {
            sentenceAdapter.setActiveIndex(sentenceIndex)
            updateProgress(sentenceIndex)

            // Smooth ElevenReader centered glide
            val scroller = CenterSmoothScroller(this@ReaderActivity)
            scroller.targetPosition = sentenceIndex
            layoutManager.startSmoothScroll(scroller)

            updatePlaybackService()
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            fabPlayPause.setImageResource(icon)
            updatePlaybackService()
        }
    }

    override fun onBuffering(isBuffering: Boolean) {
        runOnUiThread {
            if (isBuffering) {
                tvProgress.text = "⚡ Buffering speech..."
            } else {
                updateProgress(player?.getCurrentIndex() ?: 0)
            }
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
