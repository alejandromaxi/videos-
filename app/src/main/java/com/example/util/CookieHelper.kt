package com.example.util

import android.content.Context
import android.util.Log
import java.io.File

object CookieHelper {
    private const val TAG = "CookieHelper"
    private const val COOKIE_FILENAME = "cookies.txt"

    fun getCookieFile(context: Context): File {
        return File(context.filesDir, COOKIE_FILENAME)
    }

    fun hasCookies(context: Context): Boolean {
        val file = getCookieFile(context)
        return file.exists() && file.length() > 10
    }

    fun hasAuthCookies(context: Context): Boolean {
        val file = getCookieFile(context)
        if (!file.exists() || file.length() == 0L) return false
        val content = file.readText()
        return content.contains("LOGIN_INFO") ||
                content.contains("SID") ||
                content.contains("__Secure-1PSID") ||
                content.contains("__Secure-3PSID") ||
                content.contains("SAPISID")
    }

    fun saveCookies(context: Context, cookieContent: String): Boolean {
        return try {
            val file = getCookieFile(context)
            file.writeText(cookieContent.trim())
            Log.d(TAG, "Cookies guardadas exitosamente (${file.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando cookies: ${e.message}", e)
            false
        }
    }

    /**
     * Convierte el encabezado de cookies crudas obtenido de WebView CookieManager
     * (formato: "name=val; name2=val2") al formato estándar Netscape tab-separated
     * aceptado por yt-dlp y curl.
     */
    fun saveWebCookies(context: Context, rawCookieHeader: String): Int {
        if (rawCookieHeader.isBlank()) return 0
        return try {
            val pairs = rawCookieHeader.split(";")
            val sb = StringBuilder()
            sb.appendLine("# Netscape HTTP Cookie File")
            sb.appendLine("# Generado automáticamente por POWER PAPILO (WebView Login)")
            sb.appendLine("# http://curl.haxx.se/rfc/cookie_spec.html")
            sb.appendLine()

            // Expiración a 10 años en el futuro
            val expiry = (System.currentTimeMillis() / 1000L) + 315360000L
            var cookieCount = 0

            for (pair in pairs) {
                val trimmed = pair.trim()
                if (trimmed.isEmpty()) continue
                val eqIndex = trimmed.indexOf('=')
                if (eqIndex <= 0) continue

                val name = trimmed.substring(0, eqIndex).trim()
                val value = trimmed.substring(eqIndex + 1).trim()
                if (name.isEmpty()) continue

                val isSecure = name.startsWith("__Secure-") ||
                        name in listOf("SAPISID", "SSID", "APISID", "HSID", "SID")
                val secureStr = if (isSecure) "TRUE" else "FALSE"

                // Escribir para .youtube.com
                sb.appendLine(".youtube.com\tTRUE\t/\t$secureStr\t$expiry\t$name\t$value")

                // Si es token de cuenta Google, duplicar para .google.com
                if (name in listOf("SID", "HSID", "SSID", "APISID", "SAPISID", "__Secure-1PSID", "__Secure-3PSID", "LOGIN_INFO")) {
                    sb.appendLine(".google.com\tTRUE\t/\t$secureStr\t$expiry\t$name\t$value")
                }
                cookieCount++
            }

            val file = getCookieFile(context)
            file.writeText(sb.toString().trim())
            Log.d(TAG, "Guardadas $cookieCount cookies en formato Netscape (${file.length()} bytes)")
            cookieCount
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo cookies de WebView: ${e.message}", e)
            0
        }
    }

    fun clearCookies(context: Context): Boolean {
        return try {
            val file = getCookieFile(context)
            if (file.exists()) {
                file.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando cookies: ${e.message}", e)
            false
        }
    }

    fun getCookieSummary(context: Context): String {
        val file = getCookieFile(context)
        if (!file.exists() || file.length() == 0L) {
            return "Sin cookies configuradas"
        }
        val isAuth = hasAuthCookies(context)
        val lines = file.readLines().count { it.isNotBlank() && !it.startsWith("#") }
        return if (isAuth) {
            "Sesión vinculada activa ($lines cookies)"
        } else {
            "Cookies anónimas ($lines líneas)"
        }
    }

    fun getCookieContent(context: Context): String {
        val file = getCookieFile(context)
        return if (file.exists()) file.readText() else ""
    }
}
