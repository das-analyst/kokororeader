package com.kokoro.tts.reader.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
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
import com.kokoro.tts.reader.model.Book
import com.kokoro.tts.reader.model.SampleBook
import com.kokoro.tts.reader.player.BookPlayer

class ReaderActivity : AppCompatActivity(), BookPlayer.PlaybackListener {

    companion object {
        private const val TAG = "ReaderActivity"
        const val EXTRA_BOOK = "extra_book"

        fun start(context: Context, book: Book? = null) {
            val intent = Intent(context, ReaderActivity::class.java)
            if (book != null) {
                intent.putExtra(EXTRA_BOOK, book)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var kokoroEngine: KokoroEngine
    private lateinit var phonemeConverter: PhonemeConverter
    private lateinit var tokenizer: Tokenizer
    private lateinit var voiceLoader: VoiceStyleLoader
    private lateinit var player: BookPlayer

    private lateinit var book: Book
    private var currentChapterIndex = 0

    private lateinit var tvBookTitle: TextView
    private lateinit var tvChapterTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvVoiceSpeed: TextView
    private lateinit var rvSentences: RecyclerView
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

        val receivedBook = intent.getSerializableExtra(EXTRA_BOOK) as? Book
        book = receivedBook ?: SampleBook.SAMPLE_BOOK

        initViews()
        initEngine()
        loadChapter(0)
    }

    private fun initViews() {
        tvBookTitle = findViewById(R.id.tvBookTitle)
        tvChapterTitle = findViewById(R.id.tvChapterTitle)
        tvProgress = findViewById(R.id.tvProgress)
        tvVoiceSpeed = findViewById(R.id.tvVoiceSpeed)
        rvSentences = findViewById(R.id.rvSentences)
        fabPlayPause = findViewById(R.id.fabPlayPause)
        btnPrev = findViewById(R.id.btnPrevSentence)
        btnNext = findViewById(R.id.btnNextSentence)
        btnBack = findViewById(R.id.btnBack)
        btnToc = findViewById(R.id.btnToc)
        btnSettings = findViewById(R.id.btnSettings)

        tvBookTitle.text = book.title

        layoutManager = LinearLayoutManager(this)
        rvSentences.layoutManager = layoutManager

        sentenceAdapter = SentenceAdapter { clickedPosition ->
            player.seekToSentence(clickedPosition)
            if (!player.isCurrentlyPlaying()) {
                player.play()
            }
        }
        rvSentences.adapter = sentenceAdapter

        fabPlayPause.setOnClickListener {
            if (player.isCurrentlyPlaying()) {
                player.pause()
            } else {
                player.play()
            }
        }

        btnPrev.setOnClickListener { player.previousSentence() }
        btnNext.setOnClickListener { player.nextSentence() }
        btnBack.setOnClickListener { finish() }

        btnToc.setOnClickListener { showTableOfContentsDialog() }
        btnSettings.setOnClickListener { showVoiceSpeedDialog() }
        tvVoiceSpeed.setOnClickListener { showVoiceSpeedDialog() }
    }

    private fun initEngine() {
        tokenizer = Tokenizer.fromAssets(applicationContext, "vocab.json")
        voiceLoader = VoiceStyleLoader(applicationContext)
        phonemeConverter = PhonemeConverter(applicationContext, tokenizer)
        kokoroEngine = KokoroEngine(applicationContext, "model_quantized.onnx")

        kokoroEngine.initialize()

        player = BookPlayer(
            applicationContext,
            kokoroEngine,
            phonemeConverter,
            tokenizer,
            voiceLoader
        )
        player.listener = this
        updateVoiceSpeedLabel()
    }

    private fun loadChapter(chapterIndex: Int) {
        if (chapterIndex !in book.chapters.indices) return

        currentChapterIndex = chapterIndex
        val chapter = book.chapters[chapterIndex]

        tvChapterTitle.text = chapter.title
        sentenceAdapter.updateSentences(chapter.sentences)
        player.setSentences(chapter.sentences, 0)
        updateProgress(0)

        rvSentences.scrollToPosition(0)
    }

    private fun updateProgress(sentenceIndex: Int) {
        val chapter = book.chapters.getOrNull(currentChapterIndex) ?: return
        val total = chapter.sentences.size
        tvProgress.text = "Sentence ${sentenceIndex + 1} of $total • Chapter ${currentChapterIndex + 1}/${book.chapters.size}"
    }

    private fun updateVoiceSpeedLabel() {
        val speedStr = String.format("%.1fx", player.currentSpeed)
        tvVoiceSpeed.text = "${player.currentVoice} • $speedStr"
    }

    private fun showTableOfContentsDialog() {
        val chapterTitles = book.chapters.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Table of Contents")
            .setSingleChoiceItems(chapterTitles, currentChapterIndex) { dialog, which ->
                dialog.dismiss()
                if (which != currentChapterIndex) {
                    player.pause()
                    loadChapter(which)
                    player.play()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVoiceSpeedDialog() {
        val voices = voiceLoader.getAvailableVoices().ifEmpty { listOf("af_heart", "am_adam") }
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)
        val speedLabels = listOf("0.75x", "1.0x (Normal)", "1.25x (Fast)", "1.5x")

        val currentVoiceIdx = voices.indexOf(player.currentVoice).coerceAtLeast(0)
        val currentSpeedIdx = speeds.indexOf(player.currentSpeed).let { if (it >= 0) it else 1 }

        val view = layoutInflater.inflate(R.layout.dialog_voice_speed, null)
        val spVoice = view.findViewById<android.widget.Spinner>(R.id.dialogVoiceSpinner)
        val spSpeed = view.findViewById<android.widget.Spinner>(R.id.dialogSpeedSpinner)

        spVoice.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)
        spVoice.setSelection(currentVoiceIdx)

        spSpeed.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, speedLabels)
        spSpeed.setSelection(currentSpeedIdx)

        AlertDialog.Builder(this)
            .setTitle("Voice & Speed Settings")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val selectedVoice = voices[spVoice.selectedItemPosition]
                val selectedSpeed = speeds[spSpeed.selectedItemPosition]

                player.currentVoice = selectedVoice
                player.currentSpeed = selectedSpeed
                updateVoiceSpeedLabel()

                if (player.isCurrentlyPlaying()) {
                    player.seekToSentence(player.getCurrentIndex())
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

            // Smoothly center the active sentence on screen
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
                tvProgress.text = "Buffering audio..."
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        player.release()
        phonemeConverter.release()
        kokoroEngine.release()
        super.onDestroy()
    }
}
