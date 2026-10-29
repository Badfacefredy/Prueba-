package com.example.ai

import android.util.Log
import com.example.model.LrcLine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAutoSync"

data class AutoSyncResult(
    val detectedBpm: Int,
    val timeSignature: String,
    val analysisNotes: String,
    val syncedLines: List<LrcLine>
)

class GeminiAutoSyncService {

    private val syncLineSchema = Schema.obj(
        properties = mapOf(
            "lineIndex" to Schema.integer("0-based index of the lyric line"),
            "text" to Schema.string("The exact text of the line"),
            "timestampMs" to Schema.integer("Millisecond timestamp when this line begins, aligned with the song beat"),
            "beatOnset" to Schema.string("Musical timing cue, e.g. 'Bar 2 Beat 1' or 'Downbeat'"),
            "confidence" to Schema.string("High, Medium, or Low")
        )
    )

    private val autoSyncResponseSchema = Schema.obj(
        properties = mapOf(
            "detectedBpm" to Schema.integer("Estimated tempo in Beats Per Minute (BPM)"),
            "timeSignature" to Schema.string("Time signature, e.g. 4/4 or 3/4"),
            "analysisNotes" to Schema.string("Brief explanation of how the beat and vocal cadence were aligned"),
            "syncedLines" to Schema.array(syncLineSchema, "The synchronized lyric lines with millisecond timestamps")
        )
    )

    private val config by lazy {
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = autoSyncResponseSchema
            temperature = 0.2f
        }
    }

    // Lazy initialization of model per Phase 3 rules
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    /**
     * Automatically analyzes the song structure, duration, and rhythm, aligning each lyric line
     * to the musical beat using Gemini 3.8 Flash AI with strict JSON schema.
     */
    suspend fun autoSyncLyrics(
        lines: List<LrcLine>,
        durationMs: Long,
        songTitle: String,
        artist: String,
        audioEnergyProfile: String = ""
    ): AutoSyncResult = withContext(Dispatchers.IO) {
        val linesPrompt = lines.mapIndexed { idx, line ->
            "${idx + 1}. \"${line.text}\""
        }.joinToString("\n")

        val prompt = buildString {
            appendLine("You are an expert audio engineer and musical beat-tracking synchronizer.")
            appendLine("Task: Listen to and analyze this song, detecting its tempo (BPM), meter/beat structure, and align each lyric line to the song's beat and vocal delivery.")
            appendLine()
            appendLine("SONG DETAILS:")
            appendLine("- Title: $songTitle")
            appendLine("- Artist: ${artist.ifBlank { "Unknown" }}")
            appendLine("- Total Audio Duration: ${durationMs}ms (${durationMs / 1000} seconds)")
            if (audioEnergyProfile.isNotBlank()) {
                appendLine("- Audio Rhythmic Peaks & Energy Timeline:\n$audioEnergyProfile")
            }
            appendLine()
            appendLine("LYRICS TO ALIGN (in order):")
            appendLine(linesPrompt)
            appendLine()
            appendLine("RULES FOR SYNCHRONIZATION:")
            appendLine("1. Each timestampMs must be within 0 and $durationMs.")
            appendLine("2. Timestamps must be strictly non-decreasing and spaced naturally according to the musical beat, bars, and poetic meter.")
            appendLine("3. Include a realistic intro delay before the first line starts singing.")
            appendLine("4. Return the exact JSON matching the schema with detectedBpm, timeSignature, analysisNotes, and syncedLines.")
        }

        try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        text(prompt)
                    }
                )
            }

            val jsonText = response.text ?: throw IllegalStateException("Gemini returned empty response")
            Log.d(TAG, "Gemini AutoSync response: $jsonText")

            val rootJson = JSONObject(jsonText)
            val detectedBpm = rootJson.optInt("detectedBpm", 120)
            val timeSignature = rootJson.optString("timeSignature", "4/4")
            val analysisNotes = rootJson.optString("analysisNotes", "Alineado con el compás de la canción")

            val syncedArray = rootJson.getJSONArray("syncedLines")
            val updatedLines = lines.mapIndexed { idx, originalLine ->
                var foundTs: Long? = null
                for (i in 0 until syncedArray.length()) {
                    val item = syncedArray.getJSONObject(i)
                    val lineIdx = item.optInt("lineIndex", -1)
                    if (lineIdx == idx || i == idx) {
                        val ts = item.optLong("timestampMs", -1L)
                        if (ts in 0L..durationMs) {
                            foundTs = ts
                        }
                        break
                    }
                }
                originalLine.copy(timestampMs = foundTs ?: originalLine.timestampMs)
            }

            AutoSyncResult(
                detectedBpm = detectedBpm,
                timeSignature = timeSignature,
                analysisNotes = analysisNotes,
                syncedLines = updatedLines
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini auto-sync failed", e)
            throw e
        }
    }
}
