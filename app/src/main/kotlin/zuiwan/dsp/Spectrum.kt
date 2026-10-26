package zuiwan.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zuiwan.model.SpectrumFrame
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
class SpectrumProcessor : BaseAudioProcessor() {
    private val fftSize = 512
    private val bandCount = 32
    private val waveSize = 64

    private val ring = FloatArray(fftSize)
    private var ringCursor = 0
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val hann = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    private val smoothBands = FloatArray(bandCount)
    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f
    private var avgBass = 0.08f
    private var smoothBeat = 0f
    private var lastEmitMs = 0L

    private var encoding = C.ENCODING_INVALID
    private var channels = 0
    private var sampleRate = 44100

    private val frameFlow = MutableStateFlow(SpectrumFrame())
    val frame: StateFlow<SpectrumFrame> = frameFlow.asStateFlow()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = enc
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val rem = inputBuffer.remaining()
        if (rem == 0) return

        val dup = inputBuffer.duplicate().order(inputBuffer.order())
        val out = replaceOutputBuffer(rem)
        out.put(inputBuffer)
        out.flip()

        tapbuffer(dup)
    }

    private fun tapbuffer(buf: ByteBuffer) {
        val chCount = channels.coerceAtLeast(1)
        if (encoding == C.ENCODING_PCM_16BIT) {
            val frameBytes = 2 * chCount
            while (buf.remaining() >= frameBytes) {
                var mono = 0f
                for (c in 0 until chCount) {
                    mono += buf.short / 32768f
                }
                ring[ringCursor] = mono / chCount
                ringCursor = (ringCursor + 1) and (fftSize - 1)
            }
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            val frameBytes = 4 * chCount
            while (buf.remaining() >= frameBytes) {
                var mono = 0f
                for (c in 0 until chCount) {
                    mono += buf.float
                }
                ring[ringCursor] = mono / chCount
                ringCursor = (ringCursor + 1) and (fftSize - 1)
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastEmitMs >= 26L) {
            lastEmitMs = now
            computeframe(now)
        }
    }

    private fun computeframe(now: Long) {
        var sumSq = 0f
        val waveList = ArrayList<Float>(waveSize)
        val stride = fftSize / waveSize

        for (i in 0 until fftSize) {
            val s = ring[(ringCursor + i) and (fftSize - 1)].coerceIn(-1f, 1f)
            sumSq += s * s
            if (i % stride == 0 && waveList.size < waveSize) {
                waveList.add(s)
            }
            real[i] = s * hann[i]
            imag[i] = 0f
        }

        runfft(real, imag)

        val half = fftSize / 2
        val minHz = 28.0
        val maxHz = (sampleRate * 0.45).coerceAtLeast(4000.0)
        val logMin = ln(minHz)
        val logSpan = ln(maxHz) - logMin

        val outBands = ArrayList<Float>(bandCount)
        var bassAcc = 0f
        var midAcc = 0f
        var trebAcc = 0f

        for (b in 0 until bandCount) {
            val f0 = kotlin.math.exp(logMin + logSpan * (b.toDouble() / bandCount))
            val f1 = kotlin.math.exp(logMin + logSpan * ((b + 1).toDouble() / bandCount))
            val bin0 = ((f0 * fftSize) / sampleRate).toInt().coerceIn(1, half - 1)
            val bin1 = max(bin0, ((f1 * fftSize) / sampleRate).toInt().coerceIn(1, half - 1))

            var mag = 0f
            for (k in bin0..bin1) {
                mag = max(mag, hypot(real[k], imag[k]))
            }
            val tilt = (1.0 + b * 0.085).toFloat()
            val norm = ((mag * tilt) / 24f).toDouble().pow(0.62).toFloat().coerceIn(0f, 1f)
            val cur = smoothBands[b]
            val next = if (norm > cur) cur + (norm - cur) * 0.58f else cur * 0.80f
            smoothBands[b] = next
            outBands.add(next)

            when {
                b < 7 -> bassAcc += next
                b < 20 -> midAcc += next
                else -> trebAcc += next
            }
        }

        val rawBass = (bassAcc / 7f).coerceIn(0f, 1f)
        val rawMid = (midAcc / 13f).coerceIn(0f, 1f)
        val rawTreb = (trebAcc / 12f).coerceIn(0f, 1f)

        smoothBass = smoothBass * 0.45f + rawBass * 0.55f
        smoothMid = smoothMid * 0.50f + rawMid * 0.50f
        smoothTreble = smoothTreble * 0.50f + rawTreb * 0.50f

        avgBass = avgBass * 0.93f + rawBass * 0.07f
        val delta = (rawBass - avgBass * 1.18f).coerceAtLeast(0f) * 3.4f
        smoothBeat = max(delta.coerceIn(0f, 1f), smoothBeat * 0.78f)

        val rms = sqrt(sumSq / fftSize).coerceIn(0f, 1f)

        frameFlow.value = SpectrumFrame(
            bands = outBands,
            wave = waveList,
            bass = smoothBass,
            mid = smoothMid,
            treble = smoothTreble,
            rms = rms,
            beat = smoothBeat,
            stamp = now,
        )
    }

    private fun runfft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlenRe = cos(ang).toFloat()
            val wlenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                val half = len shr 1
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + half] * wRe - im[i + k + half] * wIm
                    val vIm = re[i + k + half] * wIm + im[i + k + half] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe
                    im[i + k + half] = uIm - vIm
                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun decayframe() {
        var anyActive = false
        val outBands = ArrayList<Float>(bandCount)
        for (b in 0 until bandCount) {
            smoothBands[b] *= 0.72f
            if (smoothBands[b] > 0.005f) anyActive = true else smoothBands[b] = 0f
            outBands.add(smoothBands[b])
        }
        smoothBass *= 0.72f
        smoothMid *= 0.72f
        smoothTreble *= 0.72f
        smoothBeat *= 0.70f
        frameFlow.value = if (anyActive) {
            SpectrumFrame(
                bands = outBands,
                bass = smoothBass,
                mid = smoothMid,
                treble = smoothTreble,
                beat = smoothBeat,
                stamp = System.currentTimeMillis(),
            )
        } else {
            SpectrumFrame()
        }
    }

    override fun onFlush() {
        ring.fill(0f)
    }

    override fun onReset() {
        ring.fill(0f)
        smoothBands.fill(0f)
        frameFlow.value = SpectrumFrame()
    }
}
