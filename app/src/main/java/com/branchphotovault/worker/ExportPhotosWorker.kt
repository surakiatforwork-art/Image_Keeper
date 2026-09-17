package com.branchphotovault.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.branchphotovault.data.model.ExportSummary
import com.branchphotovault.data.repository.PhotoRepository

class ExportPhotosWorker(
    appContext: Context,
    params: WorkerParameters,
    private val photoRepository: PhotoRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val account = inputData.getString(KEY_ACCOUNT)
        val branchCode = inputData.getString(KEY_BRANCH_CODE)
        val photoIds = inputData.getStringArray(KEY_PHOTO_IDS)?.toList()

        return try {
            val summary = photoRepository.exportPhotos(
                photoIds = photoIds,
                account = account,
                branchCode = branchCode
            ) { exported, total ->
                setProgress(
                    workDataOf(
                        KEY_EXPORTED_COUNT to exported,
                        KEY_TOTAL_COUNT to total
                    )
                )
            }
            Result.success(
                workDataOf(
                    KEY_EXPORTED_COUNT to summary.exportedCount,
                    KEY_FAILED_COUNT to summary.failedCount,
                    KEY_TOTAL_COUNT to summary.exportedCount + summary.failedCount
                )
            )
        } catch (error: Exception) {
            Result.failure(
                workDataOf(
                    KEY_MESSAGE to (error.message ?: "Export failed.")
                )
            )
        }
    }

    companion object {
        const val KEY_ACCOUNT = "account"
        const val KEY_BRANCH_CODE = "branch_code"
        const val KEY_PHOTO_IDS = "photo_ids"
        const val KEY_EXPORTED_COUNT = "exported_count"
        const val KEY_FAILED_COUNT = "failed_count"
        const val KEY_TOTAL_COUNT = "total_count"
        const val KEY_MESSAGE = "message"

        fun buildInputData(
            account: String,
            branchCode: String,
            photoIds: List<String>?
        ): Data {
            return workDataOf(
                KEY_ACCOUNT to account,
                KEY_BRANCH_CODE to branchCode,
                KEY_PHOTO_IDS to photoIds?.toTypedArray()
            )
        }
    }
}

