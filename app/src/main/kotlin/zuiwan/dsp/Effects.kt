package zuiwan.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import zuiwan.model.AudioEffects
import zuiwan.model.EqBand
import zuiwan.model.FilterKind
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

@UnstableApi
class EffectsProcessor : BaseAudioProcessor() {
    @Volatile
    private var fx: AudioEffects = AudioEffects()

    @Volatile
    private var dirty: Boolean = true

    private var encoding: Int = C.ENCODING_INVALID
    private var channels: Int = 0
    private var sampleRate: Int = 44100

    private var bassFilter: BiquadFilter? = null
    private var gainLinear: Double = 1.0
    private var orbitPhase: Double = 0.0

    private var combL1 = FloatArray(1)
    private var combL2 = FloatArray(1)
    private var combR1 = FloatArray(1)
    private var combR2 = FloatArray(1)
    private var apL = FloatArray(1)
    private var apR = FloatArray(1)
    private var haasBuf = FloatArray(1)

    private var idxC1 = 0
    private var idxC2 = 0
    private var idxAp = 0
    private var idxHaas = 0

    fun applyfx(next: AudioEffects) {
        fx = next
        dirty = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = enc
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        allocbuffers()
        dirty = true
        return inputAudioFormat
    }

    private fun allocbuffers() {
        val c1Len = ((sampleRate * 0.031).toInt()).coerceAtLeast(64)
        val c2Len = ((sampleRate * 0.041).toInt()).coerceAtLeast(64)
        val apLen = ((sampleRate * 0.0052).toInt()).coerceAtLeast(32)
        val haasLen = ((sampleRate * 0.011).toInt()).coerceAtLeast(16)
        combL1 = FloatArray(c1Len)
        combR1 = FloatArray(c1Len + 23)
        combL2 = FloatArray(c2Len)
        combR2 = FloatArray(c2Len + 29)
        apL = FloatArray(apLen)
        apR = FloatArray(apLen + 11)
        haasBuf = FloatArray(haasLen)
        idxC1 = 0
        idxC2 = 0
        idxAp = 0
        idxHaas = 0
    }

    private fun rebuild() {
        val cur = fx
        gainLinear = 10.0.pow(cur.gainDb.toDouble().coerceIn(-12.0, 12.0) / 20.0)
        bassFilter = if (cur.bassBoost > 0.01f && sampleRate > 0 && channels > 0) {
            BiquadFilter(
                EqBand(
                    frequency = 68.0,
                    gainDb = (cur.bassBoost.coerceIn(0f, 1f) * 9.5).toDouble(),
                    q = 0.74,
                    kind = FilterKind.LSC,
                ),
                sampleRate,
                channels,
            )
        } else {
            null
        }
        dirty = false
    }

    private fun haswork(cur: AudioEffects): Boolean {
        if (!cur.enabled) return false
        return cur.bassBoost > 0.01f ||
            cur.stereoWide > 0.01f ||
            cur.reverb > 0.01f ||
            cur.spatial8d ||
            abs(cur.balance) > 0.01f ||
            abs(cur.gainDb) > 0.05f ||
            cur.nightLimit
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (dirty) rebuild()

        val cur = fx
        val out = replaceOutputBuffer(remaining)
        if (!haswork(cur) || channels <= 0) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        if (channels == 1) {
            processmono(inputBuffer, out, cur)
        } else {
            processstereo(inputBuffer, out, cur)
        }
        out.flip()
    }

