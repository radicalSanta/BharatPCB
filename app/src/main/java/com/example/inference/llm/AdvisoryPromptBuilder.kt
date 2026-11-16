package com.example.inference.llm

import java.util.Locale

object AdvisoryPromptBuilder {

    const val PROMPT_VERSION = "llama32-1b-pcb-json-v1"

    fun buildGroundedPrompt(request: AdvisoryRequest): String {
        val defectSummary = if (request.defects.isEmpty()) {
            "None (0 localized detections)."
        } else {
            request.defects.joinToString("\n") { d ->
                val confPct = String.format(Locale.US, "%.1f%%", d.confidence * 100f)
                "- ${d.canonicalClassName} (raw='${d.rawClassName}', severity=${d.severity.name}, manualReview=${d.requiresManualReview}, confidence=$confPct, box=[x=${fmt(d.boundingBox.x)}, y=${fmt(d.boundingBox.y)}, w=${fmt(d.boundingBox.width)}, h=${fmt(d.boundingBox.height)}])"
            }
        }

        val referenceContext = if (request.verifiedReferenceExcerpts.isEmpty()) {
            "None provided. Do NOT invent standard numbers, clauses, or certification claims."
        } else {
            request.verifiedReferenceExcerpts.joinToString("\n")
        }

        return """
<|begin_of_text|><|start_header_id|>system<|end_header_id|>
You are an offline PCB manufacturing quality engineering assistant.
You must respond ONLY with a single valid JSON object matching this exact schema:
{
  "probable_root_cause": "<concise technical explanation grounded strictly in observed defects>",
  "recommended_operator_action": "<concise practical QA/rework step>",
  "relevant_standard": "<verified standard/clause ONLY if provided in Reference Context, otherwise 'None verified'>",
  "limitations": "<explicit statement of confidence/advisory limits>"
}
Rules:
1. Never alter or question the official policy disposition (${request.officialDisposition.name}).
2. Never invent detections, coordinates, or IPC/ISO clause numbers not in Reference Context.
3. Never claim official certification or standards compliance.
<|eot_id|><|start_header_id|>user<|end_header_id|>
Board Name: ${request.boardName}
Image Resolution: ${request.imageWidth}x${request.imageHeight} (lowClarityRisk=${request.lowClarityRisk})
Official Policy Disposition: ${request.officialDisposition.name}
Policy Rationale: ${request.policyRationale}
Localized Detections:
$defectSummary
Reference Context:
$referenceContext
<|eot_id|><|start_header_id|>assistant<|end_header_id|>
""".trimIndent()
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.3f", v)
}
