package com.kokoro.tts.reader.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kokoro.tts.R
import com.kokoro.tts.engine.KokoroEngine
import com.kokoro.tts.engine.PhonemeConverter
import com.kokoro.tts.engine.Tokenizer
import com.kokoro.tts.engine.VoiceStyleLoader
import com.kokoro.tts.reader.manager.LocalBookManager
import com.kokoro.tts.reader.model.Book
import com.kokoro.tts.reader.model.SampleBook
import com.kokoro.tts.reader.parser.TxtParser
import com.kokoro.tts.reader.player.BookPlayer
import java.io.File

class ReaderActivity : AppCompatActivity(), BookPlayer.PlaybackListener {

    companion object {
        private const val TAG = "ReaderActivity"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_BOOK_ID = "extra_book_id"

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

    private lateinit var tvBookTitle: TextView
    private lateinit var tvChapterTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvVoiceSpeed: TextView
    private lateinit var rvSentences: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var fabPlayPause: FloatingActionButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnBack: ImageButton
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

        val readerRoot = findViewById<android.view.View>(R.id.readerRoot)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(readerRoot) { v, insets ->
            val systemBars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        initEngine()

        if (filePath != null) {
            loadBookFromDisk(filePath)
        } else {
            book = SampleBook.SAMPLE_BOOK
            onBookReady(book)
        }
    }

    private fun initViews() {
        tvBookTitle = findViewById(R.id.tvBookTitle)
        tvChapterTitle = findViewById(R.id.tvChapterTitle)
        tvProgress = findViewById(R.id.tvProgress)
        tvVoiceSpeed = findViewById(R.id.tvVoiceSpeed)
        rvSentences = findViewById(R.id.rvSentences)
        pbLoading = findViewById(R.id.pbLoading)
        fabPlayPause = findViewById(R.id.fabPlayPause)
        btnPrev = findViewById(R.id.btnPrevSentence)
        btnNext = findViewById(R.id.btnNextSentence)
        btnBack = findViewById(R.id.btnBack)
        btnToc = findViewById(R.id.btnToc)
        btnSettings = findViewById(R.id.btnSettings)

        layoutManager = LinearLayoutManager(this)
        rvSentences.layoutManager = layoutManager

        sentenceAdapter = SentenceAdapter { clickedPosition ->
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

        btnPrev.setOnClickListener { player?.previousSentence() }
        btnNext.setOnClickListener { player?.nextSentence() }
        btnBack.setOnClickListener { finish() }
        btnToc.setOnClickListener { showTableOfContents() }
        btnSettings.setOnClickListener { showVoiceSpeedDialog() }
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

        loadChapter(0)
    }

    private fun loadChapter(chapterIndex: Int) {
        if (!::book.isInitialized || chapterIndex !in book.chapters.indices) return
        currentChapterIndex = chapterIndex
        val chapter = book.chapters[chapterIndex]

        tvChapterTitle.text = chapter.title
        sentenceAdapter.updateSentences(chapter.sentences)

        player?.setSentences(chapter.sentences)
        updateProgress(0)

        // Record reading position
        bookId?.let { id ->
            localBookManager.updateReadingPosition(id, chapterIndex, 0)
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
                    player?.pause()
                    loadChapter(which)
                    player?.play()
                }
            }
            .setNegativeButton("Close", null)
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

            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (sentenceIndex < firstVisible || sentenceIndex > lastVisible - 2) {
                rvSentences.smoothScrollToPosition((sentenceIndex + 2).coerceAtMost(sentenceAdapter.itemCount - 1))
            }
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            fabPlayPause.setImageResource(icon)
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

    override fun onDestroy() {
        player?.release()
        phonemeConverter.release()
        kokoroEngine.release()
        super.onDestroy()
    }
}
