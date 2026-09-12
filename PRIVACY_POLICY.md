# Privacy Policy for Kokoro Reader

**Effective Date:** September 12, 2026  
**Developer:** Pinto Beans Labs  
**Application:** Kokoro Reader  
**Package Name:** `com.pintobeanslabs.kokororeader`  

Pinto Beans Labs built **Kokoro Reader** as a privacy-first, on-device application. This page informs users regarding our policies regarding the collection, use, and disclosure of personal information when using Kokoro Reader.

---

### 1. Zero Data Collection & 100% On-Device Processing

Kokoro Reader is designed from the ground up to operate completely offline:

* **No Cloud Transmission:** All text-to-speech neural synthesis (via Kokoro-82M and ONNX Runtime) is executed 100% locally on your device's processor (CPU/NPU). Your documents, EPUBs, PDFs, TXT files, and spoken audio are **never** transmitted across the internet.
* **No Personal Information Collected:** We do not collect, store, transmit, or sell names, email addresses, device identifiers, IP addresses, location data, or any other personally identifiable information (PII).
* **No Analytics or Telemetry:** Kokoro Reader contains zero third-party analytics SDKs, zero advertising trackers, and zero diagnostic telemetry.

---

### 2. Permissions & How They Are Used

Kokoro Reader requests only the minimal permissions required to function as an on-device speech engine and audiobook reader:

* `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Used exclusively to keep audiobook playback active with lock-screen notification media controls when the screen is turned off or when switching apps.
* `android.permission.WAKE_LOCK`: Used to prevent the device CPU from sleeping mid-sentence during active audiobook playback.
* `android.permission.POST_NOTIFICATIONS`: Required on Android 13+ to display ongoing media playback controls (play, pause, next/prev sentence) in the notification shade.

---

### 3. Third-Party Services & Links

Kokoro Reader does not integrate with third-party tracking, advertising, or cloud AI services. 

When you use Kokoro Reader as an Android system-wide Text-to-Speech engine (`TextToSpeechService`), external apps on your device (such as Moon+ Reader, ReadEra, or Google Play Books) may pass text to Kokoro Reader for local synthesis. This text is processed immediately in memory and is never saved, uploaded, or shared.

---

### 4. Children's Privacy

Kokoro Reader does not address anyone under the age of 13 specifically, nor do we collect any personal information from children or any other users.

---

### 5. Changes to This Privacy Policy

We may update our Privacy Policy from time to time. Since we collect no user contact information, any updates will be posted directly to this page or the project repository.

---

### 6. Contact Us

If you have any questions or suggestions regarding our Privacy Policy, please contact us at:  
**Pinto Beans Labs**  
Email / GitHub Issues: via the official project repository
