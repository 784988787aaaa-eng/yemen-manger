package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.core.i18n.AppStringsProvider
import com.example.core.model.FileEntry
import com.example.core.model.FileHashReport
import com.example.data.local.BookmarkEntity
import com.example.engines.apk.ApkAnalysisReport
import com.example.engines.system.ApkFunctionAction
import com.example.engines.system.DiskVolumeStats
import com.example.engines.system.VirtualArchiveBrowserEngine
import com.example.ui.viewmodel.FilePaneState
import com.example.ui.viewmodel.MainSection
import com.example.ui.viewmodel.YemenManagerViewModel
import java.io.File

@Composable
fun DualPaneFileManagerScreen(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    paneA: FilePaneState,
    paneB: FilePaneState,
    activePaneId: String,
    isSplitDualPane: Boolean,
    isDarkTheme: Boolean,
    bookmarks: List<BookmarkEntity>,
    activeHashReport: FileHashReport?,
    apkSummaryDialogReport: ApkAnalysisReport?
) {
    var showCreateMenuDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showRenameDialogFor by remember { mutableStateOf<FileEntry?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Screenshot-matching Context Menu & Open With & APK Function Dialogs
    var contextMenuEntry by remember { mutableStateOf<FileEntry?>(null) }
    var openWithEntry by remember { mutableStateOf<FileEntry?>(null) }
    var dexOpenWithEntry by remember { mutableStateOf<FileEntry?>(null) }
    var apkFunctionsTargetReport by remember { mutableStateOf<ApkAnalysisReport?>(null) }
    var optimizeApkTargetPath by remember { mutableStateOf<String?>(null) }
    var apkFunctionResultMessage by remember { mutableStateOf<String?>(null) }

    val safDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.openStorageLocation(viewModel.activePaneId.value, it) }
    }

    val activePane = if (activePaneId == "A") paneA else paneB
    val selectedEntries = activePane.entries.filter { activePane.selectedPaths.contains(it.uriOrPath) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dual_pane_file_manager")
    ) {
        if (paneA.isLoading || (isSplitDualPane && paneB.isLoading)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        // Slim Contextual Multi-Selection Bar when files are checked
        if (selectedEntries.isNotEmpty()) {
            Surface(
                color = HeaderCharcoalColor,
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contextual_action_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${selectedEntries.size}✓",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlueColor
                    )
                    TextButton(
                        onClick = { viewModel.copySelectedToOtherPane(overwrite = false) },
                        modifier = Modifier.testTag("copy_to_other_pane_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.actionCopyBullet, color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { viewModel.moveSelectedToOtherPane(overwrite = false) },
                        modifier = Modifier.testTag("move_to_other_pane_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.actionMoveBullet, color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { viewModel.createZipFromActiveSelection("bundle_${System.currentTimeMillis() % 10000}.zip") },
                        modifier = Modifier.testTag("zip_selected_button")
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.actionCompress, color = Color.White, fontSize = 12.sp)
                    }
                    if (selectedEntries.size == 1) {
                        val single = selectedEntries.first()
                        TextButton(
                            onClick = { contextMenuEntry = single },
                            modifier = Modifier.testTag("more_actions_selected_button")
                        ) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = AccentBlueColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.actionTools, color = AccentBlueColor, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = { showRenameDialogFor = single },
                            modifier = Modifier.testTag("rename_selected_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.actionRename, color = Color.White, fontSize = 12.sp)
                        }
                        if (!single.isDirectory) {
                            TextButton(
                                onClick = { viewModel.computeFileHashes(single.uriOrPath) },
                                modifier = Modifier.testTag("hash_selected_button")
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.actionProperties, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.testTag("delete_selected_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.actionDelete, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                    }
                }
            }
        }

        // Edge-to-Edge 50/50 Dual-Pane Split Workspace matching Screenshot_20261027-174524_ee3e8f40.jpg
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isSplitDualPane) {
                Row(modifier = Modifier.fillMaxSize()) {
                    CompactPaneColumn(
                        pane = paneA,
                        isActive = activePaneId == "A",
                        isDarkTheme = isDarkTheme,
                        strings = strings,
                        viewModel = viewModel,
                        onOpenEntry = { entry ->
                            handleEntryPrimaryOpen(
                                paneId = "A",
                                entry = entry,
                                viewModel = viewModel,
                                onShowDexDialog = { dexOpenWithEntry = it },
                                onShowOpenWithDialog = { openWithEntry = it }
                            )
                        },
                        onLongPressEntry = { entry ->
                            viewModel.setActivePane("A")
                            viewModel.selectSingleEntry("A", entry.uriOrPath)
                            contextMenuEntry = entry
                        },
                        onChooseFolder = {
                            viewModel.setActivePane("A")
                            safDocumentLauncher.launch(null)
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )

                    VerticalDivider(
                        thickness = 1.dp,
                        color = if (isDarkTheme) Color(0xFF2B3038) else Color(0xFFD0D4D9)
                    )

                    CompactPaneColumn(
                        pane = paneB,
                        isActive = activePaneId == "B",
                        isDarkTheme = isDarkTheme,
                        strings = strings,
                        viewModel = viewModel,
                        onOpenEntry = { entry ->
                            handleEntryPrimaryOpen(
                                paneId = "B",
                                entry = entry,
                                viewModel = viewModel,
                                onShowDexDialog = { dexOpenWithEntry = it },
                                onShowOpenWithDialog = { openWithEntry = it }
                            )
                        },
                        onLongPressEntry = { entry ->
                            viewModel.setActivePane("B")
                            viewModel.selectSingleEntry("B", entry.uriOrPath)
                            contextMenuEntry = entry
                        },
                        onChooseFolder = {
                            viewModel.setActivePane("B")
                            safDocumentLauncher.launch(null)
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                CompactPaneColumn(
                    pane = activePane,
                    isActive = true,
                    isDarkTheme = isDarkTheme,
                    strings = strings,
                    viewModel = viewModel,
                    onOpenEntry = { entry ->
                        handleEntryPrimaryOpen(
                            paneId = activePane.paneId,
                            entry = entry,
                            viewModel = viewModel,
                            onShowDexDialog = { dexOpenWithEntry = it },
                            onShowOpenWithDialog = { openWithEntry = it }
                        )
                    },
                    onLongPressEntry = { entry ->
                        viewModel.selectSingleEntry(activePane.paneId, entry.uriOrPath)
                        contextMenuEntry = entry
                    },
                    onChooseFolder = {
                        viewModel.setActivePane(activePane.paneId)
                        safDocumentLauncher.launch(null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = if (isDarkTheme) Color(0xFF2B3038) else Color(0xFFD8DCE0)
        )

        // Slim 5-Icon Bottom Toolbar matching Screenshot_20261027-174524_ee3e8f40.jpg:  <   >   +   ⇄   ↑
        Surface(
            color = if (isDarkTheme) Color(0xFF1E2228) else Color(0xFFFAFAFA),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("slim_bottom_toolbar")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateBackInHistory(activePaneId) },
                    modifier = Modifier.testTag("bottom_bar_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = { viewModel.navigateForwardInHistory(activePaneId) },
                    enabled = activePane.forwardStack.isNotEmpty(),
                    modifier = Modifier.testTag("bottom_bar_forward_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Forward",
                        tint = if (activePane.forwardStack.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                }
                IconButton(
                    onClick = { showCreateMenuDialog = true },
                    modifier = Modifier.testTag("bottom_bar_add_button")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = { viewModel.syncInactivePaneToActive() },
                    modifier = Modifier.testTag("bottom_bar_sync_panes_button")
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Sync Windows",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = { showBookmarksDialog = true },
                    modifier = Modifier.testTag("bottom_bar_bookmarks_up_button")
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Up / Bookmarks",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Minimal quick-create dialog: one name field and FILE / FOLDER actions.
    if (showCreateMenuDialog) {
        var itemName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateMenuDialog = false },
            title = { Text(if (strings.isArabic) "إنشاء" else "Create", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    singleLine = true,
                    placeholder = { Text(if (strings.isArabic) "اسم الملف أو المجلد" else "File or folder name") },
                    modifier = Modifier.fillMaxWidth().testTag("quick_create_name_input")
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (itemName.isNotBlank()) viewModel.createNewFileInActivePane(itemName.trim(), "")
                        showCreateMenuDialog = false
                    }, enabled = itemName.isNotBlank()) {
                        Text(if (strings.isArabic) "ملف" else "FILE", color = AccentBlueColor)
                    }
                    TextButton(onClick = {
                        if (itemName.isNotBlank()) viewModel.createFolderInActivePane(itemName.trim())
                        showCreateMenuDialog = false
                    }, enabled = itemName.isNotBlank()) {
                        Text(if (strings.isArabic) "مجلد" else "FOLDER", color = AccentBlueColor)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateMenuDialog = false }) {
                    Text(if (strings.isArabic) "إلغاء" else "CANCEL", color = AccentBlueColor)
                }
            }
        )
    }

    // 2. Bookmarks & Quick Navigation Dialog (triggered by ↑ button)
    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text(if (strings.isArabic) "المفضلة والانتقال السريع" else "Bookmarks & Quick Jump") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showBookmarksDialog = false
                            viewModel.navigateUp(activePaneId)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (strings.isArabic) "صعود للمجلد الأب (..)" else "Go to Parent Directory (..)")
                    }
                    OutlinedButton(
                        onClick = {
                            showBookmarksDialog = false
                            viewModel.toggleBookmark(activePane.currentPath)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.menuAddBookmark)
                    }
                    if (bookmarks.isNotEmpty()) {
                        HorizontalDivider()
                        LazyColumn(modifier = Modifier.height(160.dp)) {
                            items(bookmarks, key = { it.pathOrUri }) { bm ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showBookmarksDialog = false
                                            if (!bm.pathOrUri.startsWith("content://")) {
                                                viewModel.navigatePane(activePaneId, bm.pathOrUri)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = AccentBlueColor, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(bm.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text(bm.pathOrUri, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) {
                    Text(strings.confirmProceed, color = AccentBlueColor)
                }
            }
        )
    }

    // 3. New Folder Dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("New_Folder") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(strings.newFolder) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_folder_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createFolderInActivePane(folderName)
                        showNewFolderDialog = false
                    },
                    modifier = Modifier.testTag("confirm_new_folder_button")
                ) { Text(strings.confirmProceed) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text(strings.confirmCancel) }
            }
        )
    }

    // 4. New File Dialog
    if (showNewFileDialog) {
        var fileName by remember { mutableStateOf("notes.txt") }
        var fileContent by remember { mutableStateOf("# Yemen Manager Note\n") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(strings.newFile) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text(if (strings.isArabic) "اسم الملف" else "File name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text(if (strings.isArabic) "المحتوى الابتدائي" else "Initial content") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createNewFileInActivePane(fileName, fileContent)
                        showNewFileDialog = false
                    }
                ) { Text(strings.confirmProceed, color = AccentBlueColor) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text(strings.confirmCancel, color = AccentBlueColor) }
            }
        )
    }

    // 5. Rename Dialog
    showRenameDialogFor?.let { entry ->
        var newName by remember { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialogFor = null },
            title = { Text(strings.rename) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameFile(activePaneId, entry.uriOrPath, newName)
                        showRenameDialogFor = null
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) { Text(strings.confirmProceed) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogFor = null }) { Text(strings.confirmCancel) }
            }
        )
    }

    // 6. Delete / Recycle Bin Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(strings.confirmDestructiveTitle) },
            text = { Text(strings.confirmDeleteMessage) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.deleteSelected(moveToTrashOnly = true)
                            showDeleteConfirmDialog = false
                        },
                        modifier = Modifier.testTag("confirm_trash_button")
                    ) { Text(strings.moveToTrash) }
                    Button(
                        onClick = {
                            viewModel.deleteSelected(moveToTrashOnly = false)
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_permanent_delete_button")
                    ) { Text(strings.deleteSelected) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(strings.confirmCancel) }
            }
        )
    }

    // 7. File Context Action 2-Column Grid Dialog (Screenshot_20261027-163612_8a6573a1.jpg)
    contextMenuEntry?.let { entry ->
        FileContextActionGridDialog(
            entry = entry,
            strings = strings,
            onDismiss = { contextMenuEntry = null },
            onCopyToOtherPane = {
                viewModel.selectSingleEntry(activePaneId, entry.uriOrPath)
                viewModel.copySelectedToOtherPane(overwrite = false)
            },
            onMoveToOtherPane = {
                viewModel.selectSingleEntry(activePaneId, entry.uriOrPath)
                viewModel.moveSelectedToOtherPane(overwrite = false)
            },
            onDelete = {
                viewModel.selectSingleEntry(activePaneId, entry.uriOrPath)
                showDeleteConfirmDialog = true
            },
            onRename = { showRenameDialogFor = entry },
            onTools = { openWithEntry = entry },
            onCompress = {
                viewModel.selectSingleEntry(activePaneId, entry.uriOrPath)
                viewModel.createZipFromActiveSelection("${entry.name}.zip")
            },
            onProperties = {
                if (!entry.isDirectory) {
                    viewModel.computeFileHashes(entry.uriOrPath)
                }
            },
            onShare = {
                viewModel.shareFile(entry.uriOrPath)
            },
            onOpenWith = { openWithEntry = entry },
            onAddBookmark = { viewModel.toggleBookmark(entry.uriOrPath) }
        )
    }

    // 8. 3-Column Colorful "Open with..." Dialog (Screenshot_20261027-163643_acdc744f.jpg)
    openWithEntry?.let { entry ->
        OpenWithColorfulGridDialog(
            entry = entry,
            strings = strings,
            onDismiss = { openWithEntry = null },
            onSelectTool = { toolId ->
                when (toolId) {
                    "image_viewer" -> viewModel.openImagePreview(entry)
                    "text_editor", "xml_functions", "script_executor", "svg_functions" ->
                        viewModel.openFileInTextEditor(entry.uriOrPath)
                    "hex_editor", "font_viewer", "nine_patch", "class_functions" ->
                        viewModel.openFileInHexEditor(entry.uriOrPath)
                    "archive_viewer", "apks_functions", "jar_functions" -> {
                        val realPath = viewModel.resolveVirtualOrRealFilePath(entry.uriOrPath)
                        viewModel.viewArchiveOrApkInsidePane(activePaneId, realPath)
                    }
                    "apk_viewer" -> {
                        viewModel.analyzeApkFile(entry.uriOrPath, switchScreen = false, showSummaryDialog = true)
                    }
                    "dex_functions" -> {
                        dexOpenWithEntry = entry
                    }
                    "arsc_functions" -> {
                        if (entry.extension == "apk") {
                            viewModel.analyzeApkFile(entry.uriOrPath, switchScreen = true)
                        } else {
                            viewModel.openFileInHexEditor(entry.uriOrPath)
                        }
                    }
                    "comparison_result" -> {
                        val left = paneA.entries.firstOrNull { !it.isDirectory }?.uriOrPath ?: entry.uriOrPath
                        val right = paneB.entries.firstOrNull { !it.isDirectory }?.uriOrPath ?: entry.uriOrPath
                        viewModel.compareFiles(left, right, switchScreen = true)
                    }
                    else -> viewModel.openFileInTextEditor(entry.uriOrPath)
                }
            },
            onOpenExternalApp = { viewModel.openFileWithExternalApp(entry.uriOrPath) }
        )
    }

    // 9. Dex Open With Radio Dialog (Screenshot_20261027-163636_e2502d06.jpg)
    dexOpenWithEntry?.let { entry ->
        DexOpenWithDialog(
            dexEntryName = entry.name,
            strings = strings,
            onDismiss = { dexOpenWithEntry = null },
            onConfirmOption = { optionId ->
                when (optionId) {
                    "dex_editor_plus", "dex_editor", "dex2smali", "dex_property", "decrypt_dex_strings", "translation_mode" -> {
                        // If inside a virtual APK, analyze that parent APK and switch to APK Lab DEX tab
                        val parentApk = VirtualArchiveBrowserEngine.parseVirtualPath(entry.uriOrPath)?.first
                        if (parentApk != null) {
                            viewModel.analyzeApkFile(parentApk, switchScreen = true)
                        } else {
                            viewModel.selectSection(MainSection.APK_LAB)
                        }
                    }
                    else -> viewModel.openFileInHexEditor(entry.uriOrPath)
                }
            }
        )
    }

    // 10. APK Summary Info Dialog (Screenshot_20261027-163714_48b195c0.jpg)
    apkSummaryDialogReport?.let { report ->
        ApkSummaryInfoDialog(
            report = report,
            strings = strings,
            onDismiss = { viewModel.dismissApkSummaryDialog() },
            onClickFunctionButton = { apkFunctionsTargetReport = report },
            onClickViewInsidePane = {
                viewModel.viewArchiveOrApkInsidePane(activePaneId, report.apkPath)
            },
            onClickOpenFullApkLab = {
                viewModel.installApkFile(report.apkPath)
                viewModel.selectSection(MainSection.APK_LAB)
            }
        )
    }

    // 11. APK Functions Menu Dialog (Screenshot_20261027-163722_944be1af.jpg)
    apkFunctionsTargetReport?.let { report ->
        ApkFunctionsMenuDialog(
            strings = strings,
            onDismiss = { apkFunctionsTargetReport = null },
            onSelectFunction = { action ->
                if (action == ApkFunctionAction.OPTIMIZE_APK) {
                    optimizeApkTargetPath = report.apkPath
                } else {
                    apkFunctionResultMessage = viewModel.executeApkProFunction(action, report)
                }
            }
        )
    }

    // 12. Optimize APK Dialog (Screenshot_20261027-163734_0144b70b.jpg)
    optimizeApkTargetPath?.let { apkPath ->
        OptimizeApkDialog(
            strings = strings,
            onDismiss = { optimizeApkTargetPath = null },
            onConfirmOptimize = { options ->
                viewModel.runOptimizeApk(apkPath, options)
            }
        )
    }

    // 13. APK Function Result Summary Dialog
    apkFunctionResultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { apkFunctionResultMessage = null },
            title = { Text(if (strings.isArabic) "نتيجة معالجة APK" else "APK Function Result", fontWeight = FontWeight.Bold) },
            text = { Text(msg, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { apkFunctionResultMessage = null }) {
                    Text(strings.confirmProceed, color = AccentBlueColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 14. Properties & Cryptographic Hashes Dialog
    activeHashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.clearHashReport() },
            title = { Text(strings.propertiesAndHashes, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(File(report.uriOrPath).name, fontWeight = FontWeight.Bold)
                    Text("Size: ${DiskVolumeStats.formatSizeShort(report.sizeBytes)} (${report.sizeBytes} bytes)", style = MaterialTheme.typography.bodySmall)
                    Text("CRC32: ${report.crc32Hex}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                    Text("MD5: ${report.md5}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                    Text("SHA-1: ${report.sha1}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                    Text("SHA-256: ${report.sha256}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHashReport() }) {
                    Text(strings.confirmProceed, color = AccentBlueColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun handleEntryPrimaryOpen(
    paneId: String,
    entry: FileEntry,
    viewModel: YemenManagerViewModel,
    onShowDexDialog: (FileEntry) -> Unit,
    onShowOpenWithDialog: (FileEntry) -> Unit
) {
    viewModel.setActivePane(paneId)
    when {
        entry.isDirectory -> viewModel.navigatePane(paneId, entry.uriOrPath)
        entry.extension == "apk" -> viewModel.analyzeApkFile(entry.uriOrPath, switchScreen = false, showSummaryDialog = true)
        entry.extension == "zip" || entry.extension == "jar" || entry.extension == "apks" -> {
            val resolved = viewModel.resolveVirtualOrRealFilePath(entry.uriOrPath)
            viewModel.viewArchiveOrApkInsidePane(paneId, resolved)
        }
        entry.extension in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif") -> viewModel.openImagePreview(entry)
        entry.extension == "dex" -> onShowDexDialog(entry)
        entry.extension == "arsc" -> viewModel.openFileInHexEditor(entry.uriOrPath)
        entry.extension == "bin" || entry.extension == "so" -> viewModel.openFileInHexEditor(entry.uriOrPath)
        entry.extension in setOf("txt", "xml", "json", "md", "properties", "smali", "sh", "html", "css", "js") ->
            viewModel.openFileInTextEditor(entry.uriOrPath)
        else -> onShowOpenWithDialog(entry)
    }
}

@Composable
private fun CompactPaneColumn(
    pane: FilePaneState,
    isActive: Boolean,
    isDarkTheme: Boolean,
    strings: AppStrings,
    viewModel: YemenManagerViewModel,
    onOpenEntry: (FileEntry) -> Unit,
    onLongPressEntry: (FileEntry) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Active pane is bright white (#FFFFFF in light mode, #1F242B in dark mode);
    // Inactive pane is slightly shaded (#ECECEC in light mode, #14171C in dark mode) matching Screenshot_20261027-174524_ee3e8f40.jpg
    val paneBgColor = when {
        isDarkTheme && isActive -> Color(0xFF1F242B)
        isDarkTheme && !isActive -> Color(0xFF14171C)
        !isDarkTheme && isActive -> Color(0xFFFFFFFF)
        else -> Color(0xFFECECEC)
    }

    Column(
        modifier = modifier
            .background(paneBgColor)
            .clickable(interactionSource = null, indication = null) {
                viewModel.setActivePane(pane.paneId)
            }
            .testTag("file_pane_${pane.paneId}")
    ) {
        // Optional active search filter chip bar if search query is active
        if (pane.searchQuery.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentBlueColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔍 ${pane.searchQuery}",
                    fontSize = 11.sp,
                    color = AccentBlueColor,
                    maxLines = 1
                )
                Text(
                    text = "✕",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlueColor,
                    modifier = Modifier.clickable { viewModel.updatePaneSearch(pane.paneId, "") }
                )
            }
        }

        pane.errorBanner?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (pane.entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when {
                        pane.currentPath.isEmpty() || pane.errorBanner != null ->
                            if (strings.isArabic) "اختر مجلدًا حقيقيًا للوصول إلى ملفاته" else "Choose a real folder to access its files"
                        else -> if (strings.isArabic) "المجلد فارغ" else "This folder is empty"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                if (pane.currentPath.isEmpty() || pane.errorBanner != null) {
                    OutlinedButton(onClick = onChooseFolder) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (strings.isArabic) "اختيار مجلد من الجهاز" else "Choose device folder")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("pane_${pane.paneId}_list")
        ) {
            // Always show ".." parent directory row at the top of every pane, matching Screenshot_20261027-174524_ee3e8f40.jpg
            item(key = ".._parent_${pane.paneId}") {
                ParentDirectoryRow(
                    paneId = pane.paneId,
                    onNavigateUp = {
                        viewModel.setActivePane(pane.paneId)
                        viewModel.navigateUp(pane.paneId)
                    }
                )
            }

            items(pane.entries, key = { it.uriOrPath }) { entry ->
                val isSelected = pane.selectedPaths.contains(entry.uriOrPath)
                val isHighlighted = pane.highlightedPath == entry.uriOrPath
                CompactFileEntryRow(
                    entry = entry,
                    isSelected = isSelected || isHighlighted,
                    onToggleSelect = {
                        viewModel.setActivePane(pane.paneId)
                        viewModel.toggleSelection(pane.paneId, entry.uriOrPath)
                    },
                    onClick = { onOpenEntry(entry) },
                    onLongClick = { onLongPressEntry(entry) }
                )
            }
        }
    }
}

@Composable
private fun ParentDirectoryRow(
    paneId: String,
    onNavigateUp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateUp() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("pane_${paneId}_up_button"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(DrawerBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "..",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "..",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactFileEntryRow(
    entry: FileEntry,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val rowBg = if (isSelected) {
        AccentBlueColor.copy(alpha = 0.20f)
    } else {
        Color.Transparent
    }

    val (badgeColor, badgeText, badgeIcon) = remember(entry.isDirectory, entry.extension, isSelected) {
        when {
            isSelected -> Triple(AccentBlueColor, null, Icons.Default.Check)
            entry.isDirectory -> Triple(DrawerBadgeColor, null, Icons.Default.Folder)
            entry.extension == "apk" || entry.extension == "apks" -> Triple(Color(0xFF68B836), null, Icons.Default.Android)
            entry.extension in setOf("zip", "jar", "7z", "tar", "gz") -> Triple(Color(0xFF8D6E63), null, Icons.Default.Archive)
            entry.extension == "dex" -> Triple(Color(0xFF37BC9B), "01", Icons.Default.Description)
            entry.extension == "arsc" -> Triple(Color(0xFFF6BB42), null, Icons.Default.Layers)
            entry.extension == "xml" -> Triple(Color(0xFF4A89DC), "<>", Icons.Default.Description)
            entry.extension in setOf("bin", "so", "dat") -> Triple(Color(0xFF607D8B), "01", Icons.Default.Description)
            entry.extension in setOf("png", "jpg", "jpeg", "webp", "svg") -> Triple(Color(0xFF48CFAD), null, Icons.Default.Image)
            entry.extension in setOf("jks", "bks", "pk8", "rsa", "keystore") -> Triple(Color(0xFF607D8B), null, Icons.Default.Key)
            else -> Triple(Color(0xFF4A89DC), null, Icons.Default.Description)
        }
    }

    val subtitleText = remember(entry.lastModified, entry.sizeBytes, entry.isDirectory) {
        val dateStr = DiskVolumeStats.formatTimestampShort(entry.lastModified)
        if (entry.isDirectory) {
            dateStr
        } else {
            "$dateStr  ${DiskVolumeStats.formatSizeShort(entry.sizeBytes)}"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("file_item_${entry.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rounded-Square File Type Icon Badge (tapping badge selects/deselects item)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(badgeColor)
                .clickable { onToggleSelect() }
                .testTag("select_checkbox_${entry.name}"),
            contentAlignment = Alignment.Center
        ) {
            if (badgeText != null) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = entry.name,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                fontSize = 13.5.sp,
                fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    entry.extension == "apk" -> Color(0xFF2E7D32)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitleText,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
