package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.data.QualityPreset
import com.example.data.VideoInfoPreview
import com.example.util.FileUtils
import com.example.util.FolderOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val isEngineReady by viewModel.isEngineReady.collectAsState()
    val engineVersion by viewModel.engineVersion.collectAsState()
    val showYouTubeLogin by viewModel.showYouTubeLogin.collectAsState()

    var showFolderDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = currentTab != 0) {
        viewModel.setTab(0)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLibrary(context)
    }

    if (showYouTubeLogin) {
        YouTubeLoginDialog(
            onDismiss = { viewModel.closeYouTubeLogin() },
            onCookiesSaved = { viewModel.onCookiesUpdated(context) }
        )
    }

    if (showFolderDialog) {
        FolderSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showFolderDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "POWER PAPILO",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = if (isEngineReady) engineVersion else "Iniciando motor...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showFolderDialog = true },
                        modifier = Modifier.testTag("action_change_folder_topbar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Elegir carpeta de descargas"
                        )
                    }
                    if (currentTab == 1) {
                        IconButton(
                            onClick = { viewModel.refreshLibrary(context) },
                            modifier = Modifier.testTag("refresh_library_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar biblioteca"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Descargas") },
                    label = { Text("Descargas") },
                    modifier = Modifier.testTag("nav_downloads")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Biblioteca") },
                    label = { Text("Biblioteca") },
                    modifier = Modifier.testTag("nav_library")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Ajustes") },
                    label = { Text("Ajustes & Info") },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                when (currentTab) {
                    0 -> DownloaderTab(viewModel = viewModel, onOpenFolderDialog = { showFolderDialog = true })
                    1 -> LibraryTab(viewModel = viewModel, onOpenFolderDialog = { showFolderDialog = true })
                    2 -> SettingsTab(viewModel = viewModel, onOpenFolderDialog = { showFolderDialog = true })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloaderTab(
    viewModel: HomeViewModel,
    onOpenFolderDialog: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val downloadMode by viewModel.downloadMode.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val currentDir by viewModel.currentDownloadDir.collectAsState()

    // Estado para modo Individual
    val urlInput by viewModel.urlInput.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val previewInfo by viewModel.previewInfo.collectAsState()
    val analysisError by viewModel.analysisError.collectAsState()

    // Estado para modo Playlist
    val playlistUrlInput by viewModel.playlistUrlInput.collectAsState()
    val playlistPreset by viewModel.playlistPreset.collectAsState()
    val isAnalyzingPlaylist by viewModel.isAnalyzingPlaylist.collectAsState()
    val playlistPreviewInfo by viewModel.playlistPreviewInfo.collectAsState()
    val playlistError by viewModel.playlistError.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Selector Claro entre los dos apartados solicitados: Individual vs Playlist
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    SecondaryTabRow(
                        selectedTabIndex = downloadMode,
                        containerColor = Color.Transparent
                    ) {
                        Tab(
                            selected = downloadMode == 0,
                            onClick = { viewModel.setDownloadMode(0) },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Descarga Individual", fontWeight = FontWeight.Bold)
                                }
                            },
                            modifier = Modifier.testTag("tab_individual_download")
                        )
                        Tab(
                            selected = downloadMode == 1,
                            onClick = { viewModel.setDownloadMode(1) },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text("Playlist en Cadena", fontWeight = FontWeight.Bold)
                                }
                            },
                            modifier = Modifier.testTag("tab_playlist_download")
                        )
                    }
                }
            }
        }

        // Indicador de carpeta de destino seleccionable
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFolderDialog() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Guardar descargas en:",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                            Text(
                                text = currentDir.ifBlank { "Carpeta de Descargas" },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    TextButton(onClick = onOpenFolderDialog) {
                        Text("Cambiar", fontSize = 12.sp)
                    }
                }
            }
        }

        // ==========================================
        // APARTADO 1: DESCARGA INDIVIDUAL (EL QUE ELIJO)
        // ==========================================
        if (downloadMode == 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Enlace del Video, Audio o Imagen Individual",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { viewModel.onUrlChange(it) },
                            placeholder = { Text("Pega el enlace específico que quieres descargar...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("url_input_field"),
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                viewModel.analyzeUrl(context)
                            }),
                            trailingIcon = {
                                if (urlInput.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onUrlChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val text = clipboardManager.getText()?.text ?: ""
                                    if (text.isNotBlank()) viewModel.onPasteClicked(context, text)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("paste_button")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pegar")
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.analyzeUrl(context)
                                },
                                enabled = urlInput.isNotBlank() && !isAnalyzing,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("analyze_button")
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Analizando...")
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Analizar")
                                }
                            }
                        }

                        // Muestras rápidas garantizadas
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Prueba Rápida Directa (100% Funcional):",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                viewModel.sampleMediaList.forEach { sample ->
                                    SuggestionChip(
                                        onClick = { viewModel.loadSample(context, sample) },
                                        label = { Text(sample.title, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        if (analysisError != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(text = analysisError ?: "", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onErrorContainer))
                                }
                            }
                        }
                    }
                }
            }

            // Previsualización de video individual
            previewInfo?.let { preview ->
                item {
                    PreviewVideoCard(
                        preview = preview,
                        onClear = { viewModel.onUrlChange("") }
                    )
                }
            }

            // Selector de formato individual (incluyendo WEBP)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Formato de Descarga (Elige Video, Audio o WEBP)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QualityPreset.values().forEach { preset ->
                                val isSelected = selectedPreset == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onPresetChange(preset) },
                                    label = { Text(preset.title) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when {
                                                preset.isWebP -> Icons.Default.Image
                                                preset.isAudioOnly -> Icons.Default.MusicNote
                                                else -> Icons.Default.Videocam
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("preset_${preset.id}")
                                )
                            }
                        }

                        Text(
                            text = selectedPreset.subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        Spacer(Modifier.height(4.dp))

                        // Botón exclusivo de descarga individual
                        Button(
                            onClick = { viewModel.startIndividualDownload(context) },
                            enabled = urlInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_individual_download_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Descargar Archivo Seleccionado (${selectedPreset.title})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // APARTADO 2: LISTA DE REPRODUCCIÓN (PLAYLIST)
        // ==========================================
        if (downloadMode == 1) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            Column {
                                Text(
                                    text = "Descarga de Playlist en Cadena",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Descarga todos los videos en cadena y los organiza en una subcarpeta.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = playlistUrlInput,
                            onValueChange = { viewModel.onPlaylistUrlChange(it) },
                            placeholder = { Text("Pega aquí el enlace de la Playlist de YouTube...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("playlist_url_input_field"),
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                viewModel.analyzePlaylistUrl(context)
                            }),
                            trailingIcon = {
                                if (playlistUrlInput.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onPlaylistUrlChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val text = clipboardManager.getText()?.text ?: ""
                                    if (text.isNotBlank()) viewModel.onPastePlaylistClicked(context, text)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("paste_playlist_button")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pegar")
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.analyzePlaylistUrl(context)
                                },
                                enabled = playlistUrlInput.isNotBlank() && !isAnalyzingPlaylist,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("analyze_playlist_button")
                            ) {
                                if (isAnalyzingPlaylist) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Verificando...")
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Analizar")
                                }
                            }
                        }

                        if (playlistError != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = playlistError ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            playlistPreviewInfo?.let { preview ->
                item {
                    PreviewVideoCard(
                        preview = preview,
                        onClear = { viewModel.onPlaylistUrlChange("") }
                    )
                }
            }

            // Calidad para la lista completa y Botón de Inicio de Playlist en Cadena
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Calidad para toda la Playlist:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(QualityPreset.BEST, QualityPreset.P720, QualityPreset.AUDIO_MP3, QualityPreset.AUDIO_M4A).forEach { preset ->
                                val isSelected = playlistPreset == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onPlaylistPresetChange(preset) },
                                    label = { Text(preset.title) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (preset.isAudioOnly) Icons.Default.MusicNote else Icons.Default.Videocam,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Botón exclusivo de descarga de playlist
                        Button(
                            onClick = { viewModel.startPlaylistChainDownload(context) },
                            enabled = playlistUrlInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_playlist_chain_download_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Descargar Playlist en Cadena",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // Cola de Descargas Activas
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cola de Descargas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                val activeCount = downloads.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
                if (activeCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("$activeCount activas")
                    }
                }
            }
        }

        val nonCompletedDownloads = downloads.filter { it.status != DownloadStatus.COMPLETED }
        if (nonCompletedDownloads.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                        Text(
                            text = "No hay descargas en proceso",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        } else {
            items(nonCompletedDownloads, key = { it.id }) { item ->
                DownloadQueueItemCard(
                    item = item,
                    onCancel = { viewModel.cancelDownload(item.id) },
                    onRetry = { viewModel.retryDownload(context, item.id) },
                    onDelete = { viewModel.deleteDownload(item.id, false) },
                    onLoginYouTube = { viewModel.openYouTubeLogin() }
                )
            }
        }
    }
}

@Composable
fun PreviewVideoCard(
    preview: VideoInfoPreview,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
            ) {
                if (!preview.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = preview.thumbnailUrl,
                        contentDescription = "Miniatura",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (preview.isPlaylist) Icons.AutoMirrored.Filled.PlaylistPlay else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (preview.isPlaylist) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "PLAYLIST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else if (preview.durationSeconds > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = FileUtils.formatDuration(preview.durationSeconds),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preview.uploader ?: "Canal",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Quitar")
            }
        }
    }
}

