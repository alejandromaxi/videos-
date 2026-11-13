package com.example.download

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.example.PowerPapiloApp
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.data.QualityPreset
import com.example.data.VideoInfoPreview
import com.example.util.FileUtils
import com.example.util.StorageType
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object DownloadManager {

    private const val TAG = "DownloadManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _activeItem = MutableStateFlow<DownloadItem?>(null)
    val activeItem: StateFlow<DownloadItem?> = _activeItem.asStateFlow()

    private var workerJob: Job? = null

    private val imageHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun enqueue(
        context: Context,
        url: String,
        preset: QualityPreset,
        preview: VideoInfoPreview? = null,
        isPlaylist: Boolean = false
    ): DownloadItem {
        // Respetar estrictamente el parámetro isPlaylist del apartado elegido (Individual vs Playlist)
        val cleanUrl = if (isPlaylist) url.trim() else YtDlpEngine.sanitizeSingleVideoUrl(url.trim())
        val defaultTitle = if (isPlaylist) "Lista de reproducción en cola..."
        else if (preset == QualityPreset.IMAGE_WEBP) "Imagen WEBP en cola..."
        else "Descarga en cola..."

        val item = DownloadItem(
            url = cleanUrl,
            title = preview?.title ?: defaultTitle,
            author = preview?.uploader ?: "Iniciando...",
            thumbnailUrl = preview?.thumbnailUrl,
            durationSeconds = preview?.durationSeconds ?: 0L,
            preset = preset,
            status = DownloadStatus.QUEUED,
            isPlaylist = isPlaylist
        )

        _downloads.update { listOf(item) + it }
        startServiceIfNeeded(context)
        checkAndStartNext(context.applicationContext)
        return item
    }

    private fun startServiceIfNeeded(context: Context) {
        try {
            val serviceIntent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando DownloadService: ${e.message}")
        }
    }

    private fun checkAndStartNext(context: Context) {
        if (workerJob?.isActive == true) return

        workerJob = scope.launch {
            while (true) {
                val nextItem = _downloads.value.firstOrNull { it.status == DownloadStatus.QUEUED }
                if (nextItem == null) {
                    _activeItem.value = null
                    break
                }
                processDownload(context, nextItem)
            }
        }
    }

    private suspend fun processDownload(context: Context, item: DownloadItem) {
        val workingDir = FileUtils.getWorkingDirectory(context, item.id)
        val isLocalMode = FileUtils.getStorageType(context) == StorageType.LOCAL_DIR
        _activeItem.value = item

        updateItem(item.id) {
            it.copy(
                status = DownloadStatus.FETCHING_INFO,
                errorMessage = null
            )
        }

        val startTime = System.currentTimeMillis()

        try {
            var currentTitle = item.title
            var currentAuthor = item.author
            var currentThumb = item.thumbnailUrl
            var currentDuration = item.durationSeconds

            // 1. Descarga de imagen en formato WEBP
            if (item.preset == QualityPreset.IMAGE_WEBP) {
                val webpFile = downloadWebpImage(context, item, workingDir)
                val exported = FileUtils.exportFilesToDestination(context, listOf(webpFile), workingDir)
                val result = exported.firstOrNull()
                updateItem(item.id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100f,
                        filePath = result?.pathOrUri ?: webpFile.absolutePath,
                        fileName = result?.fileName ?: webpFile.name,
                        totalBytes = result?.sizeBytes ?: webpFile.length(),
                        completedAt = System.currentTimeMillis()
                    )
                }
                scanLocalFiles(context)
                return
            }

            val isDirectMedia = DirectDownloadEngine.isDirectMediaUrl(item.url)
            val isYtDlpReady = if (isDirectMedia) {
                false
            } else {
                PowerPapiloApp.awaitEngineReady()
            }

            if (isDirectMedia || !isYtDlpReady) {
                if (currentTitle.startsWith("Descarga") || currentTitle.startsWith("Analizando")) {
                    try {
                        val inspected = DirectDownloadEngine.inspectMedia(item.url)
                        currentTitle = inspected.title
                        currentAuthor = inspected.uploader ?: "Streaming Web"
                    } catch (e: Exception) {
                        Log.w(TAG, "Inspección directa omitida: ${e.message}")
                    }
                }

                updateItem(item.id) {
                    it.copy(
                        title = currentTitle,
                        author = currentAuthor,
                        status = DownloadStatus.DOWNLOADING,
                        progress = 0f
                    )
                }

                val savedFile = DirectDownloadEngine.downloadDirect(
                    item = item.copy(title = currentTitle),
                    destinationDir = workingDir
                ) { progress, eta, speed ->
                    updateItem(item.id) { current ->
                        current.copy(
                            progress = progress,
                            etaSeconds = eta,
                            downloadSpeed = speed,
                            status = DownloadStatus.DOWNLOADING
                        )
                    }
                }

                val exported = FileUtils.exportFilesToDestination(context, listOf(savedFile), workingDir)
                val result = exported.firstOrNull()

                updateItem(item.id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100f,
                        filePath = result?.pathOrUri ?: savedFile.absolutePath,
                        fileName = result?.fileName ?: savedFile.name,
                        totalBytes = result?.sizeBytes ?: savedFile.length(),
                        completedAt = System.currentTimeMillis()
                    )
                }
                Log.d(TAG, "Descarga directa completada: ${result?.fileName ?: savedFile.name}")
                scanLocalFiles(context)

            } else {
                // Arranque inmediato sin pre-consulta bloqueante (evita 2º arranque de Python / esperas de red)
                if (currentThumb == null && YtDlpEngine.isYouTubeUrl(item.url)) {
                    val ytId = YtDlpEngine.extractYouTubeId(item.url)
                    if (ytId != null) {
                        currentThumb = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
                    }
                }

                updateItem(item.id) {
                    it.copy(
                        title = currentTitle,
                        author = if (currentAuthor == "Iniciando...") "Descargando..." else currentAuthor,
                        thumbnailUrl = currentThumb,
                        durationSeconds = currentDuration,
                        status = DownloadStatus.DOWNLOADING,
                        progress = 0f
                    )
                }

                val request = YtDlpEngine.buildRequest(
                    context = context,
                    item = item.copy(title = currentTitle),
                    downloadDir = workingDir
                )

                val filesBefore = if (isLocalMode) {
                    workingDir.walkTopDown().filter { it.isFile }.toSet()
                } else {
                    emptySet()
                }

                YoutubeDL.getInstance().execute(request, item.id) { progress, etaInSeconds, line ->
                    val speed = YtDlpEngine.parseSpeed(line)
                    val detectedTitle = YtDlpEngine.parseDestinationTitle(line)
                    val isConvertingPhase = !line.startsWith("[download]") && (
                        line.contains("ExtractAudio") ||
                            line.contains("ffmpeg", ignoreCase = true) ||
                            line.contains("EmbedThumbnail") ||
                            line.contains("Metadata") ||
                            line.contains("Merger")
                        )

                    updateItem(item.id) { current ->
                        val updatedTitle = if (
                            detectedTitle != null &&
                            (current.title.startsWith("Descarga") || current.title.startsWith("Lista de reproducción"))
                        ) {
                            detectedTitle
                        } else {
                            current.title
                        }
                        current.copy(
                            title = updatedTitle,
                            progress = progress,
                            etaSeconds = etaInSeconds,
                            downloadSpeed = speed ?: current.downloadSpeed,
                            status = if (isConvertingPhase) {
                                DownloadStatus.CONVERTING
                            } else {
                                DownloadStatus.DOWNLOADING
                            }
                        )
                    }
                }

                val filesAfter = workingDir.walkTopDown().filter { it.isFile && it.length() > 0 }.toSet()
                val newFiles = (filesAfter - filesBefore).ifEmpty {
                    workingDir.walkTopDown()
                        .filter { it.isFile && it.length() > 0 && it.lastModified() >= startTime }
                        .toList()
                }.toList()

                val exported = FileUtils.exportFilesToDestination(context, newFiles, workingDir)
                val primaryResult = exported.maxByOrNull { it.lastModified }

                val finalTotalBytes = if (item.isPlaylist && exported.isNotEmpty()) {
                    exported.sumOf { it.sizeBytes }
                } else {
                    primaryResult?.sizeBytes ?: 0L
                }

                val finalFileName = if (item.isPlaylist && exported.size > 1) {
                    "$currentTitle (${exported.size} archivos)"
                } else {
                    primaryResult?.fileName ?: "video_${item.id.take(8)}"
                }

                updateItem(item.id) { current ->
                    val finalTitle = if (
                        primaryResult != null &&
                        (current.title.startsWith("Descarga") || current.title.startsWith("Lista de reproducción"))
                    ) {
                        primaryResult.fileName.substringBeforeLast('.').ifBlank { current.title }
                    } else {
                        current.title
                    }
                    current.copy(
                        title = finalTitle,
                        status = DownloadStatus.COMPLETED,
                        progress = 100f,
                        filePath = primaryResult?.pathOrUri,
                        fileName = finalFileName,
                        totalBytes = finalTotalBytes,
                        completedAt = System.currentTimeMillis()
                    )
                }

                scanLocalFiles(context)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fallo en descarga ${item.id}: ${e.message}", e)
            // Limpiar staging en caso de fallo si no es modo local
            if (!isLocalMode) {
                try {
                    workingDir.deleteRecursively()
                } catch (_: Exception) {
                }
            }

            val isCancelled = e.message?.contains("destroy", ignoreCase = true) == true ||
                    e is YoutubeDL.CanceledException ||
                    e.message?.contains("Canceled", ignoreCase = true) == true

            val isBotBlock = e.message?.contains("Sign in to confirm you’re not a bot", ignoreCase = true) == true ||
                    e.message?.contains("bot", ignoreCase = true) == true ||
                    e.message?.contains("cookies", ignoreCase = true) == true

            val friendlyError = when {
                isCancelled -> "Cancelado por el usuario"
                isBotBlock -> "YouTube bloqueó la descarga por verificación anti-bot (IP de centro de datos). Pulsa 'Vincular Sesión de YouTube' para omitir esta restricción."
                e.message?.contains("403") == true -> "Acceso denegado (403 Forbidden). El servidor bloqueó la descarga."
                else -> e.message ?: "Error al descargar"
            }

            updateItem(item.id) {
                it.copy(
                    status = if (isCancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                    errorMessage = friendlyError
                )
            }
        } finally {
            _activeItem.value = null
        }
    }

    private suspend fun downloadWebpImage(context: Context, item: DownloadItem, workingDir: File): File = withContext(Dispatchers.IO) {
        updateItem(item.id) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = 25f
            )
        }

        var imageUrl = item.thumbnailUrl
        var videoTitle = item.title

        if (imageUrl.isNullOrBlank()) {
            val ytId = YtDlpEngine.extractYouTubeId(item.url)
            imageUrl = if (ytId != null) {
                "https://i.ytimg.com/vi/$ytId/maxresdefault.jpg"
            } else {
                try {
                    val info = YtDlpEngine.fetchVideoInfo(context, item.url, forceSingleVideo = !item.isPlaylist)
                    videoTitle = info.title
                    info.thumbnailUrl
                } catch (e: Exception) {
                    null
                }
            }
        }

        if (imageUrl.isNullOrBlank()) {
            throw IllegalStateException("No se pudo obtener la imagen o miniatura de la URL especificada")
        }

        val request = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = imageHttpClient.newCall(request).execute()
        val bytes: ByteArray = if (!response.isSuccessful && imageUrl.contains("maxresdefault.jpg")) {
            response.close()
            val fallbackUrl = imageUrl.replace("maxresdefault.jpg", "hqdefault.jpg")
            val fallbackReq = Request.Builder().url(fallbackUrl).build()
            imageHttpClient.newCall(fallbackReq).execute().use { fbRes ->
                if (!fbRes.isSuccessful) throw IllegalStateException("Error al descargar miniatura (${fbRes.code})")
                fbRes.body?.bytes() ?: throw IllegalStateException("Cuerpo de imagen vacío")
            }
        } else {
            response.use { res ->
                if (!res.isSuccessful) throw IllegalStateException("Error al descargar imagen (${res.code})")
                res.body?.bytes() ?: throw IllegalStateException("Cuerpo de imagen vacío")
            }
        }

        saveBytesAsWebp(bytes, videoTitle, workingDir, item)
    }

    private fun saveBytesAsWebp(bytes: ByteArray, title: String, destinationDir: File, item: DownloadItem): File {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Formato de imagen inválido")

        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_\\- .]"), "_").take(70).ifBlank { "imagen" }
        val targetFile = File(destinationDir, "$sanitizedTitle [${item.id.take(6)}].webp")

        FileOutputStream(targetFile).use { out ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 95, out)
        }
        Log.d(TAG, "Imagen WEBP creada en staging/destino: ${targetFile.absolutePath}")
        return targetFile
    }

    fun cancel(id: String) {
        YtDlpEngine.cancel(id)
        DirectDownloadEngine.cancel(id)
        updateItem(id) {
            it.copy(
                status = DownloadStatus.CANCELLED,
                errorMessage = "Descarga cancelada"
            )
        }
    }

    fun retry(context: Context, id: String) {
        updateItem(id) {
            it.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                errorMessage = null
            )
        }
        startServiceIfNeeded(context)
        checkAndStartNext(context.applicationContext)
    }

    fun retryAllFailed(context: Context) {
        val failedIds = _downloads.value.filter { it.status == DownloadStatus.FAILED }.map { it.id }
        for (id in failedIds) {
            updateItem(id) {
                it.copy(
                    status = DownloadStatus.QUEUED,
                    progress = 0f,
                    errorMessage = null
                )
            }
        }
        if (failedIds.isNotEmpty()) {
            startServiceIfNeeded(context)
            checkAndStartNext(context.applicationContext)
        }
    }

    fun delete(id: String, deleteFromDisk: Boolean, context: Context? = null) {
        val item = _downloads.value.find { it.id == id }
        if (deleteFromDisk && !item?.filePath.isNullOrBlank()) {
            val ctx = context ?: runCatching { PowerPapiloApp.instance }.getOrNull()
            if (ctx != null) {
                FileUtils.deleteMedia(ctx, item!!.filePath!!)
            } else {
                try {
                    val file = File(item!!.filePath!!)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Error eliminando archivo: ${e.message}")
                }
            }
        }
        _downloads.update { list -> list.filterNot { it.id == id } }
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { list ->
            list.map { item ->
                if (item.id == id) {
                    val updated = transform(item)
                    if (_activeItem.value?.id == id) {
                        _activeItem.value = updated
                    }
                    updated
                } else item
            }
        }
    }

    suspend fun scanLocalFiles(context: Context, clearPreviousCompleted: Boolean = false) = withContext(Dispatchers.IO) {
        val scannedResults = FileUtils.scanDestinationFiles(context)

        _downloads.update { current ->
            val nonCompleted = current.filter { it.status != DownloadStatus.COMPLETED }
            val existingCompleted = if (clearPreviousCompleted) {
                emptyList()
            } else {
                current.filter { it.status == DownloadStatus.COMPLETED }
            }

            val existingPaths = existingCompleted.mapNotNull { it.filePath }.toSet()

            val newItems = scannedResults
                .filter { !existingPaths.contains(it.pathOrUri) }
                .map { res ->
                    val ext = res.fileName.substringAfterLast('.', "").lowercase()
                    val isAudio = ext in listOf("mp3", "m4a", "opus", "wav", "flac")
                    val isWebp = ext == "webp"
                    DownloadItem(
                        url = res.fileName,
                        title = res.fileName.substringBeforeLast('.').ifBlank { res.fileName },
                        author = "Descargado en POWER PAPILO",
                        status = DownloadStatus.COMPLETED,
                        progress = 100f,
                        filePath = res.pathOrUri,
                        fileName = res.fileName,
                        totalBytes = res.sizeBytes,
                        preset = if (isWebp) QualityPreset.IMAGE_WEBP else if (isAudio) QualityPreset.AUDIO_MP3 else QualityPreset.BEST,
                        completedAt = res.lastModified
                    )
                }

            val mergedCompleted = (existingCompleted + newItems)
                .distinctBy { it.filePath ?: it.id }

            nonCompleted + mergedCompleted
        }
    }
}
