package com.kokoro.tts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kokoro.tts.engine.VoiceStyleLoader
import com.kokoro.tts.engine.normalizer.ArtifactCleaner
import com.kokoro.tts.engine.normalizer.TextNormalizer
import com.kokoro.tts.reader.converter.ConvertedBookResult
import com.kokoro.tts.reader.converter.EpubToTextConverter
import com.kokoro.tts.reader.converter.PdfToTextConverter
import com.kokoro.tts.reader.manager.LocalBookManager
import com.kokoro.tts.reader.model.SavedBook
import com.kokoro.tts.reader.ui.BookLibraryAdapter
import com.kokoro.tts.reader.ui.ReaderActivity
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var tts: TextToSpeech? = null
    private lateinit var voiceLoader: VoiceStyleLoader
    private lateinit var voiceSpinner: Spinner
    private lateinit var sampleTextInput: EditText
    private lateinit var btnTestVoice: Button
    private lateinit var btnOpenSettings: Button
    private lateinit var statusText: TextView

    // Ebook Library & Converter
    private lateinit var localBookManager: LocalBookManager
    private lateinit var libraryAdapter: BookLibraryAdapter
    private lateinit var rvLibrary: RecyclerView
    private lateinit var tvEmptyLibrary: TextView
    private lateinit var btnConvertEbook: Button
    private lateinit var btnOpenTxt: Button
    private lateinit var btnAddSample: Button

    private var pendingExportBook: SavedBook? = null
    private var pendingConvertedResult: ConvertedBookResult? = null

    // Document pickers
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedEbookUri(uri)
        }
    }

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            handleExportToUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootScrollView = findViewById<android.view.View>(R.id.rootScrollView)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootScrollView) { v, insets ->
            val systemBars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        voiceLoader = VoiceStyleLoader(applicationContext)
        localBookManager = LocalBookManager(applicationContext)
        PdfToTextConverter.init(applicationContext)

        initViews()
        setupLibrary()
        setupVoiceSpinner()

        tts = TextToSpeech(this, this, packageName)
        setupUtteranceListener()
    }

    override fun onResume() {
        super.onResume()
        refreshLibrary()
    }

    private fun initViews() {
        btnConvertEbook = findViewById(R.id.btnConvertEbook)
        btnOpenTxt = findViewById(R.id.btnOpenTxt)
        btnAddSample = findViewById(R.id.btnAddSample)
        rvLibrary = findViewById(R.id.rvLibrary)
        tvEmptyLibrary = findViewById(R.id.tvEmptyLibrary)

        voiceSpinner = findViewById(R.id.voiceSpinner)
        sampleTextInput = findViewById(R.id.sampleTextInput)
        btnTestVoice = findViewById(R.id.btnTestVoice)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        statusText = findViewById(R.id.statusText)

        btnConvertEbook.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/epub+zip",
                    "application/pdf",
                    "text/plain",
                    "*/*"
                )
            )
        }

        btnOpenTxt.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "text/plain",
                    "*/*"
                )
            )
        }

        btnAddSample.setOnClickListener {
            localBookManager.saveBook(
                title = "Pride and Prejudice",
                author = "Jane Austen",
                originalFormat = "TXT",
                content = com.kokoro.tts.reader.model.SampleBook.SAMPLE_BOOK.chapters.joinToString("\n\n") {
                    "CHAPTER ${it.index + 1}: ${it.title}\n\n${it.rawText}"
                },
                chapterCount = 2
            )
            refreshLibrary()
            Toast.makeText(this, "Sample book added to library", Toast.LENGTH_SHORT).show()
        }

        btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        btnTestVoice.setOnClickListener {
            if (tts?.isSpeaking == true) {
                tts?.stop()
                btnTestVoice.text = getString(R.string.btn_test)
                statusText.text = "Playback stopped"
                return@setOnClickListener
            }

            val text = sampleTextInput.text.toString().trim()
            if (text.isNotEmpty()) {
                val selectedVoice = voiceSpinner.selectedItem?.toString() ?: KokoroTtsService.DEFAULT_VOICE
                statusText.text = "Synthesizing voice sample..."
                btnTestVoice.text = "Stop"

                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sample_utterance")
                    putString("voiceName", selectedVoice)
                }

                tts?.voices?.find { it.name == selectedVoice }?.let { voice ->
                    tts?.voice = voice
                }

                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sample_utterance")
            }
        }
    }

    private fun setupLibrary() {
        rvLibrary.layoutManager = LinearLayoutManager(this)
        libraryAdapter = BookLibraryAdapter(
            onReadClicked = { book ->
                ReaderActivity.start(this, filePath = book.filePath, bookId = book.id)
            },
            onExportClicked = { book ->
                pendingExportBook = book
                val defaultExportName = "${book.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.txt"
                exportDocumentLauncher.launch(defaultExportName)
            },
            onDeleteClicked = { book ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Book")
                    .setMessage("Remove '${book.title}' from your library?")
                    .setPositiveButton("Delete") { _, _ ->
                        localBookManager.deleteBook(book.id)
                        refreshLibrary()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        rvLibrary.adapter = libraryAdapter
        refreshLibrary()
    }

    private fun refreshLibrary() {
        val books = localBookManager.getAllSavedBooks()
        libraryAdapter.submitList(books)
        if (books.isEmpty()) {
            tvEmptyLibrary.visibility = View.VISIBLE
            rvLibrary.visibility = View.GONE
        } else {
            tvEmptyLibrary.visibility = View.GONE
            rvLibrary.visibility = View.VISIBLE
        }
    }

    private fun handleSelectedEbookUri(uri: Uri) {
        var displayName = "Opened Document"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
            }
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_convert_ebook, null)
        val layoutConverting = dialogView.findViewById<View>(R.id.layoutConverting)
        val tvConvertingStatus = dialogView.findViewById<TextView>(R.id.tvConvertingStatus)
        val layoutPreview = dialogView.findViewById<View>(R.id.layoutPreview)
        val etBookTitle = dialogView.findViewById<EditText>(R.id.etBookTitle)
        val tvConversionStats = dialogView.findViewById<TextView>(R.id.tvConversionStats)
        val tvTextPreview = dialogView.findViewById<TextView>(R.id.tvTextPreview)
        val cbNormalizeSpeech = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbNormalizeSpeech)
        val pbNormalizing = dialogView.findViewById<ProgressBar>(R.id.pbNormalizing)
        val btnSaveToLibrary = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveToLibrary)
        val btnExportTxt = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExportTxt)
        val btnReadNow = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReadNow)
        val btnCloseDialog = dialogView.findViewById<Button>(R.id.btnCloseDialog)

        tvConvertingStatus.text = "Converting '$displayName' to readable text..."

        val dialog = AlertDialog.Builder(this)
            .setTitle("Convert Ebook")
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open file stream")

                val baseResult: ConvertedBookResult = when {
                    displayName.endsWith(".pdf", ignoreCase = true) -> {
                        PdfToTextConverter.convert(applicationContext, inputStream, displayName.removeSuffix(".pdf"))
                    }
                    displayName.endsWith(".epub", ignoreCase = true) -> {
                        EpubToTextConverter.convert(inputStream, displayName.removeSuffix(".epub"))
                    }
                    else -> {
                        // Plain text
                        val rawText = inputStream.bufferedReader().use { it.readText() }
                        val text = ArtifactCleaner.clean(rawText)
                        ConvertedBookResult(
                            title = displayName.substringBeforeLast("."),
                            author = "Unknown Author",
                            content = text,
                            chapterCount = 1,
                            originalFormat = "TXT"
                        )
                    }
                }

                var currentDisplayedResult = baseResult
                pendingConvertedResult = baseResult

                runOnUiThread {
                    layoutConverting.visibility = View.GONE
                    layoutPreview.visibility = View.VISIBLE

                    etBookTitle.setText(baseResult.title)

                    fun updateStatsAndPreview(content: String, isNormalized: Boolean = false) {
                        val wordCount = content.split(Regex("\\s+")).size
                        val sizeKb = content.toByteArray().size / 1024
                        val badge = if (isNormalized) " • 🗣️ Normalized for speech" else ""
                        tvConversionStats.text = "✔ Extracted ${baseResult.chapterCount} Chapters • ~$wordCount words • $sizeKb KB$badge"
                        tvTextPreview.text = content.take(1500) + if (content.length > 1500) "\n\n[... Remaining content preserved ...]" else ""
                    }

                    updateStatsAndPreview(baseResult.content, false)

                    cbNormalizeSpeech.setOnCheckedChangeListener { _, isChecked ->
                        pbNormalizing.visibility = View.VISIBLE
                        tvConversionStats.text = "⏳ Normalizing numbers, dates & abbreviations for speech..."
                        btnSaveToLibrary.isEnabled = false
                        btnExportTxt.isEnabled = false
                        btnReadNow.isEnabled = false

                        Thread {
                            val activeContent = if (isChecked) {
                                TextNormalizer.normalizeBookContent(baseResult.content)
                            } else {
                                baseResult.content
                            }
                            currentDisplayedResult = baseResult.copy(content = activeContent)
                            pendingConvertedResult = currentDisplayedResult
                            runOnUiThread {
                                pbNormalizing.visibility = View.GONE
                                btnSaveToLibrary.isEnabled = true
                                btnExportTxt.isEnabled = true
                                btnReadNow.isEnabled = true
                                updateStatsAndPreview(activeContent, isChecked)
                            }
                        }.start()
                    }

                    btnSaveToLibrary.setOnClickListener {
                        val toSave = pendingConvertedResult ?: baseResult
                        val finalTitle = etBookTitle.text.toString().ifBlank { toSave.title }
                        val saved = localBookManager.saveBook(
                            title = finalTitle,
                            author = toSave.author,
                            originalFormat = toSave.originalFormat,
                            content = toSave.content,
                            chapterCount = toSave.chapterCount
                        )
                        refreshLibrary()
                        dialog.dismiss()
                        Toast.makeText(this, "Saved '${saved.title}' to library!", Toast.LENGTH_SHORT).show()
                    }

                    btnExportTxt.setOnClickListener {
                        val toSave = pendingConvertedResult ?: baseResult
                        val finalTitle = etBookTitle.text.toString().ifBlank { toSave.title }
                        val saved = localBookManager.saveBook(
                            title = finalTitle,
                            author = toSave.author,
                            originalFormat = toSave.originalFormat,
                            content = toSave.content,
                            chapterCount = toSave.chapterCount
                        )
                        refreshLibrary()
                        pendingExportBook = saved
                        dialog.dismiss()
                        exportDocumentLauncher.launch("${finalTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.txt")
                    }

                    btnReadNow.setOnClickListener {
                        val toSave = pendingConvertedResult ?: baseResult
                        val finalTitle = etBookTitle.text.toString().ifBlank { toSave.title }
                        val saved = localBookManager.saveBook(
                            title = finalTitle,
                            author = toSave.author,
                            originalFormat = toSave.originalFormat,
                            content = toSave.content,
                            chapterCount = toSave.chapterCount
                        )
                        refreshLibrary()
                        dialog.dismiss()
                        ReaderActivity.start(this, filePath = saved.filePath, bookId = saved.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert ebook: $displayName", e)
                runOnUiThread {
                    dialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Conversion Failed")
                        .setMessage("Failed to parse '$displayName':\n${e.localizedMessage ?: e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun handleExportToUri(uri: Uri) {
        val book = pendingExportBook ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                localBookManager.exportBook(book, outputStream)
            }
            Toast.makeText(this, "Exported '${book.title}' successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export book", e)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pendingExportBook = null
        }
    }

    private fun setupVoiceSpinner() {
        val voices = voiceLoader.getAvailableVoices()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)
        voiceSpinner.adapter = adapter
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    statusText.text = "Synthesizing and playing..."
                    btnTestVoice.text = "Stop"
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    statusText.text = "Playback finished"
                    btnTestVoice.text = getString(R.string.btn_test)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    statusText.text = "Error during playback"
                    btnTestVoice.text = getString(R.string.btn_test)
                }
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            statusText.text = "Kokoro TTS engine ready"
        } else {
            statusText.text = "TTS initialization failed: error $status"
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
