# Reverb — Dattorro plate

A lush stereo plate reverb built into YMusic's audio path, on every platform (Android, Android TV,
macOS, Windows, Linux, iOS). It runs in the same native DSP stage as the equalizer, bass and
virtualizer, so it works for both the YouTube (`:mse`) stream and local files, with no WebView and no
extra audio session.

The algorithm is Jon Dattorro's classic plate reverberator — a mono send into a tank of four
all-pass diffusers and two delay loops, tapped for a wide stereo image. It is the same topology
behind many well-known hardware and plugin plates: dense, smooth, and musical rather than metallic.

## Where it lives

|| Layer | File | Role |
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

|| Control | What it does |
|---------|--------------|
| **Room / Decay** | Feedback around the tank — small room to a long, blooming tail. Knob is editable. |
| **Damp** | High-frequency damping inside the loop. High = dark/soft, low = bright. Knob is editable. |
| **Mix** | Dry/wet balance. Dry stays bit-exact when the effect is off. Knob is editable. |
| **Predelay** | 0–200 ms of silence before the reverb starts; pushes the source "forward". Knob is editable. |
| **Width** | Stereo spread of the wet taps, mono to full width. Knob is editable. |

### Presets

Twelve ready-made voices (values are `room / damp / mix / predelay / width`):

|| Preset | room | damp | mix | predelay | width | Character |
|--------|------|------|-----|----------|-------|-----------|
| **Room** | 0.30 | 0.50 | 0.20 | 15 ms | 0.7 | Small, intimate space. |
| **Hall** | 0.70 | 0.60 | 0.35 | 40 ms | 1.0 | Concert-hall bloom. |
| **Plate** | 0.00 | 0.30 | 0.25 | 5 ms | 0.9 | Bright, dense vintage plate. |
| **Stadium** | 0.90 | 0.70 | 0.45 | 60 ms | 1.0 | Huge, distant wash (~3 s tail). |
| **Cathedral** | 0.95 | 0.80 | 0.55 | 90 ms | 1.0 | Vast, echoing sacred space. |
| **Chamber** | 0.55 | 0.50 | 0.30 | 25 ms | 0.9 | Warm wooden room, between Room and Hall. |
| **Spring** | 0.40 | 0.25 | 0.35 | 5 ms | 0.8 | Bright, "boingy" spring; gitar & rock. |
| **Ambience** | 0.15 | 0.40 | 0.15 | 8 ms | 0.6 | Subtle early reflections; tidak terlalu basah. |
| **Cave** | 0.85 | 0.85 | 0.50 | 50 ms | 1.0 | Gelap, dalam, tail panjang. |
| **Arena** | 0.80 | 0.55 | 0.40 | 35 ms | 1.0 | Besar dan terkontrol; live / rock. |
| **Vocal** | 0.45 | 0.60 | 0.28 | 20 ms | 0.7 | Mid-forward; vokal tetap di depan. |
| **Loft** | 0.75 | 0.30 | 0.45 | 45 ms | 1.0 | Industrial, logam, lebar; elektronik. |

Picking a preset fills the knobs; moving any knob away from a preset's values shows it as **Custom**.
A **Reset** button in the reverb panel returns the effect to its defaults.

### Advanced diffusion

An expandable **Advanced** section exposes the tank's diffusion coefficients for sound design:

- **Pre-filter** — input low-pass before the tank (tames harsh transients).
- **Input diffusion 1 / 2** — the two input all-pass coefficients (smear vs. clarity of the attack).
- **Decay diffusion** — the loop all-pass coefficient (density of the tail).

These map straight onto Dattorro's `bandwidth`, `input diffusion 1/2` and `decay diffusion`
parameters; the presets leave them at the paper's defaults.

## Default Configuration (Edjing Mix compatible)

The default reverb configuration has been updated to match Edjing Mix v7.42.00:

- **Room/Decay**: 0.5
- **Damp**: 0.7 (darker/softer tone)
- **Mix**: 0.7 (more wet signal)
- **Predelay**: 0 ms (no pre-delay)
- **Width**: 1.0 (full stereo spread)

This configuration produces a reverb character similar to Edjing Mix's default Dattorro plate reverb implementation.

### End-of-stream tail

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
  reverb is integrated. YMusic is GPL-3.0; see [`LICENSE`](LICENSE). YMusic's port adapts the
  Dattorro tank to Android, desktop (Rust), Apple and Linux, and exposes per-parameter knobs
  alongside presets so the sound can be tuned beyond the ready-made voices.

### el-visio/dattorro-verb — MIT License

```license
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

Total lines: 289 (was 115, expanded with new sections)
File size: ~15KB (was 6.5KB, expanded with new default configuration section)