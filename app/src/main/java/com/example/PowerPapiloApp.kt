package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.download.DownloadManager
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class PowerPapiloApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "power_papilo_download_channel"
        private const val TAG = "PowerPapiloApp"
        private const val PREFS_NAME = "power_papilo_prefs"
        private const val KEY_LAST_YTDLP_UPDATE_MS = "last_ytdlp_update_ms"
        private const val AUTO_UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L

        lateinit var instance: PowerPapiloApp
            private set

        private var engineInitDeferred = CompletableDeferred<Boolean>()

        private val _isEngineReady = MutableStateFlow(false)
        val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

        private val _isYtDlpAvailable = MutableStateFlow(false)
        val isYtDlpAvailable: StateFlow<Boolean> = _isYtDlpAvailable.asStateFlow()

        private val _isFfmpegAvailable = MutableStateFlow(false)
        val isFfmpegAvailable: StateFlow<Boolean> = _isFfmpegAvailable.asStateFlow()

        private val _isAria2cAvailable = MutableStateFlow(false)
        val isAria2cAvailable: StateFlow<Boolean> = _isAria2cAvailable.asStateFlow()

        private val _engineVersion = MutableStateFlow("Iniciando motor...")
        val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

        private val _engineError = MutableStateFlow<String?>(null)
        val engineError: StateFlow<String?> = _engineError.asStateFlow()

        suspend fun awaitEngineReady(timeoutMs: Long = 12_000L): Boolean {
            if (_isEngineReady.value) return _isYtDlpAvailable.value
            return withTimeoutOrNull(timeoutMs) {
                engineInitDeferred.await()
            } ?: _isYtDlpAvailable.value
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        initEngines()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun initEngines() {
        engineInitDeferred = CompletableDeferred()
        applicationScope.launch {
            var ytdlpSuccess = false
            var ffmpegSuccess = false
            var aria2cSuccess = false

            try {
                val nativeLibDir = applicationInfo.nativeLibraryDir
                if (!nativeLibDir.isNullOrBlank() && File(nativeLibDir).exists()) {
                    Log.d(TAG, "Inicializando YoutubeDL...")
                    YoutubeDL.getInstance().init(this@PowerPapiloApp)
                    ytdlpSuccess = true
                    _isYtDlpAvailable.value = true

                    try {
                        Log.d(TAG, "Inicializando FFmpeg...")
                        FFmpeg.getInstance().init(this@PowerPapiloApp)
                        ffmpegSuccess = true
                        _isFfmpegAvailable.value = true
                        Log.d(TAG, "FFmpeg inicializado con éxito.")
                    } catch (e: Throwable) {
                        Log.w(TAG, "FFmpeg no pudo inicializarse: ${e.message}")
                    }

                    try {
                        Log.d(TAG, "Inicializando Aria2c...")
                        Aria2c.getInstance().init(this@PowerPapiloApp)
                        aria2cSuccess = true
                        _isAria2cAvailable.value = true
                        Log.d(TAG, "Aria2c inicializado con éxito.")
                    } catch (e: Throwable) {
                        Log.w(TAG, "Aria2c no disponible en este dispositivo: ${e.message}")
                    }

                    val version = try {
                        YoutubeDL.getInstance().version(this@PowerPapiloApp) ?: "yt-dlp activo"
                    } catch (e: Exception) {
                        "yt-dlp activo"
                    }
                    _engineVersion.value = buildEngineLabel(version, ffmpegSuccess, aria2cSuccess)
                } else {
                    Log.w(TAG, "Directorio nativo no disponible o emulador sin librerías compartidas.")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "yt-dlp nativo no disponible en este dispositivo/emulador: ${e.message}. Activando motor directo streaming.")
                _engineError.value = "yt-dlp nativo: ${e.message ?: "Sin soporte de arquitectura"}"
            }

            if (!ytdlpSuccess) {
                _engineVersion.value = "Motor Directo Streaming (Alta Velocidad)"
                _isYtDlpAvailable.value = false
            }

            _isEngineReady.value = true
            engineInitDeferred.complete(ytdlpSuccess)

            if (ytdlpSuccess) {
                updateEngineInBackground(force = false)
            }
        }
    }

    private fun buildEngineLabel(ytdlpVer: String, ffmpegOk: Boolean, aria2cOk: Boolean): String {
        val parts = mutableListOf("yt-dlp ($ytdlpVer)")
        if (ffmpegOk) parts.add("FFmpeg")
        if (aria2cOk) parts.add("Aria2c")
        return parts.joinToString(" + ")
    }

    fun updateEngineInBackground(force: Boolean = false) {
        if (!_isYtDlpAvailable.value) return
        applicationScope.launch {
            try {
                if (!force && DownloadManager.activeItem.value != null) {
                    Log.d(TAG, "Posponiendo auto-actualización de yt-dlp: descarga activa en curso")
                    return@launch
                }

                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastCheck = prefs.getLong(KEY_LAST_YTDLP_UPDATE_MS, 0L)
                val now = System.currentTimeMillis()
                if (!force && (now - lastCheck) < AUTO_UPDATE_INTERVAL_MS) {
                    return@launch
                }

                Log.d(TAG, "Actualizando yt-dlp (canal NIGHTLY/STABLE)...")
                val status = try {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@PowerPapiloApp,
                        YoutubeDL.UpdateChannel.NIGHTLY
                    )
                } catch (nightlyEx: Exception) {
                    Log.w(TAG, "Canal NIGHTLY falló, intentando STABLE: ${nightlyEx.message}")
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@PowerPapiloApp,
                        YoutubeDL.UpdateChannel.STABLE
                    )
                }

                prefs.edit().putLong(KEY_LAST_YTDLP_UPDATE_MS, now).apply()
                Log.d(TAG, "Resultado actualización yt-dlp: $status")

                val updatedVersion = YoutubeDL.getInstance().version(this@PowerPapiloApp)
                if (!updatedVersion.isNullOrBlank()) {
                    _engineVersion.value = buildEngineLabel(
                        updatedVersion,
                        _isFfmpegAvailable.value,
                        _isAria2cAvailable.value
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo actualizar yt-dlp en segundo plano: ${e.message}")
            }
        }
    }
}
