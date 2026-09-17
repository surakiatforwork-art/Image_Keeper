package com.branchphotovault.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.branchphotovault.data.local.BranchEntity
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.data.model.ExportProgress
import com.branchphotovault.data.repository.BranchRepository
import com.branchphotovault.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BranchPreviewUiState(
    val branch: BranchEntity? = null,
    val photos: List<PhotoEntity> = emptyList(),
    val selectedPhotoIds: Set<String> = emptySet(),
    val isProcessing: Boolean = false,
    val exportProgress: ExportProgress? = null,
    val message: String? = null
)

class BranchPreviewViewModel(
    private val branchRepository: BranchRepository,
    private val photoRepository: PhotoRepository,
    @Suppress("unused") workManagerContext: Context,
    private val account: String,
    private val branchCode: String
) : ViewModel() {

    private data class ContentState(
        val branch: BranchEntity?,
        val photos: List<PhotoEntity>,
        val selectedPhotoIds: Set<String>
    )

    private data class StatusState(
        val isProcessing: Boolean,
        val exportProgress: ExportProgress?,
        val message: String?
    )

    private val selectedPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    private val isProcessing = MutableStateFlow(false)
    private val exportProgress = MutableStateFlow<ExportProgress?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val branchFlow = branchRepository.observeBranch(account, branchCode)
    private val photosFlow = photoRepository.observePhotosForBranch(account, branchCode)

    private val contentFlow = combine(
        branchFlow,
        photosFlow,
        selectedPhotoIds
    ) { branch, photos, selectedIds ->
        val validIds = selectedIds.filterTo(mutableSetOf()) { selectedId ->
            photos.any { it.id == selectedId }
        }
        ContentState(
            branch = branch,
            photos = photos,
            selectedPhotoIds = validIds
        )
    }

    private val statusFlow = combine(
        isProcessing,
        exportProgress,
        message
    ) { processing, exportProgress, userMessage ->
        StatusState(
            isProcessing = processing,
            exportProgress = exportProgress,
            message = userMessage
        )
    }

    val uiState: StateFlow<BranchPreviewUiState> = combine(
        contentFlow,
        statusFlow
    ) { content, status ->
        BranchPreviewUiState(
            branch = content.branch,
            photos = content.photos,
            selectedPhotoIds = content.selectedPhotoIds,
            isProcessing = status.isProcessing,
            exportProgress = status.exportProgress,
            message = status.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BranchPreviewUiState()
    )

    fun toggleSelection(photoId: String) {
        selectedPhotoIds.update { selected ->
            if (photoId in selected) selected - photoId else selected + photoId
        }
    }

    fun clearSelection() {
        selectedPhotoIds.value = emptySet()
    }

    fun importPhoto(sourceUri: Uri, routeAtCapture: Int?, note: String?) {
        val branch = uiState.value.branch ?: return

        viewModelScope.launch {
            isProcessing.value = true
            try {
                photoRepository.importPhoto(
                    branch = branch,
                    sourceUri = sourceUri,
                    routeAtCapture = routeAtCapture,
                    note = note
                )
                message.value = "Photo imported."
            } catch (error: Exception) {
                message.value = error.message ?: "Import failed."
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun saveCapturedPhoto(tempFilePath: String, routeAtCapture: Int?, note: String?) {
        val branch = uiState.value.branch ?: return

        viewModelScope.launch {
            isProcessing.value = true
            try {
                photoRepository.saveCapturedPhoto(
                    branch = branch,
                    tempFilePath = tempFilePath,
                    routeAtCapture = routeAtCapture,
                    note = note
                )
                message.value = "Photo saved."
            } catch (error: Exception) {
                message.value = error.message ?: "Capture save failed."
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun updateRoute(newRoute: Int?) {
        val branch = uiState.value.branch ?: return

        viewModelScope.launch {
            isProcessing.value = true
            try {
                branchRepository.updateRoute(branch = branch, newRoute = newRoute)
                message.value = "Route updated."
            } catch (error: Exception) {
                message.value = error.message ?: "Route update failed."
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun saveSelectedToGallery() {
        val selectedIds = uiState.value.selectedPhotoIds
        if (selectedIds.isEmpty()) return

        exportToGallery(
            photos = uiState.value.photos.filter { it.id in selectedIds }
        )
    }

    fun saveAllToGallery() {
        val photos = uiState.value.photos
        if (photos.isEmpty()) return

        exportToGallery(
            photos = photos
        )
    }

    fun deleteSelected() {
        val selected = uiState.value.selectedPhotoIds.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            isProcessing.value = true
            try {
                val removed = photoRepository.deletePhotos(selected)
                selectedPhotoIds.value = emptySet()
                message.value = "Deleted $removed photo(s)."
            } catch (error: Exception) {
                message.value = error.message ?: "Delete failed."
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }

    private fun exportToGallery(photos: List<PhotoEntity>) {
        if (photos.isEmpty()) {
            message.value = "No photos selected."
            return
        }

        viewModelScope.launch {
            isProcessing.value = true
            exportProgress.value = ExportProgress(
                state = WorkInfo.State.RUNNING,
                exported = 0,
                total = photos.size,
                message = "Saving to gallery..."
            )
            try {
                val summary = photoRepository.exportPhotoEntities(
                    photos = photos
                ) { exported, total ->
                    exportProgress.value = ExportProgress(
                        state = WorkInfo.State.RUNNING,
                        exported = exported,
                        total = total,
                        message = "Saving to gallery..."
                    )
                }
                val total = summary.exportedCount + summary.failedCount
                exportProgress.value = ExportProgress(
                    state = if (summary.failedCount == 0) {
                        WorkInfo.State.SUCCEEDED
                    } else {
                        WorkInfo.State.FAILED
                    },
                    exported = summary.exportedCount,
                    total = total,
                    message = "${summary.exportedCount} saved, ${summary.failedCount} failed"
                )
                message.value = if (summary.failedCount == 0) {
                    "${summary.exportedCount} photo(s) saved to gallery."
                } else {
                    "${summary.exportedCount} saved, ${summary.failedCount} failed."
                }
            } catch (error: Exception) {
                exportProgress.value = ExportProgress(
                    state = WorkInfo.State.FAILED,
                    exported = 0,
                    total = photos.size,
                    message = error.message ?: "Save to gallery failed."
                )
                message.value = error.message ?: "Save to gallery failed."
            } finally {
                isProcessing.value = false
            }
        }
    }

    companion object {
        fun provideFactory(
            branchRepository: BranchRepository,
            photoRepository: PhotoRepository,
            workManagerContext: Context,
            account: String,
            branchCode: String
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BranchPreviewViewModel(
                        branchRepository = branchRepository,
                        photoRepository = photoRepository,
                        workManagerContext = workManagerContext,
                        account = account,
                        branchCode = branchCode
                    ) as T
                }
            }
        }
    }
}
