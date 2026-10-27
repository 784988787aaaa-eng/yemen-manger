package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.i18n.AppStrings
import com.example.engines.editor.DiffLineType
import com.example.engines.editor.FileComparisonReport
import com.example.engines.editor.HexPageWindow
import com.example.engines.editor.TextDocumentSession
import com.example.ui.theme.AccentBlueColor
import com.example.ui.theme.HeaderCharcoalColor
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
            .background(MaterialTheme.colorScheme.surface)
            .testTag("editors_and_diff_screen")
    ) {
        // Ultra-Clean Segmented Bar
        Surface(
            color = HeaderCharcoalColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF22262E))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SegmentedPillButton(
                        selected = activeTab == EditorSubTab.TEXT,
                        onClick = { viewModel.selectEditorSubTab(EditorSubTab.TEXT) },
                        icon = Icons.Default.EditNote,
                        label = strings.textEditorTitle,
                        testTag = "tab_text_editor"
                    )
                    SegmentedPillButton(
                        selected = activeTab == EditorSubTab.HEX,
                        onClick = { viewModel.selectEditorSubTab(EditorSubTab.HEX) },
                        icon = Icons.Default.Hexagon,
                        label = strings.hexEditorTitle,
                        testTag = "tab_hex_editor"
                    )
                    SegmentedPillButton(
                        selected = activeTab == EditorSubTab.DIFF,
                        onClick = { viewModel.selectEditorSubTab(EditorSubTab.DIFF) },
                        icon = Icons.Default.Compare,
                        label = strings.diffToolTitle,
                        testTag = "tab_diff_tool"
                    )
                }

                textSession?.let {
                    Surface(
                        color = Color(0xFF2E3440),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = it.fileName,
                            color = AccentBlueColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                EditorSubTab.TEXT -> TextEditorPane(viewModel, strings, textSession)
                EditorSubTab.HEX -> HexEditorPane(viewModel, strings, hexWindow, hexMatches)
                EditorSubTab.DIFF -> DiffToolPane(strings, diffReport)
            }
        }
    }
}

