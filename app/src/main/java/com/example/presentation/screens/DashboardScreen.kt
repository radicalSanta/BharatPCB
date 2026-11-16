package com.example.presentation.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.InspectionRecord
import com.example.domain.model.ModelInfo
import com.example.domain.usecase.DailyTrendPoint
import com.example.domain.usecase.DashboardPeriod
import com.example.domain.usecase.TrendMetric
import com.example.domain.usecase.TrendWindow
import com.example.presentation.components.BrandHeader
import com.example.presentation.components.DemonstrationBadge
import com.example.presentation.components.DispositionBadge
import com.example.presentation.components.ModelStateBadge
import com.example.presentation.components.RuntimeStatusDialog
import com.example.presentation.components.formatInspectionTimestamp
import com.example.presentation.viewmodel.DashboardUiState
import com.example.ui.theme.LocalPcbColors
import com.example.ui.theme.MonoTextStyles
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onSelectPeriod: (DashboardPeriod) -> Unit,
    onSelectTrendWindow: (TrendWindow) -> Unit,
    onSelectTrendMetric: (TrendMetric) -> Unit,
    onStartInspectionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRecordClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val colors = LocalPcbColors.current
    var showRuntimeDialog by remember { mutableStateOf(false) }

    if (showRuntimeDialog) {
        RuntimeStatusDialog(
            detectionModelInfo = uiState.detectionModelInfo,
            advisoryModelInfo = uiState.advisoryModelInfo,
            onOpenSettings = onSettingsClick,
            onDismiss = { showRuntimeDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BrandHeader(
                onRuntimeStatusClick = { showRuntimeDialog = true },
                hasRuntimeAlert = uiState.hasRuntimeAlert
            )
        }

        item {
            InspectionOverviewAnchorCard(
                uiState = uiState,
                onSelectPeriod = onSelectPeriod
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartInspectionClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("dashboard_start_inspection_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.anchorSurface,
                        contentColor = colors.onAnchorSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CenterFocusWeak,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Inspection",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onHistoryClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("dashboard_history_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.tonalButtonSurface,
                        contentColor = colors.onTonalButtonSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "History",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item {
            InspectionOutcomesSection(uiState = uiState)
        }

        item {
            DefectTrendSection(
                uiState = uiState,
                onSelectTrendWindow = onSelectTrendWindow,
                onSelectTrendMetric = onSelectTrendMetric
            )
        }

        item {
            RecentInspectionsSection(
                records = uiState.metrics.recentInspections,
                onSeeAllClick = onHistoryClick,
                onRecordClick = onRecordClick,
                onStartInspectionClick = onStartInspectionClick
            )
        }

        item {
            ModelReadinessSection(
                detectionModelInfo = uiState.detectionModelInfo,
                advisoryModelInfo = uiState.advisoryModelInfo,
                onConfigureClick = onSettingsClick
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InspectionOverviewAnchorCard(
    uiState: DashboardUiState,
    onSelectPeriod: (DashboardPeriod) -> Unit
) {
    val colors = LocalPcbColors.current
    var periodExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_overview_card"),
        shape = RoundedCornerShape(22.dp),
        color = colors.anchorSurface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inspection Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onAnchorSurface
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (colors.isDark) colors.surfaceSubtle.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clickable { periodExpanded = true }
                            .testTag("dashboard_period_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.selectedPeriod.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onAnchorSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Select overview timeframe",
                                tint = colors.onAnchorSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = periodExpanded,
                        onDismissRequest = { periodExpanded = false }
                    ) {
                        DashboardPeriod.entries.forEach { period ->
                            DropdownMenuItem(
                                text = { Text(period.label) },
                                onClick = {
                                    periodExpanded = false
                                    onSelectPeriod(period)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = uiState.metrics.totalInspections.toString(),
                    style = MaterialTheme.typography.displayMedium.merge(MonoTextStyles.tabularMetric),
                    fontWeight = FontWeight.Bold,
                    color = colors.onAnchorSurface,
                    modifier = Modifier.testTag("dashboard_total_count")
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = colors.anchorSubtleText,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "Total Inspections",
                style = MaterialTheme.typography.bodySmall,
                color = colors.anchorSubtleText
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.anchorDivider)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OverviewSubMetric(
                    label = "Pass",
                    count = uiState.metrics.passCount,
                    valueColor = if (colors.isDark) Color(0xFF157F40) else Color(0xFF4CD17D),
                    labelColor = colors.anchorSubtleText,
                    modifier = Modifier.weight(1f)
                )
                VerticalOverviewDivider(colors.anchorDivider)
                OverviewSubMetric(
                    label = "Review",
                    count = uiState.metrics.reviewCount,
                    valueColor = if (colors.isDark) Color(0xFFA66000) else Color(0xFFF4B942),
                    labelColor = colors.anchorSubtleText,
                    modifier = Modifier.weight(1f)
                )
                VerticalOverviewDivider(colors.anchorDivider)
                OverviewSubMetric(
                    label = "Reject",
                    count = uiState.metrics.rejectCount,
                    valueColor = if (colors.isDark) Color(0xFFB3261E) else Color(0xFFF26D63),
                    labelColor = colors.anchorSubtleText,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.metrics.incompleteCount > 0) {
                    VerticalOverviewDivider(colors.anchorDivider)
                    OverviewSubMetric(
                        label = "Incomp.",
                        count = uiState.metrics.incompleteCount,
                        valueColor = colors.anchorSubtleText,
                        labelColor = colors.anchorSubtleText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalOverviewDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(color)
    )
}

@Composable
private fun OverviewSubMetric(
    label: String,
    count: Int,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleLarge.merge(MonoTextStyles.tabularMetric),
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionOutcomesSection(uiState: DashboardUiState) {
    val colors = LocalPcbColors.current
    val metrics = uiState.metrics

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_outcomes_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inspection Outcomes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            if (metrics.hasDemonstrationRecords) {
                DemonstrationBadge(
                    label = "DEMO DATA",
                    modifier = Modifier.testTag("dashboard_demo_data_badge")
                )
            }
        }

        // Proportional horizontal segmented bar (no numbers inside bar)
        val total = metrics.totalInspections
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceSubtle),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (total > 0) {
                if (metrics.passCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(metrics.passCount.toFloat())
                            .height(10.dp)
                            .background(colors.passFg)
                    )
                }
                if (metrics.reviewCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(metrics.reviewCount.toFloat())
                            .height(10.dp)
                            .background(colors.reviewFg)
                    )
                }
                if (metrics.rejectCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(metrics.rejectCount.toFloat())
                            .height(10.dp)
                            .background(colors.rejectFg)
                    )
                }
                if (metrics.incompleteCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(metrics.incompleteCount.toFloat())
                            .height(10.dp)
                            .background(colors.incompleteFg)
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutcomeLegendItem(
                dotColor = colors.passFg,
                label = "Pass",
                count = metrics.passCount,
                percent = metrics.passPercent
            )
            OutcomeLegendItem(
                dotColor = colors.reviewFg,
                label = "Review",
                count = metrics.reviewCount,
                percent = metrics.reviewPercent
            )
            OutcomeLegendItem(
                dotColor = colors.rejectFg,
                label = "Reject",
                count = metrics.rejectCount,
                percent = metrics.rejectPercent
            )
            if (metrics.incompleteCount > 0) {
                OutcomeLegendItem(
                    dotColor = colors.incompleteFg,
                    label = "Incomplete",
                    count = metrics.incompleteCount,
                    percent = metrics.incompletePercent
                )
            }
        }
    }
}

@Composable
private fun OutcomeLegendItem(
    dotColor: Color,
    label: String,
    count: Int,
    percent: Float
) {
    val colors = LocalPcbColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label · ${count.toString().padStart(2, '0')} (${String.format(Locale.US, "%.1f", percent)}%)",
            style = MaterialTheme.typography.bodySmall.merge(MonoTextStyles.tabularMetric),
            color = colors.textSecondary
        )
    }
}

@Composable
private fun DefectTrendSection(
    uiState: DashboardUiState,
    onSelectTrendWindow: (TrendWindow) -> Unit,
    onSelectTrendMetric: (TrendMetric) -> Unit
) {
    val colors = LocalPcbColors.current
    var windowMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_trend_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Defect Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )

            Box {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.surfaceSubtle,
                    modifier = Modifier
                        .clickable { windowMenuExpanded = true }
                        .testTag("dashboard_trend_window_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.selectedTrendWindow.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Select trend timeframe",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = windowMenuExpanded,
                    onDismissRequest = { windowMenuExpanded = false }
                ) {
                    TrendWindow.entries.forEach { window ->
                        DropdownMenuItem(
                            text = { Text(window.label) },
                            onClick = {
                                windowMenuExpanded = false
                                onSelectTrendWindow(window)
                            }
                        )
                    }
                }
            }
        }

        // Metric toggle (Defect Counts default / Inspection Counts)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendMetric.entries.forEach { metric ->
                val selected = uiState.selectedTrendMetric == metric
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) colors.anchorSurface else colors.surfaceSubtle,
                    modifier = Modifier
                        .clickable { onSelectTrendMetric(metric) }
                        .testTag("trend_metric_${metric.name.lowercase()}")
                ) {
                    Text(
                        text = metric.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colors.onAnchorSurface else colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
            if (!uiState.metrics.hasAnyTrendRecords) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QueryStats,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "No daily trend data yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Inspected boards stored in Room will plot daily counts here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            } else {
                DailyTrendChart(
                    points = uiState.metrics.trendPoints,
                    metric = uiState.selectedTrendMetric
                )
            }
        }
    }
}

