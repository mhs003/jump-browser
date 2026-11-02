package com.jump.browser.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.jump.browser.ui.theme.Button
import com.jump.browser.ui.theme.Icon
import com.jump.browser.ui.theme.Text
import com.jump.browser.ui.theme.TextButton
import java.util.concurrent.atomic.AtomicBoolean

/**
 * QR scanning on the platform camera.
 *
 * This used to run on CameraX, which pulled four AARs plus a native image
 * processing library into the APK. Camera2 with an `ImageReader` does the same
 * job with code that is already part of the platform, so the release shrinks
 * by those dependencies and their `.so` files.
 */
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
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
    // Created during composition, not in a DisposableEffect: the effect runs
    // after the first composition, which is exactly when AndroidView already
    // called its factory, so the surface callback would have had nothing to
    // attach to and the preview stayed black.
    val preview = remember(granted) {
        if (!granted) {
            null
        } else {
            QrPreview(context) { value ->
                if (handled.compareAndSet(false, true)) onResult(value)
            }
        }
    }

    DisposableEffect(preview) {
        onDispose { preview?.close() }
    }

    // No opaque background on this Box: a SurfaceView is composited behind the
    // window, so any Compose background painted here would cover the preview.
    Box(Modifier.fillMaxSize()) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    // TextureView rather than SurfaceView: it renders inside
                    // the normal view tree, so it composes with the Compose
                    // overlay instead of being a separate layer the window
                    // background can cover.
                    android.view.TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        // The surface only exists once the view is attached, so
                        // start there rather than in the factory.
                        surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                t: android.graphics.SurfaceTexture,
                                w: Int,
                                h: Int,
                            ) {
                                preview?.attach(t)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                t: android.graphics.SurfaceTexture,
                                w: Int,
                                h: Int,
                            ) = Unit

                            override fun onSurfaceTextureDestroyed(t: android.graphics.SurfaceTexture): Boolean {
                                preview?.closeCamera()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(t: android.graphics.SurfaceTexture) = Unit
                        }
                    }
                },
                update = { view ->
                    // `factory` runs once. When the permission arrives later the
                    // view already exists, so attach the newly created preview
                    // if the surface is already there.
                    val current = preview
                    if (current != null && view.tag !== current) {
                        view.tag = current
                        view.surfaceTexture?.let(current::attach)
                    }
                },
            )
            QrFrameOverlay()
        } else {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(ReferenceIcons.QrScan, null, tint = Color.White, modifier = Modifier.size(58.dp))
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
                icon = ReferenceIcons.Back,
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

/**
 * Owns the camera device, the preview surface and the YUV reader. Everything
 * runs on a single background thread and is torn down in [close].
 */
private class QrPreview(
    private val context: Context,
    private val onDecoded: (String) -> Unit,
) {
    private val thread = HandlerThread("qr-scan").apply { start() }
    private val handler = Handler(thread.looper)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var request: CaptureRequest? = null
    private var closed = false
    private var lastTextureSize: android.util.Size? = null

    private val decoder = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    fun attach(texture: android.graphics.SurfaceTexture?) {
        if (texture == null) return
        val size = lastTextureSize ?: android.util.Size(0, 0)
        texture.setDefaultBufferSize(
            if (size.width > 0) size.width else 1280,
            if (size.height > 0) size.height else 720,
        )
        open(Surface(texture))
    }

    private fun open(output: Surface) {
        if (closed) return
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: return

        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        var width = 1280
        var height = 720
        map?.getOutputSizes(ImageFormat.YUV_420_888)?.let { sizes ->
            val best = sizes.filter { it.width <= 1920 }
                .maxByOrNull { it.width * it.height } ?: sizes.first()
            width = best.width
            height = best.height
        }
        lastTextureSize = android.util.Size(width, height)
        val analysis = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
        reader = analysis
        analysis.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                decode(image)
            } catch (_: Throwable) {
                // A malformed frame is expected while the camera warms up.
            } finally {
                image.close()
            }
        }, handler)

        @Suppress("MissingPermission")
        runCatching {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (closed) {
                        camera.close()
                        return
                    }
                    device = camera
                    runCatching { startSession(camera, output, analysis.surface) }
                }

                override fun onDisconnected(camera: CameraDevice) = camera.close()

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, handler)
        }
    }

    private fun startSession(camera: CameraDevice, preview: Surface, analysis: Surface) {
        val surfaces = listOf(preview, analysis)
        camera.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (closed) {
                        s.close()
                        return
                    }
                    session = s
                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        surfaces.forEach { addTarget(it) }
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                    request = req.build()
                    runCatching { s.setRepeatingRequest(req.build(), null, handler) }
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    s.close()
                }
            },
            handler,
        )
    }

    /**
     * Luma plane only: ZXing works on luminance, so chroma is ignored. The
     * plane is copied row by row because the buffer may be padded.
     */
    private fun decode(image: android.media.Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
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
        val result = runCatching { decoder.decodeWithState(bitmap) }.getOrNull() ?: return
        runCatching { decoder.reset() }
        result.text?.takeIf { it.isNotBlank() }?.let(onDecoded)
    }

    /** Stops the camera but keeps the object usable if the surface returns. */
    fun closeCamera() {
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        session = null
        device = null
        reader = null
    }

    fun close() {
        closed = true
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        session = null
        device = null
        reader = null
        thread.quitSafely()
    }
}

private fun pasteLink(context: Context, onResult: (String) -> Unit) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    if (text.isNotBlank()) onResult(text)
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
