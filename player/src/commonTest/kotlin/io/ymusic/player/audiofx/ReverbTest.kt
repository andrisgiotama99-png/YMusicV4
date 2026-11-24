package io.ymusic.player.audiofx

import kotlin.test.Test
import kotlin.test.assertTrue

class ReverbTest {

    @Test
    fun reverbProducesNonZeroOutput() {
        val reverb = Reverb(
            ReverbConfig(enabled = true, mix = 1.0f, decay = 0.75)
        )

        // Feed an impulse (non-zero first sample, then zeros)
        val outputs = List(1000) { i ->
            val input = if (i == 0) 1.0f else 0.0f
            reverb.processMono(input)
        }

        // At least some output samples should be non-zero (reverb tail)
        val nonZeroCount = outputs.count { it.first != 0.0f || it.second != 0.0f }
        assertTrue(nonZeroCount > 10) { "Expected reverb tail to produce non-zero output, got $nonZeroCount samples" }
    }

    @Test
    fun disabledReverbPassesThrough() {
        val reverb = Reverb(
            ReverbConfig(enabled = false, mix = 0.5f)
        )

        val (l, r) = reverb.processMono(0.5f)
        assertTrue(l == 0.5f) { "Left should pass through when disabled, got $l" }
        assertTrue(r == 0.5f) { "Right should pass through when disabled, got $r" }
    }

    @Test
    fun reverbStaysBounded() {
        val reverb = Reverb(
            ReverbConfig(enabled = true, mix = 1.0f, decay = 0.75, damping = 0.95)
        )

        // Feed white-noise-like input for a while and verify output doesn't blow up
        var maxAbs = 0.0
        for (i in 0 until 10000) {
            val input = (Math.sin(i * 0.1) + Math.sin(i * 0.37)) / 2f
            val (l, r) = reverb.processMono(input)
            maxAbs = maxOf(maxAbs, Math.abs(l.toDouble()), Math.abs(r.toDouble()))
        }
        assertTrue(maxAbs < 10.0) { "Reverb output blew up: maxAbs=$maxAbs" }
    }
}
