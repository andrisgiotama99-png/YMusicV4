#ifndef YMUSIC_AUDIO_FX_H
#define YMUSIC_AUDIO_FX_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define AFX_BANDS 10
#define AFX_LEVELS 64
/* Parameter vector for audio_fx_set_reverb, in order:
 * [0] active (0 = off), [1] room size 0..1 (decay), [2] damping 0..1 (high-frequency absorption),
 * [3] mix 0..1 (0 = dry, 1 = wet), [4] predelay ms 0..200 (wet send only), [5] width 0..1 (wet stereo spread),
 * [6] input pre-filter 0..1, [7] input diffusion 1 0..1, [8] input diffusion 2 0..1, [9] decay diffusion 0..1. */
#define AFX_REVERB_PARAMS 10

typedef struct AudioFx AudioFx;

AudioFx *audio_fx_create(void);
void audio_fx_destroy(AudioFx *fx);
void audio_fx_reset(AudioFx *fx);

void audio_fx_set_rate(AudioFx *fx, int rate);
void audio_fx_set_volume(AudioFx *fx, float vol);
void audio_fx_set_eq(AudioFx *fx, const float bands_db[AFX_BANDS]);
void audio_fx_set_bass(AudioFx *fx, float db);
void audio_fx_set_virtualizer(AudioFx *fx, float width);
/* Dattorro plate reverb on the wet send only; the dry path stays immediate, so no latency is added. */
void audio_fx_set_reverb(AudioFx *fx, const float params[AFX_REVERB_PARAMS]);

/* Whether the reverb is currently active (for end-of-stream tail draining). */
int audio_fx_reverb_active(AudioFx *fx);
/* Estimated reverb tail length in frames at the current rate (0 when inactive). */
int audio_fx_reverb_tail_frames(AudioFx *fx);
/* User level trim, -12..+12 dB, on top of the automatic loudness-matched pre-gain. */
void audio_fx_set_preamp(AudioFx *fx, float db);
void audio_fx_set_speed(AudioFx *fx, float speed);
void audio_fx_set_pitch(AudioFx *fx, float pitch);

/* Same-length: EQ, bass, virtualizer, volume. frames = samples per channel.
 * Also feeds the live spectrum used by [audio_fx_levels], even when color is off. */
void audio_fx_color(AudioFx *fx, int16_t *s16, int frames, int ch);

/* Fixed-position log-frequency bars in 0..1 (bass left, treble right), for the window ending
 * [delay_frames] before the newest colored frame — the frames the sink still holds unplayed. */
int audio_fx_levels(AudioFx *fx, float *out, int n, int delay_frames);

/*
 * SoundTouch pair: rate = pitch, tempo = speed/pitch.
 * Cubic+FIR transpose; TDStretch with energy-normalized overlap seek.
 */
int audio_fx_stretch(AudioFx *fx, const int16_t *in, int in_frames, int16_t *out, int out_cap, int ch);

#ifdef __cplusplus
}
#endif

#endif
