package com.example.download

import android.content.Context
import android.util.Log
import com.example.PowerPapiloApp
import com.example.data.DownloadItem
import com.example.data.QualityPreset
import com.example.data.VideoInfoPreview
import com.example.util.CookieHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YtDlpEngine {

    private const val TAG = "YtDlpEngine"

    private val speedPattern = Pattern.compile("at\\s+([0-9.]+\\s*[KkMmGg]?i?B/s)")
    private val youtubeIdPattern = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun resolveRedirectUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext trimmed
        }
        // Para URLs de YouTube o medios directos comunes, no hacer petición HEAD para evitar bloqueos y retardos
        if (isYouTubeUrl(trimmed) || DirectDownloadEngine.isDirectMediaUrl(trimmed)) {
            return@withContext trimmed
        }
        try {
            withTimeoutOrNull(2000L) {
                val request = Request.Builder()
                    .url(trimmed)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .head()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    response.request.url.toString()
                }
            } ?: trimmed
        } catch (e: Exception) {
            trimmed
        }
    }

    fun isYouTubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("m.youtube.com") ||
                lower.contains("share.google")
    }

    fun isPlaylistUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("list=") || lower.contains("/playlist")
    }

    fun extractYouTubeId(url: String): String? {
        val matcher = youtubeIdPattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    fun sanitizeSingleVideoUrl(url: String): String {
        val trimmed = url.trim()
        if (!isYouTubeUrl(trimmed)) return trimmed
        val hasVideoId = extractYouTubeId(trimmed) != null
        if (!hasVideoId) return trimmed

        val qIndex = trimmed.indexOf('?')
        if (qIndex == -1) return trimmed

        val base = trimmed.substring(0, qIndex)
        val query = trimmed.substring(qIndex + 1)
        val fragmentIndex = query.indexOf('#')
        val cleanQuery = if (fragmentIndex != -1) query.substring(0, fragmentIndex) else query

        val filteredParams = cleanQuery.split("&")
            .filter { param ->
                val key = param.substringBefore('=').lowercase()
                key.isNotBlank() && key !in setOf("list", "index", "start_radio", "playnext")
            }

        return if (filteredParams.isEmpty()) base else "$base?${filteredParams.joinToString("&")}"
    }

    suspend fun fetchVideoInfo(
        context: Context,
        rawUrl: String,
        forceSingleVideo: Boolean = false
    ): VideoInfoPreview = withContext(Dispatchers.IO) {
        val preProcessedUrl = if (forceSingleVideo) sanitizeSingleVideoUrl(rawUrl) else rawUrl
        val resolvedUrl = resolveRedirectUrl(preProcessedUrl)
        val isPlaylist = if (forceSingleVideo) false else isPlaylistUrl(resolvedUrl)

        // 1. Detección inmediata para YouTube: Nunca llamar a Python en análisis para evitar bloqueos y congelamientos
        if (isYouTubeUrl(resolvedUrl)) {
            val videoId = extractYouTubeId(resolvedUrl)
            val defaultThumb = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null

            // Intentar oEmbed ultrarrápido (timeout corto de 1500ms) para traer título real
            val oembed = try {
                withTimeoutOrNull(1500L) {
                    fetchYouTubeOEmbed(resolvedUrl)
                }
            } catch (e: Exception) {
                null
            }

            if (oembed != null) {
                return@withContext oembed.copy(
                    isPlaylist = isPlaylist,
                    thumbnailUrl = oembed.thumbnailUrl ?: defaultThumb
                )
            }

            // Fallback inmediato con miniatura oficial de YouTube
            return@withContext VideoInfoPreview(
                url = resolvedUrl,
                title = if (isPlaylist) "Lista de Reproducción de YouTube" else if (videoId != null) "Video de YouTube ($videoId)" else "Video de YouTube",
                uploader = "YouTube",
                durationSeconds = 0L,
                thumbnailUrl = defaultThumb,
                viewCount = null,
                webpageUrl = resolvedUrl,
                isPlaylist = isPlaylist
            )
        }

        // 2. Medios directos
        if (DirectDownloadEngine.isDirectMediaUrl(resolvedUrl)) {
            return@withContext DirectDownloadEngine.inspectMedia(resolvedUrl)
        }

        // 3. Otros sitios web mediante yt-dlp con límite estricto de 3 segundos
        try {
            val request = YoutubeDLRequest(resolvedUrl)
            applyBaseOptions(context, request)
            request.addOption("--no-playlist")
            request.addOption("--skip-download")

            val videoInfo = withTimeoutOrNull(3000L) {
                YoutubeDL.getInstance().getInfo(request)
            }

            if (videoInfo != null) {
                return@withContext VideoInfoPreview(
                    url = resolvedUrl,
                    title = videoInfo.title ?: videoInfo.fulltitle ?: "Video multimedia",
                    uploader = videoInfo.uploader ?: "Web",
                    durationSeconds = videoInfo.duration.toLong(),
                    thumbnailUrl = videoInfo.thumbnail,
                    viewCount = videoInfo.viewCount?.toLongOrNull(),
                    webpageUrl = videoInfo.webpageUrl ?: resolvedUrl,
                    isPlaylist = isPlaylist
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp getInfo falló: ${e.message}")
        }

        // Fallback genérico sin bloquear
        return@withContext VideoInfoPreview(
            url = resolvedUrl,
            title = if (isPlaylist) "Lista de reproducción" else "Enlace multimedia",
            uploader = "Web",
            durationSeconds = 0L,
            thumbnailUrl = null,
            viewCount = null,
            webpageUrl = resolvedUrl,
            isPlaylist = isPlaylist
        )
    }

    private fun fetchYouTubeOEmbed(url: String): VideoInfoPreview? {
        val oembedUrl = "https://www.youtube.com/oembed?url=$url&format=json"
        val request = Request.Builder()
            .url(oembedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val jsonStr = response.body?.string() ?: return null
            val json = JSONObject(jsonStr)

            val title = json.optString("title", "Video de YouTube")
            val author = json.optString("author_name", "YouTube")
            val thumbnail = if (json.has("thumbnail_url")) json.optString("thumbnail_url") else null

            return VideoInfoPreview(
                url = url,
                title = title,
                uploader = author,
                durationSeconds = 0L,
                thumbnailUrl = thumbnail,
                viewCount = null,
                webpageUrl = url,
                isPlaylist = isPlaylistUrl(url)
            )
        }
    }

    fun applyBaseOptions(context: Context, request: YoutubeDLRequest) {
        val cookieFile = CookieHelper.getCookieFile(context)
        val hasCookies = cookieFile.exists() && cookieFile.length() > 0

        if (hasCookies) {
            request.addOption("--cookies", cookieFile.absolutePath)
            Log.d(TAG, "Inyectando cookies.txt (${cookieFile.length()} bytes)")
        }

        // Directorio de caché escribible en Android para reutilizar el descifrado de player JS (nsig)
        val cacheDir = File(context.cacheDir, "ytdlp_cache").apply {
            if (!exists()) mkdirs()
        }
        request.addOption("--cache-dir", cacheDir.absolutePath)

        request.addOption("--force-ipv4")
        request.addOption("--geo-bypass")
        request.addOption("--no-check-certificates")
        request.addOption("--no-warnings")
        request.addOption("--newline")
        request.addOption("--socket-timeout", "15")
        request.addOption("--retries", "3")
        request.addOption("--fragment-retries", "3")
        request.addOption("--throttled-rate", "100K")

        if (PowerPapiloApp.isAria2cAvailable.value) {
            request.addOption("--downloader", "http,https:libaria2c.so")
            request.addOption(
                "--external-downloader-args",
                "aria2c:--summary-interval=1 -x 8 -s 8 -k 1M --file-allocation=none"
            )
        }
        request.addOption("--concurrent-fragments", "4")
    }

    fun buildRequest(context: Context, item: DownloadItem, downloadDir: File): YoutubeDLRequest {
        val targetUrl = if (item.isPlaylist) item.url else sanitizeSingleVideoUrl(item.url)
        val request = YoutubeDLRequest(targetUrl)
        applyBaseOptions(context, request)

        if (item.isPlaylist) {
            request.addOption("--yes-playlist")
            val outputTemplate = File(downloadDir, "%(playlist_title|Lista)s/%(playlist_index)02d - %(title).80B.%(ext)s").absolutePath
            request.addOption("-o", outputTemplate)
        } else {
            request.addOption("--no-playlist")
            request.addOption("--playlist-items", "1")
            val outputTemplate = File(downloadDir, "%(title).80B [%(id)s].%(ext)s").absolutePath
            request.addOption("-o", outputTemplate)
        }

        request.addOption("--no-mtime")

        when (item.preset) {
            QualityPreset.BEST -> {
                request.addOption("--add-metadata")
                request.addOption("-f", "bv*+ba/b")
                request.addOption("--ppa", "ffmpeg:-threads 0")
            }
            QualityPreset.P1080 -> {
                request.addOption("--add-metadata")
                request.addOption("-f", "bv*[height<=1080]+ba/b[height<=1080]/b")
                request.addOption("--ppa", "ffmpeg:-threads 0")
            }
            QualityPreset.P720 -> {
                request.addOption("--add-metadata")
                request.addOption("-f", "bv*[height<=720]+ba/b[height<=720]/b")
                request.addOption("--ppa", "ffmpeg:-threads 0")
            }
            QualityPreset.AUDIO_MP3 -> {
                request.addOption("-f", "ba/bestaudio/b")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
                request.addOption("--add-metadata")
                request.addOption("--embed-thumbnail")
                request.addOption("--convert-thumbnails", "jpg")
                request.addOption("--ppa", "ffmpeg:-threads 0")
            }
            QualityPreset.AUDIO_M4A -> {
                request.addOption("-f", "ba[ext=m4a]/ba/bestaudio/b")
                request.addOption("-x")
                request.addOption("--audio-format", "m4a")
                request.addOption("--add-metadata")
                request.addOption("--embed-thumbnail")
                request.addOption("--convert-thumbnails", "jpg")
            }
            QualityPreset.VIDEO_WEBM -> {
                request.addOption("--add-metadata")
                request.addOption("-f", "bv*[ext=webm]+ba*[ext=webm]/b[ext=webm]/best")
            }
            QualityPreset.IMAGE_WEBP -> {
                request.addOption("--write-thumbnail")
                request.addOption("--skip-download")
            }
        }

        return request
    }

    private val aria2cSpeedPattern = Pattern.compile("DL:([0-9.]+\\s*[KkMmGg]?i?B)")

    fun parseSpeed(logLine: String): String? {
        val matcher = speedPattern.matcher(logLine)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val ariaMatcher = aria2cSpeedPattern.matcher(logLine)
        if (ariaMatcher.find()) {
            val raw = ariaMatcher.group(1)?.trim() ?: return null
            return "$raw/s"
        }
        return null
    }

    fun parseDestinationTitle(logLine: String): String? {
        val marker = "Destination:"
        val idx = logLine.indexOf(marker)
        if (idx == -1) return null
        val rawPath = logLine.substring(idx + marker.length).trim()
        if (rawPath.isBlank()) return null
        val fileName = rawPath.substringAfterLast('/').substringAfterLast('\\')
        val baseName = fileName.substringBeforeLast('.')
        val cleaned = baseName.replace(Regex("\\s*\\[[A-Za-z0-9_-]{6,15}]$"), "").trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    fun cancel(processId: String): Boolean {
        return try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelando proceso $processId: ${e.message}")
            false
        }
    }
}
