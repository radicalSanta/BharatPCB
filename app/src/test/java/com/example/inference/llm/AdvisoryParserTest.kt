package com.example.inference.llm

import com.example.domain.model.AdvisoryAvailability
import com.example.domain.model.InspectionDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisoryParserTest {

    @Test
    fun `valid structured JSON parses cleanly and suppresses unverified standard citations`() {
        val raw = """
            Here is the report:
            {
              "probable_root_cause": "Excess solder paste volume bridged adjacent QFP leads.",
              "recommended_operator_action": "Inspect leads under magnification and rework bridge with flux.",
              "relevant_standard": "IPC-A-610 Section 8.3.2",
              "limitations": "Advisory generated from localized bounding box metadata only."
            }
        """.trimIndent()

        val advisory = AdvisoryOutputParser.parseAndValidate(
            rawText = raw,
            officialDisposition = InspectionDisposition.REJECT,
            hasVerifiedReferences = false,
            isDemonstration = false,
            latencyMs = 420L,
            provenanceSummary = "ExecuTorch"
        )

        assertEquals(AdvisoryAvailability.AVAILABLE, advisory.availability)
        assertTrue(advisory.relevantStandard!!.startsWith("None verified"))
        assertEquals(InspectionDisposition.REJECT, advisory.suggestedDisposition)
    }

    @Test
    fun `invalid JSON or missing required fields returns UNAVAILABLE safely`() {
        val malformed = AdvisoryOutputParser.parseAndValidate(
            rawText = "Not json at all",
            officialDisposition = InspectionDisposition.REVIEW,
            hasVerifiedReferences = false,
            isDemonstration = false,
            latencyMs = 100L,
            provenanceSummary = "ExecuTorch"
        )
        assertEquals(AdvisoryAvailability.UNAVAILABLE, malformed.availability)

        val missingField = AdvisoryOutputParser.parseAndValidate(
            rawText = """{"probable_root_cause": "Short", "recommended_operator_action": ""}""",
            officialDisposition = InspectionDisposition.REVIEW,
            hasVerifiedReferences = false,
            isDemonstration = false,
            latencyMs = 100L,
            provenanceSummary = "ExecuTorch"
        )
        assertEquals(AdvisoryAvailability.UNAVAILABLE, missingField.availability)
    }

    @Test
    fun `certification claims are rejected safely`() {
        val raw = """
            {
              "probable_root_cause": "Board is certified compliant with all classes.",
              "recommended_operator_action": "Ship immediately.",
              "relevant_standard": "None",
              "limitations": "None"
            }
        """.trimIndent()

        val result = AdvisoryOutputParser.parseAndValidate(
            rawText = raw,
            officialDisposition = InspectionDisposition.PASS,
            hasVerifiedReferences = false,
            isDemonstration = false,
            latencyMs = 50L,
            provenanceSummary = "ExecuTorch"
        )
        assertEquals(AdvisoryAvailability.UNAVAILABLE, result.availability)
    }
}
