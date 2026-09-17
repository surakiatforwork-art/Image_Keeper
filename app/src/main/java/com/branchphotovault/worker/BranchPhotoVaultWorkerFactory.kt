package com.branchphotovault.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.branchphotovault.data.repository.PhotoRepository
import com.branchphotovault.data.repository.SettingsRepository

class BranchPhotoVaultWorkerFactory(
    private val photoRepository: PhotoRepository,
    @Suppress("unused")
    private val settingsRepository: SettingsRepository
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            ExportPhotosWorker::class.java.name -> ExportPhotosWorker(
                appContext = appContext,
                params = workerParameters,
                photoRepository = photoRepository
            )

            else -> null
        }
    }
}

