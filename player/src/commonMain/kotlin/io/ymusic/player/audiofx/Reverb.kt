package io.ymusic.player.audiofx

/**
 * Convenience wrapper around [DattorroReverb] that applies wet/dry mixing.
 *
 * Usage:
 * ```
 * val reverb = Reverb(ReverbConfig(enabled = true, mix = 0.3f))
 * val out = FloatArray(numSamples * 2) // stereo interleaved
 * for (i in samples.indices) {
 *     val wet = reverb.process(samples[i]) // mono in -> (left, right) wet
 *     out[i * 2]     = samples[i] * (1 - reverb.mix) + wet.left * reverb.mix
 *     out[i * 2 + 1] = samples[i] * (1 - reverb.mix) + wet.right * reverb.mix
 * }
 * ```
 */
class Reverb(config: ReverbConfig) {

    private val engine = DattorroReverb.create().apply {
        setPreDelay(config.preDelay)
        setPreFilter(config.preFilter)
        setInputDiffusion1(config.inputDiffusion1)
        setInputDiffusion2(config.inputDiffusion2)
        setDecay(config.decay)
        setDecayDiffusion(config.decayDiffusion)
        setDamping(config.damping)
    }

    /** Wet/dry mix: 0.0 = dry only, 1.0 = 100% wet. */
    val mix: Float = config.mix
    val enabled: Boolean = config.enabled

    /** Process one mono sample into a wet stereo pair. */
    fun processMono(input: Float): Pair<Float, Float> {
        if (!enabled) return Pair(input, input)
        engine.process(input.toDouble())
        return Pair(engine.getLeft().toFloat(), engine.getRight().toFloat())
    }

    /** Process one stereo frame (left, right) into a wet stereo pair. Averages the inputs to mono
     * before reverb (Dattorro is a mono-in reverb), then returns the wet output. */
    fun processStereo(left: Float, right: Float): Pair<Float, Float> {
        if (!enabled) return Pair(left, right)
        val mono = (left + right) / 2f
        return processMono(mono)
    }
}
