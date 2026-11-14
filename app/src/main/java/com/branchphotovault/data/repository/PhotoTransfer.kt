package com.branchphotovault.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.branchphotovault.data.local.AppDatabase
import com.branchphotovault.data.local.BranchEntity
import com.branchphotovault.data.local.PhotoEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class TransferFilter(val from: LocalDate? = null, val through: LocalDate? = null, val shop: Pair<String, String>? = null) {
    fun matches(photo: PhotoEntity): Boolean {
        val zone = ZoneId.systemDefault()
        return (shop == null || shop == (photo.account to photo.branchCode)) &&
            (from == null || photo.createdAt >= from.atStartOfDay(zone).toInstant().toEpochMilli()) &&
            (through == null || photo.createdAt < through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }
}

data class TransferManifest(val format: String, val photos: List<PhotoEntity>, val branches: List<BranchEntity>)
data class TransferResult(val imported: Int, val skipped: Int)

class PhotoTransfer(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.getInstance(app)
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(TransferManifest::class.java)

    suspend fun shops() = withContext(Dispatchers.IO) {
        db.photoDao().getAllForTransfer().distinctBy { it.account to it.branchCode }
            .sortedBy { "${it.account} ${it.shopName} ${it.branchCode}" }
    }

    suspend fun archiveShops(uri: Uri): List<PhotoEntity> = withContext(Dispatchers.IO) {
        ZipInputStream(requireNotNull(app.contentResolver.openInputStream(uri)).buffered()).use { zip ->
            require(zip.nextEntry?.name == "manifest.json") { "Select a Keeper transfer ZIP." }
            val meta = requireNotNull(adapter.fromJson(zip.readBounded(32L * 1024 * 1024).toString(Charsets.UTF_8)))
            require(meta.format == FORMAT) { "Invalid Keeper archive." }
            meta.photos.distinctBy { it.account to it.branchCode }.sortedBy { it.shopName }
        }
    }

    suspend fun export(uri: Uri, filter: TransferFilter): Int = withContext(Dispatchers.IO) {
        val photos = db.photoDao().getAllForTransfer().filter(filter::matches)
        require(photos.isNotEmpty()) { "No photos match the filter." }
        val branches = photos.distinctBy { it.account to it.branchCode }.map { photo ->
            db.branchDao().getBranch(photo.account, photo.branchCode)
                ?: BranchEntity(photo.account, photo.branchCode, photo.shopName, photo.routeAtCapture, null, null)
        }
        val manifest = TransferManifest(FORMAT, photos.map { it.copy(localMainPath = "", localThumbPath = "") }, branches)
        ZipOutputStream(requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(adapter.toJson(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photos.forEachIndexed { i, photo ->
                ensureActive()
                listOf(photo.localMainPath, photo.localThumbPath).forEachIndexed { variant, path ->
                    zip.putNextEntry(ZipEntry("photos/$i-$variant.jpg"))
                    File(path).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        photos.size
    }

    suspend fun import(uri: Uri, filter: TransferFilter): TransferResult = withContext(Dispatchers.IO) {
        val folder = File(app.filesDir, "transfers/${UUID.randomUUID()}")
        val added = mutableListOf<PhotoEntity>()
        var skipped = 0
        var manifest: TransferManifest? = null
        var committed = false
        try {
            ZipInputStream(requireNotNull(app.contentResolver.openInputStream(uri)).buffered()).use { zip ->
                require(zip.nextEntry?.name == "manifest.json") { "Select a Keeper transfer ZIP." }
                val meta = requireNotNull(adapter.fromJson(zip.readBounded(32L * 1024 * 1024).toString(Charsets.UTF_8)))
                require(meta.format == FORMAT && meta.photos.size <= 100_000 && meta.photos.map { it.id }.toSet().size == meta.photos.size) { "Invalid Keeper archive." }
                manifest = meta
                meta.photos.forEachIndexed { i, photo ->
                    ensureActive()
                    require(photo.id.isNotBlank() && photo.account.isNotBlank() && photo.branchCode.isNotBlank() && photo.createdAt > 0)
                    val matches = filter.matches(photo)
                    val take = matches && db.photoDao().getPhotoById(photo.id) == null
                    if (matches && !take) skipped++
                    val paths = (0..1).map { variant ->
                        val name = "$i-$variant.jpg"
                        require(zip.nextEntry?.name == "photos/$name") { "Incomplete or invalid photo archive." }
                        if (take) {
                            check(folder.isDirectory || folder.mkdirs())
                            File(folder, name).also { file ->
                                file.outputStream().use { output -> zip.copyBounded(output, 100L * 1024 * 1024) }
                                require(file.length() > 0) { "Empty image in archive." }
                            }.absolutePath
                        } else {
                            zip.copyBounded(null, 100L * 1024 * 1024)
                            ""
                        }
                    }
                    if (take) added += photo.copy(localMainPath = paths[0], localThumbPath = paths[1])
                }
                require(zip.nextEntry == null) { "Unexpected entries in archive." }
            }
            val branches = requireNotNull(manifest).branches.associateBy { it.account to it.branchCode }
            var imported = 0
            ensureActive()
            withContext(NonCancellable) {
              db.withTransaction {
                for (photo in added) {
                    if (db.photoDao().insertForTransfer(photo) == -1L) {
                        skipped++
                    } else {
                        val branch = branches[photo.account to photo.branchCode]
                            ?: BranchEntity(photo.account, photo.branchCode, photo.shopName, photo.routeAtCapture, null, null)
                        db.branchDao().insertForTransfer(branch.copy(rowNumber = null))
                        imported++
                    }
                }
              }
              committed = true
            }
            TransferResult(imported, skipped)
        } finally {
            if (!committed) folder.deleteRecursively()
        }
    }

    private fun InputStream.readBounded(limit: Long): ByteArray =
        java.io.ByteArrayOutputStream().also { copyBounded(it, limit) }.toByteArray()

    private fun InputStream.copyBounded(output: java.io.OutputStream?, limit: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Archive entry is too large." }
            output?.write(buffer, 0, count)
        }
    }

    private companion object { const val FORMAT = "image-keeper-transfer-v1" }
}
