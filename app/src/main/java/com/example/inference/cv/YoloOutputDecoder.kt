package com.example.inference.cv

import com.example.domain.model.DetectedDefect
import com.example.domain.policy.DefectTaxonomy
import java.util.UUID

sealed interface YoloDecodeResult {
    data class Success(val detections: List<DetectedDefect>) : YoloDecodeResult
    data class UnsupportedShape(val message: String) : YoloDecodeResult
}

object YoloOutputDecoder {
    const val DECODER_VERSION = "yolo-v8-adaptive-v1"
    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.25f
    const val DEFAULT_IOU_THRESHOLD = 0.45f

    /**
     * Decodes a 3D [1, dim1, dim2] or 2D [N, 6] YOLOv8 ONNX output tensor.
     *
     * Supported layouts (validated dynamically from inspected tensor shape):
     * - [1, 4 + C, N]: standard Ultralytics YOLOv8 raw detection head
     * - [1, N, 4 + C]: transposed raw detection head
     * - [1, N, 6] or [N, 6]: embedded NMS output [x1, y1, x2, y2, score, class_id]
     */
    fun decode(
        flatData: FloatArray,
        shape: LongArray,
        classLabels: List<String>,
        transform: LetterboxTransform,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
        iouThreshold: Float = DEFAULT_IOU_THRESHOLD
    ): YoloDecodeResult {
        if (classLabels.isEmpty()) {
            return YoloDecodeResult.UnsupportedShape("Model class labels are empty.")
        }

        val numClasses = classLabels.size
        val expectedChannels = 4 + numClasses

        // Case A: Embedded NMS 2D [N, 6]
        if (shape.size == 2 && shape[1] == 6L) {
            val count = shape[0].toInt()
            if (flatData.size < count * 6) {
                return YoloDecodeResult.UnsupportedShape("Output buffer size mismatch for [N, 6].")
            }
            val detections = decodeEmbeddedNmsRows(flatData, count, classLabels, transform, confidenceThreshold)
            return YoloDecodeResult.Success(detections)
        }

        if (shape.size != 3 || shape[0] != 1L) {
            return YoloDecodeResult.UnsupportedShape(
                "Unsupported ONNX output shape ${shape.contentToString()}; expected [1, $expectedChannels, N], [1, N, $expectedChannels], or [1, N, 6]."
            )
        }

        val d1 = shape[1].toInt()
        val d2 = shape[2].toInt()
        if (d1 <= 0 || d2 <= 0 || flatData.size < d1 * d2) {
            return YoloDecodeResult.UnsupportedShape(
                "Output buffer size ${flatData.size} inconsistent with shape ${shape.contentToString()}."
            )
        }

        // Case B: Embedded NMS [1, N, 6] (only when numClasses != 2, which holds since numClasses == 11)
        if (d2 == 6 && d2 != expectedChannels) {
            val detections = decodeEmbeddedNmsRows(flatData, d1, classLabels, transform, confidenceThreshold)
            return YoloDecodeResult.Success(detections)
        }

        val candidates = mutableListOf<RawDetectionCandidate>()

        when {
            // Case C: Standard YOLOv8 [1, 4 + C, N]
            d1 == expectedChannels -> {
                val numBoxes = d2
                for (b in 0 until numBoxes) {
                    var bestClass = -1
                    var bestScore = 0f
                    for (c in 0 until numClasses) {
                        val score = flatData[(4 + c) * numBoxes + b]
                        if (score > bestScore) {
                            bestScore = score
                            bestClass = c
                        }
                    }
                    if (bestScore >= confidenceThreshold && bestClass >= 0) {
                        val cx = flatData[0 * numBoxes + b]
                        val cy = flatData[1 * numBoxes + b]
                        val bw = flatData[2 * numBoxes + b]
                        val bh = flatData[3 * numBoxes + b]
                        candidates.add(
                            RawDetectionCandidate(
                                classIndex = bestClass,
                                confidence = bestScore.coerceIn(0f, 1f),
                                x1Px = cx - bw / 2f,
                                y1Px = cy - bh / 2f,
                                x2Px = cx + bw / 2f,
                                y2Px = cy + bh / 2f
                            )
                        )
                    }
                }
            }
            // Case D: Transposed YOLOv8 [1, N, 4 + C]
            d2 == expectedChannels -> {
                val numBoxes = d1
                val stride = expectedChannels
                for (b in 0 until numBoxes) {
                    val base = b * stride
                    var bestClass = -1
                    var bestScore = 0f
                    for (c in 0 until numClasses) {
                        val score = flatData[base + 4 + c]
                        if (score > bestScore) {
                            bestScore = score
                            bestClass = c
                        }
                    }
                    if (bestScore >= confidenceThreshold && bestClass >= 0) {
                        val cx = flatData[base]
                        val cy = flatData[base + 1]
                        val bw = flatData[base + 2]
                        val bh = flatData[base + 3]
                        candidates.add(
                            RawDetectionCandidate(
                                classIndex = bestClass,
                                confidence = bestScore.coerceIn(0f, 1f),
                                x1Px = cx - bw / 2f,
                                y1Px = cy - bh / 2f,
                                x2Px = cx + bw / 2f,
                                y2Px = cy + bh / 2f
                            )
                        )
                    }
                }
            }
            else -> {
                return YoloDecodeResult.UnsupportedShape(
                    "Output shape ${shape.contentToString()} does not match 4 + ${classLabels.size} = $expectedChannels channels."
                )
            }
        }

        val kept = NonMaxSuppression.applyClassWiseNms(candidates, iouThreshold = iouThreshold)
        val detections = kept.map { cand ->
            toDetectedDefect(cand, classLabels, transform)
        }
        return YoloDecodeResult.Success(detections)
    }

    private fun decodeEmbeddedNmsRows(
        flatData: FloatArray,
        rowCount: Int,
        classLabels: List<String>,
        transform: LetterboxTransform,
        confidenceThreshold: Float
    ): List<DetectedDefect> {
        val result = mutableListOf<DetectedDefect>()
        for (i in 0 until rowCount) {
            val base = i * 6
            val score = flatData[base + 4]
            if (score < confidenceThreshold) continue
            val classIdx = flatData[base + 5].toInt()
            val candidate = RawDetectionCandidate(
                classIndex = classIdx,
                confidence = score.coerceIn(0f, 1f),
                x1Px = flatData[base],
                y1Px = flatData[base + 1],
                x2Px = flatData[base + 2],
                y2Px = flatData[base + 3]
            )
            result.add(toDetectedDefect(candidate, classLabels, transform))
        }
        return result
    }

    private fun toDetectedDefect(
        candidate: RawDetectionCandidate,
        classLabels: List<String>,
        transform: LetterboxTransform
    ): DetectedDefect {
        val rawLabel = classLabels.getOrElse(candidate.classIndex) { "unknown_class_${candidate.classIndex}" }
        val taxonomy = DefectTaxonomy.resolve(rawLabel)
        val normalizedBox = NonMaxSuppression.toNormalizedBoundingBox(candidate, transform)
        return DetectedDefect(
            defectId = "det-${UUID.randomUUID().toString().take(8)}",
            classIndex = candidate.classIndex,
            rawClassName = rawLabel,
            canonicalClassName = taxonomy.canonicalLabel,
            confidence = candidate.confidence,
            severity = taxonomy.defaultSeverity,
            requiresManualReview = taxonomy.requiresManualReview || !taxonomy.isKnownClass,
            boundingBox = normalizedBox,
            code = taxonomy.code,
            description = taxonomy.description
        )
    }
}
