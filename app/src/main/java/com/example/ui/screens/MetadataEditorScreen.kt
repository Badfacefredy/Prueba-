package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.collectAsStateWithLifecycleComposable
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanLight
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.LyricSyncViewModel
import java.util.Locale

@Composable
fun MetadataEditorScreen(
    viewModel: LyricSyncViewModel,
    modifier: Modifier = Modifier,
    onNavigateToExport: () -> Unit = {}
) {
    val lrcDoc by viewModel.lrcDocument.collectAsStateWithLifecycleComposable()
    val audioSource by viewModel.currentAudioSource.collectAsStateWithLifecycleComposable()
    val durationMs by viewModel.playerManager.durationMs.collectAsStateWithLifecycleComposable()

    var title by remember(lrcDoc.metadata.title) { mutableStateOf(lrcDoc.metadata.title) }
    var artist by remember(lrcDoc.metadata.artist) { mutableStateOf(lrcDoc.metadata.artist) }
    var album by remember(lrcDoc.metadata.album) { mutableStateOf(lrcDoc.metadata.album) }
    var author by remember(lrcDoc.metadata.author) { mutableStateOf(lrcDoc.metadata.author) }
    var creator by remember(lrcDoc.metadata.creator) { mutableStateOf(lrcDoc.metadata.creator) }
    var length by remember(lrcDoc.metadata.length) { mutableStateOf(lrcDoc.metadata.length) }
    var offsetMsStr by remember(lrcDoc.metadata.offsetMs) { mutableStateOf(lrcDoc.metadata.offsetMs.toString()) }

    fun commitMetadata() {
        val parsedOffset = offsetMsStr.toLongOrNull() ?: 0L
        viewModel.updateMetadata(
            title = title,
            artist = artist,
            album = album,
            creator = creator,
            author = author,
            length = length,
            offsetMs = parsedOffset
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioDarkBg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonCyan, NeonViolet)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Editor de Metadatos LRC",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Configura las etiquetas de cabecera ([ti], [ar], [al]) antes de exportar",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Quick auto-fill button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
            color = StudioSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Archivo activo: ${audioSource?.displayName ?: "Ninguno"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Puedes rellenar los datos automáticamente usando el audio",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                OutlinedButton(
                    onClick = {
                        audioSource?.let { src ->
                            if (title.isBlank()) {
                                title = src.displayName.substringBeforeLast(".")
                            }
                            if (length.isBlank() && durationMs > 0) {
                                val totalSec = durationMs / 1000
                                length = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
                            }
                            commitMetadata()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Text("Autocompletar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Metadata Fields Form
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "ETIQUETAS OFICIALES LRC",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )

                // 1. [ti:Title]
                TagInputField(
                    tag = "ti",
                    label = "Título de la Canción (Title)",
                    value = title,
                    onValueChange = {
                        title = it
                        commitMetadata()
                    },
                    icon = Icons.Default.MusicNote,
                    placeholder = "Ejemplo: Bohemian Rhapsody",
                    accentColor = NeonCyan,
                    testTag = "metadata_title_input"
                )

                // 2. [ar:Artist]
                TagInputField(
                    tag = "ar",
                    label = "Artista o Intérprete (Artist)",
                    value = artist,
                    onValueChange = {
                        artist = it
                        commitMetadata()
                    },
                    icon = Icons.Default.Person,
                    placeholder = "Ejemplo: Queen",
                    accentColor = NeonViolet,
                    testTag = "metadata_artist_input"
                )

                // 3. [al:Album]
                TagInputField(
                    tag = "al",
                    label = "Álbum o Colección (Album)",
                    value = album,
                    onValueChange = {
                        album = it
                        commitMetadata()
                    },
                    icon = Icons.Default.Album,
                    placeholder = "Ejemplo: A Night at the Opera",
                    accentColor = NeonViolet,
                    testTag = "metadata_album_input"
                )

                // 4. [au:Author]
                TagInputField(
                    tag = "au",
                    label = "Autor, Compositor o Letrista (Author)",
                    value = author,
                    onValueChange = {
                        author = it
                        commitMetadata()
                    },
                    icon = Icons.Default.DriveFileRenameOutline,
                    placeholder = "Ejemplo: Freddie Mercury",
                    accentColor = NeonAmber,
                    testTag = "metadata_author_input"
                )

                // 5. [by:Creator]
                TagInputField(
                    tag = "by",
                    label = "Creador del archivo LRC (By / Tagged by)",
                    value = creator,
                    onValueChange = {
                        creator = it
                        commitMetadata()
                    },
                    icon = Icons.Default.Description,
                    placeholder = "LyricSync",
                    accentColor = TextSecondary,
                    testTag = "metadata_creator_input"
                )

                // 6. [length:Length]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TagInputField(
                            tag = "length",
                            label = "Duración (Length mm:ss)",
                            value = length,
                            onValueChange = {
                                length = it
                                commitMetadata()
                            },
                            icon = Icons.Default.Timer,
                            placeholder = "03:45",
                            accentColor = NeonGreen,
                            testTag = "metadata_length_input"
                        )
                    }

                    if (durationMs > 0) {
                        OutlinedButton(
                            onClick = {
                                val totalSec = durationMs / 1000
                                length = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
                                commitMetadata()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Usar audio", fontSize = 11.sp)
                        }
                    }
                }

                // 7. [offset:OffsetMs]
                TagInputField(
                    tag = "offset",
                    label = "Desfase global de tiempo en milisegundos (Offset)",
                    value = offsetMsStr,
                    onValueChange = {
                        offsetMsStr = it
                        commitMetadata()
                    },
                    icon = Icons.Default.Schedule,
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                    accentColor = NeonAmber,
                    testTag = "metadata_offset_input"
                )
            }
        }

        // Live Header Preview Box
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
                    .padding(14.dp)
            ) {
                Text(
                    text = "VISTA PREVIA DEL ENCABEZADO LRC",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioDarkBg)
                        .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    val previewText = buildString {
                        if (title.isNotBlank()) appendLine("[ti:$title]")
                        if (artist.isNotBlank()) appendLine("[ar:$artist]")
                        if (album.isNotBlank()) appendLine("[al:$album]")
                        if (author.isNotBlank()) appendLine("[au:$author]")
                        if (creator.isNotBlank()) appendLine("[by:$creator]")
                        if (length.isNotBlank()) appendLine("[length:$length]")
                        val off = offsetMsStr.toLongOrNull() ?: 0L
                        if (off != 0L) appendLine("[offset:$off]")
                        append("[re:LyricSync for Android]")
                    }
                    Text(
                        text = previewText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = NeonCyanLight,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Proceed to Export Button
        Button(
            onClick = {
                commitMetadata()
                onNavigateToExport()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("save_metadata_and_export_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(NeonCyan, NeonViolet)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GUARDAR Y CONTINUAR A EXPORTAR",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TagInputField(
    tag: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String,
    accentColor: Color,
    keyboardType: KeyboardType = KeyboardType.Text,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextMuted) },
        leadingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "[$tag]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpiar campo", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = StudioBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = accentColor,
            unfocusedLabelColor = TextSecondary
        )
    )
}
