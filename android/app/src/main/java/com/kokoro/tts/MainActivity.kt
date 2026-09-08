package com.kokoro.tts

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        voiceLoader = VoiceStyleLoader(applicationContext)

        voiceSpinner = findViewById(R.id.voiceSpinner)
        sampleTextInput = findViewById(R.id.sampleTextInput)
        btnTestVoice = findViewById(R.id.btnTestVoice)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        statusText = findViewById(R.id.statusText)

        setupVoiceSpinner()

        // Initialize TTS bound specifically to our own Kokoro engine package
        tts = TextToSpeech(this, this, packageName)

        btnTestVoice.setOnClickListener {
            val text = sampleTextInput.text.toString().trim()
            if (text.isNotEmpty()) {
                statusText.text = "Synthesizing voice sample..."
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sample_utterance")
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
        val voiceList = if (voices.isEmpty()) listOf("af_heart (default)") else voices

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceList)
        voiceSpinner.adapter = adapter
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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
