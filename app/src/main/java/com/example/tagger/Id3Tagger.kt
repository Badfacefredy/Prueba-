package com.example.tagger

import com.example.model.LrcDocument
import com.example.model.SongMetadata
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin ID3v2.3 / ID3v2.4 parser and writer for embedding synchronized lyrics
 * (USLT, SYLT, TXXX:LYRICS) into MP3 audio files.
 */
object Id3Tagger {

    data class ExtractedTags(
        val metadata: SongMetadata,
        val embeddedLyrics: String? = null,
        val hasExistingId3: Boolean = false,
        val id3Size: Int = 0
    )

    /**
     * Reads existing ID3v2 tags from an audio input stream.
     */
    fun readExistingTags(inputStream: InputStream): ExtractedTags {
        val header = ByteArray(10)
        val read = inputStream.read(header)
        if (read < 10 || header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) {
            return ExtractedTags(SongMetadata(), hasExistingId3 = false)
        }

        val majorVersion = header[3].toInt() and 0xFF
        val synchsafeSize = readSynchSafeInt(header, 6)
        val tagData = ByteArray(synchsafeSize)
        var totalRead = 0
        while (totalRead < synchsafeSize) {
            val count = inputStream.read(tagData, totalRead, synchsafeSize - totalRead)
            if (count <= 0) break
            totalRead += count
        }

        var title = ""
        var artist = ""
        var album = ""
        var lyrics: String? = null

        var offset = 0
        while (offset + 10 <= tagData.size) {
            val frameId = String(tagData, offset, 4, Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' } || !frameId.all { it in 'A'..'Z' || it in '0'..'9' }) {
                break
            }

            val frameSize = if (majorVersion == 4) {
                readSynchSafeInt(tagData, offset + 4)
            } else {
                readInt32(tagData, offset + 4)
            }

            offset += 10
            if (frameSize <= 0 || offset + frameSize > tagData.size) {
                break
            }

            val frameData = tagData.copyOfRange(offset, offset + frameSize)
            offset += frameSize

            when (frameId) {
                "TIT2" -> title = decodeTextFrame(frameData)
                "TPE1" -> artist = decodeTextFrame(frameData)
                "TALB" -> album = decodeTextFrame(frameData)
                "USLT" -> lyrics = decodeUsltFrame(frameData)
                "TXXX" -> {
                    val (desc, value) = decodeTxxxFrame(frameData)
                    if (desc.equals("LYRICS", ignoreCase = true) || desc.equals("UNSYNCEDLYRICS", ignoreCase = true)) {
                        lyrics = value
                    }
                }
            }
        }

        return ExtractedTags(
            metadata = SongMetadata(title = title, artist = artist, album = album),
            embeddedLyrics = lyrics,
            hasExistingId3 = true,
            id3Size = 10 + synchsafeSize
        )
    }

