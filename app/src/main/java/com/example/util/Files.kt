package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.DecimalFormat
import java.util.Locale

data class FolderOption(
    val id: String,
    val name: String,
    val path: String,
    val description: String
)

object FileUtils {

    private const val PREFS_NAME = "power_papilo_prefs"
    private const val KEY_CUSTOM_DOWNLOAD_DIR = "custom_download_directory"

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

        // Default: External Files Downloads directory
        val defaultDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
    }

    fun setDownloadDirectory(context: Context, path: String): Boolean {
        return try {
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CUSTOM_DOWNLOAD_DIR, dir.absolutePath)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getFolderOptions(context: Context): List<FolderOption> {
        val currentDir = getDownloadDirectory(context).absolutePath
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

        return listOf(
            FolderOption(
                id = "downloads",
                name = "Carpeta Descargas (Predeterminada)",
                path = defaultDownloads,
                description = "Ubicación estándar de descargas compartidas"
            ),
            FolderOption(
                id = "music",
                name = "Carpeta Música y Audio",
                path = musicDir,
                description = "Ideal para guardar canciones y audios MP3/M4A"
            ),
            FolderOption(
                id = "movies",
                name = "Carpeta Películas y Videos",
                path = moviesDir,
                description = "Ideal para coleccionar clips y videos descargados"
            ),
            FolderOption(
                id = "documents",
                name = "Carpeta Documentos",
                path = docsDir,
                description = "Organización limpia en carpeta de documentos"
            ),
            FolderOption(
                id = "internal",
                name = "Almacenamiento Interno de la App",
                path = internalDir,
                description = "Espacio privado dedicado de POWER PAPILO"
            )
        )
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

    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
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
