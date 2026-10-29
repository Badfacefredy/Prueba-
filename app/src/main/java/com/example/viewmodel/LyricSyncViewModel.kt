package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.LrcDocument
import com.example.model.LrcLine
import com.example.model.LrcParser
import com.example.model.SongMetadata
import com.example.player.AudioPlayerManager
import com.example.player.DemoAudioGenerator
import com.example.tagger.Id3Tagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

data class AudioSource(
    val uri: Uri? = null,
    val localFile: File? = null,
    val displayName: String = "Ninguna",
    val isDemo: Boolean = false,
    val sizeBytes: Long = 0L,
    val isMp3: Boolean = false
)

data class EmbedResult(
    val success: Boolean,
    val message: String,
    val originalSizeBytes: Long = 0L,
    val newSizeBytes: Long = 0L,
    val embeddedLinesCount: Int = 0
)

class LyricSyncViewModel(application: Application) : AndroidViewModel(application) {

    val playerManager = AudioPlayerManager(viewModelScope)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _currentAudioSource = MutableStateFlow<AudioSource?>(null)
    val currentAudioSource: StateFlow<AudioSource?> = _currentAudioSource.asStateFlow()

    private val _lrcDocument = MutableStateFlow(LrcDocument())
    val lrcDocument: StateFlow<LrcDocument> = _lrcDocument.asStateFlow()

    private val _activeSyncLineIndex = MutableStateFlow(0)
    val activeSyncLineIndex: StateFlow<Int> = _activeSyncLineIndex.asStateFlow()

    private val _autoAdvanceOnSync = MutableStateFlow(true)
    val autoAdvanceOnSync: StateFlow<Boolean> = _autoAdvanceOnSync.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _lastEmbedResult = MutableStateFlow<EmbedResult?>(null)
    val lastEmbedResult: StateFlow<EmbedResult?> = _lastEmbedResult.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val geminiAutoSyncService = com.example.ai.GeminiAutoSyncService()

    private val _isAiSyncing = MutableStateFlow(false)
    val isAiSyncing: StateFlow<Boolean> = _isAiSyncing.asStateFlow()

    private val _aiSyncStatus = MutableStateFlow<String?>(null)
    val aiSyncStatus: StateFlow<String?> = _aiSyncStatus.asStateFlow()

    private val _lastAiSyncResult = MutableStateFlow<com.example.ai.AutoSyncResult?>(null)
    val lastAiSyncResult: StateFlow<com.example.ai.AutoSyncResult?> = _lastAiSyncResult.asStateFlow()