    /**
     * Embeds the LRC document into the MP3 file as ID3v2.3 tags:
     * - USLT with full LRC text (standard for most players)
     * - SYLT with millisecond timestamps
     * - TXXX:LYRICS with raw LRC
     * - TIT2, TPE1, TALB
     */
    fun embedLyricsIntoMp3(
        originalMp3Bytes: ByteArray,
        lrcDocument: LrcDocument
    ): ByteArray {
        val audioStartOffset = calculateAudioStartOffset(originalMp3Bytes)

        val framesStream = ByteArrayOutputStream()

        // 1. Title frame (TIT2)
        if (lrcDocument.metadata.title.isNotBlank()) {
            writeFrame(framesStream, "TIT2", encodeTextFrame(lrcDocument.metadata.title))
        }

        // 2. Artist frame (TPE1)
        if (lrcDocument.metadata.artist.isNotBlank()) {
            writeFrame(framesStream, "TPE1", encodeTextFrame(lrcDocument.metadata.artist))
        }

        // 3. Album frame (TALB)
        if (lrcDocument.metadata.album.isNotBlank()) {
            writeFrame(framesStream, "TALB", encodeTextFrame(lrcDocument.metadata.album))
        }

        // 4. USLT frame (Unsynchronized lyrics/text with timestamped LRC content)
        val lrcString = lrcDocument.toLrcString()
        writeFrame(framesStream, "USLT", encodeUsltFrame(lrcString))

        // 5. TXXX:LYRICS frame
        writeFrame(framesStream, "TXXX", encodeTxxxFrame("LYRICS", lrcString))

        // 6. SYLT frame (Synchronized lyrics ID3v2.3)
        val syltBytes = encodeSyltFrame(lrcDocument)
        if (syltBytes.isNotEmpty()) {
            writeFrame(framesStream, "SYLT", syltBytes)
        }

        val allFrames = framesStream.toByteArray()
        val tagHeader = ByteArrayOutputStream()

        // ID3v2.3 header (10 bytes)
        tagHeader.write("ID3".toByteArray(Charsets.ISO_8859_1))
        tagHeader.write(3) // version 2.3
        tagHeader.write(0) // revision 0
        tagHeader.write(0) // flags

        // Size in synchsafe integer
        val synchSafeSize = toSynchSafeBytes(allFrames.size)
        tagHeader.write(synchSafeSize)

        // Combine new ID3v2 tag + raw audio data
        val result = ByteArrayOutputStream(tagHeader.size() + allFrames.size + (originalMp3Bytes.size - audioStartOffset))
        result.write(tagHeader.toByteArray())
        result.write(allFrames)
        if (originalMp3Bytes.size > audioStartOffset) {
            result.write(originalMp3Bytes, audioStartOffset, originalMp3Bytes.size - audioStartOffset)
        }

        return result.toByteArray()
    }

    /**
     * Determines where the actual audio frames start by checking for existing ID3v2 header.
     */
    fun calculateAudioStartOffset(mp3Bytes: ByteArray): Int {
        if (mp3Bytes.size >= 10 &&
            mp3Bytes[0] == 0x49.toByte() &&
            mp3Bytes[1] == 0x44.toByte() &&
            mp3Bytes[2] == 0x33.toByte()
        ) {
            val size = readSynchSafeInt(mp3Bytes, 6)
            return (10 + size).coerceAtMost(mp3Bytes.size)
        }
        return 0
    }

    private fun writeFrame(out: ByteArrayOutputStream, frameId: String, data: ByteArray) {
        out.write(frameId.toByteArray(Charsets.ISO_8859_1))
        // 4-byte size (big endian)
        val sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array()
        out.write(sizeBuf)
        // 2-byte flags (0x00 0x00)
        out.write(0)
        out.write(0)
        out.write(data)
    }

