package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppStrings
import com.example.engines.editor.DiffLineType
import com.example.engines.editor.FileComparisonReport
import com.example.engines.editor.HexPageWindow
import com.example.engines.editor.SupportedEncoding
import com.example.engines.editor.TextDocumentSession
import com.example.ui.viewmodel.EditorSubTab
import com.example.ui.viewmodel.YemenManagerViewModel
import java.io.File

@Composable
fun EditorsAndDiffScreen(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    activeTab: EditorSubTab,
    textSession: TextDocumentSession?,
    hexWindow: HexPageWindow?,
    hexMatches: List<Long>,
    diffReport: FileComparisonReport?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("editors_and_diff_screen")
    ) {
        // Sub-navigation Tabs (Text Editor | Hex Viewer/Editor | File Diff)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeTab == EditorSubTab.TEXT,
                onClick = { viewModel.selectEditorSubTab(EditorSubTab.TEXT) },
                label = { Text(strings.textEditorTitle) },
                modifier = Modifier.testTag("tab_text_editor")
            )
            FilterChip(
                selected = activeTab == EditorSubTab.HEX,
                onClick = { viewModel.selectEditorSubTab(EditorSubTab.HEX) },
                label = { Text(strings.hexEditorTitle) },
                modifier = Modifier.testTag("tab_hex_editor")
            )
            FilterChip(
                selected = activeTab == EditorSubTab.DIFF,
                onClick = { viewModel.selectEditorSubTab(EditorSubTab.DIFF) },
                label = { Text(strings.diffToolTitle) },
                modifier = Modifier.testTag("tab_diff_tool")
            )
        }

        Spacer(Modifier.height(8.dp))

        when (activeTab) {
            EditorSubTab.TEXT -> TextEditorPane(viewModel, strings, textSession)
            EditorSubTab.HEX -> HexEditorPane(viewModel, strings, hexWindow, hexMatches)
            EditorSubTab.DIFF -> DiffToolPane(strings, diffReport)
        }
    }
}

@Composable
private fun TextEditorPane(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    session: TextDocumentSession?
) {
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var showReplaceOriginalConfirm by remember { mutableStateOf(false) }

    if (session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.emptyFolderMessage)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Working Copy Safety Banner (FR-ED-004)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text(
                        text = "${session.fileName} (${session.originalByteSize} B) • ${session.encoding.displayName}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = strings.workingCopyNotice,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Encoding + Undo/Redo + Save Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SupportedEncoding.entries.forEach { enc ->
                FilterChip(
                    selected = session.encoding == enc,
                    onClick = { viewModel.openFileInTextEditor(session.sourcePath, enc, switchScreen = false) },
                    label = { Text(enc.displayName) }
                )
            }
            AssistChip(
                onClick = { viewModel.undoTextEdit() },
                enabled = session.undoDepth > 0,
                label = { Text("${strings.undoLabel} (${session.undoDepth}/${session.maxUndoLimit})") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undoLabel) },
                modifier = Modifier.testTag("text_undo_button")
            )
            AssistChip(
                onClick = { viewModel.redoTextEdit() },
                enabled = session.redoDepth > 0,
                label = { Text("${strings.redoLabel} (${session.redoDepth})") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.redoLabel) },
                modifier = Modifier.testTag("text_redo_button")
            )
        }

        // Find & Replace Bar (FR-ED-001)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = findQuery,
                onValueChange = { findQuery = it },
                placeholder = { Text(strings.findPlaceholder, style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("text_find_input")
            )
            OutlinedTextField(
                value = replaceQuery,
                onValueChange = { replaceQuery = it },
                placeholder = { Text(strings.replacePlaceholder, style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("text_replace_input")
            )
            FilterChip(
                selected = useRegex,
                onClick = { useRegex = !useRegex },
                label = { Text(strings.useRegexLabel) }
            )
            Icon(
                Icons.Default.FindReplace,
                contentDescription = strings.replacePlaceholder,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { viewModel.runTextFindAndReplace(findQuery, replaceQuery, useRegex) }
                    .padding(8.dp)
                    .testTag("text_find_replace_button")
            )
        }

        // Main Text Area with Line Count Summary
        OutlinedTextField(
            value = session.text,
            onValueChange = { viewModel.updateEditorText(it) },
            readOnly = session.isTruncatedReadOnly,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("text_editor_field")
        )

        // Safe Save Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.saveTextDocument(replaceOriginal = false) },
                modifier = Modifier.weight(1f).testTag("save_working_copy_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(strings.saveWorkingCopyLabel)
            }
            OutlinedButton(
                onClick = { showReplaceOriginalConfirm = true },
                modifier = Modifier.weight(1f).testTag("replace_original_button")
            ) {
                Text(strings.replaceOriginalLabel)
            }
        }
    }

    if (showReplaceOriginalConfirm) {
        AlertDialog(
            onDismissRequest = { showReplaceOriginalConfirm = false },
            title = { Text(strings.confirmDestructiveTitle) },
            text = { Text(strings.workingCopyNotice) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveTextDocument(replaceOriginal = true)
                        showReplaceOriginalConfirm = false
                    },
                    modifier = Modifier.testTag("confirm_replace_original_button")
                ) { Text(strings.confirmProceed) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceOriginalConfirm = false }) { Text(strings.confirmCancel) }
            }
        )
    }
}

