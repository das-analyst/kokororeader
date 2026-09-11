#include <jni.h>
#include <string>
#include <cstring>
#include <android/log.h>
#include <espeak-ng/speak_lib.h>

#define TAG "EspeakJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static bool g_initialized = false;

extern "C" {

JNIEXPORT jint JNICALL
Java_com_kokoro_tts_engine_EspeakBridge_nativeInit(
        JNIEnv *env, jobject /* this */, jstring dataPath) {
    if (g_initialized) {
        LOGI("eSpeak already initialized, skipping");
        return 0;
    }

    const char *path = env->GetStringUTFChars(dataPath, nullptr);
    if (!path) {
        LOGE("Failed to get data path string");
        return -1;
    }

    LOGI("Initializing eSpeak with data path: %s", path);

    // AUDIO_OUTPUT_SYNCHRONOUS = no audio output, text-to-phoneme processing only
    int sampleRate = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, path, 0);

    env->ReleaseStringUTFChars(dataPath, path);

    if (sampleRate == -1) {
        LOGE("espeak_Initialize failed");
        return -1;
    }

    LOGI("eSpeak initialized, sample rate: %d", sampleRate);

    // Set voice to American English
    espeak_ERROR err = espeak_SetVoiceByName("en-us");
    if (err != EE_OK) {
        LOGE("espeak_SetVoiceByName(en-us) failed: %d", err);
        return -2;
    }

    g_initialized = true;
    LOGI("eSpeak ready (en-us)");
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_kokoro_tts_engine_EspeakBridge_nativeTextToPhonemes(
        JNIEnv *env, jobject /* this */, jstring text) {
    if (!g_initialized) {
        LOGE("eSpeak not initialized");
        return env->NewStringUTF("");
    }

    const char *inputText = env->GetStringUTFChars(text, nullptr);
    if (!inputText) {
        return env->NewStringUTF("");
    }

    // phonememode bits:
    //   bit 1 (0x02): IPA output (UTF-8)
    //   bit 7 (0x80): use bits 8-23 as tie character for multi-letter phonemes
    //   bits 8-23: tie character ('^' = 0x5E)
    // Result: 0x02 | 0x80 | (0x5E << 8) = 0x5E82
    int phonememode = 0x02 | 0x80 | ('^' << 8);

    std::string input(inputText);
    env->ReleaseStringUTFChars(text, inputText);

    // Phonemize a text fragment via eSpeak's clause-by-clause processing
    auto phonemizeFragment = [phonememode](const char *frag) -> std::string {
        std::string fragResult;
        const void *ptr = frag;
        while (ptr != nullptr) {
            const char *ph = espeak_TextToPhonemes(&ptr, espeakCHARS_UTF8, phonememode);
            if (ph && *ph) {
                if (!fragResult.empty()) fragResult += ' ';
                fragResult += ph;
            }
        }
        return fragResult;
    };

    // Helper to detect punctuation delimiters and return byte length (0 if none, 1 for ASCII, 3 for UTF-8)
    auto matchDelimiter = [](const std::string &str, size_t pos, std::string &outDelim) -> int {
        if (pos >= str.size()) return 0;
        unsigned char c = static_cast<unsigned char>(str[pos]);

        // Check 3-byte UTF-8 sequences (0xE2 0x80 ...)
        if (c == 0xE2 && pos + 2 < str.size() && static_cast<unsigned char>(str[pos + 1]) == 0x80) {
            unsigned char c3 = static_cast<unsigned char>(str[pos + 2]);
            if (c3 == 0x94) { // U+2014 Em-dash '—'
                outDelim = "—";
                return 3;
            }
            if (c3 == 0x93) { // U+2013 En-dash '–'
                outDelim = "—";
                return 3;
            }
            if (c3 == 0xA6) { // U+2026 Ellipsis '…'
                outDelim = "…";
                return 3;
            }
            if (c3 == 0x9C || c3 == 0x9D) { // U+201C / U+201D Curly quotes “ ”
                outDelim = "\"";
                return 3;
            }
        }

        // ASCII delimiters: ( ) , ; : ! ? "
        if (c == '(' || c == ')' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == '"') {
            outDelim = std::string(1, static_cast<char>(c));
            return 1;
        }

        // Period '.' (guard against decimal points e.g. 3.14)
        if (c == '.') {
            if (pos + 1 < str.size() && isdigit(static_cast<unsigned char>(str[pos + 1]))) {
                return 0; // Number decimal, keep with digits
            }
            outDelim = ".";
            return 1;
        }

        return 0;
    };

    // Split at clause/phrase delimiters (em-dash, parentheses, comma, semicolon, colon, ellipsis, sentence enders).
    // Phonemize each text fragment separately and rejoin with the punctuation tokens,
    // so the Kokoro model receives em-dashes (token 9), parentheses (tokens 12 & 13),
    // and punctuation tokens to steer prosody, pauses, and cadence.
    std::string result;
    size_t start = 0;
    size_t i = 0;

    while (i < input.size()) {
        std::string delim;
        int delimLen = matchDelimiter(input, i, delim);
        if (delimLen > 0) {
            // Phonemize the fragment before this delimiter
            if (i > start) {
                size_t fragStart = start;
                while (fragStart < i && isspace(static_cast<unsigned char>(input[fragStart]))) fragStart++;
                size_t fragEnd = i;
                while (fragEnd > fragStart && isspace(static_cast<unsigned char>(input[fragEnd - 1]))) fragEnd--;

                if (fragEnd > fragStart) {
                    std::string frag = input.substr(fragStart, fragEnd - fragStart);
                    std::string ph = phonemizeFragment(frag.c_str());
                    if (!ph.empty()) {
                        if (!result.empty() && result.back() != '(' && result.back() != ' ') {
                            result += ' ';
                        }
                        result += ph;
                    }
                }
            }

            // Append delimiter
            if (delim == "(") {
                if (!result.empty() && result.back() != ' ') {
                    result += ' ';
                }
                result += "(";
            } else if (delim == ")") {
                result += ")";
            } else if (delim == "—") {
                if (!result.empty() && result.back() != ' ') {
                    result += ' ';
                }
                result += "—";
            } else {
                result += delim;
            }

            i += delimLen;
            start = i;
        } else {
            i++;
        }
    }

    // Phonemize any remaining text after the last delimiter
    if (start < input.size()) {
        size_t fragStart = start;
        while (fragStart < input.size() && isspace(static_cast<unsigned char>(input[fragStart]))) fragStart++;
        size_t fragEnd = input.size();
        while (fragEnd > fragStart && isspace(static_cast<unsigned char>(input[fragEnd - 1]))) fragEnd--;

        if (fragEnd > fragStart) {
            std::string frag = input.substr(fragStart, fragEnd - fragStart);
            std::string ph = phonemizeFragment(frag.c_str());
            if (!ph.empty()) {
                if (!result.empty() && result.back() != '(' && result.back() != ' ') {
                    result += ' ';
                }
                result += ph;
            }
        }
    }

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_kokoro_tts_engine_EspeakBridge_nativeTerminate(
        JNIEnv * /* env */, jobject /* this */) {
    if (g_initialized) {
        espeak_Terminate();
        g_initialized = false;
        LOGI("eSpeak terminated");
    }
}

} // extern "C"
