package com.example.inference.llm

import com.example.domain.model.BoundingBox
import com.example.domain.model.DefectSeverity
import com.example.domain.model.DetectedDefect
import com.example.domain.model.InspectionDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisoryPromptBuilderTest {

    @Test
    fun `prompt version is smollm2-360m-pcb-json-v1`() {
        assertEquals("smollm2-360m-pcb-json-v1", AdvisoryPromptBuilder.PROMPT_VERSION)
    }

    @Test
    fun `prompt uses SmolLM2 ChatML formatting`() {
        val request = AdvisoryRequest(
            inspectionId = "INSP-101",
            boardName = "TestBoard_Rev2",
            officialDisposition = InspectionDisposition.REJECT,
            policyRationale = "Critical short circuit detected",
            defects = listOf(
                DetectedDefect(
                    defectId = "d1",
                    classIndex = 1,
                    canonicalClassName = "short",
                    rawClassName = "short_circuit",
                    confidence = 0.945f,
                    severity = DefectSeverity.CRITICAL,
                    requiresManualReview = false,
                    boundingBox = BoundingBox(0.12f, 0.34f, 0.05f, 0.08f)
                )
            ),
            imageWidth = 640,
            imageHeight = 640,
            lowClarityRisk = false,
            verifiedReferenceExcerpts = listOf("IPC-A-610 Section 10.1: Conductor clearance must exceed 0.1mm."),
            isDemonstration = false
        )

        val prompt = AdvisoryPromptBuilder.buildGroundedPrompt(request)

        // Verify ChatML tokens
        assertTrue(prompt.contains("<|im_start|>system\n"))
        assertTrue(prompt.contains("<|im_end|>\n<|im_start|>user\n"))
        assertTrue(prompt.contains("<|im_end|>\n<|im_start|>assistant\n"))

        // Verify content grounding
        assertTrue(prompt.contains("TestBoard_Rev2"))
        assertTrue(prompt.contains("Official Policy Disposition: REJECT"))
        assertTrue(prompt.contains("Critical short circuit detected"))
        assertTrue(prompt.contains("short (raw='short_circuit'"))
        assertTrue(prompt.contains("94.5%"))
        assertTrue(prompt.contains("IPC-A-610 Section 10.1"))

        // Verify strict JSON schema instruction
        assertTrue(prompt.contains("\"probable_root_cause\""))
        assertTrue(prompt.contains("\"recommended_operator_action\""))
        assertTrue(prompt.contains("\"relevant_standard\""))
        assertTrue(prompt.contains("\"limitations\""))
    }

    @Test
    fun `prompt handles empty defects and empty references gracefully`() {
        val request = AdvisoryRequest(
            inspectionId = "INSP-102",
            boardName = "CleanBoard",
            officialDisposition = InspectionDisposition.PASS,
            policyRationale = "No defects detected",
            defects = emptyList(),
            imageWidth = 1024,
            imageHeight = 768,
            lowClarityRisk = false,
            verifiedReferenceExcerpts = emptyList(),
            isDemonstration = false
        )

        val prompt = AdvisoryPromptBuilder.buildGroundedPrompt(request)

        assertTrue(prompt.contains("None (0 localized detections)."))
        assertTrue(prompt.contains("None provided. Do NOT invent standard numbers"))
    }
}
