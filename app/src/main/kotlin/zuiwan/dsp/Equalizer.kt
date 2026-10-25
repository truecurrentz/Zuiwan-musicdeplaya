package zuiwan.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import zuiwan.model.EqProfile
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.tanh

@UnstableApi
class EqualizerProcessor : BaseAudioProcessor() {
    @Volatile
    private var profile: EqProfile = EqProfile()

    @Volatile
    private var chain: List<BiquadFilter> = emptyList()

    @Volatile
    private var preampLinear: Double = 1.0

    @Volatile
    private var dirty: Boolean = false

    private var encoding: Int = C.ENCODING_INVALID
    private var channels: Int = 0
    private var sampleRate: Int = 0

    fun applyeq(next: EqProfile) {
        profile = next
        dirty = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = enc
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        rebuild()
        return inputAudioFormat
    }

    private fun rebuild() {
        val current = profile
        preampLinear = 10.0.pow(current.preampDb.coerceIn(-18.0, 18.0) / 20.0)
        chain = if (current.enabled && sampleRate > 0 && channels > 0) {
            current.bands.map { BiquadFilter(it, sampleRate, channels) }.filterNot { it.bypass }
        } else {
            emptyList()
        }
        dirty = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (dirty) rebuild()

        val active = profile.enabled && (chain.isNotEmpty() || preampLinear != 1.0)
        val out = replaceOutputBuffer(remaining)
        if (!active || channels <= 0) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        val filters = chain
        val pre = preampLinear
        val chCount = channels

        if (encoding == C.ENCODING_PCM_16BIT) {
            var ch = 0
            while (inputBuffer.remaining() >= 2) {
                var x = inputBuffer.short.toDouble() / 32768.0 * pre
                for (f in filters) {
                    x = f.step(x, ch)
                }
                val soft = softclip(x)
                val pcm = (soft * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                out.putShort(pcm)
                ch = (ch + 1) % chCount
            }
        } else {
            var ch = 0
            while (inputBuffer.remaining() >= 4) {
                var x = inputBuffer.float.toDouble() * pre
                for (f in filters) {
                    x = f.step(x, ch)
                }
                out.putFloat(softclip(x).toFloat())
                ch = (ch + 1) % chCount
            }
        }
        out.flip()
    }

    override fun onFlush() {
        chain.forEach { it.clear() }
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
        channels = 0
        sampleRate = 0
        chain = emptyList()
    }

    private fun softclip(v: Double): Double =
        if (v > 0.96 || v < -0.96) tanh(v) else v
}
