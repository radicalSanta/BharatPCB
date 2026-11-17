

# FILE: source/app/src/main/java/com/example/app/AppContainer.kt

```kotlin
package com.example.app

import android.content.Context
import com.example.data.csv.CsvExportManagerImpl
import com.example.data.csv.InspectionCsvLoggerImpl
import com.example.data.local.database.PcbInspectorDatabase
import com.example.data.local.preferences.SettingsRepositoryImpl
import com.example.data.local.preferences.pcbSettingsDataStore
import com.example.data.repository.InspectionRepositoryImpl
import com.example.domain.policy.InspectionPolicyEngine
import com.example.domain.repository.CsvExportManager
import com.example.domain.repository.ImageStorageManager
import com.example.domain.repository.InspectionCsvLogger
import com.example.domain.repository.InspectionRepository
import com.example.domain.repository.ModelManager
import com.example.domain.repository.SettingsRepository
import com.example.domain.repository.StorageAccessManager
import com.example.domain.usecase.ComputeDashboardMetricsUseCase
import com.example.domain.usecase.RunInspectionUseCase
import com.example.inference.ModelManagerImpl
import com.example.inference.cv.OnnxPcbDetectionEngine
import com.example.inference.demo.DemoAdvisoryEngine
import com.example.inference.demo.DemoPcbDetectionEngine
import com.example.inference.llm.ExecuTorchAdvisoryEngine
import com.example.storage.ImageStorageManagerImpl
import com.example.storage.StorageAccessManagerImpl
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: PcbInspectorDatabase = PcbInspectorDatabase.build(appContext)
    val settingsRepositoryImpl: SettingsRepositoryImpl =
        SettingsRepositoryImpl(appContext.pcbSettingsDataStore)
    val settingsRepository: SettingsRepository = settingsRepositoryImpl

    val inspectionRepository: InspectionRepository =
        InspectionRepositoryImpl(database.inspectionDao())

    val policyEngine: InspectionPolicyEngine = InspectionPolicyEngine()
    val imageStorageManager: ImageStorageManager = ImageStorageManagerImpl(appContext)

    val onnxDetectionEngine: OnnxPcbDetectionEngine = OnnxPcbDetectionEngine(appContext)
    val demoDetectionEngine: DemoPcbDetectionEngine = DemoPcbDetectionEngine()
    val execuTorchAdvisoryEngine: ExecuTorchAdvisoryEngine = ExecuTorchAdvisoryEngine()
    val demoAdvisoryEngine: DemoAdvisoryEngine = DemoAdvisoryEngine()

    val modelManager: ModelManager = ModelManagerImpl(
        context = appContext,
        settingsRepository = settingsRepositoryImpl,
        onnxEngine = onnxDetectionEngine,
        execuTorchEngine = execuTorchAdvisoryEngine
    )

    val csvExportManagerImpl: CsvExportManagerImpl = CsvExportManagerImpl()
    val csvExportManager: CsvExportManager = csvExportManagerImpl
    val csvLogger: InspectionCsvLogger = InspectionCsvLoggerImpl(
        context = appContext,
        dao = database.inspectionDao(),
        repository = inspectionRepository,
        csvExportManager = csvExportManagerImpl
    )
    val storageAccessManager: StorageAccessManager = StorageAccessManagerImpl(appContext)

    val runInspectionUseCase: RunInspectionUseCase = RunInspectionUseCase(
        imageStorageManager = imageStorageManager,
        policyEngine = policyEngine,
        productionDetectionEngine = onnxDetectionEngine,
        demoDetectionEngine = demoDetectionEngine,
        productionAdvisoryEngine = execuTorchAdvisoryEngine,
        demoAdvisoryEngine = demoAdvisoryEngine,
        inspectionRepository = inspectionRepository,
        settingsRepository = settingsRepository,
        csvLogger = csvLogger,
        applicationScope = applicationScope
    )

    val computeDashboardMetricsUseCase: ComputeDashboardMetricsUseCase = ComputeDashboardMetricsUseCase()

    fun runStartupMaintenance() {
        applicationScope.launch {
            runCatching {
                val artifacts = settingsRepositoryImpl.storedModelArtifacts.first()
                onnxDetectionEngine.setConfiguredArtifact(
                    artifacts.detectionModelPath?.let(::File)
                )
                execuTorchAdvisoryEngine.setConfiguredArtifacts(
                    modelFile = artifacts.advisoryModelPath?.let(::File),
                    tokenizerFile = artifacts.advisoryTokenizerPath?.let(::File)
                )
            }
            runCatching { csvLogger.processPendingOutboxEvents() }
            runCatching {
                val referenced = inspectionRepository.getReferencedImageUris()
                imageStorageManager.cleanupOrphanedAndEmptyFiles(referenced)
            }
        }
    }
}

```


# FILE: source/app/src/main/java/com/example/data/local/preferences/SettingsRepositoryImpl.kt