    private fun processmono(inp: ByteBuffer, out: ByteBuffer, cur: AudioEffects) {
        val bf = bassFilter
        val g = gainLinear
        if (encoding == C.ENCODING_PCM_16BIT) {
            while (inp.remaining() >= 2) {
                var s = inp.short.toDouble() / 32768.0 * g
                if (bf != null) s = bf.step(s, 0)
                if (cur.nightLimit) s = limitknee(s)
                out.putShort((clipsoft(s) * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
        } else {
            while (inp.remaining() >= 4) {
                var s = inp.float.toDouble() * g
                if (bf != null) s = bf.step(s, 0)
                if (cur.nightLimit) s = limitknee(s)
                out.putFloat(clipsoft(s).toFloat())
            }
        }
    }

    private fun processstereo(inp: ByteBuffer, out: ByteBuffer, cur: AudioEffects) {
        val bf = bassFilter
        val g = gainLinear
        val wide = cur.stereoWide.coerceIn(0f, 1f).toDouble()
        val revMix = (cur.reverb.coerceIn(0f, 1f) * 0.34f).toDouble()
        val bal = cur.balance.coerceIn(-1f, 1f).toDouble()
        val gainL = if (bal > 0.0) 1.0 - bal else 1.0
        val gainR = if (bal < 0.0) 1.0 + bal else 1.0
        val phaseStep = 2.0 * PI * 0.115 / sampleRate
        val extraCh = channels - 2

        if (encoding == C.ENCODING_PCM_16BIT) {
            val frameBytes = channels * 2
            while (inp.remaining() >= frameBytes) {
                var l = inp.short.toDouble() / 32768.0 * g
                var r = inp.short.toDouble() / 32768.0 * g
                if (bf != null) {
                    l = bf.step(l, 0)
                    r = bf.step(r, 1)
                }
                val pair = stepfx(l, r, wide, revMix, cur.spatial8d, phaseStep, gainL, gainR, cur.nightLimit)
                out.putShort((pair.first * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
                out.putShort((pair.second * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
                repeat(extraCh) { out.putShort(inp.short) }
            }
        } else {
            val frameBytes = channels * 4
            while (inp.remaining() >= frameBytes) {
                var l = inp.float.toDouble() * g
                var r = inp.float.toDouble() * g
                if (bf != null) {
                    l = bf.step(l, 0)
                    r = bf.step(r, 1)
                }
                val pair = stepfx(l, r, wide, revMix, cur.spatial8d, phaseStep, gainL, gainR, cur.nightLimit)
                out.putFloat(pair.first.toFloat())
                out.putFloat(pair.second.toFloat())
                repeat(extraCh) { out.putFloat(inp.float) }
            }
        }
    }

    private fun stepfx(
        inL: Double,
        inR: Double,
        wide: Double,
        revMix: Double,
        orbit: Boolean,
        phaseStep: Double,
        gainL: Double,
        gainR: Double,
        nightLimit: Boolean,
    ): Pair<Double, Double> {
        var l = inL
        var r = inR

        if (wide > 0.01) {
            val delayed = haasBuf[idxHaas].toDouble()
            haasBuf[idxHaas] = r.toFloat()
            idxHaas = (idxHaas + 1) % haasBuf.size
            val mid = (l + r) * 0.5
            val side = (l - (r * 0.72 + delayed * 0.28)) * 0.5 * (1.0 + wide * 1.15)
            l = mid + side
            r = mid - side
        }

        if (orbit) {
            orbitPhase += phaseStep
            if (orbitPhase > 2.0 * PI) orbitPhase -= 2.0 * PI
            val pan = sin(orbitPhase)
            val depth = 0.78 + 0.22 * cos(orbitPhase)
            val mono = (l + r) * 0.5
            val panL = ((1.0 - pan) * 0.5).coerceIn(0.08, 1.0)
            val panR = ((1.0 + pan) * 0.5).coerceIn(0.08, 1.0)
            l = (l * 0.35 + mono * 0.65) * panL * 1.35 * depth
            r = (r * 0.35 + mono * 0.65) * panR * 1.35 * depth
        }

        if (revMix > 0.005) {
            val c1L = combL1[idxC1 % combL1.size].toDouble()
            val c1R = combR1[idxC1 % combR1.size].toDouble()
            combL1[idxC1 % combL1.size] = (l + c1L * 0.76).toFloat()
            combR1[idxC1 % combR1.size] = (r + c1R * 0.76).toFloat()
            idxC1++

            val c2L = combL2[idxC2 % combL2.size].toDouble()
            val c2R = combR2[idxC2 % combR2.size].toDouble()
            combL2[idxC2 % combL2.size] = (l + c2L * 0.72).toFloat()
            combR2[idxC2 % combR2.size] = (r + c2R * 0.72).toFloat()
            idxC2++

            val sumL = (c1L + c2L) * 0.42
            val sumR = (c1R + c2R) * 0.42

            val bufL = apL[idxAp % apL.size].toDouble()
            val wetL = -sumL * 0.5 + bufL
            apL[idxAp % apL.size] = (sumL + wetL * 0.5).toFloat()

            val bufR = apR[idxAp % apR.size].toDouble()
            val wetR = -sumR * 0.5 + bufR
            apR[idxAp % apR.size] = (sumR + wetR * 0.5).toFloat()
            idxAp++

            l = l * (1.0 - revMix * 0.35) + wetL * revMix
            r = r * (1.0 - revMix * 0.35) + wetR * revMix
        }

        l *= gainL
        r *= gainR

        if (nightLimit) {
            l = limitknee(l)
            r = limitknee(r)
        }

        return clipsoft(l) to clipsoft(r)
    }

    private fun limitknee(x: Double): Double = tanh(x * 1.45) * 0.82

    private fun clipsoft(x: Double): Double =
        if (x > 0.96 || x < -0.96) tanh(x) else x

    override fun onFlush() {
        bassFilter?.clear()
        combL1.fill(0f)
        combR1.fill(0f)
        combL2.fill(0f)
        combR2.fill(0f)
        apL.fill(0f)
        apR.fill(0f)
        haasBuf.fill(0f)
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
        channels = 0
        bassFilter = null
    }
}
