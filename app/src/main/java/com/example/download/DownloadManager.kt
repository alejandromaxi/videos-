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
        val effectivePlaylist = isPlaylist || (preview?.isPlaylist == true)
        val defaultTitle = if (effectivePlaylist) "Lista de reproducción en cola..."
        else if (preset == QualityPreset.IMAGE_WEBP) "Imagen WEBP en cola..."
        else "Descarga en cola..."

        val item = DownloadItem(
            url = url.trim(),
            title = preview?.title ?: defaultTitle,
            author = preview?.uploader ?: "Iniciando...",
            thumbnailUrl = preview?.thumbnailUrl,
            durationSeconds = preview?.durationSeconds ?: 0L,
            preset = preset,
            status = DownloadStatus.QUEUED,
            isPlaylist = effectivePlaylist
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
        val downloadDir = FileUtils.getDownloadDirectory(context)
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
                downloadWebpImage(context, item, downloadDir)
                scanLocalFiles(context)
                return
            }

            val isDirectMedia = DirectDownloadEngine.isDirectMediaUrl(item.url)
            val isYtDlpReady = PowerPapiloApp.isYtDlpAvailable.value

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
                    destinationDir = downloadDir
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

                updateItem(item.id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100f,
                        filePath = savedFile.absolutePath,
                        fileName = savedFile.name,
                        totalBytes = savedFile.length(),
                        completedAt = System.currentTimeMillis()
                    )
                }
                Log.d(TAG, "Descarga directa completada: ${savedFile.name}")
                // Actualizar biblioteca de inmediato
                scanLocalFiles(context)

            } else {
                // Descarga mediante motor yt-dlp
                if (currentTitle.startsWith("Descarga") || currentThumb == null) {
                    try {
                        val info = YtDlpEngine.fetchVideoInfo(context, item.url)
                        currentTitle = info.title
                        currentAuthor = info.uploader ?: "Desconocido"
                        currentThumb = info.thumbnailUrl
                        currentDuration = info.durationSeconds
                    } catch (e: Exception) {
                        Log.w(TAG, "Fallo al obtener info previa en proceso: ${e.message}")
                    }
                }

                updateItem(item.id) {
                    it.copy(
                        title = currentTitle,
                        author = currentAuthor,
                        thumbnailUrl = currentThumb,
                        durationSeconds = currentDuration,
                        status = DownloadStatus.DOWNLOADING,
                        progress = 0f
                    )
                }

                val request = YtDlpEngine.buildRequest(
                    context = context,
                    item = item.copy(title = currentTitle),
                    downloadDir = downloadDir
                )

                val filesBefore = downloadDir.walkTopDown().filter { it.isFile }.toSet()

                YoutubeDL.getInstance().execute(request, item.id) { progress, etaInSeconds, line ->
                    val speed = YtDlpEngine.parseSpeed(line)
                    updateItem(item.id) { current ->
                        current.copy(
                            progress = progress,
                            etaSeconds = etaInSeconds,
                            downloadSpeed = speed ?: current.downloadSpeed,
                            status = if (line.contains("ExtractAudio") || line.contains("ffmpeg") || line.contains("Destination")) {
                                DownloadStatus.CONVERTING
                            } else {
                                DownloadStatus.DOWNLOADING
                            }
                        )
                    }
                }

                val filesAfter = downloadDir.walkTopDown().filter { it.isFile }.toSet()
                val newFiles = filesAfter - filesBefore
                val targetFile = newFiles.maxByOrNull { it.lastModified() }
                    ?: downloadDir.walkTopDown().filter { it.isFile && it.lastModified() >= startTime }.maxByOrNull { it.lastModified() }
                    ?: downloadDir.listFiles()?.maxByOrNull { it.lastModified() }

                val finalTotalBytes = if (item.isPlaylist && newFiles.isNotEmpty()) {
                    newFiles.sumOf { it.length() }
                } else {
                    targetFile?.length() ?: 0L
                }

                val finalFileName = if (item.isPlaylist && newFiles.size > 1) {
                    "${currentTitle} (${newFiles.size} archivos)"
                } else {
                    targetFile?.name ?: "video_${item.id.take(8)}"
                }

                updateItem(item.id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100f,
                        filePath = targetFile?.absolutePath,
                        fileName = finalFileName,
                        totalBytes = finalTotalBytes,
                        completedAt = System.currentTimeMillis()
                    )
                }

                // Actualizar biblioteca de inmediato cada vez que algo se descarga
                scanLocalFiles(context)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fallo en descarga ${item.id}: ${e.message}", e)
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

    private suspend fun downloadWebpImage(context: Context, item: DownloadItem, downloadDir: File) = withContext(Dispatchers.IO) {
        updateItem(item.id) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = 25f
            )
        }

        var imageUrl = item.thumbnailUrl
        var videoTitle = item.title

        // Si no tenemos URL de miniatura, resolverla
        if (imageUrl.isNullOrBlank()) {
            val ytId = YtDlpEngine.extractYouTubeId(item.url)
            imageUrl = if (ytId != null) {
                "https://i.ytimg.com/vi/$ytId/maxresdefault.jpg"
            } else {
                try {
                    val info = YtDlpEngine.fetchVideoInfo(context, item.url)
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
        if (!response.isSuccessful && imageUrl.contains("maxresdefault.jpg")) {
            // Reintentar con hqdefault.jpg
            val fallbackUrl = imageUrl.replace("maxresdefault.jpg", "hqdefault.jpg")
            val fallbackReq = Request.Builder().url(fallbackUrl).build()
            imageHttpClient.newCall(fallbackReq).execute().use { fbRes ->
                if (!fbRes.isSuccessful) throw IllegalStateException("Error al descargar miniatura (${fbRes.code})")
                val bytes = fbRes.body?.bytes() ?: throw IllegalStateException("Cuerpo de imagen vacío")
                saveBytesAsWebp(bytes, videoTitle, downloadDir, item)
            }
        } else {
            response.use { res ->
                if (!res.isSuccessful) throw IllegalStateException("Error al descargar imagen (${res.code})")
                val bytes = res.body?.bytes() ?: throw IllegalStateException("Cuerpo de imagen vacío")
                saveBytesAsWebp(bytes, videoTitle, downloadDir, item)
            }
        }
    }

    private fun saveBytesAsWebp(bytes: ByteArray, title: String, downloadDir: File, item: DownloadItem) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Formato de imagen inválido")

        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_\\- .]"), "_").take(70)
        val targetFile = File(downloadDir, "$sanitizedTitle [${item.id.take(6)}].webp")

        FileOutputStream(targetFile).use { out ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 95, out)
        }

        updateItem(item.id) {
            it.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100f,
                filePath = targetFile.absolutePath,
                fileName = targetFile.name,
                totalBytes = targetFile.length(),
                completedAt = System.currentTimeMillis()
            )
        }
        Log.d(TAG, "Imagen WEBP guardada exitosamente: ${targetFile.absolutePath}")
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

    fun delete(id: String, deleteFromDisk: Boolean) {
        val item = _downloads.value.find { it.id == id }
        if (deleteFromDisk && item?.filePath != null) {
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando archivo: ${e.message}")
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

    suspend fun scanLocalFiles(context: Context) = withContext(Dispatchers.IO) {
        val dir = FileUtils.getDownloadDirectory(context)
        val files = dir.walkTopDown().filter { it.isFile && it.length() > 0 }.toList()
        val existingPaths = _downloads.value.mapNotNull { it.filePath }.toSet()

        val scannedItems = files.filter { !existingPaths.contains(it.absolutePath) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val isAudio = file.extension.lowercase() in listOf("mp3", "m4a", "opus", "wav", "flac")
                val isWebp = file.extension.lowercase() == "webp"
                DownloadItem(
                    url = file.name,
                    title = file.nameWithoutExtension,
                    author = "Descargado en POWER PAPILO",
                    status = DownloadStatus.COMPLETED,
                    progress = 100f,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    totalBytes = file.length(),
                    preset = if (isWebp) QualityPreset.IMAGE_WEBP else if (isAudio) QualityPreset.AUDIO_MP3 else QualityPreset.BEST,
                    completedAt = file.lastModified()
                )
            }

        if (scannedItems.isNotEmpty()) {
            _downloads.update { current ->
                val combined = current + scannedItems
                combined.distinctBy { it.filePath ?: it.id }
            }
        }
    }
}