```kotlin
package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.repository.SettingsRepository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.pcbSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bharat_pcb_settings"
)

data class StoredModelArtifacts(
    val detectionModelPath: String? = null,
    val advisoryModelPath: String? = null,
    val advisoryTokenizerPath: String? = null
)

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val themeMode: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        runCatching { AppThemeMode.valueOf(raw) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    override val policyConfig: Flow<InspectionPolicyConfig> = dataStore.data.map { prefs ->
        val defaults = InspectionPolicyConfig()
        InspectionPolicyConfig(
            profileName = prefs[KEY_POLICY_PROFILE] ?: defaults.profileName,
            policyVersion = prefs[KEY_POLICY_VERSION] ?: defaults.policyVersion,
            rejectOnCritical = prefs[KEY_REJECT_ON_CRITICAL] ?: defaults.rejectOnCritical,
            reviewOnMedium = prefs[KEY_REVIEW_ON_MEDIUM] ?: defaults.reviewOnMedium,
            reviewOnLow = prefs[KEY_REVIEW_ON_LOW] ?: defaults.reviewOnLow,
            reviewOnLowClarityZeroDefects = prefs[KEY_REVIEW_ON_LOW_CLARITY]
                ?: defaults.reviewOnLowClarityZeroDefects,
            minImageWidth = (prefs[KEY_MIN_IMAGE_WIDTH] ?: defaults.minImageWidth).coerceAtLeast(64),
            minImageHeight = (prefs[KEY_MIN_IMAGE_HEIGHT] ?: defaults.minImageHeight).coerceAtLeast(64)
        )
    }

    val storedModelArtifacts: Flow<StoredModelArtifacts> = dataStore.data.map { prefs ->
        StoredModelArtifacts(
            detectionModelPath = prefs[KEY_DETECTION_MODEL_PATH]?.takeIf { it.isNotBlank() },
            advisoryModelPath = prefs[KEY_ADVISORY_MODEL_PATH]?.takeIf { it.isNotBlank() },
            advisoryTokenizerPath = prefs[KEY_ADVISORY_TOKENIZER_PATH]?.takeIf { it.isNotBlank() }
        )
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    override suspend fun updatePolicyConfig(config: InspectionPolicyConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_POLICY_PROFILE] = config.profileName.ifBlank {
                InspectionPolicyConfig.DEFAULT_PROFILE_NAME
            }
            prefs[KEY_POLICY_VERSION] = config.policyVersion.ifBlank {
                InspectionPolicyConfig.DEFAULT_POLICY_VERSION
            }
            prefs[KEY_REJECT_ON_CRITICAL] = config.rejectOnCritical
            prefs[KEY_REVIEW_ON_MEDIUM] = config.reviewOnMedium
            prefs[KEY_REVIEW_ON_LOW] = config.reviewOnLow
            prefs[KEY_REVIEW_ON_LOW_CLARITY] = config.reviewOnLowClarityZeroDefects
            prefs[KEY_MIN_IMAGE_WIDTH] = config.minImageWidth.coerceAtLeast(64)
            prefs[KEY_MIN_IMAGE_HEIGHT] = config.minImageHeight.coerceAtLeast(64)
        }
    }

    override suspend fun resetPolicyDefaults() {
        updatePolicyConfig(InspectionPolicyConfig())
    }

    suspend fun setDetectionModelPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(KEY_DETECTION_MODEL_PATH)
            else prefs[KEY_DETECTION_MODEL_PATH] = path
        }
    }

    suspend fun setAdvisoryModelPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(KEY_ADVISORY_MODEL_PATH)
            else prefs[KEY_ADVISORY_MODEL_PATH] = path
        }
    }

    suspend fun setAdvisoryTokenizerPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(KEY_ADVISORY_TOKENIZER_PATH)
            else prefs[KEY_ADVISORY_TOKENIZER_PATH] = path
        }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_POLICY_PROFILE = stringPreferencesKey("policy_profile")
        private val KEY_POLICY_VERSION = stringPreferencesKey("policy_version")
        private val KEY_REJECT_ON_CRITICAL = booleanPreferencesKey("reject_on_critical")
        private val KEY_REVIEW_ON_MEDIUM = booleanPreferencesKey("review_on_medium")
        private val KEY_REVIEW_ON_LOW = booleanPreferencesKey("review_on_low")
        private val KEY_REVIEW_ON_LOW_CLARITY = booleanPreferencesKey("review_on_low_clarity")
        private val KEY_MIN_IMAGE_WIDTH = intPreferencesKey("min_image_width")
        private val KEY_MIN_IMAGE_HEIGHT = intPreferencesKey("min_image_height")
        private val KEY_DETECTION_MODEL_PATH = stringPreferencesKey("detection_model_path")
        private val KEY_ADVISORY_MODEL_PATH = stringPreferencesKey("advisory_model_path")
        private val KEY_ADVISORY_TOKENIZER_PATH = stringPreferencesKey("advisory_tokenizer_path")
    }
}

```


# FILE: source/app/src/main/java/com/example/domain/model/DomainModels.kt

