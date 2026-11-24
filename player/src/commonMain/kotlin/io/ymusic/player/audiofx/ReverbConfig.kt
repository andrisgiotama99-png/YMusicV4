package io.ymusic.player.audiofx

/** Configuration for the [DattorroReverb] effect. */
data class ReverbConfig(
    /** Whether the reverb is enabled. */
    val enabled: Boolean = false,
    /** Wet/dry mix: 0.0 = dry only, 1.0 = 100% wet reverb. */
    val mix: Float = 0.5f,
    /** Pre-delay time as a fraction of MAX_PREDELAY (100 ms at 48 kHz). 0.0..1.0. */
    val preDelay: Double = 0.1,
    /** Pre-filter (input low-pass) amount. 0.0..1.0. */
    val preFilter: Double = 0.85,
    /** Input diffusion 1 amount (first two all-pass stages). 0.0..1.0. */
    val inputDiffusion1: Double = 0.75,
    /** Input diffusion 2 amount (last two all-pass stages). 0.0..1.0. */
    val inputDiffusion2: Double = 0.625,
    /** Decay / reverb time. 0.0..1.0. */
    val decay: Double = 0.75,
    /** Decay diffusion amount (modulated all-pass). 0.0..1.0. */
    val decayDiffusion: Double = 0.70,
    /** Damping (high-frequency absorption) amount. 0.0..1.0. */
    val damping: Double = 0.95,
) {
    init {
        require(mix in 0.0f..1.0f) { "mix out of range: $mix" }
        require(preDelay in 0.0..1.0) { "preDelay out of range: $preDelay" }
        require(preFilter in 0.0..1.0) { "preFilter out of range: $preFilter" }
        require(inputDiffusion1 in 0.0..1.0) { "inputDiffusion1 out of range: $inputDiffusion1" }
        require(inputDiffusion2 in 0.0..1.0) { "inputDiffusion2 out of range: $inputDiffusion2" }
        require(decay in 0.0..1.0) { "decay out of range: $decay" }
        require(decayDiffusion in 0.0..1.0) { "decayDiffusion out of range: $decayDiffusion" }
        require(damping in 0.0..1.0) { "damping out of range: $damping" }
    }
}
