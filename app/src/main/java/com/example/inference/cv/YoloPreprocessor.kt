package com.example.inference.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.nio.FloatBuffer
import kotlin.math.min

data class LetterboxTransform(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val targetWidth: Int,
    val targetHeight: Int,
    val originalWidth: Int,
    val originalHeight: Int
) {
    /**
     * Maps bounding box coordinates from model input pixel space [0..targetWidth, 0..targetHeight]
     * back to normalized original image coordinates [0..1].
     */
    fun mapBoxToNormalizedOriginal(
        x1InputPx: Float,
        y1InputPx: Float,
        x2InputPx: Float,
        y2InputPx: Float
    ): NormalizedRect {
        val origX1 = ((x1InputPx - padX) / scale).coerceIn(0f, originalWidth.toFloat())
        val origY1 = ((y1InputPx - padY) / scale).coerceIn(0f, originalHeight.toFloat())
        val origX2 = ((x2InputPx - padX) / scale).coerceIn(0f, originalWidth.toFloat())
        val origY2 = ((y2InputPx - padY) / scale).coerceIn(0f, originalHeight.toFloat())

        val normX = (origX1 / originalWidth.toFloat()).coerceIn(0f, 1f)
        val normY = (origY1 / originalHeight.toFloat()).coerceIn(0f, 1f)
        val normW = ((origX2 - origX1) / originalWidth.toFloat()).coerceIn(0f, 1f - normX)
        val normH = ((origY2 - origY1) / originalHeight.toFloat()).coerceIn(0f, 1f - normY)

        return NormalizedRect(normX, normY, normW, normH)
    }
}

data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class YoloTensorInput(
    val floatBuffer: FloatBuffer,
    val shape: LongArray,
    val transform: LetterboxTransform
)

object YoloPreprocessor {
    const val PREPROCESSING_VERSION = "yolo-letterbox-rgb-114-v1"
    private const val PAD_VALUE = 114

    fun computeLetterboxTransform(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): LetterboxTransform {
        require(originalWidth > 0 && originalHeight > 0) { "Original image dimensions must be positive." }
        require(targetWidth > 0 && targetHeight > 0) { "Target model dimensions must be positive." }

        val scale = min(
            targetWidth.toFloat() / originalWidth.toFloat(),
            targetHeight.toFloat() / originalHeight.toFloat()
        )
        val scaledW = originalWidth * scale
        val scaledH = originalHeight * scale
        val padX = (targetWidth - scaledW) / 2f
        val padY = (targetHeight - scaledH) / 2f

        return LetterboxTransform(
            scale = scale,
            padX = padX,
            padY = padY,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            originalWidth = originalWidth,
            originalHeight = originalHeight
        )
    }

    fun prepareNchwFloatInput(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): YoloTensorInput {
        val transform = computeLetterboxTransform(
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )

        val letterboxed = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        letterboxed.eraseColor(Color.rgb(PAD_VALUE, PAD_VALUE, PAD_VALUE))
        val canvas = Canvas(letterboxed)

        val scaledW = (bitmap.width * transform.scale).toInt().coerceAtLeast(1)
        val scaledH = (bitmap.height * transform.scale).toInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        canvas.drawBitmap(scaledBitmap, transform.padX, transform.padY, Paint(Paint.FILTER_BITMAP_FLAG))

        val pixelCount = targetWidth * targetHeight
        val pixels = IntArray(pixelCount)
        letterboxed.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        val floatBuffer = FloatBuffer.allocate(3 * pixelCount)
        val channelSize = pixelCount
        for (i in 0 until pixelCount) {
            val px = pixels[i]
            val r = ((px shr 16) and 0xFF) / 255.0f
            val g = ((px shr 8) and 0xFF) / 255.0f
            val b = (px and 0xFF) / 255.0f
            floatBuffer.put(i, r)
            floatBuffer.put(channelSize + i, g)
            floatBuffer.put(2 * channelSize + i, b)
        }
        floatBuffer.rewind()

        return YoloTensorInput(
            floatBuffer = floatBuffer,
            shape = longArrayOf(1L, 3L, targetHeight.toLong(), targetWidth.toLong()),
            transform = transform
        )
    }
}
