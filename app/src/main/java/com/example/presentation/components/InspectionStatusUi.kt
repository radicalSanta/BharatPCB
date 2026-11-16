package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DefectSeverity
import com.example.domain.model.InspectionDisposition
import com.example.domain.model.ModelRuntimeState
import com.example.ui.theme.LocalPcbColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DispositionBadge(
    disposition: InspectionDisposition,
    modifier: Modifier = Modifier
) {
    val colors = LocalPcbColors.current
    val (fg, bg, icon, label) = when (disposition) {
        InspectionDisposition.PASS -> Quad(colors.passFg, colors.passBg, Icons.Filled.CheckCircle, "PASS")
        InspectionDisposition.REVIEW -> Quad(colors.reviewFg, colors.reviewBg, Icons.Filled.RemoveCircle, "REVIEW")
        InspectionDisposition.REJECT -> Quad(colors.rejectFg, colors.rejectBg, Icons.Filled.Cancel, "REJECT")
        InspectionDisposition.INCOMPLETE -> Quad(colors.incompleteFg, colors.incompleteBg, Icons.Filled.ErrorOutline, "INCOMPLETE")
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
    }
}

@Composable
fun SeverityChip(
    severity: DefectSeverity,
    modifier: Modifier = Modifier
) {
    val colors = LocalPcbColors.current
    val (fg, bg, label) = when (severity) {
        DefectSeverity.CRITICAL -> Triple(colors.rejectFg, colors.rejectBg, "CRITICAL")
        DefectSeverity.MEDIUM -> Triple(colors.reviewFg, colors.reviewBg, "MEDIUM")
        DefectSeverity.LOW -> Triple(colors.textSecondary, colors.surfaceSubtle, "LOW")
        DefectSeverity.MANUAL_REVIEW -> Triple(colors.reviewFg, colors.reviewBg, "MANUAL REVIEW")
        DefectSeverity.REQUIRES_DATASET_VERIFICATION -> Triple(
            colors.incompleteFg,
            colors.incompleteBg,
            "VERIFY DATASET"
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DemonstrationBadge(
    label: String = "DEMO DATA",
    modifier: Modifier = Modifier
) {
    val colors = LocalPcbColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = colors.surfaceSubtle,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Science,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
fun ModelStateBadge(
    state: ModelRuntimeState,
    modifier: Modifier = Modifier
) {
    val colors = LocalPcbColors.current
    val (fg, bg, label) = when (state) {
        ModelRuntimeState.READY -> Triple(colors.passFg, colors.passBg, "READY")
        ModelRuntimeState.LOADING -> Triple(colors.reviewFg, colors.reviewBg, "LOADING")
        ModelRuntimeState.ERROR -> Triple(colors.rejectFg, colors.rejectBg, "ERROR")
        ModelRuntimeState.UNLOADED -> Triple(colors.incompleteFg, colors.incompleteBg, "UNLOADED")
        ModelRuntimeState.NOT_LOADED -> Triple(colors.textSecondary, colors.surfaceSubtle, "NOT LOADED")
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

fun severityColor(severity: DefectSeverity, isDark: Boolean): Color {
    return when (severity) {
        DefectSeverity.CRITICAL -> if (isDark) Color(0xFFF26D63) else Color(0xFFB3261E)
        DefectSeverity.MEDIUM -> if (isDark) Color(0xFFF4B942) else Color(0xFFBA6B00)
        DefectSeverity.LOW -> if (isDark) Color(0xFF4CD17D) else Color(0xFF1B7A43)
        DefectSeverity.MANUAL_REVIEW,
        DefectSeverity.REQUIRES_DATASET_VERIFICATION -> if (isDark) Color(0xFF9BA8AB) else Color(0xFF4A5C6A)
    }
}

fun formatInspectionTimestamp(epochMs: Long): String {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.US)
    return fmt.format(Date(epochMs))
}

fun formatDurationLabel(ms: Long?): String {
    return if (ms == null) "—" else "${ms} ms"
}

fun formatBytesHuman(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
