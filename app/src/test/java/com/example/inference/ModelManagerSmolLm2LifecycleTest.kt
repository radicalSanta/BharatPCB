package com.example.inference

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.preferences.SettingsRepositoryImpl
import com.example.domain.model.ModelRuntimeState
import com.example.domain.model.ModelSlot
import com.example.inference.cv.OnnxPcbDetectionEngine
import com.example.inference.cv.PatchCoreTransistorEngine
import com.example.inference.llm.ExecuTorchAdvisoryEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModelManagerSmolLm2LifecycleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var onnxEngine: OnnxPcbDetectionEngine
    private lateinit var execuTorchEngine: ExecuTorchAdvisoryEngine
    private lateinit var patchCoreEngine: PatchCoreTransistorEngine
    private lateinit var modelManager: ModelManagerImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dataStoreFile = tempFolder.newFile("test_settings_${System.currentTimeMillis()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { dataStoreFile }
        settingsRepository = SettingsRepositoryImpl(dataStore)

        onnxEngine = OnnxPcbDetectionEngine(context, Dispatchers.Unconfined)
        execuTorchEngine = ExecuTorchAdvisoryEngine(Dispatchers.Unconfined)
        patchCoreEngine = PatchCoreTransistorEngine(context, Dispatchers.Unconfined)

        modelManager = ModelManagerImpl(
            context = context,
            settingsRepository = settingsRepository,
            onnxEngine = onnxEngine,
            execuTorchEngine = execuTorchEngine,
            patchCoreEngine = patchCoreEngine,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `importAdvisoryModel rejects invalid file extension`() = runBlocking {
        val badFile = tempFolder.newFile("model.onnx").apply { writeText("fake onnx") }
        val result = modelManager.importAdvisoryModel(Uri.fromFile(badFile))

        assertFalse(result.success)
        assertTrue(result.message.contains("expected an ExecuTorch .pte model"))
    }

    @Test
    fun `importAdvisoryTokenizer rejects invalid file extension`() = runBlocking {
        val badFile = tempFolder.newFile("tokenizer.txt").apply { writeText("fake") }
        val result = modelManager.importAdvisoryTokenizer(Uri.fromFile(badFile))

        assertFalse(result.success)
        assertTrue(result.message.contains("expected .json, .bin, or .model"))
    }

    @Test
    fun `importAdvisoryModel stages active_smollm2_advisory pte and updates settings`() = runBlocking {
        val pteFile = tempFolder.newFile("smollm2_360m.pte").apply { writeText("dummy pte content") }
        val result = modelManager.importAdvisoryModel(Uri.fromFile(pteFile))

        assertTrue(result.success)
        val stored = settingsRepository.storedModelArtifacts.first()
        assertTrue(stored.advisoryModelPath?.endsWith("active_smollm2_advisory.pte") == true)
        assertEquals(ModelRuntimeState.NOT_LOADED, execuTorchEngine.modelInfo.value.state)
    }

    @Test
    fun `importAdvisoryTokenizer stages active_tokenizer json and updates settings`() = runBlocking {
        val tokFile = tempFolder.newFile("tokenizer.json").apply { writeText("{\"model\": \"smollm2\"}") }
        val result = modelManager.importAdvisoryTokenizer(Uri.fromFile(tokFile))

        assertTrue(result.success)
        val stored = settingsRepository.storedModelArtifacts.first()
        assertTrue(stored.advisoryTokenizerPath?.endsWith("active_tokenizer.json") == true)
    }

    @Test
    fun `inspectModelMetadata returns SmolLM2 360M metadata`() {
        val metadata = modelManager.inspectModelMetadata(ModelSlot.AI_ADVISORY)
        assertTrue(metadata.contains("Slot: AI Technical Advisory (SmolLM2 360M Instruct)"))
        assertTrue(metadata.contains("Runtime: ExecuTorch Android 1.6.0"))
        assertTrue(metadata.contains("Prompt Contract: smollm2-360m-pcb-json-v1"))
    }
}
