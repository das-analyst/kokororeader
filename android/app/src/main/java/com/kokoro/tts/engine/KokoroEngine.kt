package com.kokoro.tts.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Executes on-device neural inference using Microsoft ONNX Runtime Mobile.
 * Synthesizes speech from token sequences and style embeddings at 24,000 Hz.
 */
class KokoroEngine(
    private val context: Context,
    private val modelAssetPath: String = "model_quantized.onnx"
) {
    companion object {
        private const val TAG = "KokoroEngine"
        const val SAMPLE_RATE = 24000
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokensInputName: String = "tokens"

    @Synchronized
    fun initialize(): Boolean {
        if (session != null) return true

        return try {
            val startTime = System.currentTimeMillis()
            env = OrtEnvironment.getEnvironment()

            val options = OrtSession.SessionOptions().apply {
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                setIntraOpNumThreads(threads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            // Copy model to internal storage if not already there, for zero-copy file mapping
            val modelFile = File(context.filesDir, modelAssetPath)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                Log.i(TAG, "Extracting $modelAssetPath to ${modelFile.absolutePath}...")
                context.assets.open(modelAssetPath).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            session = env!!.createSession(modelFile.absolutePath, options)

            // Detect whether the model calls the token input 'tokens' or 'input_ids'
            val inputNames = session!!.inputNames
            tokensInputName = if (inputNames.contains("input_ids")) "input_ids" else "tokens"

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Kokoro ONNX engine initialized in ${elapsed}ms. Token input key: '$tokensInputName'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime session", e)
            false
        }
    }

    /**
     * Synthesize audio from padded token IDs and a 256-dimensional style embedding.
     * Returns 16-bit Little-Endian PCM bytes at 24,000 Hz mono.
     */
    @Synchronized
    fun synthesizeToPcm(
        tokensWithPadding: LongArray,
        styleVector: FloatArray,
        speed: Float = 1.0f
    ): ByteArray? {
        val sess = session ?: run {
            if (!initialize()) return null
            session ?: return null
        }
        val environment = env ?: return null

        val numTokens = tokensWithPadding.size
        val tokensBuffer = LongBuffer.wrap(tokensWithPadding)
        val styleBuffer = FloatBuffer.wrap(styleVector)
        val speedBuffer = FloatBuffer.wrap(floatArrayOf(speed))

        val tokensTensor = OnnxTensor.createTensor(environment, tokensBuffer, longArrayOf(1, numTokens.toLong()))
        val styleTensor = OnnxTensor.createTensor(environment, styleBuffer, longArrayOf(1, styleVector.size.toLong()))
        val speedTensor = OnnxTensor.createTensor(environment, speedBuffer, longArrayOf(1))

        return try {
            val inputs = mapOf(
                tokensInputName to tokensTensor,
                "style" to styleTensor,
                "speed" to speedTensor
            )

            val results = sess.run(inputs)
            val outputVal = results[0].value
            val floatAudio = when (outputVal) {
                is FloatArray -> outputVal
                is Array<*> -> (outputVal as Array<FloatArray>)[0]
                else -> {
                    Log.e(TAG, "Unexpected output tensor type: ${outputVal?.javaClass?.name}")
                    return null
                }
            }
            results.close()

            // Convert float32 [-1.0, 1.0] to 16-bit PCM bytes
            floatToPcm16(floatAudio)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error during synthesis", e)
            null
        } finally {
            tokensTensor.close()
            styleTensor.close()
            speedTensor.close()
        }
    }

    /**
     * Convert float array [-1.0, 1.0] into Little-Endian 16-bit PCM byte array.
     */
    private fun floatToPcm16(floats: FloatArray): ByteArray {
        val byteBuf = ByteBuffer.allocate(floats.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            val clamped = f.coerceIn(-1.0f, 1.0f)
            val pcm = (clamped * 32767.0f).toInt().toShort()
            byteBuf.putShort(pcm)
        }
        return byteBuf.array()
    }

    /**
     * Release ONNX session and environment.
     */
    @Synchronized
    fun release() {
        try {
            session?.close()
            session = null
            env?.close()
            env = null
            Log.i(TAG, "Kokoro ONNX engine released")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX session", e)
        }
    }
}
