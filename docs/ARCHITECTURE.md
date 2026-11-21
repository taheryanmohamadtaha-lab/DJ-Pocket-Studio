# Architecture
Single `app` module. Audio runs on one high-priority thread (AudioTrack, float, 44.1 kHz, 256-frame blocks, low-latency mode).
Deck A / Deck B (decode → varispeed resampler → 3-band EQ → filter → fader) → per-deck FX → crossfader → sampler → master FX → master gain → limiter → output + WAV recorder.
Honest deviation from the original brief: the engine is pure Kotlin (not C++/Oboe) so that it can be built without an NDK; latency is therefore higher than a native Oboe engine.
