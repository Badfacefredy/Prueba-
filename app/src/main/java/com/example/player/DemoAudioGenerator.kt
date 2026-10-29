package com.example.player

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object DemoAudioGenerator {

    const val DEMO_TITLE = "Neon Beats (Demo)"
    const val DEMO_ARTIST = "LyricSync Studio"
    const val DEMO_ALBUM = "Cyber Synchro 2026"

    val DEFAULT_DEMO_LYRICS = """
[ti:$DEMO_TITLE]
[ar:$DEMO_ARTIST]
[al:$DEMO_ALBUM]
[by:LyricSync]
[00:02.00]Luces de neón brillan en la ciudad
[00:06.50]El ritmo comienza a despertar
[00:10.80]Sincroniza tus pasos con la señal
[00:15.20]Nada nos puede detener jamás
[00:19.40]¡Siente la música en tu corazón!
[00:23.70]Cada palabra lleva su emoción
[00:28.00]Letras que vuelan en esta canción
    """.trimIndent()

    val UNPRACTICED_DEMO_LYRICS = """
Luces de neón brillan en la ciudad
El ritmo comienza a despertar
Sincroniza tus pasos con la señal
Nada nos puede detener jamás
¡Siente la música en tu corazón!
Cada palabra lleva su emoción
Letras que vuelan en esta canción
    """.trimIndent()

    /**
     * Generates a 32-second melodic synth wave audio track as a valid PCM WAV file.
     */
    fun getOrCreateDemoAudioFile(context: Context): File {
        val file = File(context.cacheDir, "demo_song_lyricsync.wav")
        if (file.exists() && file.length() > 10000) {
            return file
        }

        val sampleRate = 22050
        val durationSeconds = 32
        val totalSamples = sampleRate * durationSeconds

        // Chord frequencies (Dm, Bb, F, C) across 4 bars repeated
        // D4, F4, A4 | Bb3, D4, F4 | F3, A3, C4 | C4, E4, G4
        val chordProgressions = listOf(
            listOf(293.66, 349.23, 440.00), // Dm
            listOf(233.08, 293.66, 349.23), // Bb
            listOf(174.61, 220.00, 261.63), // F
            listOf(261.63, 329.63, 392.00)  // C
        )
        val bassFreqs = listOf(146.83, 116.54, 87.31, 130.81)

        val pcmData = ShortArray(totalSamples)
        val barSamples = sampleRate * 4 // 4 seconds per chord

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val barIndex = (i / barSamples) % chordProgressions.size
            val chord = chordProgressions[barIndex]
            val bass = bassFreqs[barIndex]

            // Arpeggio note selector
            val noteIndex = ((t * 4).toInt()) % chord.size
            val leadFreq = chord[noteIndex]

            // Lead Synth (sine wave + soft harmonics)
            val lead = 0.35 * sin(2.0 * PI * leadFreq * t) +
                       0.15 * sin(4.0 * PI * leadFreq * t)

            // Warm Bassline
            val bassVal = 0.30 * sin(2.0 * PI * bass * t)

            // Beat: Kick drum on each second
            val beatTime = t % 1.0
            val kick = if (beatTime < 0.15) {
                val decay = 1.0 - (beatTime / 0.15)
                val kickFreq = 120.0 * decay + 40.0
                0.4 * sin(2.0 * PI * kickFreq * beatTime) * decay
            } else 0.0

            // Snare on 0.5s
            val snareTime = (t + 0.5) % 1.0
            val snare = if (snareTime < 0.1) {
                val decay = 1.0 - (snareTime / 0.1)
                0.2 * ((Math.random() * 2.0) - 1.0) * decay
            } else 0.0

            val sampleMix = (lead + bassVal + kick + snare) * 0.75
            val clamped = sampleMix.coerceIn(-1.0, 1.0)
            pcmData[i] = (clamped * 32767).toInt().toShort()
        }

        // Write WAV header and PCM data
        FileOutputStream(file).use { fos ->
            val dataSize = totalSamples * 2
            val header = createWavHeader(sampleRate, 1, 16, dataSize)
            fos.write(header)

            val byteBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmData) {
                byteBuffer.putShort(sample)
            }
            fos.write(byteBuffer.array())
        }

        return file
    }

    private fun createWavHeader(sampleRate: Int, channels: Short, bitsPerSample: Short, dataSize: Int): ByteArray {
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buf.put("RIFF".toByteArray(Charsets.ISO_8859_1))
        buf.putInt(totalDataLen)
        buf.put("WAVE".toByteArray(Charsets.ISO_8859_1))

        // fmt chunk
        buf.put("fmt ".toByteArray(Charsets.ISO_8859_1))
        buf.putInt(16) // Subchunk1Size for PCM
        buf.putShort(1) // AudioFormat 1 = PCM
        buf.putShort(channels)
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort((channels * (bitsPerSample / 8)).toShort()) // BlockAlign
        buf.putShort(bitsPerSample)

        // data chunk
        buf.put("data".toByteArray(Charsets.ISO_8859_1))
        buf.putInt(dataSize)

        return header
    }
}
