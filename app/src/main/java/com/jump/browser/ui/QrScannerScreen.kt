package com.jump.browser.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    val handled = remember { AtomicBoolean(false) }
    val decoder = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                )
            )
        }
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            analyzerExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                update = { previewView ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                        if (cameraProvider === provider) return@addListener
                        cameraProvider = provider
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(android.util.Size(1280, 720))
                            .build()
                        analysis.setAnalyzer(analyzerExecutor) { image ->
                            analyzeQr(image, decoder, handled) { value ->
                                mainExecutor.execute {
                                    if (handled.compareAndSet(false, true)) {
                                        onResult(value)
                                    }
                                }
                            }
                        }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    }, mainExecutor)
                },
            )
            QrFrameOverlay()
        } else {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(JumpIcons.QR, null, tint = Color.White, modifier = Modifier.size(58.dp))
                Spacer(Modifier.height(18.dp))
                Text("Camera access is needed to scan QR codes", color = Color.White, fontSize = 17.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { pasteLink(context, onResult) }) {
                    Text("Paste a link instead", color = Color(0xFF9AAEEF))
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .background(Color.Black.copy(alpha = 0.35f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeButton(
                icon = JumpIcons.Back,
                contentDescription = "Back",
                tint = Color.White,
                onClick = onBack,
            )
            Text("Scan QR code", color = Color.White, fontSize = 19.sp)
        }

        if (granted) {
            TextButton(
                onClick = { pasteLink(context, onResult) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp),
            ) {
                Text("Paste a link instead", color = Color(0xFFCCD6FF), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun QrFrameOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(240.dp)) {
            val stroke = 3.dp.toPx()
            val len = 40.dp.toPx()
            val w = size.width
            val h = size.height
            val c = Color(0xFFB9C8FF)
            drawLine(c, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(len, 0f), stroke)
            drawLine(c, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, len), stroke)
            drawLine(c, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w - len, 0f), stroke)
            drawLine(c, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, len), stroke)
            drawLine(c, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(len, h), stroke)
            drawLine(c, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(0f, h - len), stroke)
            drawLine(c, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w - len, h), stroke)
            drawLine(c, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w, h - len), stroke)
        }
    }
}

private fun analyzeQr(
    image: ImageProxy,
    decoder: MultiFormatReader,
    handled: AtomicBoolean,
    onDecoded: (String) -> Unit,
) {
    if (handled.get()) {
        image.close()
        return
    }
    try {
        val yPlane = image.planes[0]
        val width = image.width
        val height = image.height
        val buffer = yPlane.buffer
        buffer.rewind()
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val data = ByteArray(width * height)
        if (pixelStride == 1 && rowStride == width) {
            buffer.get(data, 0, minOf(buffer.remaining(), data.size))
        } else {
            val row = ByteArray(rowStride)
            var offset = 0
            for (y in 0 until height) {
                val toRead = minOf(rowStride, buffer.remaining())
                if (toRead <= 0) break
                buffer.get(row, 0, toRead)
                var x = 0
                while (x < width && x * pixelStride < toRead) {
                    data[offset++] = row[x * pixelStride]
                    x++
                }
            }
        }
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = runCatching { decoder.decodeWithState(bitmap) }.getOrNull()
        if (result != null && result.text.isNotBlank()) onDecoded(result.text)
    } finally {
        runCatching { decoder.reset() }
        image.close()
    }
}

private fun pasteLink(context: Context, onResult: (String) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
    if (!text.isNullOrBlank()) onResult(text)
}