@Composable
private fun HexEditorPane(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    window: HexPageWindow?,
    matches: List<Long>
) {
    var searchPattern by remember { mutableStateOf("48 44 52") }
    var isHexMode by remember { mutableStateOf(true) }
    var selectedOffsetForPatch by remember { mutableStateOf<Long?>(null) }

    if (window == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.emptyFolderMessage)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header & Page Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${File(window.filePath).name} (${window.fileSize} B)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Window Offset: 0x%08X .. 0x%08X • Staged Patches: ${window.stagedPatches.size}".format(
                        window.pageOffset,
                        (window.pageOffset + window.pageSize).coerceAtMost(window.fileSize)
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val prev = (window.pageOffset - window.pageSize).coerceAtLeast(0L)
                        viewModel.openFileInHexEditor(window.filePath, prev, switchScreen = false)
                    },
                    enabled = window.pageOffset > 0L,
                    modifier = Modifier.testTag("hex_prev_page_button")
                ) { Text("-256B") }
                OutlinedButton(
                    onClick = {
                        val next = (window.pageOffset + window.pageSize).coerceAtMost((window.fileSize - 1).coerceAtLeast(0L))
                        viewModel.openFileInHexEditor(window.filePath, next, switchScreen = false)
                    },
                    enabled = window.pageOffset + window.pageSize < window.fileSize,
                    modifier = Modifier.testTag("hex_next_page_button")
                ) { Text("+256B") }
            }
        }

        // Byte Pattern Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchPattern,
                onValueChange = { searchPattern = it },
                placeholder = { Text("50 4B 03 04 or ASCII", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("hex_search_input")
            )
            FilterChip(
                selected = isHexMode,
                onClick = { isHexMode = !isHexMode },
                label = { Text(if (isHexMode) "HEX" else "ASCII") }
            )
            Button(
                onClick = { viewModel.searchInHexFile(searchPattern, isHexMode) },
                modifier = Modifier.testTag("hex_search_button")
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        }

        if (matches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                matches.take(10).forEach { matchOffset ->
                    AssistChip(
                        onClick = {
                            viewModel.openFileInHexEditor(window.filePath, (matchOffset / 16) * 16, switchScreen = false)
                        },
                        label = { Text("Match @0x%06X".format(matchOffset)) }
                    )
                }
            }
        }

        // Hex Grid Table
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(8.dp)
                .testTag("hex_rows_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(window.rows, key = { it.offset }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = row.offsetHex,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.bytes.forEachIndexed { colIndex, byteVal ->
                            val absOffset = row.offset + colIndex
                            val isPatched = window.stagedPatches.containsKey(absOffset)
                            Text(
                                text = "%02X".format(byteVal.toInt() and 0xFF),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isPatched) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isPatched) FontWeight.ExtraBold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isPatched) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { selectedOffsetForPatch = absOffset }
                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                                    .testTag("hex_byte_$absOffset")
                            )
                        }
                    }
                    Text(
                        text = "| ${row.asciiRepresentation}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Commit Staged Hex Patches
        if (window.stagedPatches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.commitHexPatches(replaceOriginal = false) },
                    modifier = Modifier.weight(1f).testTag("commit_hex_working_copy_button")
                ) {
                    Text("${strings.saveWorkingCopyLabel} (${window.stagedPatches.size})")
                }
                OutlinedButton(
                    onClick = { viewModel.commitHexPatches(replaceOriginal = true) },
                    modifier = Modifier.weight(1f).testTag("commit_hex_replace_original_button")
                ) {
                    Text(strings.replaceOriginalLabel)
                }
            }
        }
    }

    selectedOffsetForPatch?.let { offset ->
        var hexInput by remember { mutableStateOf("FF") }
        AlertDialog(
            onDismissRequest = { selectedOffsetForPatch = null },
            title = { Text("Patch Byte @ 0x%08X".format(offset)) },
            text = {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(2).uppercase() },
                    label = { Text("Hex Byte (00..FF)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("hex_patch_byte_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stageHexBytePatch(offset, hexInput)
                        selectedOffsetForPatch = null
                    },
                    modifier = Modifier.testTag("confirm_hex_patch_button")
                ) { Text(strings.confirmProceed) }
            },
            dismissButton = {
                TextButton(onClick = { selectedOffsetForPatch = null }) { Text(strings.confirmCancel) }
            }
        )
    }
}

@Composable
private fun DiffToolPane(
    strings: AppStrings,
    report: FileComparisonReport?
) {
    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.emptyFolderMessage)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${File(report.leftPath).name}  ⇄  ${File(report.rightPath).name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+${report.addedCount} Added  •  -${report.removedCount} Removed  •  =${report.unchangedCount} Unchanged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Binary Metrics: ${report.binarySummary.differingBytesCount} differing bytes • First diff offset: ${
                        report.binarySummary.firstDiffOffset?.let { "0x%06X".format(it) } ?: "Identical"
                    }",
                    style = MaterialTheme.typography.labelMedium
                )
                report.limitNote?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("diff_lines_list"),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(report.textLines.size) { idx ->
                val line = report.textLines[idx]
                val prefix = when (line.type) {
                    DiffLineType.ADDED -> "+"
                    DiffLineType.REMOVED -> "-"
                    DiffLineType.UNCHANGED -> " "
                }
                val bgColor = when (line.type) {
                    DiffLineType.ADDED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    DiffLineType.REMOVED -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                    DiffLineType.UNCHANGED -> MaterialTheme.colorScheme.surface
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$prefix ${line.leftLineNumber ?: "-"}/${line.rightLineNumber ?: "-"}  ${line.content}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