```kotlin
package com.example.domain.model

import kotlinx.serialization.Serializable

enum class InspectionDisposition {
    PASS,
    REVIEW,
    REJECT,
    INCOMPLETE
}

enum class DefectSeverity {
    CRITICAL,
    MEDIUM,
    LOW,
    MANUAL_REVIEW,
    REQUIRES_DATASET_VERIFICATION
}

enum class PcbImageSource {
    CAMERA,
    GALLERY,
    SAMPLE
}

enum class ModelRuntimeState {
    NOT_LOADED,
    LOADING,
    READY,
    ERROR,
    UNLOADED
}

enum class ModelSlot {
    PCB_DETECTION,
    AI_ADVISORY
}

enum class AdvisoryAvailability {
    AVAILABLE,
    UNAVAILABLE,
    NOT_REQUESTED
}

enum class DetectionErrorType {
    MODEL_NOT_LOADED,
    MODEL_UNLOADED,
    SESSION_INIT_FAILED,
    INVALID_INPUT_TENSOR,
    RUNTIME_EXECUTION_FAILED,
    OUTPUT_SHAPE_MISMATCH,
    DECODER_FAILED,
    RESOURCE_ALLOCATION_FAILED,
    UNSUPPORTED_REAL_IMAGE_FALLBACK
}

@Serializable
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    fun clamped(): BoundingBox {
        val cx = x.coerceIn(0f, 1f)
        val cy = y.coerceIn(0f, 1f)
        val cw = width.coerceIn(0f, 1f - cx)
        val ch = height.coerceIn(0f, 1f - cy)
        return BoundingBox(cx, cy, cw, ch)
    }
}

@Serializable
data class DetectedDefect(
    val defectId: String,
    val classIndex: Int,
    val rawClassName: String,
    val canonicalClassName: String,
    val confidence: Float,
    val severity: DefectSeverity,
    val requiresManualReview: Boolean,
    val boundingBox: BoundingBox,
    val code: String? = null,
    val description: String? = null
)

@Serializable
data class InspectionPolicyConfig(
    val profileName: String = DEFAULT_PROFILE_NAME,
    val policyVersion: String = DEFAULT_POLICY_VERSION,
    val rejectOnCritical: Boolean = true,
    val reviewOnMedium: Boolean = true,
    val reviewOnLow: Boolean = false,
    val reviewOnLowClarityZeroDefects: Boolean = true,
    val minImageWidth: Int = 256,
    val minImageHeight: Int = 256
) {
    fun effectiveThresholdsSummary(): String {
        return "critical=${if (rejectOnCritical) "REJECT" else "REVIEW"}, " +
            "medium=${if (reviewOnMedium) "REVIEW" else "PASS"}, " +
            "low=${if (reviewOnLow) "REVIEW" else "PASS"}, " +
            "lowClarityGate=REVIEW(always-guarded), " +
            "minDims=${minImageWidth}x${minImageHeight}"
    }

    companion object {
        const val DEFAULT_PROFILE_NAME = "Configurable Screening Profile (IPC-A-610 Class 2 Baseline)"
        const val DEFAULT_POLICY_VERSION = "1.0.0-screening"
    }
}

@Serializable
data class ModelExecutionProvenance(
    val inferenceEngine: String,
    val modelIdentity: String,
    val modelSha256: String,
    val modelContractVersion: String,
    val preprocessingVersion: String,
    val decoderVersion: String,
    val classMappingVersion: String,
    val runtimeVersion: String
) {
    companion object {
        fun unavailableProduction(runtimeVersion: String): ModelExecutionProvenance =
            ModelExecutionProvenance(
                inferenceEngine = "OnnxPcbDetectionEngine",
                modelIdentity = "NONE",
                modelSha256 = "NONE",
                modelContractVersion = "UNVERIFIED",
                preprocessingVersion = "yolo-letterbox-v1",
                decoderVersion = "yolo-nms-v1",
                classMappingVersion = "pcb-11-v1",
                runtimeVersion = runtimeVersion
            )
    }
}

@Serializable
data class InspectionLatencyMetrics(
    val imagePreprocessingMs: Long? = null,
    val yoloInferenceMs: Long? = null,
    val outputDecodingNmsMs: Long? = null,
    val policyEvaluationMs: Long? = null,
    val llmAdvisoryMs: Long? = null,
    val totalDurationMs: Long? = null
)

@Serializable
data class AiAdvisory(
    val availability: AdvisoryAvailability,
    val isDemonstration: Boolean,
    val severityClassification: String? = null,
    val probableRootCause: String? = null,
    val recommendedOperatorAction: String? = null,
    val relevantStandard: String? = null,
    val suggestedDisposition: InspectionDisposition? = null,
    val limitations: String? = null,
    val unavailableReason: String? = null,
    val advisoryLatencyMs: Long? = null,
    val provenanceSummary: String? = null
)

data class ImageValidationResult(
    val isValid: Boolean,
    val isProcessable: Boolean,
    val width: Int,
    val height: Int,
    val clarityScore: Double?,
    val lowClarityRisk: Boolean,
    val orientationDegrees: Int = 0,
    val errorMessage: String? = null
)

data class PcbImageInput(
    val sourceUri: String,
    val sourceType: PcbImageSource,
    val boardName: String,
    val validation: ImageValidationResult,
    val sampleScenarioHint: String? = null
)

sealed interface DetectionOutcome {
    val latencyMetrics: InspectionLatencyMetrics
    val provenance: ModelExecutionProvenance

    data class Success(
        val detections: List<DetectedDefect>,
        override val latencyMetrics: InspectionLatencyMetrics,
        override val provenance: ModelExecutionProvenance,
        val isDemonstration: Boolean
    ) : DetectionOutcome

    data class Failure(
        val errorType: DetectionErrorType,
        val errorMessage: String,
        override val latencyMetrics: InspectionLatencyMetrics,
        override val provenance: ModelExecutionProvenance
    ) : DetectionOutcome
}

data class PolicyEvaluationResult(
    val officialDisposition: InspectionDisposition,
    val rationale: String,
    val evaluatedDefects: List<DetectedDefect>,
    val policyProfile: String,
    val policyVersion: String,
    val configSnapshot: InspectionPolicyConfig,
    val effectiveThresholdsSummary: String
)

data class ModelInfo(
    val slot: ModelSlot,
    val displayName: String,
    val runtimeLabel: String,
    val state: ModelRuntimeState,
    val artifactPath: String? = null,
    val tokenizerPath: String? = null,
    val sha256: String? = null,
    val contractVersion: String? = null,
    val runtimeVersion: String? = null,
    val detailMessage: String,
    val isDemonstrationOnly: Boolean = false
)

data class SamplePcbPreset(
    val id: String,
    val boardName: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val orientationLabel: String,
    val scenarioHint: String,
    val drawableName: String
)

data class InspectionRecord(
    val inspectionId: String,
    val timestampEpochMs: Long,
    val boardName: String,
    val sourceType: PcbImageSource,
    val imageUri: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val lowClarityRisk: Boolean,
    val clarityScore: Double?,
    val officialDisposition: InspectionDisposition,
    val policyProfile: String,
    val policyVersion: String,
    val policyConfigSnapshot: InspectionPolicyConfig,
    val effectiveThresholdsSummary: String,
    val policyRationale: String,
    val defects: List<DetectedDefect>,
    val totalDefects: Int,
    val isDemonstration: Boolean,
    val provenance: ModelExecutionProvenance,
    val advisory: AiAdvisory,
    val latencyMetrics: InspectionLatencyMetrics
)

```


# FILE: source/app/src/main/java/com/example/domain/policy/InspectionPolicyEngine.kt

