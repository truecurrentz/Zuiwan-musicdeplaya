package zuiwan.dsp

import zuiwan.model.EqBand
import zuiwan.model.FilterKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class BiquadFilter(
    band: EqBand,
    sampleRate: Int,
    channels: Int,
) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private val z1 = DoubleArray(channels.coerceAtLeast(1))
    private val z2 = DoubleArray(channels.coerceAtLeast(1))
    val bypass: Boolean

    init {
        val sr = sampleRate.coerceAtLeast(8_000).toDouble()
        val fc = band.frequency.coerceIn(16.0, sr * 0.46)
        val q = band.q.coerceIn(0.1, 18.0)
        val gain = band.gainDb.coerceIn(-24.0, 24.0)
        val needsGain = band.kind in setOf(FilterKind.PK, FilterKind.LSC, FilterKind.HSC)

        if (!band.enabled || (needsGain && abs(gain) < 0.01)) {
            bypass = true
            b0 = 1.0
            b1 = 0.0
            b2 = 0.0
            a1 = 0.0
            a2 = 0.0
        } else {
            bypass = false
            val w0 = 2.0 * PI * fc / sr
            val cosw = cos(w0)
            val sinw = sin(w0)
            val alpha = sinw / (2.0 * q)
            val amp = 10.0.pow(gain / 40.0)
            val twoSqrtAAlpha = 2.0 * sqrt(amp) * alpha

            var rb0 = 1.0
            var rb1 = 0.0
            var rb2 = 0.0
            var ra0 = 1.0
            var ra1 = 0.0
            var ra2 = 0.0

            when (band.kind) {
                FilterKind.PK -> {
                    rb0 = 1.0 + alpha * amp
                    rb1 = -2.0 * cosw
                    rb2 = 1.0 - alpha * amp
                    ra0 = 1.0 + alpha / amp
                    ra1 = -2.0 * cosw
                    ra2 = 1.0 - alpha / amp
                }

                FilterKind.LSC -> {
                    rb0 = amp * ((amp + 1.0) - (amp - 1.0) * cosw + twoSqrtAAlpha)
                    rb1 = 2.0 * amp * ((amp - 1.0) - (amp + 1.0) * cosw)
                    rb2 = amp * ((amp + 1.0) - (amp - 1.0) * cosw - twoSqrtAAlpha)
                    ra0 = (amp + 1.0) + (amp - 1.0) * cosw + twoSqrtAAlpha
                    ra1 = -2.0 * ((amp - 1.0) + (amp + 1.0) * cosw)
                    ra2 = (amp + 1.0) + (amp - 1.0) * cosw - twoSqrtAAlpha
                }

                FilterKind.HSC -> {
                    rb0 = amp * ((amp + 1.0) + (amp - 1.0) * cosw + twoSqrtAAlpha)
                    rb1 = -2.0 * amp * ((amp - 1.0) + (amp + 1.0) * cosw)
                    rb2 = amp * ((amp + 1.0) + (amp - 1.0) * cosw - twoSqrtAAlpha)
                    ra0 = (amp + 1.0) - (amp - 1.0) * cosw + twoSqrtAAlpha
                    ra1 = 2.0 * ((amp - 1.0) - (amp + 1.0) * cosw)
                    ra2 = (amp + 1.0) - (amp - 1.0) * cosw - twoSqrtAAlpha
                }

                FilterKind.LPQ -> {
                    rb0 = (1.0 - cosw) * 0.5
                    rb1 = 1.0 - cosw
                    rb2 = (1.0 - cosw) * 0.5
                    ra0 = 1.0 + alpha
                    ra1 = -2.0 * cosw
                    ra2 = 1.0 - alpha
                }

                FilterKind.HPQ -> {
                    rb0 = (1.0 + cosw) * 0.5
                    rb1 = -(1.0 + cosw)
                    rb2 = (1.0 + cosw) * 0.5
                    ra0 = 1.0 + alpha
                    ra1 = -2.0 * cosw
                    ra2 = 1.0 - alpha
                }
            }

            b0 = rb0 / ra0
            b1 = rb1 / ra0
            b2 = rb2 / ra0
            a1 = ra1 / ra0
            a2 = ra2 / ra0
        }
    }

    fun step(sample: Double, channel: Int): Double {
        if (bypass) return sample
        val ch = channel.coerceIn(0, z1.lastIndex)
        val out = b0 * sample + z1[ch]
        z1[ch] = b1 * sample - a1 * out + z2[ch]
        z2[ch] = b2 * sample - a2 * out
        return if (out.isFinite()) out else 0.0
    }

    fun clear() {
        z1.fill(0.0)
        z2.fill(0.0)
    }
}
