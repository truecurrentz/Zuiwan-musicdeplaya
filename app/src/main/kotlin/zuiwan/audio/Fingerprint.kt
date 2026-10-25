package zuiwan.audio

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object AudioPrint {
    const val targetRate = 16_000
    private const val fftSize = 2048
    private const val fftBins = fftSize / 2 + 1
    private const val maxPeaks = 255
    private const val maxSeconds = 12.0
    private const val ringSize = 256

    private val hanning = DoubleArray(fftSize) { i ->
        0.5 * (1.0 - cos(2.0 * PI * (i + 1).toDouble() / 2049.0))
    }

    suspend fun resamplepcm(
        rawBytes: ByteArray,
        inputRate: Int,
        outputRate: Int = targetRate,
    ): ByteArray = withContext(Dispatchers.Default) {
        if (rawBytes.size < 2) return@withContext ByteArray(0)
        if (inputRate == outputRate) return@withContext rawBytes
        val count = rawBytes.size / 2
        val inSamples = ShortArray(count)
        ByteBuffer.wrap(rawBytes, 0, count * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inSamples)

        val ratio = outputRate.toDouble() / inputRate.toDouble()
        val outLen = (count * ratio).toInt()
        if (outLen <= 0) return@withContext ByteArray(0)

        val outSamples = ShortArray(outLen)
        val cutoff = 0.465 * ratio.coerceAtMost(1.0)
        val halfWin = 12

        for (i in 0 until outLen) {
            if (i and 1023 == 0) ensureActive()
            val center = i / ratio
            val cInt = center.toInt()
            var acc = 0.0
            var wSum = 0.0
            val start = (cInt - halfWin).coerceAtLeast(0)
            val end = (cInt + halfWin).coerceAtMost(count - 1)
            for (k in start..end) {
                val x = k - center
                val sinc = if (abs(x) < 1e-7) {
                    2.0 * cutoff
                } else {
                    sin(2.0 * PI * cutoff * x) / (PI * x)
                }
                val winPos = (x + halfWin) / (2.0 * halfWin)
                val hamming = if (winPos in 0.0..1.0) {
                    0.54 - 0.46 * cos(2.0 * PI * winPos)
                } else {
                    0.0
                }
                val w = sinc * hamming
                acc += inSamples[k] * w
                wSum += w
            }
            val filtered = if (abs(wSum) > 1e-9) acc / wSum else 0.0
            outSamples[i] = filtered.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val outBytes = ByteArray(outLen * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(outSamples)
        outBytes
    }

    fun makesig(pcmBytes: ByteArray): String {
        val evenLen = pcmBytes.size and -2
        require(evenLen >= 256) { "Audio sample too short" }
        val raw = ShortArray(evenLen / 2)
        ByteBuffer.wrap(pcmBytes, 0, evenLen).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(raw)
        val pcm = cleanpcm(raw)
        return SigState().runpass(pcm)
    }

    private fun cleanpcm(input: ShortArray): ShortArray {
        if (input.isEmpty()) return input
        val hp = DoubleArray(input.size)
        val alpha = 0.966
        var prevIn = input[0].toDouble()
        var prevOut = 0.0
        var sumSq = 0.0

        for (i in input.indices) {
            val cur = input[i].toDouble()
            val y = alpha * (prevOut + cur - prevIn)
            prevIn = cur
            prevOut = y
            hp[i] = y
            sumSq += y * y
        }

        val rms = sqrt(sumSq / input.size)
        val gain = if (rms > 18.0) {
            (5800.0 / rms).coerceIn(1.0, 24.0)
        } else {
            1.0
        }

        return ShortArray(input.size) { i ->
            val scaled = hp[i] * gain
            val soft = when {
                scaled > 29000.0 -> 29000.0 + (scaled - 29000.0) * 0.18
                scaled < -29000.0 -> -29000.0 + (scaled + 29000.0) * 0.18
                else -> scaled
            }
            soft.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private data class PeakPoint(
        val passNum: Int,
        val magnitude: Int,
        val bin: Int,
    )

    private class SigState {
        private val sampleRing = IntArray(fftSize)
        private var samplePos = 0

        private val fftRing = Array(ringSize) { DoubleArray(fftBins) }
        private var fftPos = 0
        private var fftCount = 0

        private val spreadRing = Array(ringSize) { DoubleArray(fftBins) }
        private var spreadPos = 0
        private var spreadCount = 0

        private var numSamples = 0
        private val bandPeaks = Array(4) { mutableListOf<PeakPoint>() }
        private var totalPeaks = 0

        fun runpass(pcm: ShortArray): String {
            var offset = 0
            while (offset + 128 <= pcm.size) {
                val elapsed = numSamples.toDouble() / targetRate
                if (elapsed >= maxSeconds && totalPeaks >= maxPeaks) break
                numSamples += 128
                feedsamples(pcm, offset, 128)
                stepfft()
                spreadpeaks()
                if (spreadCount >= 47) {
                    findpeaks()
                }
                offset += 128
            }
            return encodesig()
        }

        private fun feedsamples(pcm: ShortArray, start: Int, count: Int) {
            for (k in start until start + count) {
                sampleRing[samplePos] = pcm[k].toInt()
                samplePos = (samplePos + 1) % fftSize
            }
        }

        private fun stepfft() {
            val windowed = DoubleArray(fftSize) { i ->
                sampleRing[(samplePos + i) % fftSize].toDouble() * hanning[i]
            }
            val spectrum = realfft(windowed)
            spectrum.copyInto(fftRing[fftPos])
            fftPos = (fftPos + 1) % ringSize
            fftCount++
        }

        private fun spreadpeaks() {
            val lastIdx = (fftPos - 1 + ringSize) % ringSize
            val spread = fftRing[lastIdx].copyOf()
            for (pos in 0 until fftBins - 2) {
                spread[pos] = maxOf(spread[pos], spread[pos + 1], spread[pos + 2])
            }
            for (pos in 0 until fftBins) {
                var maxVal = spread[pos]
                for (off in intArrayOf(-1, -3, -6)) {
                    val idx = ((spreadPos + off) % ringSize + ringSize) % ringSize
                    val prev = spreadRing[idx][pos]
                    if (prev > maxVal) maxVal = prev
                    spreadRing[idx][pos] = maxVal
                }
            }
            spread.copyInto(spreadRing[spreadPos])
            spreadPos = (spreadPos + 1) % ringSize
            spreadCount++
        }

        private fun findpeaks() {
            val fft46 = fftRing[(fftPos - 46 + ringSize * 2) % ringSize]
            val spread49 = spreadRing[(spreadPos - 49 + ringSize * 2) % ringSize]
            val otherOffsets = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

            for (binPos in 10 until fftBins - 8) {
                val fftVal = fft46[binPos]
                if (fftVal < 1.0 / 64.0 || fftVal < spread49[binPos]) continue

                var maxNeighbor49 = 0.0
                for (nOff in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) {
                    val v = spread49[binPos + nOff]
                    if (v > maxNeighbor49) maxNeighbor49 = v
                }
                if (fftVal <= maxNeighbor49) continue

                var maxOther = maxNeighbor49
                for (oOff in otherOffsets) {
                    val sIdx = ((spreadPos + oOff) % ringSize + ringSize) % ringSize
                    val v = spreadRing[sIdx][binPos - 1]
                    if (v > maxOther) maxOther = v
                }
                if (fftVal <= maxOther) continue

                val fftNum = spreadCount - 46
                val mag = ln(max(1.0 / 64.0, fftVal)) * 1477.3 + 6144.0
                val magPrev = ln(max(1.0 / 64.0, fft46[binPos - 1])) * 1477.3 + 6144.0
                val magNext = ln(max(1.0 / 64.0, fft46[binPos + 1])) * 1477.3 + 6144.0

                val var1 = mag * 2.0 - magPrev - magNext
                val var2 = if (var1 != 0.0) (magNext - magPrev) * 32.0 / var1 else 0.0
                val correctedBin = binPos * 64.0 + var2
                val freqHz = correctedBin * (16000.0 / 2.0 / 1024.0 / 64.0)

                val band = when {
                    freqHz < 250.0 -> continue
                    freqHz < 520.0 -> 0
                    freqHz < 1450.0 -> 1
                    freqHz < 3500.0 -> 2
                    freqHz <= 5500.0 -> 3
                    else -> continue
                }

                bandPeaks[band].add(
                    PeakPoint(
                        passNum = fftNum,
                        magnitude = mag.toInt(),
                        bin = correctedBin.toInt(),
                    ),
                )
                totalPeaks++
            }
        }

        private fun encodesig(): String {
            val contentOut = ByteArrayOutputStream()
            for (bandId in 0..3) {
                val peaks = bandPeaks[bandId]
                if (peaks.isEmpty()) continue
                val peakBuf = ByteArrayOutputStream()
                var prevPass = 0
                for (p in peaks) {
                    val diff = p.passNum - prevPass
                    if (diff >= 255) {
                        peakBuf.write(0xFF)
                        putle32(peakBuf, p.passNum)
                        prevPass = p.passNum
                    }
                    peakBuf.write(p.passNum - prevPass)
                    putle16(peakBuf, p.magnitude)
                    putle16(peakBuf, p.bin)
                    prevPass = p.passNum
                }
                val peakBytes = peakBuf.toByteArray()
                putle32(contentOut, 0x60030040 + bandId)
                putle32(contentOut, peakBytes.size)
                contentOut.write(peakBytes)
                val pad = (4 - peakBytes.size % 4) % 4
                repeat(pad) { contentOut.write(0) }
            }

            val contents = contentOut.toByteArray()
            val sizeMinusHeader = contents.size + 8
            val samplesPlusOffset = (numSamples + targetRate * 0.24).toInt()

            val headerBytes = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0xcafe2580.toInt())
                putInt(0)
                putInt(sizeMinusHeader)
                putInt(0x94119c00.toInt())
                putInt(0)
                putInt(0)
                putInt(0)
                putInt(3 shl 27)
                putInt(0)
                putInt(0)
                putInt(samplesPlusOffset)
                putInt((15 shl 19) + 0x40000)
            }.array()

            val fullBuf = ByteArrayOutputStream(56 + contents.size)
            fullBuf.write(headerBytes)
            putle32(fullBuf, 0x40000000)
            putle32(fullBuf, contents.size + 8)
            fullBuf.write(contents)

            val fullBytes = fullBuf.toByteArray()
            val crc = CRC32()
            crc.update(fullBytes, 8, fullBytes.size - 8)
            val crcVal = crc.value.toInt()
            fullBytes[4] = (crcVal and 0xFF).toByte()
            fullBytes[5] = ((crcVal shr 8) and 0xFF).toByte()
            fullBytes[6] = ((crcVal shr 16) and 0xFF).toByte()
            fullBytes[7] = ((crcVal shr 24) and 0xFF).toByte()

            val b64 = Base64.encodeToString(fullBytes, Base64.NO_WRAP)
            return "data:audio/vnd.shazam.sig;base64,$b64"
        }
    }

    private fun putle32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun putle16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun realfft(windowed: DoubleArray): DoubleArray {
        val n = windowed.size
        val re = windowed.copyOf()
        val im = DoubleArray(n)

        var j = 0
        for (i in 1 until n) {
            var bit = n ushr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit ushr 1
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
            val half = len ushr 1
            val ang = -PI / half
            val wbRe = cos(ang)
            val wbIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until half) {
                    val u = i + k
                    val v = u + half
                    val eRe = re[u]
                    val eIm = im[u]
                    val oRe = re[v] * wRe - im[v] * wIm
                    val oIm = re[v] * wIm + im[v] * wRe
                    re[u] = eRe + oRe
                    im[u] = eIm + oIm
                    re[v] = eRe - oRe
                    im[v] = eIm - oIm
                    val nextWRe = wRe * wbRe - wIm * wbIm
                    wIm = wRe * wbIm + wIm * wbRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }

        val scale = 1.0 / (1 shl 17)
        val floor = 1e-10
        return DoubleArray(fftBins) { idx ->
            val r = re[idx]
            val img = im[idx]
            val mag = (r * r + img * img) * scale
            if (mag < floor) floor else mag
        }
    }
}