```kotlin
package com.example.domain.policy

import com.example.domain.model.DefectSeverity
import com.example.domain.model.DetectedDefect
import com.example.domain.model.DetectionOutcome
import com.example.domain.model.ImageValidationResult
import com.example.domain.model.InspectionDisposition
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.model.PolicyEvaluationResult

/**
 * Pure Kotlin deterministic screening policy engine.
 *
 * Enforces all mandatory safety invariants independently of CV confidence and LLM output.
 */
class InspectionPolicyEngine {

    fun evaluate(
        imageValidation: ImageValidationResult,
        detectionOutcome: DetectionOutcome,
        config: InspectionPolicyConfig
    ): PolicyEvaluationResult {
        val thresholdSummary = config.effectiveThresholdsSummary()

        // 1. Unprocessable image guard (corrupt, decode failure, or below minimum dimensions)
        if (!imageValidation.isProcessable || !imageValidation.isValid ||
            imageValidation.width < config.minImageWidth ||
            imageValidation.height < config.minImageHeight
        ) {
            val reason = imageValidation.errorMessage
                ?: "Image is unprocessable or below minimum dimensions (${imageValidation.width}x${imageValidation.height} < ${config.minImageWidth}x${config.minImageHeight})."
            return PolicyEvaluationResult(
                officialDisposition = InspectionDisposition.INCOMPLETE,
                rationale = "INCOMPLETE: $reason Inspection cannot return PASS without a valid optical frame.",
                evaluatedDefects = emptyList(),
                policyProfile = config.profileName,
                policyVersion = config.policyVersion,
                configSnapshot = config,
                effectiveThresholdsSummary = thresholdSummary
            )
        }

        // 2. Inference failure / unloaded production model guard
        if (detectionOutcome is DetectionOutcome.Failure) {
            return PolicyEvaluationResult(
                officialDisposition = InspectionDisposition.INCOMPLETE,
                rationale = "INCOMPLETE (${detectionOutcome.errorType.name}): ${detectionOutcome.errorMessage} Production or runtime failure can never yield PASS.",
                evaluatedDefects = emptyList(),
                policyProfile = config.profileName,
                policyVersion = config.policyVersion,
                configSnapshot = config,
                effectiveThresholdsSummary = thresholdSummary
            )
        }

        val success = detectionOutcome as DetectionOutcome.Success
        // 3. Resolve every defect strictly from normalized class name (never index, never confidence)
        val normalizedDefects = success.detections.map { rawDefect ->
            val taxonomy = DefectTaxonomy.resolve(rawDefect.rawClassName)
            rawDefect.copy(
                canonicalClassName = taxonomy.canonicalLabel,
                severity = taxonomy.defaultSeverity,
                requiresManualReview = taxonomy.requiresManualReview || !taxonomy.isKnownClass,
                code = rawDefect.code ?: taxonomy.code,
                description = rawDefect.description ?: taxonomy.description,
                boundingBox = rawDefect.boundingBox.clamped()
            )
        }

        val totalCount = normalizedDefects.size
        val criticalCount = normalizedDefects.count { it.severity == DefectSeverity.CRITICAL }
        val mediumCount = normalizedDefects.count { it.severity == DefectSeverity.MEDIUM }
        val lowCount = normalizedDefects.count { it.severity == DefectSeverity.LOW }
        val manualReviewCount = normalizedDefects.count {
            it.severity == DefectSeverity.MANUAL_REVIEW ||
                it.severity == DefectSeverity.REQUIRES_DATASET_VERIFICATION ||
                it.requiresManualReview
        }
        val unverifiedCopperCount = normalizedDefects.count {
            it.severity == DefectSeverity.REQUIRES_DATASET_VERIFICATION
        }

        val clarityFlag = imageValidation.lowClarityRisk
        val classSummary = normalizedDefects
            .groupingBy { it.canonicalClassName }
            .eachCount()
            .entries
            .joinToString(", ") { "${it.key} (${it.value})" }

        // 4. Critical defects never yield PASS
        if (criticalCount > 0) {
            val disposition = if (config.rejectOnCritical) {
                InspectionDisposition.REJECT
            } else {
                InspectionDisposition.REVIEW
            }
            val clarityNote = if (clarityFlag) " Low image clarity risk was also detected." else ""
            val actionNote = if (config.rejectOnCritical) {
                "Auto-reject policy triggered by $criticalCount critical defect(s)."
            } else {
                "Critical auto-rejection is disabled; escalated to REVIEW for mandatory operator sign-off (never PASS)."
            }
            return PolicyEvaluationResult(
                officialDisposition = disposition,
                rationale = "${disposition.name}: Detected $totalCount total defect(s) [$classSummary], including $criticalCount critical defect(s). $actionNote$clarityNote",
                evaluatedDefects = normalizedDefects,
                policyProfile = config.profileName,
                policyVersion = config.policyVersion,
                configSnapshot = config,
                effectiveThresholdsSummary = thresholdSummary
            )
        }

        // 5. Undocumented / unknown / unverified dataset classes require manual review
        val undocumentedOrUnknownCount = normalizedDefects.count {
            it.severity == DefectSeverity.MANUAL_REVIEW ||
                it.severity == DefectSeverity.REQUIRES_DATASET_VERIFICATION
        }
        if (undocumentedOrUnknownCount > 0) {
            val copperNote = if (unverifiedCopperCount > 0) {
                " Includes $unverifiedCopperCount 'Copper' detection(s) requiring dataset verification."
            } else {
                ""
            }
            val clarityNote = if (clarityFlag) " Low image clarity risk also flagged." else ""
            return PolicyEvaluationResult(
                officialDisposition = InspectionDisposition.REVIEW,
                rationale = "REVIEW: Detected $totalCount total defect(s) [$classSummary] with $undocumentedOrUnknownCount undocumented, unknown, or dataset-unverified class(es) requiring manual review.$copperNote$clarityNote",
                evaluatedDefects = normalizedDefects,
                policyProfile = config.profileName,
                policyVersion = config.policyVersion,
                configSnapshot = config,
                effectiveThresholdsSummary = thresholdSummary
            )
        }

        // 6. Medium severity gating
        if (mediumCount > 0 && config.reviewOnMedium) {
            val clarityNote = if (clarityFlag) " Low image clarity risk also flagged." else ""
            return PolicyEvaluationResult(
                officialDisposition = InspectionDisposition.REVIEW,
                rationale = "REVIEW: Detected $totalCount total defect(s) [$classSummary], including $mediumCount medium-severity defect(s) requiring operator review.$clarityNote",
                evaluatedDefects = normalizedDefects,
                policyProfile = config.profileName,
                policyVersion = config.policyVersion,
                configSnapshot = config,
                effectiveThresholdsSummary = thresholdSummary
            )
        }

        // 7. Low severity gating
        if (lowCount > 0 && config.reviewOnLow) {
            val clarityNote = if (clarityFlag) " Low image clarity risk also flagged." else ""
            return PolicyEvaluationResult(
                officialDisposition = InspectionDisposition.REVIEW,
                rationale = "REVIEW: Detected $totalCount total defect(s) [$classSummary] with low-severity review enabled.$clarityNote",
                evaluatedDefects = normalizedDefects,
                policyProfile = config.profileName,
                policyVersion = config.policyVersion,
                configSnapshot = config,
                effectiveThresholdsSummary = thresholdSummary
            )
        }

        // 8. Poor image clarity gate (zero detections + poor clarity NEVER yields PASS)
        if (clarityFlag) {
            val gateNote = if (config.reviewOnLowClarityZeroDefects) {
                "Low image clarity review gate triggered."
            } else {
                "Low-clarity toggle is off, but policy safety invariant prevents PASS when zero detections occur on poor-clarity frames."
            }
            val countPhrase = if (totalCount == 0) {
                "0 localized defects"
            } else {
                "$totalCount tolerated defect(s) [$classSummary]"
            }
            return PolicyEvaluationResult(
                officialDisposition = InspectionDisposition.REVIEW,
                rationale = "REVIEW: Observed $countPhrase on a frame flagged with low clarity risk. $gateNote",
                evaluatedDefects = normalizedDefects,
                policyProfile = config.profileName,
                policyVersion = config.policyVersion,
                configSnapshot = config,
                effectiveThresholdsSummary = thresholdSummary
            )
        }

        // 9. Adequate image clarity with tolerated low/medium-off defects -> PASS with exact count!
        if (totalCount > 0) {
            return PolicyEvaluationResult(
                officialDisposition = InspectionDisposition.PASS,
                rationale = "PASS (${config.profileName}): Detected $totalCount localized defect(s) [$classSummary]; none exceeded active rejection or review thresholds at adequate image clarity (${imageValidation.width}x${imageValidation.height}). Screening profile result only; uncertified without verified standard acceptance criteria.",
                evaluatedDefects = normalizedDefects,
                policyProfile = config.profileName,
                policyVersion = config.policyVersion,
                configSnapshot = config,
                effectiveThresholdsSummary = thresholdSummary
            )
        }

        // 10. Adequate image clarity + zero localized defects -> PASS (screening profile)
        return PolicyEvaluationResult(
            officialDisposition = InspectionDisposition.PASS,
            rationale = "PASS (${config.profileName}): 0 defects localized across ${imageValidation.width}x${imageValidation.height} frame with adequate image clarity. Screening profile result only; uncertified without verified standard acceptance criteria.",
            evaluatedDefects = emptyList(),
            policyProfile = config.profileName,
            policyVersion = config.policyVersion,
            configSnapshot = config,
            effectiveThresholdsSummary = thresholdSummary
        )
    }
}

```


