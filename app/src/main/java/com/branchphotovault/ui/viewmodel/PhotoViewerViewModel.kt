package com.branchphotovault.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.branchphotovault.data.local.PhotoEntity
import com.branchphotovault.data.repository.PhotoRepository
import com.branchphotovault.integration.GhostShiftSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoViewerUiState(
    val photos: List<PhotoEntity> = emptyList(),
    val initialIndex: Int = 0,
    val isSavingToGallery: Boolean = false,
    val isDeleting: Boolean = false,
    val message: String? = null
)

class PhotoViewerViewModel(
    private val photoRepository: PhotoRepository,
    account: String,
    branchCode: String,
    initialPhotoId: String
) : ViewModel() {

    private val isSavingToGallery = MutableStateFlow(false)
    private val isDeleting = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val photosFlow = photoRepository.observePhotosForBranch(account, branchCode)

    val uiState: StateFlow<PhotoViewerUiState> = combine(
        photosFlow,
        isSavingToGallery,
        isDeleting,
        message
    ) { photos, savingToGallery, deleting, userMessage ->
            PhotoViewerUiState(
                photos = photos,
                initialIndex = photos.indexOfFirst { it.id == initialPhotoId }.takeIf { it >= 0 } ?: 0,
                isSavingToGallery = savingToGallery,
                isDeleting = deleting,
                message = userMessage
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PhotoViewerUiState()
        )

    fun savePhotoToGallery(photoId: String) {
        viewModelScope.launch {
            isSavingToGallery.value = true
            try {
                val summary = photoRepository.exportPhotos(
                    photoIds = listOf(photoId),
                    account = null,
                    branchCode = null
                ) { _, _ -> }
                message.value = if (summary.exportedCount > 0) {
                    "Photo saved to gallery."
                } else {
                    "Unable to save photo to gallery."
                }
            } catch (error: Exception) {
                message.value = error.message ?: "Unable to save photo to gallery."
            } finally {
                isSavingToGallery.value = false
            }
        }
    }

    fun sendPhotoToGhostShift(context: android.content.Context, photo: PhotoEntity) {
        val sent = GhostShiftSender.send(context, listOf(photo))
        message.value = if (sent) {
            "Photo sent to GhostShift."
        } else {
            "GhostShift is not installed or the image is unavailable."
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            isDeleting.value = true
            try {
                val removed = photoRepository.deletePhotos(listOf(photoId))
                message.value = if (removed > 0) {
                    "Photo deleted."
                } else {
                    "Photo not found."
                }
            } catch (error: Exception) {
                message.value = error.message ?: "Delete failed."
            } finally {
                isDeleting.value = false
            }
        }
    }

    fun clearMessage() {
        message.update { null }
    }

    companion object {
        fun provideFactory(
            photoRepository: PhotoRepository,
            account: String,
            branchCode: String,
            initialPhotoId: String
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PhotoViewerViewModel(
                        photoRepository = photoRepository,
                        account = account,
                        branchCode = branchCode,
                        initialPhotoId = initialPhotoId
                    ) as T
                }
            }
        }
    }
}
