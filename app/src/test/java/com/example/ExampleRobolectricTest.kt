package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.QualityPreset
import com.example.download.DirectDownloadEngine
import com.example.download.YtDlpEngine
import com.example.util.CookieHelper
import com.example.util.FileUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("POWER PAPILO", appName)
    }

    @Test
    fun `test file utils formatting`() {
        assertEquals("1 KB", FileUtils.formatBytes(1024))
        assertEquals("1 MB", FileUtils.formatBytes(1048576))
        assertEquals("00:45", FileUtils.formatDuration(45))
        assertEquals("01:05", FileUtils.formatDuration(65))
        assertEquals("01:01:05", FileUtils.formatDuration(3665))
        assertEquals("45s", FileUtils.formatEta(45))
        assertEquals("2m 5s", FileUtils.formatEta(125))
    }

    @Test
    fun `test presets configuration including webp`() {
        assertTrue(QualityPreset.AUDIO_MP3.isAudioOnly)
        assertTrue(QualityPreset.AUDIO_M4A.isAudioOnly)
        assertFalse(QualityPreset.BEST.isAudioOnly)
        assertFalse(QualityPreset.P1080.isAudioOnly)
        assertFalse(QualityPreset.P720.isAudioOnly)

        // WebP test
        assertTrue(QualityPreset.IMAGE_WEBP.isWebP)
        assertFalse(QualityPreset.IMAGE_WEBP.isAudioOnly)
        assertEquals("webp_image", QualityPreset.IMAGE_WEBP.id)
    }

    @Test
    fun `test mime type for webp and media files`() {
        assertEquals("image/webp", FileUtils.getMimeType(File("image.webp")))
        assertEquals("video/mp4", FileUtils.getMimeType(File("video.mp4")))
        assertEquals("video/webm", FileUtils.getMimeType(File("video.webm")))
        assertEquals("audio/mpeg", FileUtils.getMimeType(File("music.mp3")))
    }

    @Test
    fun `test custom download folder selection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = FileUtils.getFolderOptions(context)
        assertTrue(options.isNotEmpty())

        val customTestDir = File(context.filesDir, "TestCustomFolder")
        val success = FileUtils.setDownloadDirectory(context, customTestDir.absolutePath)
        assertTrue(success)

        val currentDir = FileUtils.getDownloadDirectory(context)
        assertEquals(customTestDir.absolutePath, currentDir.absolutePath)
    }

    @Test
    fun `test direct media url detection`() {
        assertTrue(DirectDownloadEngine.isDirectMediaUrl("https://example.com/video.mp4"))
        assertTrue(DirectDownloadEngine.isDirectMediaUrl("https://example.com/song.mp3?token=123"))
        assertTrue(DirectDownloadEngine.isDirectMediaUrl("https://example.com/audio.m4a"))
        assertFalse(DirectDownloadEngine.isDirectMediaUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `test youtube id and playlist extraction`() {
        val id1 = YtDlpEngine.extractYouTubeId("https://m.youtube.com/watch?v=BX8Dbsa0lB4")
        assertEquals("BX8Dbsa0lB4", id1)

        val id2 = YtDlpEngine.extractYouTubeId("https://youtu.be/GAeuN6qM_4k")
        assertEquals("GAeuN6qM_4k", id2)

        assertTrue(YtDlpEngine.isPlaylistUrl("https://www.youtube.com/playlist?list=PLrEnWoR732-BHrPp_Pm8_VleD68f9n14-"))
        assertTrue(YtDlpEngine.isPlaylistUrl("https://www.youtube.com/watch?v=abc&list=PL123"))
        assertFalse(YtDlpEngine.isPlaylistUrl("https://www.youtube.com/watch?v=abc"))
    }

    @Test
    fun `test cookie helper save and clear`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleCookies = "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t1799999999\tSID\tsample_session"
        val saved = CookieHelper.saveCookies(context, sampleCookies)
        assertTrue(saved)
        assertTrue(CookieHelper.hasCookies(context))

        CookieHelper.clearCookies(context)
        assertFalse(CookieHelper.hasCookies(context))
    }

    @Test
    fun `test web cookies conversion to netscape format`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webCookieHeader = "VISITOR_INFO1_LIVE=abc123xyz; LOGIN_INFO=AFmmF2kwRQ...; SID=sample_google_sid; __Secure-3PSID=sample_secure_sid"

        val count = CookieHelper.saveWebCookies(context, webCookieHeader)
        assertTrue(count >= 4)
        assertTrue(CookieHelper.hasCookies(context))
        assertTrue(CookieHelper.hasAuthCookies(context))

        val content = CookieHelper.getCookieContent(context)
        assertTrue(content.contains("# Netscape HTTP Cookie File"))
        assertTrue(content.contains(".youtube.com\tTRUE\t/\t"))
        assertTrue(content.contains("LOGIN_INFO"))
        assertTrue(content.contains(".google.com\tTRUE\t/\t"))

        CookieHelper.clearCookies(context)
        assertFalse(CookieHelper.hasCookies(context))
    }

    @Test
    fun `test youtube url resolves immediately without network blocking`() = runBlocking {
        val ytUrl = "https://m.youtube.com/watch?v=0zmdCZ-m9Ns"
        val resolved = YtDlpEngine.resolveRedirectUrl(ytUrl)
        assertEquals(ytUrl, resolved)
    }

    @Test
    fun `test speed parsing from ytdlp log`() {
        val line = "[download]  45.2% of  25.00MiB at  3.45MiB/s ETA 00:04"
        val speed = YtDlpEngine.parseSpeed(line)
        assertNotNull(speed)
        assertEquals("3.45MiB/s", speed?.trim())
    }
}
