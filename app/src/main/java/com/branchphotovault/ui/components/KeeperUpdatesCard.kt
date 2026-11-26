package com.branchphotovault.ui.components

import android.app.DownloadManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.branchphotovault.BuildConfig
import com.branchphotovault.data.update.GithubReleaseUpdater
import com.branchphotovault.data.update.KeeperRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Official updates. No silent installs, no unverified APKs. */
@Composable
fun KeeperUpdatesCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val updater = remember(context) { GithubReleaseUpdater(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<KeeperRelease?>(null) }
    var status by remember { mutableStateOf("ตรวจสอบจาก GitHub Release ทางการ") }
    var downloadedId by remember { mutableLongStateOf(updater.activeDownloadId()) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var verified by remember { mutableStateOf(false) }
    var checkingFile by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    suspend fun fetchUpdates() {
        if (busy) return
        busy = true
        try {
            val latest = updater.latest()
            release = latest
            status = if (latest.newer)
                "มีอัปเดตใหม่ ${latest.tag} • ${latest.size / 1048576} MB"
            else
                "ใช้เวอร์ชันล่าสุดแล้ว (${BuildConfig.VERSION_NAME})"
        } catch (error: Exception) {
            status = "ตรวจสอบไม่สำเร็จ: " + (error.message ?: "เชื่อมต่อไม่ได้")
        } finally {
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        fetchUpdates()
    }

    LaunchedEffect(downloadedId) {
        if (downloadedId == -1L) return@LaunchedEffect
        while (true) {
            val current = try {
                withContext(Dispatchers.IO) { updater.progress(downloadedId) }
            } catch (error: Exception) {
                status = "ตรวจสอบดาวน์โหลดไม่ได้: " + (error.message ?: "เกิดข้อผิดพลาด")
                break
            }
            if (current == null) {
                status = "ไม่พบงานดาวน์โหลด"
                updater.discard()
                downloadedId = -1L
                break
            }
            when (current.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    checkingFile = true
                    try {
                        updater.verifiedApk()
                        verified = true
                        progress = 1f
                        status = "ดาวน์โหลดสำเร็จ • ตรวจสอบ SHA-256 และลายเซ็นผ่านแล้ว"
                    } catch (error: Exception) {
                        verified = false
                        updater.discard()
                        downloadedId = -1L
                        status = "ไฟล์ไม่ผ่านการตรวจสอบ: " + (error.message ?: "")
                    } finally {
                        checkingFile = false
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    updater.discard()
                    downloadedId = -1L
                    status = "ดาวน์โหลดไม่สำเร็จ กรุณาลองอีกครั้ง"
                    break
                }
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED -> {
                    progress = if (current.total > 0)
                        (current.done.toFloat() / current.total).coerceIn(0f, 1f) else null
                    status = if (current.total > 0)
                        "กำลังดาวน์โหลด ${current.done / 1048576} / ${current.total / 1048576} MB"
                    else "กำลังเตรียมดาวน์โหลดผ่าน Android"
                }
            }
            delay(850)
        }
    }

    GlassCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.SystemUpdate, contentDescription = null,
                    tint = scheme.primary, modifier = Modifier.size(23.dp)
                )
                Text("อัปเดตแอป", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Image Keeper ${BuildConfig.VERSION_NAME} • GitHub Stable Release",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Text(status, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            release?.takeIf { it.newer }?.let { item ->
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.notes.isNotBlank()) {
                    Text(
                        item.notes.trim().take(420),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        color = scheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (downloadedId != -1L && !verified) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (checkingFile) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("กำลังตรวจสอบ APK ที่ดาวน์โหลด")
                }
            }
            when {
                verified -> GlassButton(
                    onClick = {
                        scope.launch {
                            try {
                                val file = updater.verifiedApk()
                                if (!updater.install(file)) {
                                    status = "อนุญาตติดตั้งจากแหล่งนี้ แล้วกลับมากดติดตั้งอีกครั้ง"
                                }
                            } catch (error: Exception) {
                                status = "เปิดหน้าติดตั้งไม่ได้: " + (error.message ?: "")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.Security, contentDescription = null)
                    Text("ติดตั้งอัปเดตที่ตรวจสอบแล้ว")
                }
                downloadedId == -1L && release?.newer == true -> GlassButton(
                    onClick = {
                        try {
                            downloadedId = updater.start(release!!)
                            verified = false
                            progress = null
                        } catch (error: Exception) {
                            status = "เริ่มดาวน์โหลดไม่ได้: " + (error.message ?: "")
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    Text("ดาวน์โหลดอัปเดต ${release?.tag ?: ""}")
                }
            }
            GlassSecondaryButton(
                onClick = { scope.launch { fetchUpdates() } },
                enabled = !busy && !checkingFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("ตรวจสอบเวอร์ชันล่าสุด")
            }
            Text(
                "ตรวจสอบไฟล์ด้วย SHA-256 และลายเซ็นแอปก่อนติดตั้ง • ต้องยืนยันการติดตั้งด้วยตนเอง",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}
