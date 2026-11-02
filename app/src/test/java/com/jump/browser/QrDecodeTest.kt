package com.jump.browser

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the luminance path the camera scanner feeds into ZXing.
 *
 * The scanner decodes from the camera's Y plane rather than a bitmap, so this
 * renders a QR into a luma buffer (with row padding, as camera frames have) and
 * checks it decodes. That is the part most likely to break when the camera
 * plumbing changes.
 */
class QrDecodeTest {

    private fun qrMatrix(text: String, size: Int): BitMatrix =
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)

    private fun decoder() = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    @Test
    fun decodesPackedLumaPlane() {
        val text = "https://example.com/packed"
        val size = 240
        val matrix = qrMatrix(text, size)
        val data = ByteArray(size * size) { index ->
            val x = index % size
            val y = index / size
            if (matrix.get(x, y)) 0 else 255.toByte()
        }
        val source = PlanarYUVLuminanceSource(data, size, size, 0, 0, size, size, false)
        val result = decoder().decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(text, result.text)
    }

    @Test
    fun decodesPaddedLumaPlane() {
        // Camera Y planes are commonly padded to a row stride larger than the
        // image width; the scanner copies row by row, and this mirrors that.
        val text = "https://example.com/padded"
        val size = 200
        val rowStride = size + 24
        val matrix = qrMatrix(text, size)
        val plane = ByteArray(rowStride * size) { 0x80.toByte() }
        for (y in 0 until size) {
            for (x in 0 until size) {
                plane[y * rowStride + x] = if (matrix.get(x, y)) 0 else 255.toByte()
            }
        }
        // Same copy the scanner performs.
        val data = ByteArray(size * size)
        var offset = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                data[offset++] = plane[y * rowStride + x]
            }
        }
        val source = PlanarYUVLuminanceSource(data, size, size, 0, 0, size, size, false)
        val result = decoder().decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(text, result.text)
    }
}
