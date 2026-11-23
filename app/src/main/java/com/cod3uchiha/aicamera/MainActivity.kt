package com.cod3uchiha.aicamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var flashEnabled by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparing on-device AI model…") }
    var busy by remember { mutableStateOf(false) }
    var modelPath by remember { mutableStateOf<File?>(null) }
    var modelProgress by remember { mutableFloatStateOf(0f) }
    var downloadAttempt by remember { mutableIntStateOf(0) }
    var gridEnabled by remember { mutableStateOf(false) }
    var lastPhoto by remember { mutableStateOf<Uri?>(null) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(downloadAttempt) {
        if (modelPath == null) {
            status = "Downloading Qualcomm AI model…"
            try {
                val downloaded = ModelDownloader.ensureModel(context) { received, total ->
                    modelProgress = (received.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    status = "Downloading AI model… ${(modelProgress * 100).toInt()}%"
                }
                modelPath = downloaded
                modelProgress = 1f
                status = "AI enhancement ready • processed on this device"
            } catch (error: Exception) {
                status = "Model download failed: ${error.message ?: "check your connection"}"
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Color(0xFF08090B), surface = Color(0xFF15181D),
        primary = Color(0xFFC7F36B), onPrimary = Color(0xFF11150A),
        onBackground = Color.White, onSurface = Color.White
    )) {
        Box(Modifier.fillMaxSize().background(Color(0xFF08090B))) {
            if (hasPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()
                            imageCapture = capture
                            try {
                                provider.unbindAll()
                                activeCamera = provider.bindToLifecycle(
                                    owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
                                )
                                activeCamera?.cameraInfo?.zoomState?.observe(owner) { state ->
                                    minZoom = state.minZoomRatio.coerceAtLeast(1f)
                                    maxZoom = state.maxZoomRatio.coerceAtLeast(minZoom)
                                    zoom = zoom.coerceIn(minZoom, maxZoom)
                                }
                            } catch (e: Exception) {
                                status = "Camera error: ${e.message}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        view
                    }
                )
                if (gridEnabled) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                        repeat(2) { Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .35f))) }
                    }
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(2) { Spacer(Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = .35f))) }
                    }
                }
            } else {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, Modifier.align(Alignment.Center)) {
                    Text("Allow camera access")
                }
            }

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("COD3UCHIHA", color = Color(0xFFC7F36B), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("CAMERA", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TopControl(Icons.Default.GridOn, "Grid", gridEnabled) { gridEnabled = !gridEnabled }
                    TopControl(
                        if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        "Torch", flashEnabled
                    ) {
                        flashEnabled = !flashEnabled
                        activeCamera?.cameraControl?.enableTorch(flashEnabled)
                    }
                    TopControl(Icons.Default.HdrOn, "AI enhancement", modelPath != null) {
                        status = "AI enhancement runs automatically after capture"
                    }
                }
            }

            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 66.dp)
                    .clip(CircleShape).background(Color(0xB814171B)).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (modelPath != null) Color(0xFFC7F36B) else Color(0xFFFFC857)))
                Spacer(Modifier.width(7.dp))
                Text(
                    if (modelPath != null) "AI ENHANCE ON" else "SETTING UP AI • ${(modelProgress * 100).toInt()}%",
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp
                )
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE608090B), Color(0xFF08090B))))
                    .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 58.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.6f, 1f, 3f, 10f).filter { it >= minZoom - .01f && it <= maxZoom + .01f }.forEach { stop ->
                        val selected = kotlin.math.abs(zoom - stop) < .12f
                        Box(
                            Modifier.padding(horizontal = 7.dp).size(if (selected) 42.dp else 36.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color(0xFFC7F36B) else Color(0xB814171B))
                                .clickable {
                                    zoom = stop.coerceIn(minZoom, maxZoom)
                                    activeCamera?.cameraControl?.setZoomRatio(zoom)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (stop < 1f) "0.6" else stop.toInt().toString(),
                                color = if (selected) Color(0xFF08090B) else Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Slider(
                    value = zoom.coerceIn(minZoom, maxZoom.coerceAtLeast(minZoom + .01f)),
                    onValueChange = {
                        zoom = it
                        activeCamera?.cameraControl?.setZoomRatio(it.coerceIn(minZoom, maxZoom))
                    },
                    valueRange = minZoom..maxZoom.coerceAtLeast(minZoom + .01f),
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFC7F36B), activeTrackColor = Color(0xFFC7F36B))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("PHOTO", color = Color(0xFFC7F36B), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(
                        "${String.format(Locale.US, "%.1f", zoom)}× / ${String.format(Locale.US, "%.0f", maxZoom)}×",
                        color = Color.White.copy(alpha = .75f), fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xB814171B))
                            .border(1.dp, Color.White.copy(alpha = .3f), RoundedCornerShape(12.dp))
                            .clickable {
                                lastPhoto?.let {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).setDataAndType(it, "image/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("LAST", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(78.dp).clip(CircleShape).background(Color.White)
                            .border(5.dp, Color.White.copy(alpha = .7f), CircleShape)
                            .padding(5.dp).clip(CircleShape).background(Color.White)
                            .clickable(enabled = !busy && imageCapture != null) {
                                val capture = imageCapture ?: return@clickable
                                val model = modelPath
                                busy = true
                                val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                                capture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(file).build(),
                                    executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            scope.launch {
                                                var original: Bitmap? = null
                                                var enhanced: Bitmap? = null
                                                try {
                                                    original = withContext(Dispatchers.IO) {
                                                        BitmapFactory.decodeFile(file.absolutePath)
                                                    } ?: error("Could not decode photo")
                                                    val result = if (model != null) {
                                                        status = "Enhancing photo on device…"
                                                        enhanced = withContext(Dispatchers.Default) {
                                                            val engine = LiteSrEngine(model)
                                                            try { engine.enhance(original!!) } finally { engine.close() }
                                                        }
                                                        enhanced!!
                                                    } else original!!
                                                    val saved = withContext(Dispatchers.IO) {
                                                        saveToGallery(context, result)
                                                    }
                                                    lastPhoto = saved
                                                    status = if (model != null) "AI-enhanced photo saved"
                                                        else "Photo saved • AI model is downloading"
                                                } catch (e: Exception) {
                                                    status = "Processing failed: ${e.message}"
                                                } finally {
                                                    original?.recycle()
                                                    enhanced?.recycle()
                                                    busy = false
                                                    file.delete()
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            scope.launch { status = "Capture failed: ${exception.message}"; busy = false }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(30.dp), color = Color(0xFF08090B), strokeWidth = 3.dp)
                        else Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White))
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Text("AI", color = Color(0xFFC7F36B), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(status, color = Color.White.copy(alpha = .8f), fontSize = 10.sp, maxLines = 1)
                if (modelPath == null && status.startsWith("Model download failed")) {
                    TextButton(onClick = { downloadAttempt++ }) {
                        Text("RETRY MODEL DOWNLOAD", color = Color(0xFFC7F36B))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopControl(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(42.dp).clip(CircleShape)
            .background(if (active) Color(0xFFC7F36B).copy(alpha = .18f) else Color(0xB814171B))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) Color(0xFFC7F36B) else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun saveToGallery(context: android.content.Context, bitmap: Bitmap): Uri {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Cod3uchiha_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Cod3uchiha AI Camera")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Could not create gallery image")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) { "Could not encode photo" }
        } ?: error("Could not open gallery destination")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    } catch (error: Exception) {
        resolver.delete(uri, null, null)
        throw error
    }
}
