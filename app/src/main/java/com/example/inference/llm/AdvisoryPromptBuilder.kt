package com.example.inference.llm

import java.util.Locale

object AdvisoryPromptBuilder {

    const val PROMPT_VERSION = "smollm2-360m-pcb-json-v1"
    const val ZERO_DEFECT_ROOT_CAUSE = "No localized defects detected; no defect-specific root cause can be established."

    fun buildGroundedPrompt(request: AdvisoryRequest): String {
        val hasZeroDefects = request.defects.isEmpty()
        val defectSummary = if (hasZeroDefects) {
            "None (0 localized detections)."
        } else {
            request.defects.joinToString("\n") { d ->
                val confPct = String.format(Locale.US, "%.1f%%", d.confidence * 100f)
                val boxStr = if (d.boundingBox != null) {
                    "box=[x=${fmt(d.boundingBox.x)}, y=${fmt(d.boundingBox.y)}, w=${fmt(d.boundingBox.width)}, h=${fmt(d.boundingBox.height)}]"
                } else {
                    "box=none"
                }
                "- ${d.canonicalClassName} (raw='${d.rawClassName}', severity=${d.severity.name}, manualReview=${d.requiresManualReview}, confidence=$confPct, $boxStr)"
            }
        }

        val referenceContext = if (request.verifiedReferenceExcerpts.isEmpty()) {
            "None provided. Do NOT invent standard numbers, clauses, or certification claims."
        } else {
            request.verifiedReferenceExcerpts.joinToString("\n")
        }

        val (exampleUser, exampleAssistant, zeroDefectRule) = if (hasZeroDefects) {
            Triple(
                """
                Board Name: DemoBoard_Rev1
                Image Resolution: 640x640 (lowClarityRisk=false)
                Official Policy Disposition: PASS
                Policy Rationale: All inspection zones clear. No defect anomalies detected.
                Localized Detections:
                None (0 localized detections).
                Reference Context:
                None provided. Do NOT invent standard numbers, clauses, or certification claims.
                """.trimIndent(),
                """{"probable_root_cause": "$ZERO_DEFECT_ROOT_CAUSE", "recommended_operator_action": "Proceed with standard production flow and routine visual inspection.", "relevant_standard": "None verified", "limitations": "Advisory only; confirmation based on automated optical inspection."}""",
                "\n3. CRITICAL: 0 defects were detected. For \"probable_root_cause\", you MUST output EXACTLY: \"$ZERO_DEFECT_ROOT_CAUSE\". Never hypothesize or invent defect causes."
            )
        } else {
            Triple(
                """
                Board Name: DemoBoard_Rev1
                Image Resolution: 640x640 (lowClarityRisk=false)
                Official Policy Disposition: REJECT
                Policy Rationale: Critical trace bridge short detected
                Localized Detections:
                - short (raw='short_circuit', severity=CRITICAL, manualReview=false, confidence=94.0%, box=[x=0.120, y=0.340, w=0.050, h=0.080])
                Reference Context:
                IPC-A-610 Section 10.1: Conductor clearance must exceed 0.1mm.
                """.trimIndent(),
                """{"probable_root_cause": "Solder bridge across adjacent conductive traces.", "recommended_operator_action": "Inspect stencil alignment and rework bridge with desoldering braid.", "relevant_standard": "IPC-A-610 Section 10.1", "limitations": "Advisory only; requires manual optical verification."}""",
                ""
            )
        }

        return """
<|im_start|>system
You are an offline PCB quality engineering assistant. Output EXACTLY ONE JSON object with exact keys: "probable_root_cause", "recommended_operator_action", "relevant_standard", "limitations".
Rules:
1. Output ONLY the raw JSON object starting immediately with '{' and ending with '}'. No Markdown code fences (no ```), no preamble, and no text outside JSON.
2. Each field value must be strictly 1 concise sentence of at most 25 words.$zeroDefectRule
3. If Reference Context has no verified standard, "relevant_standard" must be "None verified".
4. Never alter or question the official policy disposition (${request.officialDisposition.name}). Never claim official certification or standards compliance.
JSON Schema:
{
  "probable_root_cause": "<1 concise sentence, max 25 words>",
  "recommended_operator_action": "<1 concise sentence, max 25 words>",
  "relevant_standard": "<verified standard or 'None verified'>",
  "limitations": "<1 concise sentence, max 25 words>"
}
<|im_end|>
<|im_start|>user
$exampleUser
<|im_end|>
<|im_start|>assistant
$exampleAssistant<|im_end|>
<|im_start|>user
Board Name: ${request.boardName}
Image Resolution: ${request.imageWidth}x${request.imageHeight} (lowClarityRisk=${request.lowClarityRisk})
Official Policy Disposition: ${request.officialDisposition.name}
Policy Rationale: ${request.policyRationale}
Localized Detections:
$defectSummary
Reference Context:
$referenceContext
<|im_end|>
<|im_start|>assistant
""".trimIndent() + "\n"
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.3f", v)
}
