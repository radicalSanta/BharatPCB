package com.example.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.model.ModelInfo
import com.example.domain.model.ModelRuntimeState
import com.example.domain.model.ModelSlot
import com.example.presentation.components.BrandHeader
import com.example.presentation.components.ModelStateBadge
import com.example.presentation.components.formatBytesHuman
import com.example.presentation.viewmodel.SettingsUiState
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalPcbColors
import com.example.ui.theme.MonoTextStyles

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    suggestedAllCsvFilename: String,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onUpdatePolicyConfig: (InspectionPolicyConfig) -> Unit,
    onResetPolicyDefaults: () -> Unit,
    onImportDetectionModel: (Uri) -> Unit,
    onLoadDetectionModel: () -> Unit,
    onUnloadDetectionModel: () -> Unit,
    onImportTransistorModel: (Uri) -> Unit = {},
    onLoadTransistorModel: () -> Unit = {},
    onUnloadTransistorModel: () -> Unit = {},
    onImportAdvisoryModel: (Uri) -> Unit,
    onImportAdvisoryTokenizer: (Uri) -> Unit,
    onLoadAdvisoryModel: () -> Unit,
    onUnloadAdvisoryModel: () -> Unit,
    onInspectModelMetadata: (ModelSlot) -> Unit,
    onDismissMetadataDialog: () -> Unit,
    onDismissModelStatusBanner: () -> Unit,
    onReconcileCsvOutbox: () -> Unit,
    onExportAllCsvToSafUri: (Uri) -> Unit,
    onRequestClearHistory: () -> Unit,
    onDismissClearHistory: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onClearStorageMessage: () -> Unit
) {
    val colors = LocalPcbColors.current

    val onnxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportDetectionModel(uri) }

    val transistorPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportTransistorModel(uri) }

    val ptePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportAdvisoryModel(uri) }

    val tokenizerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportAdvisoryTokenizer(uri) }

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) onExportAllCsvToSafUri(uri) }

    if (uiState.inspectedMetadataBody != null) {
        AlertDialog(
            onDismissRequest = onDismissMetadataDialog,
            containerColor = colors.surfaceCard,
            title = {
                Text(
                    text = uiState.inspectedMetadataTitle ?: "Model Metadata",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = uiState.inspectedMetadataBody,
                    style = MaterialTheme.typography.bodySmall.merge(MonoTextStyles.tabularMetric),
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissMetadataDialog) {
                    Text("Close")
                }
            }
        )
    }

    if (uiState.showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearHistory,
            containerColor = colors.surfaceCard,
            title = {
                Text(
                    text = "Clear Inspection History?",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This permanently deletes all ${uiState.totalRecordsCount} Room inspection records, resets the internal CSV audit log and ledger, and removes unreferenced PCB image files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmClearHistory,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.rejectFg,
                        contentColor = colors.canvas
                    ),
                    modifier = Modifier.testTag("settings_confirm_clear_history")
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearHistory) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BrandHeader()
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = "Theme, on-device runtimes, screening policy & CSV storage",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }

        if (uiState.isBusy) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (uiState.modelStatusBanner != null) {
            item {
                val banner = uiState.modelStatusBanner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (banner.success) colors.passBg else colors.rejectBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_model_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = banner.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (banner.success) colors.passFg else colors.rejectFg,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissModelStatusBanner) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss status",
                                tint = if (banner.success) colors.passFg else colors.rejectFg
                            )
                        }
                    }
                }
            }
        }


        // 1. Appearance
        item {
            SettingsSectionCard(title = "Appearance") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AppThemeMode.SYSTEM to "System",
                        AppThemeMode.LIGHT to "Light",
                        AppThemeMode.DARK to "Dark"
                    ).forEach { (mode, label) ->
                        val selected = uiState.themeMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) colors.anchorSurface else colors.surfaceSubtle,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetThemeMode(mode) }
                                .testTag("settings_theme_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) colors.onAnchorSurface else colors.textSecondary,
                                modifier = Modifier.padding(vertical = 11.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 2. Model Runtime & Import
        item {
            SettingsSectionCard(title = "Model Runtime & Import") {
                ModelSlotPanel(
                    info = uiState.detectionModelInfo,
                    primaryImportLabel = "Import .onnx",
                    onPrimaryImport = { onnxPicker.launch(arrayOf("*/*")) },
                    secondaryImportLabel = null,
                    onSecondaryImport = null,
                    onLoad = onLoadDetectionModel,
                    onUnload = onUnloadDetectionModel,
                    onInspect = { onInspectModelMetadata(ModelSlot.PCB_DETECTION) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = colors.divider
                )

                ModelSlotPanel(
                    info = uiState.transistorModelInfo,
                    primaryImportLabel = "Import .onnx",
                    onPrimaryImport = { transistorPicker.launch(arrayOf("*/*")) },
                    secondaryImportLabel = null,
                    onSecondaryImport = null,
                    onLoad = onLoadTransistorModel,
                    onUnload = onUnloadTransistorModel,
                    onInspect = { onInspectModelMetadata(ModelSlot.TRANSISTOR_ANOMALY) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = colors.divider
                )

                ModelSlotPanel(
                    info = uiState.advisoryModelInfo,
                    primaryImportLabel = "Import .pte",
                    onPrimaryImport = { ptePicker.launch(arrayOf("*/*")) },
                    secondaryImportLabel = "Tokenizer",
                    onSecondaryImport = { tokenizerPicker.launch(arrayOf("*/*")) },
                    onLoad = onLoadAdvisoryModel,
                    onUnload = onUnloadAdvisoryModel,
                    onInspect = { onInspectModelMetadata(ModelSlot.AI_ADVISORY) }
                )
            }
        }

        // 3. Inspection Policy
        item {
            val policy = uiState.policyConfig
            SettingsSectionCard(title = "Inspection Screening Policy") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceSubtle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${policy.profileName} • Provisional screening thresholds only; does not assert certified IPC-A-610 compliance.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                PolicySwitchRow(
                    title = "Reject on Critical Defects",
                    subtitle = "Classifies open/short/missing hole/spurious copper as REJECT instead of REVIEW.",
                    checked = policy.rejectOnCritical,
                    testTag = "settings_policy_reject_critical",
                    onCheckedChange = { onUpdatePolicyConfig(policy.copy(rejectOnCritical = it)) }
                )
                HorizontalDivider(color = colors.divider)
                PolicySwitchRow(
                    title = "Review on Medium Defects",
                    subtitle = "Routes mouse bite / spur / solder bridge / tombstoning findings to REVIEW.",
                    checked = policy.reviewOnMedium,
                    testTag = "settings_policy_review_medium",
                    onCheckedChange = { onUpdatePolicyConfig(policy.copy(reviewOnMedium = it)) }
                )
                HorizontalDivider(color = colors.divider)
                PolicySwitchRow(
                    title = "Review on Low Severity Defects",
                    subtitle = "Escalates low-severity findings (e.g., pin hole / solder ball) to REVIEW.",
                    checked = policy.reviewOnLow,
                    testTag = "settings_policy_review_low",
                    onCheckedChange = { onUpdatePolicyConfig(policy.copy(reviewOnLow = it)) }
                )
                HorizontalDivider(color = colors.divider)

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Minimum Optical Frame Resolution",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(224, 256, 320, 512).forEach { dim ->
                        val active = policy.minImageWidth == dim && policy.minImageHeight == dim
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) colors.anchorSurface else colors.surfaceSubtle,
                            modifier = Modifier
                                .clickable {
                                    onUpdatePolicyConfig(
                                        policy.copy(minImageWidth = dim, minImageHeight = dim)
                                    )
                                }
                                .testTag("settings_min_dim_$dim")
                        ) {
                            Text(
                                text = "${dim}×${dim}",
                                style = MaterialTheme.typography.labelMedium.merge(MonoTextStyles.tabularMetric),
                                color = if (active) colors.onAnchorSurface else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Active Guard: Poor image clarity with zero detections always returns REVIEW (never PASS).",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onResetPolicyDefaults,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reset_policy_button")
                ) {
                    Text("Reset Policy to Baseline Defaults")
                }
            }
        }

        // 4. Storage & CSV Audit
        item {
            val stats = uiState.storageStats
            SettingsSectionCard(title = "Storage & CSV Audit") {
                StorageStatLine("Room Inspection Records", uiState.totalRecordsCount.toString())
                StorageStatLine(
                    "CSV Outbox / Ledger",
                    "${stats.confirmedCsvLedgerCount} confirmed • ${stats.pendingCsvOutboxCount} pending"
                )
                StorageStatLine(
                    "Internal Audit Log Size",
                    formatBytesHuman(stats.auditFileSizeBytes)
                )
                StorageStatLine(
                    "Captured PCB Images",
                    formatBytesHuman(stats.imageStorageBytes)
                )

                if (uiState.storageActionMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceSubtle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClearStorageMessage() }
                            .testTag("settings_storage_banner")
                    ) {
                        Text(
                            text = uiState.storageActionMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReconcileCsvOutbox,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_sync_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Audit", maxLines = 1)
                    }
                    Button(
                        onClick = { safExportLauncher.launch(suggestedAllCsvFilename) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_export_all_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export SAF", maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestClearHistory,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.rejectFg),
                    border = BorderStroke(1.dp, colors.rejectFg.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Inspection History")
                }
            }
        }

        // 5. About & Implementation Status
        item {
            SettingsSectionCard(title = "About & Implementation Status") {
                Text(
                    text = "Bharat AI PCB Inspector • Offline-First Native Edge AI",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Real camera & gallery inspections require an imported YOLOv8n .onnx file; unready production runtime returns INCOMPLETE.\n" +
                        "• Prototype Sample Boards execute strictly through DemoPcbDetectionEngine (isDemonstration=true).\n" +
                        "• Optional AI Technical Advisory uses ExecuTorch Android (SmolLM2 360M .pte + tokenizer) and never alters deterministic policy dispositions.\n" +
                        "• Dataset Note: 'copper' class defaults to REQUIRES_DATASET_VERIFICATION pending training schema confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LocalPcbColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelSlotPanel(
    info: ModelInfo,
    primaryImportLabel: String,
    onPrimaryImport: () -> Unit,
    secondaryImportLabel: String?,
    onSecondaryImport: (() -> Unit)?,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onInspect: () -> Unit
) {
    val colors = LocalPcbColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = info.runtimeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            ModelStateBadge(state = info.state)
        }

        Text(
            text = info.detailMessage,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )

        if (info.sha256 != null) {
            Text(
                text = "SHA-256: ${info.sha256.take(16)}...",
                style = MaterialTheme.typography.labelSmall.merge(MonoTextStyles.tabularMetric),
                color = colors.textMuted
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onPrimaryImport) {
                Icon(
                    imageVector = Icons.Outlined.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(primaryImportLabel)
            }

            if (secondaryImportLabel != null && onSecondaryImport != null) {
                OutlinedButton(onClick = onSecondaryImport) {
                    Text(secondaryImportLabel)
                }
            }

            if (info.state == ModelRuntimeState.READY) {
                OutlinedButton(onClick = onUnload) {
                    Text("Unload")
                }
            } else if (info.artifactPath != null) {
                Button(onClick = onLoad) {
                    Text("Load")
                }
            }

            TextButton(onClick = onInspect) {
                Text("Metadata")
            }
        }
    }
}

@Composable
private fun PolicySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalPcbColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun StorageStatLine(label: String, value: String) {
    val colors = LocalPcbColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.merge(MonoTextStyles.tabularMetric),
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary
        )
    }
}
