package com.example.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.domain.model.ModelRuntimeState
import com.example.domain.model.PcbImageSource
import com.example.domain.model.SamplePcbPreset
import com.example.inference.demo.SamplePcbCatalog
import com.example.presentation.components.BrandHeader
import com.example.presentation.components.DemonstrationBadge
import com.example.presentation.components.RuntimeStatusDialog
import com.example.presentation.viewmodel.InspectUiState
import com.example.ui.theme.LocalPcbColors
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun InspectScreen(
    uiState: InspectUiState,
    allocateCameraCaptureDestination: suspend () -> Pair<String, Uri>,
    onSetLiveCameraActive: (Boolean) -> Unit,
    onCameraImageCaptured: (String, Boolean) -> Unit,
    onCameraCaptureError: (String) -> Unit,
    onGalleryImagePicked: (Uri?) -> Unit,
    onSamplePresetSelected: (SamplePcbPreset) -> Unit,
    onBoardNameChanged: (String) -> Unit,
    onClearSelectedImage: () -> Unit,
    onDismissError: () -> Unit,
    onStartInspectionClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalPcbColors.current
    val coroutineScope = rememberCoroutineScope()
    var showRuntimeDialog by remember { mutableStateOf(false) }
    var permissionDeniedBanner by remember { mutableStateOf(false) }
    var pendingSystemCameraFileUri by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = uiState.isCameraLiveActive) {
        onSetLiveCameraActive(false)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onGalleryImagePicked(uri)
    }

    val systemCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val fileUri = pendingSystemCameraFileUri
        pendingSystemCameraFileUri = null
        if (fileUri != null) {
            onCameraImageCaptured(fileUri, success)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedBanner = false
            onSetLiveCameraActive(true)
        } else {
            permissionDeniedBanner = true
        }
    }

    fun handleCapturePcbClick() {
        val hasCameraPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPerm) {
            permissionDeniedBanner = false
            onSetLiveCameraActive(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showRuntimeDialog) {
        RuntimeStatusDialog(
            detectionModelInfo = uiState.detectionModelInfo,
            advisoryModelInfo = uiState.detectionModelInfo,
            onOpenSettings = onOpenSettingsClick,
            onDismiss = { showRuntimeDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("inspect_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            BrandHeader(
                onRuntimeStatusClick = { showRuntimeDialog = true },
                hasRuntimeAlert = uiState.detectionModelInfo.state != ModelRuntimeState.READY
            )
            Text(
                text = "New Inspection",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = "Acquire & validate PCB optical frame",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }

        if (permissionDeniedBanner) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.reviewBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Camera permission denied",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.reviewFg
                        )
                        Text(
                            text = "You can still upload a PCB image from Photo Picker, load a sample board below, or retry camera permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        if (uiState.errorMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.rejectBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspect_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.rejectFg
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.rejectFg,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissError) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss error",
                                tint = colors.rejectFg
                            )
                        }
                    }
                }
            }
        }

        item {
            PcbPreviewViewport(
                uiState = uiState,
                allocateCameraCaptureDestination = allocateCameraCaptureDestination,
                onCameraImageCaptured = onCameraImageCaptured,
                onCameraCaptureError = onCameraCaptureError,
                onCancelLiveCamera = { onSetLiveCameraActive(false) },
                onUseSystemCameraFallback = {
                    coroutineScope.launch {
                        val (fileUri, contentUri) = allocateCameraCaptureDestination()
                        pendingSystemCameraFileUri = fileUri
                        systemCameraLauncher.launch(contentUri)
                    }
                },
                onClearSelectedImage = onClearSelectedImage
            )
        }

        if (uiState.selectedImageUri != null && !uiState.isCameraLiveActive) {
            item {
                OutlinedTextField(
                    value = uiState.boardName,
                    onValueChange = onBoardNameChanged,
                    label = { Text("Board Identifier / Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspect_board_name_input"),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        if (uiState.selectedSourceType != null &&
            uiState.selectedSourceType != PcbImageSource.SAMPLE &&
            uiState.detectionModelInfo.state != ModelRuntimeState.READY
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.incompleteBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspect_production_model_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.incompleteFg
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "YOLOv8n ONNX detector is ${uiState.detectionModelInfo.state.name}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Real camera/gallery images never fall back to demo inference. Running now will record INCOMPLETE until you import a .onnx model in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        TextButton(onClick = onOpenSettingsClick) {
                            Text("Settings")
                        }
                    }
                }
            }
        }

        // Capture PCB | Upload PCB inline action bar
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clickable(enabled = !uiState.isRunningInspection) {
                                handleCapturePcbClick()
                            }
                            .testTag("inspect_capture_pcb_button")
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = "Capture PCB",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Capture PCB",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(colors.divider)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clickable(enabled = !uiState.isRunningInspection) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("inspect_upload_pcb_button")
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = "Upload PCB",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upload PCB",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        // Primary Start Inspection CTA (strictly gated)
        item {
            Button(
                onClick = onStartInspectionClick,
                enabled = uiState.canStartInspection,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("inspect_start_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.anchorSurface,
                    contentColor = colors.onAnchorSurface,
                    disabledContainerColor = colors.surfaceSubtle,
                    disabledContentColor = colors.textMuted
                )
            ) {
                if (uiState.isRunningInspection || uiState.isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.onAnchorSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.progressMessage ?: "Validating image...",
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.CenterFocusWeak,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Inspection",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Prototype Sample Boards (Quick Load)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Prototype Sample Boards (Quick Load)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        uiState.samplePresets.forEachIndexed { index, preset ->
                            SamplePresetRow(
                                preset = preset,
                                enabled = !uiState.isRunningInspection,
                                onClick = { onSamplePresetSelected(preset) }
                            )
                            if (index < uiState.samplePresets.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = colors.divider
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PcbPreviewViewport(
    uiState: InspectUiState,
    allocateCameraCaptureDestination: suspend () -> Pair<String, Uri>,
    onCameraImageCaptured: (String, Boolean) -> Unit,
    onCameraCaptureError: (String) -> Unit,
    onCancelLiveCamera: () -> Unit,
    onUseSystemCameraFallback: () -> Unit,
    onClearSelectedImage: () -> Unit
) {
    val colors = LocalPcbColors.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inspect_preview_viewport")
    ) {
        when {
            uiState.isCameraLiveActive -> {
                CameraXLiveCapturePane(
                    allocateDestination = allocateCameraCaptureDestination,
                    onCaptured = onCameraImageCaptured,
                    onCameraError = onCameraCaptureError,
                    onCancel = onCancelLiveCamera,
                    onUseSystemCameraFallback = onUseSystemCameraFallback
                )
            }

            uiState.selectedImageUri != null -> {
                val valResult = uiState.validation
                val ratio = if (valResult != null && valResult.width > 0 && valResult.height > 0) {
                    (valResult.width.toFloat() / valResult.height.toFloat()).coerceIn(0.65f, 1.85f)
                } else {
                    4f / 3f
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = Uri.parse(uiState.selectedImageUri),
                            contentDescription = uiState.boardName.ifBlank { "Selected PCB preview" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        IconButton(
                            onClick = onClearSelectedImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(colors.canvas.copy(alpha = 0.85f), RoundedCornerShape(50))
                                .testTag("inspect_clear_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear selected PCB image",
                                tint = colors.textPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dims = if (valResult != null && valResult.width > 0) {
                            "${valResult.width} × ${valResult.height} px"
                        } else {
                            "Validating dimensions..."
                        }
                        Text(
                            text = dims,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (uiState.selectedSourceType == PcbImageSource.SAMPLE) {
                                DemonstrationBadge(label = "SAMPLE DEMO")
                            }
                            if (valResult != null && valResult.isProcessable) {
                                val (fg, bg, label) = if (valResult.lowClarityRisk) {
                                    Triple(colors.reviewFg, colors.reviewBg, "Low Clarity Risk")
                                } else {
                                    Triple(colors.passFg, colors.passBg, "Clarity OK")
                                }
                                Surface(shape = RoundedCornerShape(50), color = bg) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = fg,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 230.dp)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CenterFocusWeak,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No PCB image selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Capture a board photo, upload from gallery, or load a sample PCB below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraXLiveCapturePane(
    allocateDestination: suspend () -> Pair<String, Uri>,
    onCaptured: (String, Boolean) -> Unit,
    onCameraError: (String) -> Unit,
    onCancel: () -> Unit,
    onUseSystemCameraFallback: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalPcbColors.current
    val coroutineScope = rememberCoroutineScope()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                boundProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                imageCapture = capture
            } catch (e: Throwable) {
                Log.w("CameraXLive", "CameraX bind failed", e)
                bindError = e.message ?: "Live camera unavailable on this device."
            }
        }, executor)

        onDispose {
            runCatching { boundProvider?.unbindAll() }
        }
    }

    Column(modifier = Modifier.padding(12.dp)) {
        if (bindError != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Live CameraX preview unavailable",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Text(
                    text = bindError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel) {
                        Text("Close")
                    }
                    Button(onClick = onUseSystemCameraFallback) {
                        Text("System Camera")
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.canvas)
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val capture = imageCapture ?: return@Button
                        if (isCapturing) return@Button
                        isCapturing = true
                        coroutineScope.launch {
                            val (fileUriString, _) = allocateDestination()
                            val destFile = File(Uri.parse(fileUriString).path ?: "")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(destFile).build()
                            capture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        onCaptured(fileUriString, true)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        onCameraError("Capture failed: ${exception.message ?: "camera error"}")
                                        onCaptured(fileUriString, false)
                                    }
                                }
                            )
                        }
                    },
                    enabled = imageCapture != null && !isCapturing,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.anchorSurface,
                        contentColor = colors.onAnchorSurface
                    )
                ) {
                    Text(if (isCapturing) "Capturing..." else "Snap Frame")
                }
            }
        }
    }
}

@Composable
private fun SamplePresetRow(
    preset: SamplePcbPreset,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPcbColors.current
    val drawableId = remember(preset.drawableName) {
        SamplePcbCatalog.drawableResId(preset.drawableName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("sample_preset_${preset.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (drawableId != 0) {
            Image(
                painter = painterResource(id = drawableId),
                contentDescription = preset.boardName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceSubtle)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CenterFocusWeak,
                    contentDescription = null,
                    tint = colors.textMuted
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.boardName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preset.orientationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "Load sample",
            tint = colors.textSecondary
        )
    }
}
