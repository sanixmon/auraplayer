package com.auraplayer.app.playback

import com.auraplayer.app.data.model.VisualizerFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure-Kotlin Radix-2 FFT spectrum and waveform analyzer for offline PCM audio buffers.
 */
class AudioSpectrumAnalyzer(
    private val fftSize: Int = 512,
    private val waveformPoints: Int = 64
) {
    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) {
            "fftSize must be a positive power of 2"
        }
        require(waveformPoints > 0) {
            "waveformPoints must be positive"
        }
    }

    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val magnitudes = FloatArray(fftSize / 2)

    fun analyze(
        monoSamples: FloatArray,
        sampleCount: Int = monoSamples.size,
        bandCount: Int = 48
    ): VisualizerFrame {
        if (sampleCount <= 0 || monoSamples.isEmpty()) {
            return VisualizerFrame.EMPTY
        }

        val count = min(sampleCount, monoSamples.size)
        var sumSquares = 0.0

        for (i in 0 until fftSize) {
            val sample = if (i < count) monoSamples[i].coerceIn(-1f, 1f) else 0f
            if (i < count) {
                sumSquares += sample * sample
            }
            real[i] = sample * hannWindow[i]
            imag[i] = 0f
        }

        val rms = kotlin.math.sqrt(sumSquares / max(1, min(count, fftSize))).toFloat().coerceIn(0f, 1f)

        fft(real, imag)

        val halfSize = fftSize / 2
        val scale = 2.0f / fftSize
        for (i in 0 until halfSize) {
            val mag = hypot(real[i], imag[i]) * scale
            // Convert magnitude to a perceptually compressed 0..1 curve.
            val dbScaled = (ln(1.0 + mag * 32.0) / ln(33.0)).toFloat()
            magnitudes[i] = dbScaled.coerceIn(0f, 1f)
        }

        val safeBandCount = bandCount.coerceIn(4, 128)
        val bands = groupIntoLogBands(magnitudes, safeBandCount)
        val waveform = downsampleWaveform(monoSamples, count, waveformPoints)

        val bassEnd = max(1, (safeBandCount * 0.2f).toInt())
        val midEnd = max(bassEnd + 1, (safeBandCount * 0.6f).toInt())

        val bassEnergy = averageRange(bands, 0, bassEnd)
        val midEnergy = averageRange(bands, bassEnd, midEnd)
        val trebleEnergy = averageRange(bands, midEnd, bands.size)

        return VisualizerFrame(
            spectrumBands = bands,
            waveform = waveform,
            bassEnergy = bassEnergy,
            midEnergy = midEnergy,
            trebleEnergy = trebleEnergy,
            rmsLevel = rms
        )
    }

    private fun groupIntoLogBands(bins: FloatArray, bandCount: Int): List<Float> {
        val result = ArrayList<Float>(bandCount)
        val usableBins = bins.size - 1
        if (usableBins <= 1) {
            repeat(bandCount) { result += 0f }
            return result
        }

        val minIndex = 1.0
        val maxIndex = usableBins.toDouble()

        for (band in 0 until bandCount) {
            val t0 = band.toDouble() / bandCount
            val t1 = (band + 1).toDouble() / bandCount
            val startBin = (minIndex * (maxIndex / minIndex).pow(t0)).toInt().coerceIn(1, usableBins)
            val endBin = max(
                startBin,
                (minIndex * (maxIndex / minIndex).pow(t1)).toInt().coerceIn(startBin, usableBins)
            )
            var peak = 0f
            for (b in startBin..endBin) {
                if (bins[b] > peak) peak = bins[b]
            }
            result += peak.coerceIn(0f, 1f)
        }
        return result
    }

    private fun downsampleWaveform(
        samples: FloatArray,
        sampleCount: Int,
        targetPoints: Int
    ): List<Float> {
        val out = ArrayList<Float>(targetPoints)
        val step = max(1f, sampleCount.toFloat() / targetPoints)
        for (i in 0 until targetPoints) {
            val idx = (i * step).toInt().coerceIn(0, sampleCount - 1)
            out += samples[idx].coerceIn(-1f, 1f)
        }
        return out
    }

    private fun averageRange(values: List<Float>, fromInclusive: Int, toExclusive: Int): Float {
        if (fromInclusive >= toExclusive || fromInclusive >= values.size) return 0f
        val end = min(toExclusive, values.size)
        var sum = 0f
        for (i in fromInclusive until end) {
            sum += values[i]
        }
        return (sum / (end - fromInclusive)).coerceIn(0f, 1f)
    }

    private fun fft(re: FloatArray, im: FloatArray) {
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
                val tmpRe = re[i]
                re[i] = re[j]
                re[j] = tmpRe

                val tmpIm = im[i]
                im[i] = im[j]
                im[j] = tmpIm
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenRe = cos(ang).toFloat()
            val wLenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                val halfLen = len shr 1
                for (k in 0 until halfLen) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + halfLen] * wRe - im[i + k + halfLen] * wIm
                    val vIm = re[i + k + halfLen] * wIm + im[i + k + halfLen] * wRe

                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + halfLen] = uRe - vRe
                    im[i + k + halfLen] = uIm - vIm

                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
