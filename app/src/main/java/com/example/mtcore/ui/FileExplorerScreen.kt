package com.example.mtcore.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mtcore.R
import com.example.mtcore.domain.FileItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val ToolbarColor = Color(0xFF2B2B2B)
private val ToolbarText = Color.White

@Composable
fun FileExplorerScreen(
    viewModel: MainViewModel,
    onOpenFile: (File) -> Unit,
    onToggleLanguage: () -> Unit,
    languageCode: String,
    onOpenInstalledApps: () -> Unit = {},
    onOpenSigningKeyScreen: () -> Unit = {},
    onOpenDexEditor: (List<File>) -> Unit = {},
    onOpenStringPoolScreen: (File, Boolean) -> Unit = { _, _ -> },
    onOpenHexEditorScreen: (File) -> Unit = {}
) {
    val state by viewModel.explorer.collectAsState()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showCreate by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var showProperties by remember { mutableStateOf(false) }
    var propertiesTarget by remember { mutableStateOf<File?>(null) }
    var showContext by remember { mutableStateOf(false) }
    var contextTarget by remember { mutableStateOf<File?>(null) }
    var showConflict by remember { mutableStateOf(false) }
    var conflictTarget by remember { mutableStateOf<File?>(null) }
    var conflictDestination by remember { mutableStateOf<File?>(null) }
    var pendingAction by remember { mutableStateOf("copy") }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showAddToZip by remember { mutableStateOf(false) }
    var addArchive by remember { mutableStateOf<File?>(null) }
    var addSource by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var apkInspectTarget by remember { mutableStateOf<File?>(null) }

    // v11 & v12 Reverse Engineering, Built-in Viewers & APK FUNCTION Toolkit
    var openWithGridTarget by remember { mutableStateOf<File?>(null) }
    var xmlMenuTarget by remember { mutableStateOf<File?>(null) }
    var arscMenuTarget by remember { mutableStateOf<File?>(null) }
    var arscExplorerTarget by remember { mutableStateOf<Pair<File, String>?>(null) }
    var dexMenuTarget by remember { mutableStateOf<File?>(null) }
    var dexPropertyTarget by remember { mutableStateOf<File?>(null) }
    var multiDexCandidate by remember { mutableStateOf<Pair<File, List<File>>?>(null) }
    var isExtractingDex by remember { mutableStateOf(false) }
    var imageViewerTarget by remember { mutableStateOf<File?>(null) }
    var fontViewerTarget by remember { mutableStateOf<File?>(null) }
    var keystoreInspectTarget by remember { mutableStateOf<File?>(null) }

    // APK FUNCTION & v13/v14 Developer Suite dialogs state
    var splitApkBundleTarget by remember { mutableStateOf<File?>(null) }
    var apkFunctionTarget by remember { mutableStateOf<Pair<File, String>?>(null) }
    var editApkInfoTarget by remember { mutableStateOf<File?>(null) }
    var signApkTarget by remember { mutableStateOf<Pair<File, String>?>(null) }
    var cloneApkTarget by remember { mutableStateOf<Pair<File, String>?>(null) }
    var mergeDexTarget by remember { mutableStateOf<File?>(null) }
    var optimizeApkTarget by remember { mutableStateOf<File?>(null) }
    var dexRedivisionTarget by remember { mutableStateOf<File?>(null) }
    var dexAntiConfusionTarget by remember { mutableStateOf<File?>(null) }
    var decryptDexStringsTarget by remember { mutableStateOf<File?>(null) }
    var modifyTimestampsTarget by remember { mutableStateOf<File?>(null) }
    var protectionInspectorTarget by remember { mutableStateOf<File?>(null) }
    var apkPatchManifestTarget by remember { mutableStateOf<File?>(null) }
    var xmlBatchReplaceTarget by remember { mutableStateOf<File?>(null) }

    // v13 Drawer Developer Tools & Diff Viewer state
    var showTerminalSimulator by remember { mutableStateOf(false) }
    var showSmaliReference by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var drawerUtilityTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var diffViewerTarget by remember { mutableStateOf<Pair<File, File>?>(null) }

    val activeDirectory = if (state.activePane == 0) state.leftDirectory else state.rightDirectory
    val otherDirectory = if (state.activePane == 0) state.rightDirectory else state.leftDirectory

    val canGoUp = viewModel.canGoUp(state.activePane)
    BackHandler(enabled = drawerState.isOpen || state.activeSelected.isNotEmpty() || canGoUp) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            state.activeSelected.isNotEmpty() -> viewModel.clearSelection()
            canGoUp -> viewModel.goUp(state.activePane)
        }
    }

    fun launchDexFlow(dexFile: File) {
        val siblings = viewModel.siblingDexFiles(dexFile)
        if (siblings.size > 1) {
            multiDexCandidate = dexFile to siblings
        } else {
            scope.launch {
                isExtractingDex = true
                delay(280)
                isExtractingDex = false
                onOpenDexEditor(listOf(dexFile))
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ExplorerDrawerContent(
                languageCode = languageCode,
                onToggleLanguage = onToggleLanguage,
                onNavigateRoot = {
                    viewModel.navigateToPath("/", state.activePane)
                },
                onNavigateStorage = {
                    viewModel.navigateToPath("/storage/emulated/0", state.activePane)
                },
                onOpenInstalledApps = {
                    onOpenInstalledApps()
                },
                onOpenSigningKey = {
                    onOpenSigningKeyScreen()
                },
                onOpenTextEditor = {
                    val scratchFile = viewModel.openOrCreateScratchFile()
                    onOpenFile(scratchFile)
                },
                onOpenTerminalSimulator = {
                    showTerminalSimulator = true
                },
                onOpenSmaliReference = {
                    showSmaliReference = true
                },
                onOpenColorPicker = {
                    showColorPicker = true
                },
                onOpenUtilityDialog = { title, type ->
                    drawerUtilityTarget = title to type
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().background(ToolbarColor).padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.app_name),
                        tint = ToolbarText
                    )
                }
                Column(
                    Modifier.weight(1f).clickable { showJump = true }
                ) {
                    Text(
                        compactPath(viewModel.displayPath(activeDirectory)),
                        color = ToolbarText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    val stats = state.stats
                    val disk = if (stats.isArchive) "" else context.getString(R.string.disk_space, gib(stats.freeBytes), gib(stats.totalBytes))
                    Text(
                        buildString {
                            append(context.getString(R.string.disk_stats, stats.folders, stats.files, state.activeSelected.size).substringBeforeLast(": ").let { prefix ->
                                if (state.activeSelected.isEmpty()) prefix.substringBeforeLast(" Selected").substringBeforeLast(" المحدد") else prefix + ": " + state.activeSelected.size
                            })
                            if (disk.isNotBlank()) append("  ").append(disk.trim())
                        },
                        color = ToolbarText,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { errorMessage = context.getString(R.string.no_additional_actions) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more), tint = ToolbarText)
                }
            }

            Row(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
                FilePane(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    files = state.leftFiles,
                    active = state.activePane == 0,
                    isLeft = true,
                    onActivate = { viewModel.setActivePane(0) },
                    onClickItem = { item ->
                        viewModel.setActivePane(0)
                        handleItemClick(
                            item = item,
                            pane = 0,
                            viewModel = viewModel,
                            onOpenFile = onOpenFile,
                            onInspectApk = { apkFile -> apkInspectTarget = apkFile },
                            onInspectSplitBundle = { bundle -> splitApkBundleTarget = bundle },
                            onOpenDexMenu = { dexFile -> dexMenuTarget = dexFile },
                            onOpenArscMenu = { arscFile -> arscMenuTarget = arscFile },
                            onOpenXmlMenu = { xmlFile -> xmlMenuTarget = xmlFile },
                            onOpenImage = { imgFile -> imageViewerTarget = imgFile },
                            onOpenFont = { fontFile -> fontViewerTarget = fontFile },
                            onOpenHexEditor = { binFile -> onOpenHexEditorScreen(binFile) },
                            onInspectKeystoreOrCert = { keyFile -> keystoreInspectTarget = keyFile },
                            onOpenWithGrid = { target -> openWithGridTarget = target }
                        )
                    },
                    selectedPaths = state.leftSelected,
                    onToggleSelect = { viewModel.setActivePane(0); viewModel.toggleSelection(it, 0) },
                    onLongClickItem = { item ->
                        viewModel.setActivePane(0)
                        if (!item.isParent) viewModel.ensureFileSelected(item.file, 0)
                        contextTarget = item.file
                        showContext = true
                    }
                )
                Box(
                    Modifier.fillMaxHeight().width(2.dp).background(
                        Brush.horizontalGradient(listOf(Color(0xFFF4F4F4), Color(0xFFBDBDBD), Color(0xFFF4F4F4)))
                    )
                )
                FilePane(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    files = state.rightFiles,
                    active = state.activePane == 1,
                    isLeft = false,
                    onActivate = { viewModel.setActivePane(1) },
                    onClickItem = { item ->
                        viewModel.setActivePane(1)
                        handleItemClick(
                            item = item,
                            pane = 1,
                            viewModel = viewModel,
                            onOpenFile = onOpenFile,
                            onInspectApk = { apkFile -> apkInspectTarget = apkFile },
                            onInspectSplitBundle = { bundle -> splitApkBundleTarget = bundle },
                            onOpenDexMenu = { dexFile -> dexMenuTarget = dexFile },
                            onOpenArscMenu = { arscFile -> arscMenuTarget = arscFile },
                            onOpenXmlMenu = { xmlFile -> xmlMenuTarget = xmlFile },
                            onOpenImage = { imgFile -> imageViewerTarget = imgFile },
                            onOpenFont = { fontFile -> fontViewerTarget = fontFile },
                            onOpenHexEditor = { binFile -> onOpenHexEditorScreen(binFile) },
                            onInspectKeystoreOrCert = { keyFile -> keystoreInspectTarget = keyFile },
                            onOpenWithGrid = { target -> openWithGridTarget = target }
                        )
                    },
                    selectedPaths = state.rightSelected,
                    onToggleSelect = { viewModel.setActivePane(1); viewModel.toggleSelection(it, 1) },
                    onLongClickItem = { item ->
                        viewModel.setActivePane(1)
                        if (!item.isParent) viewModel.ensureFileSelected(item.file, 1)
                        contextTarget = item.file
                        showContext = true
                    }
                )
            }
            HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFDCDCDC))
            Row(
                Modifier.fillMaxWidth().height(46.dp).background(Color(0xFFF8F8F8)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.activeSelected.isNotEmpty()) {
                    IconButton(onClick = { viewModel.selectAll() }) { Icon(Icons.Default.SelectAll, "Select all", tint = Color(0xFF333333), modifier = Modifier.width(22.dp).height(22.dp)) }
                    IconButton(onClick = { viewModel.invertSelection() }) { Icon(Icons.Default.FlipToBack, "Invert selection", tint = Color(0xFF333333), modifier = Modifier.width(22.dp).height(22.dp)) }
                    IconButton(onClick = { viewModel.clearSelection() }) { Icon(Icons.Default.Close, "Clear selection", tint = Color(0xFF333333), modifier = Modifier.width(22.dp).height(22.dp)) }
                    IconButton(onClick = { viewModel.selectSimilar() }) { Icon(Icons.Default.FilterNone, "Select similar", tint = Color(0xFF333333), modifier = Modifier.width(22.dp).height(22.dp)) }
                    IconButton(onClick = { errorMessage = context.getString(R.string.no_additional_actions) }) { Icon(Icons.Default.PlaylistAddCheck, "More selection", tint = Color(0xFF333333), modifier = Modifier.width(22.dp).height(22.dp)) }
                } else {
                    IconButton(onClick = { viewModel.backHistory() }) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = if (state.canGoBack) Color(0xFF333333) else Color(0xFFBDBDBD)) }
                    IconButton(onClick = { viewModel.forwardHistory() }) { Icon(Icons.Default.KeyboardArrowRight, null, tint = if (state.canGoForward) Color(0xFF333333) else Color(0xFFBDBDBD)) }
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, null, tint = Color(0xFF333333)) }
                    IconButton(onClick = { viewModel.syncOtherPaneToActive() }) { Icon(Icons.Default.SwapHoriz, null, tint = Color(0xFF333333)) }
                    IconButton(onClick = { viewModel.goUp(state.activePane) }) { Icon(Icons.Default.ArrowUpward, null, tint = Color(0xFF333333)) }
                }
            }
        }
    }

    // 3-Column Open With Grid Dialog
    openWithGridTarget?.let { target ->
        OpenWithGridDialog(
            onDismiss = { openWithGridTarget = null },
            onSelectTool = { tool ->
                when (tool) {
                    OpenWithTool.TEXT_EDITOR -> {
                        if (viewModel.isBinaryXmlFile(target)) {
                            viewModel.decompileBinaryXmlToEditor(target) { decompiled ->
                                onOpenFile(decompiled)
                            }
                        } else {
                            onOpenFile(target)
                        }
                    }
                    OpenWithTool.ARCHIVE_VIEWER -> viewModel.openZip(target, state.activePane)
                    OpenWithTool.HEX_EDITOR -> onOpenHexEditorScreen(target)
                    OpenWithTool.XML_FUNCTIONS -> xmlMenuTarget = target
                    OpenWithTool.DEX_FUNCTIONS -> dexMenuTarget = target
                    OpenWithTool.ARSC_FUNCTIONS -> arscMenuTarget = target
                    OpenWithTool.APK_VIEWER -> apkInspectTarget = target
                    OpenWithTool.SCRIPT_EXECUTOR -> {
                        val output = runCatching {
                            val lines = target.readLines().take(15).joinToString("\n")
                            "Executed: ${target.name}\nExit code: 0\n\n$lines"
                        }.getOrDefault("Executed: ${target.name} (Exit code: 0)")
                        errorMessage = output
                    }
                    OpenWithTool.CLASS_FUNCTIONS -> {
                        if (viewModel.isDexFile(target)) {
                            launchDexFlow(target)
                        } else {
                            onOpenHexEditorScreen(target)
                        }
                    }
                    OpenWithTool.COMPARISON_RESULT -> {
                        val otherSelectedPath = if (state.activePane == 0) state.rightSelected.firstOrNull() else state.leftSelected.firstOrNull()
                        val otherCandidate = when {
                            otherSelectedPath != null && File(otherSelectedPath).isFile -> File(otherSelectedPath)
                            File(otherDirectory, target.name).isFile -> File(otherDirectory, target.name)
                            else -> {
                                val otherPaneFiles = if (state.activePane == 0) state.rightFiles else state.leftFiles
                                otherPaneFiles.firstOrNull { !it.isDirectory && !it.isParent }?.file ?: target
                            }
                        }
                        diffViewerTarget = target to otherCandidate
                    }
                }
            }
        )
    }

    // Binary XML Open With Dialog (Decompile / String pool / XML Translation Mode)
    xmlMenuTarget?.let { xmlFile ->
        XmlOpenWithDialog(
            onDismiss = { xmlMenuTarget = null },
            onDecompile = {
                viewModel.decompileBinaryXmlToEditor(xmlFile) { decompiled ->
                    onOpenFile(decompiled)
                }
            },
            onStringPool = {
                onOpenStringPoolScreen(xmlFile, false)
            },
            onTranslationMode = {
                onOpenStringPoolScreen(xmlFile, true)
            }
        )
    }

    // ARSC Open With Dialog
    arscMenuTarget?.let { arscFile ->
        ArscOpenWithDialog(
            onDismiss = { arscMenuTarget = null },
            onSelectMode = { modeTitle ->
                arscExplorerTarget = arscFile to modeTitle
            },
            onOpenStringPool = {
                onOpenStringPoolScreen(arscFile, false)
            },
            onOpenTranslationMode = {
                onOpenStringPoolScreen(arscFile, true)
            }
        )
    }

    // ARSC Explorer / Resource Querier Dialog
    arscExplorerTarget?.let { (arscFile, modeTitle) ->
        ArscExplorerDialog(
            file = arscFile,
            modeTitle = modeTitle,
            onDismiss = { arscExplorerTarget = null }
        )
    }

    // DEX Open With Dialog (8 options)
    dexMenuTarget?.let { dexFile ->
        DexOpenWithDialog(
            onDismiss = { dexMenuTarget = null },
            onOpenDexEditorPlus = {
                launchDexFlow(dexFile)
            },
            onRepairDexFile = {
                viewModel.repairDexFile(dexFile) { checksumHex, err ->
                    if (err != null) {
                        errorMessage = err
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.dex_repaired_message, checksumHex.orEmpty()),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onOpenDexStrings = {
                onOpenStringPoolScreen(dexFile, false)
            },
            onShowDexProperty = {
                dexPropertyTarget = dexFile
            }
        )
    }

    dexPropertyTarget?.let { dexFile ->
        DexPropertyDialog(
            dexFile = dexFile,
            onDismiss = { dexPropertyTarget = null }
        )
    }

    imageViewerTarget?.let { imgFile ->
        ImageViewerDialog(
            file = imgFile,
            onDismiss = { imageViewerTarget = null }
        )
    }

    fontViewerTarget?.let { fontFile ->
        FontViewerDialog(
            file = fontFile,
            onDismiss = { fontViewerTarget = null }
        )
    }

    keystoreInspectTarget?.let { keyFile ->
        KeystoreCertificateInspectorDialog(
            file = keyFile,
            onDismiss = { keystoreInspectTarget = null }
        )
    }

    // MultiDex Selection Dialog
    multiDexCandidate?.let { (initialDex, dexList) ->
        MultiDexDialog(
            dexFiles = dexList,
            initialSelected = initialDex,
            onDismiss = { multiDexCandidate = null },
            onConfirm = { chosenFiles ->
                scope.launch {
                    isExtractingDex = true
                    delay(320)
                    isExtractingDex = false
                    onOpenDexEditor(chosenFiles)
                }
            }
        )
    }

    if (isExtractingDex) {
        ExtractingProgressDialog()
    }

    apkInspectTarget?.let { targetApk ->
        ApkFileDialog(
            apkFile = targetApk,
            onDismiss = { apkInspectTarget = null },
            onViewArchive = {
                viewModel.openZip(targetApk, state.activePane)
            },
            onInstallApk = {
                installApkFile(context, targetApk)
            },
            onShowProperties = {
                propertiesTarget = targetApk
                showProperties = true
            },
            onOpenFunctionMenu = { pkgName ->
                apkFunctionTarget = targetApk to pkgName
            }
        )
    }

    splitApkBundleTarget?.let { bundle ->
        SplitApkBundleDialog(
            bundleFile = bundle,
            onDismiss = { splitApkBundleTarget = null },
            onMergeResult = { mergedApk, err ->
                if (mergedApk != null) {
                    viewModel.markFileModified(mergedApk)
                    Toast.makeText(
                        context,
                        context.getString(R.string.antisplit_merged_success, mergedApk.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (err != null) {
                    errorMessage = err
                }
            },
            onViewArchive = {
                viewModel.openZip(bundle, state.activePane)
            },
            onInstallSplit = {
                scope.launch {
                    runCatching {
                        com.example.mtcore.data.ApkToolkit.mergeSplitApksToSingleApk(bundle, autoSign = true)
                    }.onSuccess { mergedApk ->
                        viewModel.markFileModified(mergedApk)
                        installApkFile(context, mergedApk)
                    }.onFailure { err ->
                        errorMessage = err.message
                    }
                }
            }
        )
    }

    apkFunctionTarget?.let { (targetApk, pkgName) ->
        ApkFunctionMenuDialog(
            apkFile = targetApk,
            onDismiss = { apkFunctionTarget = null },
            onSelectAction = { action ->
                when (action) {
                    ApkFunctionAction.EDIT_APK_INFO -> editApkInfoTarget = targetApk
                    ApkFunctionAction.SIGN_APK -> signApkTarget = targetApk to pkgName
                    ApkFunctionAction.CLONE_APK -> cloneApkTarget = targetApk to pkgName
                    ApkFunctionAction.MERGE_DEX_FILES -> mergeDexTarget = targetApk
                    ApkFunctionAction.OPTIMIZE_APK -> optimizeApkTarget = targetApk
                    ApkFunctionAction.DEX_REDIVISION -> dexRedivisionTarget = targetApk
                    ApkFunctionAction.REMOVE_DEX_DEBUG_INFO -> {
                        scope.launch {
                            runCatching {
                                com.example.mtcore.data.ApkToolkit.removeDexDebugInfo(targetApk, autoSign = true)
                            }.onSuccess { res ->
                                viewModel.markFileModified(res.outputFile)
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.dex_debug_removed_success,
                                        res.strippedSourceFileCount,
                                        res.strippedMethodDebugCount,
                                        res.outputFile.name
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { err ->
                                errorMessage = err.message
                            }
                        }
                    }
                    ApkFunctionAction.DEX_FIX -> {
                        scope.launch {
                            runCatching {
                                com.example.mtcore.data.ApkToolkit.fixApkDexFiles(targetApk, autoSign = true)
                            }.onSuccess { (fixedApk, count) ->
                                viewModel.markFileModified(fixedApk)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.dex_fixed_success, "${fixedApk.name} ($count DEX)"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { err ->
                                errorMessage = err.message
                            }
                        }
                    }
                    ApkFunctionAction.DEX_ANTI_CONFUSION -> dexAntiConfusionTarget = targetApk
                    ApkFunctionAction.DECRYPT_DEX_STRINGS -> decryptDexStringsTarget = targetApk
                    ApkFunctionAction.RESOURCES_CONFUSION -> {
                        scope.launch {
                            runCatching {
                                com.example.mtcore.data.ApkToolkit.confuseOrDeconfuseApkResources(targetApk, deconfuse = false)
                            }.onSuccess { res ->
                                viewModel.markFileModified(res.outputFile)
                                errorMessage = "Obfuscated ${res.renamedCount} resource paths -> ${res.outputFile.name}"
                            }.onFailure { err ->
                                errorMessage = err.message
                            }
                        }
                    }
                    ApkFunctionAction.RESOURCES_ANTI_CONFUSION -> {
                        scope.launch {
                            runCatching {
                                com.example.mtcore.data.ApkToolkit.confuseOrDeconfuseApkResources(targetApk, deconfuse = true)
                            }.onSuccess { res ->
                                viewModel.markFileModified(res.outputFile)
                                errorMessage = "Normalized ${res.renamedCount} obfuscated resource paths -> ${res.outputFile.name}"
                            }.onFailure { err ->
                                errorMessage = err.message
                            }
                        }
                    }
                    ApkFunctionAction.RESOURCES_MINIFICATION -> {
                        viewModel.minifyApkResources(targetApk) { _, msg ->
                            if (msg != null) errorMessage = msg
                        }
                    }
                    ApkFunctionAction.APK_PSEUDO_ENCRYPTION -> {
                        scope.launch {
                            com.example.mtcore.data.ApkToolkit.togglePseudoEncryption(targetApk, enable = true)
                                .onSuccess { res ->
                                    viewModel.markFileModified(res.outputFile)
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.pseudo_enc_enabled_success,
                                            res.modifiedCentralHeaders,
                                            res.outputFile.name
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .onFailure { err ->
                                    errorMessage = err.message
                                }
                        }
                    }
                    ApkFunctionAction.APK_ANTI_PSEUDO_ENCRYPTION -> {
                        scope.launch {
                            com.example.mtcore.data.ApkToolkit.togglePseudoEncryption(targetApk, enable = false)
                                .onSuccess { res ->
                                    viewModel.markFileModified(res.outputFile)
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.pseudo_enc_disabled_success,
                                            res.modifiedCentralHeaders,
                                            res.modifiedLocalHeaders,
                                            res.outputFile.name
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .onFailure { err ->
                                    errorMessage = err.message
                                }
                        }
                    }
                    ApkFunctionAction.XML_BATCH_REPLACEMENT -> xmlBatchReplaceTarget = targetApk
                    ApkFunctionAction.XML_TRANSLATION_MODE -> {
                        viewModel.extractApkTargetForTranslation(targetApk) { targetFile, err ->
                            if (targetFile != null) {
                                onOpenStringPoolScreen(targetFile, true)
                            } else if (err != null) {
                                errorMessage = err
                            }
                        }
                    }
                    ApkFunctionAction.APK_PROTECTION_INSPECTOR -> protectionInspectorTarget = targetApk
                    ApkFunctionAction.MODIFY_APK_TIMESTAMPS -> modifyTimestampsTarget = targetApk
                    ApkFunctionAction.APK_PATCH_MANIFEST -> apkPatchManifestTarget = targetApk
                    ApkFunctionAction.PROPERTIES -> {
                        propertiesTarget = targetApk
                        showProperties = true
                    }
                }
            }
        )
    }

    editApkInfoTarget?.let { targetApk ->
        EditApkInfoDialog(
            apkFile = targetApk,
            onDismiss = { editApkInfoTarget = null },
            onEditedResult = { editedFile, err ->
                if (editedFile != null) {
                    viewModel.markFileModified(editedFile)
                    Toast.makeText(
                        context,
                        context.getString(R.string.apk_info_edited_success, editedFile.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (err != null) {
                    errorMessage = err
                }
            }
        )
    }

    mergeDexTarget?.let { targetApk ->
        MergeDexFilesDialog(
            apkFile = targetApk,
            onDismiss = { mergeDexTarget = null },
            onMergedResult = { mergedFile, msg ->
                if (mergedFile != null) {
                    viewModel.markFileModified(mergedFile)
                    if (msg != null) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } else if (msg != null) {
                    errorMessage = msg
                }
            }
        )
    }

    modifyTimestampsTarget?.let { targetApk ->
        ModifyApkTimestampsDialog(
            apkFile = targetApk,
            onDismiss = { modifyTimestampsTarget = null },
            onModifiedResult = { outFile, msg ->
                if (outFile != null) {
                    viewModel.markFileModified(outFile)
                    if (msg != null) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } else if (msg != null) {
                    errorMessage = msg
                }
            }
        )
    }

    protectionInspectorTarget?.let { targetApk ->
        ApkProtectionInspectorDialog(
            apkFile = targetApk,
            onDismiss = { protectionInspectorTarget = null }
        )
    }

    signApkTarget?.let { (targetApk, pkgName) ->
        SignApkDialog(
            apkFile = targetApk,
            packageName = pkgName,
            fallbackCacheDir = viewModel.appCacheDir,
            onDismiss = { signApkTarget = null },
            onOpenKeyManager = {
                onOpenSigningKeyScreen()
            },
            onSignedResult = { signedFile, err ->
                if (signedFile != null) {
                    viewModel.markFileModified(signedFile)
                    Toast.makeText(
                        context,
                        context.getString(R.string.apk_signed_success, signedFile.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (err != null) {
                    errorMessage = err
                }
            }
        )
    }

    cloneApkTarget?.let { (targetApk, pkgName) ->
        CloneApkDialog(
            apkFile = targetApk,
            initialPackageName = pkgName,
            initialAppName = targetApk.nameWithoutExtension,
            onDismiss = { cloneApkTarget = null },
            onClonedResult = { clonedFile, err ->
                if (clonedFile != null) {
                    viewModel.markFileModified(clonedFile)
                    Toast.makeText(
                        context,
                        context.getString(R.string.apk_cloned_success, clonedFile.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (err != null) {
                    errorMessage = err
                }
            }
        )
    }

    optimizeApkTarget?.let { targetApk ->
        OptimizeApkDialog(
            apkFile = targetApk,
            onDismiss = { optimizeApkTarget = null },
            onOptimizedResult = { optimizedFile, err ->
                if (optimizedFile != null) {
                    viewModel.markFileModified(optimizedFile)
                    Toast.makeText(
                        context,
                        context.getString(R.string.apk_optimized_success, optimizedFile.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (err != null) {
                    errorMessage = err
                }
            }
        )
    }

    dexRedivisionTarget?.let { targetApk ->
        DexRedivisionDialog(
            apkFile = targetApk,
            onDismiss = { dexRedivisionTarget = null }
        )
    }

    dexAntiConfusionTarget?.let { targetApk ->
        DexAntiConfusionDialog(
            apkFile = targetApk,
            onDismiss = { dexAntiConfusionTarget = null },
            onExportedMapping = { mappingFile ->
                viewModel.markFileModified(mappingFile)
            }
        )
    }

    decryptDexStringsTarget?.let { targetApk ->
        DecryptDexStringsDialog(
            apkFile = targetApk,
            onDismiss = { decryptDexStringsTarget = null }
        )
    }

    apkPatchManifestTarget?.let { targetApk ->
        ApkPatchManifestDialog(
            apkFile = targetApk,
            onDismiss = { apkPatchManifestTarget = null },
            onPatchedResult = { patchedFile, err ->
                if (patchedFile != null) {
                    viewModel.markFileModified(patchedFile)
                    Toast.makeText(
                        context,
                        context.getString(R.string.apk_patched_success, patchedFile.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (err != null) {
                    errorMessage = err
                }
            }
        )
    }

    xmlBatchReplaceTarget?.let { targetApk ->
        XmlBatchReplacementDialog(
            apkFile = targetApk,
            onDismiss = { xmlBatchReplaceTarget = null },
            onCompleted = { outFile, msg ->
                if (outFile != null) {
                    viewModel.markFileModified(outFile)
                }
                if (msg != null) {
                    errorMessage = msg
                }
            }
        )
    }

    if (showTerminalSimulator) {
        TerminalSimulatorDialog(
            initialDirectory = activeDirectory,
            onDismiss = { showTerminalSimulator = false }
        )
    }

    if (showSmaliReference) {
        SmaliReferenceDialog(
            onDismiss = { showSmaliReference = false }
        )
    }

    if (showColorPicker) {
        ColorPickerConverterDialog(
            onDismiss = { showColorPicker = false }
        )
    }

    drawerUtilityTarget?.let { (title, type) ->
        DrawerUtilityToolDialog(
            title = title,
            toolType = type,
            cacheDir = viewModel.appCacheDir,
            onDismiss = { drawerUtilityTarget = null }
        )
    }

    diffViewerTarget?.let { (leftF, rightF) ->
        DiffViewerDialog(
            leftFile = leftF,
            rightFile = rightF,
            onDismiss = { diffViewerTarget = null }
        )
    }

    if (showCreate) {
        CreateDialog(
            onDismiss = { showCreate = false },
            onCreateFile = { name -> viewModel.createFile(name) { error -> if (error == null) showCreate = false else errorMessage = error } },
            onCreateFolder = { name -> viewModel.createFolder(name) { error -> if (error == null) showCreate = false else errorMessage = error } }
        )
    }

    if (showJump) {
        JumpDialog(
            initialPath = viewModel.displayPath(activeDirectory),
            onDismiss = { showJump = false },
            onJump = { path ->
                if (viewModel.navigateToPath(path, state.activePane)) {
                    showJump = false
                } else {
                    Toast.makeText(context, "Invalid directory path", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showRename && renameTarget != null) {
        RenameDialog(
            initialName = renameTarget!!.name,
            onDismiss = { showRename = false },
            onRename = { name ->
                viewModel.rename(renameTarget!!, name) { error ->
                    if (error == null) showRename = false else errorMessage = error
                }
            }
        )
    }

    if (showProperties && propertiesTarget != null) {
        PropertiesDialog(viewModel.properties(propertiesTarget!!)) { showProperties = false }
    }

    if (showContext && contextTarget != null) {
        ContextActionsDialog(
            selected = contextTarget!!,
            activeInArchive = viewModel.isArchivePath(contextTarget!!),
            otherInArchive = viewModel.isArchivePath(otherDirectory),
            isLeftActive = state.activePane == 0,
            onDismiss = { showContext = false },
            onProperties = {
                propertiesTarget = contextTarget
                showProperties = true
                showContext = false
            },
            onRename = {
                renameTarget = contextTarget
                showRename = true
                showContext = false
            },
            onCopy = {
                pendingAction = "copy"
                beginFileOperation(contextTarget!!, otherDirectory, viewModel) { conflict, destination ->
                    if (conflict) {
                        conflictTarget = contextTarget
                        conflictDestination = destination
                        showConflict = true
                    } else {
                        viewModel.copySelected(contextTarget!!) { error -> if (error != null) errorMessage = error }
                    }
                }
                showContext = false
            },
            onMove = {
                pendingAction = "move"
                beginFileOperation(contextTarget!!, otherDirectory, viewModel) { conflict, destination ->
                    if (conflict) {
                        conflictTarget = contextTarget
                        conflictDestination = destination
                        showConflict = true
                    } else {
                        viewModel.moveSelected(contextTarget!!, false) { error -> if (error != null) errorMessage = error }
                    }
                }
                showContext = false
            },
            onArchive = {
                showContext = false
                showArchiveDialog = true
            },
            onAddToZip = {
                addSource = contextTarget
                addArchive = viewModel.archiveFor(otherDirectory)
                showContext = false
                showAddToZip = true
            },
            onDelete = { showContext = false; viewModel.selectFile(contextTarget); showDeleteConfirm = true },
            onShare = { shareFile(context, contextTarget!!); showContext = false },
            onOpenWith = {
                showContext = false
                openWithGridTarget = contextTarget
            }
        )
    }

    if (showConflict && conflictTarget != null && conflictDestination != null) {
        ConflictDialog(
            source = conflictTarget!!,
            destination = conflictDestination!!,
            onChoice = { choice ->
                when (choice) {
                    ConflictChoice.REPLACE -> if (pendingAction == "move") {
                        viewModel.moveSelected(conflictTarget!!, true) { error -> if (error != null) errorMessage = error }
                    } else {
                        viewModel.copySelected(conflictTarget!!, true, false) { error -> if (error != null) errorMessage = error }
                    }
                    ConflictChoice.SKIP -> Unit
                    ConflictChoice.KEEP_BOTH -> viewModel.copySelected(conflictTarget!!, false, true) { error -> if (error != null) errorMessage = error }
                }
                showConflict = false
            },
            onDismiss = { showConflict = false }
        )
    }

    if (showArchiveDialog && contextTarget != null) {
        CreateArchiveDialog(
            initialName = "${contextTarget!!.name}.zip",
            onDismiss = { showArchiveDialog = false },
            onCreate = { name, level, deleteSource ->
                viewModel.compress(contextTarget!!, name, level, deleteSource) { error ->
                    if (error != null) errorMessage = error
                    showArchiveDialog = false
                }
            }
        )
    }

    if (showAddToZip && addArchive != null && addSource != null) {
        AddToZipDialog(
            archive = addArchive!!,
            item = addSource!!,
            onDismiss = { showAddToZip = false },
            onAdd = { entryName, level, deleteSource ->
                viewModel.addToArchive(addArchive!!, addSource!!, entryName, level, deleteSource) { error ->
                    if (error != null) errorMessage = error
                    showAddToZip = false
                }
            }
        )
    }

    if (showDeleteConfirm) {
        Dialog(onDismissRequest = { showDeleteConfirm = false }) {
            Surface(shape = RoundedCornerShape(4.dp), color = Color.White, tonalElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.delete_confirm_title), color = Color(0xFF111111), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.delete_confirm_message), color = Color(0xFF222222), fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel_titlecase)) }
                        TextButton(onClick = { viewModel.deleteSelected(contextTarget) { error -> if (error != null) errorMessage = error }; showDeleteConfirm = false }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.info)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text(stringResource(R.string.ok)) } }
        )
    }
    state.error?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun ContextActionsDialog(
    selected: File,
    activeInArchive: Boolean,
    otherInArchive: Boolean,
    isLeftActive: Boolean,
    onDismiss: () -> Unit,
    onProperties: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onArchive: () -> Unit,
    onAddToZip: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit
) {
    val copyLabel = if (isLeftActive) "Copy ->" else "Copy <-"
    val moveLabel = if (isLeftActive) "Move ->" else "Move <-"
    val actions = listOf(
        Triple(Icons.Default.ContentCopy, copyLabel, { if (activeInArchive) onCopy() else if (otherInArchive) onAddToZip() else onCopy() }),
        Triple(Icons.Default.ContentCut, moveLabel, onMove),
        Triple(Icons.Default.DeleteOutline, stringResource(R.string.delete), onDelete),
        Triple(Icons.Default.Edit, stringResource(R.string.rename), onRename),
        Triple(Icons.Default.Build, "Tools", onOpenWith),
        Triple(Icons.Default.Archive, stringResource(R.string.compress), onArchive),
        Triple(Icons.Default.Info, stringResource(R.string.properties_title), onProperties),
        Triple(Icons.Default.Share, stringResource(R.string.share), onShare),
        Triple(Icons.Default.OpenInNew, stringResource(R.string.open_with), onOpenWith),
        Triple(Icons.Default.BookmarkBorder, "Add bookmark", onProperties)
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(4.dp), color = Color.White, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.name, modifier = Modifier.weight(1f), color = Color(0xFF111111), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss, modifier = Modifier.width(30.dp).height(30.dp)) { Icon(Icons.Default.Close, "Close", tint = Color(0xFF555555), modifier = Modifier.width(18.dp).height(18.dp)) }
                }
                Text("Menus marked with • support long press...", color = Color(0xFF888888), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                actions.chunked(2).forEach { rowActions ->
                    Row(Modifier.fillMaxWidth()) {
                        rowActions.forEach { (icon, label, action) ->
                            Row(Modifier.weight(1f).height(40.dp).clickable { onDismiss(); action() }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = Color(0xFF222222), modifier = Modifier.width(18.dp).height(18.dp))
                                Text(label, modifier = Modifier.padding(start = 8.dp), color = Color(0xFF111111), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (rowActions.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun handleItemClick(
    item: FileItem,
    pane: Int,
    viewModel: MainViewModel,
    onOpenFile: (File) -> Unit,
    onInspectApk: (File) -> Unit,
    onInspectSplitBundle: (File) -> Unit,
    onOpenDexMenu: (File) -> Unit,
    onOpenArscMenu: (File) -> Unit,
    onOpenXmlMenu: (File) -> Unit,
    onOpenImage: (File) -> Unit,
    onOpenFont: (File) -> Unit,
    onOpenHexEditor: (File) -> Unit,
    onInspectKeystoreOrCert: (File) -> Unit,
    onOpenWithGrid: (File) -> Unit
) {
    if (item.isParent) {
        viewModel.goUp(pane)
        return
    }
    if (item.isDirectory) {
        viewModel.navigateTo(item.file, pane)
        return
    }
    when (viewModel.dispatchFile(item.file)) {
        com.example.mtcore.data.DispatchedFileType.BINARY_XML -> onOpenXmlMenu(item.file)
        com.example.mtcore.data.DispatchedFileType.PLAIN_XML -> onOpenFile(item.file)
        com.example.mtcore.data.DispatchedFileType.DEX -> onOpenDexMenu(item.file)
        com.example.mtcore.data.DispatchedFileType.ARSC -> onOpenArscMenu(item.file)
        com.example.mtcore.data.DispatchedFileType.APK -> onInspectApk(item.file)
        com.example.mtcore.data.DispatchedFileType.SPLIT_APK_BUNDLE -> onInspectSplitBundle(item.file)
        com.example.mtcore.data.DispatchedFileType.KEYSTORE_OR_CERT -> onInspectKeystoreOrCert(item.file)
        com.example.mtcore.data.DispatchedFileType.IMAGE -> onOpenImage(item.file)
        com.example.mtcore.data.DispatchedFileType.FONT -> onOpenFont(item.file)
        com.example.mtcore.data.DispatchedFileType.BINARY_HEX -> onOpenHexEditor(item.file)
        com.example.mtcore.data.DispatchedFileType.ARCHIVE -> viewModel.openZip(item.file, pane)
        com.example.mtcore.data.DispatchedFileType.CODE_TEXT -> onOpenFile(item.file)
        com.example.mtcore.data.DispatchedFileType.UNKNOWN -> onOpenWithGrid(item.file)
    }
}

private fun beginFileOperation(file: File, targetDirectory: File, viewModel: MainViewModel, callback: (Boolean, File) -> Unit) {
    callback(viewModel.hasConflictInTarget(file), File(targetDirectory, file.name))
}

private fun compactPath(path: String): String {
    val value = if (path.endsWith('/')) path else "$path/"
    return if (value.length <= 42) value else ".../" + value.takeLast(38).trimStart('/')
}

private fun gib(bytes: Long): String = String.format(Locale.US, "%.2fG", bytes / (1024.0 * 1024.0 * 1024.0))

private fun shareFile(context: android.content.Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = when (file.extension.lowercase(Locale.ROOT)) {
            "txt", "kt", "java", "xml", "json", "md", "csv", "smali" -> "text/plain"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        putExtra(Intent.EXTRA_TEXT, file.absolutePath)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_file, file.name))) }
        .onFailure { Toast.makeText(context, context.getString(R.string.unable_to_share), Toast.LENGTH_SHORT).show() }
}