    private fun encodeTextFrame(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x01) // Encoding: UTF-16 with BOM
        out.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) // UTF-16BE BOM
        out.write(text.toByteArray(Charsets.UTF_16BE))
        return out.toByteArray()
    }

    private fun decodeTextFrame(data: ByteArray): String {
        if (data.size <= 1) return ""
        val encoding = data[0].toInt()
        return when (encoding) {
            0x00 -> String(data, 1, data.size - 1, Charsets.ISO_8859_1).trimEnd('\u0000')
            0x01 -> String(data, 1, data.size - 1, Charsets.UTF_16).trimEnd('\u0000')
            0x02 -> String(data, 1, data.size - 1, Charsets.UTF_16BE).trimEnd('\u0000')
            0x03 -> String(data, 1, data.size - 1, Charsets.UTF_8).trimEnd('\u0000')
            else -> String(data, 1, data.size - 1, Charsets.ISO_8859_1).trimEnd('\u0000')
        }
    }

    private fun encodeUsltFrame(lyricsText: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x01) // UTF-16 with BOM
        out.write("eng".toByteArray(Charsets.ISO_8859_1)) // Language
        // Descriptor: empty UTF-16 string with null terminator: BOM + 0x00 0x00
        out.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0x00))
        // Lyrics content
        out.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
        out.write(lyricsText.toByteArray(Charsets.UTF_16BE))
        return out.toByteArray()
    }

    private fun decodeUsltFrame(data: ByteArray): String {
        if (data.size < 5) return ""
        val encoding = data[0].toInt()
        // Skip encoding (1) + language (3) = 4 bytes
        var pos = 4
        // Find descriptor termination
        if (encoding == 0x01 || encoding == 0x02) {
            // 2-byte null terminator
            while (pos + 1 < data.size) {
                if (data[pos] == 0x00.toByte() && data[pos + 1] == 0x00.toByte()) {
                    pos += 2
                    break
                }
                pos += 2
            }
        } else {
            while (pos < data.size && data[pos] != 0x00.toByte()) {
                pos++
            }
            pos++
        }

        if (pos >= data.size) return ""
        val lyricsBytes = data.copyOfRange(pos, data.size)
        return when (encoding) {
            0x01 -> String(lyricsBytes, Charsets.UTF_16).trimEnd('\u0000')
            0x02 -> String(lyricsBytes, Charsets.UTF_16BE).trimEnd('\u0000')
            0x03 -> String(lyricsBytes, Charsets.UTF_8).trimEnd('\u0000')
            else -> String(lyricsBytes, Charsets.ISO_8859_1).trimEnd('\u0000')
        }
    }

    private fun encodeTxxxFrame(description: String, value: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x01) // UTF-16 with BOM
        out.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
        out.write(description.toByteArray(Charsets.UTF_16BE))
        out.write(byteArrayOf(0x00, 0x00)) // UTF-16 null delimiter
        out.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
        out.write(value.toByteArray(Charsets.UTF_16BE))
        return out.toByteArray()
    }

    private fun decodeTxxxFrame(data: ByteArray): Pair<String, String> {
        if (data.size <= 1) return "" to ""
        val encoding = data[0].toInt()
        var pos = 1
        var descEnd = pos
        if (encoding == 0x01 || encoding == 0x02) {
            while (pos + 1 < data.size) {
                if (data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                    descEnd = pos
                    pos += 2
                    break
                }
                pos += 2
            }
        } else {
            while (pos < data.size && data[pos] != 0.toByte()) {
                pos++
            }
            descEnd = pos
            pos++
        }
        val descBytes = data.copyOfRange(1, descEnd.coerceAtMost(data.size))
        val valBytes = if (pos < data.size) data.copyOfRange(pos, data.size) else ByteArray(0)

        val charset = if (encoding == 0x01) Charsets.UTF_16 else if (encoding == 0x03) Charsets.UTF_8 else Charsets.ISO_8859_1
        return String(descBytes, charset).trimEnd('\u0000') to String(valBytes, charset).trimEnd('\u0000')
    }

    private fun encodeSyltFrame(doc: LrcDocument): ByteArray {
        val syncedLines = doc.lines.filter { it.isSynced }
        if (syncedLines.isEmpty()) return ByteArray(0)

        val out = ByteArrayOutputStream()
        out.write(0x01) // Encoding: UTF-16 with BOM
        out.write("eng".toByteArray(Charsets.ISO_8859_1)) // Language: 3 bytes
        out.write(0x02) // Time stamp format: 2 = milliseconds (32-bit int)
        out.write(0x01) // Content type: 1 = lyrics
        // Content descriptor: null-terminated UTF-16 string (BOM + 0x00 0x00)
        out.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0x00))

        for (line in syncedLines) {
            val ms = line.timestampMs ?: 0L
            // Text terminated by 0x00 0x00
            out.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
            out.write(line.text.toByteArray(Charsets.UTF_16BE))
            out.write(byteArrayOf(0x00, 0x00))

            // 4-byte timestamp ms (big endian)
            val timeBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ms.toInt()).array()
            out.write(timeBytes)
        }

        return out.toByteArray()
    }

    private fun readSynchSafeInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
                ((data[offset + 1].toInt() and 0x7F) shl 14) or
                ((data[offset + 2].toInt() and 0x7F) shl 7) or
                (data[offset + 3].toInt() and 0x7F)
    }

    private fun toSynchSafeBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte()
        )
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).int
    }
}
