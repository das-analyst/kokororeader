import os
import time
import json
import numpy as np
import onnxruntime as ort
import soundfile as sf
from kokoro_onnx.tokenizer import Tokenizer

class EmotionExperimentRunner:
    def __init__(self, model_path: str, vocab_path: str, voice_dir: str):
        print(f"Loading ONNX Model: {model_path}...")
        self.session = ort.InferenceSession(
            model_path,
            providers=["CPUExecutionProvider"]
        )
        
        with open(vocab_path, "r", encoding="utf-8") as f:
            self.vocab = json.load(f)
            
        self.tokenizer = Tokenizer(vocab=self.vocab)
        
        input_names = [inp.name for inp in self.session.get_inputs()]
        self.tokens_key = "input_ids" if "input_ids" in input_names else "tokens"
        
        # Load voice library
        self.voices = {}
        for voice_name in ["af_heart", "af_bella", "af_nicole", "af_sarah", "am_adam", "am_onyx", "am_michael"]:
            path = os.path.join(voice_dir, f"{voice_name}.bin")
            if os.path.exists(path):
                raw = np.fromfile(path, dtype=np.float32).reshape(510, 1, 256)
                self.voices[voice_name] = raw
                print(f"Loaded voice: {voice_name}")

    def synthesize_chunk(
        self,
        text: str,
        style_vector: np.ndarray,
        speed: float = 1.0,
        pause_duration_sec: float = 0.20,
        gain_db: float = 0.0,
        lang: str = "en-us"
    ) -> np.ndarray:
        phonemes = self.tokenizer.phonemize(text, lang=lang)
        tokens = [self.vocab[c] for c in phonemes if c in self.vocab]
        if not tokens:
            return np.zeros(int(pause_duration_sec * 24000), dtype=np.float32)

        token_input = np.array([[0, *tokens, 0]], dtype=np.int64)
        speed_input = np.array([speed], dtype=np.float32)

        outputs = self.session.run(None, {
            self.tokens_key: token_input,
            "style": style_vector,
            "speed": speed_input
        })

        audio = np.asarray(outputs[0]).ravel()

        # Apply gain adjustment if specified (e.g. -3dB for whispers, +1.5dB for shouts)
        if gain_db != 0.0:
            factor = 10.0 ** (gain_db / 20.0)
            audio = audio * factor

        # Append contextual pause
        if pause_duration_sec > 0:
            pause_samples = int(pause_duration_sec * 24000)
            silence = np.zeros(pause_samples, dtype=np.float32)
            audio = np.concatenate([audio, silence])

        return audio

    def get_style_vector(self, token_count: int, primary_voice: str, secondary_voice: str = None, secondary_weight: float = 0.0) -> np.ndarray:
        idx = min(max(token_count, 1), 510) - 1
        v1 = self.voices[primary_voice][idx]
        if secondary_voice and secondary_voice in self.voices and secondary_weight > 0.0:
            v2 = self.voices[secondary_voice][idx]
            return (1.0 - secondary_weight) * v1 + secondary_weight * v2
        return v1