@Composable
private fun DailyTrendChart(
    points: List<DailyTrendPoint>,
    metric: TrendMetric
) {
    val colors = LocalPcbColors.current
    val values = points.map { point ->
        when (metric) {
            TrendMetric.DEFECT_COUNT -> point.defectCount
            TrendMetric.INSPECTION_COUNT -> point.inspectionCount
        }
    }
    val maxVal = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val lineColor = colors.textPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (metric == TrendMetric.DEFECT_COUNT) {
                    "Total Defects: ${values.sum()}"
                } else {
                    "Total Runs: ${values.sum()}"
                },
                style = MaterialTheme.typography.labelMedium.merge(MonoTextStyles.tabularMetric),
                color = colors.textSecondary
            )
            Text(
                text = "Peak/day: ${values.maxOrNull() ?: 0}",
                style = MaterialTheme.typography.labelMedium.merge(MonoTextStyles.tabularMetric),
                color = colors.textSecondary
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
        ) {
            if (points.isEmpty()) return@Canvas
            val leftPad = 14.dp.toPx()
            val rightPad = 14.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 12.dp.toPx()
            val usableW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val usableH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            val coords = points.mapIndexed { idx, _ ->
                val x = if (points.size == 1) {
                    leftPad + usableW / 2f
                } else {
                    leftPad + (idx.toFloat() / (points.size - 1).toFloat()) * usableW
                }
                val ratio = values[idx].toFloat() / maxVal.toFloat()
                val y = topPad + usableH * (1f - ratio)
                Offset(x, y)
            }

            // Baseline grid line
            drawLine(
                color = colors.divider,
                start = Offset(leftPad, topPad + usableH),
                end = Offset(leftPad + usableW, topPad + usableH),
                strokeWidth = 1.dp.toPx()
            )

            if (coords.size >= 2) {
                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 0 until coords.lastIndex) {
                        val p0 = coords[i]
                        val p1 = coords[i + 1]
                        val midX = (p0.x + p1.x) / 2f
                        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(coords.last().x, topPad + usableH)
                    lineTo(coords.first().x, topPad + usableH)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.20f),
                            lineColor.copy(alpha = 0.01f)
                        ),
                        startY = topPad,
                        endY = topPad + usableH
                    )
                )
                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            coords.forEach { pt ->
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = colors.surfaceCard,
                    radius = 2.dp.toPx(),
                    center = pt
                )
            }
        }

        // X-axis day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = if (points.size > 8) 2 else 1
            points.forEachIndexed { index, pt ->
                if (index % step == 0 || index == points.lastIndex) {
                    Text(
                        text = pt.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentInspectionsSection(
    records: List<InspectionRecord>,
    onSeeAllClick: () -> Unit,
    onRecordClick: (String) -> Unit,
    onStartInspectionClick: () -> Unit
) {
    val colors = LocalPcbColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Inspections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            TextButton(
                onClick = onSeeAllClick,
                modifier = Modifier.testTag("dashboard_see_all_button")
            ) {
                Text("See all", color = colors.textSecondary)
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (records.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "No inspections recorded yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Capture or upload a PCB image, or run one of the 3 sample boards on the Inspect tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    TextButton(
                        onClick = onStartInspectionClick,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Go to New Inspection", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    records.forEachIndexed { idx, record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRecordClick(record.inspectionId) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("recent_inspection_row_${record.inspectionId}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = Uri.parse(record.imageUri),
                                contentDescription = record.boardName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceSubtle)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.inspectionId,
                                    style = MonoTextStyles.inspectionId,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = record.boardName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(formatInspectionTimestamp(record.timestampEpochMs))
                                        append(" • ${record.totalDefects} defect(s)")
                                        if (record.isDemonstration) append(" • DEMO")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            DispositionBadge(disposition = record.officialDisposition)
                        }
                        if (idx < records.lastIndex) {
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
}

@Composable
private fun ModelReadinessSection(
    detectionModelInfo: ModelInfo,
    advisoryModelInfo: ModelInfo,
    onConfigureClick: () -> Unit
) {
    val colors = LocalPcbColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Model Readiness",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            TextButton(
                onClick = onConfigureClick,
                modifier = Modifier.testTag("dashboard_configure_models_button")
            ) {
                Text("Configure", color = colors.textSecondary)
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ModelReadinessRow(
                    title = "YOLOv8n Defect Detector",
                    subtitle = detectionModelInfo.runtimeLabel,
                    info = detectionModelInfo
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = colors.divider
                )
                ModelReadinessRow(
                    title = "Llama 3.2 1B Technical Advisory",
                    subtitle = advisoryModelInfo.runtimeLabel,
                    info = advisoryModelInfo
                )
            }
        }
    }
}

@Composable
private fun ModelReadinessRow(
    title: String,
    subtitle: String,
    info: ModelInfo
) {
    val colors = LocalPcbColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.surfaceSubtle,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
            Text(
                text = info.detailMessage,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ModelStateBadge(state = info.state)
    }
}
