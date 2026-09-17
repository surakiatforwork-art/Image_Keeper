package com.branchphotovault.data.model

import androidx.work.WorkInfo

data class ExportProgress(
    val state: WorkInfo.State = WorkInfo.State.ENQUEUED,
    val exported: Int = 0,
    val total: Int = 0,
    val message: String? = null
)

