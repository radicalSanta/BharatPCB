package com.example.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ModelInfo
import com.example.ui.theme.LocalPcbColors

@Composable
fun RuntimeStatusDialog(
    detectionModelInfo: ModelInfo,
    advisoryModelInfo: ModelInfo,
    transistorModelInfo: ModelInfo? = null,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPcbColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        title = {
            Text(
                text = "On-Device AI Runtime Status",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Real camera and gallery inspections require imported, loaded on-device models. Sample boards run through the isolated demo simulator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                RuntimeDialogSlotCard(info = detectionModelInfo)
                transistorModelInfo?.let { RuntimeDialogSlotCard(info = it) }
                RuntimeDialogSlotCard(info = advisoryModelInfo)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
                modifier = Modifier.testTag("runtime_dialog_open_settings")
            ) {
                Text("Manage Models")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun RuntimeDialogSlotCard(info: ModelInfo) {
    val colors = LocalPcbColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                ModelStateBadge(state = info.state)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = info.runtimeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = colors.divider
            )
            Text(
                text = info.detailMessage,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}
