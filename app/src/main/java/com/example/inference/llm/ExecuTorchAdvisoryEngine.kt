package com.example.inference.llm

import com.example.domain.model.AdvisoryAvailability
import com.example.domain.model.AiAdvisory
import com.example.domain.model.ModelInfo
import com.example.domain.model.ModelRuntimeState
import com.example.domain.model.ModelSlot
import com.example.inference.cv.ModelOperationResult
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule

/**
 * Production on-device Llama 3.2 1B Instruct advisory engine via ExecuTorch Android.
 *
 * Remains NOT_LOADED until compatible `.pte` and tokenizer files are imported and loaded.
 * Advisory generation is strictly read-only/explanatory and never alters policy dispositions.
 */
class ExecuTorchAdvisoryEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AdvisoryEngine {

    override val engineLabel: String = "ExecuTorchAdvisoryEngine (Llama 3.2 1B Instruct)"
    override val isDemonstrationEngine: Boolean = false

    private val inferenceMutex = Mutex()
    private var llmModule: LlmModule? = null
    private var activeModelPath: String? = null
    private var activeTokenizerPath: String? = null
    private var activeSha256: String? = null
    private val isGenerating = AtomicBoolean(false)

    private val _modelInfo = MutableStateFlow(initialNotLoadedInfo())
    override val modelInfo: StateFlow<ModelInfo> = _modelInfo.asStateFlow()

    override suspend fun initialize(): ModelOperationResult {
        val mPath = activeModelPath ?: _modelInfo.value.artifactPath
        val tPath = activeTokenizerPath ?: _modelInfo.value.tokenizerPath
        if (mPath.isNullOrBlank() || tPath.isNullOrBlank()) {
            return ModelOperationResult(false, "Both .pte model and tokenizer files must be imported first.")
        }
        return loadFromArtifacts(File(mPath), File(tPath))
    }

    suspend fun setConfiguredArtifacts(modelFile: File?, tokenizerFile: File?) {
        inferenceMutex.withLock {
            val mPath = modelFile?.takeIf { it.exists() && it.length() > 0L }?.absolutePath
            val tPath = tokenizerFile?.takeIf { it.exists() && it.length() > 0L }?.absolutePath
            activeModelPath = mPath
            activeTokenizerPath = tPath
            if (llmModule == null) {
                val detail = when {
                    mPath == null && tPath == null ->
                        "Not loaded. Import Llama 3.2 1B .pte and tokenizer files in Settings."
                    mPath == null ->
                        "Tokenizer staged (${File(tPath!!).name}); awaiting .pte model file."
                    tPath == null ->
                        "Model staged (${File(mPath).name}); awaiting tokenizer file."
                    else ->
                        "Artifacts staged (${File(mPath).name} + ${File(tPath).name}); tap Load to initialize ExecuTorch."
                }
                _modelInfo.value = _modelInfo.value.copy(
                    state = ModelRuntimeState.NOT_LOADED,
                    artifactPath = mPath,
                    tokenizerPath = tPath,
                    detailMessage = detail
                )
            }
        }
    }

    suspend fun loadFromArtifacts(modelFile: File, tokenizerFile: File): ModelOperationResult =
        inferenceMutex.withLock {
            withContext(ioDispatcher) {
                _modelInfo.value = _modelInfo.value.copy(
                    state = ModelRuntimeState.LOADING,
                    detailMessage = "Validating .pte + tokenizer and loading ExecuTorch runtime..."
                )
                try {
                    require(modelFile.exists() && modelFile.canRead() && modelFile.length() > 0L) {
                        "ExecuTorch .pte file is missing or unreadable."
                    }
                    require(tokenizerFile.exists() && tokenizerFile.canRead() && tokenizerFile.length() > 0L) {
                        "Tokenizer file is missing or unreadable."
                    }
                    validateArtifactExtensions(modelFile, tokenizerFile)

                    val sha256 = computeSha256(modelFile)
                    val module = LlmModule(
                        modelFile.absolutePath,
                        tokenizerFile.absolutePath,
                        0.2f
                    )
                    val loadStatus = module.load()
                    if (loadStatus != 0) {
                        runCatching { module.resetNative() }
                        error("ExecuTorch LlmModule.load() returned status code $loadStatus.")
                    }

                    // Release any prior loaded module only after replacement succeeds
                    runCatching { llmModule?.resetNative() }
                    llmModule = module
                    activeModelPath = modelFile.absolutePath
                    activeTokenizerPath = tokenizerFile.absolutePath
                    activeSha256 = sha256

                    _modelInfo.value = ModelInfo(
                        slot = ModelSlot.AI_ADVISORY,
                        displayName = "Llama 3.2 1B Instruct (${modelFile.name})",
                        runtimeLabel = "ExecuTorch Android $EXECUTORCH_RUNTIME_VERSION",
                        state = ModelRuntimeState.READY,
                        artifactPath = modelFile.absolutePath,
                        tokenizerPath = tokenizerFile.absolutePath,
                        sha256 = sha256,
                        contractVersion = AdvisoryPromptBuilder.PROMPT_VERSION,
                        runtimeVersion = EXECUTORCH_RUNTIME_VERSION,
                        detailMessage = "Ready • model=${modelFile.name} • tokenizer=${tokenizerFile.name}",
                        isDemonstrationOnly = false
                    )
                    ModelOperationResult(true, "ExecuTorch LLM runtime initialized.")
                } catch (e: Throwable) {
                    if (llmModule == null) {
                        _modelInfo.value = _modelInfo.value.copy(
                            state = ModelRuntimeState.ERROR,
                            artifactPath = modelFile.absolutePath,
                            tokenizerPath = tokenizerFile.absolutePath,
                            detailMessage = "ExecuTorch load failed: ${e.message ?: "unknown error"}"
                        )
                    }
                    ModelOperationResult(false, "ExecuTorch load failed: ${e.message ?: "unknown error"}")
                }
            }
        }

