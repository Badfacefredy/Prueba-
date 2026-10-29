package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PasteLyricsDialog
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
fun SongLyricsScreen(
    viewModel: LyricSyncViewModel,
    modifier: Modifier = Modifier
) {
    val audioSource by viewModel.currentAudioSource.collectAsStateWithLifecycleComposable()
    val lrcDoc by viewModel.lrcDocument.collectAsStateWithLifecycleComposable()
    val durationMs by viewModel.playerManager.durationMs.collectAsStateWithLifecycleComposable()

    var showPasteDialog by remember { mutableStateOf(false) }

    // Launcher for picking audio file (.mp3, .wav, .m4a, etc.)
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadAudioFromUri(uri)
        }
    }

    // Launcher for picking .lrc or .txt lyrics file
    val lyricsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadLyricsFromUri(uri)
        }
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
        // App Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonCyan, NeonViolet)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Gestión de Archivos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Carga tu música y tus letras para comenzar a sincronizar",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Current Active Song & Lyrics Card
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
                    text = "ESTADO ACTUAL",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = NeonViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = audioSource?.displayName ?: "Sin archivo de audio",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        val durSeconds = durationMs / 1000
                        val durMin = durSeconds / 60
                        val durSec = durSeconds % 60
                        Text(
                            text = if (durationMs > 0) "Duración: %02d:%02d".format(durMin, durSec) else "00:00",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    if (audioSource?.isMp3 == true) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("MP3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val syncedCount = lrcDoc.lines.count { it.isSynced }
                        Text(
                            text = "Letras: ${lrcDoc.lines.size} líneas ($syncedCount sincronizadas)",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        if (lrcDoc.metadata.title.isNotBlank() || lrcDoc.metadata.artist.isNotBlank()) {
                            Text(
                                text = "${lrcDoc.metadata.title} - ${lrcDoc.metadata.artist}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Action 1: Select Audio File
        ActionSelectCard(
            title = "Seleccionar Canción del Dispositivo",
            description = "Abre cualquier archivo MP3, WAV o M4A. Si ya tiene letras ID3v2 incrustadas, se cargarán automáticamente.",
            icon = Icons.Default.FolderOpen,
            iconTint = NeonCyan,
            buttonText = "Buscar Canción (Audio)",
            onClick = {
                audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/mp4"))
            },
            testTag = "pick_audio_button"
        )

        // Action 2: Select LRC File
        ActionSelectCard(
            title = "Cargar Archivo .LRC o Letra",
            description = "Importa un archivo .lrc existente o un archivo de texto con las letras de tu canción.",
            icon = Icons.Default.Description,
            iconTint = NeonViolet,
            buttonText = "Cargar Archivo .LRC / .TXT",
            onClick = {
                lyricsPicker.launch(arrayOf("*/*", "text/plain", "application/x-subrip"))
            },
            testTag = "pick_lrc_button"
        )

        // Action 3: Paste lyrics
        ActionSelectCard(
            title = "Pegar o Escribir Letras",
            description = "Escribe la letra o pégala directamente desde el portapapeles sin necesidad de un archivo.",
            icon = Icons.Default.ContentPaste,
            iconTint = NeonAmber,
            buttonText = "Abrir Editor de Texto",
            onClick = { showPasteDialog = true },
            testTag = "paste_lyrics_button"
        )

        // Action 4: Demo Song Quick Loader
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
            color = StudioSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Canción Demo Melódica",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Prueba la app al instante con un tema de sintetizador integrado y su letra lista para practicar la sincronización manual.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.loadDemoSong(preSynced = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color(0xFF003822)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Demo Sincronizada", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.loadDemoSong(preSynced = false) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Demo Sin Sincronizar", fontSize = 12.sp)
                    }
                }
            }
        }

        // Quick Tutorial / Tips
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp)),
            color = StudioSurfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "¿Cómo sincronizar e incrustar?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Carga tu canción y las letras.\n2. Pulsa 'Auto-Sincronizar con Gemini AI' para alinear automáticamente cada línea con el compás y los picos de ritmo de la canción, o sincroniza manualmente en 'En Vivo' pulsando 'MARCAR TIEMPO'.\n3. En 'Editor', ajusta milisegundos con precisión, divide frases largas o fusiona versos.\n4. En 'Incrustar', pulsa 'Incrustar y Guardar Canción (.mp3)' o 'Exportar Archivo .LRC'.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showPasteDialog) {
        PasteLyricsDialog(
            initialText = lrcDoc.lines.joinToString("\n") { it.text },
            onDismiss = { showPasteDialog = false },
            onConfirm = { rawText ->
                viewModel.loadLyricsFromText(rawText)
                showPasteDialog = false
            }
        )
    }
}

@Composable
private fun ActionSelectCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    buttonText: String,
    onClick: () -> Unit,
    testTag: String
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = iconTint.copy(alpha = 0.85f), contentColor = Color.White)
            ) {
                Text(text = buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
