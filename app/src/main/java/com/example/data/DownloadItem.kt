package com.example.data

import java.util.UUID

enum class QualityPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val isAudioOnly: Boolean,
    val isWebP: Boolean = false
) {
    BEST(
        id = "best",
        title = "Mejor Calidad",
        subtitle = "Video hasta 4K / Máxima resolución disponible",
        isAudioOnly = false,
        isWebP = false
    ),
    P1080(
        id = "1080p",
        title = "Video 1080p",
        subtitle = "Full HD (1920x1080) equilibrado",
        isAudioOnly = false,
        isWebP = false
    ),
    P720(
        id = "720p",
        title = "Video 720p",
        subtitle = "HD ligero (1280x720) descarga rápida",
        isAudioOnly = false,
        isWebP = false
    ),
    AUDIO_MP3(
        id = "mp3",
        title = "Audio MP3",
        subtitle = "Extrae audio estéreo en 320kbps con carátula",
        isAudioOnly = true,
        isWebP = false
    ),
    AUDIO_M4A(
        id = "m4a",
        title = "Audio M4A",
        subtitle = "Audio nativo AAC sin recompresión",
        isAudioOnly = true,
        isWebP = false
    ),
    IMAGE_WEBP(
        id = "webp_image",
        title = "Carátula / Miniatura WEBP",
        subtitle = "Descarga la imagen oficial del video en formato WEBP de alta calidad",
        isAudioOnly = false,
        isWebP = true
    ),
    VIDEO_WEBM(
        id = "webm_video",
        title = "Video WEBM",
        subtitle = "Formato de video WebM abierto (VP9/Opus) ligero y optimizado",
        isAudioOnly = false,
        isWebP = false
    )
}

enum class DownloadStatus {
    IDLE,
    QUEUED,
    FETCHING_INFO,
    DOWNLOADING,
    CONVERTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class VideoInfoPreview(
    val url: String,
    val title: String,
    val uploader: String? = null,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val viewCount: Long? = null,
    val webpageUrl: String? = null,
    val isPlaylist: Boolean = false
)

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "Descarga de medios",
    val author: String = "Desconocido",
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0L,
    val preset: QualityPreset = QualityPreset.BEST,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val etaSeconds: Long = 0L,
    val downloadSpeed: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val filePath: String? = null,
    val fileName: String? = null,
    val errorMessage: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val isPlaylist: Boolean = false
)
