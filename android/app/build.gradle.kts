plugins {
    id("com.android.application")
}

android {
    namespace = "com.kokoro.tts"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.kokoro.tts"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += setOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ONNX Runtime Android (1.29.0+ supports 16 KB page size alignment)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")

    // Lightweight EPUB HTML/XHTML Parser
    implementation("org.jsoup:jsoup:1.18.1")

    // UI Components for Ebook Reader
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // MediaSession & Foreground Media Playback for Lock Screen / Bluetooth
    implementation("androidx.media:media:1.7.0")

    // PDF Text Extraction (Pure Java, 16 KB page-size safe)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
}

