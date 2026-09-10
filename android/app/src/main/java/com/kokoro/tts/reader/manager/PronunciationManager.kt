package com.kokoro.tts.reader.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

/**
 * Manages user-defined pronunciation overrides (e.g., custom character names, acronyms, fantasy terms).
 * Intercepts text before phonemization so the neural model receives familiar phonemic spellings.
 */
class PronunciationManager private constructor(context: Context) {

    companion object {
        private const val TAG = "PronunciationManager"
        private const val PREFS_NAME = "kokoro_pronunciation_prefs"
        private const val KEY_RULES_JSON = "pronunciation_rules_json"

        @Volatile
        private var instance: PronunciationManager? = null

        fun getInstance(context: Context): PronunciationManager {
            return instance ?: synchronized(this) {
                instance ?: PronunciationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val rulesMap = mutableMapOf<String, String>()

    init {
        loadRules()
    }

    @Synchronized
    private fun loadRules() {
        rulesMap.clear()
        val jsonStr = prefs.getString(KEY_RULES_JSON, null) ?: return
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                rulesMap[key] = json.getString(key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pronunciation rules", e)
        }
    }

    @Synchronized
    private fun saveRules() {
        try {
            val json = JSONObject()
            for ((k, v) in rulesMap) {
                json.put(k, v)
            }
            prefs.edit().putString(KEY_RULES_JSON, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pronunciation rules", e)
        }
    }

    @Synchronized
    fun getRules(): Map<String, String> {
        return rulesMap.toMap()
    }

    @Synchronized
    fun addRule(word: String, replacement: String) {
        val trimmedWord = word.trim()
        val trimmedRep = replacement.trim()
        if (trimmedWord.isNotEmpty() && trimmedRep.isNotEmpty()) {
            rulesMap[trimmedWord] = trimmedRep
            saveRules()
        }
    }

    @Synchronized
    fun removeRule(word: String) {
        if (rulesMap.remove(word) != null) {
            saveRules()
        }
    }

    /**
     * Replaces configured terms with user-provided spoken equivalents using word boundaries.
     */
    @Synchronized
    fun applyRules(text: String): String {
        if (rulesMap.isEmpty() || text.isBlank()) return text

        var result = text
        for ((word, replacement) in rulesMap) {
            try {
                val pattern = Regex("(?i)\\b${Regex.escape(word)}\\b")
                result = result.replace(pattern, replacement)
            } catch (e: Exception) {
                Log.w(TAG, "Regex replacement failed for word: $word", e)
            }
        }
        return result
    }
}
