package com.kokoro.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import com.kokoro.tts.engine.KokoroEngine
import com.kokoro.tts.engine.PhonemeConverter
import com.kokoro.tts.engine.TextSplitter
import com.kokoro.tts.engine.Tokenizer
import com.kokoro.tts.engine.VoiceStyleLoader
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Text-to-Speech Engine Service powered by Kokoro-82M ONNX.
 *
 * Registers with Android OS as a system-wide speech synthesizer, enabling
 * ebook readers (Moon+ Reader, ReadEra, Evie, Voice Dream) and accessibility
 * tools to read text with human-natural on-device neural voices.
 */
class KokoroTtsService : TextToSpeechService() {

    companion object {
        private const val TAG = "KokoroTtsService"
        const val DEFAULT_VOICE = "af_heart"
    }

    private lateinit var tokenizer: Tokenizer
    private lateinit var voiceLoader: VoiceStyleLoader
    private lateinit var phonemeConverter: PhonemeConverter
    private lateinit var kokoroEngine: KokoroEngine

    private val isStopping = AtomicBoolean(false)
    private var currentVoice: String = DEFAULT_VOICE

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "KokoroTtsService onCreate")

        tokenizer = Tokenizer.fromAssets(applicationContext, "vocab.json")
        voiceLoader = VoiceStyleLoader(applicationContext)
        phonemeConverter = PhonemeConverter(applicationContext, tokenizer)
        kokoroEngine = KokoroEngine(applicationContext, "model_quantized.onnx")

        // Pre-initialize in background to warm up ONNX session
        Thread {
            kokoroEngine.initialize()
        }.start()
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang.equals("en", ignoreCase = true) || lang.equals("eng", ignoreCase = true)) {
            return if (country.equals("US", ignoreCase = true) || country.equals("USA", ignoreCase = true)) {
                TextToSpeech.LANG_COUNTRY_AVAILABLE
            } else {
                TextToSpeech.LANG_AVAILABLE
            }
        }
        return TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onGetLanguage(): Array<String> {
        return arrayOf("eng", "USA", "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onStop() {
        Log.i(TAG, "Synthesis onStop received")
        isStopping.set(true)
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return

        isStopping.set(false)
        val text = request.charSequenceText?.toString() ?: ""
        if (text.isBlank()) return

        // Speed calculation: Android default rate is 100 (normal = 1.0f)
        val speedRate = (request.speechRate / 100.0f).coerceIn(0.5f, 2.0f)

        // Split long passage into sentences for real-time streaming
        val sentences = TextSplitter.splitIntoSentences(text)
        if (sentences.isEmpty()) return

        // Configure callback audio parameters: 24,000 Hz, 16-bit PCM, Mono
        val sampleRate = KokoroEngine.SAMPLE_RATE
        callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)

        // Inter-sentence pause: ~180 ms of silence (0.18s * 24000 * 2 bytes = 8640 bytes)
        val pauseBytes = ByteArray((0.18 * sampleRate * 2).toInt())

        for ((index, sentence) in sentences.withIndex()) {
            if (isStopping.get()) {
                Log.i(TAG, "Synthesis cancelled mid-stream")
                break
            }

            try {
                // 1. Convert text to IPA phonemes
                val phonemes = phonemeConverter.convertTextToPhonemes(sentence)
                if (phonemes.isBlank()) continue

                // 2. Tokenize with required [0, ... 0] padding
                val tokens = tokenizer.tokenizeWithPadding(phonemes)
                if (tokens.size <= 2) continue

                // 3. Extract matching 256-dim style vector for sentence length
                val styleVector = voiceLoader.getStyleVector(currentVoice, tokens.size - 2)

                // 4. Run ONNX inference
                val pcmAudio = kokoroEngine.synthesizeToPcm(tokens, styleVector, speedRate)

                if (pcmAudio != null && pcmAudio.isNotEmpty()) {
                    // Stream PCM audio chunk to audio track
                    callback.audioAvailable(pcmAudio, 0, pcmAudio.size)

                    // Insert natural pause after sentence if not the last sentence
                    if (index < sentences.size - 1) {
                        callback.audioAvailable(pauseBytes, 0, pauseBytes.size)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error synthesizing sentence: '$sentence'", e)
            }
        }

        callback.done()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "KokoroTtsService onDestroy")
        phonemeConverter.release()
        kokoroEngine.release()
    }
}
