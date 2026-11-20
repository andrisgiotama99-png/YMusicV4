# Reverb — Dattorro plate

A lush stereo plate reverb built into YMusic's audio path, on every platform (Android, Android TV,
macOS, Windows, Linux, iOS). It runs in the same native DSP stage as the equalizer, bass and
virtualizer, so it works for both the YouTube (`:mse`) stream and local files, with no WebView and no
extra audio session.

The algorithm is Jon Dattorro's classic plate reverberator — a mono send into a tank of four
all-pass diffusers and two delay loops, tapped for a wide stereo image. It is the same topology
behind many well-known hardware and plugin plates: dense, smooth, and musical rather than metallic.

## Where it lives

| Layer | File | Role |
|-------|------|------|
| C DSP (Android / JVM / Apple) | `mse/native/audio_fx.c` | The Dattorro tank on the PCM fill path; `audio_fx_set_reverb` applies the config. |
| C DSP (local files, JVM) | `mse/native/file_av.c` | Same tank via `file_av_set_reverb`. |
| Rust mirror (desktop app) | `desktopRust/crates/ym-mse/src/lib.rs` (`Reverb`) | Bit-matching tank for the native Rust desktop player. |
| Config + plumbing | `ReverbConfig` → `Player.setReverb` → `MsePlayer` / `AndroidLocalPlayer` / `JvmLocalPlayer` | One config object flows to whichever host is playing. |
| UI | `commonUi/.../EqualizerSheet.kt` (`ReverbSection`) | The reverb panel in the sound sheet. |
| Persistence | `audio_effects` (Rust) / shared prefs (Kotlin) | The setting survives restarts. |

The C and Rust tanks are kept in lock-step: the delay lengths are Dattorro's 48 kHz numbers scaled to
the host sample rate, so the character is identical whichever backend is active.

## Controls

| Control | What it does |
|---------|--------------|
| **Room / Decay** | Feedback around the tank — small room to a long, blooming tail. |
| **Damp** | High-frequency damping inside the loop. High = dark/soft, low = bright. |
| **Mix** | Dry/wet balance. Dry stays bit-exact when the effect is off. |
| **Predelay** | 0–200 ms of silence before the reverb starts; pushes the source "forward". |
| **Width** | Stereo spread of the wet taps, mono to full width. |

### Presets

Four ready-made voices (values are `room / damp / mix / predelay / width`):

| Preset | room | damp | mix | predelay | width | Character |
|--------|------|------|-----|----------|-------|-----------|
| **Room** | 0.35 | 0.60 | 0.25 | 10 ms | 0.8 | Small, intimate space. |
| **Hall** | 0.75 | 0.45 | 0.35 | 40 ms | 1.0 | Concert-hall bloom. |
| **Plate** | 0.85 | 0.25 | 0.40 | 5 ms | 1.0 | Bright, dense vintage plate. |
| **Stadium** | 0.95 | 0.35 | 0.45 | 80 ms | 1.0 | Huge, distant wash (~3 s tail). |

Picking a preset fills the knobs; moving any knob away from a preset's values shows it as custom.
A **Reset** button in the reverb panel returns the effect to its defaults.

### Advanced diffusion

An expandable **Advanced** section exposes the tank's diffusion coefficients for sound design:

- **Pre-filter** — input low-pass before the tank (tames harsh transients).
- **Input diffusion 1 / 2** — the two input all-pass coefficients (smear vs. clarity of the attack).
- **Decay diffusion** — the loop all-pass coefficient (density of the tail).

These map straight onto Dattorro's `bandwidth`, `input diffusion 1/2` and `decay diffusion`
parameters; the presets leave them at the paper's defaults.

## End-of-stream tail

When a track ends, the reverb tail is allowed to **drain** instead of being cut off: the DAC path
keeps pulling wet-only frames until the tank rings out, so the last note fades naturally rather than
stopping dead.

## Credits

This reverb would not exist without the work below. Thank you.

- **Jon Dattorro** — the algorithm. *"Effect Design Part 1: Reverberator and Other Filters"*,
  Journal of the Audio Engineering Society, 1997 (CCRMA, Stanford). The tank topology, the 48 kHz
  delay lengths and the diffusion structure are his.
  <https://ccrma.stanford.edu/~dattorro/EffectDesignPart1.pdf>
- **Pauli Pölkki / [`el-visio/dattorro-verb`](https://github.com/el-visio/dattorro-verb)** — the
  reference implementation (MIT License, © 2022 Pauli Pölkki) that the C and Rust tanks were adapted
  from. The MIT notice is reproduced below as required.
- **YMusic** — the host application and its native DSP pipeline (`audio_fx.c`), into which the
  reverb is integrated. YMusic is GPL-3.0; see [`LICENSE`](LICENSE).

### el-visio/dattorro-verb — MIT License

```
MIT License

Copyright (c) 2022 Pauli Pölkki

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```