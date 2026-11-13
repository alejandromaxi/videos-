package com.example.ui

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.BrowserFolderEntry
import com.example.util.FileUtils
import com.example.util.StorageType
import java.io.File

private enum class BrowseMode {
    LOCAL,
    SAF,
    PUBLIC_DOWNLOADS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderSelectionDialog(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentLabel by viewModel.currentDownloadDir.collectAsState()
    val storageVolumes = remember { viewModel.getStorageVolumes(context) }
    val quickOptions = remember { viewModel.getFolderOptions(context) }

    val initialStorageType = remember { FileUtils.getStorageType(context) }
    val initialLocalPath = remember { FileUtils.getDownloadDirectory(context).absolutePath }
    val initialSafUri = remember { FileUtils.getSafTreeUri(context) }
    val initialSafDocId = remember {
        FileUtils.getSafSubfolderDocId(context) ?: initialSafUri?.let {
            runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull()
        }
    }

    var browseMode by remember {
        mutableStateOf(
            when (initialStorageType) {
                StorageType.PUBLIC_DOWNLOADS -> BrowseMode.PUBLIC_DOWNLOADS
                StorageType.SAF_TREE -> if (initialSafUri != null) BrowseMode.SAF else BrowseMode.LOCAL
                StorageType.LOCAL_DIR -> BrowseMode.LOCAL
            }
        )
    }

    var currentLocalPath by remember { mutableStateOf(initialLocalPath) }
    var activeSafTreeUri by remember { mutableStateOf(initialSafUri) }
    val safStack = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            if (initialSafDocId != null) {
                add(initialSafDocId to "Carpeta Elegida")
            }
        }
    }

    var refreshTrigger by remember { mutableStateOf(0) }
    var newFolderName by remember { mutableStateOf("") }
    var showCreateFolderInput by remember { mutableStateOf(false) }

    val localRootBoundary = remember {
        context.getExternalFilesDir(null)?.parentFile?.absolutePath ?: context.filesDir.absolutePath
    }

    // Lanzador nativo de Android (Storage Access Framework) para navegar Descargas, Interno y Memoria SD
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = viewModel.setSafTreeFolder(context, uri)
            if (saved) {
                activeSafTreeUri = uri
                safStack.clear()
                runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()?.let { docId ->
                    safStack.add(docId to FileUtils.getCurrentDestinationLabel(context))
                }
                browseMode = BrowseMode.SAF
                refreshTrigger++
            }
        }
    }

    // Lanzador para abrir un StorageVolume específico (e.g., Tarjeta SD) en Android 10+
    val volumePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val saved = viewModel.setSafTreeFolder(context, uri)
                if (saved) {
                    activeSafTreeUri = uri
                    safStack.clear()
                    runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()?.let { docId ->
                        safStack.add(docId to FileUtils.getCurrentDestinationLabel(context))
                    }
                    browseMode = BrowseMode.SAF
                    refreshTrigger++
                }
            }
        }
    }

    val currentSafDocId = safStack.lastOrNull()?.first

    val childFolders: List<BrowserFolderEntry> = remember(browseMode, currentLocalPath, activeSafTreeUri, currentSafDocId, refreshTrigger) {
        when (browseMode) {
            BrowseMode.LOCAL -> FileUtils.listLocalSubfolders(currentLocalPath)
            BrowseMode.SAF -> {
                val uri = activeSafTreeUri
                if (uri != null) FileUtils.listSafSubfolders(context, uri, currentSafDocId)
                else emptyList()
            }
            BrowseMode.PUBLIC_DOWNLOADS -> emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Explorar y Elegir Carpeta",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ubicación activa actual
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Destino actual:",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = currentLabel.ifBlank { "Carpeta de Descargas" },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Explorador nativo del sistema (Permite buscar Descargas, Interno y Memoria SD)
                Button(
                    onClick = { openDocumentTreeLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("button_open_system_folder_picker"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Navegar Sistema (Descargas / Memoria SD)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Botones directos por volumen detectado (Interno / Tarjeta SD)
                if (storageVolumes.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        storageVolumes.forEach { vol ->
                            AssistChip(
                                onClick = {
                                    if (vol.openTreeIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        try {
                                            volumePickerLauncher.launch(vol.openTreeIntent)
                                        } catch (e: Exception) {
                                            openDocumentTreeLauncher.launch(null)
                                        }
                                    } else {
                                        openDocumentTreeLauncher.launch(null)
                                    }
                                },
                                label = {
                                    Text(
                                        text = if (vol.isRemovable) "Explorar ${vol.label}" else vol.label,
                                        fontSize = 11.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (vol.isRemovable) Icons.Default.SdStorage else Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Accesos rápidos internos + Descargas Públicas
                Text(
                    text = "Accesos Rápidos y Navegador Interno:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        AssistChip(
                            onClick = {
                                browseMode = BrowseMode.PUBLIC_DOWNLOADS
                            },
                            label = { Text("Descargas (Pública)", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                        )
                    }

                    quickOptions.forEach { opt ->
                        AssistChip(
                            onClick = {
                                browseMode = BrowseMode.LOCAL
                                currentLocalPath = opt.path
                                refreshTrigger++
                            },
                            label = { Text(opt.name, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (opt.isRemovableSd) Icons.Default.SdStorage else Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        )
                    }
                }

                // Vista navegable del directorio seleccionado actualmente
                if (browseMode == BrowseMode.PUBLIC_DOWNLOADS) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = "Carpeta Pública Descargas (/Download/POWER PAPILO)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Las descargas aparecerán directamente en tu app de Archivos / Descargas del teléfono.",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                } else {
                    // Cabecera de navegación (Subir nivel + Crear carpeta)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val canGoUp = when (browseMode) {
                                    BrowseMode.LOCAL -> {
                                        val parent = File(currentLocalPath).parentFile
                                        parent != null && parent.absolutePath.length >= localRootBoundary.length && parent.canRead()
                                    }
                                    BrowseMode.SAF -> safStack.size > 1
                                    BrowseMode.PUBLIC_DOWNLOADS -> false
                                }

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (canGoUp) {
                                        IconButton(
                                            onClick = {
                                                if (browseMode == BrowseMode.LOCAL) {
                                                    File(currentLocalPath).parentFile?.let {
                                                        currentLocalPath = it.absolutePath
                                                        refreshTrigger++
                                                    }
                                                } else if (browseMode == BrowseMode.SAF && safStack.size > 1) {
                                                    safStack.removeAt(safStack.lastIndex)
                                                    refreshTrigger++
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Subir carpeta",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = when (browseMode) {
                                            BrowseMode.LOCAL -> currentLocalPath
                                            BrowseMode.SAF -> safStack.lastOrNull()?.second ?: "Carpeta Seleccionada"
                                            BrowseMode.PUBLIC_DOWNLOADS -> "Descargas"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showCreateFolderInput = !showCreateFolderInput },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CreateNewFolder,
                                        contentDescription = "Nueva carpeta",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (showCreateFolderInput) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newFolderName,
                                        onValueChange = { newFolderName = it },
                                        placeholder = { Text("Nombre de carpeta...", fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Button(
                                        onClick = {
                                            if (newFolderName.isNotBlank()) {
                                                if (browseMode == BrowseMode.LOCAL) {
                                                    FileUtils.createLocalSubfolder(currentLocalPath, newFolderName)?.let { created ->
                                                        currentLocalPath = created.absolutePath
                                                        newFolderName = ""
                                                        showCreateFolderInput = false
                                                        refreshTrigger++
                                                    }
                                                } else if (browseMode == BrowseMode.SAF && activeSafTreeUri != null) {
                                                    val createdId = FileUtils.createSafSubfolder(
                                                        context,
                                                        activeSafTreeUri!!,
                                                        currentSafDocId,
                                                        newFolderName
                                                    )
                                                    if (createdId != null) {
                                                        safStack.add(createdId to newFolderName.trim())
                                                        newFolderName = ""
                                                        showCreateFolderInput = false
                                                        refreshTrigger++
                                                    }
                                                }
                                            }
                                        },
                                        enabled = newFolderName.isNotBlank()
                                    ) {
                                        Text("Crear", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Lista de subcarpetas navegables
                            if (childFolders.isEmpty()) {
                                Text(
                                    text = "No hay subcarpetas aquí. Puedes usar esta carpeta o crear una nueva arriba.",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 170.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(childFolders) { folder ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (folder.isSaf) {
                                                        safStack.add(folder.pathOrDocId to folder.name)
                                                    } else {
                                                        currentLocalPath = folder.pathOrDocId
                                                    }
                                                    refreshTrigger++
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = folder.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (browseMode) {
                        BrowseMode.PUBLIC_DOWNLOADS -> {
                            viewModel.setPublicDownloadsFolder(context)
                        }
                        BrowseMode.LOCAL -> {
                            viewModel.setDestinationFolder(context, currentLocalPath)
                        }
                        BrowseMode.SAF -> {
                            val uri = activeSafTreeUri
                            if (uri != null) {
                                viewModel.setSafTreeFolder(
                                    context = context,
                                    treeUri = uri,
                                    subfolderDocId = currentSafDocId
                                )
                            }
                        }
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_selected_folder_button")
            ) {
                Text("Usar Esta Carpeta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
