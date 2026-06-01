package com.clothcall.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.clothcall.ui.navigation.Route
import com.clothcall.ui.viewmodels.ScanState
import com.clothcall.ui.viewmodels.ScanViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MorningSpinScreen(navController: NavController, viewModel: ScanViewModel) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val state by viewModel.state.collectAsState()
    var isSpinning by remember { mutableStateOf(false) }
    var frameCount by remember { mutableIntStateOf(0) }
    var capturedFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    // When spinning: capture a frame every 2 seconds
    LaunchedEffect(isSpinning) {
        if (isSpinning) {
            frameCount = 0
            capturedFrames = emptyList()
            while (isSpinning && frameCount < 3) {
                delay(2_000)
                if (isSpinning) {
                    imageCapture?.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                image.close()
                                capturedFrames = capturedFrames + rotated
                                frameCount++
                            }
                            override fun onError(e: ImageCaptureException) { Log.e("MorningSpin", "Frame error", e) }
                        }
                    )
                }
            }
            isSpinning = false
        }
    }

    // When spin stops with frames captured, analyze the sharpest (last) frame
    LaunchedEffect(isSpinning, capturedFrames) {
        if (!isSpinning && capturedFrames.isNotEmpty() && state is ScanState.Idle) {
            viewModel.analyze(capturedFrames.last())
        }
    }

    // Navigate on done
    LaunchedEffect(state) {
        if (state is ScanState.Done) {
            viewModel.resetState()
            navController.navigate(Route.CALL_UI) {
                popUpTo(Route.MORNING_SPIN) { inclusive = true }
            }
        }
    }

    when {
        state is ScanState.Loading -> LoadingOverlay()
        state is ScanState.Error -> ErrorOverlay(
            message = (state as ScanState.Error).message,
            onRetry = { viewModel.resetState(); capturedFrames = emptyList() },
            onBack = { navController.popBackStack() }
        )
        !cameraPermission.status.isGranted -> PermissionDeniedScreen { cameraPermission.launchPermissionRequest() }
        else -> {
            val lifecycleOwner = LocalLifecycleOwner.current
            val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
            val capture = remember {
                ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            }

            LaunchedEffect(capture) { imageCapture = capture }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> PreviewView(ctx).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } },
                    update = { previewView ->
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                            } catch (e: Exception) { Log.e("MorningSpin", "Camera error", e) }
                        }, ContextCompat.getMainExecutor(previewView.context))
                    }
                )

                // Back button
                IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }

                // Instructions
                Text(
                    text = if (isSpinning) "Scanning… slowly turn the garment\nFrame ${frameCount + 1} of 3" else "Tap to begin scanning.\nHold each side of the garment in view.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.45f), shape = MaterialTheme.shapes.medium)
                        .padding(12.dp)
                )

                // Spin / stop button
                FloatingActionButton(
                    onClick = { isSpinning = !isSpinning },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).size(80.dp),
                    containerColor = if (isSpinning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isSpinning) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = if (isSpinning) "Stop scan" else "Start scan",
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Frame dots
                if (isSpinning) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) { i ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        if (i < frameCount) Color.White else Color.White.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
