package com.branchphotovault.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.branchphotovault.data.repository.BranchRepository
import com.branchphotovault.data.repository.PhotoRepository
import com.branchphotovault.data.repository.SettingsRepository
import com.branchphotovault.data.model.ImageAspectRatioOption
import com.branchphotovault.data.model.ImageSizeOption
import com.branchphotovault.util.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = AppConfig.DEFAULT_APPS_SCRIPT_URL,
    val retentionMonths: Int = AppConfig.DEFAULT_RETENTION_MONTHS,
    val exportFolderPattern: String = AppConfig.DEFAULT_EXPORT_FOLDER_PATTERN,
    val imageSizeOption: ImageSizeOption = ImageSizeOption.LARGE,
    val imageAspectRatioOption: ImageAspectRatioOption = ImageAspectRatioOption.ORIGINAL,
    val isSaving: Boolean = false,
    val isCheckingApi: Boolean = false,
    val isCleaningUp: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val photoRepository: PhotoRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private data class DraftState(
        val baseUrl: String,
        val retentionMonths: Int,
        val exportFolderPattern: String,
        val imageSizeOption: ImageSizeOption,
        val imageAspectRatioOption: ImageAspectRatioOption
    )

    private data class ActionState(
        val isSaving: Boolean,
        val isCheckingApi: Boolean,
        val isCleaningUp: Boolean,
        val message: String?
    )

    private val draftBaseUrl = MutableStateFlow(AppConfig.DEFAULT_APPS_SCRIPT_URL)
    private val draftRetentionMonths = MutableStateFlow(AppConfig.DEFAULT_RETENTION_MONTHS)
    private val draftExportFolderPattern = MutableStateFlow(AppConfig.DEFAULT_EXPORT_FOLDER_PATTERN)
    private val draftImageSizeOption = MutableStateFlow(ImageSizeOption.LARGE)
    private val draftImageAspectRatioOption = MutableStateFlow(ImageAspectRatioOption.ORIGINAL)
    private val isSaving = MutableStateFlow(false)
    private val isCheckingApi = MutableStateFlow(false)
    private val isCleaningUp = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            settingsRepository.baseUrlFlow.collect { draftBaseUrl.value = it }
        }
        viewModelScope.launch {
            settingsRepository.retentionMonthsFlow.collect { draftRetentionMonths.value = it }
        }
        viewModelScope.launch {
            settingsRepository.exportFolderPatternFlow.collect { draftExportFolderPattern.value = it }
        }
        viewModelScope.launch {
            settingsRepository.imageLongEdgeFlow.collect {
                draftImageSizeOption.value = ImageSizeOption.fromLongEdge(it)
            }
        }
        viewModelScope.launch {
            settingsRepository.imageAspectRatioKeyFlow.collect {
                draftImageAspectRatioOption.value = ImageAspectRatioOption.fromKey(it)
            }
        }
    }

    private val draftStateFlow = combine(
        draftBaseUrl,
        draftRetentionMonths,
        draftExportFolderPattern,
        draftImageSizeOption,
        draftImageAspectRatioOption
    ) { baseUrl, retentionMonths, exportPattern, imageSizeOption, imageAspectRatioOption ->
        DraftState(
            baseUrl = baseUrl,
            retentionMonths = retentionMonths,
            exportFolderPattern = exportPattern,
            imageSizeOption = imageSizeOption,
            imageAspectRatioOption = imageAspectRatioOption
        )
    }

    private val actionStateFlow = combine(
        isSaving,
        isCheckingApi,
        isCleaningUp,
        message
    ) { saving, checkingApi, cleaningUp, userMessage ->
        ActionState(
            isSaving = saving,
            isCheckingApi = checkingApi,
            isCleaningUp = cleaningUp,
            message = userMessage
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        draftStateFlow,
        actionStateFlow
    ) { draft, action ->
        SettingsUiState(
            baseUrl = draft.baseUrl,
            retentionMonths = draft.retentionMonths,
            exportFolderPattern = draft.exportFolderPattern,
            imageSizeOption = draft.imageSizeOption,
            imageAspectRatioOption = draft.imageAspectRatioOption,
            isSaving = action.isSaving,
            isCheckingApi = action.isCheckingApi,
            isCleaningUp = action.isCleaningUp,
            message = action.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun updateBaseUrl(value: String) {
        draftBaseUrl.value = value
    }

    fun updateRetentionMonths(value: String) {
        draftRetentionMonths.value = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
    }

    fun updateExportPattern(value: String) {
        draftExportFolderPattern.value = value
    }

    fun updateImageSizeOption(value: ImageSizeOption) {
        draftImageSizeOption.value = value
    }

    fun updateImageAspectRatioOption(value: ImageAspectRatioOption) {
        draftImageAspectRatioOption.value = value
    }

    fun saveSettings() {
        viewModelScope.launch {
            isSaving.value = true
            try {
                settingsRepository.setBaseUrl(draftBaseUrl.value)
                settingsRepository.setRetentionMonths(draftRetentionMonths.value)
                settingsRepository.setExportFolderPattern(draftExportFolderPattern.value)
                settingsRepository.setImageLongEdge(draftImageSizeOption.value.longEdge)
                settingsRepository.setImageAspectRatioKey(draftImageAspectRatioOption.value.key)
                message.value = "Settings saved."
            } catch (error: Exception) {
                message.value = error.message ?: "Unable to save settings."
            } finally {
                isSaving.value = false
            }
        }
    }

    fun checkApi() {
        viewModelScope.launch {
            isCheckingApi.value = true
            try {
                branchRepository.healthCheck()
                message.value = "Health check passed."
            } catch (error: Exception) {
                message.value = error.message ?: "Health check failed."
            } finally {
                isCheckingApi.value = false
            }
        }
    }

    fun cleanupExpiredPhotos() {
        viewModelScope.launch {
            isCleaningUp.value = true
            try {
                val removed = photoRepository.cleanupExpiredPhotos(draftRetentionMonths.value)
                message.value = "Removed $removed old photo(s)."
            } catch (error: Exception) {
                message.value = error.message ?: "Cleanup failed."
            } finally {
                isCleaningUp.value = false
            }
        }
    }

    fun clearMessage() {
        message.update { null }
    }

    companion object {
        fun provideFactory(
            settingsRepository: SettingsRepository,
            photoRepository: PhotoRepository,
            branchRepository: BranchRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        settingsRepository = settingsRepository,
                        photoRepository = photoRepository,
                        branchRepository = branchRepository
                    ) as T
                }
            }
        }
    }
}
