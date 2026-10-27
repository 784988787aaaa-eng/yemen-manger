package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.i18n.AppStringsProvider
import com.example.core.model.OperationStatus
import com.example.core.model.SortField
import com.example.engines.editor.SupportedEncoding
import com.example.ui.screens.AccentBlueColor
import com.example.ui.screens.ActiveDrawerToolModalHost
import com.example.ui.screens.ApkLabScreen
import com.example.ui.screens.ArchiveLabScreen
import com.example.ui.screens.BuiltInCoilImageViewerDialog
import com.example.ui.screens.DualPaneFileManagerScreen
import com.example.ui.screens.EditorsAndDiffScreen
import com.example.ui.screens.HeaderCharcoalColor
import com.example.ui.screens.InstalledAppsScreen
import com.example.ui.screens.PermissionsAndBatteryCenterDialog
import com.example.ui.screens.SettingsAndCapabilitiesScreen
import com.example.ui.screens.TaskCenterScreen
import com.example.ui.screens.TopOverflowDropdownMenu
import com.example.ui.screens.YemenNavigationDrawerContent
import com.example.ui.theme.YemenManagerTheme
import com.example.ui.viewmodel.MainSection
import com.example.ui.viewmodel.YemenManagerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: YemenManagerViewModel = viewModel()
            YemenManagerApp(vm)
        }
    }
}