@Composable
fun DownloadQueueItemCard(
    item: DownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onLoginYouTube: () -> Unit
) {
    val isBotBlock = item.errorMessage?.contains("antibot", ignoreCase = true) == true ||
            item.errorMessage?.contains("verificación", ignoreCase = true) == true ||
            item.errorMessage?.contains("bot", ignoreCase = true) == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (item.isPlaylist) "Playlist • ${item.preset.title}" else item.preset.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val statusLabel = when (item.status) {
                            DownloadStatus.QUEUED -> "En espera"
                            DownloadStatus.FETCHING_INFO -> "Analizando"
                            DownloadStatus.DOWNLOADING -> "${item.progress.toInt()}%"
                            DownloadStatus.CONVERTING -> "Convirtiendo"
                            DownloadStatus.FAILED -> "Error"
                            DownloadStatus.CANCELLED -> "Cancelado"
                            else -> ""
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = when (item.status) {
                                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                    DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Medium
                            )
                        )

                        if (item.downloadSpeed.isNotBlank() && item.status == DownloadStatus.DOWNLOADING) {
                            Text(
                                text = "• ${item.downloadSpeed}",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }
                        if (item.etaSeconds > 0 && item.status == DownloadStatus.DOWNLOADING) {
                            Text(
                                text = "• ETA: ${FileUtils.formatEta(item.etaSeconds)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                }

                Row {
                    if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.FETCHING_INFO) {
                        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    } else if (item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELLED) {
                        IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reintentar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar de la lista", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            } else if (item.status == DownloadStatus.FETCHING_INFO || item.status == DownloadStatus.CONVERTING) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                )

                if (isBotBlock && item.status == DownloadStatus.FAILED) {
                    Button(
                        onClick = onLoginYouTube,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("action_login_youtube_from_card"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Vincular Sesión de YouTube para Continuar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryTab(
    viewModel: HomeViewModel,
    onOpenFolderDialog: () -> Unit
) {
    val context = LocalContext.current
    val libraryItems by viewModel.libraryItems.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val currentDir by viewModel.currentDownloadDir.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Carpeta de la biblioteca
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = currentDir.ifBlank { "Descargas" },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onOpenFolderDialog) {
                    Text("Cambiar Carpeta", fontSize = 11.sp)
                }
            }
        }

        OutlinedTextField(
            value = searchFilter,
            onValueChange = { viewModel.onSearchFilterChange(it) },
            placeholder = { Text("Buscar en descargas...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchFilter.isNotBlank()) {
                    IconButton(onClick = { viewModel.onSearchFilterChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Borrar filtro")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        if (libraryItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (searchFilter.isNotBlank()) "Sin resultados para la búsqueda" else "No hay archivos en la biblioteca",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Cada archivo descargado aparecerá aquí automáticamente. Se actualiza de forma instantánea al finalizar cualquier descarga.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(libraryItems, key = { it.id }) { item ->
                    LibraryItemCard(
                        item = item,
                        onOpen = { viewModel.openFile(context, item) },
                        onShare = { viewModel.shareFile(context, item) },
                        onDelete = { viewModel.deleteDownload(item.id, true) }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryItemCard(
    item: DownloadItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isAudio = item.preset.isAudioOnly || item.fileName?.endsWith(".mp3", ignoreCase = true) == true || item.fileName?.endsWith(".m4a", ignoreCase = true) == true
    val isWebp = item.preset.isWebP || item.fileName?.endsWith(".webp", ignoreCase = true) == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    isWebp -> MaterialTheme.colorScheme.tertiaryContainer
                    isAudio -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            isWebp -> Icons.Default.Image
                            isAudio -> Icons.Default.Audiotrack
                            else -> Icons.Default.Videocam
                        },
                        contentDescription = null,
                        tint = when {
                            isWebp -> MaterialTheme.colorScheme.onTertiaryContainer
                            isAudio -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.totalBytes > 0) {
                        Text(
                            text = FileUtils.formatBytes(item.totalBytes),
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                    val ext = item.fileName?.substringAfterLast('.', "")?.uppercase() ?: if (isWebp) "WEBP" else ""
                    if (ext.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = ext,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Row {
                IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Compartir archivo", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir / Abrir", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    viewModel: HomeViewModel,
    onOpenFolderDialog: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isEngineReady by viewModel.isEngineReady.collectAsState()
    val isYtDlpAvailable by viewModel.isYtDlpAvailable.collectAsState()
    val engineVersion by viewModel.engineVersion.collectAsState()
    val cookieSummary by viewModel.cookieSummary.collectAsState()
    val currentDownloadDir by viewModel.currentDownloadDir.collectAsState()

    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInputText by remember { mutableStateOf("") }

    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Pegar cookies.txt Manual")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pega el contenido de cookies.txt si prefieres usar un archivo exportado de tu PC:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = cookieInputText,
                        onValueChange = { cookieInputText = it },
                        placeholder = { Text("# Netscape HTTP Cookie File...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 8,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text ?: ""
                                if (clip.isNotBlank()) cookieInputText = clip
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pegar")
                        }
                        if (cookieSummary.contains("activa", ignoreCase = true) || cookieSummary.contains("Activas")) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearCookies(context)
                                    cookieInputText = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Borrar")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cookieInputText.isNotBlank()) viewModel.saveCookies(context, cookieInputText)
                        showCookieDialog = false
                    }
                ) {
                    Text("Guardar Cookies")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Carpeta de Almacenamiento Seleccionable
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Ubicación de Descargas", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Text(
                        text = "Ruta seleccionada:",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentDownloadDir.ifBlank { "Carpeta de Descargas" },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Button(
                        onClick = onOpenFolderDialog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_change_download_directory")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Elegir o Crear Carpeta de Guardado")
                    }
                }
            }
        }

        // Estado de los Motores
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            imageVector = if (isEngineReady) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isEngineReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(text = "Estado de los Motores", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = if (isEngineReady) "Motores operativos y listos" else "Inicializando...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Motor activo:", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = engineVersion, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }

                    if (isYtDlpAvailable) {
                        Button(
                            onClick = { viewModel.updateEngineNow() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Buscar Actualizaciones de yt-dlp")
                        }
                    }
                }
            }
        }

        // Bypass Anti-Bot de YouTube
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Bypass Anti-Bot de YouTube", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    Text(
                        text = "Estado: $cookieSummary",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (cookieSummary.contains("activa", ignoreCase = true) || cookieSummary.contains("Activas")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Button(
                        onClick = { viewModel.openYouTubeLogin() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_youtube_login_sheet_button")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Vincular Sesión de YouTube con Navegador")
                    }

                    OutlinedButton(
                        onClick = {
                            cookieInputText = viewModel.getCookiesText(context)
                            showCookieDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("O importar cookies.txt manualmente")
                    }
                }
            }
        }
    }
}

@Composable
fun FolderSelectionDialog(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentDir by viewModel.currentDownloadDir.collectAsState()
    val options = remember { viewModel.getFolderOptions(context) }
    var selectedPath by remember { mutableStateOf(currentDir) }
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Elegir Carpeta de Guardado", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        text = "Selecciona dónde deseas guardar todas tus descargas de video, audio e imágenes:",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                items(options) { option ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedPath == option.path) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPath = option.path }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPath == option.path,
                                onClick = { selectedPath = option.path }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = option.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "O crear una nueva subcarpeta personalizada:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("Ej: Mis Videos") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    val success = viewModel.createCustomDestinationFolder(context, newFolderName)
                                    if (success) {
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = newFolderName.isNotBlank()
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Crear")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.setDestinationFolder(context, selectedPath)
                    onDismiss()
                }
            ) {
                Text("Guardar Carpeta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
