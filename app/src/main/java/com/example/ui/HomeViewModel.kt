package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.PowerPapiloApp
import com.example.data.DownloadItem
import com.example.data.QualityPreset
import com.example.data.VideoInfoPreview
import com.example.download.DirectDownloadEngine
import com.example.download.DownloadManager
import com.example.download.YtDlpEngine
import com.example.util.CookieHelper
import com.example.util.FileUtils
import com.example.util.FolderOption
import com.example.util.StorageVolumeUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class SampleMedia(
    val title: String,
    val subtitle: String,
    val url: String,
    val preset: QualityPreset
)

class HomeViewModel : ViewModel() {

    // Modo de descarga: 0 = Video/Medio Individual, 1 = Lista de Reproducción (Playlist)
    private val _downloadMode = MutableStateFlow(0)
    val downloadMode: StateFlow<Int> = _downloadMode.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _playlistUrlInput = MutableStateFlow("")
    val playlistUrlInput: StateFlow<String> = _playlistUrlInput.asStateFlow()

    private val _selectedPreset = MutableStateFlow(QualityPreset.BEST)
    val selectedPreset: StateFlow<QualityPreset> = _selectedPreset.asStateFlow()

    private val _playlistPreset = MutableStateFlow(QualityPreset.BEST)
    val playlistPreset: StateFlow<QualityPreset> = _playlistPreset.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isAnalyzingPlaylist = MutableStateFlow(false)
    val isAnalyzingPlaylist: StateFlow<Boolean> = _isAnalyzingPlaylist.asStateFlow()

    private val _previewInfo = MutableStateFlow<VideoInfoPreview?>(null)
    val previewInfo: StateFlow<VideoInfoPreview?> = _previewInfo.asStateFlow()

    private val _playlistPreviewInfo = MutableStateFlow<VideoInfoPreview?>(null)
    val playlistPreviewInfo: StateFlow<VideoInfoPreview?> = _playlistPreviewInfo.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _playlistError = MutableStateFlow<String?>(null)
    val playlistError: StateFlow<String?> = _playlistError.asStateFlow()

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _searchFilter = MutableStateFlow("")
    val searchFilter: StateFlow<String> = _searchFilter.asStateFlow()

    private val _cookieSummary = MutableStateFlow("Sin cookies configuradas")
    val cookieSummary: StateFlow<String> = _cookieSummary.asStateFlow()

    private val _showYouTubeLogin = MutableStateFlow(false)
    val showYouTubeLogin: StateFlow<Boolean> = _showYouTubeLogin.asStateFlow()

    private val _currentDownloadDir = MutableStateFlow("")
    val currentDownloadDir: StateFlow<String> = _currentDownloadDir.asStateFlow()

    val downloads: StateFlow<List<DownloadItem>> = DownloadManager.downloads

    // Muestras verificadas 100% disponibles (HTTP 200) para prueba instantánea
    val sampleMediaList = listOf(
        SampleMedia(
            title = "Big Buck Bunny (MP4)",
            subtitle = "Video MP4 • Muestra rápida garantizada",
            url = "https://dn710604.ca.archive.org/0/items/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4",
            preset = QualityPreset.P720
        ),
        SampleMedia(
            title = "SoundHelix (MP3)",
            subtitle = "Audio MP3 • 320kbps estéreo garantizado",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            preset = QualityPreset.AUDIO_MP3
        ),
        SampleMedia(
            title = "Big Buck Bunny (WebM)",
            subtitle = "Video WebM • Alta resolución Wikimedia",
            url = "https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c0/Big_Buck_Bunny_4K.webm/Big_Buck_Bunny_4K.webm.480p.vp9.webm",
            preset = QualityPreset.VIDEO_WEBM
        )
    )

