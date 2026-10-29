package com.example.ai

import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.sqrt

object AudioBeatAnalyzer {

    data class BeatProfile(
        val estimatedBpm: Int,
        val peakMarkersSummary: String,
        val averageEnergy: Double
    )

    /**
     * Analyzes audio bytes to extract rhythmic pulses and peak energy timestamps.
     */
    fun analyzeAudioBytes(audioBytes: ByteArray, durationMs: Long): BeatProfile {
        if (audioBytes.size < 1000 || durationMs <= 0) {
            return BeatProfile(120, "Audio no procesable", 0.0)
        }

        // Sample audio energy across 30-50 windows
        val windowCount = 40.coerceAtMost((durationMs / 500).toInt().coerceAtLeast(10))
        val windowBytes = audioBytes.size / windowCount
        val energies = DoubleArray(windowCount)

        for (w in 0 until windowCount) {
            val start = w * windowBytes
            val end = ((w + 1) * windowBytes).coerceAtMost(audioBytes.size)
            var sumSquare = 0.0
            var count = 0
            var i = start
            while (i + 1 < end) {
                // Approximate 16-bit PCM sample
                val sample = (audioBytes[i].toInt() and 0xFF) or (audioBytes[i + 1].toInt() shl 8)
                sumSquare += (sample * sample).toDouble()
                count++
                i += 4 // Sample step
            }
            energies[w] = if (count > 0) sqrt(sumSquare / count) else 0.0
        }

        val avgEnergy = energies.average()
        val peaks = mutableListOf<String>()

        for (w in energies.indices) {
            val timeMs = (w * durationMs) / windowCount
            if (energies[w] > avgEnergy * 1.15) {
                val sec = timeMs / 1000
                val hundredths = (timeMs % 1000) / 10
                val min = sec / 60
                val s = sec % 60
                val timeStr = String.format(Locale.US, "%02d:%02d.%02d", min, s, hundredths)
                peaks.add("$timeStr (Rhythm Peak)")
            }
        }

        val estimatedBpm = if (peaks.size > 2) {
            val beatsPerSec = (peaks.size.toDouble() / (durationMs / 1000.0))
            (beatsPerSec * 60).toInt().coerceIn(60, 180)
        } else {
            120
        }

        val summary = peaks.take(15).joinToString(", ")
        return BeatProfile(
            estimatedBpm = estimatedBpm,
            peakMarkersSummary = summary.ifBlank { "Transiciones suaves distribuidas uniformemente" },
            averageEnergy = avgEnergy
        )
    }
}
