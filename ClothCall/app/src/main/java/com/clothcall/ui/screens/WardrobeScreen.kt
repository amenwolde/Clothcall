package com.clothcall.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.camera.core.CameraSelector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.clothcall.data.db.Garment
import com.clothcall.ui.viewmodels.WardrobeViewModel
import java.io.File
import java.util.Locale

private enum class WardrobeStep { LIST, CAMERA, NAME }

@Composable
fun WardrobeScreen(navController: NavController, viewModel: WardrobeViewModel) {
    val garments by viewModel.garments.collectAsState()
    var step by remember { mutableStateOf(WardrobeStep.LIST) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    when (step) {
        WardrobeStep.LIST -> GarmentList(
            garments = garments,
            onAdd = { step = WardrobeStep.CAMERA },
            onDelete = { viewModel.deleteGarment(it) },
            onBack = { navController.popBackStack() }
        )
        WardrobeStep.CAMERA -> WardrobeCameraScreen(
            onCapture = { bitmap -> capturedBitmap = bitmap; step = WardrobeStep.NAME },
            onBack = { step = WardrobeStep.LIST }
        )
        WardrobeStep.NAME -> capturedBitmap?.let { bmp ->
            NameGarmentScreen(
                bitmap = bmp,
                onSave = { name ->
                    viewModel.addGarment(navController.context, name, bmp)
                    step = WardrobeStep.LIST
                },
                onBack = { step = WardrobeStep.CAMERA }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarmentList(
    garments: List<Garment>,
    onAdd: () -> Unit,
    onDelete: (Garment) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wardrobe") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add garment") }
                }
            )
        }
    ) { padding ->
        if (garments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Checkroom, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Your wardrobe is empty.\nTap + to add a garment.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(garments, key = { it.id }) { garment ->
                    GarmentCard(garment = garment, onDelete = { onDelete(garment) })
                }
            }
        }
    }
}

@Composable
private fun GarmentCard(garment: Garment, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val file = File(garment.imagePath)
            if (file.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(file),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Checkroom, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(garment.name, style = MaterialTheme.typography.bodyLarge)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WardrobeCameraScreen(onCapture: (Bitmap) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

    Box(modifier = Modifier.fillMaxSize()) {
        key(lensFacing) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    val selector = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                        CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                        } catch (e: Exception) { Log.e("Wardrobe", "Camera error", e) }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White)
        }

        IconButton(
            onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                    CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.FlipCameraAndroid, "Flip camera",
                tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(32.dp))
        }

        FloatingActionButton(
            onClick = {
                imageCapture.takePicture(ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            onCapture(imageProxyToBitmap(image))
                            image.close()
                        }
                        override fun onError(e: ImageCaptureException) { Log.e("Wardrobe", "Capture error", e) }
                    })
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).size(72.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Camera, "Capture", modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun NameGarmentScreen(bitmap: Bitmap, onSave: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val stt = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    DisposableEffect(Unit) { onDispose { stt.destroy() } }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Filled.ArrowBack, "Back")
        }

        Text("Name this garment", style = MaterialTheme.typography.headlineMedium)

        Image(
            painter = rememberAsyncImagePainter(bitmap),
            contentDescription = null,
            modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Garment name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    isListening = true
                    startSttForName(stt, context) { result ->
                        if (result.isNotBlank()) name = result
                        isListening = false
                    }
                }) {
                    Icon(
                        if (isListening) Icons.Filled.Mic else Icons.Filled.MicNone,
                        contentDescription = "Speak name"
                    )
                }
            }
        )

        Button(
            onClick = { if (name.isNotBlank()) onSave(name.trim()) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Save to wardrobe")
        }
    }
}

private fun startSttForName(stt: SpeechRecognizer, context: android.content.Context, onResult: (String) -> Unit) {
    stt.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(r: Bundle?) {}
        override fun onEvent(e: Int, p: Bundle?) {}
        override fun onError(e: Int) { onResult("") }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            onResult(matches?.firstOrNull() ?: "")
        }
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    stt.startListening(intent)
}
