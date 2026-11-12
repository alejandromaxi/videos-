package com.example.download

import android.util.Log
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.data.VideoInfoPreview
import com.example.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object DirectDownloadEngine {

    private const val TAG = "DirectDownloadEngine"

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val activeCalls = mutableMapOf<String, okhttp3.Call>()

    fun isDirectMediaUrl(url: String): Boolean {
        val cleanUrl = url.substringBefore('?').lowercase()
        return cleanUrl.endsWith(".mp4") ||
                cleanUrl.endsWith(".mp3") ||
                cleanUrl.endsWith(".m4a") ||
                cleanUrl.endsWith(".mkv") ||
                cleanUrl.endsWith(".webm") ||
                cleanUrl.endsWith(".wav") ||
                cleanUrl.endsWith(".aac") ||
                cleanUrl.endsWith(".ogg") ||
                cleanUrl.endsWith(".flac") ||
                cleanUrl.endsWith(".mov")
    }

    suspend fun inspectMedia(url: String): VideoInfoPreview = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .head()
            .build()

        var filename = extractFilenameFromUrl(url)
        var sizeBytes = 0L

        try {
            client.newCall(request).execute().use { response ->
                val disposition = response.header("Content-Disposition")
                if (!disposition.isNullOrBlank() && disposition.contains("filename=")) {
                    val rawName = disposition.substringAfter("filename=").trim('"', ' ', '\'')
                    if (rawName.isNotBlank()) filename = rawName
                }
                sizeBytes = response.header("Content-Length")?.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD request falló, usando nombre extraído de URL: ${e.message}")
        }

        val title = filename.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .capitalizeWords()

        VideoInfoPreview(
            url = url,
            title = if (title.isBlank()) "Medio Web Descargable" else title,
            uploader = "Descarga Directa HTTP/HTTPS",
            durationSeconds = 0L,
            thumbnailUrl = null,
            viewCount = null,
            webpageUrl = url
        )
    }

    suspend fun downloadDirect(
        item: DownloadItem,
        destinationDir: File,
        onProgress: (progress: Float, etaSeconds: Long, speed: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        var filename = item.fileName
        if (filename.isNullOrBlank()) {
            filename = extractFilenameFromUrl(item.url)
        }
        val extension = when {
            item.preset.isAudioOnly && !filename.endsWith(".mp3") && !filename.endsWith(".m4a") -> ".mp3"
            !item.preset.isAudioOnly && !filename.endsWith(".mp4") && !filename.endsWith(".webm") && !filename.endsWith(".mkv") -> ".mp4"
            else -> ""
        }
        val safeFileName = if (extension.isNotEmpty() && !filename.contains('.')) "$filename$extension" else filename
        val targetFile = File(destinationDir, safeFileName)

        val request = Request.Builder()
            .url(item.url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .build()

        val call = client.newCall(request)
        synchronized(activeCalls) {
            activeCalls[item.id] = call
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        403 -> "Acceso denegado (HTTP 403 Forbidden). El servidor bloqueó la solicitud."
                        404 -> "Archivo no encontrado (HTTP 404 Not Found)."
                        else -> "Servidor respondió con código HTTP ${response.code}"
                    }
                    throw IllegalStateException(msg)
                }
                val body = response.body ?: throw IllegalStateException("Cuerpo de respuesta vacío")
                val totalLength = body.contentLength()

                var downloadedBytes = 0L
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = startTime

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime >= 250 || (totalLength > 0 && downloadedBytes == totalLength)) {
                                val elapsedSec = ((now - startTime) / 1000.0).coerceAtLeast(0.1)
                                val bytesPerSec = (downloadedBytes / elapsedSec).toLong()
                                val speedStr = "${FileUtils.formatBytes(bytesPerSec)}/s"

                                val progress = if (totalLength > 0) {
                                    (downloadedBytes.toFloat() / totalLength.toFloat()) * 100f
                                } else {
                                    50f
                                }

                                val eta = if (totalLength > 0 && bytesPerSec > 0) {
                                    ((totalLength - downloadedBytes) / bytesPerSec).coerceAtLeast(0)
                                } else 0L

                                onProgress(progress, eta, speedStr)
                                lastUpdateTime = now
                            }
                        }
                        output.flush()
                    }
                }
            }
            targetFile
        } finally {
            synchronized(activeCalls) {
                activeCalls.remove(item.id)
            }
        }
    }

    fun cancel(id: String) {
        synchronized(activeCalls) {
            activeCalls[id]?.cancel()
            activeCalls.remove(id)
        }
    }

    private fun extractFilenameFromUrl(url: String): String {
        return try {
            val path = url.substringBefore('?').substringAfterLast('/')
            val decoded = URLDecoder.decode(path, "UTF-8")
            if (decoded.isNotBlank()) decoded else "descarga_papilo_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "descarga_papilo_${System.currentTimeMillis()}"
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
