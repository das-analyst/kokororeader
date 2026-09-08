# Kokoro Android System TTS Engine

An on-device, neural Text-to-Speech (TTS) engine for Android powered by **Kokoro-82M** and **Microsoft ONNX Runtime Mobile**.

This project registers as a system-wide speech synthesizer via `android.speech.tts.TextToSpeechService`. Any Android ebook reader app (such as **Moon+ Reader**, **ReadEra**, **Evie**, **Voice Dream**, or **Google Play Books**) can route book text through this service to read ebooks aloud with near-human vocal naturalness completely offline.

---

## Architecture

```
[ Ebook App (e.g. Moon+ Reader) ]
            │  (Sends raw book text via Android OS)
            ▼
[ android.speech.tts.TextToSpeechService ]
            │
            ├─► 1. TextSplitter (Abbreviation-safe sentence chunker)
            │      └─ Prevents premature splitting on "Mr.", "Mrs.", "Dr.", "etc."
            │
            ├─► 2. PhonemeConverter (Pre-normalization + eSpeak-ng JNI)
            │      └─ Converts English words into IPA phoneme symbols
            │
            ├─► 3. Tokenizer (IPA -> Token IDs)
            │      └─ Maps phonemes to token integers with [0, *ids, 0] padding
            │
            ├─► 4. VoiceStyleLoader (Slices 1x256 style vector)
            │      └─ Extracts style embedding from raw 522 KB binary voice file
            │
            ├─► 5. KokoroEngine (Microsoft ONNX Runtime Android)
            │      └─ Runs Kokoro-82M int8 neural inference across CPU/NPU cores
            │
            ├─► 6. Float32 to Little-Endian PCM-16 Converter
            │      └─ Clamps and converts 24,000 Hz audio to 16-bit PCM bytes
            │
            ▼
[ SynthesisCallback.audioAvailable() ] ──► (Streams directly to phone speaker/headphones)
```

---

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                  # Registers TTS Service & Intent Filter
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt                   # Fetches eSpeak-ng & builds libespeak_jni.so
│   │   │   └── espeak_jni.cpp                   # JNI text-to-phoneme bridge
│   │   ├── java/com/kokoro/tts/
│   │   │   ├── KokoroTtsService.kt              # Android TextToSpeechService implementation
│   │   │   ├── MainActivity.kt                  # Voice preview & settings activity
│   │   │   └── engine/
│   │   │       ├── KokoroEngine.kt              # ONNX Runtime model inference wrapper
│   │   │       ├── Tokenizer.kt                 # IPA phoneme to token ID mapper
│   │   │       ├── VoiceStyleLoader.kt          # Raw .bin voice embedding loader
│   │   │       ├── TextSplitter.kt              # Abbreviation-safe sentence segmenter
│   │   │       ├── PhonemeConverter.kt          # Normalizer & IPA post-processor
│   │   │       └── EspeakBridge.kt              # Native eSpeak JNI lifecycle & data manager
│   │   └── assets/
│   │       ├── model_quantized.onnx             # Kokoro-82M int8 model (~92 MB)
│   │       ├── vocab.json                       # 114-entry IPA token dictionary
│   │       ├── voices/                          # 522 KB raw float32 voice files
│   │       │   ├── af_heart.bin                 # Default female voice
│   │       │   └── am_adam.bin                  # Default male voice
│   │       └── espeak-ng-data/                  # Minimal English pronunciation dictionaries (~820 KB)
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```

---

## Prerequisites & Building

1. **Android Studio** (Ladybug or newer)
2. **Android SDK 35** (Minimum SDK 26 / Android 8.0+)
3. **Android NDK** (version 25+ or 27+) and **CMake 3.22.1+** (installable via Android Studio SDK Tools)
4. Open the `android/` directory in Android Studio.
5. Build and install the debug APK:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Enabling in Android Settings

1. Open Android **Settings** $\rightarrow$ **Accessibility** (or **System / Languages**) $\rightarrow$ **Text-to-Speech output**.
2. Under **Preferred engine**, choose **"Kokoro Natural AI Voice"**.
3. Tap the gear icon or open the **Kokoro TTS** app to test a voice sample and switch between available voices.

---

## Testing with an Ebook Reader

1. Open your preferred reading app (e.g. **Moon+ Reader**, **ReadEra**, or **Evie**).
2. Open any EPUB or PDF book.
3. Tap **Read Aloud / Text-to-Speech**.
4. The reader will automatically stream book text through your local Kokoro neural voice.
