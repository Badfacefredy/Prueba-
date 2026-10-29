package com.example.model

import java.util.Locale
import java.util.UUID

/**
 * Represents a single line of lyrics with an optional millisecond timestamp.
 */
data class LrcLine(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long? = null,
    val text: String = ""
) {
    val isSynced: Boolean
        get() = timestampMs != null && timestampMs >= 0

    fun formattedTimestamp(includeBrackets: Boolean = true): String {
        val ms = timestampMs ?: return if (includeBrackets) "[--:--.--]" else "--:--.--"
        val totalHundredths = ms / 10
        val hundredths = totalHundredths % 100
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60
        val timeStr = String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
        return if (includeBrackets) "[$timeStr]" else timeStr
    }
}

/**
 * Metadata embedded within an LRC file or ID3 tag.
 */
data class SongMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val author: String = "",
    val creator: String = "LyricSync",
    val length: String = "",
    val offsetMs: Long = 0L
)

/**
 * Represents the complete LRC document state.
 */
data class LrcDocument(
    val metadata: SongMetadata = SongMetadata(),
    val lines: List<LrcLine> = emptyList()
) {
    /**
     * Serializes the document into standard LRC format.
     */
    fun toLrcString(): String {
        val sb = StringBuilder()
        if (metadata.title.isNotBlank()) sb.append("[ti:${metadata.title}]\n")
        if (metadata.artist.isNotBlank()) sb.append("[ar:${metadata.artist}]\n")
        if (metadata.album.isNotBlank()) sb.append("[al:${metadata.album}]\n")
        if (metadata.author.isNotBlank()) sb.append("[au:${metadata.author}]\n")
        if (metadata.creator.isNotBlank()) sb.append("[by:${metadata.creator}]\n")
        if (metadata.length.isNotBlank()) sb.append("[length:${metadata.length}]\n")
        if (metadata.offsetMs != 0L) sb.append("[offset:${metadata.offsetMs}]\n")
        sb.append("[re:LyricSync for Android]\n")

        for (line in lines) {
            val ts = line.formattedTimestamp(includeBrackets = true)
            sb.append("$ts${line.text}\n")
        }
        return sb.toString()
    }
}

object LrcParser {
    private val tagRegex = Regex("""^\[([a-zA-Z]+):(.*)\]$""")
    private val timeRegex = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?\]""")

    fun parse(rawText: String): LrcDocument {
        var title = ""
        var artist = ""
        var album = ""
        var author = ""
        var creator = "LyricSync"
        var length = ""
        var offsetMs = 0L
        val linesList = mutableListOf<LrcLine>()

        val rawLines = rawText.lines()
        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check metadata tag
            val tagMatch = tagRegex.matchEntire(trimmed)
            if (tagMatch != null) {
                val tag = tagMatch.groupValues[1].lowercase(Locale.ROOT)
                val value = tagMatch.groupValues[2].trim()
                when (tag) {
                    "ti" -> title = value
                    "ar" -> artist = value
                    "al" -> album = value
                    "au" -> author = value
                    "by" -> creator = value
                    "length" -> length = value
                    "offset" -> offsetMs = value.toLongOrNull() ?: 0L
                }
                continue
            }

            // Check if line contains one or more timestamps [mm:ss.xx]
            val timeMatches = timeRegex.findAll(trimmed).toList()
            if (timeMatches.isNotEmpty()) {
                val lastMatch = timeMatches.last()
                val textPart = trimmed.substring(lastMatch.range.last + 1).trim()

                for (match in timeMatches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionalStr = match.groupValues[3]
                    val msFraction = when (fractionalStr.length) {
                        0 -> 0L
                        1 -> (fractionalStr.toLongOrNull() ?: 0L) * 100
                        2 -> (fractionalStr.toLongOrNull() ?: 0L) * 10
                        else -> fractionalStr.take(3).toLongOrNull() ?: 0L
                    }
                    val timestampMs = (minutes * 60 + seconds) * 1000 + msFraction
                    linesList.add(LrcLine(timestampMs = timestampMs, text = textPart))
                }
            } else {
                // Plain line without timestamp
                linesList.add(LrcLine(timestampMs = null, text = trimmed))
            }
        }

        return LrcDocument(
            metadata = SongMetadata(title, artist, album, author, creator, length, offsetMs),
            lines = linesList
        )
    }

    fun parseTimestampToMs(minutes: Long, seconds: Long, hundredths: Long): Long {
        return (minutes * 60 + seconds) * 1000 + hundredths * 10
    }
}
