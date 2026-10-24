package zuiwan.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.tanh

@UnstableApi
class NormalizerProcessor : BaseAudioProcessor() {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var gainLinear: Double = 1.0

    private var encoding: Int = C.ENCODING_INVALID

    fun setloudness(loudnessDb: Double?, perceptualDb: Double?) {
        val gainDb = when {
            perceptualDb != null -> (-14.0 - perceptualDb).coerceIn(-12.0, 9.0)
            loudnessDb != null -> (-loudnessDb).coerceIn(-12.0, 9.0)
            else -> 0.0
        }
        gainLinear = 10.0.pow(gainDb / 20.0)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = enc
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val gain = gainLinear
        val out = replaceOutputBuffer(remaining)
        if (!enabled || gain in 0.995..1.005) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        if (encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.remaining() >= 2) {
                val norm = (inputBuffer.short.toDouble() / 32768.0) * gain
                val limited = if (norm > 0.97 || norm < -0.97) tanh(norm) else norm
                out.putShort((limited * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                val norm = inputBuffer.float.toDouble() * gain
                val limited = if (norm > 0.97 || norm < -0.97) tanh(norm) else norm
                out.putFloat(limited.toFloat())
            }
        }
        out.flip()
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
    }
}
