package com.example.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.InspectionRecord
import com.example.presentation.components.BrandHeader
import com.example.presentation.components.DispositionBadge
import com.example.presentation.components.formatInspectionTimestamp
import com.example.presentation.viewmodel.HistoryDispositionFilter
import com.example.presentation.viewmodel.HistorySortOrder
import com.example.presentation.viewmodel.HistoryUiState
import com.example.ui.theme.LocalPcbColors
import com.example.ui.theme.MonoTextStyles

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onSearchQueryChanged: (String) -> Unit,
    onDispositionFilterSelected: (HistoryDispositionFilter) -> Unit,
    onSortOrderSelected: (HistorySortOrder) -> Unit,
    onStageExportAllCsv: () -> String,
    onStageExportSingleCsv: (InspectionRecord) -> String,
    onSafUriSelected: (Uri) -> Unit,
    onShareAllCsv: ((Uri) -> Unit) -> Unit,
    onShareRecordCsv: (InspectionRecord, (Uri) -> Unit) -> Unit,
    onRecordClick: (String) -> Unit,
    onClearStatusMessage: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalPcbColors.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val safCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) onSafUriSelected(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BrandHeader()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inspection History",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${uiState.totalRecordsCount} stored inspection record(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                if (uiState.totalRecordsCount > 0) {
                    Row {
                        IconButton(
                            onClick = {
                                val fileName = onStageExportAllCsv()
                                safCreateLauncher.launch(fileName)
                            },
                            modifier = Modifier.testTag("history_export_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = "Export all records CSV",
                                tint = colors.textPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                onShareAllCsv { shareUri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share Inspection History CSV")
                                    )
                                }
                            },
                            modifier = Modifier.testTag("history_share_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share all records CSV",
                                tint = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

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

        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search ID, board name, or defect class") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryDispositionFilter.entries.forEach { filter ->
                        val selected = uiState.dispositionFilter == filter
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) colors.anchorSurface else colors.surfaceSubtle,
                            modifier = Modifier
                                .clickable { onDispositionFilterSelected(filter) }
                                .testTag("history_filter_${filter.name.lowercase()}")
                        ) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) colors.onAnchorSurface else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.divider),
                        modifier = Modifier
                            .clickable { sortMenuExpanded = true }
                            .testTag("history_sort_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.sortOrder.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textPrimary
                            )
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Sort order",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        HistorySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSortOrderSelected(order)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.filteredRecords.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_empty_state")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = if (uiState.totalRecordsCount == 0) {
                                "No inspection records yet"
                            } else {
                                "No records match the current filter"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (uiState.totalRecordsCount == 0) {
                                "Completed PCB inspections are saved locally in Room and automatically logged to CSV."
                            } else {
                                "Try clearing the search query or switching the disposition filter."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = uiState.filteredRecords,
                key = { _, rec -> rec.inspectionId }
            ) { index, record ->
                HistoryRecordRow(
                    record = record,
                    onClick = { onRecordClick(record.inspectionId) },
                    onExportSaf = {
                        val name = onStageExportSingleCsv(record)
                        safCreateLauncher.launch(name)
                    },
                    onShare = {
                        onShareRecordCsv(record) { shareUri ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share ${record.inspectionId} CSV")
                            )
                        }
                    }
                )
                if (index == uiState.filteredRecords.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordRow(
    record: InspectionRecord,
    onClick: () -> Unit,
    onExportSaf: () -> Unit,
    onShare: () -> Unit
) {
    val colors = LocalPcbColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_item_${record.inspectionId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = Uri.parse(record.imageUri),
                contentDescription = record.boardName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
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
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            DispositionBadge(disposition = record.officialDisposition)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Record export actions",
                        tint = colors.textSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Save CSV via SAF") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onExportSaf()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share CSV") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                }
            }
        }
    }
}
