package com.kokoro.tts.reader.player

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
import com.kokoro.tts.engine.director.SpeechDirector
import com.kokoro.tts.reader.model.SentenceItem
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Direct AudioTrack lookahead streaming player for Ebooks.
 *
 * Integrated with SpeechDirector (Path A: "Smart Director + Fast Performer"):
 * Dynamically modulates sentence tempo and streams hardware-level PCM silence buffers
 * (220ms - 1400ms) into AudioTrack, eliminating abrupt 0ms runaway playback.
 */
class BookPlayer(
    private val kokoroEngine: KokoroEngine,
    private val phonemeConverter: PhonemeConverter,
    private val tokenizer: Tokenizer,
    private val voiceLoader: VoiceStyleLoader,
    var listener: PlaybackListener? = null
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
        fun onChapterFinished(lastIndex: Int) {}
    }

    private var sentences: List<SentenceItem> = emptyList()
    private val currentSentenceIndex = AtomicInteger(0)
    private val targetSentenceIndex = AtomicInteger(0)

    private val isPlaying = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)
    private val generationId = AtomicInteger(0)
    private val seekGenerationId = AtomicInteger(0)

    var currentVoice: String = KokoroTtsService.DEFAULT_VOICE
    var currentSpeed: Float = 1.0f

    private val trackLock = Any()

    @Volatile
    private var activeProducerThread: Thread? = null

    @Volatile
    private var activePlaybackThread: Thread? = null

    data class SentenceAudio(
        val speechPcm: ByteArray,
        val silencePcm: ByteArray
    )

    // 50-entry in-memory sentence audio cache
    private val audioCache = object : LruCache<String, SentenceAudio>(50) {
        override fun sizeOf(key: String, value: SentenceAudio): Int = 1
    }

    private data class AudioChunk(
        val genId: Int,
        val seekGenId: Int,
        val sentenceIndex: Int,
        val pcm: ByteArray,
        val silencePcm: ByteArray,
        val isLast: Boolean
    )

    private val audioQueue = LinkedBlockingQueue<AudioChunk>(LOOKAHEAD_QUEUE_CAPACITY)

    private var audioTrack: AudioTrack? = null

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

        synchronized(trackLock) {
            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
        }
    }

    fun setSentences(newSentences: List<SentenceItem>, startIndex: Int = 0) {
        pause()
        sentences = newSentences
        currentSentenceIndex.set(startIndex)
        targetSentenceIndex.set(startIndex)
        clearQueue()
        preWarmSentence(startIndex)
    }

    private fun getCacheKey(sentence: SentenceItem): String {
        return "$currentVoice:$currentSpeed:${sentence.text}:${sentence.isParagraphEnd}:${sentence.isDialogue}"
    }

    /**
     * Pre-synthesizes a sentence in the background into the audio cache.
     * Called when a book or chapter loads so Sentence 0 is immediately ready for instant playback.
     */
    fun preWarmSentence(index: Int = 0) {
        if (index < 0 || index >= sentences.size) return
        Thread({
            val sentence = sentences.getOrNull(index) ?: return@Thread
            val isLast = index == sentences.size - 1
            val cacheKey = getCacheKey(sentence)
            if (audioCache.get(cacheKey) == null) {
                val performance = SpeechDirector.direct(sentence, isLast, currentSpeed)
                val pcm = synthesize(sentence.text, performance.effectiveSpeed)
                if (pcm != null && pcm.isNotEmpty()) {
                    val silencePcm = SpeechDirector.generateSilencePcm(performance.postSilenceMs, SAMPLE_RATE)
                    audioCache.put(cacheKey, SentenceAudio(pcm, silencePcm))
                    Log.i(TAG, "Pre-warmed sentence $index into cache (${pcm.size} bytes speech, ${silencePcm.size} bytes silence, role=${performance.role})")
                }
            }
        }, "BookPlayer-PreWarmer").start()
    }

    fun play() {
        if (sentences.isEmpty()) return
        if (isPlaying.get()) return

        val resumeIndex = currentSentenceIndex.get().coerceIn(0, sentences.size - 1)
        targetSentenceIndex.set(resumeIndex)

        isPlaying.set(true)
        isStopped.set(false)
        listener?.onPlaybackStateChanged(true)

        val startSentence = sentences.getOrNull(resumeIndex)
        val isCached = startSentence != null && audioCache.get(getCacheKey(startSentence)) != null
        if (!isCached) {
            listener?.onBuffering(true)
        }

        synchronized(trackLock) {
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                setupAudioTrack()
            }
            try {
                audioTrack?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting AudioTrack playback", e)
            }
        }

        startThreads()
    }

    fun pause() {
        if (!isPlaying.get()) return
        isPlaying.set(false)
        targetSentenceIndex.set(currentSentenceIndex.get())
        synchronized(trackLock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack pause error", e)
            }
        }
        clearQueue()
        stopThreads()
        listener?.onBuffering(false)
        listener?.onPlaybackStateChanged(false)
    }

    /**
     * Seamlessly updates voice and speed settings during active reading.
     *
     * If currently playing:
     * 1. The sentence currently speaking to the listener finishes naturally without stutter or cutoff.
     * 2. The lookahead queue of upcoming sentences (which had old voice/speed) is purged.
     * 3. Target synthesis index moves to the NEXT sentence (currentIndex + 1).
     * 4. The synthesizer immediately generates upcoming audio using the new voice and speed.
     * This avoids repeating the current sentence from the start and avoids pause/play stalls.
     */
    fun updateVoiceAndSpeed(newVoice: String, newSpeed: Float) {
        val voiceChanged = currentVoice != newVoice
        val speedChanged = kotlin.math.abs(currentSpeed - newSpeed) > 0.01f
        if (!voiceChanged && !speedChanged) return

        currentVoice = newVoice
        currentSpeed = newSpeed

        if (!isPlaying.get()) {
            clearQueue()
            preWarmSentence(currentSentenceIndex.get())
            return
        }

        val nextIndex = currentSentenceIndex.get() + 1
        targetSentenceIndex.set(nextIndex)

        val newGen = generationId.incrementAndGet()
        clearQueue()

        Log.i(TAG, "Seamless voice switch -> $newVoice @ ${"%.2f".format(newSpeed)}x. Current sentence ${currentSentenceIndex.get()} finishing, next sentence $nextIndex queuing with new voice (gen=$newGen).")
        preWarmSentence(nextIndex)
    }

    fun seekToSentence(index: Int) {
        val clamped = index.coerceIn(0, sentences.size - 1)
        targetSentenceIndex.set(clamped)
        currentSentenceIndex.set(clamped)

        // Increment BOTH seekGenerationId (aborts active chunk) and generationId (clears pipeline)
        seekGenerationId.incrementAndGet()
        val newGenId = generationId.incrementAndGet()
        clearQueue()

        val targetSentence = sentences.getOrNull(clamped)
        val isCached = targetSentence != null && audioCache.get(getCacheKey(targetSentence)) != null
        if (isPlaying.get() && !isCached) {
            listener?.onBuffering(true)
        }

        synchronized(trackLock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                if (isPlaying.get()) {
                    audioTrack?.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack seek error", e)
            }
        }

        if (isPlaying.get()) {
            if (activeProducerThread == null || !activeProducerThread!!.isAlive ||
                activePlaybackThread == null || !activePlaybackThread!!.isAlive) {
                startThreads()
            }
        }

        listener?.onSentenceStarted(clamped)
        preWarmSentence(clamped + 1)
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

    private fun stopThreads() {
        val oldProducer = activeProducerThread
        val oldPlayback = activePlaybackThread
        activeProducerThread = null
        activePlaybackThread = null

        oldProducer?.interrupt()
        oldPlayback?.interrupt()
        clearQueue()

        try {
            oldProducer?.join(150)
            oldPlayback?.join(150)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun startThreads() {
        stopThreads()

        // 1. Synthesizer Thread (Producer) - runs ahead and fills queue
        val producer = Thread({
            val myThread = Thread.currentThread()
            var synthIndex = targetSentenceIndex.get()
            var myGenId = generationId.get()

            while (activeProducerThread === myThread && isPlaying.get() && !isStopped.get() && !myThread.isInterrupted) {
                val currentGen = generationId.get()
                if (currentGen != myGenId) {
                    // Generation changed: update cursor and continue without leaking threads
                    myGenId = currentGen
                    synthIndex = targetSentenceIndex.get()
                    continue
                }

                if (synthIndex >= sentences.size) {
                    // Reached end of chapter for synthesis: wait for seek or chapter end
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    continue
                }

                val sentence = sentences[synthIndex]
                val isLast = synthIndex == sentences.size - 1
                val performance = SpeechDirector.direct(sentence, isLast, currentSpeed)
                val cacheKey = getCacheKey(sentence)
                val cached = audioCache.get(cacheKey)

                val sentenceAudio = if (cached != null) {
                    cached
                } else {
                    val pcm = synthesize(sentence.text, performance.effectiveSpeed)
                    if (pcm != null && pcm.isNotEmpty()) {
                        val silence = SpeechDirector.generateSilencePcm(performance.postSilenceMs, SAMPLE_RATE)
                        val sa = SentenceAudio(pcm, silence)
                        audioCache.put(cacheKey, sa)
                        sa
                    } else null
                }

                // Check if thread was invalidated or generation changed while synthesis was running
                if (activeProducerThread !== myThread || !isPlaying.get() || isStopped.get() || myThread.isInterrupted) {
                    break
                }
                if (generationId.get() != myGenId) {
                    continue
                }

                if (sentenceAudio != null) {
                    val chunk = AudioChunk(
                        genId = myGenId,
                        seekGenId = seekGenerationId.get(),
                        sentenceIndex = synthIndex,
                        pcm = sentenceAudio.speechPcm,
                        silencePcm = sentenceAudio.silencePcm,
                        isLast = isLast
                    )
                    Log.i(TAG, "Director: sentence $synthIndex | role=${performance.role} | speed=${"%.2f".format(performance.effectiveSpeed)} (base=$currentSpeed) | silence=${performance.postSilenceMs}ms | pEnd=${sentence.isParagraphEnd}")
                    var offered = false
                    while (activeProducerThread === myThread && isPlaying.get() && generationId.get() == myGenId && !offered) {
                        try {
                            offered = audioQueue.offer(chunk, 100, TimeUnit.MILLISECONDS)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                }
                synthIndex++
            }
        }, "BookPlayer-Synthesizer")

        // 2. Playback Thread (Consumer) - streams PCM and directed silence to AudioTrack continuously
        val playback = Thread({
            val myThread = Thread.currentThread()
            var activeGenId = generationId.get()
            var lastPlayedSentenceIndex = -1
            var lastPlayedGenId = -1

            while (activePlaybackThread === myThread && isPlaying.get() && !isStopped.get() && !myThread.isInterrupted) {
                val currentGen = generationId.get()
                if (currentGen != activeGenId) {
                    activeGenId = currentGen
                    continue
                }

                val chunk = try {
                    audioQueue.poll(200, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }

                if (chunk == null) {
                    continue
                }

                if (activePlaybackThread !== myThread || !isPlaying.get() || isStopped.get()) {
                    break
                }

                if (chunk.genId != generationId.get()) {
                    // Stale chunk from prior seek or voice switch, discard
                    continue
                }

                // Deduplication safeguard: NEVER play the same sentence twice in the same generation
                if (chunk.sentenceIndex == lastPlayedSentenceIndex && chunk.genId == lastPlayedGenId) {
                    Log.w(TAG, "Discarding duplicate chunk for sentence ${chunk.sentenceIndex}")
                    continue
                }
                lastPlayedSentenceIndex = chunk.sentenceIndex
                lastPlayedGenId = chunk.genId

                currentSentenceIndex.set(chunk.sentenceIndex)
                targetSentenceIndex.set(chunk.sentenceIndex)
                listener?.onBuffering(false)
                listener?.onSentenceStarted(chunk.sentenceIndex)
                Log.i(TAG, "Playing sentence ${chunk.sentenceIndex}: ${chunk.pcm.size}B speech + ${chunk.silencePcm.size}B silence (${chunk.silencePcm.size / 48}ms)")

                // 1. Stream speech PCM bytes to AudioTrack in chunks
                val track = audioTrack ?: continue
                var offset = 0
                val data = chunk.pcm
                val chunkSize = 8192
                val currentSeekGen = chunk.seekGenId

                while (offset < data.size && isPlaying.get() && currentSeekGen == seekGenerationId.get() && activePlaybackThread === myThread) {
                    val count = minOf(chunkSize, data.size - offset)
                    val written = synchronized(trackLock) {
                        if (isPlaying.get() && activePlaybackThread === myThread) {
                            track.write(data, offset, count)
                        } else -1
                    }
                    if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: $written")
                        break
                    }
                    offset += count
                }

                // 2. Stream hardware PCM silence buffer for natural cadence
                if (chunk.silencePcm.isNotEmpty() && isPlaying.get() && currentSeekGen == seekGenerationId.get() && activePlaybackThread === myThread) {
                    var silenceOffset = 0
                    val silenceData = chunk.silencePcm
                    while (silenceOffset < silenceData.size && isPlaying.get() && currentSeekGen == seekGenerationId.get() && activePlaybackThread === myThread) {
                        val count = minOf(chunkSize, silenceData.size - silenceOffset)
                        val written = synchronized(trackLock) {
                            if (isPlaying.get() && activePlaybackThread === myThread) {
                                track.write(silenceData, silenceOffset, count)
                            } else -1
                        }
                        if (written < 0) break
                        silenceOffset += count
                    }
                }

                // 3. Handle end of chapter
                if (chunk.isLast && isPlaying.get() && currentSeekGen == seekGenerationId.get() && activePlaybackThread === myThread) {
                    pause()
                    listener?.onChapterFinished(chunk.sentenceIndex)
                }
            }
        }, "BookPlayer-Playback")

        activeProducerThread = producer
        activePlaybackThread = playback
        producer.start()
        playback.start()
    }

    private fun synthesize(text: String, speed: Float): ByteArray? {
        return try {
            val phonemes = phonemeConverter.convertTextToPhonemes(text)
            if (phonemes.isBlank()) return null

            val tokens = tokenizer.tokenizeWithPadding(phonemes)
            if (tokens.size <= 2) return null

            val styleVector = voiceLoader.getStyleVector(currentVoice, tokens.size - 2)
            kokoroEngine.synthesizeToPcm(tokens, styleVector, speed)
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error for: '$text'", e)
            null
        }
    }

    fun release() {
        isStopped.set(true)
        pause()
        stopThreads()
        synchronized(trackLock) {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioTrack", e)
            }
            audioTrack = null
        }
    }
}
