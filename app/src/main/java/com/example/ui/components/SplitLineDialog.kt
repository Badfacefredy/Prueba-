package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceLight
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitLineDialog(
    line: LrcLine,
    currentPlaybackMs: Long,
    onDismiss: () -> Unit,
    onConfirmSplit: (splitIndex: Int, newTimestampMs: Long) -> Unit
) {
    val fullText = line.text
    val words = remember(fullText) { fullText.split(" ").filter { it.isNotEmpty() } }

    var splitCharIndex by remember {
        val mid = fullText.length / 2
        // Find nearest space
        val nearestSpace = fullText.indexOf(' ', mid).takeIf { it != -1 } ?: mid
        mutableStateOf(nearestSpace)
    }

    var part2TimeMs by remember {
        val initialPart2Time = if (currentPlaybackMs > (line.timestampMs ?: 0L)) {
            currentPlaybackMs
        } else {
            (line.timestampMs ?: 0L) + 2000L
        }
        mutableLongStateOf(initialPart2Time)
    }

    val part1Text = fullText.substring(0, splitCharIndex.coerceIn(0, fullText.length)).trim()
    val part2Text = fullText.substring(splitCharIndex.coerceIn(0, fullText.length)).trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CallSplit,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Dividir Línea de Tiempo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Toca una palabra para cortar la frase en dos partes:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Word Chips to pick split point easily
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioSurfaceVariant)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    var accumulatedLen = 0
                    words.forEachIndexed { idx, word ->
                        val wordEndPos = accumulatedLen + word.length
                        val isPart1 = wordEndPos <= splitCharIndex
                        val isCutPoint = wordEndPos == splitCharIndex ||
                                (accumulatedLen < splitCharIndex && wordEndPos >= splitCharIndex)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPart1) NeonCyan.copy(alpha = 0.25f) else StudioSurfaceLight)
                                .border(
                                    width = if (isCutPoint) 1.5.dp else 0.5.dp,
                                    color = if (isCutPoint) NeonCyan else StudioBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    splitCharIndex = (wordEndPos + 1).coerceAtMost(fullText.length)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = word,
                                fontSize = 13.sp,
                                color = if (isPart1) NeonCyan else TextPrimary,
                                fontWeight = if (isPart1) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        accumulatedLen += word.length + 1
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Time selector for Part 2
                Text(
                    text = "Marca de tiempo para la 2ª parte:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option: Current playback time
                    OutlinedButton(
                        onClick = { part2TimeMs = currentPlaybackMs },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonCyan
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(NeonCyan))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tiempo actual", fontSize = 11.sp)
                            Text(formatTimestamp(currentPlaybackMs), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Option: +2.0 seconds after line 1
                    OutlinedButton(
                        onClick = { part2TimeMs = (line.timestampMs ?: 0L) + 2000L },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonViolet
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+2.0 seg", fontSize = 11.sp)
                            Text(formatTimestamp((line.timestampMs ?: 0L) + 2000L), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Result Preview Box
                Text(
                    text = "Resultado tras la división:",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = StudioDarkBg,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = line.formattedTimestamp(includeBrackets = true),
                                color = NeonCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = part1Text.ifBlank { "(vacío)" },
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatTimestamp(part2TimeMs),
                                color = NeonViolet,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = part2Text.ifBlank { "(vacío)" },
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmSplit(splitCharIndex, part2TimeMs)
                },
                enabled = part1Text.isNotBlank() && part2Text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF00363F)),
                modifier = Modifier.testTag("confirm_split_button")
            ) {
                Text("Dividir Línea", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = StudioSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    val hundredths = (ms % 1000) / 10
    return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
}
