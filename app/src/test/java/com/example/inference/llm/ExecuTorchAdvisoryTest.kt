package com.example.inference.llm

import com.example.domain.model.AdvisoryAvailability
import com.example.domain.model.InspectionDisposition
import com.example.domain.model.ModelRuntimeState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExecuTorchAdvisoryTest {

    @Test
    fun `unloaded ExecuTorch engine reports NOT_LOADED and returns UNAVAILABLE advisory`() = runTest {
        val engine = ExecuTorchAdvisoryEngine()
        assertEquals(ModelRuntimeState.NOT_LOADED, engine.modelInfo.value.state)

        val advisory = engine.generateAdvisory(
            AdvisoryRequest(
                inspectionId = "PCB-2001",
                boardName = "Main PCB",
                officialDisposition = InspectionDisposition.REVIEW,
                policyRationale = "Medium defect detected",
                defects = emptyList(),
                imageWidth = 1200,
                imageHeight = 900,
                lowClarityRisk = false,
                isDemonstration = false
            )
        )
        assertEquals(AdvisoryAvailability.UNAVAILABLE, advisory.availability)
    }
}
