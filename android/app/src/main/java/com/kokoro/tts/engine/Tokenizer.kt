package com.kokoro.tts.engine

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Character-level tokenizer for Kokoro TTS.
 * Maps IPA phoneme characters to integer token IDs using the model's vocabulary.
 */
class Tokenizer(private val vocab: Map<Char, Int>) {

    companion object {
        private const val TAG = "Tokenizer"
        const val MAX_PHONEME_LENGTH = 510

        /**
         * Load tokenizer from an assets/vocab.json file.
         */
        fun fromAssets(context: Context, assetPath: String = "vocab.json"): Tokenizer {
            return try {
                val jsonStr = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonStr)
                val map = mutableMapOf<Char, Int>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.isNotEmpty()) {
                        map[key[0]] = jsonObject.getInt(key)
                    }
                }
                Log.i(TAG, "Loaded vocabulary with ${map.size} tokens from $assetPath")
                Tokenizer(map)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load vocab from $assetPath, using fallback", e)
                Tokenizer(emptyMap())
            }
        }
    }

    /**
     * Check if a character is supported by the model vocabulary.
     */
    fun isKnown(ch: Char): Boolean = vocab.containsKey(ch)

    /**
     * Filter phoneme string down to only valid known characters.
     */
    fun filterKnown(phonemes: String): String {
        return phonemes.filter { isKnown(it) || it.isWhitespace() }
    }

    /**
     * Convert a phoneme string to an array of integer token IDs.
     * Characters not in the vocabulary are skipped.
     */
    fun tokenize(phonemes: String): LongArray {
        val tokens = mutableListOf<Long>()
        for (ch in phonemes) {
            val id = vocab[ch]
            if (id != null) {
                tokens.add(id.toLong())
            }
        }
        return tokens.toLongArray()
    }

    /**
     * Tokenize and pad with 0 at the beginning and end (required by Kokoro ONNX model).
     * Input:  [t1, t2, t3]
     * Output: [0, t1, t2, t3, 0]
     */
    fun tokenizeWithPadding(phonemes: String): LongArray {
        val tokens = tokenize(phonemes)
        val padded = LongArray(tokens.size + 2)
        padded[0] = 0L
        System.arraycopy(tokens, 0, padded, 1, tokens.size)
        padded[padded.size - 1] = 0L
        return padded
    }
}
