package com.branchphotovault.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.branchphotovault.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class KeeperRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val newer: Boolean
)

data class KeeperDownloadProgress(val status: Int, val done: Long, val total: Long)

/** Never installs silently. Only verified stable GitHub assets are accepted. */
class GithubReleaseUpdater(private val context: Context) {
    private val prefs = context.getSharedPreferences("keeper_official_updates", Context.MODE_PRIVATE)
    private val downloads get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val pendingId: Long get() = prefs.getLong("id", -1L)

    /** A successfully installed update must not reappear as an obsolete pending download. */
    fun activeDownloadId(): Long {
        val saved = prefs.getString("tag", null)
        if (pendingId != -1L && saved != null &&
            compareVersions(saved, BuildConfig.VERSION_NAME) <= 0
        ) {
            discard()
        }
        return pendingId
    }

    suspend fun latest(): KeeperRelease = withContext(Dispatchers.IO) {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("User-Agent", "ImageKeeper-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode != 200)
                error("GitHub API ตอบกลับ HTTP " + connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRelease(body, BuildConfig.VERSION_NAME)
        } finally { connection.disconnect() }
    }

    fun start(release: KeeperRelease): Long {
        require(release.newer) { "เวอร์ชันนี้ไม่ใหม่กว่าแอปที่ติดตั้ง" }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("ไม่พบโฟลเดอร์ดาวน์โหลด")
        dir.mkdirs()
        val target = File(dir, FILE_NAME)
        if (target.exists() && !target.delete()) error("ลบไฟล์อัปเดตเก่าไม่ได้")
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("Image Keeper " + release.tag)
            .setDescription("กำลังดาวน์โหลดอัปเดตจาก GitHub")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, FILE_NAME)
        if (pendingId != -1L) downloads.remove(pendingId)
        val id = downloads.enqueue(request)
        prefs.edit().putLong("id", id).putString("sha256", release.sha256)
            .putString("tag", release.tag).apply()
        return id
    }

    fun progress(id: Long): KeeperDownloadProgress? {
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            return KeeperDownloadProgress(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            )
        }
        return null
    }

    suspend fun verifiedApk(): File = withContext(Dispatchers.IO) {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("ไม่มีโฟลเดอร์"),
            FILE_NAME
        )
        if (!file.isFile || file.length() < 1024) error("ไม่พบไฟล์ APK")
        val expected = prefs.getString("sha256", null) ?: error("ไม่มี SHA-256 ที่ตรวจสอบได้")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected, true)) {
            file.delete()
            error("ไฟล์อัปเดตไม่ผ่านการตรวจสอบ SHA-256")
        }
        @Suppress("DEPRECATION")
        val signatureFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val apk = context.packageManager.getPackageArchiveInfo(
            file.absolutePath, signatureFlag
        ) ?: error("APK ใช้งานไม่ได้")
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(
            context.packageName, signatureFlag
        )
        if (apk.packageName != context.packageName) error("APK เป็นคนละแอป")
        if (androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(apk) <=
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(installed)
        ) error("APK ไม่ใหม่กว่าที่ติดตั้ง")
        @Suppress("DEPRECATION")
        val expectedSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners
        } else {
            installed.signatures
        } ?: error("ไม่พบลายเซ็นแอป")
        @Suppress("DEPRECATION")
        val receivedSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            apk.signingInfo?.apkContentsSigners
        } else {
            apk.signatures
        } ?: error("ไม่พบลายเซ็น APK")
        if (expectedSigners.size != receivedSigners.size ||
            expectedSigners.any { old ->
                receivedSigners.none { it.toByteArray().contentEquals(old.toByteArray()) }
            }
        ) error("ลายเซ็น APK ไม่ตรงกับแอปเดิม")
        file
    }

    /** Returns false when Android requires user authorization for installing APKs. */
    fun install(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        return true
    }

    fun discard() {
        if (pendingId != -1L) downloads.remove(pendingId)
        prefs.edit().clear().apply()
    }

    companion object {
        /** Separate parser allows security testing without a network connection. */
        fun parseRelease(jsonText: String, currentVersion: String): KeeperRelease {
            val json = JSONObject(jsonText)
            if (json.optBoolean("prerelease") || json.optBoolean("draft"))
                error("ไม่พบ Stable Release")
            val tag = json.getString("tag_name")
            if (!tag.matches(Regex("v?\\d+\\.\\d+\\.\\d+")))
                error("เลขเวอร์ชันไม่ถูกต้อง")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull {
                    it.optString("name").startsWith("ImageKeeper", true) &&
                        it.optString("name").endsWith(".apk", true)
                } ?: error("Release ล่าสุดไม่มี APK")
            val url = apk.getString("browser_download_url")
            if (!url.startsWith("https://github.com/surakiatforwork-art/Image_Keeper/releases/download/"))
                error("แหล่งดาวน์โหลดไม่ถูกต้อง")
            val sha = apk.optString("digest").removePrefix("sha256:").lowercase()
            if (!sha.matches(Regex("[0-9a-f]{64}")))
                error("Release ไม่ได้ระบุ SHA-256")
            return KeeperRelease(
                tag, json.optString("name").ifBlank { tag },
                json.optString("body").take(5000), url, sha, apk.optLong("size"),
                compareVersions(tag, currentVersion) > 0
            )
        }

        const val API = "https://api.github.com/repos/surakiatforwork-art/Image_Keeper/releases/latest"
        private const val FILE_NAME = "ImageKeeper_official_update.apk"
        fun compareVersions(remote: String, current: String): Int {
            fun parts(s: String): List<Int>? {
                val t = s.removePrefix("v").substringBefore("-").substringBefore("+").split(".")
                if (t.size != 3) return null
                return t.map { it.toIntOrNull() ?: return null }
            }
            val a = parts(remote) ?: return 0
            val b = parts(current) ?: return 0
            for (i in 0..2) if (a[i] != b[i]) return a[i].compareTo(b[i])
            // A stable version is newer than a prerelease of the same base version.
            val remotePre = remote.substringBefore("+").contains('-')
            val currentPre = current.substringBefore("+").contains('-')
            if (remotePre != currentPre) return if (remotePre) -1 else 1
            return 0
        }
    }
}
