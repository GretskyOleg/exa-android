package dev.exa.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner. Decodes `vless://`, subscription URLs and plain text.
 * ZXing runs fully on-device: no Google Play Services, no telemetry, no network.
 */
@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraQrPreview(
                onDecoded = onResult,
                onFailure = { failure = it }
            )
            ScannerOverlay()
        } else {
            PermissionPrompt(
                asked = asked,
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { openAppSettings(context) }
            )
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "QR // IMPORT",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 10.sp,
                letterSpacing = 1.8.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (failure != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(20.dp)
                    .background(ExaSurface.copy(alpha = 0.94f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(failure!!, color = ExaRed, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    asked: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Нужен доступ к камере",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = ExaText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "EXA читает QR-код локально на устройстве через ZXing. " +
                "Изображение не отправляется в сеть.",
            fontSize = 12.sp,
            color = ExaMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRequest) { Text("Разрешить камеру") }
        if (asked) {
            TextButton(onClick = onOpenSettings) {
                Text("Открыть настройки приложения")
            }
        }
    }
}

@Composable
private fun ScannerOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(250.dp)
                .border(2.dp, ExaRed.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
        )
        Column(
            Modifier.fillMaxSize().padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                "Наведи камеру на QR-код профиля",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun CameraQrPreview(
    onDecoded: (String) -> Unit,
    onFailure: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { AtomicBoolean(false) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
    }
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(true) }
    val cameraProvider = remember { ProcessCameraProvider.getInstance(context) }
    var analysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { analysis?.clearAnalyzer() }
            runCatching { cameraProvider.get().unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                cameraProvider.addListener({
                    runCatching {
                        val provider = cameraProvider.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                            if (handled.get()) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            val value = runCatching { decodeQr(proxy, reader) }.getOrNull()
                            proxy.close()
                            if (value != null && handled.compareAndSet(false, true)) {
                                mainExecutor.execute { onDecoded(value) }
                            }
                        }
                        analysis = imageAnalysis
                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        torchAvailable = runCatching { camera.cameraInfo.hasFlashUnit() }
                            .getOrDefault(false)
                        cameraRef = camera
                        if (!torchAvailable) torchOn = false
                    }.onFailure {
                        mainExecutor.execute {
                            onFailure("Камера недоступна: ${it.message ?: "ошибка"}")
                        }
                    }
                }, mainExecutor)
                previewView
            }
        )

        IconButton(
            onClick = {
                torchOn = !torchOn
                runCatching { cameraRef?.cameraControl?.enableTorch(torchOn) }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 8.dp)
        ) {
            if (torchAvailable) {
                Icon(
                    if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = "Вспышка",
                    tint = if (torchOn) ExaRed else Color.White
                )
            }
        }
    }
}

/**
 * Decodes a QR from the luminance plane. ZXing's detector handles rotation,
 * mirroring and perspective, so no bitmap conversion is needed.
 */
private fun decodeQr(proxy: ImageProxy, reader: MultiFormatReader): String? {
    val plane = proxy.planes.firstOrNull() ?: return null
    val width = proxy.width
    val height = proxy.height
    if (width <= 0 || height <= 0) return null
    val rowStride = plane.rowStride
    if (rowStride < width) return null
    val buffer = plane.buffer
    buffer.rewind()

    val data = ByteArray(width * height)
    if (rowStride == width) {
        val available = minOf(buffer.remaining(), data.size)
        buffer.get(data, 0, available)
    } else {
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            val offset = y * rowStride
            if (offset + width > buffer.limit()) break
            buffer.position(offset)
            val length = minOf(width, buffer.remaining())
            buffer.get(row, 0, length)
            System.arraycopy(row, 0, data, y * width, length)
        }
    }

    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        reader.decodeWithState(bitmap).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Throwable) {
        null
    } finally {
        reader.reset()
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
