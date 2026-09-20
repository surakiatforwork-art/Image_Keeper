package com.branchphotovault.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.branchphotovault.LocalAppContainer
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CapturePhotoScreen(
    account: String,
    branchCode: String,
    onBack: () -> Unit,
    onCaptured: (tempPath: String, mirrorHorizontally: Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = LocalAppContainer.current
    val hostView = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var captureLocked by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val preview = remember {
        Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera permission is required.")
            }
        }
    }

    DisposableEffect(hostView) {
        val window = context.findActivity()?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, hostView)
            window.statusBarColor = AndroidColor.BLACK
            window.navigationBarColor = AndroidColor.BLACK
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
        onDispose { }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        // Wait for the persisted value instead of using a temporary front-camera default.
        lensFacing = container.settingsRepository.getLastCameraLensFacing()
    }

    LaunchedEffect(lensFacing, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProvider = context.getCameraProvider()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
    }

    LaunchedEffect(lensFacing, previewViewRef) {
        previewViewRef?.applyFrontCameraMirror(lensFacing == CameraSelector.LENS_FACING_FRONT)
    }

    Surface(color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { previewContext ->
                        PreviewView(previewContext).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            preview.setSurfaceProvider(surfaceProvider)
                            imageCapture.targetRotation = display?.rotation ?: Surface.ROTATION_0
                            previewViewRef = this
                        }
                    },
                    update = { previewView ->
                        imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                        previewViewRef = previewView
                    }
                )

                CaptureFrameOverlay()

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close camera",
                        tint = Color.White
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                    CameraSelector.LENS_FACING_BACK
                                } else {
                                    CameraSelector.LENS_FACING_FRONT
                                }
                                coroutineScope.launch {
                                    container.settingsRepository.setLastCameraLensFacing(lensFacing)
                                }
                            },
                            enabled = !captureLocked
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = "Switch camera",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                if (captureLocked) return@Button
                                captureLocked = true
                                val tempFile = container.photoRepository.createTempCaptureFile(account, branchCode)
                                imageCapture.targetRotation = previewViewRef?.display?.rotation
                                    ?: Surface.ROTATION_0
                                imageCapture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(tempFile).build(),
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(
                                            outputFileResults: ImageCapture.OutputFileResults
                                        ) {
                                            onCaptured(
                                                tempFile.absolutePath,
                                                lensFacing == CameraSelector.LENS_FACING_FRONT
                                            )
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            captureLocked = false
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    exception.message ?: "Failed to capture photo."
                                                )
                                            }
                                        }
                                    }
                                )
                            },
                            enabled = !captureLocked,
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Transparent
                            )
                        ) { }
                    }

                    Box(modifier = Modifier.weight(1f))
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun CaptureFrameOverlay() {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val targetRatio = 3f / 4f
        val frameWidth: androidx.compose.ui.unit.Dp
        val frameHeight: androidx.compose.ui.unit.Dp

        if (maxWidth / maxHeight > targetRatio) {
            frameHeight = maxHeight * 0.85f
            frameWidth = frameHeight * targetRatio
        } else {
            frameWidth = maxWidth * 0.9f
            frameHeight = frameWidth / targetRatio
        }

        Box(
            modifier = Modifier
                .size(frameWidth, frameHeight)
                .border(2.dp, Color.White.copy(alpha = 0.7f))
        )
        Canvas(modifier = Modifier.size(frameWidth, frameHeight)) {
            val cornerLength = 30.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val cornerColor = Color.White

            drawLine(cornerColor, Offset.Zero, Offset(cornerLength, 0f), strokeWidth)
            drawLine(cornerColor, Offset.Zero, Offset(0f, cornerLength), strokeWidth)
            drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width - cornerLength, 0f), strokeWidth)
            drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width, cornerLength), strokeWidth)
            drawLine(cornerColor, Offset(0f, size.height), Offset(cornerLength, size.height), strokeWidth)
            drawLine(cornerColor, Offset(0f, size.height), Offset(0f, size.height - cornerLength), strokeWidth)
            drawLine(
                cornerColor,
                Offset(size.width, size.height),
                Offset(size.width - cornerLength, size.height),
                strokeWidth
            )
            drawLine(
                cornerColor,
                Offset(size.width, size.height),
                Offset(size.width, size.height - cornerLength),
                strokeWidth
            )
        }
    }
}

private fun PreviewView.applyFrontCameraMirror(isFrontCamera: Boolean) {
    post {
        findTextureView(this)?.scaleX = if (isFrontCamera) -1f else 1f
    }
}

private fun findTextureView(view: View): TextureView? {
    if (view is TextureView) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findTextureView(view.getChildAt(index))?.let { return it }
        }
    }
    return null
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener(
        { continuation.resume(cameraProviderFuture.get()) },
        ContextCompat.getMainExecutor(this)
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
