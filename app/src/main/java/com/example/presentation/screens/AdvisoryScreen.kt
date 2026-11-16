package com.example.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AdvisoryAvailability
import com.example.presentation.components.DemonstrationBadge
import com.example.presentation.components.DispositionBadge
import com.example.presentation.components.ModelStateBadge
import com.example.presentation.components.formatDurationLabel
import com.example.presentation.viewmodel.AdvisoryUiState
import com.example.ui.theme.LocalPcbColors
import com.example.ui.theme.MonoTextStyles

@Composable
fun AdvisoryScreen(
    uiState: AdvisoryUiState,
    onBackClick: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    val colors = LocalPcbColors.current

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                text = "Inspection record unavailable",
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

    val advisory = record.advisory

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("advisory_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to Results",
                    tint = colors.textPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Technical Advisory",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "${record.inspectionId} • ${record.boardName}",
                    style = MonoTextStyles.inspectionId,
                    color = colors.textSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("advisory_screen"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

        // Authoritative deterministic policy card (Advisory never alters official disposition)
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Authoritative Policy Disposition",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        DispositionBadge(disposition = record.officialDisposition)
                    }
                    Text(
                        text = "Advisory guidance is strictly explanatory and does not modify bounding boxes, defect counts, or official screening disposition.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    if (advisory.isDemonstration) {
                        DemonstrationBadge(label = "DEMO ADVISORY")
                    }
                }
            }
        }

        if (advisory.availability != AdvisoryAvailability.AVAILABLE) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advisory_unavailable_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Offline Advisory Unavailable",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }
                            ModelStateBadge(state = uiState.advisoryModelInfo.state)
                        }

                        Text(
                            text = advisory.unavailableReason
                                ?: "Llama 3.2 1B ExecuTorch runtime is not loaded. Import a compatible .pte model and tokenizer in Settings to generate local engineering notes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )

                        Button(
                            onClick = onOpenSettingsClick,
                            modifier = Modifier.testTag("advisory_open_settings_button")
                        ) {
                            Text("Configure ExecuTorch in Settings")
                        }
                    }
                }
            }
        } else {
            item {
                AdvisorySectionCard(
                    title = "Severity & Screening Context",
                    body = advisory.severityClassification
                        ?: "${record.totalDefects} defect(s) flagged; policy disposition = ${record.officialDisposition.name}."
                )
            }
            item {
                AdvisorySectionCard(
                    title = "Probable Root Cause",
                    body = advisory.probableRootCause ?: "Not provided."
                )
            }
            item {
                AdvisorySectionCard(
                    title = "Recommended Operator Action",
                    body = advisory.recommendedOperatorAction ?: "Perform visual verification under microscope."
                )
            }
            item {
                AdvisorySectionCard(
                    title = "Relevant Standard",
                    body = advisory.relevantStandard ?: "None verified"
                )
            }
            item {
                AdvisorySectionCard(
                    title = "Limitations & Scope",
                    body = advisory.limitations
                        ?: "AI advisory is informational only and relies solely on optical bounding-box detections."
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceSubtle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Advisory Execution Metadata",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = colors.divider
                        )
                        Text(
                            text = "Provenance: ${advisory.provenanceSummary ?: "N/A"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Latency: ${formatDurationLabel(advisory.advisoryLatencyMs)}",
                            style = MaterialTheme.typography.labelSmall.merge(MonoTextStyles.tabularMetric),
                            color = colors.textSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
    }
}

@Composable
private fun AdvisorySectionCard(
    title: String,
    body: String
) {
    val colors = LocalPcbColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}
