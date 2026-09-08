package com.kokoro.tts.engine

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads Kokoro voice style vectors from assets/voices/.
 *
 * Each Kokoro v1.0 voice is stored as a raw little-endian float32 .bin file
 * containing 130,560 floats (510 rows × 256 dimensions = 522,240 bytes).
 *
 * For a synthesized segment of length N phonemes, the model uses style vector
 * row min(N, 510) - 1.
 */
class VoiceStyleLoader(private val context: Context) {

    companion object {
        private const val TAG = "VoiceStyleLoader"
        private const val VOICES_DIR = "voices"
        const val STYLE_DIM = 256
        const val MAX_ROWS = 510
    }

    /** Cache of loaded voice matrices: [rows][STYLE_DIM] */
    private val voiceCache = mutableMapOf<String, Array<FloatArray>>()

    /**
     * Get list of available voice names from assets/voices/.
     */
    fun getAvailableVoices(): List<String> {
        return try {
            context.assets.list(VOICES_DIR)
                ?.filter { it.endsWith(".bin") }
                ?.map { it.removeSuffix(".bin") }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list voices from $VOICES_DIR", e)
            emptyList()
        }
    }

    /**
     * Load full voice style matrix [510][256] for a given voice name.
     */
    fun loadVoice(name: String): Array<FloatArray> {
        voiceCache[name]?.let { return it }

        val path = "$VOICES_DIR/$name.bin"
        val bytes = context.assets.open(path).use { it.readBytes() }
        require(bytes.size == MAX_ROWS * STYLE_DIM * 4) {
            "Voice file $path size (${bytes.size} bytes) does not match expected size (${MAX_ROWS * STYLE_DIM * 4} bytes)"
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(MAX_ROWS * STYLE_DIM)
        buf.asFloatBuffer().get(floats)

        val matrix = Array(MAX_ROWS) { row ->
            floats.copyOfRange(row * STYLE_DIM, (row + 1) * STYLE_DIM)
        }

        voiceCache[name] = matrix
        Log.i(TAG, "Successfully loaded voice '$name' ($MAX_ROWS x $STYLE_DIM)")
        return matrix
    }

    /**
     * Slices the 256-dimensional style vector appropriate for the given token count.
     */
    fun getStyleVector(voiceName: String, tokenCount: Int): FloatArray {
        val matrix = loadVoice(voiceName)
        val index = (tokenCount.coerceIn(1, MAX_ROWS)) - 1
        return matrix[index]
    }
}
