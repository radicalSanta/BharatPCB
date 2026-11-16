package com.example.imageprocessing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafeImageDecoderTest {

    @Test
    fun `orientationToDegrees maps exif constants accurately`() {
        assertEquals(0, SafeImageDecoder.orientationToDegrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(90, SafeImageDecoder.orientationToDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, SafeImageDecoder.orientationToDegrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, SafeImageDecoder.orientationToDegrees(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun `computeInSampleSize keeps decoded edges bounded`() {
        assertEquals(1, SafeImageDecoder.computeInSampleSize(1200, 900, 1600))
        assertEquals(2, SafeImageDecoder.computeInSampleSize(3200, 2400, 1600))
        assertEquals(4, SafeImageDecoder.computeInSampleSize(6400, 4800, 1600))
    }

    @Test
    fun `laplacian heuristic distinguishes flat blurry image from high contrast edge image`() {
        val flatBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(120, 120, 120))
        }
        val sharpBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                val color = if ((x / 8 + y / 8) % 2 == 0) Color.BLACK else Color.WHITE
                sharpBitmap.setPixel(x, y, color)
            }
        }

        val flatScore = SafeImageDecoder.computeLaplacianVarianceHeuristic(flatBitmap)
        val sharpScore = SafeImageDecoder.computeLaplacianVarianceHeuristic(sharpBitmap)

        assertTrue("Flat image should have near-zero variance, was $flatScore", flatScore < 1.0)
        assertTrue("Sharp image should have high variance, was $sharpScore", sharpScore > 500.0)
    }

    @Test
    fun `null stream produces unprocessable validation failure`() {
        val result = SafeImageDecoder.validateFromStreamProvider(
            minWidth = 256,
            minHeight = 256,
            openStream = { null }
        )
        assertFalse(result.isValid)
        assertFalse(result.isProcessable)
    }
}
