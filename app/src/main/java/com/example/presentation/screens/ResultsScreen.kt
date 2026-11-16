package com.example.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.AdvisoryAvailability
import com.example.domain.model.DetectedDefect
import com.example.domain.model.InspectionDisposition
import com.example.domain.model.InspectionRecord
import com.example.presentation.components.DemonstrationBadge
import com.example.presentation.components.DispositionBadge
import com.example.presentation.components.SeverityChip
import com.example.presentation.components.formatDurationLabel
import com.example.presentation.components.formatInspectionTimestamp
import com.example.presentation.components.severityColor
import com.example.presentation.viewmodel.ResultsUiState
import com.example.ui.theme.LocalPcbColors
import com.example.ui.theme.MonoTextStyles
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(
    uiState: ResultsUiState,
    suggestedCsvFilename: String,
    onBackClick: () -> Unit,
    onSetAnnotatedOverlay: (Boolean) -> Unit,
    onSelectDefect: (String?) -> Unit,
    onToggleProvenance: () -> Unit,
    onOpenAdvisoryClick: (String) -> Unit,
    onSafExportUriSelected: (Uri) -> Unit,
    onShareCsvClick: ((Uri) -> Unit) -> Unit,
    onClearStatusMessage: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalPcbColors.current

    val safCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) onSafExportUriSelected(uri)
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val record = uiState.record
    if (record == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Inspection record not found",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBackClick) {
                Text("Go Back")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("results_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.inspectionId,
                    style = MonoTextStyles.inspectionId,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.boardName} • ${formatInspectionTimestamp(record.timestampEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { safCreateLauncher.launch(suggestedCsvFilename) },
                modifier = Modifier.testTag("results_export_csv_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Save CSV via SAF",
                    tint = colors.textPrimary
                )
            }
            IconButton(
                onClick = {
                    onShareCsvClick { shareUri ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Inspection CSV"))
                    }
                },
                modifier = Modifier.testTag("results_share_csv_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share Inspection CSV",
                    tint = colors.textPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("results_screen"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

        if (uiState.statusMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClearStatusMessage() }
                ) {
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Disposition Header Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Official Screening Disposition",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DispositionBadge(
                                disposition = record.officialDisposition,
                                modifier = Modifier.testTag("results_disposition_badge")
                            )
                            if (record.isDemonstration) {
                                DemonstrationBadge(label = "DEMO")
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = record.totalDefects.toString(),
                            style = MaterialTheme.typography.headlineMedium.merge(MonoTextStyles.tabularMetric),
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Defects Found",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        if (record.isDemonstration) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceSubtle,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("results_demo_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Science,
                            contentDescription = null,
                            tint = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Demonstration Simulation Result: Detections were generated by DemoPcbDetectionEngine for sample evaluation and do not reflect live ONNX inference.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        if (record.officialDisposition == InspectionDisposition.INCOMPLETE) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.incompleteBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("results_incomplete_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = colors.incompleteFg
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = record.policyRationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        } else if (record.lowClarityRisk) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.reviewBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.reviewFg
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val scoreText = record.clarityScore?.let {
                            String.format(Locale.US, " (score %.1f)", it)
                        } ?: ""
                        Text(
                            text = "Low image clarity detected$scoreText — optical confidence may be degraded; manual review recommended.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        // Interactive Pan & Zoom Viewport with Original / Annotated Toggle
        item {
            InteractiveAnnotatedPcbViewport(
                record = record,
                showAnnotated = uiState.showAnnotatedOverlay,
                selectedDefectId = uiState.selectedDefectId,
                onToggleOverlay = onSetAnnotatedOverlay,
                onSelectDefect = onSelectDefect
            )
        }

        // Policy Evaluation Rationale
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = record.policyProfile,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                    Text(
                        text = record.policyRationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "Policy v${record.policyVersion} • ${record.effectiveThresholdsSummary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }
            }
        }

        // Defect List
        item {
            Text(
                text = "Detected Defects (${record.defects.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }

        if (record.defects.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (record.officialDisposition == InspectionDisposition.INCOMPLETE) {
                                "No detections available (inspection incomplete)"
                            } else {
                                "No PCB defects detected"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (record.officialDisposition == InspectionDisposition.INCOMPLETE) {
                                "Inference could not complete reliably. Resolve the runtime or image issue and re-inspect."
                            } else {
                                "Zero bounding boxes exceeded the detector confidence threshold."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        record.defects.forEachIndexed { idx, defect ->
                            val isSelected = defect.defectId == uiState.selectedDefectId
                            DefectListRow(
                                defect = defect,
                                isSelected = isSelected,
                                onClick = { onSelectDefect(defect.defectId) }
                            )
                            if (idx < record.defects.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = colors.divider
                                )
                            }
                        }
                    }
                }
            }
        }

        // AI Technical Advisory CTA
        item {
            val advLabel = when (record.advisory.availability) {
                AdvisoryAvailability.AVAILABLE ->
                    if (record.advisory.isDemonstration) "Open AI Technical Advisory (Demo)" else "Open AI Technical Advisory (Ready)"
                AdvisoryAvailability.UNAVAILABLE -> "Open AI Technical Advisory (Unavailable)"
                AdvisoryAvailability.NOT_REQUESTED -> "Open AI Technical Advisory"
            }
            Button(
                onClick = { onOpenAdvisoryClick(record.inspectionId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("results_open_advisory_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.anchorSurface,
                    contentColor = colors.onAnchorSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = advLabel, fontWeight = FontWeight.SemiBold)
            }
        }

        // Expandable Provenance & Latency Details
        item {
            ProvenanceAndLatencyCard(
                record = record,
                expanded = uiState.isProvenanceExpanded,
                onToggle = onToggleProvenance
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
    }
}

@Composable
private fun InteractiveAnnotatedPcbViewport(
    record: InspectionRecord,
    showAnnotated: Boolean,
    selectedDefectId: String?,
    onToggleOverlay: (Boolean) -> Unit,
    onSelectDefect: (String?) -> Unit
) {
    val colors = LocalPcbColors.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val ratio = if (record.imageWidth > 0 && record.imageHeight > 0) {
        (record.imageWidth.toFloat() / record.imageHeight.toFloat()).coerceIn(0.6f, 1.9f)
    } else {
        4f / 3f
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceSubtle)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(false to "Original", true to "Annotated").forEach { (isAnnot, label) ->
                    val active = showAnnotated == isAnnot
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (active) colors.anchorSurface else colors.surfaceSubtle,
                        modifier = Modifier
                            .clickable { onToggleOverlay(isAnnot) }
                            .testTag(if (isAnnot) "results_toggle_annotated" else "results_toggle_original")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) colors.onAnchorSurface else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = colors.surfaceSubtle,
                modifier = Modifier
                    .clickable {
                        scale = 1f
                        offset = Offset.Zero
                    }
                    .testTag("results_fit_reset_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = "Reset zoom",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (scale > 1.05f) "Reset (${String.format(Locale.US, "%.1fx", scale)})" else "Fit",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(18.dp))
                    .clipToBounds()
                    .background(colors.surfaceSubtle)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = newScale
                            if (newScale <= 1.01f) {
                                offset = Offset.Zero
                            } else {
                                val maxX = (size.width * (newScale - 1f)) / 2f
                                val maxY = (size.height * (newScale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                        }
                    }
            ) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                ) {
                    AsyncImage(
                        model = Uri.parse(record.imageUri),
                        contentDescription = record.boardName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showAnnotated && record.defects.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            record.defects.forEach { defect ->
                                val box = defect.boundingBox.clamped()
                                val left = box.x * size.width
                                val top = box.y * size.height
                                val w = box.width * size.width
                                val h = box.height * size.height
                                val isSel = defect.defectId == selectedDefectId
                                val strokeCol = severityColor(defect.severity, colors.isDark)
                                val strokeW = if (isSel) 4.dp.toPx() else 2.dp.toPx()

                                if (isSel) {
                                    drawRect(
                                        color = strokeCol.copy(alpha = 0.22f),
                                        topLeft = Offset(left, top),
                                        size = Size(w, h)
                                    )
                                }
                                drawRect(
                                    color = strokeCol,
                                    topLeft = Offset(left, top),
                                    size = Size(w, h),
                                    style = Stroke(width = strokeW)
                                )
                            }
                        }

                        // Clickable normalized box hit-targets + compact labels
                        record.defects.forEachIndexed { idx, defect ->
                            val box = defect.boundingBox.clamped()
                            val leftPx = (box.x * widthPx).roundToInt()
                            val topPx = (box.y * heightPx).roundToInt()
                            val boxW = with(LocalDensity.current) { (box.width * widthPx).toDp() }
                            val boxH = with(LocalDensity.current) { (box.height * heightPx).toDp() }
                            val tagColor = severityColor(defect.severity, colors.isDark)

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(leftPx, topPx) }
                                    .size(
                                        width = boxW.coerceAtLeast(36.dp),
                                        height = boxH.coerceAtLeast(36.dp)
                                    )
                                    .clickable { onSelectDefect(defect.defectId) }
                                    .testTag("results_bbox_${defect.defectId}")
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = tagColor
                                ) {
                                    Text(
                                        text = "#${idx + 1} ${defect.canonicalClassName} ${(defect.confidence * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefectListRow(
    defect: DetectedDefect,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalPcbColors.current
    val bg = if (isSelected) colors.surfaceSubtle else colors.surfaceCard
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("results_defect_row_${defect.defectId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = defect.canonicalClassName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                SeverityChip(severity = defect.severity)
            }
            if (defect.rawClassName != defect.canonicalClassName) {
                Text(
                    text = "Raw class: ${defect.rawClassName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
            val box = defect.boundingBox
            Text(
                text = String.format(
                    Locale.US,
                    "Confidence %.1f%% • Box [%.2f, %.2f, %.2f, %.2f]",
                    defect.confidence * 100f,
                    box.x,
                    box.y,
                    box.width,
                    box.height
                ),
                style = MaterialTheme.typography.bodySmall.merge(MonoTextStyles.tabularMetric),
                color = colors.textSecondary
            )
            if (defect.requiresManualReview) {
                Text(
                    text = "Requires manual verification",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.reviewFg
                )
            }
        }
    }
}

@Composable
private fun ProvenanceAndLatencyCard(
    record: InspectionRecord,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalPcbColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .testTag("results_provenance_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model Provenance & Latency",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = "Toggle provenance details",
                    tint = colors.textSecondary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HorizontalDivider(color = colors.divider)
                    Spacer(modifier = Modifier.height(4.dp))
                    ProvenanceLine("Inference Engine", record.provenance.inferenceEngine)
                    ProvenanceLine("Model Identity", record.provenance.modelIdentity)
                    ProvenanceLine("Model SHA-256", record.provenance.modelSha256)
                    ProvenanceLine("Model Contract", record.provenance.modelContractVersion)
                    ProvenanceLine("Preprocessing", record.provenance.preprocessingVersion)
                    ProvenanceLine("Decoder", record.provenance.decoderVersion)
                    ProvenanceLine("Class Mapping", record.provenance.classMappingVersion)
                    ProvenanceLine("Runtime Version", record.provenance.runtimeVersion)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = colors.divider
                    )
                    val lat = record.latencyMetrics
                    ProvenanceLine("Preprocess Latency", formatDurationLabel(lat.imagePreprocessingMs))
                    ProvenanceLine("YOLO Inference", formatDurationLabel(lat.yoloInferenceMs))
                    ProvenanceLine("Decode + NMS", formatDurationLabel(lat.outputDecodingNmsMs))
                    ProvenanceLine("Policy Evaluation", formatDurationLabel(lat.policyEvaluationMs))
                    ProvenanceLine("LLM Advisory", formatDurationLabel(lat.llmAdvisoryMs))
                    ProvenanceLine("Total Pipeline", formatDurationLabel(lat.totalDurationMs))
                }
            }
        }
    }
}

@Composable
private fun ProvenanceLine(label: String, value: String) {
    val colors = LocalPcbColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.merge(MonoTextStyles.tabularMetric),
            color = colors.textPrimary,
            modifier = Modifier.weight(0.58f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
