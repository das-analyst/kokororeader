package com.kokoro.tts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.kokoro.tts.engine.VoiceStyleLoader
import com.kokoro.tts.engine.normalizer.ArtifactCleaner
import com.kokoro.tts.engine.normalizer.TextNormalizer
import com.kokoro.tts.reader.converter.ChapterDetector
import com.kokoro.tts.reader.converter.ConvertedBookResult
import com.kokoro.tts.reader.converter.EpubToTextConverter
import com.kokoro.tts.reader.converter.PdfToTextConverter
import com.kokoro.tts.reader.manager.LocalBookManager
import com.kokoro.tts.reader.model.SavedBook
import com.kokoro.tts.reader.model.SpeakerProfile
import com.kokoro.tts.reader.ui.ReaderActivity
import com.kokoro.tts.ui.compose.EbookConvertSheet
import com.kokoro.tts.ui.compose.MainScreen
import com.kokoro.tts.ui.theme.KokoroTTSTheme
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var tts: TextToSpeech? = null
    private lateinit var voiceLoader: VoiceStyleLoader
    private lateinit var localBookManager: LocalBookManager

    private var availableVoiceIds by mutableStateOf<List<String>>(emptyList())
    private var selectedVoiceId by mutableStateOf(KokoroTtsService.DEFAULT_VOICE)
    private var sampleText by mutableStateOf("")
    private var isSpeakingSample by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Initializing TTS engine...")

    private var books by mutableStateOf<List<SavedBook>>(emptyList())
    private var bookToDelete by mutableStateOf<SavedBook?>(null)

    // Ebook Converter State
    private var isConvertingSheetVisible by mutableStateOf(false)
    private var isConverting by mutableStateOf(false)
    private var convertingDisplayName by mutableStateOf("")
    private var convertedResult by mutableStateOf<ConvertedBookResult?>(null)
    private var baseConvertedResult: ConvertedBookResult? = null
    private var convertedTitle by mutableStateOf("")
    private var isNormalized by mutableStateOf(false)
    private var isNormalizing by mutableStateOf(false)

    private var pendingExportBook: SavedBook? = null

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

        voiceLoader = VoiceStyleLoader(applicationContext)
        localBookManager = LocalBookManager(applicationContext)
        PdfToTextConverter.init(applicationContext)

        sampleText = getString(R.string.sample_text)
        setupVoiceList()

        tts = TextToSpeech(this, this, packageName)
        setupUtteranceListener()

        setContent {
            KokoroTTSTheme {
                MainScreen(
                    books = books,
                    availableVoiceIds = availableVoiceIds,
                    selectedVoiceId = selectedVoiceId,
                    sampleText = sampleText,
                    isSpeakingSample = isSpeakingSample,
                    statusMessage = statusMessage,
                    onVoiceChange = { newVoice -> selectedVoiceId = newVoice },
                    onSampleTextChange = { newText -> sampleText = newText },
                    onTestVoiceClick = { handleTestVoice() },
                    onOpenSettingsClick = { openAccessibilitySettings() },
                    onConvertEbookClick = {
                        openDocumentLauncher.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/pdf",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    onOpenTxtClick = {
                        openDocumentLauncher.launch(
                            arrayOf(
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    onReadBookClick = { book ->
                        ReaderActivity.start(this, filePath = book.filePath, bookId = book.id)
                    },
                    onExportBookClick = { book ->
                        pendingExportBook = book
                        val defaultExportName = "${book.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.txt"
                        exportDocumentLauncher.launch(defaultExportName)
                    },
                    onDeleteBookClick = { book ->
                        bookToDelete = book
                    }
                )

                // Delete Book Confirmation Dialog
                if (bookToDelete != null) {
                    val targetBook = bookToDelete!!
                    AlertDialog(
                        onDismissRequest = { bookToDelete = null },
                        title = { Text("Delete Book") },
                        text = { Text("Remove '${targetBook.title}' from your library?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    localBookManager.deleteBook(targetBook.id)
                                    bookToDelete = null
                                    refreshLibrary()
                                }
                            ) {
                                Text("Delete", color = Color(0xFFC62828))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { bookToDelete = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Ebook Conversion Modal Bottom Sheet
                if (isConvertingSheetVisible) {
                    EbookConvertSheet(
                        isConverting = isConverting,
                        displayName = convertingDisplayName,
                        result = convertedResult,
                        bookTitle = convertedTitle,
                        onTitleChange = { convertedTitle = it },
                        isNormalized = isNormalized,
                        isNormalizing = isNormalizing,
                        onNormalizeToggle = { checked -> handleNormalizeToggle(checked) },
                        onSaveToLibrary = { handleSaveConvertedToLibrary() },
                        onExportTxt = { handleExportConvertedTxt() },
                        onReadNow = { handleReadConvertedNow() },
                        onDismiss = { isConvertingSheetVisible = false }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLibrary()
    }

    private fun refreshLibrary() {
        books = localBookManager.getAllSavedBooks()
    }

    private fun setupVoiceList() {
        val rawVoices = voiceLoader.getAvailableVoices()
        val sortedProfiles = SpeakerProfile.PROFILES.filter { rawVoices.contains(it.id) }
        val remainingIds = rawVoices.filter { id -> sortedProfiles.none { it.id == id } }
        availableVoiceIds = sortedProfiles.map { it.id } + remainingIds
        if (availableVoiceIds.isNotEmpty() && !availableVoiceIds.contains(selectedVoiceId)) {
            selectedVoiceId = availableVoiceIds.first()
        }
    }

    private fun handleTestVoice() {
        if (tts?.isSpeaking == true || isSpeakingSample) {
            tts?.stop()
            isSpeakingSample = false
            statusMessage = "Playback stopped"
            return
        }

        val text = sampleText.trim()
        if (text.isNotEmpty()) {
            statusMessage = "Synthesizing voice sample..."
            isSpeakingSample = true

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sample_utterance")
                putString("voiceName", selectedVoiceId)
            }

            tts?.voices?.find { it.name == selectedVoiceId }?.let { voice ->
                tts?.voice = voice
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sample_utterance")
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
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

        convertingDisplayName = displayName
        isConverting = true
        isConvertingSheetVisible = true
        convertedResult = null
        isNormalized = false
        isNormalizing = false

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
                        val rawText = inputStream.bufferedReader().use { it.readText() }
                        val text = ArtifactCleaner.clean(rawText)
                        val detected = ChapterDetector.detect(text)

                        var title = displayName.substringBeforeLast(".")
                        var author = "Unknown Author"
                        for (line in text.lineSequence().take(15)) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("# ")) {
                                title = trimmed.removePrefix("# ").trim()
                            } else if (trimmed.startsWith("Author:", ignoreCase = true)) {
                                author = trimmed.substringAfter(":").trim()
                            }
                        }

                        ConvertedBookResult(
                            title = title,
                            author = author,
                            content = text,
                            chapterCount = detected.chapters.size.coerceAtLeast(1),
                            originalFormat = "TXT"
                        )
                    }
                }

                baseConvertedResult = baseResult
                runOnUiThread {
                    convertedResult = baseResult
                    convertedTitle = baseResult.title
                    isConverting = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert ebook: $displayName", e)
                runOnUiThread {
                    isConvertingSheetVisible = false
                    Toast.makeText(this, "Failed to convert: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun handleNormalizeToggle(isChecked: Boolean) {
        val base = baseConvertedResult ?: return
        isNormalizing = true

        Thread {
            val content = if (isChecked) {
                TextNormalizer.normalizeBookContent(base.content)
            } else {
                base.content
            }
            runOnUiThread {
                isNormalized = isChecked
                convertedResult = base.copy(content = content)
                isNormalizing = false
            }
        }.start()
    }

    private fun handleSaveConvertedToLibrary() {
        val toSave = convertedResult ?: baseConvertedResult ?: return
        val finalTitle = convertedTitle.ifBlank { toSave.title }

        val saved = localBookManager.saveBook(
            title = finalTitle,
            author = toSave.author,
            originalFormat = toSave.originalFormat,
            content = toSave.content,
            chapterCount = toSave.chapterCount
        )

        val downloadsUri = localBookManager.saveBookToDownloads(finalTitle, toSave.content)
        refreshLibrary()
        isConvertingSheetVisible = false

        val safeName = "${finalTitle.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")}.txt"
        val msg = if (downloadsUri != null) {
            "✔ Saved to Library & Downloads/$safeName"
        } else {
            "Saved '${saved.title}' to library!"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun handleExportConvertedTxt() {
        val toSave = convertedResult ?: baseConvertedResult ?: return
        val finalTitle = convertedTitle.ifBlank { toSave.title }
        val saved = localBookManager.saveBook(
            title = finalTitle,
            author = toSave.author,
            originalFormat = toSave.originalFormat,
            content = toSave.content,
            chapterCount = toSave.chapterCount
        )
        refreshLibrary()
        pendingExportBook = saved
        isConvertingSheetVisible = false
        exportDocumentLauncher.launch("${finalTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.txt")
    }

    private fun handleReadConvertedNow() {
        val toSave = convertedResult ?: baseConvertedResult ?: return
        val finalTitle = convertedTitle.ifBlank { toSave.title }
        val saved = localBookManager.saveBook(
            title = finalTitle,
            author = toSave.author,
            originalFormat = toSave.originalFormat,
            content = toSave.content,
            chapterCount = toSave.chapterCount
        )
        localBookManager.saveBookToDownloads(finalTitle, toSave.content)
        refreshLibrary()
        isConvertingSheetVisible = false
        ReaderActivity.start(this, filePath = saved.filePath, bookId = saved.id)
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

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    statusMessage = "Synthesizing and playing..."
                    isSpeakingSample = true
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    statusMessage = "Playback finished"
                    isSpeakingSample = false
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    statusMessage = "Error during playback"
                    isSpeakingSample = false
                }
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            statusMessage = "Kokoro TTS engine ready"
        } else {
            statusMessage = "TTS initialization failed: error $status"
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
