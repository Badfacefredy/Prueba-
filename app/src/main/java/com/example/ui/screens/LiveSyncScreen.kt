package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PlayerControlBar
import com.example.ui.components.collectAsStateWithLifecycleComposable
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonViolet
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
fun LiveSyncScreen(
    viewModel: LyricSyncViewModel,
    modifier: Modifier = Modifier
) {
    val lrcDoc by viewModel.lrcDocument.collectAsStateWithLifecycleComposable()
    val activeIdx by viewModel.activeSyncLineIndex.collectAsStateWithLifecycleComposable()
    val playingIdx by viewModel.currentPlayingLineIndex.collectAsStateWithLifecycleComposable()
    val autoAdvance by viewModel.autoAdvanceOnSync.collectAsStateWithLifecycleComposable()
    val audioSource by viewModel.currentAudioSource.collectAsStateWithLifecycleComposable()

    val isAiSyncing by viewModel.isAiSyncing.collectAsStateWithLifecycleComposable()
    val aiSyncStatus by viewModel.aiSyncStatus.collectAsStateWithLifecycleComposable()
    val lastAiResult by viewModel.lastAiSyncResult.collectAsStateWithLifecycleComposable()
    val durationMs by viewModel.playerManager.durationMs.collectAsStateWithLifecycleComposable()

    var showAiDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Smoothly keep the active sync line visible
    LaunchedEffect(activeIdx) {
        if (activeIdx in lrcDoc.lines.indices) {
            val target = (activeIdx - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioDarkBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Audio Player Bar
        PlayerControlBar(
            playerManager = viewModel.playerManager,
            trackTitle = audioSource?.displayName ?: "Sin archivo cargado",
            isMp3 = audioSource?.isMp3 == true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Hero Sync Panel (Action area)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp)),
            color = StudioSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row inside panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val syncedCount = lrcDoc.lines.count { it.isSynced }
                    val total = lrcDoc.lines.size

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (syncedCount == total && total > 0) NeonGreen else NeonCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sincronizado: $syncedCount / $total",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = autoAdvance,
                            onClick = { viewModel.toggleAutoAdvance() },
                            label = { Text("Auto-avanzar", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = autoAdvance,
                                borderColor = if (autoAdvance) NeonCyan else StudioBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active line target prompt
                val currentTargetLine = lrcDoc.lines.getOrNull(activeIdx)
                if (currentTargetLine != null) {
                    Text(
                        text = "LÍNEA ${activeIdx + 1} DE ${lrcDoc.lines.size}",
                        fontSize = 11.sp,
                        color = NeonViolet,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTargetLine.text.ifBlank { "(Línea en blanco)" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    Text(
                        text = "¡Todas las líneas han sido sincronizadas!",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // BIG TACTILE SYNC BUTTON
                Button(
                    onClick = { viewModel.markCurrentLineTimestamp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("tap_to_sync_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(NeonCyan, NeonViolet)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MARCAR TIEMPO AHORA",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AI Auto-Sync Action Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { showAiDialog = true }
                        .testTag("ai_auto_sync_banner_button"),
                    color = NeonCyan.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAiSyncing) "Sincronizando con IA..." else "Auto-Sincronizar con Gemini AI (Alinear al Compás)",
                            color = NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lyrics List (Karaoke & Focus)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(lrcDoc.lines, key = { _, line -> line.id }) { index, line ->
                val isActiveSync = (index == activeIdx)
                val isCurrentPlaying = (index == playingIdx)

                val cardBg by animateColorAsState(
                    targetValue = when {
                        isCurrentPlaying -> NeonCyan.copy(alpha = 0.20f)
                        isActiveSync -> NeonViolet.copy(alpha = 0.15f)
                        else -> StudioSurface
                    },
                    label = "cardBg"
                )

                val borderColor by animateColorAsState(
                    targetValue = when {
                        isCurrentPlaying -> NeonCyan
                        isActiveSync -> NeonViolet
                        else -> StudioBorder
                    },
                    label = "border"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isCurrentPlaying || isActiveSync) 1.5.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            viewModel.setActiveSyncLine(index)
                            if (line.isSynced && line.timestampMs != null) {
                                viewModel.playerManager.seekTo(line.timestampMs)
                            }
                        }
                        .testTag("lyric_line_item_$index"),
                    color = cardBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Index number badge
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isActiveSync) NeonViolet else StudioSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActiveSync) Color.White else TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Timestamp tag
                        Text(
                            text = line.formattedTimestamp(includeBrackets = true),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (line.isSynced) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isCurrentPlaying -> NeonCyan
                                line.isSynced -> NeonCyan.copy(alpha = 0.8f)
                                else -> TextMuted
                            }
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Lyric text
                        Text(
                            text = line.text.ifBlank { "(Línea vacía)" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrentPlaying) Color.White else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        // Status indicator
                        if (line.isSynced) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Sincronizado",
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Pendiente",
                                tint = TextMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
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
