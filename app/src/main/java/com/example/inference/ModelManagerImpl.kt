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
