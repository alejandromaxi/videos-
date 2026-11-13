package com.example.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat
import java.util.Locale

enum class StorageType {
    LOCAL_DIR,
    PUBLIC_DOWNLOADS,
    SAF_TREE
}

data class FolderOption(
    val id: String,
    val name: String,
    val path: String,
    val description: String,
    val isRemovableSd: Boolean = false
)

data class StorageVolumeUi(
    val id: String,
    val label: String,
    val isRemovable: Boolean,
    val openTreeIntent: Intent?
)

data class BrowserFolderEntry(
    val name: String,
    val pathOrDocId: String,
    val isSaf: Boolean
)

data class ExportedMediaResult(
    val pathOrUri: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: Long
)

object FileUtils {

    private const val TAG = "FileUtils"
    private const val PREFS_NAME = "power_papilo_prefs"
    private const val KEY_STORAGE_TYPE = "storage_type"
    private const val KEY_CUSTOM_DOWNLOAD_DIR = "custom_download_directory"
    private const val KEY_SAF_TREE_URI = "saf_tree_uri"
    private const val KEY_SAF_SUBFOLDER_DOC_ID = "saf_subfolder_doc_id"
    private const val KEY_SAF_DISPLAY_PATH = "saf_display_path"

    fun getStorageType(context: Context): StorageType {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STORAGE_TYPE, StorageType.LOCAL_DIR.name)
        return try {
            StorageType.valueOf(raw ?: StorageType.LOCAL_DIR.name)
        } catch (e: Exception) {
            StorageType.LOCAL_DIR
        }
    }

    fun getDownloadDirectory(context: Context): File {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_CUSTOM_DOWNLOAD_DIR, null)

        if (!customPath.isNullOrBlank()) {
            try {
                val customDir = File(customPath)
                if (customDir.exists() || customDir.mkdirs()) {
                    return customDir
                }
            } catch (e: Exception) {
                // Fallback to default
            }
        }

        val defaultDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
    }

    fun getWorkingDirectory(context: Context, itemId: String): File {
        return when (getStorageType(context)) {
            StorageType.LOCAL_DIR -> getDownloadDirectory(context)
            StorageType.PUBLIC_DOWNLOADS, StorageType.SAF_TREE -> {
                File(context.cacheDir, "papilo_staging/$itemId").apply {
                    if (!exists()) mkdirs()
                }
            }
        }
    }

    fun setDownloadDirectory(context: Context, path: String): Boolean {
        return try {
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STORAGE_TYPE, StorageType.LOCAL_DIR.name)
                .putString(KEY_CUSTOM_DOWNLOAD_DIR, dir.absolutePath)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setPublicDownloadsDestination(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STORAGE_TYPE, StorageType.PUBLIC_DOWNLOADS.name)
                .apply()
            true
        } else {
            val defaultDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "Download")
            setDownloadDirectory(context, defaultDir.absolutePath)
        }
    }

    fun setSafTreeDestination(
        context: Context,
        treeUri: Uri,
        subfolderDocId: String? = null,
        customDisplayPath: String? = null
    ): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (e: SecurityException) {
                Log.w(TAG, "No se pudo tomar permiso persistente (puede que ya exista o sea emulador): ${e.message}")
            }

            val displayPath = customDisplayPath ?: formatSafDisplayPath(context, treeUri, subfolderDocId)

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STORAGE_TYPE, StorageType.SAF_TREE.name)
                .putString(KEY_SAF_TREE_URI, treeUri.toString())
                .putString(KEY_SAF_SUBFOLDER_DOC_ID, subfolderDocId)
                .putString(KEY_SAF_DISPLAY_PATH, displayPath)
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando SAF treeUri: ${e.message}", e)
            false
        }
    }

    fun getSafTreeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAF_TREE_URI, null) ?: return null
        return try {
            Uri.parse(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun getSafSubfolderDocId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAF_SUBFOLDER_DOC_ID, null)
    }

    fun getCurrentDestinationLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (getStorageType(context)) {
            StorageType.PUBLIC_DOWNLOADS -> "Descargas del Dispositivo (/Download/POWER PAPILO)"
            StorageType.SAF_TREE -> {
                prefs.getString(KEY_SAF_DISPLAY_PATH, null)
                    ?: getSafTreeUri(context)?.let { formatSafDisplayPath(context, it, getSafSubfolderDocId(context)) }
                    ?: "Carpeta Externa Seleccionada"
            }
            StorageType.LOCAL_DIR -> {
                val dir = getDownloadDirectory(context)
                val path = dir.absolutePath
                val removableDirs = getRemovableSdAppDirs(context)
                val matchedSd = removableDirs.firstOrNull { path.startsWith(it.absolutePath) }
                if (matchedSd != null) {
                    val rel = path.removePrefix(matchedSd.absolutePath).trimStart('/')
                    if (rel.isBlank()) "Memoria SD (App)" else "Memoria SD / $rel"
                } else {
                    val externalRoot = context.getExternalFilesDir(null)?.absolutePath
                    if (externalRoot != null && path.startsWith(externalRoot)) {
                        val rel = path.removePrefix(externalRoot).trimStart('/')
                        if (rel.isBlank()) "Almacenamiento Interno (App)" else "Interno (App) / $rel"
                    } else {
                        path
                    }
                }
            }
        }
    }

    private fun formatSafDisplayPath(context: Context, treeUri: Uri, subfolderDocId: String?): String {
        val docId = subfolderDocId ?: try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            null
        }

        if (!docId.isNullOrBlank()) {
            val parts = docId.split(":", limit = 2)
            if (parts.size == 2) {
                val volume = parts[0]
                val relPath = parts[1].trim('/')
                val volumeLabel = when {
                    volume.equals("primary", ignoreCase = true) -> "Almacenamiento Interno"
                    volume.equals("downloads", ignoreCase = true) -> "Descargas"
                    else -> "Memoria SD ($volume)"
                }
                return if (relPath.isBlank()) volumeLabel else "$volumeLabel / $relPath"
            }
        }

        return try {
            val targetDocId = subfolderDocId ?: DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return "Carpeta: $name"
                }
            }
            "Carpeta Seleccionada"
        } catch (e: Exception) {
            "Carpeta Externa"
        }
    }

    fun getRemovableSdAppDirs(context: Context): List<File> {
        return try {
            val dirs = context.getExternalFilesDirs(null)
            dirs.drop(1).filterNotNull().filter { dir ->
                try {
                    dir.exists() || dir.mkdirs()
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getStorageVolumesForPicker(context: Context): List<StorageVolumeUi> {
        val result = mutableListOf<StorageVolumeUi>()
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            val volumes = storageManager?.storageVolumes ?: emptyList()
            volumes.forEachIndexed { index, volume ->
                val label = volume.getDescription(context) ?: if (volume.isRemovable) "Memoria SD" else "Almacenamiento Interno"
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        volume.createOpenDocumentTreeIntent()
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
                result.add(
                    StorageVolumeUi(
                        id = volume.uuid ?: "vol_$index",
                        label = label,
                        isRemovable = volume.isRemovable,
                        openTreeIntent = intent
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron enumerar StorageVolumes: ${e.message}")
        }
        return result
    }

    fun getFolderOptions(context: Context): List<FolderOption> {
        val defaultDownloads = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")).absolutePath

        val musicDir = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "Music")).absolutePath

        val moviesDir = (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "Movies")).absolutePath

        val docsDir = (context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "Documents")).absolutePath

        val internalDir = File(context.filesDir, "Descargas_POWER_PAPILO").apply {
            if (!exists()) mkdirs()
        }.absolutePath

        val options = mutableListOf(
            FolderOption(
                id = "downloads",
                name = "Descargas (App)",
                path = defaultDownloads,
                description = "Carpeta de descargas estándar de la app"
            ),
            FolderOption(
                id = "music",
                name = "Música y Audio",
                path = musicDir,
                description = "Ideal para canciones y audios MP3/M4A"
            ),
            FolderOption(
                id = "movies",
                name = "Películas y Videos",
                path = moviesDir,
                description = "Ideal para coleccionar clips y videos"
            ),
            FolderOption(
                id = "documents",
                name = "Documentos",
                path = docsDir,
                description = "Carpeta de documentos"
            ),
            FolderOption(
                id = "internal",
                name = "Interno Privado",
                path = internalDir,
                description = "Espacio privado de POWER PAPILO"
            )
        )

        getRemovableSdAppDirs(context).forEachIndexed { index, sdDir ->
            val sdDownloads = File(sdDir, "Download").apply {
                if (!exists()) mkdirs()
            }
            options.add(
                FolderOption(
                    id = "sd_$index",
                    name = "Memoria SD ${if (index > 0) index + 1 else ""} (Descargas)",
                    path = sdDownloads.absolutePath,
                    description = "Guardar directamente en la tarjeta SD",
                    isRemovableSd = true
                )
            )
        }

        return options
    }

    fun listLocalSubfolders(dirPath: String): List<BrowserFolderEntry> {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
            ?.map {
                BrowserFolderEntry(
                    name = it.name,
                    pathOrDocId = it.absolutePath,
                    isSaf = false
                )
            } ?: emptyList()
    }

    fun listSafSubfolders(context: Context, treeUri: Uri, parentDocId: String?): List<BrowserFolderEntry> {
        return try {
            val targetParentId = parentDocId ?: DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, targetParentId)
            val folders = mutableListOf<BrowserFolderEntry>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val id = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx) ?: id
                        folders.add(BrowserFolderEntry(name = name, pathOrDocId = id, isSaf = true))
                    }
                }
            }
            folders.sortedBy { it.name.lowercase(Locale.getDefault()) }
        } catch (e: Exception) {
            Log.w(TAG, "Error listando subcarpetas SAF: ${e.message}")
            emptyList()
        }
    }

    fun createLocalSubfolder(parentPath: String, folderName: String): File? {
        val sanitized = folderName.trim().replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
        if (sanitized.isBlank()) return null
        val newFolder = File(parentPath, sanitized)
        return try {
            if (newFolder.exists() || newFolder.mkdirs()) newFolder else null
        } catch (e: Exception) {
            null
        }
    }

    fun createSafSubfolder(context: Context, treeUri: Uri, parentDocId: String?, folderName: String): String? {
        val sanitized = folderName.trim().replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
        if (sanitized.isBlank()) return null
        return try {
            val targetParentId = parentDocId ?: DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetParentId)
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                sanitized
            )
            createdUri?.let { DocumentsContract.getDocumentId(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error creando carpeta SAF: ${e.message}", e)
            null
        }
    }

    fun createCustomFolder(context: Context, folderName: String): File? {
        val sanitized = folderName.trim().replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
        if (sanitized.isBlank()) return null
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val newFolder = File(baseDir, sanitized)
        return try {
            if (!newFolder.exists()) {
                newFolder.mkdirs()
            }
            setDownloadDirectory(context, newFolder.absolutePath)
            newFolder
        } catch (e: Exception) {
            null
        }
    }

    fun exportFilesToDestination(
        context: Context,
        downloadedFiles: List<File>,
        workingDir: File
    ): List<ExportedMediaResult> {
        val storageType = getStorageType(context)
        if (storageType == StorageType.LOCAL_DIR || downloadedFiles.isEmpty()) {
            return downloadedFiles.map {
                ExportedMediaResult(
                    pathOrUri = it.absolutePath,
                    fileName = it.name,
                    sizeBytes = it.length(),
                    lastModified = it.lastModified()
                )
            }
        }

        val results = mutableListOf<ExportedMediaResult>()
        val resolver = context.contentResolver

        for (file in downloadedFiles) {
            val relativeParent = file.parentFile?.let { parent ->
                if (parent.absolutePath != workingDir.absolutePath) {
                    parent.absolutePath.removePrefix(workingDir.absolutePath).trimStart('/')
                } else null
            }
            val mimeType = getMimeType(file)
            var exportedUri: Uri? = null

            if (storageType == StorageType.SAF_TREE) {
                val treeUri = getSafTreeUri(context)
                if (treeUri != null) {
                    exportedUri = copyFileToSafTree(
                        resolver = resolver,
                        sourceFile = file,
                        treeUri = treeUri,
                        baseDocId = getSafSubfolderDocId(context),
                        relativeSubfolder = relativeParent,
                        mimeType = mimeType
                    )
                }
            } else if (storageType == StorageType.PUBLIC_DOWNLOADS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportedUri = copyFileToPublicDownloads(
                    resolver = resolver,
                    sourceFile = file,
                    relativeSubfolder = relativeParent,
                    mimeType = mimeType
                )
            }

            if (exportedUri != null) {
                results.add(
                    ExportedMediaResult(
                        pathOrUri = exportedUri.toString(),
                        fileName = file.name,
                        sizeBytes = file.length(),
                        lastModified = System.currentTimeMillis()
                    )
                )
            } else {
                // Fallback: mover a directorio local de la app para no perder el archivo descargado
                val fallbackDir = getDownloadDirectory(context)
                val targetDir = if (!relativeParent.isNullOrBlank()) {
                    File(fallbackDir, relativeParent).apply { mkdirs() }
                } else fallbackDir
                val destFile = File(targetDir, file.name)
                try {
                    file.copyTo(destFile, overwrite = true)
                    results.add(
                        ExportedMediaResult(
                            pathOrUri = destFile.absolutePath,
                            fileName = destFile.name,
                            sizeBytes = destFile.length(),
                            lastModified = destFile.lastModified()
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo fallback exportando archivo: ${e.message}")
                }
            }
        }

        // Limpiar carpeta temporal de staging
        try {
            workingDir.deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo limpiar staging dir: ${e.message}")
        }

        return results
    }

    private fun copyFileToSafTree(
        resolver: ContentResolver,
        sourceFile: File,
        treeUri: Uri,
        baseDocId: String?,
        relativeSubfolder: String?,
        mimeType: String
    ): Uri? {
        return try {
            var currentParentId = baseDocId ?: DocumentsContract.getTreeDocumentId(treeUri)

            if (!relativeSubfolder.isNullOrBlank()) {
                for (segment in relativeSubfolder.split('/').filter { it.isNotBlank() }) {
                    currentParentId = findOrCreateSafSubfolder(resolver, treeUri, currentParentId, segment)
                        ?: currentParentId
                }
            }

            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentParentId)
            val createdUri = DocumentsContract.createDocument(resolver, parentDocUri, mimeType, sourceFile.name)
                ?: return null

            resolver.openOutputStream(createdUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out, bufferSize = 32 * 1024)
                }
            }
            createdUri
        } catch (e: Exception) {
            Log.e(TAG, "Error copiando archivo a SAF tree: ${e.message}", e)
            null
        }
    }

    private fun findOrCreateSafSubfolder(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        folderName: String
    ): String? {
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR && name.equals(folderName, ignoreCase = true)) {
                        return cursor.getString(idIdx)
                    }
                }
            }

            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val created = DocumentsContract.createDocument(
                resolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                folderName
            )
            return created?.let { DocumentsContract.getDocumentId(it) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo encontrar/crear subcarpeta SAF $folderName: ${e.message}")
            return null
        }
    }

    private fun copyFileToPublicDownloads(
        resolver: ContentResolver,
        sourceFile: File,
        relativeSubfolder: String?,
        mimeType: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val relPath = buildString {
                append(Environment.DIRECTORY_DOWNLOADS)
                append("/POWER PAPILO")
                if (!relativeSubfolder.isNullOrBlank()) {
                    append("/")
                    append(relativeSubfolder)
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values) ?: return null

            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out, bufferSize = 32 * 1024)
                }
            }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Error exportando a MediaStore.Downloads: ${e.message}", e)
            null
        }
    }

    fun scanDestinationFiles(context: Context): List<ExportedMediaResult> {
        return when (getStorageType(context)) {
            StorageType.LOCAL_DIR -> {
                val dir = getDownloadDirectory(context)
                dir.walkTopDown()
                    .filter { it.isFile && it.length() > 0 }
                    .sortedByDescending { it.lastModified() }
                    .map { file ->
                        ExportedMediaResult(
                            pathOrUri = file.absolutePath,
                            fileName = file.name,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified()
                        )
                    }.toList()
            }
            StorageType.SAF_TREE -> {
                val treeUri = getSafTreeUri(context) ?: return emptyList()
                val rootId = getSafSubfolderDocId(context) ?: try {
                    DocumentsContract.getTreeDocumentId(treeUri)
                } catch (e: Exception) {
                    return emptyList()
                }
                val collected = mutableListOf<ExportedMediaResult>()
                collectSafFilesRecursive(context.contentResolver, treeUri, rootId, depth = 0, out = collected)
                collected.sortedByDescending { it.lastModified }
            }
            StorageType.PUBLIC_DOWNLOADS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    scanMediaStoreDownloads(context.contentResolver)
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun collectSafFilesRecursive(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        depth: Int,
        out: MutableList<ExportedMediaResult>
    ) {
        if (depth > 2) return
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Archivo" else "Archivo"
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val modified = if (modIdx >= 0) cursor.getLong(modIdx) else 0L

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        collectSafFilesRecursive(resolver, treeUri, docId, depth + 1, out)
                    } else if (size > 0 || name.contains('.')) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        out.add(
                            ExportedMediaResult(
                                pathOrUri = docUri.toString(),
                                fileName = name,
                                sizeBytes = size,
                                lastModified = modified
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error escaneando SAF tree: ${e.message}")
        }
    }

    private fun scanMediaStoreDownloads(resolver: ContentResolver): List<ExportedMediaResult> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val results = mutableListOf<ExportedMediaResult>()
        try {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
            )
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: continue
                    val size = cursor.getLong(sizeIdx)
                    val modified = cursor.getLong(dateIdx) * 1000L
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    results.add(
                        ExportedMediaResult(
                            pathOrUri = uri.toString(),
                            fileName = name,
                            sizeBytes = size,
                            lastModified = modified
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error consultando MediaStore.Downloads: ${e.message}")
        }
        return results
    }

    fun getMimeType(file: File): String {
        return getMimeTypeFromName(file.name)
    }

    fun getMimeTypeFromName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "mp4", "m4v", "mov" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        }
    }

    fun openMedia(context: Context, pathOrUri: String, fileName: String? = null) {
        if (pathOrUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(pathOrUri)
                val mimeType = context.contentResolver.getType(uri)
                    ?: getMimeTypeFromName(fileName ?: pathOrUri)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Abrir con"))
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo abrir el archivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            openFile(context, File(pathOrUri))
        }
    }

    fun shareMedia(context: Context, pathOrUri: String, fileName: String? = null) {
        if (pathOrUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(pathOrUri)
                val mimeType = context.contentResolver.getType(uri)
                    ?: getMimeTypeFromName(fileName ?: pathOrUri)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, fileName ?: "Archivo de POWER PAPILO")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir archivo").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            shareFile(context, File(pathOrUri))
        }
    }

    fun deleteMedia(context: Context, pathOrUri: String): Boolean {
        return try {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } catch (e: Exception) {
                    context.contentResolver.delete(uri, null, null) > 0
                }
            } else {
                val file = File(pathOrUri)
                !file.exists() || file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando medio $pathOrUri: ${e.message}")
            false
        }
    }

    fun openFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "El archivo ya no existe", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val mimeType = getMimeType(file)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Abrir con"))
        } catch (e: Exception) {
            Toast.makeText(context, "No se encontró una aplicación para abrir este archivo", Toast.LENGTH_LONG).show()
        }
    }

    fun shareFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "El archivo no existe", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val mimeType = getMimeType(file)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir archivo").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    fun formatEta(etaSeconds: Long): String {
        if (etaSeconds <= 0) return "--"
        if (etaSeconds < 60) return "${etaSeconds}s"
        val minutes = etaSeconds / 60
        val seconds = etaSeconds % 60
        return if (minutes < 60) {
            "${minutes}m ${seconds}s"
        } else {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            "${hours}h ${remMinutes}m"
        }
    }
}
