package com.example.domain.policy

import com.example.domain.model.BoundingBox
import com.example.domain.model.DefectSeverity
import com.example.domain.model.DetectedDefect
import com.example.domain.model.DetectionErrorType
import com.example.domain.model.DetectionOutcome
import com.example.domain.model.ImageValidationResult
import com.example.domain.model.InspectionDisposition
import com.example.domain.model.InspectionLatencyMetrics
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.model.ModelExecutionProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionPolicyEngineTest {

    private val engine = InspectionPolicyEngine()
    private val defaultConfig = InspectionPolicyConfig()
    private val provenance = ModelExecutionProvenance.unavailableProduction("test")

    private fun validImage(lowClarity: Boolean = false) = ImageValidationResult(
        isValid = true,
        isProcessable = true,
        width = 1200,
        height = 900,
        clarityScore = if (lowClarity) 18.0 else 240.0,
        lowClarityRisk = lowClarity,
        orientationDegrees = 0
    )

    private fun defect(
        rawClassName: String,
        classIndex: Int = 99,
        confidence: Float = 0.85f,
        claimedSeverity: DefectSeverity = DefectSeverity.LOW
    ) = DetectedDefect(
        defectId = "d-$rawClassName-$classIndex",
        classIndex = classIndex,
        rawClassName = rawClassName,
        canonicalClassName = rawClassName,
        confidence = confidence,
        severity = claimedSeverity,
        requiresManualReview = false,
        boundingBox = BoundingBox(0.1f, 0.2f, 0.2f, 0.2f)
    )

    private fun outcomeOf(vararg defects: DetectedDefect) = DetectionOutcome.Success(
        detections = defects.toList(),
        latencyMetrics = InspectionLatencyMetrics(yoloInferenceMs = 25L),
        provenance = provenance,
        isDemonstration = false
    )

    @Test
    fun `unprocessable image produces INCOMPLETE and never PASS`() {
        val unprocessable = ImageValidationResult(
            isValid = false,
            isProcessable = false,
            width = 120,
            height = 90,
            clarityScore = null,
            lowClarityRisk = true,
            errorMessage = "Image below minimum dimensions"
        )
        val result = engine.evaluate(unprocessable, outcomeOf(), defaultConfig)
        assertEquals(InspectionDisposition.INCOMPLETE, result.officialDisposition)
    }

    @Test
    fun `inference failure always produces INCOMPLETE even with empty detections`() {
        val failure = DetectionOutcome.Failure(
            errorType = DetectionErrorType.MODEL_NOT_LOADED,
            errorMessage = "ONNX session not loaded",
            latencyMetrics = InspectionLatencyMetrics(),
            provenance = provenance
        )
        val result = engine.evaluate(validImage(lowClarity = false), failure, defaultConfig)
        assertEquals(InspectionDisposition.INCOMPLETE, result.officialDisposition)
        assertTrue(result.rationale.contains("MODEL_NOT_LOADED"))
    }

    @Test
    fun `zero detections with adequate image quality yields PASS under screening profile`() {
        val result = engine.evaluate(validImage(lowClarity = false), outcomeOf(), defaultConfig)
        assertEquals(InspectionDisposition.PASS, result.officialDisposition)
        assertTrue(result.rationale.contains("0 defects"))
        assertFalse(result.rationale.contains("certified IPC-A-610 compliance", ignoreCase = false))
    }

    @Test
    fun `zero detections with poor image quality yields REVIEW and never PASS`() {
        val resultWithGateOn = engine.evaluate(
            validImage(lowClarity = true),
            outcomeOf(),
            defaultConfig.copy(reviewOnLowClarityZeroDefects = true)
        )
        assertEquals(InspectionDisposition.REVIEW, resultWithGateOn.officialDisposition)

        val resultWithGateOff = engine.evaluate(
            validImage(lowClarity = true),
            outcomeOf(),
            defaultConfig.copy(reviewOnLowClarityZeroDefects = false)
        )
        assertEquals(InspectionDisposition.REVIEW, resultWithGateOff.officialDisposition)
    }

    @Test
    fun `processable questionable image with critical defect yields REJECT`() {
        val result = engine.evaluate(
            validImage(lowClarity = true),
            outcomeOf(defect("short", classIndex = 1, confidence = 0.35f)),
            defaultConfig
        )
        assertEquals(InspectionDisposition.REJECT, result.officialDisposition)
        assertTrue(result.rationale.contains("clarity", ignoreCase = true))
    }

    @Test
    fun `disabling critical auto reject escalates critical defect to REVIEW and never PASS`() {
        val config = defaultConfig.copy(rejectOnCritical = false)
        val result = engine.evaluate(
            validImage(),
            outcomeOf(defect("open", classIndex = 0, confidence = 0.25f)),
            config
        )
        assertEquals(InspectionDisposition.REVIEW, result.officialDisposition)
        assertEquals(DefectSeverity.CRITICAL, result.evaluatedDefects.single().severity)
    }

    @Test
    fun `severity comes from normalized class name not model class index or confidence`() {
        // Pass classIndex=7 (pin_hole index) with rawClassName="missing_copper" and low confidence
        val result = engine.evaluate(
            validImage(),
            outcomeOf(
                defect(
                    rawClassName = "missing_copper",
                    classIndex = 7,
                    confidence = 0.12f,
                    claimedSeverity = DefectSeverity.LOW
                )
            ),
            defaultConfig
        )
        assertEquals(InspectionDisposition.REJECT, result.officialDisposition)
        assertEquals(DefectSeverity.CRITICAL, result.evaluatedDefects.single().severity)
        assertEquals("Missing Copper", result.evaluatedDefects.single().canonicalClassName)
    }

    @Test
    fun `unknown and copper and undocumented classes always force REVIEW`() {
        listOf("copper_burr", "hole_breakout", "scratch", "copper", "unexpected_defect").forEach { label ->
            val result = engine.evaluate(
                validImage(),
                outcomeOf(defect(label)),
                defaultConfig.copy(rejectOnCritical = false, reviewOnMedium = false, reviewOnLow = false)
            )
            assertEquals("Expected REVIEW for $label", InspectionDisposition.REVIEW, result.officialDisposition)
            assertTrue(result.evaluatedDefects.single().requiresManualReview)
        }
    }

    @Test
    fun `tolerated medium defect when reviewOnMedium is false yields PASS with accurate non-zero count`() {
        val config = defaultConfig.copy(reviewOnMedium = false, reviewOnLow = false)
        val result = engine.evaluate(
            validImage(),
            outcomeOf(defect("mouse_bite"), defect("spur")),
            config
        )
        assertEquals(InspectionDisposition.PASS, result.officialDisposition)
        assertTrue(result.rationale.contains("2 localized defect"))
        assertFalse(result.rationale.contains("0 defect"))
    }

    @Test
    fun `policy evaluation preserves exact config snapshot and version`() {
        val customConfig = InspectionPolicyConfig(
            profileName = "Lab Line 3 Screening",
            policyVersion = "2.1.0-screening",
            rejectOnCritical = false,
            reviewOnMedium = true,
            minImageWidth = 640,
            minImageHeight = 480
        )
        val result = engine.evaluate(validImage(), outcomeOf(defect("spur")), customConfig)
        assertEquals("Lab Line 3 Screening", result.policyProfile)
        assertEquals("2.1.0-screening", result.policyVersion)
        assertEquals(customConfig, result.configSnapshot)
    }
}
