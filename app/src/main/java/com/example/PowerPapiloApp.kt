package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PowerPapiloApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "power_papilo_download_channel"
        private const val TAG = "PowerPapiloApp"

        lateinit var instance: PowerPapiloApp
            private set

        private val _isEngineReady = MutableStateFlow(false)
        val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

        private val _isYtDlpAvailable = MutableStateFlow(false)
        val isYtDlpAvailable: StateFlow<Boolean> = _isYtDlpAvailable.asStateFlow()

        private val _engineVersion = MutableStateFlow("Iniciando motor...")
        val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

        private val _engineError = MutableStateFlow<String?>(null)
        val engineError: StateFlow<String?> = _engineError.asStateFlow()
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
        applicationScope.launch {
            var ytdlpSuccess = false
            try {
                val nativeLibDir = applicationInfo.nativeLibraryDir
                if (!nativeLibDir.isNullOrBlank() && File(nativeLibDir).exists()) {
                    Log.d(TAG, "Inicializando YoutubeDL...")
                    YoutubeDL.getInstance().init(this@PowerPapiloApp)
                    Log.d(TAG, "YoutubeDL inicializado. Inicializando FFmpeg...")
                    FFmpeg.getInstance().init(this@PowerPapiloApp)
                    Log.d(TAG, "FFmpeg inicializado con éxito.")

                    val version = try {
                        YoutubeDL.getInstance().version(this@PowerPapiloApp) ?: "yt-dlp 2024+"
                    } catch (e: Exception) {
                        "yt-dlp activo"
                    }
                    _engineVersion.value = "yt-dlp ($version) + FFmpeg"
                    _isYtDlpAvailable.value = true
                    ytdlpSuccess = true
                    updateEngineInBackground()
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

            // El motor general de descargas siempre está listo
            _isEngineReady.value = true
        }
    }

    fun updateEngineInBackground() {
        if (!_isYtDlpAvailable.value) return
        applicationScope.launch {
            try {
                Log.d(TAG, "Comprobando actualizaciones de yt-dlp...")
                YoutubeDL.getInstance().updateYoutubeDL(
                    this@PowerPapiloApp,
                    YoutubeDL.UpdateChannel.STABLE
                )
                val updatedVersion = YoutubeDL.getInstance().version(this@PowerPapiloApp)
                if (!updatedVersion.isNullOrBlank()) {
                    _engineVersion.value = "yt-dlp ($updatedVersion) + FFmpeg"
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo actualizar yt-dlp en segundo plano: ${e.message}")
            }
        }
    }
}
