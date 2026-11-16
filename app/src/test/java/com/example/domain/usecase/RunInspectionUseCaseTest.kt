package com.example.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.csv.CsvExportManagerImpl
import com.example.data.csv.InspectionCsvLoggerImpl
import com.example.data.local.database.PcbInspectorDatabase
import com.example.data.repository.InspectionRepositoryImpl
import com.example.domain.model.AdvisoryAvailability
import com.example.domain.model.InspectionDisposition
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.model.PcbImageSource
import com.example.domain.policy.InspectionPolicyEngine
import com.example.domain.repository.SettingsRepository
import com.example.inference.cv.OnnxPcbDetectionEngine
import com.example.inference.demo.DemoAdvisoryEngine
import com.example.inference.demo.DemoPcbDetectionEngine
import com.example.inference.demo.SamplePcbCatalog
import com.example.inference.llm.ExecuTorchAdvisoryEngine
import com.example.storage.ImageStorageManagerImpl
import com.example.ui.theme.AppThemeMode
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class RunInspectionUseCaseTest {

    private lateinit var context: Context
    private lateinit var db: PcbInspectorDatabase
    private lateinit var repository: InspectionRepositoryImpl
    private lateinit var imageStorageManager: ImageStorageManagerImpl
    private lateinit var csvLogger: InspectionCsvLoggerImpl
    private lateinit var appScope: CoroutineScope
    private lateinit var fakeSettings: FakeSettingsRepository
    private lateinit var useCase: RunInspectionUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PcbInspectorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = InspectionRepositoryImpl(db.inspectionDao())
        imageStorageManager = ImageStorageManagerImpl(context, Dispatchers.Unconfined)
        val csvExport = CsvExportManagerImpl()
        csvLogger = InspectionCsvLoggerImpl(
            context = context,
            dao = db.inspectionDao(),
            repository = repository,
            csvExportManager = csvExport,
            ioDispatcher = Dispatchers.Unconfined
        )
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        fakeSettings = FakeSettingsRepository()

        useCase = RunInspectionUseCase(
            imageStorageManager = imageStorageManager,
            policyEngine = InspectionPolicyEngine(),
            productionDetectionEngine = OnnxPcbDetectionEngine(context, Dispatchers.Unconfined),
            demoDetectionEngine = DemoPcbDetectionEngine(),
            productionAdvisoryEngine = ExecuTorchAdvisoryEngine(Dispatchers.Unconfined),
            demoAdvisoryEngine = DemoAdvisoryEngine(),
            inspectionRepository = repository,
            settingsRepository = fakeSettings,
            csvLogger = csvLogger,
            applicationScope = appScope,
            defaultDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
    }

    @Test
    fun realGalleryImageWithUnloadedOnnxModelYieldsIncompleteAndNeverFallsBackToDemo() = runBlocking {
        val sharpPng = createTexturedPng("real_pcb_test.png", 640, 480)
        val record = useCase.execute(
            imageUriString = Uri.fromFile(sharpPng).toString(),
            sourceType = PcbImageSource.GALLERY,
            boardName = "Real Line Board 01"
        )

        assertEquals(InspectionDisposition.INCOMPLETE, record.officialDisposition)
        assertFalse(record.isDemonstration)
        assertTrue(record.defects.isEmpty())
        assertEquals("OnnxPcbDetectionEngine", record.provenance.inferenceEngine)
        assertEquals("NONE", record.provenance.modelSha256)
        assertEquals(AdvisoryAvailability.UNAVAILABLE, record.advisory.availability)

        val stored = repository.getRecordById(record.inspectionId)
        assertEquals(InspectionDisposition.INCOMPLETE, stored?.officialDisposition)
        assertFalse(stored?.isDemonstration ?: true)
    }

    @Test
    fun samplePresetExecutesThroughDemoEngineAndPersistsAtomically() = runBlocking {
        val preset = SamplePcbCatalog.presets.first { it.id == "sample_qfp100" }
        val materializedUri = imageStorageManager.materializeSamplePreset(preset).getOrThrow()

        val record = useCase.execute(
            imageUriString = materializedUri,
            sourceType = PcbImageSource.SAMPLE,
            boardName = preset.boardName,
            sampleScenarioHint = preset.scenarioHint
        )

        assertTrue(record.isDemonstration)
        assertEquals(InspectionDisposition.REJECT, record.officialDisposition)
        assertTrue(record.defects.isNotEmpty())
        assertEquals(AdvisoryAvailability.AVAILABLE, record.advisory.availability)
        assertTrue(record.advisory.isDemonstration)

        val all = repository.observeAllRecords().first()
        assertEquals(1, all.size)
        assertEquals(record.inspectionId, all.first().inspectionId)
    }

    @Test
    fun unreadableRealImageYieldsIncompleteSafely() = runBlocking {
        val missingUri = "file:///nonexistent_path/missing_board.png"
        val record = useCase.execute(
            imageUriString = missingUri,
            sourceType = PcbImageSource.CAMERA,
            boardName = "Missing Capture"
        )

        assertEquals(InspectionDisposition.INCOMPLETE, record.officialDisposition)
        assertFalse(record.isDemonstration)
        assertTrue(record.defects.isEmpty())
    }

    private fun createTexturedPng(name: String, width: Int, height: Int): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(22, 85, 45))
        for (y in 20 until height - 20 step 8) {
            for (x in 20 until width - 20 step 8) {
                bmp.setPixel(x, y, Color.rgb(225, 195, 90))
                bmp.setPixel(x + 1, y, Color.rgb(30, 30, 30))
            }
        }
        val outFile = File(context.cacheDir, name)
        FileOutputStream(outFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        return outFile
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val _theme = MutableStateFlow(AppThemeMode.SYSTEM)
        private val _policy = MutableStateFlow(InspectionPolicyConfig())
        override val themeMode: Flow<AppThemeMode> = _theme
        override val policyConfig: Flow<InspectionPolicyConfig> = _policy
        override suspend fun setThemeMode(mode: AppThemeMode) { _theme.value = mode }
        override suspend fun updatePolicyConfig(config: InspectionPolicyConfig) { _policy.value = config }
        override suspend fun resetPolicyDefaults() { _policy.value = InspectionPolicyConfig() }
    }
}
