package com.jump.browser

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scanner reads QR only, through a direct [QRCodeReader] rather than
 * MultiFormatReader, which keeps the other barcode readers out of the dex.
 * This round-trips a code through encode → luminance frame → decode to prove
 * that path still works without a camera.
 */
class QrDecodeTest {

    /** Renders [text] as a QR and feeds it to the reader the way the camera does. */
    private fun decode(text: String): String {
        // Quiet zone of four modules, as the spec requires a frame to have.
        val quiet = 4
        val scale = 8
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to quiet),
        )
        // Re-render at a camera-like density: QRCodeWriter honours the
        // requested size, so multiply the natural size by [scale].
        val natural = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            matrix.width * scale,
            matrix.height * scale,
            mapOf(EncodeHintType.MARGIN to quiet),
        )
        val w = natural.width
        val h = natural.height
        // A Y plane of luminance bytes: 255 white, 0 black.
        val data = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                data[y * w + x] = (if (natural[x, y]) 0 else 255).toByte()
            }
        }
        val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
        val hints = java.util.Collections.singletonMap(DecodeHintType.TRY_HARDER, true as Any)
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        return result.text
    }

    @Test
    fun decodesAnUrl() {
        assertEquals("https://jump.example.org/scan", decode("https://jump.example.org/scan"))
    }

    @Test
    fun decodesPlainText() {
        assertEquals("Hello, Jump!", decode("Hello, Jump!"))
    }

    @Test
    fun decodesAShortString() {
        assertEquals("x", decode("x"))
    }
}
