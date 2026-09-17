package com.branchphotovault.data.repository

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.branchphotovault.data.local.BranchDao
import com.branchphotovault.data.local.BranchEntity
import com.branchphotovault.data.local.PhotoDao
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.data.model.ImageAspectRatioOption
import com.branchphotovault.data.model.ExportSummary
import com.branchphotovault.util.AppConfig
import com.branchphotovault.util.DateTimeUtils
import com.branchphotovault.util.FileStorageManager
import com.branchphotovault.util.ImageProcessor
import com.branchphotovault.util.MediaStoreExporter
import com.branchphotovault.worker.ExportPhotosWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

class PhotoRepository(
    private val photoDao: PhotoDao,
    private val branchDao: BranchDao,
    private val settingsRepository: SettingsRepository,
    private val imageProcessor: ImageProcessor,
    private val mediaStoreExporter: MediaStoreExporter,
    context: Context
) {

    private val fileStorageManager = FileStorageManager(context)

    fun observePhotosForBranch(account: String, branchCode: String): Flow<List<PhotoEntity>> {
        return photoDao.observePhotosForBranch(account, branchCode)
    }

    suspend fun getPhotosForBranch(account: String, branchCode: String): List<PhotoEntity> {
        return photoDao.getPhotosForBranch(account, branchCode)
    }

    suspend fun getBranch(account: String, branchCode: String): BranchEntity? {
        return branchDao.getBranch(account, branchCode)
    }

    suspend fun importPhoto(
        branch: BranchEntity,
        sourceUri: Uri,
        routeAtCapture: Int?,
        note: String?
    ): PhotoEntity = withContext(Dispatchers.IO) {
        val imageLongEdge = settingsRepository.getImageLongEdge()
        val aspectRatioOption = ImageAspectRatioOption.fromKey(settingsRepository.getImageAspectRatioKey())
        savePhoto(
            branch = branch,
            routeAtCapture = routeAtCapture,
            note = note
        ) { mainFile, thumbFile ->
            imageProcessor.processUri(
                uri = sourceUri,
                mainFile = mainFile,
                thumbFile = thumbFile,
                mainLongEdge = imageLongEdge,
                aspectRatioOption = aspectRatioOption
            )
        }
    }

    suspend fun saveCapturedPhoto(
        branch: BranchEntity,
        tempFilePath: String,
        routeAtCapture: Int?,
        note: String?,
        mirrorHorizontally: Boolean
    ): PhotoEntity = withContext(Dispatchers.IO) {
        val sourceFile = File(tempFilePath)
        try {
            savePhoto(
                branch = branch,
                routeAtCapture = routeAtCapture,
                note = note
            ) { mainFile, thumbFile ->
                imageProcessor.processFile(
                    sourceFile = sourceFile,
                    mainFile = mainFile,
                    thumbFile = thumbFile,
                    mainLongEdge = AppConfig.CAMERA_CAPTURE_LONG_EDGE,
                    aspectRatioOption = ImageAspectRatioOption.RATIO_3_4,
                    forcePortrait = true,
                    mirrorHorizontally = mirrorHorizontally
                )
            }
        } finally {
            sourceFile.delete()
        }
    }

    suspend fun deletePhotos(photoIds: List<String>): Int = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext 0
        val photos = photoDao.getPhotosByIds(photoIds)
        photos.forEach { photo ->
            fileStorageManager.deleteFile(photo.localMainPath)
            fileStorageManager.deleteFile(photo.localThumbPath)
        }
        photoDao.deleteByIds(photoIds)
        photos.size
    }

    suspend fun cleanupExpiredPhotos(retentionMonths: Int? = null): Int = withContext(Dispatchers.IO) {
        val months = retentionMonths ?: settingsRepository.getRetentionMonths()
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, -months)
        }
        val cutoff = calendar.timeInMillis
        val expiredPhotos = photoDao.getPhotosOlderThan(cutoff)
        if (expiredPhotos.isEmpty()) return@withContext 0

        expiredPhotos.forEach { photo ->
            fileStorageManager.deleteFile(photo.localMainPath)
            fileStorageManager.deleteFile(photo.localThumbPath)
        }
        photoDao.deleteByIds(expiredPhotos.map { it.id })
        expiredPhotos.size
    }

    suspend fun exportPhotos(
        photoIds: List<String>?,
        account: String?,
        branchCode: String?,
        onProgress: suspend (exported: Int, total: Int) -> Unit
    ): ExportSummary = withContext(Dispatchers.IO) {
        val photos = when {
            !photoIds.isNullOrEmpty() -> photoDao.getPhotosByIds(photoIds)
            !account.isNullOrBlank() && !branchCode.isNullOrBlank() -> {
                photoDao.getPhotosForBranch(account, branchCode)
            }

            else -> emptyList()
        }

        exportResolvedPhotos(photos, onProgress)
    }

    suspend fun exportPhotoEntities(
        photos: List<PhotoEntity>,
        onProgress: suspend (exported: Int, total: Int) -> Unit
    ): ExportSummary = withContext(Dispatchers.IO) {
        exportResolvedPhotos(photos, onProgress)
    }

    private suspend fun exportResolvedPhotos(
        photos: List<PhotoEntity>,
        onProgress: suspend (exported: Int, total: Int) -> Unit
    ): ExportSummary {
        val folderPattern = settingsRepository.getExportFolderPattern()
        var exported = 0
        var failed = 0
        val total = photos.size

        photos.forEachIndexed { index, photo ->
            val folderName = resolveExportFolder(folderPattern, photo)
            val displayName = "${photo.branchCode}_${photo.id}.jpg"
            val success = mediaStoreExporter.exportImage(
                sourceFile = File(photo.localMainPath),
                displayName = displayName,
                folderName = folderName
            )
            if (success) {
                exported += 1
            } else {
                failed += 1
            }
            onProgress(index + 1, total)
        }

        return ExportSummary(exportedCount = exported, failedCount = failed)
    }

    fun enqueueExportSelected(
        workManager: WorkManager,
        account: String,
        branchCode: String,
        photoIds: List<String>
    ): UUID {
        return enqueueExportWork(
            workManager = workManager,
            account = account,
            branchCode = branchCode,
            photoIds = photoIds
        )
    }

    fun enqueueExportAll(
        workManager: WorkManager,
        account: String,
        branchCode: String
    ): UUID {
        return enqueueExportWork(
            workManager = workManager,
            account = account,
            branchCode = branchCode,
            photoIds = null
        )
    }

    fun createTempCaptureFile(account: String, branchCode: String): File {
        return fileStorageManager.createCaptureTempFile(account, branchCode)
    }

    private fun enqueueExportWork(
        workManager: WorkManager,
        account: String,
        branchCode: String,
        photoIds: List<String>?
    ): UUID {
        val request = OneTimeWorkRequestBuilder<ExportPhotosWorker>()
            .setInputData(
                ExportPhotosWorker.buildInputData(
                    account = account,
                    branchCode = branchCode,
                    photoIds = photoIds
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            "export_${account}_${branchCode}_${request.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id
    }

    private suspend fun savePhoto(
        branch: BranchEntity,
        routeAtCapture: Int?,
        note: String?,
        processor: suspend (mainFile: File, thumbFile: File) -> Unit
    ): PhotoEntity {
        val createdAt = System.currentTimeMillis()
        val photoId = UUID.randomUUID().toString()
        val monthKey = DateTimeUtils.monthKey(createdAt)
        val mainFile = fileStorageManager.createMainFile(
            account = branch.account,
            branchCode = branch.branchCode,
            monthKey = monthKey,
            photoId = photoId
        )
        val thumbFile = fileStorageManager.createThumbFile(
            account = branch.account,
            branchCode = branch.branchCode,
            monthKey = monthKey,
            photoId = photoId
        )

        try {
            processor(mainFile, thumbFile)
            val photo = PhotoEntity(
                id = photoId,
                account = branch.account,
                branchCode = branch.branchCode,
                shopName = branch.shopName,
                routeAtCapture = routeAtCapture,
                localMainPath = mainFile.absolutePath,
                localThumbPath = thumbFile.absolutePath,
                createdAt = createdAt,
                monthKey = monthKey,
                note = note?.trim().takeUnless { it.isNullOrBlank() },
                tag = null
            )
            photoDao.insert(photo)
            return photo
        } catch (error: Exception) {
            fileStorageManager.deleteFile(mainFile.absolutePath)
            fileStorageManager.deleteFile(thumbFile.absolutePath)
            throw error
        }
    }

    private fun resolveExportFolder(pattern: String, photo: PhotoEntity): String {
        return pattern
            .replace("{account}", sanitizeFolderName(photo.account))
            .replace("{branchCode}", sanitizeFolderName(photo.branchCode))
            .replace("{shopName}", sanitizeFolderName(photo.shopName))
            .ifBlank { "${sanitizeFolderName(photo.account)}_${sanitizeFolderName(photo.branchCode)}" }
    }

    private fun sanitizeFolderName(value: String): String {
        return value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
