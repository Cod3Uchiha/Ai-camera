package com.cod3uchiha.aicamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        setContent { CameraScreen() }
    }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    val owner = context as LifecycleOwner
    val scope = rememberCoroutineScope()
    val executor = remember { Executors.newSingleThreadExecutor() }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf("Import Qualcomm ESRGAN model to enable AI enhancement") }
    var busy by remember { mutableStateOf(false) }
    var modelPath by remember { mutableStateOf<File?>(null) }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            status = "Importing model…"
            try {
                val target = File(context.filesDir, "esrgan.tflite")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Unable to read selected model")
                }
                modelPath = target
                status = "ESRGAN model imported"
            } catch (e: Exception) {
                status = "Model import failed: ${e.message}"
            } finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Color(0xFF080A0E), surface = Color(0xFF121722),
        primary = Color(0xFFB6F36B), onPrimary = Color(0xFF10150A),
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(Modifier.fillMaxSize().background(Color(0xFF080A0E)).padding(16.dp)) {
            Text("COD3UCHIHA", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB6F36B))
            Text("AI CAMERA", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (hasPermission) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val provider = future.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .build()
                                imageCapture = capture
                                try {
                                    provider.unbindAll()
                                    activeCamera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                    activeCamera?.cameraInfo?.zoomState?.observe(owner) { state ->
                                        maxZoom = state.maxZoomRatio.coerceAtLeast(1f)
                                        zoom = zoom.coerceIn(1f, maxZoom)
                                    }
                                } catch (e: Exception) {
                                    status = "Camera could not start: ${e.message}"
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )
                } else {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Zoom ${"%.1f".format(zoom)}×", color = Color.White)
            Slider(
                value = zoom.coerceIn(1f, maxZoom.coerceAtLeast(1f)),
                onValueChange = { requested ->
                    zoom = requested
                    activeCamera?.cameraControl?.setZoomRatio(requested.coerceIn(1f, maxZoom))
                },
                valueRange = 1f..maxZoom.coerceAtLeast(1.01f)
            )
            Text("Maximum reported by camera: ${"%.1f".format(maxZoom)}×. Actual zoom depends on device camera support.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = !busy && imageCapture != null,
                    onClick = {
                        val capture = imageCapture ?: return@Button
                        busy = true
                        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                scope.launch {
                                    try {
                                        val original = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
                                            ?: error("Could not decode captured photo")
                                        val model = modelPath
                                        val result = if (model != null && model.exists()) {
                                            status = "Enhancing photo on device…"
                                            withContext(Dispatchers.Default) {
                                                val engine = LiteSrEngine(model)
                                                try { engine.enhance(original) } finally { engine.close() }
                                            }
                                        } else original
                                        val saved = withContext(Dispatchers.IO) { saveToGallery(result) }
                                        status = if (saved != null && model != null) "Enhanced image saved to Gallery"
                                            else if (saved != null) "Photo saved. Import ESRGAN to enable AI enhancement."
                                            else "Could not save photo"
                                        if (result !== original) original.recycle()
                                    } catch (e: Exception) {
                                        status = "Capture/enhancement failed: ${e.message}"
                                    } finally {
                                        busy = false
                                        file.delete()
                                    }
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                status = "Capture failed: ${exception.message}"
                                busy = false
                            }
                        })
                    }
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "PROCESSING…" else "CAPTURE")
                }
                OutlinedButton(enabled = !busy, onClick = { modelPicker.launch("*/*") }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("MODEL")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB6F36B))
        }
    }
}

private fun saveToGallery(bitmap: Bitmap): Uri? {
    val resolver = AppContextHolder.context?.contentResolver ?: return null
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Cod3uchiha_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Cod3uchiha AI Camera")
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it) }
    return uri
}

private object AppContextHolder {
    var context: android.content.Context? = null
}
