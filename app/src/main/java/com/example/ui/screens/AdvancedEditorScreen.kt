package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LrcLine
import com.example.ui.components.EditLineDialog
import com.example.ui.components.PlayerControlBar
import com.example.ui.components.SplitLineDialog
import com.example.ui.components.collectAsStateWithLifecycleComposable
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceLight
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.LyricSyncViewModel

@Composable
fun AdvancedEditorScreen(
    viewModel: LyricSyncViewModel,
    modifier: Modifier = Modifier
) {
    val lrcDoc by viewModel.lrcDocument.collectAsStateWithLifecycleComposable()
    val audioSource by viewModel.currentAudioSource.collectAsStateWithLifecycleComposable()
    val currentPlaybackMs by viewModel.playerManager.currentPositionMs.collectAsStateWithLifecycleComposable()

    var lineToSplit by remember { mutableStateOf<LrcLine?>(null) }
    var lineToEdit by remember { mutableStateOf<LrcLine?>(null) }
    var showAiDialog by remember { mutableStateOf(false) }

    val isAiSyncing by viewModel.isAiSyncing.collectAsStateWithLifecycleComposable()
    val aiSyncStatus by viewModel.aiSyncStatus.collectAsStateWithLifecycleComposable()
    val lastAiResult by viewModel.lastAiSyncResult.collectAsStateWithLifecycleComposable()
    val durationMs by viewModel.playerManager.durationMs.collectAsStateWithLifecycleComposable()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioDarkBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Player Control Bar
        PlayerControlBar(
            playerManager = viewModel.playerManager,
            trackTitle = audioSource?.displayName ?: "Sin archivo cargado",
            isMp3 = audioSource?.isMp3 == true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Global Drift & Tools Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp)),
            color = StudioSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ajuste Global de Tiempo (Desfase)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showAiDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Auto-sincronizar con Gemini IA",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.sortLinesChronologically() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Ordenar cronológicamente",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Shift buttons (-500ms, -100ms, +100ms, +500ms)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-500L to "-500ms", -100L to "-100ms", 100L to "+100ms", 500L to "+500ms").forEach { (delta, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioSurfaceVariant)
                                .border(0.5.dp, StudioBorder, RoundedCornerShape(8.dp))
                                .clickable { viewModel.shiftAllTimestamps(delta) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (delta > 0) NeonCyan else NeonAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Header for Lines List with "Add line" action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Líneas de la Letra (${lrcDoc.lines.size})",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { viewModel.addLine(afterIndex = lrcDoc.lines.size - 1) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nueva Línea", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Advanced Line Cards
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(lrcDoc.lines, key = { _, line -> line.id }) { index, line ->
                LineEditorCard(
                    line = line,
                    index = index,
                    totalLines = lrcDoc.lines.size,
                    onPlayPreview = {
                        if (line.timestampMs != null) {
                            viewModel.playerManager.seekTo(line.timestampMs)
                            viewModel.playerManager.play()
                        }
                    },
                    onNudge = { deltaMs -> viewModel.nudgeLine(line.id, deltaMs) },
                    onSplit = { lineToSplit = line },
                    onMergeWithNext = { viewModel.mergeLines(line.id) },
                    onEdit = { lineToEdit = line },
                    onDelete = { viewModel.removeLine(line.id) },
                    onAddBelow = { viewModel.addLine(afterIndex = index) }
                )
            }
        }
    }

    // Split Line Modal Dialog
    lineToSplit?.let { targetLine ->
        SplitLineDialog(
            line = targetLine,
            currentPlaybackMs = currentPlaybackMs,
            onDismiss = { lineToSplit = null },
            onConfirmSplit = { splitIdx, newTs ->
                viewModel.splitLine(targetLine.id, splitIdx, newTs)
                lineToSplit = null
            }
        )
    }

    // Edit Line Modal Dialog
    lineToEdit?.let { targetLine ->
        EditLineDialog(
            line = targetLine,
            currentPlaybackMs = currentPlaybackMs,
            onDismiss = { lineToEdit = null },
            onSave = { newText, newTs ->
                viewModel.updateLineText(targetLine.id, newText)
                viewModel.setLineTimestamp(targetLine.id, newTs)
                lineToEdit = null
            }
        )
    }

    if (showAiDialog) {
        com.example.ui.components.AiSyncDialog(
            isSyncing = isAiSyncing,
            syncProgressText = aiSyncStatus,
            lastResult = lastAiResult,
            songTitle = audioSource?.displayName ?: "Canción",
            totalLines = lrcDoc.lines.size,
            durationMs = durationMs,
            onDismiss = { showAiDialog = false },
            onStartSync = { viewModel.startAiAutoSync() }
        )
    }
}

@Composable
private fun LineEditorCard(
    line: LrcLine,
    index: Int,
    totalLines: Int,
    onPlayPreview: () -> Unit,
    onNudge: (deltaMs: Long) -> Unit,
    onSplit: () -> Unit,
    onMergeWithNext: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddBelow: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
        color = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Line Header: Index badge, timestamp, Play button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(NeonViolet.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonVioletLight
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = line.formattedTimestamp(includeBrackets = true),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (line.isSynced) NeonCyan else TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (line.isSynced) {
                        IconButton(
                            onClick = onPlayPreview,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Escuchar este punto",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar texto o tiempo",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar línea",
                            tint = NeonRose.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Line Lyric Text
            Text(
                text = line.text.ifBlank { "(Línea sin texto)" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() }
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fine Nudge bar: [-100ms] [+100ms]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Precision Nudge buttons
                listOf(-500L to "-0.5s", -100L to "-0.1s", 100L to "+0.1s", 500L to "+0.5s").forEach { (delta, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioSurfaceVariant)
                            .clickable { onNudge(delta) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Split and Merge Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // SPLIT BUTTON
                OutlinedButton(
                    onClick = onSplit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonCyan
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dividir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // MERGE BUTTON (Enabled only if there is a next line)
                OutlinedButton(
                    onClick = onMergeWithNext,
                    enabled = index < totalLines - 1,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonViolet
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fusionar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // ADD BELOW BUTTON
                OutlinedButton(
                    onClick = onAddBelow,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Insertar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
