package com.example.data.csv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.PcbInspectorDatabase
import com.example.data.repository.InspectionRepositoryImpl
import com.example.domain.model.AdvisoryAvailability
import com.example.domain.model.AiAdvisory
import com.example.domain.model.BoundingBox
import com.example.domain.model.DefectSeverity
import com.example.domain.model.DetectedDefect
import com.example.domain.model.InspectionDisposition
import com.example.domain.model.InspectionLatencyMetrics
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.model.InspectionRecord
import com.example.domain.model.ModelExecutionProvenance
import com.example.domain.model.PcbImageSource
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CsvAuditOutboxTest {

    private lateinit var context: Context
    private lateinit var db: PcbInspectorDatabase
    private lateinit var repository: InspectionRepositoryImpl
    private lateinit var csvExport: CsvExportManagerImpl
    private lateinit var csvLogger: InspectionCsvLoggerImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PcbInspectorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = InspectionRepositoryImpl(db.inspectionDao())
        csvExport = CsvExportManagerImpl()
        csvLogger = InspectionCsvLoggerImpl(context, db.inspectionDao(), repository, csvExport)
        File(csvLogger.getAuditFilePath()).delete()
    }

    @After
    fun teardown() {
        File(csvLogger.getAuditFilePath()).delete()
        db.close()
    }

    private fun sampleRecord(
        id: String,
        boardName: String = "Board-A",
        disposition: InspectionDisposition = InspectionDisposition.PASS,
        defects: List<DetectedDefect> = emptyList(),
        isDemo: Boolean = false,
        rootCause: String? = null
    ): InspectionRecord {
        val cfg = InspectionPolicyConfig()
        return InspectionRecord(
            inspectionId = id,
            timestampEpochMs = 1_760_000_000_000L,
            boardName = boardName,
            sourceType = PcbImageSource.CAMERA,
            imageUri = "file:///tmp/$id.jpg",
            imageWidth = 1200,
            imageHeight = 900,
            lowClarityRisk = false,
            clarityScore = 150.0,
            officialDisposition = disposition,
            policyProfile = cfg.profileName,
            policyVersion = cfg.policyVersion,
            policyConfigSnapshot = cfg,
            effectiveThresholdsSummary = cfg.effectiveThresholdsSummary(),
            policyRationale = "Rationale for $id",
            defects = defects,
            totalDefects = defects.size,
            isDemonstration = isDemo,
            provenance = ModelExecutionProvenance.unavailableProduction("1.22.0"),
            advisory = AiAdvisory(
                availability = if (rootCause == null) AdvisoryAvailability.UNAVAILABLE else AdvisoryAvailability.AVAILABLE,
                isDemonstration = isDemo,
                probableRootCause = rootCause,
                recommendedOperatorAction = "Check board"
            ),
            latencyMetrics = InspectionLatencyMetrics(
                imagePreprocessingMs = 10L,
                yoloInferenceMs = null, // Intentionally unexecuted to test empty serialization (never 0)
                outputDecodingNmsMs = null,
                policyEvaluationMs = 2L,
                llmAdvisoryMs = null,
                totalDurationMs = 15L
            )
        )
    }

    @Test
    fun `rfc4180 escaping handles commas quotes and multiline text cleanly`() {
        val rec = sampleRecord(
            id = "PCB-ESC-1",
            boardName = "Main, \"Rev 2\"\nBoard",
            rootCause = "Line 1, \"quoted\"\r\nLine 2"
        )
        val csv = csvExport.serializeRecordsToCsv(listOf(rec))
        val parsed = CsvExportManagerImpl.parseRfc4180Rows(csv)
        assertEquals(2, parsed.size)
        assertEquals(24, parsed[0].size)
        assertEquals(24, parsed[1].size)
        assertEquals("Main, \"Rev 2\"\nBoard", parsed[1][2])
        assertEquals("Line 1, \"quoted\"\nLine 2", parsed[1][15].replace("\r\n", "\n"))
        // Missing yolo_inference_ms (col 19) must be empty, never "0"
        assertEquals("", parsed[1][19])
    }

    @Test
    fun `zero defect and multi defect records write expected rows and outbox reconciles crash window`() = runTest {
        val zeroRec = sampleRecord(id = "PCB-ZERO", disposition = InspectionDisposition.PASS, defects = emptyList())
        val multiRec = sampleRecord(
            id = "PCB-MULTI",
            disposition = InspectionDisposition.REJECT,
            isDemo = true,
            defects = listOf(
                DetectedDefect("d1", 0, "open", "Open Circuit", 0.9f, DefectSeverity.CRITICAL, true, BoundingBox(0.1f, 0.1f, 0.2f, 0.2f)),
                DetectedDefect("d2", 3, "spur", "Spur", 0.7f, DefectSeverity.MEDIUM, true, BoundingBox(0.5f, 0.5f, 0.1f, 0.1f))
            )
        )

        repository.saveInspectionAtomically(zeroRec)
        repository.saveInspectionAtomically(multiRec)

        // Simulate crash window: PCB-ZERO already flushed to CSV on disk before Room outbox ack
        val auditFile = File(csvLogger.getAuditFilePath())
        auditFile.parentFile?.mkdirs()
        auditFile.writeText(
            CsvExportManagerImpl.CSV_HEADER + "\r\n" + csvExport.serializeRowsOnly(zeroRec),
            StandardCharsets.UTF_8
        )

        val status = csvLogger.processPendingOutboxEvents()
        assertEquals(0, status.pendingCount)
        assertEquals(2, status.confirmedCount)

        val rows = CsvExportManagerImpl.parseRfc4180Rows(auditFile.readText(StandardCharsets.UTF_8))
        // Header + 1 row for PCB-ZERO (no duplicate!) + 2 rows for PCB-MULTI = 4 rows
        assertEquals(4, rows.size)
        assertEquals(1, rows.count { it[0] == "PCB-ZERO" })
        assertEquals(2, rows.count { it[0] == "PCB-MULTI" })
    }

    @Test
    fun `corrupted partial tail triggers deterministic full CSV regeneration from Room`() = runTest {
        val rec = sampleRecord(id = "PCB-RECOVER")
        repository.saveInspectionAtomically(rec)

        val auditFile = File(csvLogger.getAuditFilePath())
        auditFile.parentFile?.mkdirs()
        // Corrupt/truncate partial line simulating process death mid-flush
        auditFile.writeText(CsvExportManagerImpl.CSV_HEADER + "\r\nPCB-RECOVER,truncated,broken", StandardCharsets.UTF_8)

        val status = csvLogger.processPendingOutboxEvents()
        assertEquals(0, status.pendingCount)
        assertEquals(1, status.confirmedCount)

        val (ids, healthy) = CsvExportManagerImpl.extractInspectionIdsAndValidateIntegrity(
            auditFile.readText(StandardCharsets.UTF_8)
        )
        assertTrue(healthy)
        assertEquals(setOf("PCB-RECOVER"), ids)
    }

    @Test
    fun `suggested filename sanitizes unsafe characters`() {
        assertEquals(
            "bharat_ai_PCB_2026_01.csv",
            csvExport.suggestedFileNameForRecord("PCB/2026:01")
        )
        assertFalse(csvExport.suggestedFileNameForRecord("../bad").contains("/"))
    }
}
