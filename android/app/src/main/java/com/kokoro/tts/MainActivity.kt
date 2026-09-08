package com.kokoro.tts

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.speech.tts.UtteranceProgressListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kokoro.tts.engine.VoiceStyleLoader
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
    private lateinit var btnReadSample: Button
    private lateinit var btnOpenEbook: Button

    private val openDocumentLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            loadEbookFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        voiceLoader = VoiceStyleLoader(applicationContext)

        btnReadSample = findViewById(R.id.btnReadSample)
        btnOpenEbook = findViewById(R.id.btnOpenEbook)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        sampleTextInput = findViewById(R.id.sampleTextInput)
        btnTestVoice = findViewById(R.id.btnTestVoice)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        statusText = findViewById(R.id.statusText)

        btnReadSample.setOnClickListener {
            com.kokoro.tts.reader.ui.ReaderActivity.start(this, com.kokoro.tts.reader.model.SampleBook.SAMPLE_BOOK)
        }

        btnOpenEbook.setOnClickListener {
            openDocumentLauncher.launch(arrayOf(
                "application/epub+zip",
                "text/plain",
                "*/*"
            ))
        }

        setupVoiceSpinner()

        // Initialize TTS bound specifically to our own Kokoro engine package
        tts = TextToSpeech(this, this, packageName)
        setupUtteranceListener()

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

                // Set voice directly on TTS instance if available
                tts?.voices?.find { it.name == selectedVoice }?.let { voice ->
                    tts?.voice = voice
                }

                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sample_utterance")
            }
        }

        btnOpenSettings.setOnClickListener {
            try {
                val intent = Intent("com.android.settings.TTS_SETTINGS")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun setupVoiceSpinner() {
        val voices = voiceLoader.getAvailableVoices()
        val voiceList = if (voices.isEmpty()) listOf(KokoroTtsService.DEFAULT_VOICE) else voices

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceList)
        voiceSpinner.adapter = adapter
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    statusText.text = "Speaking..."
                    btnTestVoice.text = "Stop"
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    statusText.text = "Kokoro TTS engine ready"
                    btnTestVoice.text = getString(R.string.btn_test)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    statusText.text = "Error during synthesis"
                    btnTestVoice.text = getString(R.string.btn_test)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                runOnUiThread {
                    statusText.text = "Synthesis error (code: $errorCode)"
                    btnTestVoice.text = getString(R.string.btn_test)
                }
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                statusText.text = "Language US not supported"
            } else {
                statusText.text = "Kokoro TTS engine ready"
            }
        } else {
            statusText.text = "Failed to connect to Kokoro TTS service"
        }
    }

    private fun loadEbookFromUri(uri: android.net.Uri) {
        statusText.text = "Loading and parsing ebook..."
        Thread {
            try {
                val contentResolver = applicationContext.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    runOnUiThread { statusText.text = "Failed to open file stream" }
                    return@Thread
                }

                var displayName = "Opened Book"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            displayName = cursor.getString(nameIndex) ?: displayName
                        }
                    }
                }

                val book = if (displayName.endsWith(".epub", ignoreCase = true)) {
                    com.kokoro.tts.reader.parser.EpubParser.parse(inputStream, displayName)
                } else {
                    com.kokoro.tts.reader.parser.TxtParser.parse(inputStream, displayName)
                }

                runOnUiThread {
                    statusText.text = "Kokoro TTS engine ready"
                    com.kokoro.tts.reader.ui.ReaderActivity.start(this, book)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening ebook from URI: $uri", e)
                runOnUiThread {
                    statusText.text = "Failed to parse book: ${e.message}"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
