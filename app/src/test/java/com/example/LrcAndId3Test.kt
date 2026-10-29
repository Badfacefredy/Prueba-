package com.example

import com.example.model.LrcDocument
import com.example.model.LrcLine
import com.example.model.LrcParser
import com.example.model.SongMetadata
import com.example.tagger.Id3Tagger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LrcAndId3Test {

    @Test
    fun testLrcParserAndSerializer() {
        val rawLrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [al:Test Album]
            [00:04.50]First line of song
            [01:15.80]Second line of song
        """.trimIndent()

        val doc = LrcParser.parse(rawLrc)
        assertEquals("Test Title", doc.metadata.title)
        assertEquals("Test Artist", doc.metadata.artist)
        assertEquals("Test Album", doc.metadata.album)
        assertEquals(2, doc.lines.size)

        assertEquals(4500L, doc.lines[0].timestampMs)
        assertEquals("First line of song", doc.lines[0].text)

        assertEquals(75800L, doc.lines[1].timestampMs)
        assertEquals("Second line of song", doc.lines[1].text)

        val serialized = doc.toLrcString()
        assertTrue(serialized.contains("[ti:Test Title]"))
        assertTrue(serialized.contains("[00:04.50]First line of song"))
        assertTrue(serialized.contains("[01:15.80]Second line of song"))
    }

    @Test
    fun testId3TagEmbeddingAndReading() {
        // Mock MP3 MPEG frame bytes (0xFF 0xFB ...)
        val mockMp3Audio = ByteArray(512) { 0x55.toByte() }
        mockMp3Audio[0] = 0xFF.toByte()
        mockMp3Audio[1] = 0xFB.toByte()

        val doc = LrcDocument(
            metadata = SongMetadata(title = "Cancion Viva", artist = "Artista Demo", album = "Album Studio"),
            lines = listOf(
                LrcLine(timestampMs = 2500L, text = "Letra linea 1"),
                LrcLine(timestampMs = 6000L, text = "Letra linea 2")
            )
        )

        val taggedBytes = Id3Tagger.embedLyricsIntoMp3(mockMp3Audio, doc)
        assertTrue(taggedBytes.size > mockMp3Audio.size)

        // Verify ID3 magic header
        assertEquals(0x49.toByte(), taggedBytes[0]) // 'I'
        assertEquals(0x44.toByte(), taggedBytes[1]) // 'D'
        assertEquals(0x33.toByte(), taggedBytes[2]) // '3'
        assertEquals(0x03.toByte(), taggedBytes[3]) // Version 2.3

        // Now read back tags from taggedBytes
        val extracted = Id3Tagger.readExistingTags(ByteArrayInputStream(taggedBytes))
        assertTrue(extracted.hasExistingId3)
        assertEquals("Cancion Viva", extracted.metadata.title)
        assertEquals("Artista Demo", extracted.metadata.artist)
        assertEquals("Album Studio", extracted.metadata.album)
        assertNotNull(extracted.embeddedLyrics)
        assertTrue(extracted.embeddedLyrics!!.contains("Letra linea 1"))
    }

    @Test
    fun testAudioBeatAnalyzer() {
        val syntheticAudio = ByteArray(4000) { (it % 128).toByte() }
        val profile = com.example.ai.AudioBeatAnalyzer.analyzeAudioBytes(syntheticAudio, 30000L)
        assertTrue(profile.estimatedBpm in 60..180)
        assertNotNull(profile.peakMarkersSummary)
    }

    @Test
    fun testComprehensiveLrcMetadata() {
        val meta = SongMetadata(
            title = "Mi Cancion",
            artist = "Mi Artista",
            album = "Mi Album",
            author = "Mi Letrista",
            creator = "LyricSync Pro",
            length = "03:45",
            offsetMs = 150L
        )
        val doc = LrcDocument(metadata = meta)
        val lrc = doc.toLrcString()
        assertTrue(lrc.contains("[ti:Mi Cancion]"))
        assertTrue(lrc.contains("[ar:Mi Artista]"))
        assertTrue(lrc.contains("[al:Mi Album]"))
        assertTrue(lrc.contains("[au:Mi Letrista]"))
        assertTrue(lrc.contains("[by:LyricSync Pro]"))
        assertTrue(lrc.contains("[length:03:45]"))
        assertTrue(lrc.contains("[offset:150]"))

        val parsed = LrcParser.parse(lrc)
        assertEquals("Mi Cancion", parsed.metadata.title)
        assertEquals("Mi Artista", parsed.metadata.artist)
        assertEquals("Mi Album", parsed.metadata.album)
        assertEquals("Mi Letrista", parsed.metadata.author)
        assertEquals("LyricSync Pro", parsed.metadata.creator)
        assertEquals("03:45", parsed.metadata.length)
        assertEquals(150L, parsed.metadata.offsetMs)
    }
}
