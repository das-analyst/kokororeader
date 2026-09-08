package com.kokoro.tts.reader.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import android.util.LruCache
import com.kokoro.tts.KokoroTtsService
import com.kokoro.tts.engine.KokoroEngine
import com.kokoro.tts.engine.PhonemeConverter
import com.kokoro.tts.engine.Tokenizer
import com.kokoro.tts.engine.VoiceStyleLoader
import com.kokoro.tts.reader.model.SentenceItem
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Direct AudioTrack lookahead streaming player for Ebooks.
 *
 * Pre-synthesizes upcoming sentences in a background producer thread while AudioTrack
 * continuously plays the current sentence, guaranteeing 0 ms gap between sentences.
 */
class BookPlayer(
    private val context: Context,
    private val kokoroEngine: KokoroEngine,
    private val phonemeConverter: PhonemeConverter,
    private val tokenizer: Tokenizer,
    private val voiceLoader: VoiceStyleLoader
) {
    companion object {
        private const val TAG = "BookPlayer"
        private const val SAMPLE_RATE = KokoroEngine.SAMPLE_RATE
        private const val LOOKAHEAD_QUEUE_CAPACITY = 4
    }

    interface PlaybackListener {
        fun onSentenceStarted(sentenceIndex: Int)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onBuffering(isBuffering: Boolean)
        fun onError(message: String)
    }

    var listener: PlaybackListener? = null

    private var sentences: List<SentenceItem> = emptyList()
    private val currentSentenceIndex = AtomicInteger(0)
    private val targetSentenceIndex = AtomicInteger(0)

    private val isPlaying = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)
    private val generationId = AtomicInteger(0)

    var currentVoice: String = KokoroTtsService.DEFAULT_VOICE
    var currentSpeed: Float = 1.0f

    // 50-entry in-memory sentence audio cache
    private val audioCache = object : LruCache<String, ByteArray>(50) {
        override fun sizeOf(key: String, value: ByteArray): Int = 1
    }

    private data class AudioChunk(
        val genId: Int,
        val sentenceIndex: Int,
        val pcm: ByteArray
    )

    private val audioQueue = LinkedBlockingQueue<AudioChunk>(LOOKAHEAD_QUEUE_CAPACITY)

    private var audioTrack: AudioTrack? = null
    private var producerThread: Thread? = null
    private var playbackThread: Thread? = null

    init {
        setupAudioTrack()
    }

    private fun setupAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize * 4, 32768)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        audioTrack = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.play()
    }

    fun setSentences(newSentences: List<SentenceItem>, startIndex: Int = 0) {
        pause()
        sentences = newSentences
        currentSentenceIndex.set(startIndex)
        targetSentenceIndex.set(startIndex)
        clearQueue()
    }

    fun play() {
        if (sentences.isEmpty()) return
        if (isPlaying.get()) return

        isPlaying.set(true)
        isStopped.set(false)
        listener?.onPlaybackStateChanged(true)

        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            setupAudioTrack()
        }
        audioTrack?.play()

        startThreads()
    }

    fun pause() {
        if (!isPlaying.get()) return
        isPlaying.set(false)
        audioTrack?.pause()
        audioTrack?.flush()
        clearQueue()
        listener?.onPlaybackStateChanged(false)
    }

    fun seekToSentence(index: Int) {
        val clamped = index.coerceIn(0, sentences.size - 1)
        targetSentenceIndex.set(clamped)
        currentSentenceIndex.set(clamped)

        // Increment generation ID to invalidate any queued audio from previous position
        generationId.incrementAndGet()
        clearQueue()

        audioTrack?.pause()
        audioTrack?.flush()
        if (isPlaying.get()) {
            audioTrack?.play()
        }

        listener?.onSentenceStarted(clamped)
    }

    fun nextSentence() {
        val next = currentSentenceIndex.get() + 1
        if (next < sentences.size) {
            seekToSentence(next)
        }
    }

    fun previousSentence() {
        val prev = (currentSentenceIndex.get() - 1).coerceAtLeast(0)
        seekToSentence(prev)
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying.get()

    fun getCurrentIndex(): Int = currentSentenceIndex.get()

    private fun clearQueue() {
        audioQueue.clear()
    }

    private fun startThreads() {
        stopThreads()

        // 1. Synthesizer Thread (Producer) - runs ahead and fills queue
        producerThread = Thread({
            var synthIndex = targetSentenceIndex.get()
            val myGenId = generationId.get()

            while (isPlaying.get() && !isStopped.get()) {
                // If user jumped to a different sentence, reset synthesis cursor
                if (generationId.get() != myGenId) {
                    break
                }

                if (synthIndex >= sentences.size) {
                    // Reached end of chapter
                    break
                }

                val sentence = sentences[synthIndex]
                val cacheKey = "$currentVoice:$currentSpeed:${sentence.text}"
                val cached = audioCache.get(cacheKey)

                val pcm = cached ?: synthesize(sentence.text)
                if (pcm != null && pcm.isNotEmpty()) {
                    audioCache.put(cacheKey, pcm)

                    val chunk = AudioChunk(myGenId, synthIndex, pcm)
                    var offered = false
                    while (isPlaying.get() && generationId.get() == myGenId && !offered) {
                        try {
                            offered = audioQueue.offer(chunk, 100, TimeUnit.MILLISECONDS)
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                }
                synthIndex++
            }
        }, "BookPlayer-Synthesizer").apply { start() }

        // 2. Playback Thread (Consumer) - streams PCM to AudioTrack continuously
        playbackThread = Thread({
            var activeGenId = generationId.get()

            while (isPlaying.get() && !isStopped.get()) {
                if (generationId.get() != activeGenId) {
                    activeGenId = generationId.get()
                    continue
                }

                val chunk = try {
                    audioQueue.poll(200, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    null
                }

                if (chunk == null) {
                    continue
                }

                if (chunk.genId != generationId.get()) {
                    // Stale chunk from prior seek, discard
                    continue
                }

                currentSentenceIndex.set(chunk.sentenceIndex)
                listener?.onSentenceStarted(chunk.sentenceIndex)

                // Stream PCM bytes to AudioTrack in chunks
                val track = audioTrack ?: continue
                var offset = 0
                val data = chunk.pcm
                val chunkSize = 8192

                while (offset < data.size && isPlaying.get() && chunk.genId == generationId.get()) {
                    val count = minOf(chunkSize, data.size - offset)
                    val written = track.write(data, offset, count)
                    if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: $written")
                        break
                    }
                    offset += count
                }
            }
        }, "BookPlayer-Playback").apply { start() }
    }

    private fun synthesize(text: String): ByteArray? {
        return try {
            val phonemes = phonemeConverter.convertTextToPhonemes(text)
            if (phonemes.isBlank()) return null

            val tokens = tokenizer.tokenizeWithPadding(phonemes)
            if (tokens.size <= 2) return null

            val styleVector = voiceLoader.getStyleVector(currentVoice, tokens.size - 2)
            kokoroEngine.synthesizeToPcm(tokens, styleVector, currentSpeed)
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error for: '$text'", e)
            null
        }
    }

    private fun stopThreads() {
        producerThread?.interrupt()
        playbackThread?.interrupt()
        producerThread = null
        playbackThread = null
    }

    fun release() {
        isStopped.set(true)
        pause()
        stopThreads()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
