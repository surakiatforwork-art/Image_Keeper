package com.branchphotovault.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object GalleryNaming {
    const val RELATIVE_PATH = "Pictures/Image Keeper/"
    private val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT)

    fun fileName(account: String, branchCode: String, savedAt: Long = System.currentTimeMillis(),
        uniqueId: String = UUID.randomUUID().toString(), zone: ZoneId = ZoneId.systemDefault()): String {
        fun safe(value: String): String {
            var part = value.trim().replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "unknown" }
            while (part.toByteArray(Charsets.UTF_8).size > 70) part = part.dropLast(1)
            return part
        }
        val time = timestamp.format(Instant.ofEpochMilli(savedAt).atZone(zone))
        return "${safe(account)}_${safe(branchCode)}_${time}_${safe(uniqueId)}.jpg"
    }
}
