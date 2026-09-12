# Google Play Store Listing & Submission Guide: Kokoro Reader

**Developer Name:** Pinto Beans Labs  
**Package Name (`applicationId`):** `com.pintobeanslabs.kokororeader`  
**Category:** Books & Reference (or Tools)  
**Content Rating:** Everyone / PEGI 3  

---

## 1. Store Listing Copy

### App Title (Max 30 characters)
```text
Kokoro Reader: AI Audiobooks
```
*(Exact count: 28 characters)*

### Short Description (Max 80 characters)
```text
100% offline on-device neural AI voice & ebook reader. Zero ads or subscriptions.
```
*(Exact count: 80 characters)*

### Full Description (Max 4,000 characters)
```text
Transform any ebook or document into a studio-quality audiobook—powered by on-device neural AI. 

Kokoro Reader by Pinto Beans Labs brings state-of-the-art open-weight speech synthesis directly to your phone. Powered by the breakthrough Kokoro-82M neural engine and Microsoft ONNX Runtime Mobile, Kokoro Reader produces natural cadence, subtle breath pauses, and warm vocal depth that rivals top commercial cloud services—completely offline.

Why pay $15–$30/month for cloud readers that upload your personal documents to corporate servers? With Kokoro Reader, zero bytes ever leave your device. 

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🌟 WHY KOKORO READER STANDS OUT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

◆ 100% ON-DEVICE & OFFLINE
Listen seamlessly on airplanes, subways, daily commutes, or remote travels. No internet connection, no mobile data usage, and no streaming buffering.

◆ SYSTEM-WIDE ANDROID TTS ENGINE
Kokoro Reader registers as an official Android TextToSpeechService. You can use it as your phone's default voice engine to upgrade third-party apps like Moon+ Reader, ReadEra, Evie, Voice Dream, or Google Play Books with lifelike AI narration.

◆ BUILT-IN MODERN EBOOK READER
Import EPUB, PDF, and TXT files directly. Enjoy sentence-by-sentence karaoke highlighting, auto-scrolling, tap-to-seek playback, and custom chapter navigation.

◆ 17 CURATED NEURAL VOICES
Choose from a rich palette of expressive voices, including warm narrators and soothing soft-bass male and female profiles tailored specifically for fatigue-free long-form audiobook listening.

◆ INTELLIGENT EBOOK NORMALIZATION
Never stumble over clunky computer speech. Kokoro Reader features advanced text normalizers that effortlessly handle abbreviations (Dr., Mrs., etc.), Roman numerals (Chapter XIV → Chapter Fourteen), currency ($49.99), metrics, and cleans messy OCR artifacts.

◆ BACKGROUND AUDIO & LOCK SCREEN CONTROLS
Full MediaSession support. Play, pause, jump sentences, or set a sleep timer right from your lock screen, notification shade, or Bluetooth headset.

◆ ABSOLUTE PRIVACY & ZERO ADS
No ads. No paywalls. No account creation. No tracking. What you read is strictly your business.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SUPPORTED FORMATS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• EPUB (Standard electronic publications & fiction)
• PDF (Articles, papers, and formatted documents)
• TXT (Plain text notes & manuscripts)
• Any app supporting Android System Text-to-Speech

Experience the future of offline listening. Download Kokoro Reader today and rediscover your reading library.
```

---

## 2. Google Play Console Declarations

### Data Safety Form Answers
When Google Play Console asks you about Data Safety:
1. **Does your app collect or share any of the required user data types?**  
   ➡️ Select **"No"**.
2. **Is all of the user data collected by your app encrypted in transit?**  
   ➡️ Select **"Not applicable"** (no data is collected or transmitted).
3. **Do you provide a way for users to request that their data be deleted?**  
   ➡️ Select **"Not applicable"** (no personal data or user accounts exist).

### Foreground Service (FGS) Media Playback Declaration
Because the app uses `FOREGROUND_SERVICE_MEDIA_PLAYBACK`:
* **Core Purpose:** Audiobook and Text-to-Speech audio playback.
* **User Value:** Allows users to listen to books continuously when their screen is locked, when walking with headphones, or while multitasking in other apps.
* **Reviewer Instructions:** Open any book or sample text in Kokoro Reader, tap **Play**, lock the screen or press Home, and observe that playback continues smoothly with lock-screen notification media controls (play/pause/skip).

---

## 3. Generating Your Release Keystore (One-Time Setup)

To build a release Android App Bundle (`.aab`) for Google Play, you need a private upload keystore.

Run this command in PowerShell or Android Studio Terminal:
```powershell
keytool -genkey -v -keystore android/upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
*(Enter your name, organization, and a secure password when prompted).*

Next, copy `android/keystore.properties.example` to `android/keystore.properties`:
```properties
storeFile=upload-keystore.jks
storePassword=YOUR_PASSWORD
keyAlias=upload
keyPassword=YOUR_PASSWORD
```

Then build your production App Bundle:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd android
.\gradlew bundleRelease
```
Your release bundle will be generated at:
`android/app/build/outputs/bundle/release/app-release.aab`