# FILE: source/app/src/main/java/com/example/domain/repository/DomainInterfaces.kt

```kotlin
package com.example.domain.repository

import android.net.Uri
import com.example.domain.model.ImageValidationResult
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.model.InspectionRecord
import com.example.domain.model.ModelInfo
import com.example.domain.model.ModelSlot
import com.example.domain.model.SamplePcbPreset
import com.example.inference.cv.ModelOperationResult
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InspectionRepository {
    fun observeAllRecords(): Flow<List<InspectionRecord>>
    fun observeRecordById(inspectionId: String): Flow<InspectionRecord?>
    suspend fun getRecordById(inspectionId: String): InspectionRecord?
    suspend fun saveInspectionAtomically(record: InspectionRecord)
    suspend fun clearAllHistory()
    suspend fun getReferencedImageUris(): Set<String>
    suspend fun getPendingOutboxCount(): Int
    suspend fun getConfirmedCsvLedgerCount(): Int
}

interface SettingsRepository {
    val themeMode: Flow<AppThemeMode>
    val policyConfig: Flow<InspectionPolicyConfig>
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun updatePolicyConfig(config: InspectionPolicyConfig)
    suspend fun resetPolicyDefaults()
}

interface ImageStorageManager {
    suspend fun createCameraCaptureDestination(): Pair<String, Uri>
    suspend fun importPickedImageToAppStorage(sourceUri: Uri): Result<String>
    suspend fun materializeSamplePreset(preset: SamplePcbPreset): Result<String>
    suspend fun validateAndAssessClarity(
        imageUriString: String,
        minWidth: Int,
        minHeight: Int
    ): ImageValidationResult
    suspend fun cleanupOrphanedAndEmptyFiles(protectedUris: Set<String>): Int
    suspend fun deleteAllUnreferencedCaptures(protectedUris: Set<String>): Int
    suspend fun deleteUnreferencedCaptureIfEmpty(uriString: String?)
    suspend fun getImageStorageBytes(): Long
}

interface ModelManager {
    val detectionModelInfo: StateFlow<ModelInfo>
    val advisoryModelInfo: StateFlow<ModelInfo>

    suspend fun importDetectionModel(sourceUri: Uri): ModelOperationResult
    suspend fun loadDetectionModel(): ModelOperationResult
    suspend fun unloadDetectionModel(): ModelOperationResult

    suspend fun importAdvisoryModel(sourceUri: Uri): ModelOperationResult
    suspend fun importAdvisoryTokenizer(sourceUri: Uri): ModelOperationResult
    suspend fun loadAdvisoryModel(): ModelOperationResult
    suspend fun unloadAdvisoryModel(): ModelOperationResult

    fun inspectModelMetadata(slot: ModelSlot): String
}

interface InspectionCsvLogger {
    suspend fun processPendingOutboxEvents(): CsvAuditStatus
    suspend fun regenerateAuditCsvFromRoom(): Result<String>
    suspend fun clearAuditLogAndLedger()
    suspend fun getAuditFileSizeBytes(): Long
    fun getAuditFilePath(): String
}

data class CsvAuditStatus(
    val pendingCount: Int,
    val confirmedCount: Int,
    val failedCount: Int,
    val lastError: String? = null
)

interface CsvExportManager {
    fun serializeRecordsToCsv(records: List<InspectionRecord>): String
    fun suggestedFileNameForAll(): String
    fun suggestedFileNameForRecord(inspectionId: String): String
}

interface StorageAccessManager {
    suspend fun writeCsvToSafUri(uri: Uri, csvContent: String): Result<Unit>
    suspend fun prepareShareableCsvUri(fileName: String, csvContent: String): Result<Uri>
}

```


