"""
Asset setup script for Kokoro TTS.
Downloads the Kokoro-82M int8 ONNX model (~92 MB) if not already present.
"""
import os
import urllib.request

MODEL_URL = "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.int8.onnx"
TARGET_PATHS = [
    os.path.join("models", "kokoro-v1.0.int8.onnx"),
    os.path.join("android", "app", "src", "main", "assets", "model_quantized.onnx")
]

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
            import shutil
            print(f"Copying model to: {target}")
            shutil.copy2(first_target, target)
        else:
            print(f"Model already present: {target}")

    print("Assets check completed successfully!")

if __name__ == "__main__":
    main()