    // Dynamically highlights current lyric line according to playback position
    val currentPlayingLineIndex: StateFlow<Int> = combine(
        playerManager.currentPositionMs,
        _lrcDocument
    ) { posMs, doc ->
        findCurrentPlayingLineIndex(posMs, doc.lines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    init {
        // Automatically load demo song on start so the app is instantly usable
        loadDemoSong(preSynced = true)
    }

    private fun findCurrentPlayingLineIndex(currentPosMs: Long, lines: List<LrcLine>): Int {
        val syncedWithIndices = lines.mapIndexed { idx, line -> idx to line }
            .filter { it.second.isSynced }
            .sortedBy { it.second.timestampMs ?: 0L }

        if (syncedWithIndices.isEmpty()) return -1

        var matchIdx = -1
        for ((origIdx, line) in syncedWithIndices) {
            val ts = line.timestampMs ?: 0L
            if (currentPosMs >= ts) {
                matchIdx = origIdx
            } else {
                break
            }
        }
        return matchIdx
    }

    // -------------------------------------------------------------
    // Audio Loading & Demo
    // -------------------------------------------------------------

    fun loadDemoSong(preSynced: Boolean = true) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val demoFile = withContext(Dispatchers.IO) {
                    DemoAudioGenerator.getOrCreateDemoAudioFile(getApplication())
                }
                playerManager.loadFromFile(demoFile, DemoAudioGenerator.DEMO_TITLE)
                _currentAudioSource.value = AudioSource(
                    localFile = demoFile,
                    displayName = "${DemoAudioGenerator.DEMO_TITLE}.wav",
                    isDemo = true,
                    sizeBytes = demoFile.length(),
                    isMp3 = false
                )

                val rawLyrics = if (preSynced) DemoAudioGenerator.DEFAULT_DEMO_LYRICS else DemoAudioGenerator.UNPRACTICED_DEMO_LYRICS
                val doc = LrcParser.parse(rawLyrics)
                _lrcDocument.value = doc.copy(
                    metadata = SongMetadata(
                        title = DemoAudioGenerator.DEMO_TITLE,
                        artist = DemoAudioGenerator.DEMO_ARTIST,
                        album = DemoAudioGenerator.DEMO_ALBUM,
                        creator = "LyricSync"
                    )
                )
                _activeSyncLineIndex.value = 0
                _statusMessage.value = "Canción demo cargada con éxito"
            } catch (e: Exception) {
                Log.e("LyricSyncViewModel", "Error loading demo song", e)
                _statusMessage.value = "Error al generar canción demo"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadAudioFromUri(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val context = getApplication<Application>()
                val nameAndSize = getFileNameAndSize(context, uri)
                val fileName = nameAndSize.first ?: "Cancion_audio"
                val sizeBytes = nameAndSize.second
                val isMp3 = fileName.endsWith(".mp3", ignoreCase = true)

                playerManager.loadFromUri(context, uri, fileName)
                _currentAudioSource.value = AudioSource(
                    uri = uri,
                    displayName = fileName,
                    isDemo = false,
                    sizeBytes = sizeBytes,
                    isMp3 = isMp3
                )

                // Inspect ID3 tags if it's MP3
                if (isMp3) {
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val extracted = Id3Tagger.readExistingTags(stream)
                                if (extracted.hasExistingId3) {
                                    val currentMeta = _lrcDocument.value.metadata
                                    val updatedMeta = currentMeta.copy(
                                        title = extracted.metadata.title.ifBlank { currentMeta.title.ifBlank { fileName.removeSuffix(".mp3") } },
                                        artist = extracted.metadata.artist.ifBlank { currentMeta.artist },
                                        album = extracted.metadata.album.ifBlank { currentMeta.album }
                                    )
                                    if (!extracted.embeddedLyrics.isNullOrBlank()) {
                                        val parsed = LrcParser.parse(extracted.embeddedLyrics)
                                        _lrcDocument.value = parsed.copy(metadata = updatedMeta)
                                        _statusMessage.value = "Canción cargada. ¡Se detectaron letras ID3v2 incrustadas!"
                                    } else {
                                        _lrcDocument.value = _lrcDocument.value.copy(metadata = updatedMeta)
                                        _statusMessage.value = "Canción cargada: $fileName"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("LyricSyncViewModel", "Could not inspect ID3 tags", e)
                        }
                    }
                } else {
                    _statusMessage.value = "Canción cargada: $fileName"
                }
            } catch (e: Exception) {
                Log.e("LyricSyncViewModel", "Error loading audio uri", e)
                _statusMessage.value = "Error al abrir archivo de audio"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size: Long = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) { }
        return (name ?: uri.lastPathSegment) to size
    }

    // -------------------------------------------------------------
    // Lyrics Loading & Text Input
    // -------------------------------------------------------------

    fun loadLyricsFromUri(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val context = getApplication<Application>()
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                }
                loadLyricsFromText(text)
                _statusMessage.value = "Archivo LRC/letra importado correctamente"
            } catch (e: Exception) {
                Log.e("LyricSyncViewModel", "Error reading lyrics file", e)
                _statusMessage.value = "Error al leer archivo de letras"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadLyricsFromText(rawText: String) {
        val parsed = LrcParser.parse(rawText)
        val currentMeta = _lrcDocument.value.metadata
        val mergedMeta = parsed.metadata.copy(
            title = parsed.metadata.title.ifBlank { currentMeta.title },
            artist = parsed.metadata.artist.ifBlank { currentMeta.artist },
            album = parsed.metadata.album.ifBlank { currentMeta.album }
        )
        _lrcDocument.value = parsed.copy(metadata = mergedMeta)
        _activeSyncLineIndex.value = 0
    }

    // -------------------------------------------------------------
    // Live Manual Sync Actions ("Tap to Sync")
    // -------------------------------------------------------------

    fun markCurrentLineTimestamp() {
        val currentIdx = _activeSyncLineIndex.value
        val lines = _lrcDocument.value.lines.toMutableList()
        if (currentIdx !in lines.indices) return

        val currentPlaybackMs = playerManager.currentPositionMs.value
        val updatedLine = lines[currentIdx].copy(timestampMs = currentPlaybackMs)
        lines[currentIdx] = updatedLine

        _lrcDocument.value = _lrcDocument.value.copy(lines = lines)
        triggerHaptic()

        if (_autoAdvanceOnSync.value && currentIdx + 1 < lines.size) {
            _activeSyncLineIndex.value = currentIdx + 1
        }
    }

    fun setActiveSyncLine(index: Int) {
        if (index in _lrcDocument.value.lines.indices) {
            _activeSyncLineIndex.value = index
        }
    }

    fun toggleAutoAdvance() {
        _autoAdvanceOnSync.value = !_autoAdvanceOnSync.value
    }

    fun resetAllTimestamps() {
        val cleared = _lrcDocument.value.lines.map { it.copy(timestampMs = null) }
        _lrcDocument.value = _lrcDocument.value.copy(lines = cleared)
        _activeSyncLineIndex.value = 0
        _statusMessage.value = "Marcas de tiempo reiniciadas"
    }

    // -------------------------------------------------------------
    // Advanced Precision Editor (Nudge, Split, Merge, Shift, Add, Delete)
    // -------------------------------------------------------------

    fun nudgeLine(lineId: String, deltaMs: Long) {
        val lines = _lrcDocument.value.lines.toMutableList()
        val index = lines.indexOfFirst { it.id == lineId }
        if (index == -1) return

        val currentMs = lines[index].timestampMs ?: playerManager.currentPositionMs.value
        val newMs = (currentMs + deltaMs).coerceAtLeast(0L)
        lines[index] = lines[index].copy(timestampMs = newMs)
        _lrcDocument.value = _lrcDocument.value.copy(lines = lines)
    }

    fun setLineTimestamp(lineId: String, timestampMs: Long?) {
        val lines = _lrcDocument.value.lines.toMutableList()
        val index = lines.indexOfFirst { it.id == lineId }
        if (index == -1) return

        lines[index] = lines[index].copy(timestampMs = timestampMs?.coerceAtLeast(0L))
        _lrcDocument.value = _lrcDocument.value.copy(lines = lines)
    }

    fun updateLineText(lineId: String, newText: String) {
        val lines = _lrcDocument.value.lines.toMutableList()
        val index = lines.indexOfFirst { it.id == lineId }
        if (index == -1) return

        lines[index] = lines[index].copy(text = newText)
        _lrcDocument.value = _lrcDocument.value.copy(lines = lines)
    }

    /**
     * Splits a lyric line at a specific text index into two separate lines.
     * Line 1 retains the start text and original timestamp.
     * Line 2 gets the trailing text and [newTimestampMs].
     */
    fun splitLine(lineId: String, splitCharIndex: Int, newTimestampMs: Long?) {
        val lines = _lrcDocument.value.lines.toMutableList()
        val index = lines.indexOfFirst { it.id == lineId }
        if (index == -1) return

        val original = lines[index]
        val clampedSplit = splitCharIndex.coerceIn(0, original.text.length)
        val text1 = original.text.substring(0, clampedSplit).trim()
        val text2 = original.text.substring(clampedSplit).trim()

        val line1 = original.copy(text = text1)
        val line2 = LrcLine(
            timestampMs = newTimestampMs ?: original.timestampMs?.let { it + 1500L },
            text = text2
        )

        lines[index] = line1
        lines.add(index + 1, line2)
        _lrcDocument.value = _lrcDocument.value.copy(lines = lines)
        _statusMessage.value = "Línea dividida en dos líneas de tiempo"
    }

    /**
     * Merges line with the next line in the list.
     */
    fun mergeLines(firstLineId: String) {
        val lines = _lrcDocument.value.lines.toMutableList()
        val index = lines.indexOfFirst { it.id == firstLineId }
        if (index == -1 || index >= lines.size - 1) return

        val line1 = lines[index]
        val line2 = lines[index + 1]

        val combinedText = "${line1.text} ${line2.text}".trim()
        val mergedLine = line1.copy(text = combinedText)

        lines[index] = mergedLine
        lines.removeAt(index + 1)

        _lrcDocument.value = _lrcDocument.value.copy(lines = lines)
        _statusMessage.value = "Líneas fusionadas exitosamente"
    }

    /**
     * Shifts all synced line timestamps by [deltaMs] (+200ms, -200ms, etc.)
     */
    fun shiftAllTimestamps(deltaMs: Long) {
        val updated = _lrcDocument.value.lines.map { line ->
            if (line.timestampMs != null) {
                line.copy(timestampMs = (line.timestampMs + deltaMs).coerceAtLeast(0L))
            } else line
        }
        _lrcDocument.value = _lrcDocument.value.copy(lines = updated)
        val sign = if (deltaMs >= 0) "+$deltaMs" else "$deltaMs"
        _statusMessage.value = "Desplazamiento global aplicado: ${sign}ms"
    }

    fun addLine(afterIndex: Int, text: String = "", timestampMs: Long? = null) {
        val lines = _lrcDocument.value.lines.toMutableList()
        val newLine = LrcLine(
            text = text,
            timestampMs = timestampMs ?: playerManager.currentPositionMs.value
        )
        val targetPos = (afterIndex + 1).coerceIn(0, lines.size)
        lines.add(targetPos, newLine)
        _lrcDocument.value = _lrcDocument.value.copy(lines = lines)
        _activeSyncLineIndex.value = targetPos
    }

    fun removeLine(lineId: String) {
        val lines = _lrcDocument.value.lines.toMutableList()
        lines.removeAll { it.id == lineId }
        _lrcDocument.value = _lrcDocument.value.copy(lines = lines)
        if (_activeSyncLineIndex.value >= lines.size) {
            _activeSyncLineIndex.value = (lines.size - 1).coerceAtLeast(0)
        }
    }

    fun sortLinesChronologically() {
        val synced = _lrcDocument.value.lines.filter { it.isSynced }.sortedBy { it.timestampMs ?: 0L }
        val unsynced = _lrcDocument.value.lines.filter { !it.isSynced }
        _lrcDocument.value = _lrcDocument.value.copy(lines = synced + unsynced)
        _statusMessage.value = "Líneas ordenadas cronológicamente"
    }

    fun updateMetadata(
        title: String,
        artist: String,
        album: String,
        creator: String = "LyricSync",
        author: String = "",
        length: String = "",
        offsetMs: Long = 0L
    ) {
        _lrcDocument.value = _lrcDocument.value.copy(
            metadata = _lrcDocument.value.metadata.copy(
                title = title,
                artist = artist,
                album = album,
                creator = creator,
                author = author,
                length = length,
                offsetMs = offsetMs
            )
        )
    }

    // -------------------------------------------------------------
    // Export & Embedding
    // -------------------------------------------------------------

    /**
     * Exports the independent LRC file to the user-selected destination URI.
     */
    fun exportLrcToUri(destinationUri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val context = getApplication<Application>()
                val lrcContent = _lrcDocument.value.toLrcString()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
                        out.write(lrcContent.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
                _statusMessage.value = "Archivo .LRC exportado y guardado con éxito"
            } catch (e: Exception) {
                Log.e("LyricSyncViewModel", "Error saving LRC file", e)
                _statusMessage.value = "Error al guardar archivo .LRC: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Embeds the synchronized LRC (USLT, SYLT, TXXX:LYRICS) into the audio file
     * and saves the resulting audio file to [destinationUri].
     */
    fun embedLrcIntoAudioFile(destinationUri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val context = getApplication<Application>()
                val source = _currentAudioSource.value
                val lrcDoc = _lrcDocument.value

                val audioBytes = withContext(Dispatchers.IO) {
                    readSourceAudioBytes(context, source)
                }

                if (audioBytes == null || audioBytes.isEmpty()) {
                    _statusMessage.value = "Error: no hay datos de audio para incrustar"
                    return@launch
                }

                val taggedAudioBytes = withContext(Dispatchers.IO) {
                    Id3Tagger.embedLyricsIntoMp3(audioBytes, lrcDoc)
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
                        out.write(taggedAudioBytes)
                        out.flush()
                    }
                }

                val result = EmbedResult(
                    success = true,
                    message = "¡Letras incrustadas exitosamente en el archivo de audio con etiquetas ID3v2 (USLT + SYLT + TXXX)!",
                    originalSizeBytes = audioBytes.size.toLong(),
                    newSizeBytes = taggedAudioBytes.size.toLong(),
                    embeddedLinesCount = lrcDoc.lines.count { it.isSynced }
                )
                _lastEmbedResult.value = result
                _statusMessage.value = result.message
            } catch (e: Exception) {
                Log.e("LyricSyncViewModel", "Error embedding lyrics", e)
                val result = EmbedResult(
                    success = false,
                    message = "Error al incrustar letras: ${e.localizedMessage}"
                )
                _lastEmbedResult.value = result
                _statusMessage.value = result.message
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun readSourceAudioBytes(context: Context, source: AudioSource?): ByteArray? {
        if (source == null) return null
        return try {
            if (source.localFile != null && source.localFile.exists()) {
                FileInputStream(source.localFile).use { it.readBytes() }
            } else if (source.uri != null) {
                context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
            } else null
        } catch (e: Exception) {
            Log.e("LyricSyncViewModel", "Error reading audio bytes", e)
            null
        }
    }

    fun startAiAutoSync() {
        viewModelScope.launch {
            val lines = _lrcDocument.value.lines
            if (lines.isEmpty()) {
                _statusMessage.value = "No hay letras cargadas para sincronizar con IA"
                return@launch
            }
            val durationMs = playerManager.durationMs.value
            if (durationMs <= 0) {
                _statusMessage.value = "Espera a que cargue el audio para conocer su duración"
                return@launch
            }

            _isAiSyncing.value = true
            _aiSyncStatus.value = "Analizando ritmo y energía del audio..."

            try {
                val context = getApplication<Application>()
                val source = _currentAudioSource.value
                val audioBytes = withContext(Dispatchers.IO) {
                    readSourceAudioBytes(context, source)
                }

                val beatProfile = if (audioBytes != null && audioBytes.isNotEmpty()) {
                    com.example.ai.AudioBeatAnalyzer.analyzeAudioBytes(audioBytes, durationMs)
                } else {
                    com.example.ai.AudioBeatAnalyzer.BeatProfile(120, "Transiciones distribuidas uniformemente", 0.0)
                }

                _aiSyncStatus.value = "Alineando letras con el compás (~${beatProfile.estimatedBpm} BPM) usando Gemini 3.8 Flash..."

                val result = geminiAutoSyncService.autoSyncLyrics(
                    lines = lines,
                    durationMs = durationMs,
                    songTitle = _lrcDocument.value.metadata.title.ifBlank { source?.displayName ?: "Canción" },
                    artist = _lrcDocument.value.metadata.artist,
                    audioEnergyProfile = beatProfile.peakMarkersSummary
                )

                _lrcDocument.value = _lrcDocument.value.copy(lines = result.syncedLines)
                _lastAiSyncResult.value = result
                triggerHaptic()
                _statusMessage.value = "¡Sincronización con IA completada! ${result.detectedBpm} BPM • Compás ${result.timeSignature}"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LyricSyncViewModel", "AI Auto-sync failed", e)
                _statusMessage.value = "Error en Auto-Sync con IA: ${e.localizedMessage}"
            } finally {
                _isAiSyncing.value = false
                _aiSyncStatus.value = null
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
