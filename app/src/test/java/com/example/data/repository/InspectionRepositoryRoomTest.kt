package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.PcbInspectorDatabase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InspectionRepositoryRoomTest {

    private lateinit var db: PcbInspectorDatabase
    private lateinit var repository: InspectionRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PcbInspectorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = InspectionRepositoryImpl(db.inspectionDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `saveInspectionAtomically persists record defects provenance and pending outbox event`() = runTest {
        val snapshot = InspectionPolicyConfig(
            profileName = "Line A Snapshot",
            policyVersion = "1.2.0",
            rejectOnCritical = true,
            minImageWidth = 320,
            minImageHeight = 240
        )
        val record = InspectionRecord(
            inspectionId = "PCB-1001",
            timestampEpochMs = 1_760_000_000_000L,
            boardName = "Power Supply Rev C",
            sourceType = PcbImageSource.CAMERA,
            imageUri = "file:///data/user/0/pcb1001.jpg",
            imageWidth = 1280,
            imageHeight = 960,
            lowClarityRisk = false,
            clarityScore = 180.5,
            officialDisposition = InspectionDisposition.REJECT,
            policyProfile = snapshot.profileName,
            policyVersion = snapshot.policyVersion,
            policyConfigSnapshot = snapshot,
            effectiveThresholdsSummary = snapshot.effectiveThresholdsSummary(),
            policyRationale = "REJECT: Short circuit found.",
            defects = listOf(
                DetectedDefect(
                    defectId = "def-1",
                    classIndex = 1,
                    rawClassName = "short",
                    canonicalClassName = "Short Circuit",
                    confidence = 0.91f,
                    severity = DefectSeverity.CRITICAL,
                    requiresManualReview = true,
                    boundingBox = BoundingBox(0.2f, 0.3f, 0.1f, 0.12f)
                )
            ),
            totalDefects = 1,
            isDemonstration = false,
            provenance = ModelExecutionProvenance(
                inferenceEngine = "OnnxPcbDetectionEngine",
                modelIdentity = "yolov8n-pcb.onnx",
                modelSha256 = "abc123sha",
                modelContractVersion = "onnx-yolo-v1",
                preprocessingVersion = "letterbox-rgb-v1",
                decoderVersion = "yolo-nms-v1",
                classMappingVersion = "pcb-11-v1",
                runtimeVersion = "ort-1.21.0"
            ),
            advisory = AiAdvisory(
                availability = AdvisoryAvailability.UNAVAILABLE,
                isDemonstration = false,
                unavailableReason = "ExecuTorch model not loaded"
            ),
            latencyMetrics = InspectionLatencyMetrics(
                imagePreprocessingMs = 12L,
                yoloInferenceMs = 45L,
                outputDecodingNmsMs = 6L,
                policyEvaluationMs = 2L,
                llmAdvisoryMs = null,
                totalDurationMs = 65L
            )
        )

        repository.saveInspectionAtomically(record)

        val loaded = repository.getRecordById("PCB-1001")
        assertNotNull(loaded)
        assertEquals("Line A Snapshot", loaded!!.policyProfile)
        assertEquals(320, loaded.policyConfigSnapshot.minImageWidth)
        assertEquals("abc123sha", loaded.provenance.modelSha256)
        assertNull(loaded.latencyMetrics.llmAdvisoryMs)
        assertEquals(1, loaded.defects.size)
        assertEquals(1, repository.getPendingOutboxCount())
        assertTrue(repository.getReferencedImageUris().contains("file:///data/user/0/pcb1001.jpg"))

        repository.clearAllHistory()
        assertTrue(repository.observeAllRecords().first().isEmpty())
        assertEquals(0, repository.getPendingOutboxCount())
    }
}
