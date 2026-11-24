package io.ymusic.player.audiofx

/**
 * Jon Dattorro reverb algorithm — a fine-tuned IIR feedback delay network.
 *
 * Ported from https://github.com/el-visio/dattorro-verb (C, by el-visio 2022)
 * which follows Jon Dattorro's paper: https://ccrma.stanford.edu/~dattorro/EffectDesignPart1.pdf
 *
 * Signal flow (mono in, stereo out):
 *   1. Pre-delay
 *   2. Input filter (low-pass)
 *   3. Input diffusor x 4 (all-pass filters)
 *   4. Signal splits into two halves of the "reverbation tank", for each:
 *      4.1 Cross feedback from post-damping delay of the other half
 *      4.2 Decay diffusor 1 (modulated all-pass filter)
 *      4.3 Pre-damping delay
 *      4.4 Damping (low-pass filter)
 *      4.5 Decay diffusor 2 (all-pass filter)
 *      4.6 Post-damping delay
 *   5. Left / right output tapped from multiple delay lines in the network
 *
 * Call [process] once per input sample, then [getLeft] / [getRight] for the wet stereo output.
 */
class DattorroReverb private constructor(
    private val preDelay: DelayBuffer,
    private val preFilterState: DoubleArray,
    private var preFilterAmount: Double,
    private val inDiffusion: Array<DelayBuffer>,
    private var inputDiffusion1Amount: Double,
    private var inputDiffusion2Amount: Double,
    private var decayDiffusion1Amount: Double,
    private val decayDiffusion1: Array<DelayBuffer>,
    private val preDampingDelay: Array<DelayBuffer>,
    private val dampingState: DoubleArray,
    private var dampingAmount: Double,
    private val decayDiffusion2: Array<DelayBuffer>,
    private var decayDiffusion2Amount: Double,
    private val postDampingDelay: Array<DelayBuffer>,
    private var decayAmount: Double,
    private var t: Int = 0,
) {

    companion object {
        private const val MAX_PREDELAY = 4800 // 100 ms at 48 kHz

        /** Create a reverb instance with default settings. */
        fun create(): DattorroReverb {
            val preDelay = DelayBuffer.create(MAX_PREDELAY)
            preDelay.setDelay(DelayBuffer.TAP_MAIN, (MAX_PREDELAY * 0.1).toInt())

            val inDiffusion = arrayOf(
                DelayBuffer.create(142),
                DelayBuffer.create(107),
                DelayBuffer.create(379),
                DelayBuffer.create(277),
            )

            val decayDiffusion1 = arrayOf(
                DelayBuffer.create(672),
                DelayBuffer.create(908),
            )

            val preDampingDelay = arrayOf(
                DelayBuffer.create(4453).apply {
                    setDelay(DelayBuffer.TAP_OUT1, 353)
                    setDelay(DelayBuffer.TAP_OUT2, 3627)
                    setDelay(DelayBuffer.TAP_OUT3, 1990)
                },
                DelayBuffer.create(4217).apply {
                    setDelay(DelayBuffer.TAP_OUT1, 266)
                    setDelay(DelayBuffer.TAP_OUT2, 2974)
                    setDelay(DelayBuffer.TAP_OUT3, 2111)
                },
            )

            val decayDiffusion2 = arrayOf(
                DelayBuffer.create(1800).apply {
                    setDelay(DelayBuffer.TAP_OUT1, 187)
                    setDelay(DelayBuffer.TAP_OUT2, 1228)
                },
                DelayBuffer.create(2656).apply {
                    setDelay(DelayBuffer.TAP_OUT1, 335)
                    setDelay(DelayBuffer.TAP_OUT2, 1913)
                },
            )

            val postDampingDelay = arrayOf(
                DelayBuffer.create(3720).apply {
                    setDelay(DelayBuffer.TAP_OUT1, 1066)
                    setDelay(DelayBuffer.TAP_OUT2, 2673)
                },
                DelayBuffer.create(3163).apply {
                    setDelay(DelayBuffer.TAP_OUT1, 121)
                    setDelay(DelayBuffer.TAP_OUT2, 1996)
                },
            )

            return DattorroReverb(
                preDelay = preDelay,
                preFilterState = DoubleArray(1),
                preFilterAmount = 0.85,
                inDiffusion = inDiffusion,
                inputDiffusion1Amount = 0.75,
                inputDiffusion2Amount = 0.625,
                decayDiffusion1Amount = 0.70,
                decayDiffusion1 = decayDiffusion1,
                preDampingDelay = preDampingDelay,
                dampingState = DoubleArray(2),
                dampingAmount = 0.95,
                decayDiffusion2 = decayDiffusion2,
                decayDiffusion2Amount = 0.5, // set by setDecay
                postDampingDelay = postDampingDelay,
                decayAmount = 0.75,
                t = 0,
            ).apply {
                setPreDelay(0.1)
                setPreFilter(0.85)
                setInputDiffusion1(0.75)
                setInputDiffusion2(0.625)
                setDecay(0.75)
                setDecayDiffusion(0.70)
                setDamping(0.95)
            }
        }
    }

    /** Set pre-delay length (relative to MAX_PREDELAY, 0.0..1.0). */
    fun setPreDelay(value: Double) {
        preDelay.setDelay(DelayBuffer.TAP_MAIN, (value * MAX_PREDELAY).toInt())
    }

    /** Set pre-filter (input low-pass) amount. */
    fun setPreFilter(value: Double) {
        preFilterAmount = value
    }

    /** Set input diffusion 1 amount. */
    fun setInputDiffusion1(value: Double) {
        inputDiffusion1Amount = value
    }

    /** Set input diffusion 2 amount. */
    fun setInputDiffusion2(value: Double) {
        inputDiffusion2Amount = value
    }

    /** Set decay diffusion 1 amount. */
    fun setDecayDiffusion(value: Double) {
        decayDiffusion1Amount = value
    }

    /** Set decay amount and derive decayDiffusion2Amount. */
    fun setDecay(value: Double) {
        decayAmount = value
        decayDiffusion2Amount = (value + 0.15).coerceIn(0.25, 0.50)
    }

    /** Set damping amount. */
    fun setDamping(value: Double) {
        dampingAmount = value
    }

    /**
     * Process one mono input sample. After calling, retrieve the wet stereo output
     * with [getLeft] and [getRight].
     */
    fun process(input: Double) {
        var x = input

        // Modulate decayDiffusion1 read offsets (LFO-like effect every 2048 samples)
        if ((t and 0x07FF) == 0) {
            if (t < (1 shl 15)) {
                decayDiffusion1[0].readOffset[DelayBuffer.TAP_MAIN]--
                decayDiffusion1[1].readOffset[DelayBuffer.TAP_MAIN]--
            } else {
                decayDiffusion1[0].readOffset[DelayBuffer.TAP_MAIN]++
                decayDiffusion1[1].readOffset[DelayBuffer.TAP_MAIN]++
            }
        }

        // 1. Pre-delay
        x = preDelay.process(t, x)

        // 2. Pre-filter (one-pole low-pass)
        preFilterState[0] += (x - preFilterState[0]) * preFilterAmount
        x = preFilterState[0]

        // 3. Input diffusion (4 all-pass filters)
        x = allPass(inDiffusion[0], t, inputDiffusion1Amount, x)
        x = allPass(inDiffusion[1], t, inputDiffusion1Amount, x)
        x = allPass(inDiffusion[2], t, inputDiffusion2Amount, x)
        x = allPass(inDiffusion[3], t, inputDiffusion2Amount, x)

        // 4. Reverbation tank — two halves with cross-feedback
        for (i in 0 until 2) {
            val other = 1 - i
            // Cross feedback from post-damping delay of the other half
            var x1 = x + postDampingDelay[other].read(DelayBuffer.TAP_MAIN, t) * decayAmount

            // Decay diffusor 1 (modulated all-pass)
            x1 = allPass(decayDiffusion1[i], t, -decayDiffusion1Amount, x1)
            // Pre-damping delay
            x1 = preDampingDelay[i].process(t, x1)
            // Damping (low-pass)
            dampingState[other] += (x1 - dampingState[other]) * dampingAmount
            x1 = dampingState[other]
            x1 *= decayAmount
            // Decay diffusor 2 (all-pass)
            x1 = allPass(decayDiffusion2[i], t, decayDiffusion2Amount, x1)
            // Write to post-damping delay
            postDampingDelay[i].write(t, x1)
        }

        // Increment delay position
        t++
    }

    /** Get the wet left-channel output. */
    fun getLeft(): Double {
        var a = 0.0
        a += preDampingDelay[1].read(DelayBuffer.TAP_OUT1, t)
        a += preDampingDelay[1].read(DelayBuffer.TAP_OUT2, t)
        a -= decayDiffusion2[1].read(DelayBuffer.TAP_OUT2, t)
        a += postDampingDelay[1].read(DelayBuffer.TAP_OUT2, t)
        a -= preDampingDelay[0].read(DelayBuffer.TAP_OUT3, t)
        a -= decayDiffusion2[0].read(DelayBuffer.TAP_OUT1, t)
        a += postDampingDelay[0].read(DelayBuffer.TAP_OUT1, t)
        return a
    }

    /** Get the wet right-channel output. */
    fun getRight(): Double {
        var a = 0.0
        a += preDampingDelay[0].read(DelayBuffer.TAP_OUT1, t)
        a += preDampingDelay[0].read(DelayBuffer.TAP_OUT2, t)
        a -= decayDiffusion2[0].read(DelayBuffer.TAP_OUT2, t)
        a += postDampingDelay[0].read(DelayBuffer.TAP_OUT2, t)
        a -= preDampingDelay[1].read(DelayBuffer.TAP_OUT3, t)
        a -= decayDiffusion2[1].read(DelayBuffer.TAP_OUT1, t)
        a += postDampingDelay[1].read(DelayBuffer.TAP_OUT1, t)
        return a
    }

    /** All-pass filter: keeps amplitude per frequency, messes with phase. */
    private fun allPass(db: DelayBuffer, t: Int, gain: Double, sample: Double): Double {
        val delayed = db.read(DelayBuffer.TAP_MAIN, t)
        val w = sample + delayed * (-gain)
        db.write(t, w)
        return delayed + w * gain
    }
}

