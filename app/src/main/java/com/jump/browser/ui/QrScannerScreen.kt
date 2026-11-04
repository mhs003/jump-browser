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
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
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
    // Camera runs camera2 + TextureView inside a platform View (QrScannerView);
    // only this thin AndroidView wrapper remains in Compose.
    AndroidView(
        factory = { ctx ->
            QrScannerView(ctx, onBack, onResult)
        },
        update = { },
    )
}

/**
 * Owns the camera device, the preview surface and the YUV reader. Everything
 * runs on a single background thread and is torn down in [close].
 */
internal class QrPreview(
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

    // QR only: a direct reader keeps R8 from retaining the DataMatrix, Aztec,
    // PDF417 and one-dimensional decoders that MultiFormatReader would drag in.
    private val decoder = com.google.zxing.qrcode.QRCodeReader()
    private val hints = java.util.Collections.singletonMap(DecodeHintType.TRY_HARDER, true)

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
        val result = runCatching { decoder.decode(bitmap, hints) }.getOrNull() ?: return
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

internal fun pasteLink(context: Context, onResult: (String) -> Unit) {
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