# FILE: source/app/src/main/java/com/example/domain/usecase/RunInspectionUseCase.kt

```kotlin
package com.example.domain.usecase

import com.example.domain.model.AdvisoryAvailability
import com.example.domain.model.AiAdvisory
import com.example.domain.model.DetectionErrorType
import com.example.domain.model.DetectionOutcome
import com.example.domain.model.InspectionDisposition
import com.example.domain.model.InspectionLatencyMetrics
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.model.InspectionRecord
import com.example.domain.model.ModelExecutionProvenance
import com.example.domain.model.PcbImageInput
import com.example.domain.model.PcbImageSource
import com.example.domain.policy.InspectionPolicyEngine
import com.example.domain.repository.ImageStorageManager
import com.example.domain.repository.InspectionCsvLogger
import com.example.domain.repository.InspectionRepository
import com.example.domain.repository.SettingsRepository
import com.example.inference.cv.PcbDetectionEngine
import com.example.inference.demo.DemoAdvisoryEngine
import com.example.inference.demo.DemoPcbDetectionEngine
import com.example.inference.llm.AdvisoryEngine
import com.example.inference.llm.AdvisoryRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RunInspectionUseCase(
    private val imageStorageManager: ImageStorageManager,
    private val policyEngine: InspectionPolicyEngine,
    private val productionDetectionEngine: PcbDetectionEngine,
    private val demoDetectionEngine: DemoPcbDetectionEngine,
    private val productionAdvisoryEngine: AdvisoryEngine,
    private val demoAdvisoryEngine: DemoAdvisoryEngine,
    private val inspectionRepository: InspectionRepository,
    private val settingsRepository: SettingsRepository,
    private val csvLogger: InspectionCsvLogger,
    private val applicationScope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun execute(
        imageUriString: String,
        sourceType: PcbImageSource,
        boardName: String,
        sampleScenarioHint: String? = null,
        onStageProgress: (String) -> Unit = {}
    ): InspectionRecord = withContext(defaultDispatcher) {
        val totalStartMs = System.currentTimeMillis()
        val policyConfig: InspectionPolicyConfig = settingsRepository.policyConfig.first()

        onStageProgress("Validating PCB optical frame")
        val validation = imageStorageManager.validateAndAssessClarity(
            imageUriString = imageUriString,
            minWidth = policyConfig.minImageWidth,
            minHeight = policyConfig.minImageHeight
        )

        val input = PcbImageInput(
            sourceUri = imageUriString,
            sourceType = sourceType,
            boardName = boardName.ifBlank { "PCB Assembly" },
            validation = validation,
            sampleScenarioHint = if (sourceType == PcbImageSource.SAMPLE) sampleScenarioHint else null
        )

        val isSampleDemo = sourceType == PcbImageSource.SAMPLE
        onStageProgress(
            if (isSampleDemo) "Running demonstration PCB simulation" else "Running on-device YOLOv8n detection"
        )

        val detectionOutcome: DetectionOutcome = if (!validation.isProcessable || !validation.isValid) {
            DetectionOutcome.Failure(
                errorType = DetectionErrorType.INVALID_INPUT_TENSOR,
                errorMessage = validation.errorMessage ?: "Image is not processable.",
                latencyMetrics = InspectionLatencyMetrics(),
                provenance = if (isSampleDemo) {
                    ModelExecutionProvenance(
                        inferenceEngine = demoDetectionEngine.engineLabel,
                        modelIdentity = "prototype-sample-simulator",
                        modelSha256 = "DEMO-SAMPLE-ONLY",
                        modelContractVersion = "demo-sample-v1",
                        preprocessingVersion = "demo-noop-v1",
                        decoderVersion = "demo-preset-v1",
                        classMappingVersion = "pcb-11-v1",
                        runtimeVersion = "demo-1.0"
                    )
                } else {
                    ModelExecutionProvenance.unavailableProduction("uninitialized")
                }
            )
        } else if (isSampleDemo) {
            // Sample presets explicitly execute through the demonstration engine
            demoDetectionEngine.detect(input)
        } else {
            // Real CAMERA/GALLERY inputs MUST use the production detector; never fall back to demo
            productionDetectionEngine.detect(input)
        }

        onStageProgress("Evaluating deterministic screening policy")
        val policyStartMs = System.currentTimeMillis()
        val policyResult = policyEngine.evaluate(
            imageValidation = validation,
            detectionOutcome = detectionOutcome,
            config = policyConfig
        )
        val policyEvalMs = (System.currentTimeMillis() - policyStartMs).coerceAtLeast(1L)

        // Generate optional advisory (never overrides policy, never fails inspection)
        onStageProgress("Checking AI technical advisory")
        val advisoryStartMs = System.currentTimeMillis()
        val advisory: AiAdvisory = if (policyResult.officialDisposition == InspectionDisposition.INCOMPLETE && !isSampleDemo) {
            AiAdvisory(
                availability = AdvisoryAvailability.UNAVAILABLE,
                isDemonstration = false,
                unavailableReason = "Technical advisory skipped because inspection is INCOMPLETE."
            )
        } else {
            val engineToUse: AdvisoryEngine = if (isSampleDemo) demoAdvisoryEngine else productionAdvisoryEngine
            val request = AdvisoryRequest(
                inspectionId = "pending",
                boardName = input.boardName,
                officialDisposition = policyResult.officialDisposition,
                policyRationale = policyResult.rationale,
                defects = policyResult.evaluatedDefects,
                imageWidth = validation.width,
                imageHeight = validation.height,
                lowClarityRisk = validation.lowClarityRisk,
                isDemonstration = isSampleDemo
            )
            runCatching { engineToUse.generateAdvisory(request) }.getOrElse { err ->
                AiAdvisory(
                    availability = AdvisoryAvailability.UNAVAILABLE,
                    isDemonstration = isSampleDemo,
                    unavailableReason = "Advisory failed safely: ${err.message ?: "unknown error"}"
                )
            }
        }
        val advisoryLatencyMs = advisory.advisoryLatencyMs
            ?: if (advisory.availability == AdvisoryAvailability.AVAILABLE) {
                (System.currentTimeMillis() - advisoryStartMs).coerceAtLeast(1L)
            } else {
                null
            }

        val totalMs = (System.currentTimeMillis() - totalStartMs).coerceAtLeast(1L)
        val combinedLatency = InspectionLatencyMetrics(
            imagePreprocessingMs = detectionOutcome.latencyMetrics.imagePreprocessingMs,
            yoloInferenceMs = detectionOutcome.latencyMetrics.yoloInferenceMs,
            outputDecodingNmsMs = detectionOutcome.latencyMetrics.outputDecodingNmsMs,
            policyEvaluationMs = policyEvalMs,
            llmAdvisoryMs = advisoryLatencyMs,
            totalDurationMs = totalMs
        )

        val inspectionId = generateInspectionId()
        val record = InspectionRecord(
            inspectionId = inspectionId,
            timestampEpochMs = System.currentTimeMillis(),
            boardName = input.boardName,
            sourceType = sourceType,
            imageUri = imageUriString,
            imageWidth = validation.width,
            imageHeight = validation.height,
            lowClarityRisk = validation.lowClarityRisk,
            clarityScore = validation.clarityScore,
            officialDisposition = policyResult.officialDisposition,
            policyProfile = policyResult.policyProfile,
            policyVersion = policyResult.policyVersion,
            policyConfigSnapshot = policyResult.configSnapshot,
            effectiveThresholdsSummary = policyResult.effectiveThresholdsSummary,
            policyRationale = policyResult.rationale,
            defects = policyResult.evaluatedDefects,
            totalDefects = policyResult.evaluatedDefects.size,
            isDemonstration = isSampleDemo,
            provenance = detectionOutcome.provenance,
            advisory = advisory,
            latencyMetrics = combinedLatency
        )

        onStageProgress("Saving inspection record")
        inspectionRepository.saveInspectionAtomically(record)

        // Dispatch CSV audit outbox non-blockingly; CSV failure never fails inspection completion
        applicationScope.launch(defaultDispatcher) {
            runCatching { csvLogger.processPendingOutboxEvents() }
        }

        record
    }

    private fun generateInspectionId(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val suffix = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        return "PCB-$stamp-$suffix"
    }
}

```


