package com.example.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ImageValidationResult
import com.example.domain.model.ModelInfo
import com.example.domain.model.PcbImageSource
import com.example.domain.model.SamplePcbPreset
import com.example.domain.repository.ImageStorageManager
import com.example.domain.repository.ModelManager
import com.example.domain.repository.SettingsRepository
import com.example.domain.usecase.RunInspectionUseCase
import com.example.inference.demo.SamplePcbCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InspectUiState(
    val selectedImageUri: String? = null,
    val selectedSourceType: PcbImageSource? = null,
    val boardName: String = "",
    val sampleScenarioHint: String? = null,
    val validation: ImageValidationResult? = null,
    val isCameraLiveActive: Boolean = false,
    val isValidating: Boolean = false,
    val isRunningInspection: Boolean = false,
    val progressMessage: String? = null,
    val errorMessage: String? = null,
    val detectionModelInfo: ModelInfo,
    val samplePresets: List<SamplePcbPreset> = SamplePcbCatalog.presets
) {
    val canStartInspection: Boolean
        get() = selectedImageUri != null &&
            validation?.isValid == true &&
            validation.isProcessable &&
            !isValidating &&
            !isRunningInspection &&
            !isCameraLiveActive
}

class InspectViewModel(
    private val imageStorageManager: ImageStorageManager,
    private val settingsRepository: SettingsRepository,
    private val runInspectionUseCase: RunInspectionUseCase,
    modelManager: ModelManager
) : ViewModel() {

    private val _localState = MutableStateFlow(
        InspectUiState(detectionModelInfo = modelManager.detectionModelInfo.value)
    )

    val uiState: StateFlow<InspectUiState> = combine(
        _localState,
        modelManager.detectionModelInfo
    ) { state, detInfo ->
        state.copy(detectionModelInfo = detInfo)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _localState.value
    )

    fun setLiveCameraActive(active: Boolean) {
        _localState.update {
            it.copy(
                isCameraLiveActive = active,
                errorMessage = null
            )
        }
    }

    suspend fun allocateCameraCaptureDestination(): Pair<String, Uri> =
        imageStorageManager.createCameraCaptureDestination()

    fun onCameraImageCaptured(fileUriString: String, success: Boolean) {
        viewModelScope.launch {
            if (!success) {
                imageStorageManager.deleteUnreferencedCaptureIfEmpty(fileUriString)
                _localState.update {
                    it.copy(
                        isCameraLiveActive = false,
                        errorMessage = "Camera capture cancelled or failed."
                    )
                }
                return@launch
            }
            validateAndSelectImage(
                imageUriString = fileUriString,
                sourceType = PcbImageSource.CAMERA,
                suggestedBoardName = "Captured PCB Board",
                scenarioHint = null
            )
        }
    }

    fun onCameraCaptureError(message: String) {
        _localState.update {
            it.copy(
                isCameraLiveActive = false,
                errorMessage = message
            )
        }
    }

    fun onGalleryImagePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _localState.update { it.copy(isValidating = true, errorMessage = null, isCameraLiveActive = false) }
            val importResult = imageStorageManager.importPickedImageToAppStorage(uri)
            importResult.onSuccess { storedUriString ->
                validateAndSelectImage(
                    imageUriString = storedUriString,
                    sourceType = PcbImageSource.GALLERY,
                    suggestedBoardName = "Uploaded PCB Board",
                    scenarioHint = null
                )
            }.onFailure { err ->
                _localState.update {
                    it.copy(
                        isValidating = false,
                        errorMessage = "Could not import selected photo: ${err.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun onSamplePresetSelected(preset: SamplePcbPreset) {
        viewModelScope.launch {
            _localState.update { it.copy(isValidating = true, errorMessage = null, isCameraLiveActive = false) }
            val res = imageStorageManager.materializeSamplePreset(preset)
            res.onSuccess { uriString ->
                validateAndSelectImage(
                    imageUriString = uriString,
                    sourceType = PcbImageSource.SAMPLE,
                    suggestedBoardName = preset.boardName,
                    scenarioHint = preset.scenarioHint,
                    forceBoardName = true
                )
            }.onFailure { err ->
                _localState.update {
                    it.copy(
                        isValidating = false,
                        errorMessage = "Failed to load sample PCB: ${err.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun onBoardNameChanged(name: String) {
        _localState.update { it.copy(boardName = name) }
    }

    fun clearSelectedImage() {
        _localState.update {
            it.copy(
                selectedImageUri = null,
                selectedSourceType = null,
                validation = null,
                sampleScenarioHint = null,
                errorMessage = null
            )
        }
    }

    fun dismissError() {
        _localState.update { it.copy(errorMessage = null) }
    }

    fun startInspection(onCompleted: (String) -> Unit) {
        val current = _localState.value
        val uri = current.selectedImageUri ?: return
        val source = current.selectedSourceType ?: return
        if (!current.canStartInspection) return

        viewModelScope.launch {
            _localState.update {
                it.copy(
                    isRunningInspection = true,
                    progressMessage = "Preparing inspection pipeline",
                    errorMessage = null
                )
            }
            try {
                val record = runInspectionUseCase.execute(
                    imageUriString = uri,
                    sourceType = source,
                    boardName = current.boardName.ifBlank {
                        if (source == PcbImageSource.SAMPLE) "Sample PCB" else "PCB Assembly"
                    },
                    sampleScenarioHint = current.sampleScenarioHint,
                    onStageProgress = { stage ->
                        _localState.update { it.copy(progressMessage = stage) }
                    }
                )
                _localState.update {
                    it.copy(
                        isRunningInspection = false,
                        progressMessage = null
                    )
                }
                onCompleted(record.inspectionId)
            } catch (e: Throwable) {
                _localState.update {
                    it.copy(
                        isRunningInspection = false,
                        progressMessage = null,
                        errorMessage = "Inspection failed: ${e.message ?: "unexpected error"}"
                    )
                }
            }
        }
    }

    private suspend fun validateAndSelectImage(
        imageUriString: String,
        sourceType: PcbImageSource,
        suggestedBoardName: String,
        scenarioHint: String?,
        forceBoardName: Boolean = false
    ) {
        _localState.update { it.copy(isValidating = true, isCameraLiveActive = false, errorMessage = null) }
        val policy = settingsRepository.policyConfig.first()
        val validation = imageStorageManager.validateAndAssessClarity(
            imageUriString = imageUriString,
            minWidth = policy.minImageWidth,
            minHeight = policy.minImageHeight
        )
        _localState.update { prev ->
            val resolvedName = when {
                forceBoardName -> suggestedBoardName
                prev.boardName.isBlank() -> suggestedBoardName
                else -> prev.boardName
            }
            prev.copy(
                selectedImageUri = imageUriString,
                selectedSourceType = sourceType,
                boardName = resolvedName,
                sampleScenarioHint = scenarioHint,
                validation = validation,
                isValidating = false,
                errorMessage = if (!validation.isProcessable || !validation.isValid) {
                    validation.errorMessage ?: "Selected image failed validation."
                } else {
                    null
                }
            )
        }
    }

    class Factory(
        private val imageStorageManager: ImageStorageManager,
        private val settingsRepository: SettingsRepository,
        private val runInspectionUseCase: RunInspectionUseCase,
        private val modelManager: ModelManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InspectViewModel(
                imageStorageManager = imageStorageManager,
                settingsRepository = settingsRepository,
                runInspectionUseCase = runInspectionUseCase,
                modelManager = modelManager
            ) as T
        }
    }
}
