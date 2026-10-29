package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LrcLine
import com.example.model.LrcParser
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EditLineDialog(
    line: LrcLine,
    currentPlaybackMs: Long,
    onDismiss: () -> Unit,
    onSave: (newText: String, newTimestampMs: Long?) -> Unit
) {
    var text by remember { mutableStateOf(line.text) }

    val initialMs = line.timestampMs ?: 0L
    val initialTotalSec = initialMs / 1000
    var minutesStr by remember { mutableStateOf((initialTotalSec / 60).toString()) }
    var secondsStr by remember { mutableStateOf((initialTotalSec % 60).toString()) }
    var hundredthsStr by remember { mutableStateOf(((initialMs % 1000) / 10).toString()) }
    var isTimestampCleared by remember { mutableStateOf(line.timestampMs == null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Editar Línea de Letra",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Texto de la línea:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_line_text_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = StudioBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Marca de tiempo (Min : Seg . Cent):",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = minutesStr,
                        onValueChange = {
                            if (it.length <= 3 && it.all { c -> c.isDigit() }) {
                                minutesStr = it
                                isTimestampCleared = false
                            }
                        },
                        label = { Text("Min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text(":", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    OutlinedTextField(
                        value = secondsStr,
                        onValueChange = {
                            if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                secondsStr = it
                                isTimestampCleared = false
                            }
                        },
                        label = { Text("Seg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text(".", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                    OutlinedTextField(
                        value = hundredthsStr,
                        onValueChange = {
                            if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                hundredthsStr = it
                                isTimestampCleared = false
                            }
                        },
                        label = { Text("Cent") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick buttons: "Fijar al tiempo actual" or "Limpiar tiempo"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val totalSec = currentPlaybackMs / 1000
                            minutesStr = (totalSec / 60).toString()
                            secondsStr = (totalSec % 60).toString()
                            hundredthsStr = ((currentPlaybackMs % 1000) / 10).toString()
                            isTimestampCleared = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Usar tiempo actual", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { isTimestampCleared = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRose)
                    ) {
                        Text("Sin sincronizar", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTs = if (isTimestampCleared) {
                        null
                    } else {
                        val m = minutesStr.toLongOrNull() ?: 0L
                        val s = secondsStr.toLongOrNull() ?: 0L
                        val cs = hundredthsStr.toLongOrNull() ?: 0L
                        LrcParser.parseTimestampToMs(m, s, cs)
                    }
                    onSave(text, finalTs)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF00363F)),
                modifier = Modifier.testTag("save_line_button")
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
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