    override suspend fun generateAdvisory(request: AdvisoryRequest): AiAdvisory =
        inferenceMutex.withLock {
            withContext(ioDispatcher) {
                val module = llmModule
                if (module == null || _modelInfo.value.state != ModelRuntimeState.READY) {
                    return@withContext AiAdvisory(
                        availability = AdvisoryAvailability.UNAVAILABLE,
                        isDemonstration = false,
                        unavailableReason = "ExecuTorch LLM is ${_modelInfo.value.state.name}. Import and load Llama 3.2 1B .pte + tokenizer in Settings.",
                        provenanceSummary = currentProvenanceSummary()
                    )
                }

                val startMs = System.currentTimeMillis()
                val prompt = AdvisoryPromptBuilder.buildGroundedPrompt(request)
                val outputBuffer = StringBuilder()

                isGenerating.set(true)
                try {
                    module.generate(
                        prompt,
                        MAX_OUTPUT_SEQ_LEN,
                        object : LlmCallback {
                            override fun onResult(result: String) {
                                if (outputBuffer.length < 4096) {
                                    outputBuffer.append(result)
                                }
                            }

                            override fun onStats(stats: String) = Unit
                        }
                    )
                    val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    AdvisoryOutputParser.parseAndValidate(
                        rawText = outputBuffer.toString(),
                        officialDisposition = request.officialDisposition,
                        hasVerifiedReferences = request.verifiedReferenceExcerpts.isNotEmpty(),
                        isDemonstration = false,
                        latencyMs = latencyMs,
                        provenanceSummary = currentProvenanceSummary()
                    )
                } catch (c: CancellationException) {
                    runCatching { module.stop() }
                    throw c
                } catch (e: Throwable) {
                    AiAdvisory(
                        availability = AdvisoryAvailability.UNAVAILABLE,
                        isDemonstration = false,
                        unavailableReason = "ExecuTorch generation failed safely: ${e.message ?: "runtime error"}",
                        advisoryLatencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
                        provenanceSummary = currentProvenanceSummary()
                    )
                } finally {
                    isGenerating.set(false)
                }
            }
        }

    fun cancelActiveGeneration() {
        if (isGenerating.get()) {
            runCatching { llmModule?.stop() }
        }
    }

    override suspend fun unload() {
        cancelActiveGeneration()
        inferenceMutex.withLock {
            withContext(ioDispatcher) {
                runCatching { llmModule?.resetNative() }
                llmModule = null
                val hasBoth = !activeModelPath.isNullOrBlank() && !activeTokenizerPath.isNullOrBlank()
                _modelInfo.value = _modelInfo.value.copy(
                    state = if (hasBoth) ModelRuntimeState.UNLOADED else ModelRuntimeState.NOT_LOADED,
                    detailMessage = if (hasBoth) {
                        "Artifacts stored; ExecuTorch session unloaded to free RAM."
                    } else {
                        "Not loaded. Import Llama 3.2 1B .pte and tokenizer files in Settings."
                    }
                )
            }
        }
    }

    override suspend fun close() {
        unload()
    }

    fun inspectCurrentMetadataText(): String {
        val info = _modelInfo.value
        return buildString {
            appendLine("Slot: AI Technical Advisory (Llama 3.2 1B Instruct)")
            appendLine("State: ${info.state.name}")
            appendLine("Runtime: ${info.runtimeLabel}")
            appendLine("Model (.pte): ${info.artifactPath ?: "None"}")
            appendLine("Tokenizer: ${info.tokenizerPath ?: "None"}")
            appendLine("Model SHA-256: ${info.sha256 ?: "N/A"}")
            appendLine("Prompt Contract: ${info.contractVersion ?: AdvisoryPromptBuilder.PROMPT_VERSION}")
        }
    }

    private fun validateArtifactExtensions(modelFile: File, tokenizerFile: File) {
        require(modelFile.name.endsWith(".pte", ignoreCase = true)) {
            "Expected a .pte ExecuTorch program file, got '${modelFile.name}'."
        }
        val tokLower = tokenizerFile.name.lowercase()
        require(tokLower.endsWith(".bin") || tokLower.endsWith(".model") || tokLower.endsWith(".json")) {
            "Unsupported tokenizer extension '${tokenizerFile.name}'; expected .bin, .model, or .json."
        }
    }

    private fun currentProvenanceSummary(): String {
        return "ExecuTorch $EXECUTORCH_RUNTIME_VERSION • prompt=${AdvisoryPromptBuilder.PROMPT_VERSION} • sha256=${activeSha256?.take(12) ?: "NONE"}"
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun initialNotLoadedInfo(): ModelInfo = ModelInfo(
        slot = ModelSlot.AI_ADVISORY,
        displayName = "Llama 3.2 1B Instruct",
        runtimeLabel = "ExecuTorch Android $EXECUTORCH_RUNTIME_VERSION",
        state = ModelRuntimeState.NOT_LOADED,
        contractVersion = AdvisoryPromptBuilder.PROMPT_VERSION,
        runtimeVersion = EXECUTORCH_RUNTIME_VERSION,
        detailMessage = "Not loaded. Import Llama 3.2 1B .pte and tokenizer files in Settings.",
        isDemonstrationOnly = false
    )

    companion object {
        const val EXECUTORCH_RUNTIME_VERSION = "1.0.1"
        private const val MAX_OUTPUT_SEQ_LEN = 384
    }
}
