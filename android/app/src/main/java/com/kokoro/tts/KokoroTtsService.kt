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
import android.speech.tts.Voice
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

    // In-memory cache for recent sentences to eliminate redundant inference
    private val audioCache = object : android.util.LruCache<String, ByteArray>(50) {
        override fun sizeOf(key: String, value: ByteArray): Int = 1
    }

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

    override fun onGetVoices(): MutableList<Voice> {
        val voices = mutableListOf<Voice>()
        for (name in voiceLoader.getAvailableVoices()) {
            voices.add(
                Voice(
                    name,
                    Locale.US,
                    Voice.QUALITY_VERY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    emptySet()
                )
            )
        }
        return voices
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        return if (voiceName != null && voiceLoader.getAvailableVoices().contains(voiceName)) {
            TextToSpeech.SUCCESS
        } else {
            TextToSpeech.ERROR
        }
    }

    override fun onLoadVoice(voiceName: String?): Int {
        if (voiceName != null && voiceLoader.getAvailableVoices().contains(voiceName)) {
            currentVoice = voiceName
            return TextToSpeech.SUCCESS
        }
        return TextToSpeech.ERROR
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        return DEFAULT_VOICE
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

        // Resolve requested voice
        val requestedVoice = request.voiceName
            ?: request.params?.getString("voiceName")
        val voiceToUse = if (!requestedVoice.isNullOrBlank() && voiceLoader.getAvailableVoices().contains(requestedVoice)) {
            requestedVoice
        } else {
            currentVoice
        }

        // Split long passage into sentences for real-time streaming
        val sentences = TextSplitter.splitIntoSentences(text)
        if (sentences.isEmpty()) return

        Log.i(TAG, "onSynthesizeText: received ${sentences.size} segment(s) for: '${text.take(60)}...'")

        // Configure callback audio parameters: 24,000 Hz, 16-bit PCM, Mono
        val sampleRate = KokoroEngine.SAMPLE_RATE
        callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)

        if (sentences.size == 1) {
            val pcmAudio = synthesizeSentence(sentences[0], voiceToUse, speedRate)
            if (pcmAudio != null && pcmAudio.isNotEmpty() && !isStopping.get()) {
                sendAudioInChunks(callback, pcmAudio)
            }
        } else {
            // Jitter Pre-buffer & Producer-Consumer Pipeline:
            // Pre-buffers at least 3.0s of audio or 2 sentences before beginning playback.
            // This guarantees that playback never starves between sentences regardless of upcoming sentence length!
            val targetPrebufferBytes = 24000 * 2 * 3 // 3.0 seconds (144,000 bytes)
            val audioQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
            val poisonPill = ByteArray(0)
            val prebufferReady = java.util.concurrent.CountDownLatch(1)

            val producer = Thread({
                var bufferedBytes = 0
                var bufferedCount = 0

                for ((idx, sentence) in sentences.withIndex()) {
                    if (isStopping.get()) break
                    val t0 = System.currentTimeMillis()
                    val pcm = synthesizeSentence(sentence, voiceToUse, speedRate)
                    val dt = System.currentTimeMillis() - t0

                    if (pcm != null && pcm.isNotEmpty()) {
                        val durationSec = pcm.size / 48000.0f
                        Log.i(TAG, "Pre-buffered chunk $idx [${sentence.take(25)}...]: ${String.format(Locale.US, "%.2f", durationSec)}s audio in ${dt}ms")
                        audioQueue.put(pcm)
                        bufferedBytes += pcm.size
                        bufferedCount++

                        // Release consumer when pre-buffer threshold is satisfied or on last sentence
                        if (bufferedBytes >= targetPrebufferBytes || bufferedCount >= 2 || idx == sentences.size - 1) {
                            prebufferReady.countDown()
                        }
                    }
                }
                prebufferReady.countDown() // Ensure latch opens even if errors occurred
                audioQueue.put(poisonPill)
            }, "KokoroProducerThread")
            producer.start()

            // Wait for pre-buffer to fill (up to 8 seconds) before starting playback
            prebufferReady.await(8, java.util.concurrent.TimeUnit.SECONDS)
            Log.i(TAG, "Initial pre-buffer ready, beginning seamless streaming")

            var chunkIndex = 0
            while (!isStopping.get()) {
                val pcm = audioQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                if (pcm.isEmpty()) break // Poison pill reached
                Log.d(TAG, "Streaming audio chunk $chunkIndex (${pcm.size} bytes)...")
                val success = sendAudioInChunks(callback, pcm)
                if (!success) break
                chunkIndex++
            }

            producer.interrupt()
            try {
                producer.join(500)
            } catch (e: InterruptedException) {
                // Ignore
            }
        }

        callback.done()
    }

    private fun synthesizeSentence(sentence: String, voiceName: String, speedRate: Float): ByteArray? {
        val cacheKey = "$voiceName:$speedRate:$sentence"
        audioCache.get(cacheKey)?.let { cached ->
            Log.d(TAG, "Cache HIT for '$sentence'")
            return cached
        }

        return try {
            val phonemes = phonemeConverter.convertTextToPhonemes(sentence)
            if (phonemes.isBlank()) return null

            val tokens = tokenizer.tokenizeWithPadding(phonemes)
            if (tokens.size <= 2) return null

            val styleVector = voiceLoader.getStyleVector(voiceName, tokens.size - 2)
            val pcm = kokoroEngine.synthesizeToPcm(tokens, styleVector, speedRate)
            if (pcm != null && pcm.isNotEmpty()) {
                audioCache.put(cacheKey, pcm)
            }
            pcm
        } catch (e: Exception) {
            Log.e(TAG, "Error synthesizing sentence: '$sentence'", e)
            null
        }
    }

    /**
     * Streams PCM byte buffer to SynthesisCallback in chunks no larger than callback.maxBufferSize.
     * Android's PlaybackSynthesisCallback enforces this limit and throws IllegalArgumentException if exceeded.
     */
    private fun sendAudioInChunks(callback: SynthesisCallback, data: ByteArray): Boolean {
        val maxBufferSize = if (callback.maxBufferSize > 0) callback.maxBufferSize else 8192
        var offset = 0
        while (offset < data.size && !isStopping.get()) {
            val chunkSize = minOf(maxBufferSize, data.size - offset)
            val result = callback.audioAvailable(data, offset, chunkSize)
            if (result != TextToSpeech.SUCCESS) {
                Log.w(TAG, "audioAvailable returned non-success code: $result (stopping synthesis)")
                return false
            }
            offset += chunkSize
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "KokoroTtsService onDestroy")
        phonemeConverter.release()
        kokoroEngine.release()
    }
}