/**
 * Circular delay buffer with multiple named taps. Buffer size is always a power of two
 * so wrapping uses a bitmask instead of modulo.
 */
class DelayBuffer private constructor(
    internal val buffer: DoubleArray,
    internal val mask: Int,
    internal val readOffset: IntArray,
) {
    companion object {
        const val TAP_MAIN = 0
        const val TAP_OUT1 = 1
        const val TAP_OUT2 = 2
        const val TAP_OUT3 = 3
        private const val MAX_TAPS = 4

        /** Create a delay buffer that can hold at least [minDelay] samples. */
        fun create(minDelay: Int): DelayBuffer {
            // Buffer size is always 2^n
            var numBits = 0
            var x = minDelay
            while (x > 0) {
                numBits++
                x = x shr 1
            }
            var bufferSize = 1 shl numBits
            if (bufferSize < 2) bufferSize = 2

            val buffer = DoubleArray(bufferSize)
            val mask = bufferSize - 1
            val readOffset = IntArray(MAX_TAPS)

            return DelayBuffer(buffer, mask, readOffset).apply {
                setDelay(TAP_MAIN, minDelay)
            }
        }
    }

    /** Set the read offset for a tap. */
    fun setDelay(tap: Int, delay: Int) {
        readOffset[tap] = mask + 1 - delay
    }

    /** Write [sample] into the buffer at position t, return the delayed output. */
    fun process(t: Int, sample: Double): Double {
        buffer[t and mask] = sample
        return buffer[(t + readOffset[TAP_MAIN]) and mask]
    }

    /** Write [sample] into the buffer at position t. */
    fun write(t: Int, sample: Double) {
        buffer[t and mask] = sample
    }

    /** Read a delayed value from [tap] at position t. */
    fun read(tap: Int, t: Int): Double {
        return buffer[(t + readOffset[tap]) and mask]
    }
}