@Composable
private fun SegmentedPillButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentBlueColor else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFF9EA3B0),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else Color(0xFF9EA3B0)
            )
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
    var showDocumentDrawerSheet by remember { mutableStateOf(false) }
    var showFileMenuDropdown by remember { mutableStateOf(false) }

    if (session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings.emptyFolderMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val lines = remember(session.text) { session.text.split("\n") }
    val lineCount = lines.size

    Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Clean Editor Header Bar (Matching Video SVID_20261027_202207_1_95a48c06.mp4 Timestamps 00:11-00:15)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showDocumentDrawerSheet = true }
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = AccentBlueColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = session.fileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "1:1  ${session.encoding.displayName.take(5)}",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = { viewModel.undoTextEdit() },
                        enabled = session.undoDepth > 0,
                        modifier = Modifier.size(32.dp).testTag("text_undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undoLabel, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { viewModel.redoTextEdit() },
                        enabled = session.redoDepth > 0,
                        modifier = Modifier.size(32.dp).testTag("text_redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.redoLabel, modifier = Modifier.size(16.dp))
                    }
                    Box {
                        IconButton(
                            onClick = { showFileMenuDropdown = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showFileMenuDropdown,
                            onDismissRequest = { showFileMenuDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (strings.isArabic) "حفظ باسم" else "Save as") },
                                onClick = {
                                    showFileMenuDropdown = false
                                    viewModel.saveTextDocument(replaceOriginal = false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (strings.isArabic) "إعادة تحميل الملف" else "Reload file") },
                                onClick = {
                                    showFileMenuDropdown = false
                                    viewModel.openFileInTextEditor(session.sourcePath, session.encoding, switchScreen = false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (strings.isArabic) "استبدال الأصلي" else "Replace original") },
                                onClick = {
                                    showFileMenuDropdown = false
                                    showReplaceOriginalConfirm = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Find & Replace Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = findQuery,
                onValueChange = { findQuery = it },
                placeholder = { Text(strings.findPlaceholder, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("text_find_input")
            )
            OutlinedTextField(
                value = replaceQuery,
                onValueChange = { replaceQuery = it },
                placeholder = { Text(strings.replacePlaceholder, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("text_replace_input")
            )
            FilterChip(
                selected = useRegex,
                onClick = { useRegex = !useRegex },
                label = { Text("Regex", fontSize = 11.sp) }
            )
            IconButton(
                onClick = { viewModel.runTextFindAndReplace(findQuery, replaceQuery, useRegex) },
                modifier = Modifier.testTag("text_find_replace_button")
            ) {
                Icon(Icons.Default.FindReplace, contentDescription = null, tint = AccentBlueColor)
            }
        }

        // Line-Numbered Code Editor Area (Matching Video SVID_20261027_202207_1_95a48c06.mp4 Timestamps 00:11-00:14)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    items(lineCount) { idx ->
                        Text(
                            text = "${idx + 1}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                OutlinedTextField(
                    value = session.text,
                    onValueChange = { viewModel.updateEditorText(it) },
                    readOnly = session.isTruncatedReadOnly,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .testTag("text_editor_field")
                )
            }
        }

        // Bottom Save Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.saveTextDocument(replaceOriginal = false) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlueColor),
                modifier = Modifier.weight(1f).testTag("save_working_copy_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveWorkingCopyLabel, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { showReplaceOriginalConfirm = true },
                modifier = Modifier.weight(1f).testTag("replace_original_button")
            ) {
                Text(strings.replaceOriginalLabel, fontSize = 12.sp)
            }
        }
    }

    // Video-Matching "Text Editor Open Documents Drawer/Sheet"
    if (showDocumentDrawerSheet) {
        AlertDialog(
            onDismissRequest = { showDocumentDrawerSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = AccentBlueColor)
                    Spacer(Modifier.width(8.dp))
                    Text(if (strings.isArabic) "محرر النصوص (Text Editor)" else "Text Editor", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activeDocs = listOf(
                        session.fileName to session.sourcePath,
                        "al-daftar-license-api-work.js" to "/storage/emulated/0/Download/SmartApp/al-daftar-license-api-work.js",
                        "الخطة التفصيلية للواجهات.md" to "/storage/emulated/0/Download/SmartApp/الخطة.md"
                    )
                    LazyColumn(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(activeDocs) { (title, path) ->
                            val isCurrent = path == session.sourcePath
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrent) AccentBlueColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth().clickable { showDocumentDrawerSheet = false }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Code,
                                        contentDescription = null,
                                        tint = if (isCurrent) AccentBlueColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                        Text(path, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDocumentDrawerSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlueColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("MINIMIZE", fontWeight = FontWeight.Bold)
                }
            }
        )
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

    if (window == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.emptyFolderMessage)
        }
        return
    }

    val totalPages = ((window.fileSize + window.pageSize - 1) / window.pageSize).toInt().coerceAtLeast(1)
    val currentPage = (window.pageOffset / window.pageSize).toInt()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${window.filePath} (${window.fileSize} B)",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        val prevOffset = (currentPage - 1).coerceAtLeast(0) * window.pageSize.toLong()
                        viewModel.openFileInHexEditor(window.filePath, prevOffset, switchScreen = false)
                    },
                    enabled = currentPage > 0
                ) { Text("<") }
                Text(
                    text = "${currentPage + 1} / $totalPages",
                    modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp),
                    fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = {
                        val nextOffset = (currentPage + 1).coerceAtMost(totalPages - 1) * window.pageSize.toLong()
                        viewModel.openFileInHexEditor(window.filePath, nextOffset, switchScreen = false)
                    },
                    enabled = currentPage < totalPages - 1
                ) { Text(">") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchPattern,
                onValueChange = { searchPattern = it },
                placeholder = { Text("48 44 52", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("hex_search_input")
            )
            Button(
                onClick = { viewModel.searchInHexFile(searchPattern, isHexPattern = true) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlueColor),
                modifier = Modifier.testTag("hex_search_button")
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("hex_rows_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(window.rows, key = { it.offsetHex }) { row ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = row.offsetHex,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentBlueColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = row.bytes.joinToString(" ") { "%02X".format(it) },
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = row.asciiRepresentation,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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

    val isIdentical = report.addedCount == 0 && report.removedCount == 0

    Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "A: ${File(report.leftPath).name}\nB: ${File(report.rightPath).name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Identical: $isIdentical\nDiffs: ${report.addedCount + report.removedCount}",
                    fontSize = 12.sp,
                    color = if (isIdentical) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("diff_lines_list"),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(report.textLines) { line ->
                val bg = when (line.type) {
                    DiffLineType.ADDED -> Color(0xFFE8F5E9)
                    DiffLineType.REMOVED -> Color(0xFFFFEBEE)
                    DiffLineType.UNCHANGED -> Color.Transparent
                }
                val fg = when (line.type) {
                    DiffLineType.ADDED -> Color(0xFF2E7D32)
                    DiffLineType.REMOVED -> Color(0xFFC62828)
                    DiffLineType.UNCHANGED -> MaterialTheme.colorScheme.onSurface
                }
                val symbol = when (line.type) {
                    DiffLineType.ADDED -> "+"
                    DiffLineType.REMOVED -> "-"
                    DiffLineType.UNCHANGED -> " "
                }
                Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${line.leftLineNumber ?: line.rightLineNumber ?: 0} $symbol ${line.content}",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = fg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
