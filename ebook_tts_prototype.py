import json
import os
import re
import time
import numpy as np
import onnxruntime as ort
import soundfile as sf

class EbookTTSPipeline:
    def __init__(self, model_path: str, vocab_path: str, voice_bin_path: str):
        print(f"Loading ONNX Model: {model_path}...")
        self.session = ort.InferenceSession(
            model_path,
            providers=["CPUExecutionProvider"]
        )
        
        print(f"Loading Vocab: {vocab_path}...")
        with open(vocab_path, "r", encoding="utf-8") as f:
            self.vocab = json.load(f)
            
        print(f"Loading Voice Embedding: {voice_bin_path}...")
        # Voice format: 510 vectors, each 1x256 float32 (522,240 bytes)
        raw_bytes = np.fromfile(voice_bin_path, dtype=np.float32)
        self.voice_styles = raw_bytes.reshape(510, 1, 256)
        print(f"Voice embedding loaded. Shape: {self.voice_styles.shape}")
        
        # Check input name: 'tokens' or 'input_ids'
        input_names = [inp.name for inp in self.session.get_inputs()]
        self.tokens_key = "input_ids" if "input_ids" in input_names else "tokens"
        print(f"Model token input key: '{self.tokens_key}'")
        
        # Lazy import phonemizer
        from kokoro_onnx.tokenizer import Tokenizer
        self.tokenizer = Tokenizer(vocab=self.vocab)

    def split_sentences(self, text: str) -> list[str]:
        """Split text into sentences while preserving sentence punctuation and avoiding abbreviation splits."""
        cleaned = " ".join(text.split())
        # Protect common abbreviations: Mr., Mrs., Ms., Dr., Prof., etc.
        abbrevs = ["Mr", "Mrs", "Ms", "Dr", "Prof", "Sr", "Jr", "vs", "etc", "i.e", "e.g"]
        placeholder_map = {}
        for idx, abbrev in enumerate(abbrevs):
            token = f"__ABBREV_{idx}__"
            pattern = rf'\b{re.escape(abbrev)}\.'
            placeholder_map[token] = f"{abbrev}."
            cleaned = re.sub(pattern, token, cleaned)

        # Split on sentence boundaries: . ! ? followed by space
        raw_sentences = [s.strip() for s in re.split(r'(?<=[.!?])\s+', cleaned) if s.strip()]
        
        # Restore abbreviations
        final_sentences = []
        for s in raw_sentences:
            for token, orig in placeholder_map.items():
                s = s.replace(token, orig)
            final_sentences.append(s)
        return final_sentences

    def text_to_tokens(self, text: str, lang: str = "en-us") -> tuple[str, list[int]]:
        """Convert raw text to phonemes, then map to token IDs."""
        phonemes = self.tokenizer.phonemize(text, lang=lang)
        tokens = [self.vocab[c] for c in phonemes if c in self.vocab]
        return phonemes, tokens

    def synthesize_sentence(self, text: str, speed: float = 1.0, lang: str = "en-us") -> dict:
        """Synthesize a single sentence and return audio + telemetry."""
        t_start = time.perf_counter()
        
        # 1. Phonemize & Tokenize
        phonemes, tokens = self.text_to_tokens(text, lang=lang)
        if not tokens:
            return None
        
        # 2. Prepare ONNX inputs
        token_input = np.array([[0, *tokens, 0]], dtype=np.int64)
        style_idx = min(len(tokens), 510) - 1
        style_input = self.voice_styles[style_idx]
        speed_input = np.array([speed], dtype=np.float32)
        
        t_infer_start = time.perf_counter()
        outputs = self.session.run(None, {
            self.tokens_key: token_input,
            "style": style_input,
            "speed": speed_input
        })
        t_infer_end = time.perf_counter()
        
        raw_audio = np.asarray(outputs[0]).ravel()
        audio_duration = len(raw_audio) / 24000.0
        
        # 3. Add small natural inter-sentence pause (e.g., 0.20s silence)
        pause_samples = int(0.20 * 24000)
        silence = np.zeros(pause_samples, dtype=np.float32)
        audio_with_pause = np.concatenate([raw_audio, silence])
        
        # 4. Convert to PCM-16 bytes (as Android SynthesisCallback expects)
        clamped = np.clip(audio_with_pause, -1.0, 1.0)
        pcm16_samples = (clamped * 32767.0).astype(np.int16)
        pcm_bytes = pcm16_samples.tobytes()
        
        total_time = t_infer_end - t_start
        infer_time = t_infer_end - t_infer_start
        rtf = infer_time / audio_duration if audio_duration > 0 else 0.0
        
        return {
            "text": text,
            "phonemes": phonemes,
            "token_count": len(tokens),
            "infer_time_sec": infer_time,
            "total_time_sec": total_time,
            "audio_duration_sec": audio_duration,
            "rtf": rtf,
            "audio_float": audio_with_pause,
            "pcm16_bytes": pcm_bytes
        }

