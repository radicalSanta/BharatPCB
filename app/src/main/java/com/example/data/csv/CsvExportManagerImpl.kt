package com.example.data.csv

import com.example.domain.model.InspectionRecord
import com.example.domain.repository.CsvExportManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure RFC 4180 CSV serializer preserving the exact 24-column audit contract.
 *
 * - One row per detection; zero-detection and INCOMPLETE records emit one row with empty defect columns.
 * - Missing/unexecuted latencies remain empty strings, never "0".
 */
class CsvExportManagerImpl : CsvExportManager {

    override fun serializeRecordsToCsv(records: List<InspectionRecord>): String {
        val sb = StringBuilder()
        sb.append(CSV_HEADER).append("\r\n")
        for (record in records) {
            appendRecordRows(sb, record)
        }
        return sb.toString()
    }

    fun serializeRowsOnly(record: InspectionRecord): String {
        val sb = StringBuilder()
        appendRecordRows(sb, record)
        return sb.toString()
    }

    fun appendRecordRows(sb: StringBuilder, record: InspectionRecord) {
        val isoTimestamp = formatIsoTimestamp(record.timestampEpochMs)
        val prepMs = record.latencyMetrics.imagePreprocessingMs?.toString().orEmpty()
        val yoloMs = record.latencyMetrics.yoloInferenceMs?.toString().orEmpty()
        val decodeMs = record.latencyMetrics.outputDecodingNmsMs?.toString().orEmpty()
        val policyMs = record.latencyMetrics.policyEvaluationMs?.toString().orEmpty()
        val llmMs = record.latencyMetrics.llmAdvisoryMs?.toString().orEmpty()
        val totalMs = record.latencyMetrics.totalDurationMs?.toString().orEmpty()

        val rootCause = record.advisory.probableRootCause.orEmpty()
        val operatorAction = record.advisory.recommendedOperatorAction.orEmpty()
        val standard = record.advisory.relevantStandard.orEmpty()
        val policyDisposition = record.officialDisposition.name

        if (record.defects.isEmpty()) {
            val row = listOf(
                record.inspectionId,
                isoTimestamp,
                record.boardName,
                record.officialDisposition.name,
                record.totalDefects.toString(),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                policyDisposition,
                standard,
                rootCause,
                operatorAction,
                record.isDemonstration.toString(),
                prepMs,
                yoloMs,
                decodeMs,
                policyMs,
                llmMs,
                totalMs
            )
            sb.append(row.joinToString(",") { escapeRfc4180(it) }).append("\r\n")
        } else {
            for (defect in record.defects) {
                val row = listOf(
                    record.inspectionId,
                    isoTimestamp,
                    record.boardName,
                    record.officialDisposition.name,
                    record.totalDefects.toString(),
                    defect.defectId,
                    defect.canonicalClassName,
                    String.format(Locale.US, "%.4f", defect.confidence),
                    defect.severity.name,
                    String.format(Locale.US, "%.4f", defect.boundingBox.x),
                    String.format(Locale.US, "%.4f", defect.boundingBox.y),
                    String.format(Locale.US, "%.4f", defect.boundingBox.width),
                    String.format(Locale.US, "%.4f", defect.boundingBox.height),
                    policyDisposition,
                    standard,
                    rootCause,
                    operatorAction,
                    record.isDemonstration.toString(),
                    prepMs,
                    yoloMs,
                    decodeMs,
                    policyMs,
                    llmMs,
                    totalMs
                )
                sb.append(row.joinToString(",") { escapeRfc4180(it) }).append("\r\n")
            }
        }
    }

    override fun suggestedFileNameForAll(): String = DEFAULT_AUDIT_FILENAME

    override fun suggestedFileNameForRecord(inspectionId: String): String {
        val sanitized = inspectionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "bharat_ai_${sanitized}.csv"
    }

    companion object {
        const val DEFAULT_AUDIT_FILENAME = "bharat_ai_inspection_logs.csv"

        const val CSV_HEADER =
            "inspection_id,timestamp,board_name,overall_disposition,total_defects," +
                "defect_id,defect_class,confidence,severity,bbox_x,bbox_y,bbox_width,bbox_height," +
                "policy_disposition,inspection_standard,root_cause,operator_action," +
                "is_demonstration,image_preprocessing_ms,yolo_inference_ms,output_decoding_nms_ms," +
                "policy_evaluation_ms,llm_advisory_ms,total_duration_ms"

        fun escapeRfc4180(value: String): String {
            val needsQuotes = value.contains(',') ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')
            return if (needsQuotes) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
        }

        fun formatIsoTimestamp(epochMs: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(epochMs))
        }

        /**
         * Parses first-column inspection_id values cleanly from an RFC 4180 CSV document
         * (handling quoted multiline fields) to detect crash-window duplicates and file integrity.
         */
        fun extractInspectionIdsAndValidateIntegrity(csvText: String): Pair<Set<String>, Boolean> {
            if (csvText.isBlank()) return emptySet<String>() to false
            val rows = parseRfc4180Rows(csvText)
            if (rows.isEmpty()) return emptySet<String>() to false

            val headerJoined = rows.first().joinToString(",")
            if (headerJoined != CSV_HEADER) {
                return emptySet<String>() to false
            }

            val ids = linkedSetOf<String>()
            for (i in 1 until rows.size) {
                val row = rows[i]
                if (row.size != 24) {
                    // Partial / truncated row detected (e.g., crash during flush)
                    return ids to false
                }
                val id = row[0]
                if (id.isNotBlank()) ids.add(id)
            }
            return ids to true
        }

        fun parseRfc4180Rows(input: String): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            val currentRow = mutableListOf<String>()
            val currentField = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < input.length) {
                val ch = input[i]
                if (inQuotes) {
                    if (ch == '"') {
                        if (i + 1 < input.length && input[i + 1] == '"') {
                            currentField.append('"')
                            i += 2
                            continue
                        } else {
                            inQuotes = false
                        }
                    } else {
                        currentField.append(ch)
                    }
                } else {
                    when (ch) {
                        '"' -> inQuotes = true
                        ',' -> {
                            currentRow.add(currentField.toString())
                            currentField.setLength(0)
                        }
                        '\r' -> {
                            if (i + 1 < input.length && input[i + 1] == '\n') i++
                            currentRow.add(currentField.toString())
                            currentField.setLength(0)
                            if (currentRow.any { it.isNotEmpty() }) {
                                rows.add(currentRow.toList())
                            }
                            currentRow.clear()
                        }
                        '\n' -> {
                            currentRow.add(currentField.toString())
                            currentField.setLength(0)
                            if (currentRow.any { it.isNotEmpty() }) {
                                rows.add(currentRow.toList())
                            }
                            currentRow.clear()
                        }
                        else -> currentField.append(ch)
                    }
                }
                i++
            }
            if (inQuotes) {
                // Unterminated quote -> truncated row
                currentRow.add(currentField.toString())
                rows.add(currentRow.toList())
            } else if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
                currentRow.add(currentField.toString())
                rows.add(currentRow.toList())
            }
            return rows
        }
    }
}