# FILE: source/app/src/main/java/com/example/inference/ModelManagerImpl.kt

```kotlin
package com.example.inference

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.preferences.SettingsRepositoryImpl
import com.example.domain.model.ModelInfo
import com.example.domain.model.ModelSlot
import com.example.domain.repository.ModelManager
import com.example.inference.cv.ModelOperationResult
import com.example.inference.cv.OnnxPcbDetectionEngine
import com.example.inference.llm.ExecuTorchAdvisoryEngine
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Manages atomic staging, validation, SHA-256 verification, replacement, and unloading
 * of production ONNX and ExecuTorch model artifacts in app-private storage.
 */
class ModelManagerImpl(
    private val context: Context,
    private val settingsRepository: SettingsRepositoryImpl,
    private val onnxEngine: OnnxPcbDetectionEngine,
    private val execuTorchEngine: ExecuTorchAdvisoryEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ModelManager {

    override val detectionModelInfo: StateFlow<ModelInfo> = onnxEngine.modelInfo
    override val advisoryModelInfo: StateFlow<ModelInfo> = execuTorchEngine.modelInfo

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    private val stagingDir: File
        get() = File(modelsDir, "staging").apply { mkdirs() }

    override suspend fun importDetectionModel(sourceUri: Uri): ModelOperationResult =
        withContext(ioDispatcher) {
            val displayName = resolveDisplayName(sourceUri) ?: "yolov8n_pcb.onnx"
            if (!displayName.endsWith(".onnx", ignoreCase = true)) {
                return@withContext ModelOperationResult(
                    false,
                    "Invalid file '$displayName': expected a .onnx YOLOv8n artifact."
                )
            }

            val stagedFile = File(stagingDir, "stage_${UUID.randomUUID()}.onnx")
            try {
                copyUriToFile(sourceUri, stagedFile)
                val validation = onnxEngine.validateAndStageCandidate(stagedFile)
                if (validation.isFailure) {
                    return@withContext ModelOperationResult(
                        false,
                        "ONNX validation failed (previous model preserved): ${validation.exceptionOrNull()?.message}"
                    )
                }

                val targetFile = File(modelsDir, "active_pcb_detector.onnx")
                val backupFile = File(modelsDir, "backup_pcb_detector.onnx")
                if (targetFile.exists()) {
                    targetFile.copyTo(backupFile, overwrite = true)
                }

                check(stagedFile.copyTo(targetFile, overwrite = true).length() > 0L) {
                    "Failed to promote staged ONNX file."
                }
                val loadResult = onnxEngine.loadFromModelFile(targetFile)
                if (!loadResult.success) {
                    if (backupFile.exists()) {
                        backupFile.copyTo(targetFile, overwrite = true)
                        onnxEngine.loadFromModelFile(targetFile)
                    }
                    return@withContext loadResult
                }

                backupFile.delete()
                settingsRepository.setDetectionModelPath(targetFile.absolutePath)
                loadResult
            } catch (e: Throwable) {
                ModelOperationResult(false, "ONNX import failed: ${e.message ?: "unknown error"}")
            } finally {
                stagedFile.delete()
            }
        }

    override suspend fun loadDetectionModel(): ModelOperationResult {
        val artifacts = settingsRepository.storedModelArtifacts.first()
        val path = artifacts.detectionModelPath ?: return ModelOperationResult(
            false,
            "No imported ONNX model found. Import a .onnx file first."
        )
        return onnxEngine.loadFromModelFile(File(path))
    }

    override suspend fun unloadDetectionModel(): ModelOperationResult {
        onnxEngine.unload()
        return ModelOperationResult(true, "ONNX detection session unloaded.")
    }

    override suspend fun importAdvisoryModel(sourceUri: Uri): ModelOperationResult =
        withContext(ioDispatcher) {
            val displayName = resolveDisplayName(sourceUri) ?: "llama_3_2_1b.pte"
            if (!displayName.endsWith(".pte", ignoreCase = true)) {
                return@withContext ModelOperationResult(
                    false,
                    "Invalid file '$displayName': expected an ExecuTorch .pte model."
                )
            }
            val stagedFile = File(stagingDir, "stage_${UUID.randomUUID()}.pte")
            try {
                copyUriToFile(sourceUri, stagedFile)
                val targetFile = File(modelsDir, "active_llama_advisory.pte")
                stagedFile.copyTo(targetFile, overwrite = true)
                settingsRepository.setAdvisoryModelPath(targetFile.absolutePath)

                val artifacts = settingsRepository.storedModelArtifacts.first()
                val tokFile = artifacts.advisoryTokenizerPath?.let(::File)
                execuTorchEngine.setConfiguredArtifacts(targetFile, tokFile)
                if (tokFile != null && tokFile.exists()) {
                    execuTorchEngine.loadFromArtifacts(targetFile, tokFile)
                } else {
                    ModelOperationResult(true, "Imported $displayName. Import tokenizer next to load ExecuTorch.")
                }
            } catch (e: Throwable) {
                ModelOperationResult(false, "ExecuTorch .pte import failed: ${e.message ?: "unknown error"}")
            } finally {
                stagedFile.delete()
            }
        }

    override suspend fun importAdvisoryTokenizer(sourceUri: Uri): ModelOperationResult =
        withContext(ioDispatcher) {
            val displayName = resolveDisplayName(sourceUri) ?: "tokenizer.bin"
            val lower = displayName.lowercase()
            if (!lower.endsWith(".bin") && !lower.endsWith(".model") && !lower.endsWith(".json")) {
                return@withContext ModelOperationResult(
                    false,
                    "Invalid tokenizer '$displayName': expected .bin, .model, or .json."
                )
            }
            val stagedFile = File(stagingDir, "stage_tok_${UUID.randomUUID()}_$displayName")
            try {
                copyUriToFile(sourceUri, stagedFile)
                val ext = displayName.substringAfterLast('.', "bin")
                val targetFile = File(modelsDir, "active_tokenizer.$ext")
                stagedFile.copyTo(targetFile, overwrite = true)
                settingsRepository.setAdvisoryTokenizerPath(targetFile.absolutePath)

                val artifacts = settingsRepository.storedModelArtifacts.first()
                val modelFile = artifacts.advisoryModelPath?.let(::File)
                execuTorchEngine.setConfiguredArtifacts(modelFile, targetFile)
                if (modelFile != null && modelFile.exists()) {
                    execuTorchEngine.loadFromArtifacts(modelFile, targetFile)
                } else {
                    ModelOperationResult(true, "Imported tokenizer $displayName. Import .pte model next to load ExecuTorch.")
                }
            } catch (e: Throwable) {
                ModelOperationResult(false, "Tokenizer import failed: ${e.message ?: "unknown error"}")
            } finally {
                stagedFile.delete()
            }
        }

    override suspend fun loadAdvisoryModel(): ModelOperationResult {
        val artifacts = settingsRepository.storedModelArtifacts.first()
        val modelPath = artifacts.advisoryModelPath
        val tokPath = artifacts.advisoryTokenizerPath
        if (modelPath.isNullOrBlank() || tokPath.isNullOrBlank()) {
            return ModelOperationResult(false, "Both .pte model and tokenizer must be imported before loading.")
        }
        return execuTorchEngine.loadFromArtifacts(File(modelPath), File(tokPath))
    }

    override suspend fun unloadAdvisoryModel(): ModelOperationResult {
        execuTorchEngine.unload()
        return ModelOperationResult(true, "ExecuTorch advisory session unloaded.")
    }

    override fun inspectModelMetadata(slot: ModelSlot): String {
        return when (slot) {
            ModelSlot.PCB_DETECTION -> onnxEngine.inspectCurrentMetadataText()
            ModelSlot.AI_ADVISORY -> execuTorchEngine.inspectCurrentMetadataText()
        }
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        } ?: error("Cannot open selected model file stream.")
        require(target.exists() && target.length() > 0L) {
            "Imported file is empty."
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).name }
        }
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        } ?: uri.lastPathSegment
    }
}

```


# FILE: source/app/src/main/java/com/example/inference/cv/PcbDetectionEngine.kt

```kotlin
package com.example.inference.cv

import com.example.domain.model.DetectionOutcome
import com.example.domain.model.ModelInfo
import com.example.domain.model.PcbImageInput
import kotlinx.coroutines.flow.StateFlow

data class ModelOperationResult(
    val success: Boolean,
    val message: String
)

interface PcbDetectionEngine {
    val engineLabel: String
    val requiresBitmap: Boolean
    val isDemonstrationEngine: Boolean
    val modelInfo: StateFlow<ModelInfo>

    suspend fun initialize(): ModelOperationResult
    suspend fun detect(input: PcbImageInput): DetectionOutcome
    suspend fun unload()
    suspend fun close()
}

```
