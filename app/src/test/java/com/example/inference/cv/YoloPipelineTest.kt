package com.example.inference.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.DefectSeverity
import com.example.domain.model.DetectionErrorType
import com.example.domain.model.DetectionOutcome
import com.example.domain.model.ImageValidationResult
import com.example.domain.model.ModelRuntimeState
import com.example.domain.model.PcbImageInput
import com.example.domain.model.PcbImageSource
import com.example.domain.policy.DefectTaxonomy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YoloPipelineTest {

    @Test
    fun `letterbox transform reverse maps model space coordinates accurately to original normalized space`() {
        // 1200x900 landscape into 640x640 target -> scale = 640/1200 = 0.533333, scaledH = 480, padY = 80
        val transform = YoloPreprocessor.computeLetterboxTransform(
            originalWidth = 1200,
            originalHeight = 900,
            targetWidth = 640,
            targetHeight = 640
        )
        assertEquals(0f, transform.padX, 0.01f)
        assertEquals(80f, transform.padY, 0.01f)

        // Box spanning full scaled image: x=[0..640], y=[80..560] -> original normalized [0, 0, 1, 1]
        val mapped = transform.mapBoxToNormalizedOriginal(0f, 80f, 640f, 560f)
        assertEquals(0f, mapped.x, 0.002f)
        assertEquals(0f, mapped.y, 0.002f)
        assertEquals(1f, mapped.width, 0.002f)
        assertEquals(1f, mapped.height, 0.002f)

        // Center quarter box: x=[160..480], y=[200..440] -> normalized [0.25, 0.25, 0.50, 0.50]
        val quarter = transform.mapBoxToNormalizedOriginal(160f, 200f, 480f, 440f)
        assertEquals(0.25f, quarter.x, 0.005f)
        assertEquals(0.25f, quarter.y, 0.005f)
        assertEquals(0.50f, quarter.width, 0.005f)
        assertEquals(0.50f, quarter.height, 0.005f)
    }

    @Test
    fun `prepareNchwFloatInput pads with 114 normalized gray and preserves NCHW shape`() {
        val bmp = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(255, 0, 0))
        }
        val tensor = YoloPreprocessor.prepareNchwFloatInput(bmp, targetWidth = 100, targetHeight = 100)
        assertEquals(listOf(1L, 3L, 100L, 100L), tensor.shape.toList())
        // Row 0 is top letterbox padding (padY = 25px) -> R = 114/255
        val expectedPad = 114f / 255f
        assertEquals(expectedPad, tensor.floatBuffer.get(0), 0.01f)
        // Row 50 (center of red image) -> R = 1f, G = 0f, B = 0f
        val centerIdx = 50 * 100 + 50
        assertEquals(1f, tensor.floatBuffer.get(centerIdx), 0.01f)
        assertEquals(0f, tensor.floatBuffer.get(100 * 100 + centerIdx), 0.01f)
    }

    @Test
    fun `class wise NMS suppresses overlapping same class while preserving distinct classes`() {
        val a1 = RawDetectionCandidate(classIndex = 1, confidence = 0.90f, x1Px = 100f, y1Px = 100f, x2Px = 200f, y2Px = 200f)
        val a2 = RawDetectionCandidate(classIndex = 1, confidence = 0.70f, x1Px = 105f, y1Px = 105f, x2Px = 205f, y2Px = 205f)
        val b1 = RawDetectionCandidate(classIndex = 3, confidence = 0.80f, x1Px = 105f, y1Px = 105f, x2Px = 205f, y2Px = 205f)

        val kept = NonMaxSuppression.applyClassWiseNms(listOf(a1, a2, b1), iouThreshold = 0.45f)
        assertEquals(2, kept.size)
        assertEquals(0.90f, kept[0].confidence, 0.001f)
        assertEquals(3, kept[1].classIndex)
    }

    @Test
    fun `decoder parses standard raw YOLOv8 1x15x2 layout and maps normalized boxes`() {
        val labels = DefectTaxonomy.expectedDatasetLabelsByIndex // 11 classes -> 4 + 11 = 15 channels
        val numBoxes = 2
        val data = FloatArray(15 * numBoxes)
        // Box 0: cx=320, cy=320, w=128, h=128 -> class 1 ("short") score=0.88
        data[0 * numBoxes + 0] = 320f
        data[1 * numBoxes + 0] = 320f
        data[2 * numBoxes + 0] = 128f
        data[3 * numBoxes + 0] = 128f
        data[(4 + 1) * numBoxes + 0] = 0.88f

        // Box 1: below threshold (0.10)
        data[0 * numBoxes + 1] = 100f
        data[1 * numBoxes + 1] = 100f
        data[2 * numBoxes + 1] = 50f
        data[3 * numBoxes + 1] = 50f
        data[(4 + 0) * numBoxes + 1] = 0.10f

        val transform = YoloPreprocessor.computeLetterboxTransform(640, 640, 640, 640)
        val result = YoloOutputDecoder.decode(
            flatData = data,
            shape = longArrayOf(1L, 15L, 2L),
            classLabels = labels,
            transform = transform
        )

        assertTrue(result is YoloDecodeResult.Success)
        val detections = (result as YoloDecodeResult.Success).detections
        assertEquals(1, detections.size)
        assertEquals("Short Circuit", detections[0].canonicalClassName)
        assertEquals(DefectSeverity.CRITICAL, detections[0].severity)
        assertEquals(0.4f, detections[0].boundingBox.x, 0.01f)
        assertEquals(0.4f, detections[0].boundingBox.y, 0.01f)
    }

    @Test
    fun `decoder rejects unsupported output shape cleanly`() {
        val transform = YoloPreprocessor.computeLetterboxTransform(640, 640, 640, 640)
        val result = YoloOutputDecoder.decode(
            flatData = FloatArray(100),
            shape = longArrayOf(1L, 10L, 10L),
            classLabels = DefectTaxonomy.expectedDatasetLabelsByIndex,
            transform = transform
        )
        assertTrue(result is YoloDecodeResult.UnsupportedShape)
    }

    @Test
    fun `unloaded OnnxPcbDetectionEngine reports NOT_LOADED and fails real inspection cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = OnnxPcbDetectionEngine(context)
        assertEquals(ModelRuntimeState.NOT_LOADED, engine.modelInfo.value.state)

        val outcome = engine.detect(
            PcbImageInput(
                sourceUri = "file:///tmp/board.jpg",
                sourceType = PcbImageSource.CAMERA,
                boardName = "Real Camera Board",
                validation = ImageValidationResult(
                    isValid = true,
                    isProcessable = true,
                    width = 1200,
                    height = 900,
                    clarityScore = 180.0,
                    lowClarityRisk = false
                )
            )
        )
        assertTrue(outcome is DetectionOutcome.Failure)
        assertEquals(DetectionErrorType.MODEL_NOT_LOADED, (outcome as DetectionOutcome.Failure).errorType)
    }
}
