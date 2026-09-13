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

    fun isInitialized(): Boolean = session != null

    @Synchronized
    fun initialize(): Boolean {
        if (session != null) return true

        return try {
            val startTime = System.currentTimeMillis()
            env = OrtEnvironment.getEnvironment()

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

            var sess: OrtSession? = null

            // Strategy 1: Attempt XNNPACK acceleration on ARMv9 Cortex-X4/A715 cores
            try {
                val xnnpackOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(3)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setMemoryPatternOptimization(true)
                    addXnnpack(mapOf("intra_op_num_threads" to "3"))
                }
                sess = env!!.createSession(modelFile.absolutePath, xnnpackOptions)
                Log.i(TAG, "Successfully initialized ONNX Runtime session with XNNPACK acceleration")
            } catch (e: Throwable) {
                Log.i(TAG, "XNNPACK not available (${e.message}), using optimized CPU")
            }

            // Strategy 2: High-performance CPU configuration pinned to 3 Big/Prime cores
            if (sess == null) {
                val cpuOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(3)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setMemoryPatternOptimization(true)
                }
                sess = env!!.createSession(modelFile.absolutePath, cpuOptions)
                Log.i(TAG, "Successfully initialized ONNX Runtime session on Tensor G3 Big Cores (3 threads)")
            }

            session = sess

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
        speed: Float = 1.0f,
        gain: Float = 1.0f
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

            // Trim excessive trailing vocoder silence to a natural ~50ms decay
            val trimmedAudio = trimTrailingSilence(floatAudio)

            // Convert float32 [-1.0, 1.0] to 16-bit PCM bytes with acoustic gain
            floatToPcm16(trimmedAudio, gain)
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
     * Trims excessive trailing silence produced by Kokoro vocoder, leaving a clean
     * ~50 ms natural room-decay with a 10 ms linear fade-out to prevent audio pops.
     */
    private fun trimTrailingSilence(
        floats: FloatArray,
        threshold: Float = 0.008f,
        keepSamples: Int = 1200 // 50ms at 24kHz
    ): FloatArray {
        var lastAudible = floats.size - 1
        while (lastAudible >= 0 && kotlin.math.abs(floats[lastAudible]) < threshold) {
            lastAudible--
        }
        if (lastAudible <= 0) return floats

        val end = minOf(floats.size, lastAudible + keepSamples)
        val fadeSamples = minOf(240, end - lastAudible)
        val trimmed = floats.copyOfRange(0, end)
        for (i in 0 until fadeSamples) {
            val idx = end - fadeSamples + i
            val factor = 1.0f - (i.toFloat() / fadeSamples)
            trimmed[idx] *= factor
        }
        return trimmed
    }

    /**
     * Convert float array [-1.0, 1.0] into Little-Endian 16-bit PCM byte array with gain.
     */
    private fun floatToPcm16(floats: FloatArray, gain: Float = 1.0f): ByteArray {
        val byteBuf = ByteBuffer.allocate(floats.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            val amplified = f * gain
            val clamped = amplified.coerceIn(-1.0f, 1.0f)
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