private data class NavDestination(
    val section: MainSection,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YemenManagerApp(viewModel: YemenManagerViewModel) {
    val isArabicRtl by viewModel.isArabicRtl.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
    val editorSubTab by viewModel.editorSubTab.collectAsStateWithLifecycle()
    val isSplitDualPane by viewModel.isSplitDualPane.collectAsStateWithLifecycle()
    val activePaneId by viewModel.activePaneId.collectAsStateWithLifecycle()
    val paneA by viewModel.paneA.collectAsStateWithLifecycle()
    val paneB by viewModel.paneB.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val activeHashReport by viewModel.activeHashReport.collectAsStateWithLifecycle()
    val apkSummaryDialogReport by viewModel.apkSummaryDialogReport.collectAsStateWithLifecycle()
    val rootVolumeStats by viewModel.rootVolumeStats.collectAsStateWithLifecycle()
    val storageVolumeStats by viewModel.storageVolumeStats.collectAsStateWithLifecycle()
    val enabledTools by viewModel.enabledTools.collectAsStateWithLifecycle()
    val activeDrawerTool by viewModel.activeDrawerToolDialog.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val trashEntries by viewModel.trashEntries.collectAsStateWithLifecycle()
    val signingKeys by viewModel.signingKeys.collectAsStateWithLifecycle()
    val commonPasswords by viewModel.commonPasswords.collectAsStateWithLifecycle()
    val networkProfiles by viewModel.networkProfiles.collectAsStateWithLifecycle()
    val terminalTranscript by viewModel.terminalTranscript.collectAsStateWithLifecycle()
    val textSession by viewModel.textSession.collectAsStateWithLifecycle()
    val hexWindow by viewModel.hexWindow.collectAsStateWithLifecycle()
    val hexMatches by viewModel.hexSearchMatches.collectAsStateWithLifecycle()
    val diffReport by viewModel.diffReport.collectAsStateWithLifecycle()
    val archiveReport by viewModel.archiveReport.collectAsStateWithLifecycle()
    val selectedArchiveEntries by viewModel.selectedArchiveEntries.collectAsStateWithLifecycle()
    val apkReport by viewModel.apkReport.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val extractedBundle by viewModel.extractedBundle.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val operationLogs by viewModel.operationLogs.collectAsStateWithLifecycle()
    val privilegeStatuses by viewModel.privilegeStatuses.collectAsStateWithLifecycle()
    val lanServerState by viewModel.lanServerState.collectAsStateWithLifecycle()
    val validatedExtension by viewModel.validatedExtension.collectAsStateWithLifecycle()
    val permissionsSnapshot by viewModel.permissionsSnapshot.collectAsStateWithLifecycle()
    val showPermissionsCenterDialog by viewModel.showPermissionsCenterDialog.collectAsStateWithLifecycle()
    val imagePreviewEntry by viewModel.imagePreviewEntry.collectAsStateWithLifecycle()
    val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()

    val strings = if (isArabicRtl) AppStringsProvider.Arabic else AppStringsProvider.English
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showCharsetDialog by remember { mutableStateOf(false) }

    val safDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.registerSafUri(it.toString()) }
    }

    LaunchedEffect(statusToast) {
        statusToast?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusToast()
        }
    }

    val activePane = if (activePaneId == "A") paneA else paneB

    BackHandler(enabled = currentSection != MainSection.FILES || activePane.isVirtualArchive) {
        if (currentSection != MainSection.FILES) {
            viewModel.selectSection(MainSection.FILES)
        } else if (activePane.isVirtualArchive) {
            viewModel.navigateUp(activePaneId)
        }
    }

    val runningTaskCount = tasks.count { it.status == OperationStatus.RUNNING || it.status == OperationStatus.QUEUED }

    YemenManagerTheme(darkTheme = isDarkTheme, isArabicRtl = isArabicRtl) {
        val navItems = listOf(
            NavDestination(MainSection.FILES, strings.navFileManager, Icons.Default.FolderCopy, "nav_files"),
            NavDestination(MainSection.EDITORS, strings.navEditorsDiff, Icons.Default.EditNote, "nav_editors"),
            NavDestination(MainSection.ARCHIVES, strings.navArchiveLab, Icons.Default.Archive, "nav_archives"),
            NavDestination(MainSection.APK_LAB, strings.navApkLab, Icons.Default.Android, "nav_apk_lab"),
            NavDestination(MainSection.INSTALLED_APPS, strings.navInstalledApps, Icons.Default.Apps, "nav_installed_apps"),
            NavDestination(MainSection.TASKS, strings.navTasks, Icons.Default.Checklist, "nav_tasks"),
            NavDestination(MainSection.SETTINGS, strings.navSettings, Icons.Default.Settings, "nav_settings")
        )

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                YemenNavigationDrawerContent(
                    viewModel = viewModel,
                    strings = strings,
                    rootStats = rootVolumeStats,
                    storageStats = storageVolumeStats,
                    enabledTools = enabledTools,
                    preferences = preferences,
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    },
                    onRequestMountSaf = {
                        safDocumentLauncher.launch(arrayOf("*/*"))
                    }
                )
            }
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    // Dark Charcoal Top Bar (#26292E) matching Screenshot_20261027-174524_ee3e8f40.jpg
                    Surface(
                        color = HeaderCharcoalColor,
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Hamburger Icon or Back Arrow
                                IconButton(
                                    onClick = {
                                        if (currentSection == MainSection.FILES) {
                                            coroutineScope.launch {
                                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                            }
                                        } else {
                                            viewModel.selectSection(MainSection.FILES)
                                        }
                                    },
                                    modifier = Modifier.testTag("drawer_open_button")
                                ) {
                                    Icon(
                                        imageVector = if (currentSection == MainSection.FILES) {
                                            Icons.Default.Menu
                                        } else {
                                            Icons.AutoMirrored.Filled.ArrowBack
                                        },
                                        contentDescription = "Menu",
                                        tint = Color.White
                                    )
                                }

                                // Center/Start Path + Folders/Files/Disk Subtitle matching Screenshot_20261027-174524_ee3e8f40.jpg
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .clickable {
                                            if (currentSection == MainSection.FILES) {
                                                showSearchDialog = true
                                            }
                                        }
                                        .testTag("app_title_text")
                                ) {
                                    val headerTitle = when (currentSection) {
                                        MainSection.FILES -> activePane.displayPath.ifEmpty { "/storage/emulated/0/" }
                                        MainSection.INSTALLED_APPS -> strings.toolInstalledApps
                                        MainSection.SETTINGS -> strings.menuPreferences
                                        MainSection.EDITORS -> strings.textEditorTitle
                                        MainSection.ARCHIVES -> strings.navArchiveLab
                                        MainSection.APK_LAB -> strings.navApkLab
                                        MainSection.TASKS -> strings.taskCenterTitle
                                    }

                                    Text(
                                        text = headerTitle,
                                        color = Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val headerSubtitle = if (currentSection == MainSection.FILES) {
                                        if (activePane.isVirtualArchive) {
                                            "${strings.foldersCountLabel}: ${activePane.folderCount}  ${strings.filesCountLabel}: ${activePane.fileCount}"
                                        } else {
                                            "${strings.foldersCountLabel}: ${activePane.folderCount}  ${strings.filesCountLabel}: ${activePane.fileCount}  ${strings.diskUsageLabel}: ${storageVolumeStats.topBarSummary()}"
                                        }
                                    } else {
                                        "${strings.appName} • ${strings.offlineModeBadge}"
                                    }

                                    Text(
                                        text = headerSubtitle,
                                        color = Color(0xFFB8BDC4),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Right Actions: Permissions/Battery Center + Language Toggle + Theme Toggle + 3-Dots Overflow Menu
                                IconButton(
                                    onClick = { viewModel.openPermissionsCenterDialog() },
                                    modifier = Modifier.size(34.dp).testTag("top_bar_permissions_button")
                                ) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = "Permissions & Battery",
                                        tint = if (permissionsSnapshot.allCriticalGranted) Color(0xFF66BB6A) else AccentBlueColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleLanguage() },
                                    modifier = Modifier.size(34.dp).testTag("top_bar_language_toggle")
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = strings.languageToggleLabel,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleTheme() },
                                    modifier = Modifier.size(34.dp).testTag("top_bar_theme_toggle")
                                ) {
                                    Icon(
                                        Icons.Default.DarkMode,
                                        contentDescription = strings.themeToggleLabel,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box {
                                    IconButton(
                                        onClick = { showTopOverflowMenu = true },
                                        modifier = Modifier.size(38.dp).testTag("top_bar_overflow_button")
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "More options",
                                            tint = Color.White
                                        )
                                    }

                                    TopOverflowDropdownMenu(
                                        expanded = showTopOverflowMenu,
                                        isVirtualArchiveMode = activePane.isVirtualArchive,
                                        strings = strings,
                                        viewModel = viewModel,
                                        onDismiss = { showTopOverflowMenu = false },
                                        onOpenSearchDialog = { showSearchDialog = true },
                                        onOpenFilterDialog = { showSearchDialog = true },
                                        onOpenSortDialog = { showSortDialog = true },
                                        onOpenCharsetDialog = { showCharsetDialog = true }
                                    )
                                }
                            }

                            // Compact Quick-Navigation Strip for fast switching & full test compatibility
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E2024))
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("main_bottom_navigation"),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                navItems.forEach { dest ->
                                    val selected = currentSection == dest.section
                                    Row(
                                        modifier = Modifier
                                            .clickable { viewModel.selectSection(dest.section) }
                                            .background(
                                                if (selected) AccentBlueColor.copy(alpha = 0.22f) else Color.Transparent
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag(dest.testTag),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (dest.section == MainSection.TASKS && runningTaskCount > 0) {
                                            BadgedBox(badge = { Badge { Text("$runningTaskCount") } }) {
                                                Icon(
                                                    dest.icon,
                                                    contentDescription = dest.label,
                                                    tint = if (selected) AccentBlueColor else Color(0xFFB8BDC4),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                dest.icon,
                                                contentDescription = dest.label,
                                                tint = if (selected) AccentBlueColor else Color(0xFFB8BDC4),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = dest.label,
                                            fontSize = 11.sp,
                                            color = if (selected) Color.White else Color(0xFFB8BDC4),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(innerPadding)
                ) {
                    when (currentSection) {
                        MainSection.FILES -> DualPaneFileManagerScreen(
                            viewModel = viewModel,
                            strings = strings,
                            paneA = paneA,
                            paneB = paneB,
                            activePaneId = activePaneId,
                            isSplitDualPane = isSplitDualPane,
                            isDarkTheme = isDarkTheme,
                            bookmarks = bookmarks,
                            activeHashReport = activeHashReport,
                            apkSummaryDialogReport = apkSummaryDialogReport
                        )
                        MainSection.EDITORS -> EditorsAndDiffScreen(
                            viewModel = viewModel,
                            strings = strings,
                            activeTab = editorSubTab,
                            textSession = textSession,
                            hexWindow = hexWindow,
                            hexMatches = hexMatches,
                            diffReport = diffReport
                        )
                        MainSection.ARCHIVES -> ArchiveLabScreen(
                            viewModel = viewModel,
                            strings = strings,
                            report = archiveReport,
                            selectedEntries = selectedArchiveEntries
                        )
                        MainSection.APK_LAB -> ApkLabScreen(
                            viewModel = viewModel,
                            strings = strings,
                            report = apkReport
                        )
                        MainSection.INSTALLED_APPS -> InstalledAppsScreen(
                            viewModel = viewModel,
                            strings = strings,
                            apps = installedApps,
                            extractedBundle = extractedBundle
                        )
                        MainSection.TASKS -> TaskCenterScreen(
                            viewModel = viewModel,
                            strings = strings,
                            tasks = tasks,
                            logs = operationLogs
                        )
                        MainSection.SETTINGS -> SettingsAndCapabilitiesScreen(
                            viewModel = viewModel,
                            strings = strings,
                            isArabic = isArabicRtl,
                            isDarkTheme = isDarkTheme,
                            privileges = privilegeStatuses,
                            lanState = lanServerState,
                            capabilities = viewModel.capabilityMatrix,
                            validatedExtension = validatedExtension
                        )
                    }
                }
            }
        }

        // Modal Host for Drawer Tools (Recycle Bin, Color Picker, Signing Key, Passwords, Terminal, Smali, etc.)
        ActiveDrawerToolModalHost(
            activeTool = activeDrawerTool,
            viewModel = viewModel,
            strings = strings,
            trashEntries = trashEntries,
            signingKeys = signingKeys,
            commonPasswords = commonPasswords,
            networkProfiles = networkProfiles,
            lanState = lanServerState,
            terminalTranscript = terminalTranscript,
            installedApps = installedApps,
            smaliOpcodes = viewModel.smaliOpcodes
        )

        // Permissions & Battery Optimization Center Modal Dialog
        if (showPermissionsCenterDialog) {
            PermissionsAndBatteryCenterDialog(
                snapshot = permissionsSnapshot,
                strings = strings,
                onRefreshSnapshot = { viewModel.refreshPermissionsSnapshot() },
                onDismiss = { viewModel.dismissPermissionsCenterDialog() }
            )
        }

        // Built-in Offline Coil Image Viewer Dialog
        imagePreviewEntry?.let { imgEntry ->
            BuiltInCoilImageViewerDialog(
                entry = imgEntry,
                resolvedFilePath = viewModel.resolveVirtualOrRealFilePath(imgEntry.uriOrPath),
                strings = strings,
                onDismiss = { viewModel.dismissImagePreview() },
                onOpenInHex = { viewModel.openFileInHexEditor(imgEntry.uriOrPath) },
                onShareFile = { viewModel.shareFile(imgEntry.uriOrPath) }
            )
        }

        // Search / Filter Dialog triggered from Top Bar or Overflow Menu
        if (showSearchDialog) {
            var query by remember(activePane.searchQuery) { mutableStateOf(activePane.searchQuery) }
            AlertDialog(
                onDismissRequest = { showSearchDialog = false },
                title = { Text(strings.menuSearch, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(strings.searchPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pane_${activePaneId}_search_input")
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updatePaneSearch(activePaneId, query)
                            showSearchDialog = false
                        }
                    ) { Text(strings.confirmProceed, color = AccentBlueColor, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.updatePaneSearch(activePaneId, "")
                            showSearchDialog = false
                        }
                    ) { Text(strings.confirmCancel, color = AccentBlueColor) }
                }
            )
        }

        // Sort Dialog triggered from Overflow Menu
        if (showSortDialog) {
            val fields = listOf(
                SortField.NAME to (if (isArabicRtl) "الاسم (Name)" else "Name"),
                SortField.SIZE to (if (isArabicRtl) "الحجم (Size)" else "Size"),
                SortField.DATE to (if (isArabicRtl) "التاريخ (Last Modified)" else "Date modified"),
                SortField.EXTENSION to (if (isArabicRtl) "الامتداد (Extension)" else "Extension")
            )
            AlertDialog(
                onDismissRequest = { showSortDialog = false },
                title = { Text(strings.menuSort, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        fields.forEach { (f, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.cycleSortField(activePaneId)
                                        showSortDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = activePane.sortConfig.field == f,
                                    onClick = {
                                        viewModel.cycleSortField(activePaneId)
                                        showSortDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentBlueColor)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSortDialog = false }) {
                        Text(strings.confirmCancel, color = AccentBlueColor)
                    }
                }
            )
        }

        // Charset Dialog triggered from Virtual Archive Overflow Menu
        if (showCharsetDialog) {
            val encodings = SupportedEncoding.entries
            AlertDialog(
                onDismissRequest = { showCharsetDialog = false },
                title = { Text(strings.menuCharset, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        encodings.forEach { enc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCharsetDialog = false }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(enc.displayName, fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCharsetDialog = false }) {
                        Text(strings.confirmCancel, color = AccentBlueColor)
                    }
                }
            )
        }
    }
}
