# Keep ONNX Runtime JNI classes
-keep class ai.onnxruntime.** { *; }

# Keep our native methods and TTS engine
-keep class com.kokoro.tts.engine.EspeakBridge { *; }
-keepclassmembers class com.kokoro.tts.engine.EspeakBridge {
    native <methods>;
}
-keep class com.kokoro.tts.KokoroTtsService { *; }