def run():
    model_path = "models/kokoro-v1.0.int8.onnx"
    vocab_path = "models/vocab.json"
    voice_dir = "models/single_voices"
    
    runner = EmotionExperimentRunner(model_path, vocab_path, voice_dir)
    
    # Dramatic test scene with shifting emotional beats
    script = [
        {
            "id": 1,
            "text": "The old lighthouse was dark, darker than it had ever been on a stormy night.",
            "emotion": "Atmospheric / Ominous",
            "speed": 0.94,
            "pause": 0.50,
            "blend_primary": "af_heart",
            "blend_secondary": "af_sarah",
            "blend_weight": 0.35, # Sarah adds a solemn, serious tone
            "gain_db": 0.0
        },
        {
            "id": 2,
            "text": "“Thomas!” she called, her voice trembling against the freezing wind. “Thomas, answer me!”",
            "emotion": "Desperate / Panicked Shouting",
            "speed": 1.14, # Spoken faster, urgent
            "pause": 0.45,
            "blend_primary": "af_heart",
            "blend_secondary": "af_bella",
            "blend_weight": 0.50, # Bella adds high pitch excitement and dramatic energy
            "gain_db": 1.5 # Boost vocal projection
        },
        {
            "id": 3,
            "text": "There was no answer, only the relentless crashing of the waves against the jagged rocks below.",
            "emotion": "Grim Realization / Cadence",
            "speed": 0.92, # Slower, heavy delivery
            "pause": 0.70, # Extended pregnant silence
            "blend_primary": "af_heart",
            "blend_secondary": "af_sarah",
            "blend_weight": 0.40,
            "gain_db": -0.5
        },
        {
            "id": 4,
            "text": "Then, from the shadows of the spiral staircase, she heard footsteps slowly descending.",
            "emotion": "Creeping Suspense / Dread",
            "speed": 0.96,
            "pause": 0.60,
            "blend_primary": "af_heart",
            "blend_secondary": "af_nicole",
            "blend_weight": 0.30,
            "gain_db": -1.0
        },
        {
            "id": 5,
            "text": "“Stay back,” she whispered into the suffocating darkness. “Whoever you are... stay back.”",
            "emotion": "Intimate / Terrified Whisper",
            "speed": 0.88, # Elongated, cautious whisper
            "pause": 0.65,
            "blend_primary": "af_heart",
            "blend_secondary": "af_nicole",
            "blend_weight": 0.60, # Nicole is soft, gentle, and breathy
            "gain_db": -3.0 # Softened volume for whisper
        },
        {
            "id": 6,
            "text": "A sudden gust of wind blew out her lantern, leaving her completely alone in the blackness.",
            "emotion": "Sudden Climax & Finality",
            "speed": 1.02,
            "pause": 1.00, # Long chapter-ending silence
            "blend_primary": "af_heart",
            "blend_secondary": "af_sarah",
            "blend_weight": 0.30,
            "gain_db": 0.0
        }
    ]

    print("\n" + "="*85)
    print(" 1. GENERATING BASELINE AUDIO (Flat 1.0x Speed, Fixed 0.20s Pause, Static Voice)")
    print("="*85)
    baseline_chunks = []
    t0 = time.perf_counter()
    for item in script:
        tokens = [runner.vocab[c] for c in runner.tokenizer.phonemize(item["text"]) if c in runner.vocab]
        style = runner.get_style_vector(len(tokens), "af_heart")
        audio = runner.synthesize_chunk(
            text=item["text"],
            style_vector=style,
            speed=1.0,
            pause_duration_sec=0.20,
            gain_db=0.0
        )
        baseline_chunks.append(audio)
        print(f"  Sentence {item['id']}: Static af_heart | speed=1.0 | pause=0.20s")

    baseline_audio = np.concatenate(baseline_chunks)
    baseline_file = "experiment_baseline_neutral.wav"
    sf.write(baseline_file, np.clip(baseline_audio, -1.0, 1.0), 24000)
    t_baseline = time.perf_counter() - t0
    print(f"--> Saved Baseline Audio: {baseline_file} ({len(baseline_audio)/24000:.2f}s audio generated in {t_baseline:.2f}s)\n")

    print("="*85)
    print(" 2. GENERATING EMOTION & PROSODY SHAPED AUDIO (Dynamic Blending, Tempo, Pauses, Gain)")
    print("="*85)
    emotional_chunks = []
    t1 = time.perf_counter()
    for item in script:
        tokens = [runner.vocab[c] for c in runner.tokenizer.phonemize(item["text"]) if c in runner.vocab]
        style = runner.get_style_vector(
            token_count=len(tokens),
            primary_voice=item["blend_primary"],
            secondary_voice=item["blend_secondary"],
            secondary_weight=item["blend_weight"]
        )
        audio = runner.synthesize_chunk(
            text=item["text"],
            style_vector=style,
            speed=item["speed"],
            pause_duration_sec=item["pause"],
            gain_db=item["gain_db"]
        )
        emotional_chunks.append(audio)
        print(f"  Sentence {item['id']} [{item['emotion']}]:")
        print(f"    Blend: {item['blend_primary']} + {item['blend_weight']*100:.0f}% {item['blend_secondary']}")
        print(f"    Tempo: {item['speed']}x | Pause: {item['pause']:.2f}s | Gain: {item['gain_db']:+.1f}dB")

    emotional_audio = np.concatenate(emotional_chunks)
    emotional_file = "experiment_emotional_prosody.wav"
    sf.write(emotional_file, np.clip(emotional_audio, -1.0, 1.0), 24000)
    t_emotional = time.perf_counter() - t1
    print(f"--> Saved Emotional Audio: {emotional_file} ({len(emotional_audio)/24000:.2f}s audio generated in {t_emotional:.2f}s)\n")

    print("="*85)
    print(" EXPERIMENT COMPLETE")
    print("="*85)
    print(f"File 1: {baseline_file} (Duration: {len(baseline_audio)/24000:.2f}s)")
    print(f"File 2: {emotional_file} (Duration: {len(emotional_audio)/24000:.2f}s)")
    print("="*85)

if __name__ == "__main__":
    run()
