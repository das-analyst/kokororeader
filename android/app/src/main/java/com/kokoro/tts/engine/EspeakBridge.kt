package com.kokoro.tts.engine

import android.content.Context
import android.util.Log
import java.io.File

/**
 * JNI bridge to eSpeak-ng for text-to-phoneme conversion.
 * Automatically extracts compiled espeak-ng-data binary files from assets to internal storage.
 */
class EspeakBridge(private val context: Context) {

    companion object {
        private const val TAG = "EspeakBridge"
        private const val DATA_DIR = "espeak-ng-data"
        private const val DATA_VERSION = "kokoro-v1.0-data"

        init {
            try {
                System.loadLibrary("espeak_jni")
                Log.i(TAG, "Loaded libespeak_jni.so successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libespeak_jni.so", e)
            }
        }
    }

    private var initialized = false

    /**
     * Initialize eSpeak-ng engine with data path.
     */
    @Synchronized
    fun initialize(): Boolean {
        if (initialized) return true

        return try {
            val dataDir = extractDataIfNeeded()
            // eSpeak expects parent directory of espeak-ng-data
            val parentPath = dataDir.parentFile?.absolutePath ?: return false

            val result = nativeInit(parentPath)
            if (result != 0) {
                Log.e(TAG, "nativeInit failed with return code: $result")
                return false
            }

            initialized = true
            Log.i(TAG, "eSpeak-ng initialized successfully with data: $parentPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing eSpeak-ng", e)
            false
        }
    }

    /**
     * Convert English text into IPA phonemes.
     */
    @Synchronized
    fun textToPhonemes(text: String): String {
        if (!initialized && !initialize()) {
            Log.w(TAG, "eSpeak not initialized, returning empty phonemes")
            return ""
        }
        return nativeTextToPhonemes(text)
    }

    /**
     * Release eSpeak-ng resources.
     */
    @Synchronized
    fun terminate() {
        if (initialized) {
            nativeTerminate()
            initialized = false
            Log.i(TAG, "eSpeak-ng terminated")
        }
    }

    /**
     * Extract espeak-ng-data from APK assets to internal storage if not already present.
     */
    private fun extractDataIfNeeded(): File {
        val destDir = File(context.filesDir, DATA_DIR)
        val versionFile = File(destDir, ".version")

        if (versionFile.exists() && versionFile.readText().trim() == DATA_VERSION) {
            return destDir
        }

        Log.i(TAG, "Extracting espeak-ng-data from assets to ${destDir.absolutePath}")
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        copyAssetDir(DATA_DIR, destDir)
        versionFile.writeText(DATA_VERSION)
        Log.i(TAG, "espeak-ng-data extraction complete")
        return destDir
    }

    private fun copyAssetDir(assetPath: String, destDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            // It is a file: copy to destination
            try {
                context.assets.open(assetPath).use { input ->
                    File(destDir.parentFile, destDir.name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed copying asset file: $assetPath", e)
            }
        } else {
            // It is a directory: recurse
            destDir.mkdirs()
            for (child in assets) {
                copyAssetDir("$assetPath/$child", File(destDir, child))
            }
        }
    }

    // Native C++ methods declared in espeak_jni.cpp
    private external fun nativeInit(dataPath: String): Int
    private external fun nativeTextToPhonemes(text: String): String
    private external fun nativeTerminate()
}