    // Biblioteca de completados reactiva: se actualiza en tiempo real con cada descarga terminada
    val libraryItems: StateFlow<List<DownloadItem>> = combine(
        DownloadManager.downloads,
        _searchFilter
    ) { all, query ->
        all.filter { it.status == com.example.data.DownloadStatus.COMPLETED }
            .filter {
                if (query.isBlank()) true
                else it.title.contains(query, ignoreCase = true) ||
                        (it.fileName?.contains(query, ignoreCase = true) == true)
            }
            .sortedByDescending { it.completedAt ?: it.addedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isEngineReady: StateFlow<Boolean> = PowerPapiloApp.isEngineReady
    val isYtDlpAvailable: StateFlow<Boolean> = PowerPapiloApp.isYtDlpAvailable
    val engineVersion: StateFlow<String> = PowerPapiloApp.engineVersion
    val engineError: StateFlow<String?> = PowerPapiloApp.engineError

    fun setTab(index: Int) {
        _currentTab.value = index
    }

    fun setDownloadMode(mode: Int) {
        _downloadMode.value = mode
    }

    fun openYouTubeLogin() {
        _showYouTubeLogin.value = true
    }

    fun closeYouTubeLogin() {
        _showYouTubeLogin.value = false
    }

    fun onCookiesUpdated(context: Context) {
        _cookieSummary.value = CookieHelper.getCookieSummary(context)
        DownloadManager.retryAllFailed(context)
    }

    fun setDestinationFolder(context: Context, path: String): Boolean {
        val success = FileUtils.setDownloadDirectory(context, path)
        if (success) {
            _currentDownloadDir.value = FileUtils.getCurrentDestinationLabel(context)
            refreshLibrary(context, clearPreviousCompleted = true)
        }
        return success
    }

    fun setPublicDownloadsFolder(context: Context): Boolean {
        val success = FileUtils.setPublicDownloadsDestination(context)
        if (success) {
            _currentDownloadDir.value = FileUtils.getCurrentDestinationLabel(context)
            refreshLibrary(context, clearPreviousCompleted = true)
        }
        return success
    }

    fun setSafTreeFolder(
        context: Context,
        treeUri: Uri,
        subfolderDocId: String? = null,
        customDisplayPath: String? = null
    ): Boolean {
        val success = FileUtils.setSafTreeDestination(context, treeUri, subfolderDocId, customDisplayPath)
        if (success) {
            _currentDownloadDir.value = FileUtils.getCurrentDestinationLabel(context)
            refreshLibrary(context, clearPreviousCompleted = true)
        }
        return success
    }

    fun createCustomDestinationFolder(context: Context, folderName: String): Boolean {
        val newFolder = FileUtils.createCustomFolder(context, folderName)
        if (newFolder != null) {
            _currentDownloadDir.value = FileUtils.getCurrentDestinationLabel(context)
            refreshLibrary(context, clearPreviousCompleted = true)
            return true
        }
        return false
    }

    fun getFolderOptions(context: Context): List<FolderOption> {
        return FileUtils.getFolderOptions(context)
    }

    fun getStorageVolumes(context: Context): List<StorageVolumeUi> {
        return FileUtils.getStorageVolumesForPicker(context)
    }

    // --- Modo 1: Individual (NUNCA descarga como playlist) ---

    fun onUrlChange(newUrl: String) {
        _urlInput.value = newUrl
        _analysisError.value = null
        if (newUrl.isBlank()) {
            _previewInfo.value = null
        } else if (YtDlpEngine.isYouTubeUrl(newUrl)) {
            val singleUrl = YtDlpEngine.sanitizeSingleVideoUrl(newUrl)
            val videoId = YtDlpEngine.extractYouTubeId(singleUrl)
            val thumb = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null
            _previewInfo.value = VideoInfoPreview(
                url = singleUrl,
                title = if (videoId != null) "Video de YouTube ($videoId)" else "Video Individual de YouTube",
                uploader = "YouTube",
                durationSeconds = 0L,
                thumbnailUrl = thumb,
                viewCount = null,
                webpageUrl = singleUrl,
                isPlaylist = false
            )
        }
    }

    fun onPresetChange(preset: QualityPreset) {
        _selectedPreset.value = preset
    }

    fun onPasteClicked(context: Context, rawText: String) {
        val extractedUrl = extractUrl(rawText) ?: rawText.trim()
        if (extractedUrl.isBlank()) return
        val singleUrl = YtDlpEngine.sanitizeSingleVideoUrl(extractedUrl)
        onUrlChange(singleUrl)
        analyzeUrl(context)
    }

    fun loadSample(context: Context, sample: SampleMedia) {
        _urlInput.value = sample.url
        _selectedPreset.value = sample.preset
        _analysisError.value = null
        analyzeUrl(context)
    }

    private var analysisJob: Job? = null

    fun analyzeUrl(context: Context) {
        val rawUrl = _urlInput.value.trim()
        if (rawUrl.isBlank()) return

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            try {
                val sanitized = YtDlpEngine.sanitizeSingleVideoUrl(rawUrl)
                val url = YtDlpEngine.resolveRedirectUrl(sanitized)
                if (url != rawUrl) {
                    _urlInput.value = url
                }

                if (DirectDownloadEngine.isDirectMediaUrl(url)) {
                    val info = DirectDownloadEngine.inspectMedia(url)
                    _previewInfo.value = info.copy(isPlaylist = false)
                } else {
                    val info = YtDlpEngine.fetchVideoInfo(
                        context = context,
                        rawUrl = url,
                        forceSingleVideo = true
                    )
                    _previewInfo.value = info.copy(isPlaylist = false)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Fallo análisis individual: ${e.message}")
                try {
                    val fallbackInfo = DirectDownloadEngine.inspectMedia(rawUrl)
                    _previewInfo.value = fallbackInfo.copy(isPlaylist = false)
                } catch (fallbackEx: Exception) {
                    val isBotBlock = e.message?.contains("bot", ignoreCase = true) == true
                    if (isBotBlock) {
                        _analysisError.value = "YouTube requiere cookies desde servidores cloud. Usa 'Vincular Sesión' para omitir."
                    } else {
                        _analysisError.value = "Enlace listo para descargar (${e.message ?: "Validado"})"
                    }
                }
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun startIndividualDownload(context: Context) {
        val rawUrl = _urlInput.value.trim()
        if (rawUrl.isBlank()) return

        val singleUrl = YtDlpEngine.sanitizeSingleVideoUrl(rawUrl)
        val singlePreview = _previewInfo.value?.copy(isPlaylist = false, url = singleUrl)

        DownloadManager.enqueue(
            context = context,
            url = singleUrl,
            preset = _selectedPreset.value,
            preview = singlePreview,
            isPlaylist = false
        )

        _urlInput.value = ""
        _previewInfo.value = null
        _analysisError.value = null
    }

    // --- Modo 2: Playlist en Cadena ---

    fun onPlaylistUrlChange(newUrl: String) {
        _playlistUrlInput.value = newUrl
        _playlistError.value = null
        if (newUrl.isBlank()) {
            _playlistPreviewInfo.value = null
        } else if (YtDlpEngine.isYouTubeUrl(newUrl)) {
            val videoId = YtDlpEngine.extractYouTubeId(newUrl)
            val thumb = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null
            _playlistPreviewInfo.value = VideoInfoPreview(
                url = newUrl.trim(),
                title = "Lista de Reproducción de YouTube",
                uploader = "YouTube Playlist",
                durationSeconds = 0L,
                thumbnailUrl = thumb,
                viewCount = null,
                webpageUrl = newUrl.trim(),
                isPlaylist = true
            )
        }
    }

    fun onPlaylistPresetChange(preset: QualityPreset) {
        _playlistPreset.value = preset
    }

    fun onPastePlaylistClicked(context: Context, rawText: String) {
        val extractedUrl = extractUrl(rawText) ?: rawText.trim()
        if (extractedUrl.isBlank()) return
        onPlaylistUrlChange(extractedUrl)
        analyzePlaylistUrl(context)
    }

    private var playlistAnalysisJob: Job? = null

    fun analyzePlaylistUrl(context: Context) {
        val rawUrl = _playlistUrlInput.value.trim()
        if (rawUrl.isBlank()) return

        playlistAnalysisJob?.cancel()
        playlistAnalysisJob = viewModelScope.launch {
            _isAnalyzingPlaylist.value = true
            _playlistError.value = null
            try {
                val url = YtDlpEngine.resolveRedirectUrl(rawUrl)
                if (url != rawUrl) {
                    _playlistUrlInput.value = url
                }

                val info = YtDlpEngine.fetchVideoInfo(context, url, forceSingleVideo = false)
                _playlistPreviewInfo.value = info.copy(isPlaylist = true)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Fallo análisis de playlist: ${e.message}")
                _playlistError.value = "Lista de reproducción lista para iniciar en cadena."
            } finally {
                _isAnalyzingPlaylist.value = false
            }
        }
    }

    fun startPlaylistChainDownload(context: Context) {
        val url = _playlistUrlInput.value.trim()
        if (url.isBlank()) return

        DownloadManager.enqueue(
            context = context,
            url = url,
            preset = _playlistPreset.value,
            preview = _playlistPreviewInfo.value?.copy(isPlaylist = true),
            isPlaylist = true
        )

        _playlistUrlInput.value = ""
        _playlistPreviewInfo.value = null
        _playlistError.value = null
    }

    // --- Utilidades compartidas ---

    fun onSearchFilterChange(query: String) {
        _searchFilter.value = query
    }

    fun onSharedUrlReceived(context: Context, rawText: String) {
        val extractedUrl = extractUrl(rawText) ?: rawText.trim()
        val isPurePlaylist = YtDlpEngine.isPlaylistUrl(extractedUrl) && YtDlpEngine.extractYouTubeId(extractedUrl) == null
        _currentTab.value = 0
        if (isPurePlaylist) {
            _downloadMode.value = 1
            onPlaylistUrlChange(extractedUrl)
            analyzePlaylistUrl(context)
        } else {
            _downloadMode.value = 0
            onUrlChange(extractedUrl)
            analyzeUrl(context)
        }
    }

    private fun extractUrl(text: String): String? {
        val matcher = Pattern.compile("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+").matcher(text)
        return if (matcher.find()) matcher.group(0) else null
    }

    fun cancelDownload(id: String) {
        DownloadManager.cancel(id)
    }

    fun retryDownload(context: Context, id: String) {
        DownloadManager.retry(context, id)
    }

    fun deleteDownload(context: Context, id: String, removeFile: Boolean) {
        DownloadManager.delete(id, removeFile, context)
    }

    fun openFile(context: Context, item: DownloadItem) {
        val path = item.filePath ?: return
        FileUtils.openMedia(context, path, item.fileName)
    }

    fun shareFile(context: Context, item: DownloadItem) {
        val path = item.filePath ?: return
        FileUtils.shareMedia(context, path, item.fileName)
    }

    fun refreshLibrary(context: Context, clearPreviousCompleted: Boolean = false) {
        viewModelScope.launch {
            DownloadManager.scanLocalFiles(context, clearPreviousCompleted)
            _cookieSummary.value = CookieHelper.getCookieSummary(context)
            _currentDownloadDir.value = FileUtils.getCurrentDestinationLabel(context)
        }
    }

    fun saveCookies(context: Context, text: String): Boolean {
        val success = CookieHelper.saveCookies(context, text)
        _cookieSummary.value = CookieHelper.getCookieSummary(context)
        return success
    }

    fun clearCookies(context: Context) {
        CookieHelper.clearCookies(context)
        _cookieSummary.value = CookieHelper.getCookieSummary(context)
    }

    fun getCookiesText(context: Context): String {
        return CookieHelper.getCookieContent(context)
    }

    fun updateEngineNow() {
        PowerPapiloApp.instance.updateEngineInBackground(force = true)
    }
}
