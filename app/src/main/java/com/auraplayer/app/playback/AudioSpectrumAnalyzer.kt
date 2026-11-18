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
import kotlin.math.sqrt

/**
 * Pure-Kotlin Radix-2 FFT spectrum and waveform analyzer with perceptual frequency tilt,
 * automatic gain control (AGC), and transient beat-onset detection.
 */
class AudioSpectrumAnalyzer(
    private val fftSize: Int = 1024,
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

    private var agcPeak = 0.25f
    private var bassMovingAvg = 0.1f

    fun reset() {
        agcPeak = 0.25f
        bassMovingAvg = 0.1f
    }

    fun analyze(
        monoSamples: FloatArray,
        sampleCount: Int = monoSamples.size,
        bandCount: Int = 64
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

        val rms = sqrt(sumSquares / max(1, min(count, fftSize))).toFloat().coerceIn(0f, 1f)

        fft(real, imag)

        val halfSize = fftSize / 2
        val scale = 2.0f / fftSize
        for (i in 0 until halfSize) {
            val mag = hypot(real[i], imag[i]) * scale
            val dbScaled = (ln(1.0 + mag * 48.0) / ln(49.0)).toFloat()
            magnitudes[i] = dbScaled.coerceIn(0f, 1f)
        }

        val safeBandCount = bandCount.coerceIn(4, 128)
        val rawBands = groupIntoLogBandsWithTilt(magnitudes, safeBandCount)

        // Short-term AGC keeps quiet acoustic tracks lively and loud EDM tracks unclipped.
        val framePeak = rawBands.maxOrNull() ?: 0f
        agcPeak = max(0.08f, max(framePeak, agcPeak * 0.992f))
        val agcScale = (0.92f / agcPeak).coerceIn(0.6f, 4.5f)

        val bands = rawBands.map { (it * agcScale).coerceIn(0f, 1f) }
        val waveform = downsampleWaveform(monoSamples, count, waveformPoints)

        val bassEnd = max(1, (safeBandCount * 0.18f).toInt())
        val midEnd = max(bassEnd + 1, (safeBandCount * 0.58f).toInt())

        val bassEnergy = averageRange(bands, 0, bassEnd)
        val midEnergy = averageRange(bands, bassEnd, midEnd)
        val trebleEnergy = averageRange(bands, midEnd, bands.size)

        // Detect bass kick / transient onset above rolling bass average.
        val onsetDelta = (bassEnergy - bassMovingAvg * 1.18f).coerceAtLeast(0f)
        bassMovingAvg = bassMovingAvg * 0.88f + bassEnergy * 0.12f
        val beatPulse = (onsetDelta * 3.8f).coerceIn(0f, 1f)

        return VisualizerFrame(
            spectrumBands = bands,
            waveform = waveform,
            bassEnergy = bassEnergy,
            midEnergy = midEnergy,
            trebleEnergy = trebleEnergy,
            rmsLevel = rms,
            beatPulse = beatPulse
        )
    }

    private fun groupIntoLogBandsWithTilt(bins: FloatArray, bandCount: Int): List<Float> {
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
            var sum = 0f
            var peak = 0f
            for (b in startBin..endBin) {
                val v = bins[b]
                sum += v
                if (v > peak) peak = v
            }
            val avg = sum / (endBin - startBin + 1)
            val combined = peak * 0.65f + avg * 0.35f
            // Perceptual treble compensation so mid & high bands stay responsive alongside bass.
            val freqNorm = band.toFloat() / max(1, bandCount - 1)
            val tiltBoost = 1.0f + 1.85f * freqNorm.pow(0.85f)
            result += (combined * tiltBoost).coerceIn(0f, 1f)
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