def run_prototype():
    model_path = "models/kokoro-v1.0.int8.onnx"
    vocab_path = "models/vocab.json"
    voice_path = "models/single_voices/af_heart.bin"
    
    pipeline = EbookTTSPipeline(model_path, vocab_path, voice_path)
    
    # Sample excerpt from an ebook
    sample_ebook_text = (
        "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife. "
        "However little known the feelings or views of such a man may be on his first entering a neighbourhood, "
        "this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters. "
        "\"My dear Mr. Bennet,\" said his lady to him one day, \"have you heard that Netherfield Park is let at last?\""
    )
    
    print("\n" + "="*80)
    print(" EBOOK TTS STREAMING PROTOTYPE BENCHMARK")
    print("="*80)
    print("Book Passage:")
    print(sample_ebook_text)
    print("-" * 80)
    
    sentences = pipeline.split_sentences(sample_ebook_text)
    print(f"Segmented into {len(sentences)} sentences for sub-second streaming.\n")
    
    all_audio_chunks = []
    total_pcm_bytes = 0
    total_audio_duration = 0.0
    cumulative_playback_time = 0.0
    cumulative_gen_time = 0.0
    time_to_first_audio = 0.0
    
    for idx, sentence in enumerate(sentences, 1):
        result = pipeline.synthesize_sentence(sentence, speed=1.0)
        if not result:
            continue
        
        cumulative_gen_time += result["total_time_sec"]
        if idx == 1:
            time_to_first_audio = result["total_time_sec"]
            
        all_audio_chunks.append(result["audio_float"])
        total_pcm_bytes += len(result["pcm16_bytes"])
        total_audio_duration += result["audio_duration_sec"]
        
        lead_time = total_audio_duration - cumulative_gen_time
        
        print(f"Sentence {idx}/{len(sentences)}:")
        print(f"  Text: \"{sentence}\"")
        print(f"  Phonemes: {result['phonemes']}")
        print(f"  Tokens: {result['token_count']} | Inference: {result['infer_time_sec']:.3f}s | Audio: {result['audio_duration_sec']:.2f}s | RTF: {result['rtf']:.3f}")
        print(f"  Buffer Lead Time: {lead_time:+.2f}s (Audio generated ahead of playback cursor)")
        print()

    # Concatenate complete passage
    combined_audio = np.concatenate(all_audio_chunks)
    output_wav = "ebook_chapter_sample.wav"
    sf.write(output_wav, combined_audio, 24000)
    
    print("="*80)
    print(" STREAMING RESULTS SUMMARY")
    print("="*80)
    print(f"Time to First Audio (TTFA):   {time_to_first_audio:.3f} seconds (Listener hears voice almost immediately)")
    print(f"Total Audio Duration:         {total_audio_duration:.2f} seconds")
    print(f"Total Generation Time:        {cumulative_gen_time:.2f} seconds")
    print(f"Overall Real-Time Factor:     {cumulative_gen_time / total_audio_duration:.3f}")
    print(f"Total PCM16 Bytes Streamed:   {total_pcm_bytes:,} bytes")
    print(f"Saved complete audio to:      {output_wav}")
    print("="*80)

if __name__ == "__main__":
    run_prototype()
