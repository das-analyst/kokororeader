"""
Asset setup script for Kokoro TTS.
Downloads the Kokoro-82M int8 ONNX model (~92 MB) and voices package if not present,
and unpacks curated speaker profiles into assets.
"""
import io
import os
import shutil
import urllib.request
import zipfile
import numpy as np

MODEL_URL = "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.int8.onnx"
VOICES_URL = "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin"

TARGET_PATHS = [
    os.path.join("models", "kokoro-v1.0.int8.onnx"),
    os.path.join("android", "app", "src", "main", "assets", "model_quantized.onnx")
]

VOICES_BIN_PATH = os.path.join("models", "voices-v1.0.bin")

CURATED_VOICES = [
    # Soft, Harmonious & Bass Male Voices
    "am_onyx",      # Deep soft bass, calm
    "am_michael",   # Soft, harmonious, natural storyteller
    "am_echo",      # Calm, mellow, smooth
    "bm_george",    # British deep bass classic narrator
    "bm_fable",     # British warm expressive storyteller
    "bm_daniel",    # British dignified resonant
    "am_eric",      # Conversational, clear
    "am_liam",      # Smooth, young
    "am_adam",      # Resonant, dynamic
    # Soft & Expressive Female Voices
    "af_heart",     # Soft, warm, natural (default favorite)
    "af_bella",     # Gentle, melodious
    "af_sarah",     # Calm, pleasant audiobook narrator
    "af_nicole",    # Whisper soft, intimate
    "af_sky",       # Light, airy, gentle
    "bf_emma",      # British warm, refined
    "bf_isabella",  # British expressive, melodic
    "bf_alice",     # British classic, soft
]

VOICE_DEST_DIRS = [
    os.path.join("models", "single_voices"),
    os.path.join("android", "app", "src", "main", "assets", "voices")
]

def setup_voices():
    if not os.path.exists(VOICES_BIN_PATH):
        print(f"Downloading voices package from:\n  {VOICES_URL}")
        urllib.request.urlretrieve(VOICES_URL, VOICES_BIN_PATH)
        print(f"Saved: {VOICES_BIN_PATH} ({os.path.getsize(VOICES_BIN_PATH):,} bytes)")

    for d in VOICE_DEST_DIRS:
        os.makedirs(d, exist_ok=True)

    with zipfile.ZipFile(VOICES_BIN_PATH) as z:
        for voice in CURATED_VOICES:
            npy_name = f"{voice}.npy"
            if npy_name in z.namelist():
                with z.open(npy_name) as f:
                    arr = np.load(io.BytesIO(f.read()))
                    raw_bytes = arr.astype(np.float32).tobytes()
                    for d in VOICE_DEST_DIRS:
                        out_path = os.path.join(d, f"{voice}.bin")
                        if not os.path.exists(out_path):
                            with open(out_path, "wb") as out_f:
                                out_f.write(raw_bytes)
                            print(f"Extracted voice {voice} -> {out_path}")
            else:
                print(f"Warning: {npy_name} not found in {VOICES_BIN_PATH}")

def main():
    first_target = TARGET_PATHS[0]
    os.makedirs(os.path.dirname(first_target), exist_ok=True)
    
    if not os.path.exists(first_target):
        print(f"Downloading Kokoro int8 ONNX model from:\n  {MODEL_URL}")
        urllib.request.urlretrieve(MODEL_URL, first_target)
        print(f"Saved: {first_target} ({os.path.getsize(first_target):,} bytes)")
    else:
        print(f"Model already present: {first_target}")

    for target in TARGET_PATHS[1:]:
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if not os.path.exists(target):
            print(f"Copying model to: {target}")
            shutil.copy2(first_target, target)
        else:
            print(f"Model already present: {target}")

    setup_voices()
    print("Assets check completed successfully!")

if __name__ == "__main__":
    main()

