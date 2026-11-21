# DJ Pocket Studio – Ultimate Mobile DJ

Dual-deck DJ console for Android, controlled entirely by touch. Kotlin + Jetpack Compose, offline, no account.

## What is implemented in this first version (v0.1)
Real audio processing (not just buttons):
- Two independent decks decoding MP3/WAV/FLAC/AAC/OGG via the device's MediaCodec
- Play / Pause / Cue (hold to preview), Sync (tempo + beat phase), pitch slider (±6/10/16/100 %)
- Jog wheel: vinyl scratch (touch = hold, drag = scrub) or pitch-bend mode; Brake and Backspin
- Scrolling coloured waveform with beat grid, cue markers and loop region; tap / drag to seek, double-tap to zoom
- Automatic BPM and first-beat detection; manual BPM, ÷2/×2, grid shift and "beat 1 = now"
- Mixer: gain, 3-band EQ (long-press = kill), sweep filter, channel faders and meters, crossfader (smooth / cut / reverse), master volume, limiter, clip warning
- FX (per deck A / B or master): Echo, Reverb, Flanger, Phaser, Bit Crusher, Gate, Roll
- Loops: auto 1/2…32 beats, manual in/out, exit, reloop
- 8 hot cues per deck, saved per track
- 16-pad sampler with built-in synthesized sounds (no licensing issues), loop mode
- Recording of the final master mix to WAV
- LIVE mode, two themes (Club / RGB Neon), Persian + English

Not implemented yet: playlists/library database, key detection and key lock, AAC recording, compressor / dub-echo / flanger variants,
separate headphone cue output, user samples, theme colour customisation, in-app playback of recordings.

## Build
Push to GitHub. The workflow in `.github/workflows/build-apk.yml` builds `DJ_Pocket_Studio_Debug.apk` on every push (Actions → run → Artifacts).
Pushing a tag such as `v0.1.0` also builds a release APK. Release is only installable if the secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are set; otherwise it is published as UNSIGNED and clearly named so.

Local build: Android Studio (JDK 17), open the folder and press Run.
