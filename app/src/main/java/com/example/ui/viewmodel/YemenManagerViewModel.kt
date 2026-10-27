package com.example.ui.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.i18n.AppStrings
import com.example.core.i18n.AppStringsProvider
import com.example.core.model.AppResult
import com.example.core.model.CapabilityEntry
import com.example.core.model.FileEntry
import com.example.core.model.FileHashReport
import com.example.core.model.OperationStatus
import com.example.core.model.OperationTask
import com.example.core.model.OperationType
import com.example.core.model.SortConfig
import com.example.core.model.SortField
import com.example.core.storage.LocalFileStorageProvider
import com.example.core.storage.SafStorageProvider
import com.example.core.storage.WorkspaceSeeder
import com.example.core.tasks.TaskCenterEngine
import com.example.data.local.BookmarkEntity
import com.example.data.local.EditorDraftEntity
import com.example.data.local.OperationLogEntity
import com.example.data.local.PathHistoryEntity
import com.example.data.local.YemenDatabase
import com.example.engines.apk.ApkAnalysisReport
import com.example.engines.apk.ApkLabEngine
import com.example.engines.archive.ArchiveInspectionReport
import com.example.engines.archive.ZipArchiveEngine
import com.example.engines.editor.BoundedUndoRedoStack
import com.example.engines.editor.DiffEngine
import com.example.engines.editor.FileComparisonReport
import com.example.engines.editor.HexEngine
import com.example.engines.editor.HexPageWindow
import com.example.engines.editor.SupportedEncoding
import com.example.engines.editor.TextDocumentSession
import com.example.engines.editor.TextEditorEngine
import com.example.engines.system.ApkFunctionAction
import com.example.engines.system.ApkProFunctionsEngine
import com.example.engines.system.DiskVolumeStats
import com.example.engines.system.ExtensionManifest
import com.example.engines.system.ExtractedPackageBundle
import com.example.engines.system.HonestCapabilityRegistry
import com.example.data.local.YemenPreferencesRepository
import com.example.engines.system.InstalledAppRecord
import com.example.engines.system.InstalledAppsEngine
import com.example.engines.system.LanServerState
import com.example.engines.system.LocalLanTransferServer
import com.example.engines.system.LocalTerminalSimulator
import com.example.engines.system.OptimizeApkOptions
import com.example.engines.system.PermissionsAndSystemIntentsEngine
import com.example.engines.system.PrivilegeProviderProber
import com.example.engines.system.PrivilegeProviderStatus
import com.example.engines.system.SigningKeyItem
import com.example.engines.system.SigningKeyManagerEngine
import com.example.engines.system.SmaliInstructionReference
import com.example.engines.system.SmaliOpcodeEntry
import com.example.engines.system.StorageStatsHelper
import com.example.engines.system.SystemPermissionsSnapshot
import com.example.engines.system.VirtualArchiveBrowserEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class MainSection {
    FILES,
    EDITORS,
    ARCHIVES,
    APK_LAB,
    INSTALLED_APPS,
    TASKS,
    SETTINGS
}

enum class EditorSubTab {
    TEXT, HEX, DIFF
}

enum class DrawerTool(val id: String) {
    RECYCLE_BIN("recycle_bin"),
    PLUGIN_MANAGER("plugin_manager"),
    REMOTE_MANAGER("remote_manager"),
    SCREEN_COLOR_PICKER("screen_color_picker"),
    SIGNING_KEY("signing_key"),
    COMMON_PASSWORDS("common_passwords"),
    INSTALLED_APPS("installed_apps"),
    TEXT_EDITOR("text_editor"),
    TERMINAL_SIMULATOR("terminal_simulator"),
    APK_MCP("apk_mcp"),
    ACTIVITY_RECORD("activity_record"),
    SMALI_INSTRUCTIONS("smali_instructions")
}

data class UserPreferencesState(
    val themeFollowsSystem: Boolean = true,
    val requestRootPermission: Boolean = true,
    val requestShellPermission: Boolean = true,
    val customSuCommand: String = "su",
    val startupPathLeft: String = "Home",
    val startupPathRight: String = "Home",
    val fileListSize: String = "Medium",
    val maxLinesFileName: String = "Single line",
    val fileListTimePreference: String = "Last modified time",
    val disablePermissionInFileList: Boolean = true,
    val dateTimeFormat: String = "yy-MM-dd HH:mm",
    val generateBackupFile: Boolean = true,
    val preserveFileTime: Boolean = false,
    val enableInputBoxFavorites: Boolean = true,
    val enableRecycleBin: Boolean = true,
    val moveToRecycleBinByDefault: Boolean = true,
    val autoCleanRecycleBinDays: String = "Keep for 30 days",
    val showDeletionWarning: Boolean = true,
    val apkInstallVerification: Boolean = true,
    val apkInstallPreventsDeletion: Boolean = false,
    val doubleConfirmBeforeInstalling: Boolean = true,
    val installerMethod: String = "Standard System Installer",
    val showHiddenFiles: Boolean = true,
    val rememberLastPositionInBookmark: Boolean = true,
    val floatingBackToTopButton: Boolean = true,
    val vibrateOnDragToolbar: Boolean = true
)

data class FilePaneState(
    val paneId: String,
    val currentPath: String,
    val entries: List<FileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val searchQuery: String = "",
    val sortConfig: SortConfig = SortConfig(),
    val navigationBackstack: List<String> = emptyList(),
    val forwardStack: List<String> = emptyList(),
    val highlightedPath: String? = null,
    val errorBanner: String? = null
) {
    val isVirtualArchive: Boolean
        get() = VirtualArchiveBrowserEngine.isVirtualPath(currentPath)

    val displayPath: String
        get() = if (isVirtualArchive) {
            VirtualArchiveBrowserEngine.formatDisplayPath(currentPath)
        } else {
            currentPath
        }

    val folderCount: Int
        get() = entries.count { it.isDirectory }

    val fileCount: Int
        get() = entries.count { !it.isDirectory }
}

class YemenManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = YemenDatabase.getInstance(application)
    private val dao = db.dao()
    val preferencesRepository = YemenPreferencesRepository(application)

    val workspaceRoot = File(application.filesDir, "yemen_workspace")
    val trashDir = File(application.filesDir, "yemen_trash")
    val keysDir = File(application.filesDir, "yemen_keys")
    val virtualCacheDir = File(application.cacheDir, "yemen_virtual")

    val localProvider = LocalFileStorageProvider(trashDir)
    val safProvider = SafStorageProvider(application)
    val taskCenter = TaskCenterEngine(dao, viewModelScope)

    val textEditorEngine = TextEditorEngine()
    val hexEngine = HexEngine()
    val diffEngine = DiffEngine()
    val zipArchiveEngine = ZipArchiveEngine()
    val apkLabEngine = ApkLabEngine()
    val apkProFunctionsEngine = ApkProFunctionsEngine(apkLabEngine)
    val signingKeyManager = SigningKeyManagerEngine(keysDir)
    val installedAppsEngine = InstalledAppsEngine(application)
    val privilegeProber = PrivilegeProviderProber(application)
    val lanServer = LocalLanTransferServer()

    private val _isArabicRtl = MutableStateFlow(true)
    val isArabicRtl: StateFlow<Boolean> = _isArabicRtl.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(false) // Default high-contrast crisp light mode like screenshots, toggleable anytime
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _currentSection = MutableStateFlow(MainSection.FILES)
    val currentSection: StateFlow<MainSection> = _currentSection.asStateFlow()

    private val _editorSubTab = MutableStateFlow(EditorSubTab.TEXT)
    val editorSubTab: StateFlow<EditorSubTab> = _editorSubTab.asStateFlow()

    private val _isSplitDualPane = MutableStateFlow(true)
    val isSplitDualPane: StateFlow<Boolean> = _isSplitDualPane.asStateFlow()

    private val _activePaneId = MutableStateFlow("A")
    val activePaneId: StateFlow<String> = _activePaneId.asStateFlow()

    private val _paneA = MutableStateFlow(FilePaneState(paneId = "A", currentPath = ""))
    val paneA: StateFlow<FilePaneState> = _paneA.asStateFlow()

    private val _paneB = MutableStateFlow(FilePaneState(paneId = "B", currentPath = ""))
    val paneB: StateFlow<FilePaneState> = _paneB.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _activeHashReport = MutableStateFlow<FileHashReport?>(null)
    val activeHashReport: StateFlow<FileHashReport?> = _activeHashReport.asStateFlow()

    private val _safMountedEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val safMountedEntries: StateFlow<List<FileEntry>> = _safMountedEntries.asStateFlow()

    // Live Permissions & Battery Optimization Snapshot + Dialog State
    private val _permissionsSnapshot = MutableStateFlow(
        PermissionsAndSystemIntentsEngine.checkPermissionsSnapshot(application)
    )
    val permissionsSnapshot: StateFlow<SystemPermissionsSnapshot> = _permissionsSnapshot.asStateFlow()

    private val _showPermissionsCenterDialog = MutableStateFlow(false)
    val showPermissionsCenterDialog: StateFlow<Boolean> = _showPermissionsCenterDialog.asStateFlow()

    // Built-in Coil Offline Image Preview State
    private val _imagePreviewEntry = MutableStateFlow<FileEntry?>(null)
    val imagePreviewEntry: StateFlow<FileEntry?> = _imagePreviewEntry.asStateFlow()

    // Storage Volume Stats
    private val _rootVolumeStats = MutableStateFlow(
        StorageStatsHelper.queryVolumeStats(Environment.getRootDirectory(), "مجلد الجذر", "Root Directory")
    )
    val rootVolumeStats: StateFlow<DiskVolumeStats> = _rootVolumeStats.asStateFlow()

    private val _storageVolumeStats = MutableStateFlow(
        StorageStatsHelper.queryVolumeStats(workspaceRoot, "ذاكرة التخزين", "Storage Directory")
    )
    val storageVolumeStats: StateFlow<DiskVolumeStats> = _storageVolumeStats.asStateFlow()

    // Drawer Tools & Preferences State
    private val _enabledTools = MutableStateFlow(DrawerTool.entries.toSet())
    val enabledTools: StateFlow<Set<DrawerTool>> = _enabledTools.asStateFlow()

    private val _activeDrawerToolDialog = MutableStateFlow<DrawerTool?>(null)
    val activeDrawerToolDialog: StateFlow<DrawerTool?> = _activeDrawerToolDialog.asStateFlow()

    private val _preferences = MutableStateFlow(UserPreferencesState())
    val preferences: StateFlow<UserPreferencesState> = _preferences.asStateFlow()

    private val _trashEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val trashEntries: StateFlow<List<FileEntry>> = _trashEntries.asStateFlow()

    private val _signingKeys = MutableStateFlow<List<SigningKeyItem>>(emptyList())
    val signingKeys: StateFlow<List<SigningKeyItem>> = _signingKeys.asStateFlow()

    private val _commonPasswords = MutableStateFlow(
        listOf("123456", "android", "yemen_manager", "password", "admin", "root")
    )
    val commonPasswords: StateFlow<List<String>> = _commonPasswords.asStateFlow()

    private val _networkProfiles = MutableStateFlow(
        listOf(
            "FTP • 192.168.1.100:2121 (Local Dev)",
            "SFTP • 10.0.0.15:22 (Build Server)",
            "WebDAV • http://127.0.0.1:8080/dav"
        )
    )
    val networkProfiles: StateFlow<List<String>> = _networkProfiles.asStateFlow()

    private val _terminalWorkingDir = MutableStateFlow(workspaceRoot)
    val terminalWorkingDir: StateFlow<File> = _terminalWorkingDir.asStateFlow()

    private val _terminalTranscript = MutableStateFlow(
        listOf(
            "Yemen Manager Local Terminal Simulator [v1.0.0-offline]",
            "Type 'help' for available commands.",
            "$ pwd",
            workspaceRoot.absolutePath
        )
    )
    val terminalTranscript: StateFlow<List<String>> = _terminalTranscript.asStateFlow()

    val smaliOpcodes: List<SmaliOpcodeEntry> = SmaliInstructionReference.opcodes

    // Editors & Diff State
    private val undoRedoStack = BoundedUndoRedoStack(maxSteps = 50)
    private val _textSession = MutableStateFlow<TextDocumentSession?>(null)
    val textSession: StateFlow<TextDocumentSession?> = _textSession.asStateFlow()

    private val _hexWindow = MutableStateFlow<HexPageWindow?>(null)
    val hexWindow: StateFlow<HexPageWindow?> = _hexWindow.asStateFlow()

    private val _hexSearchMatches = MutableStateFlow<List<Long>>(emptyList())
    val hexSearchMatches: StateFlow<List<Long>> = _hexSearchMatches.asStateFlow()

    private val _diffReport = MutableStateFlow<FileComparisonReport?>(null)
    val diffReport: StateFlow<FileComparisonReport?> = _diffReport.asStateFlow()

    // Archive Lab State
    private val _archiveReport = MutableStateFlow<ArchiveInspectionReport?>(null)
    val archiveReport: StateFlow<ArchiveInspectionReport?> = _archiveReport.asStateFlow()

    private val _selectedArchiveEntries = MutableStateFlow<Set<String>>(emptySet())
    val selectedArchiveEntries: StateFlow<Set<String>> = _selectedArchiveEntries.asStateFlow()

    // APK Lab State & Dialog Triggers
    private val _apkReport = MutableStateFlow<ApkAnalysisReport?>(null)
    val apkReport: StateFlow<ApkAnalysisReport?> = _apkReport.asStateFlow()

    private val _apkSummaryDialogReport = MutableStateFlow<ApkAnalysisReport?>(null)
    val apkSummaryDialogReport: StateFlow<ApkAnalysisReport?> = _apkSummaryDialogReport.asStateFlow()

    // Installed Apps & System State
    private val _installedApps = MutableStateFlow<List<InstalledAppRecord>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppRecord>> = _installedApps.asStateFlow()

    private val _extractedBundle = MutableStateFlow<ExtractedPackageBundle?>(null)
    val extractedBundle: StateFlow<ExtractedPackageBundle?> = _extractedBundle.asStateFlow()

    private val _privilegeStatuses = MutableStateFlow<List<PrivilegeProviderStatus>>(emptyList())
    val privilegeStatuses: StateFlow<List<PrivilegeProviderStatus>> = _privilegeStatuses.asStateFlow()

    private val _lanServerState = MutableStateFlow(LanServerState())
    val lanServerState: StateFlow<LanServerState> = _lanServerState.asStateFlow()

    private val _validatedExtension = MutableStateFlow<ExtensionManifest?>(null)
    val validatedExtension: StateFlow<ExtensionManifest?> = _validatedExtension.asStateFlow()

    val capabilityMatrix: List<CapabilityEntry> = HonestCapabilityRegistry.getCapabilityMatrix()

    // Room Observables
    val tasks: StateFlow<List<OperationTask>> = taskCenter.tasks
    val bookmarks: StateFlow<List<BookmarkEntity>> = dao.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentPaths: StateFlow<List<PathHistoryEntity>> = dao.observeRecentPaths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val operationLogs: StateFlow<List<OperationLogEntity>> = dao.observeOperationLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val editorDrafts: StateFlow<List<EditorDraftEntity>> = dao.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initializeWorkspaceAndFixtures()
    }

    fun strings(): AppStrings = if (_isArabicRtl.value) AppStringsProvider.Arabic else AppStringsProvider.English

    fun initializeWorkspaceAndFixtures() {
        val (dirA, dirB) = WorkspaceSeeder.ensureSeeded(workspaceRoot)
        _terminalWorkingDir.value = dirA
        navigatePane("A", dirA.absolutePath, recordHistory = false)
        navigatePane("B", dirB.absolutePath, recordHistory = false)

        val readmeFile = File(dirA, "مرحبا_يمن_مانجر.txt")
        if (readmeFile.exists()) openFileInTextEditor(readmeFile.absolutePath, switchScreen = false)

        val binFile = File(dirA, "firmware_header.bin")
        if (binFile.exists()) openFileInHexEditor(binFile.absolutePath, switchScreen = false)

        val cfg1 = File(dirA, "app_config_v1.json")
        val cfg2 = File(dirB, "app_config_v2.json")
        if (cfg1.exists() && cfg2.exists()) compareFiles(cfg1.absolutePath, cfg2.absolutePath, switchScreen = false)

        val zipFile = File(dirA, "project_bundle.zip")
        if (zipFile.exists()) inspectZipArchive(zipFile.absolutePath, switchScreen = false)

        val apkFile = File(dirA, "sample_yemen_tool_v1.apk")
        if (apkFile.exists()) analyzeApkFile(apkFile.absolutePath, switchScreen = false)

        refreshTrashEntries()
        _signingKeys.value = signingKeyManager.listOrSeedKeys()
        refreshStorageVolumeStats()
        refreshSystemCapabilities()
        refreshPermissionsSnapshot()
    }

    fun refreshPermissionsSnapshot() {
        _permissionsSnapshot.value = PermissionsAndSystemIntentsEngine.checkPermissionsSnapshot(getApplication())
    }

    fun openPermissionsCenterDialog() {
        refreshPermissionsSnapshot()
        _showPermissionsCenterDialog.value = true
    }

    fun dismissPermissionsCenterDialog() {
        _showPermissionsCenterDialog.value = false
    }

    fun openImagePreview(entry: FileEntry) {
        _imagePreviewEntry.value = entry
    }

    fun dismissImagePreview() {
        _imagePreviewEntry.value = null
    }

    fun installApkFile(apkPath: String) {
        val resolved = resolveVirtualOrRealFilePath(apkPath)
        val (_, message) = PermissionsAndSystemIntentsEngine.installApkViaFileProvider(getApplication(), resolved)
        refreshPermissionsSnapshot()
        _statusToast.value = message
    }

    fun shareFile(filePath: String) {
        val resolved = resolveVirtualOrRealFilePath(filePath)
        val ok = PermissionsAndSystemIntentsEngine.shareFileViaFileProvider(getApplication(), resolved)
        if (!ok) {
            computeFileHashes(resolved)
        }
    }

    fun exportLocalSettingsBackup() {
        val backupFile = File(workspaceRoot, "yemen_manager_backup.json")
        val json = preferencesRepository.exportPreferencesToJson(_preferences.value)
        backupFile.writeText(json, Charsets.UTF_8)
        refreshPane("A")
        refreshPane("B")
        _statusToast.value = if (_isArabicRtl.value) {
            "تم حفظ النسخة الاحتياطية للإعدادات محليًا: ${backupFile.name}"
        } else {
            "Backed up local settings to: ${backupFile.name}"
        }
    }

    fun restoreLocalSettingsBackup() {
        val backupFile = File(workspaceRoot, "yemen_manager_backup.json")
        if (!backupFile.exists()) {
            exportLocalSettingsBackup()
        }
        val restored = preferencesRepository.importPreferencesFromJson(backupFile.readText(Charsets.UTF_8))
        if (restored != null) {
            _preferences.value = restored
            viewModelScope.launch(Dispatchers.IO) {
                preferencesRepository.saveUserPreferences(restored)
            }
            _statusToast.value = if (_isArabicRtl.value) {
                "تمت استعادة الإعدادات المحلية بنجاح من ${backupFile.name}"
            } else {
                "Restored local settings from ${backupFile.name}"
            }
        }
    }

    fun refreshStorageVolumeStats() {
        _rootVolumeStats.value = StorageStatsHelper.queryVolumeStats(
            Environment.getRootDirectory(),
            "مجلد الجذر",
            "Root Directory"
        )
        _storageVolumeStats.value = StorageStatsHelper.queryVolumeStats(
            workspaceRoot,
            "ذاكرة التخزين",
            "Storage Directory"
        )
    }

    fun toggleLanguage() {
        val next = !_isArabicRtl.value
        _isArabicRtl.value = next
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.saveLanguage(next)
        }
    }

    fun setLanguageArabic(isArabic: Boolean) {
        _isArabicRtl.value = isArabic
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.saveLanguage(isArabic)
        }
    }

    fun toggleTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.saveTheme(next)
        }
    }

    fun selectSection(section: MainSection) {
        _currentSection.value = section
        if (section == MainSection.INSTALLED_APPS && _installedApps.value.isEmpty()) {
            loadInstalledApps()
        }
    }

    fun openDrawerTool(tool: DrawerTool) {
        when (tool) {
            DrawerTool.INSTALLED_APPS -> selectSection(MainSection.INSTALLED_APPS)
            DrawerTool.TEXT_EDITOR -> selectSection(MainSection.EDITORS)
            DrawerTool.APK_MCP -> selectSection(MainSection.APK_LAB)
            DrawerTool.RECYCLE_BIN -> {
                refreshTrashEntries()
                _activeDrawerToolDialog.value = tool
            }
            DrawerTool.SIGNING_KEY -> {
                _signingKeys.value = signingKeyManager.listOrSeedKeys()
                _activeDrawerToolDialog.value = tool
            }
            DrawerTool.ACTIVITY_RECORD -> {
                if (_installedApps.value.isEmpty()) loadInstalledApps()
                _activeDrawerToolDialog.value = tool
            }
            else -> _activeDrawerToolDialog.value = tool
        }
    }

    fun dismissDrawerToolDialog() {
        _activeDrawerToolDialog.value = null
    }

    fun updateEnabledTools(newSet: Set<DrawerTool>) {
        _enabledTools.value = newSet
    }

    fun updatePreferences(transform: (UserPreferencesState) -> UserPreferencesState) {
        _preferences.update(transform)
        val current = _preferences.value
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.saveUserPreferences(current)
        }
    }

    fun selectEditorSubTab(tab: EditorSubTab) {
        _editorSubTab.value = tab
    }

    fun toggleSplitDualPane() {
        _isSplitDualPane.update { !it }
    }

    fun setActivePane(paneId: String) {
        if (paneId == "A" || paneId == "B") {
            _activePaneId.value = paneId
        }
    }

    fun clearStatusToast() {
        _statusToast.value = null
    }

    fun clearHashReport() {
        _activeHashReport.value = null
    }

    fun dismissApkSummaryDialog() {
        _apkSummaryDialogReport.value = null
    }

    fun clearExtractedBundleDialog() {
        _extractedBundle.value = null
    }

    // --- Dual-Pane File Manager & Virtual Archive Navigation ---

    fun navigatePane(paneId: String, targetPath: String, recordHistory: Boolean = true, clearForward: Boolean = true) {
        val stateFlow = if (paneId == "A") _paneA else _paneB
        val current = stateFlow.value
        val listResult = if (VirtualArchiveBrowserEngine.isVirtualPath(targetPath)) {
            VirtualArchiveBrowserEngine.listVirtualDirectory(targetPath)
        } else {
            localProvider.list(targetPath)
        }

        when (listResult) {
            is AppResult.Success -> {
                val sorted = filterAndSort(listResult.data, current.searchQuery, current.sortConfig)
                val updatedStack = if (recordHistory && current.currentPath.isNotEmpty() && current.currentPath != targetPath) {
                    (current.navigationBackstack + current.currentPath).takeLast(30)
                } else {
                    current.navigationBackstack
                }
                stateFlow.value = current.copy(
                    currentPath = targetPath,
                    entries = sorted,
                    selectedPaths = emptySet(),
                    navigationBackstack = updatedStack,
                    forwardStack = if (clearForward) emptyList() else current.forwardStack,
                    highlightedPath = null,
                    errorBanner = null
                )
                if (recordHistory && !VirtualArchiveBrowserEngine.isVirtualPath(targetPath)) {
                    viewModelScope.launch(Dispatchers.IO) {
                        dao.insertPathHistory(PathHistoryEntity(paneId = paneId, pathOrUri = targetPath))
                    }
                }
            }
            is AppResult.Failure -> {
                stateFlow.value = current.copy(
                    errorBanner = listResult.error.localizedMessage(_isArabicRtl.value)
                )
            }
        }
    }

    fun navigateUp(paneId: String) {
        val state = if (paneId == "A") _paneA.value else _paneB.value
        if (state.isVirtualArchive) {
            val (archivePath, innerDir) = VirtualArchiveBrowserEngine.parseVirtualPath(state.currentPath) ?: return
            if (innerDir.isNotEmpty()) {
                val parentInner = innerDir.substringBeforeLast('/', "")
                navigatePane(paneId, VirtualArchiveBrowserEngine.buildVirtualPath(archivePath, parentInner))
            } else {
                // Exit archive back to host directory
                val hostDir = File(archivePath).parentFile?.absolutePath ?: workspaceRoot.absolutePath
                navigatePane(paneId, hostDir)
            }
            return
        }

        val currentFile = File(state.currentPath)
        val parent = currentFile.parentFile
        if (parent != null && parent.canRead()) {
            navigatePane(paneId, parent.absolutePath)
        } else if (state.navigationBackstack.isNotEmpty()) {
            navigateBackInHistory(paneId)
        }
    }

    fun navigateBackInHistory(paneId: String) {
        val stateFlow = if (paneId == "A") _paneA else _paneB
        val state = stateFlow.value
        if (state.navigationBackstack.isEmpty()) {
            navigateUp(paneId)
            return
        }
        val prev = state.navigationBackstack.last()
        stateFlow.update {
            it.copy(
                navigationBackstack = it.navigationBackstack.dropLast(1),
                forwardStack = (it.forwardStack + it.currentPath).takeLast(30)
            )
        }
        navigatePane(paneId, prev, recordHistory = false, clearForward = false)
    }

    fun navigateForwardInHistory(paneId: String) {
        val stateFlow = if (paneId == "A") _paneA else _paneB
        val state = stateFlow.value
        if (state.forwardStack.isEmpty()) return
        val next = state.forwardStack.last()
        stateFlow.update {
            it.copy(
                navigationBackstack = (it.navigationBackstack + it.currentPath).takeLast(30),
                forwardStack = it.forwardStack.dropLast(1)
            )
        }
        navigatePane(paneId, next, recordHistory = false, clearForward = false)
    }

    fun viewArchiveOrApkInsidePane(paneId: String, archiveFilePath: String) {
        val virtualRoot = VirtualArchiveBrowserEngine.buildVirtualPath(archiveFilePath, "")
        navigatePane(paneId, virtualRoot, recordHistory = true)
    }

    fun closeVirtualArchiveInActivePane() {
        val pane = activePane()
        if (!pane.isVirtualArchive) return
        val (archivePath, _) = VirtualArchiveBrowserEngine.parseVirtualPath(pane.currentPath) ?: return
        val hostParent = File(archivePath).parentFile?.absolutePath ?: workspaceRoot.absolutePath
        navigatePane(pane.paneId, hostParent)
    }

    fun swapPanes() {
        val a = _paneA.value
        val b = _paneB.value
        _paneA.value = b.copy(paneId = "A")
        _paneB.value = a.copy(paneId = "B")
        _statusToast.value = if (_isArabicRtl.value) "تم تبديل النافذتين" else "Swapped windows"
    }

    fun syncInactivePaneToActive() {
        val src = activePane()
        val dst = inactivePane()
        navigatePane(dst.paneId, src.currentPath)
        _statusToast.value = if (_isArabicRtl.value) "تمت مزامنة النافذة المقابلة" else "Synced other window to current path"
    }

    fun locateFileInActivePane(filePath: String) {
        val targetFile = File(filePath)
        val parentDir = targetFile.parentFile ?: workspaceRoot
        val paneId = _activePaneId.value
        _currentSection.value = MainSection.FILES
        navigatePane(paneId, parentDir.absolutePath)
        val stateFlow = if (paneId == "A") _paneA else _paneB
        stateFlow.update {
            it.copy(
                selectedPaths = setOf(targetFile.absolutePath),
                highlightedPath = targetFile.absolutePath
            )
        }
    }

    fun resolveVirtualOrRealFilePath(uriOrPath: String): String {
        if (!VirtualArchiveBrowserEngine.isVirtualPath(uriOrPath)) return uriOrPath
        return when (val res = VirtualArchiveBrowserEngine.materializeVirtualEntryToWorkingFile(uriOrPath, virtualCacheDir)) {
            is AppResult.Success -> res.data.absolutePath
            is AppResult.Failure -> uriOrPath
        }
    }

    fun refreshPane(paneId: String) {
        val path = (if (paneId == "A") _paneA.value else _paneB.value).currentPath
        if (path.isNotEmpty()) {
            navigatePane(paneId, path, recordHistory = false, clearForward = false)
        }
        refreshStorageVolumeStats()
    }

    fun updatePaneSearch(paneId: String, query: String) {
        val stateFlow = if (paneId == "A") _paneA else _paneB
        val current = stateFlow.value
        val rawResult = if (current.isVirtualArchive) {
            VirtualArchiveBrowserEngine.listVirtualDirectory(current.currentPath)
        } else {
            localProvider.list(current.currentPath)
        }
        val allEntries = (rawResult as? AppResult.Success)?.data ?: current.entries
        stateFlow.value = current.copy(
            searchQuery = query,
            entries = filterAndSort(allEntries, query, current.sortConfig)
        )
    }

    fun cycleSortField(paneId: String) {
        val stateFlow = if (paneId == "A") _paneA else _paneB
        val current = stateFlow.value
        val nextField = when (current.sortConfig.field) {
            SortField.NAME -> SortField.SIZE
            SortField.SIZE -> SortField.DATE
            SortField.DATE -> SortField.EXTENSION
            SortField.EXTENSION -> SortField.NAME
        }
        val newConfig = current.sortConfig.copy(field = nextField)
        val rawResult = if (current.isVirtualArchive) {
            VirtualArchiveBrowserEngine.listVirtualDirectory(current.currentPath)
        } else {
            localProvider.list(current.currentPath)
        }
        val allEntries = (rawResult as? AppResult.Success)?.data ?: current.entries
        stateFlow.value = current.copy(
            sortConfig = newConfig,
            entries = filterAndSort(allEntries, current.searchQuery, newConfig)
        )
    }

    fun toggleSelection(paneId: String, path: String) {
        val stateFlow = if (paneId == "A") _paneA else _paneB
        stateFlow.update { state ->
            val nextSet = if (state.selectedPaths.contains(path)) {
                state.selectedPaths - path
            } else {
                state.selectedPaths + path
            }
            state.copy(selectedPaths = nextSet)
        }
    }

    fun selectSingleEntry(paneId: String, path: String) {
        val stateFlow = if (paneId == "A") _paneA else _paneB
        stateFlow.update { it.copy(selectedPaths = setOf(path)) }
    }

    fun clearSelection(paneId: String) {
        val stateFlow = if (paneId == "A") _paneA else _paneB
        stateFlow.update { it.copy(selectedPaths = emptySet()) }
    }

    fun createFolderInActivePane(folderName: String) {
        val pane = activePane()
        if (pane.isVirtualArchive) {
            _statusToast.value = if (_isArabicRtl.value) "لا يمكن إنشاء مجلد مباشر داخل وضع التصفح الافتراضي" else "Cannot create folder directly inside virtual archive"
            return
        }
        when (val res = localProvider.createDirectory(pane.currentPath, folderName)) {
            is AppResult.Success -> {
                refreshPane(pane.paneId)
                _statusToast.value = if (_isArabicRtl.value) "تم إنشاء المجلد: ${res.data.name}" else "Created folder: ${res.data.name}"
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun createNewFileInActivePane(fileName: String, content: String) {
        val pane = activePane()
        if (pane.isVirtualArchive) return
        when (val res = localProvider.createNewFile(pane.currentPath, fileName, content.toByteArray(Charsets.UTF_8))) {
            is AppResult.Success -> {
                refreshPane(pane.paneId)
                _statusToast.value = if (_isArabicRtl.value) "تم إنشاء الملف: ${res.data.name}" else "Created file: ${res.data.name}"
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun renameFile(paneId: String, path: String, newName: String) {
        when (val res = localProvider.rename(path, newName)) {
            is AppResult.Success -> {
                refreshPane(paneId)
                _statusToast.value = if (_isArabicRtl.value) "تمت إعادة التسمية إلى ${res.data.name}" else "Renamed to ${res.data.name}"
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun copySelectedToOtherPane(overwrite: Boolean = false) {
        val srcPane = activePane()
        val dstPane = inactivePane()
        val selected = srcPane.selectedPaths.toList()
        if (selected.isEmpty() || dstPane.isVirtualArchive) return

        val resolvedSources = selected.map { resolveVirtualOrRealFilePath(it) }

        taskCenter.enqueueAndRun(
            type = OperationType.COPY,
            titleAr = "نسخ ${resolvedSources.size} عنصر إلى النافذة ${dstPane.paneId}",
            titleEn = "Copy ${resolvedSources.size} item(s) to Window ${dstPane.paneId}",
            sources = resolvedSources,
            destination = dstPane.currentPath
        ) { onProgress, isCancelled ->
            PermissionsAndSystemIntentsEngine.withCpuWakeLock(getApplication(), "YemenManager:CopyTask") {
                val outputs = mutableListOf<String>()
                val warnings = mutableListOf<String>()
                for (srcPath in resolvedSources) {
                    val res = localProvider.copyStreaming(
                        sourceUriOrPath = srcPath,
                        destDirUriOrPath = dstPane.currentPath,
                        overwrite = overwrite,
                        onProgress = onProgress,
                        isCancelled = isCancelled
                    )
                    when (res) {
                        is AppResult.Success -> {
                            outputs.add(res.data.uriOrPath)
                            warnings.addAll(res.warnings)
                        }
                        is AppResult.Failure -> {
                            return@withCpuWakeLock AppResult.Failure(res.error, outputs)
                        }
                    }
                }
                viewModelScope.launch {
                    refreshPane("A")
                    refreshPane("B")
                    clearSelection(srcPane.paneId)
                    _statusToast.value = if (_isArabicRtl.value) "اكتمل النسخ بنجاح (${outputs.size})" else "Copied ${outputs.size} item(s)"
                }
                AppResult.Success(outputs, warnings)
            }
        }
    }

    fun moveSelectedToOtherPane(overwrite: Boolean = false) {
        val srcPane = activePane()
        val dstPane = inactivePane()
        val selected = srcPane.selectedPaths.toList()
        if (selected.isEmpty() || srcPane.isVirtualArchive || dstPane.isVirtualArchive) return

        taskCenter.enqueueAndRun(
            type = OperationType.MOVE,
            titleAr = "نقل ${selected.size} عنصر إلى النافذة ${dstPane.paneId}",
            titleEn = "Move ${selected.size} item(s) to Window ${dstPane.paneId}",
            sources = selected,
            destination = dstPane.currentPath
        ) { onProgress, isCancelled ->
            val outputs = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            for (srcPath in selected) {
                val res = localProvider.move(
                    sourceUriOrPath = srcPath,
                    destDirUriOrPath = dstPane.currentPath,
                    overwrite = overwrite,
                    onProgress = onProgress,
                    isCancelled = isCancelled
                )
                when (res) {
                    is AppResult.Success -> {
                        outputs.add(res.data.uriOrPath)
                        warnings.addAll(res.warnings)
                    }
                    is AppResult.Failure -> {
                        return@enqueueAndRun AppResult.Failure(res.error, outputs)
                    }
                }
            }
            viewModelScope.launch {
                refreshPane("A")
                refreshPane("B")
                clearSelection(srcPane.paneId)
                _statusToast.value = if (_isArabicRtl.value) "اكتمل النقل (${outputs.size})" else "Moved ${outputs.size} item(s)"
            }
            AppResult.Success(outputs, warnings)
        }
    }

    fun deleteSelected(moveToTrashOnly: Boolean) {
        val srcPane = activePane()
        val selected = srcPane.selectedPaths.toList()
        if (selected.isEmpty() || srcPane.isVirtualArchive) return

        taskCenter.enqueueAndRun(
            type = if (moveToTrashOnly) OperationType.TRASH else OperationType.DELETE,
            titleAr = if (moveToTrashOnly) "نقل ${selected.size} عنصر لسلة المحذوفات" else "حذف نهائي لـ ${selected.size} عنصر",
            titleEn = if (moveToTrashOnly) "Move ${selected.size} item(s) to Recycle Bin" else "Delete ${selected.size} item(s)",
            sources = selected,
            destination = if (moveToTrashOnly) trashDir.absolutePath else null
        ) { _, _ ->
            val outputs = mutableListOf<String>()
            for (path in selected) {
                if (moveToTrashOnly) {
                    when (val res = localProvider.moveToTrash(path)) {
                        is AppResult.Success -> outputs.add(res.data.uriOrPath)
                        is AppResult.Failure -> return@enqueueAndRun AppResult.Failure(res.error, outputs)
                    }
                } else {
                    when (val res = localProvider.delete(path)) {
                        is AppResult.Success -> outputs.add(path)
                        is AppResult.Failure -> return@enqueueAndRun AppResult.Failure(res.error, outputs)
                    }
                }
            }
            viewModelScope.launch {
                refreshPane(srcPane.paneId)
                refreshTrashEntries()
                clearSelection(srcPane.paneId)
                _statusToast.value = if (_isArabicRtl.value) "تم تنفيذ العملية بنجاح" else "Operation completed"
            }
            AppResult.Success(outputs)
        }
    }

    fun refreshTrashEntries() {
        val res = localProvider.list(trashDir.absolutePath)
        _trashEntries.value = (res as? AppResult.Success)?.data ?: emptyList()
    }

    fun restoreFromTrash(entry: FileEntry) {
        val dstPath = activePane().currentPath.takeIf { !VirtualArchiveBrowserEngine.isVirtualPath(it) }
            ?: workspaceRoot.absolutePath
        viewModelScope.launch(Dispatchers.IO) {
            val res = localProvider.move(
                sourceUriOrPath = entry.uriOrPath,
                destDirUriOrPath = dstPath,
                overwrite = false,
                onProgress = { _, _, _ -> },
                isCancelled = { false }
            )
            refreshTrashEntries()
            refreshPane("A")
            refreshPane("B")
            if (res is AppResult.Success) {
                _statusToast.value = if (_isArabicRtl.value) "تمت استعادة ${entry.name}" else "Restored ${entry.name}"
            }
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch(Dispatchers.IO) {
            trashDir.listFiles()?.forEach { it.deleteRecursively() }
            refreshTrashEntries()
            _statusToast.value = if (_isArabicRtl.value) "تم إفراغ سلة المحذوفات" else "Recycle bin emptied"
        }
    }

    fun computeFileHashes(path: String) {
        val resolved = resolveVirtualOrRealFilePath(path)
        taskCenter.enqueueAndRun(
            type = OperationType.HASH_COMPUTE,
            titleAr = "حساب البصمات الرقمية (MD5 / SHA-1 / SHA-256)",
            titleEn = "Compute Cryptographic Hashes",
            sources = listOf(resolved),
            destination = null
        ) { onProgress, isCancelled ->
            PermissionsAndSystemIntentsEngine.withCpuWakeLock(getApplication(), "YemenManager:HashTask") {
                when (val res = localProvider.computeHashes(resolved, onProgress, isCancelled)) {
                    is AppResult.Success -> {
                        _activeHashReport.value = res.data
                        AppResult.Success(listOf("SHA-256: ${res.data.sha256}"))
                    }
                    is AppResult.Failure -> res
                }
            }
        }
    }

    fun toggleBookmark(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = bookmarks.value.any { it.pathOrUri == path }
            if (existing) {
                dao.deleteBookmark(path)
                _statusToast.value = if (_isArabicRtl.value) "تمت الإزالة من المفضلة" else "Removed bookmark"
            } else {
                val name = File(path).name.ifEmpty { path }
                dao.upsertBookmark(BookmarkEntity(pathOrUri = path, title = name, providerType = "LOCAL_FS"))
                _statusToast.value = if (_isArabicRtl.value) "تمت الإضافة للمفضلة: $name" else "Bookmarked: $name"
            }
        }
    }

    fun registerSafUri(uriString: String) {
        when (val res = safProvider.inspectUriMetadata(uriString)) {
            is AppResult.Success -> {
                _safMountedEntries.update { listOf(res.data) + it.filterNot { e -> e.uriOrPath == uriString } }
                viewModelScope.launch(Dispatchers.IO) {
                    dao.upsertBookmark(BookmarkEntity(pathOrUri = uriString, title = "SAF: ${res.data.name}", providerType = "SAF_URI"))
                }
                _statusToast.value = if (_isArabicRtl.value) "تم ربط URI عبر SAF: ${res.data.name}" else "Mounted SAF URI: ${res.data.name}"
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    // --- Editors & Diff Actions ---

    fun openFileInTextEditor(path: String, encoding: SupportedEncoding = SupportedEncoding.UTF_8, switchScreen: Boolean = true) {
        val resolved = resolveVirtualOrRealFilePath(path)
        when (val res = textEditorEngine.loadTextFile(resolved, encoding)) {
            is AppResult.Success -> {
                undoRedoStack.clear()
                _textSession.value = res.data
                if (switchScreen) {
                    _editorSubTab.value = EditorSubTab.TEXT
                    _currentSection.value = MainSection.EDITORS
                }
                if (res.warnings.isNotEmpty()) {
                    _statusToast.value = res.warnings.first()
                }
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun updateEditorText(newText: String) {
        val current = _textSession.value ?: return
        if (current.isTruncatedReadOnly || current.text == newText) return
        undoRedoStack.pushState(current.text)
        _textSession.value = current.copy(
            text = newText,
            undoDepth = undoRedoStack.undoCount,
            redoDepth = undoRedoStack.redoCount,
            isDirty = true
        )
    }

    fun undoTextEdit() {
        val current = _textSession.value ?: return
        val restored = undoRedoStack.undo(current.text) ?: return
        _textSession.value = current.copy(
            text = restored,
            undoDepth = undoRedoStack.undoCount,
            redoDepth = undoRedoStack.redoCount,
            isDirty = true
        )
    }

    fun redoTextEdit() {
        val current = _textSession.value ?: return
        val restored = undoRedoStack.redo(current.text) ?: return
        _textSession.value = current.copy(
            text = restored,
            undoDepth = undoRedoStack.undoCount,
            redoDepth = undoRedoStack.redoCount,
            isDirty = true
        )
    }

    fun runTextFindAndReplace(findQuery: String, replaceWith: String, useRegex: Boolean) {
        val current = _textSession.value ?: return
        when (val res = textEditorEngine.findAndReplace(current.text, findQuery, replaceWith, useRegex)) {
            is AppResult.Success -> {
                val (updated, count) = res.data
                if (count > 0) {
                    updateEditorText(updated)
                }
                _statusToast.value = if (_isArabicRtl.value) "تم استبدال $count تطابق" else "Replaced $count match(es)"
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun saveTextDocument(replaceOriginal: Boolean) {
        val current = _textSession.value ?: return
        val bytes = current.text.toByteArray(current.encoding.charset)
        when (val res = localProvider.writeAtomicWorkingCopy(current.sourcePath, bytes, replaceOriginal)) {
            is AppResult.Success -> {
                _textSession.value = current.copy(isDirty = false)
                viewModelScope.launch(Dispatchers.IO) {
                    dao.upsertDraft(
                        EditorDraftEntity(
                            sourcePathOrUri = current.sourcePath,
                            workingCopyPath = res.data.uriOrPath,
                            encoding = current.encoding.displayName,
                            contentPreview = current.text.take(120),
                            byteLength = bytes.size.toLong()
                        )
                    )
                }
                refreshPane("A")
                refreshPane("B")
                _statusToast.value = if (_isArabicRtl.value) {
                    "تم الحفظ بنجاح في: ${res.data.name}"
                } else {
                    "Saved safely to: ${res.data.name}"
                }
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun openFileInHexEditor(path: String, pageOffset: Long = 0L, switchScreen: Boolean = true) {
        val resolved = resolveVirtualOrRealFilePath(path)
        val currentPatches = if (_hexWindow.value?.filePath == resolved) _hexWindow.value?.stagedPatches.orEmpty() else emptyMap()
        when (val res = hexEngine.readWindow(resolved, pageOffset, currentPatches)) {
            is AppResult.Success -> {
                _hexWindow.value = res.data
                if (switchScreen) {
                    _editorSubTab.value = EditorSubTab.HEX
                    _currentSection.value = MainSection.EDITORS
                }
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun stageHexBytePatch(offset: Long, newHexByte: String) {
        val current = _hexWindow.value ?: return
        val byteVal = newHexByte.trim().removePrefix("0x").toIntOrNull(16)
        if (byteVal == null || byteVal !in 0..255) {
            _statusToast.value = if (_isArabicRtl.value) "قيمة بايت Hex غير صالحة (00..FF)" else "Invalid Hex byte (00..FF)"
            return
        }
        val nextPatches = current.stagedPatches + (offset to byteVal.toByte())
        when (val res = hexEngine.readWindow(current.filePath, current.pageOffset, nextPatches)) {
            is AppResult.Success -> _hexWindow.value = res.data
            is AppResult.Failure -> _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
        }
    }

    fun searchInHexFile(query: String, isHexPattern: Boolean) {
        val current = _hexWindow.value ?: return
        when (val res = hexEngine.searchBytes(current.filePath, query, isHexPattern)) {
            is AppResult.Success -> {
                _hexSearchMatches.value = res.data
                if (res.data.isNotEmpty()) {
                    openFileInHexEditor(current.filePath, (res.data.first() / 16) * 16, switchScreen = false)
                }
                _statusToast.value = if (_isArabicRtl.value) "عدد التطابقات: ${res.data.size}" else "Matches found: ${res.data.size}"
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun commitHexPatches(replaceOriginal: Boolean) {
        val current = _hexWindow.value ?: return
        when (val res = hexEngine.commitPatchesSafely(current.filePath, current.stagedPatches, replaceOriginal)) {
            is AppResult.Success -> {
                _hexWindow.value = current.copy(stagedPatches = emptyMap())
                openFileInHexEditor(res.data, current.pageOffset, switchScreen = false)
                refreshPane("A")
                refreshPane("B")
                _statusToast.value = if (_isArabicRtl.value) "تم حفظ التعديلات الثنائية بأمان: ${File(res.data).name}" else "Saved binary output: ${File(res.data).name}"
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun compareFiles(leftPath: String, rightPath: String, switchScreen: Boolean = true) {
        val rLeft = resolveVirtualOrRealFilePath(leftPath)
        val rRight = resolveVirtualOrRealFilePath(rightPath)
        when (val res = diffEngine.compareFiles(rLeft, rRight)) {
            is AppResult.Success -> {
                _diffReport.value = res.data
                if (switchScreen) {
                    _editorSubTab.value = EditorSubTab.DIFF
                    _currentSection.value = MainSection.EDITORS
                }
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    // --- Archive Lab Actions ---

    fun inspectZipArchive(path: String, switchScreen: Boolean = true) {
        val resolved = resolveVirtualOrRealFilePath(path)
        when (val res = zipArchiveEngine.inspectArchive(resolved)) {
            is AppResult.Success -> {
                _archiveReport.value = res.data
                _selectedArchiveEntries.value = emptySet()
                if (switchScreen) {
                    _currentSection.value = MainSection.ARCHIVES
                }
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun toggleArchiveEntrySelection(entryPath: String) {
        _selectedArchiveEntries.update { set ->
            if (set.contains(entryPath)) set - entryPath else set + entryPath
        }
    }

    fun extractCurrentArchive(extractOnlySelected: Boolean) {
        val report = _archiveReport.value ?: return
        val destPane = activePane()
        val baseDir = if (destPane.isVirtualArchive) workspaceRoot.absolutePath else destPane.currentPath
        val destFolder = File(baseDir, "${File(report.archivePath).nameWithoutExtension}_extracted")
        val selection = if (extractOnlySelected && _selectedArchiveEntries.value.isNotEmpty()) {
            _selectedArchiveEntries.value
        } else null

        taskCenter.enqueueAndRun(
            type = OperationType.ARCHIVE_EXTRACT,
            titleAr = "استخراج آمن للأرشيف إلى ${destFolder.name}",
            titleEn = "Safe Extract Archive to ${destFolder.name}",
            sources = listOf(report.archivePath),
            destination = destFolder.absolutePath
        ) { onProgress, isCancelled ->
            val res = zipArchiveEngine.extractEntries(
                archivePath = report.archivePath,
                destinationDirPath = destFolder.absolutePath,
                selectedEntryPaths = selection,
                onProgress = onProgress,
                isCancelled = isCancelled
            )
            viewModelScope.launch {
                refreshPane(destPane.paneId)
                if (res is AppResult.Success) {
                    _statusToast.value = if (_isArabicRtl.value) "تم استخراج ${res.data.size} ملف بأمان" else "Extracted ${res.data.size} file(s)"
                } else if (res is AppResult.Failure) {
                    _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
                }
            }
            res
        }
    }

    fun createZipFromActiveSelection(archiveFileName: String) {
        val pane = activePane()
        if (pane.isVirtualArchive) return
        val sources = pane.selectedPaths.toList().ifEmpty {
            pane.entries.take(3).map { it.uriOrPath }
        }
        val cleanName = if (archiveFileName.endsWith(".zip", true)) archiveFileName else "$archiveFileName.zip"
        val destZip = File(pane.currentPath, cleanName)

        taskCenter.enqueueAndRun(
            type = OperationType.ARCHIVE_CREATE,
            titleAr = "إنشاء أرشيف ZIP: $cleanName",
            titleEn = "Create ZIP Archive: $cleanName",
            sources = sources,
            destination = destZip.absolutePath
        ) { onProgress, isCancelled ->
            when (val res = zipArchiveEngine.createZipArchive(sources, destZip.absolutePath, overwrite = true, onProgress, isCancelled)) {
                is AppResult.Success -> {
                    viewModelScope.launch {
                        refreshPane(pane.paneId)
                        inspectZipArchive(res.data, switchScreen = false)
                        _statusToast.value = if (_isArabicRtl.value) "تم إنشاء الأرشيف: $cleanName" else "Created ZIP: $cleanName"
                    }
                    AppResult.Success(listOf(res.data))
                }
                is AppResult.Failure -> res
            }
        }
    }

    // --- APK Static Analysis, Summary Dialog, Pro Functions & Signing Lab ---

    fun analyzeApkFile(path: String, switchScreen: Boolean = true, showSummaryDialog: Boolean = false) {
        val resolved = resolveVirtualOrRealFilePath(path)
        when (val res = apkLabEngine.analyzeApk(resolved)) {
            is AppResult.Success -> {
                _apkReport.value = res.data
                if (showSummaryDialog) {
                    _apkSummaryDialogReport.value = res.data
                } else if (switchScreen) {
                    _currentSection.value = MainSection.APK_LAB
                }
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun executeApkProFunction(action: ApkFunctionAction, report: ApkAnalysisReport): String {
        return when (val res = apkProFunctionsEngine.executeApkFunction(action, report)) {
            is AppResult.Success -> {
                refreshPane("A")
                refreshPane("B")
                _statusToast.value = res.data.lines().firstOrNull()
                res.data
            }
            is AppResult.Failure -> {
                val msg = res.error.localizedMessage(_isArabicRtl.value)
                _statusToast.value = msg
                msg
            }
        }
    }

    fun runOptimizeApk(apkPath: String, options: OptimizeApkOptions) {
        taskCenter.enqueueAndRun(
            type = OperationType.APK_REBUILD_SIGN,
            titleAr = "تحسين وضغط ومحاذاة 4K لملف APK",
            titleEn = "Optimize, 4K Align & Sign APK",
            sources = listOf(apkPath),
            destination = "${apkPath}_optimized.apk"
        ) { _, _ ->
            PermissionsAndSystemIntentsEngine.withCpuWakeLock(getApplication(), "YemenManager:ApkOptimizeTask") {
                when (val res = apkProFunctionsEngine.optimizeApk(apkPath, options)) {
                    is AppResult.Success -> {
                        viewModelScope.launch {
                            refreshPane("A")
                            refreshPane("B")
                            analyzeApkFile(res.data.absolutePath, switchScreen = false)
                            _statusToast.value = if (_isArabicRtl.value) {
                                "تم تحسين وتوقيع الحزمة: ${res.data.name}"
                            } else {
                                "Optimized & signed APK: ${res.data.name}"
                            }
                        }
                        AppResult.Success(listOf(res.data.absolutePath))
                    }
                    is AppResult.Failure -> res
                }
            }
        }
    }

    fun rebuildAndSignCurrentApk(newVersionName: String, newVersionCode: Int, auditNote: String) {
        val current = _apkReport.value ?: return
        taskCenter.enqueueAndRun(
            type = OperationType.APK_REBUILD_SIGN,
            titleAr = "إعادة بناء وتوقيع نسخة عمل APK ($newVersionName)",
            titleEn = "Rebuild & Sign Working APK ($newVersionName)",
            sources = listOf(current.apkPath),
            destination = "${current.apkPath}_rebuilt_signed.apk"
        ) { _, _ ->
            PermissionsAndSystemIntentsEngine.withCpuWakeLock(getApplication(), "YemenManager:ApkSignTask") {
                when (val res = apkLabEngine.rebuildAndSignWorkingApk(current.apkPath, newVersionName, newVersionCode, auditNote)) {
                    is AppResult.Success -> {
                        _apkReport.value = res.data
                        viewModelScope.launch {
                            refreshPane("A")
                            refreshPane("B")
                            _statusToast.value = if (_isArabicRtl.value) {
                                "تم بناء وتوقيع الحزمة والتحقق منها: ${File(res.data.apkPath).name}"
                            } else {
                                "Rebuilt, signed & verified: ${File(res.data.apkPath).name}"
                            }
                        }
                        AppResult.Success(listOf(res.data.apkPath), res.warnings)
                    }
                    is AppResult.Failure -> {
                        viewModelScope.launch {
                            _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
                        }
                        res
                    }
                }
            }
        }
    }

    // --- Drawer Tools Helpers (Signing Keys, Passwords, Terminal, Network, Installed Apps) ---

    fun generateNewSigningKey(alias: String) {
        if (alias.isBlank()) return
        signingKeyManager.generateNewKey(alias.trim())
        _signingKeys.value = signingKeyManager.listOrSeedKeys()
        _statusToast.value = if (_isArabicRtl.value) "تم توليد مفتاح توقيع RSA-2048: $alias" else "Generated RSA-2048 signing key: $alias"
    }

    fun addCommonPassword(pwd: String) {
        val clean = pwd.trim()
        if (clean.isEmpty()) return
        _commonPasswords.update { (listOf(clean) + it).distinct() }
    }

    fun removeCommonPassword(pwd: String) {
        _commonPasswords.update { it - pwd }
    }

    fun addNetworkProfile(protocol: String, hostAndPort: String) {
        val clean = "$protocol • ${hostAndPort.trim()}"
        _networkProfiles.update { (listOf(clean) + it).distinct() }
        _statusToast.value = if (_isArabicRtl.value) "تم حفظ ملف الاتصال الشبكي ($protocol)" else "Saved network storage profile ($protocol)"
    }

    fun runTerminalCommand(commandLine: String) {
        val trimmed = commandLine.trim()
        if (trimmed.equals("clear", ignoreCase = true)) {
            _terminalTranscript.value = emptyList()
            return
        }
        val (nextDir, output) = LocalTerminalSimulator.executeCommand(trimmed, _terminalWorkingDir.value)
        _terminalWorkingDir.value = nextDir
        _terminalTranscript.update { prev ->
            (prev + "$ $trimmed" + output.lines()).takeLast(120)
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = installedAppsEngine.queryInstalledApps()
        }
    }

    fun extractInstalledApp(record: InstalledAppRecord) {
        val baseDir = activePane().currentPath.takeIf { !VirtualArchiveBrowserEngine.isVirtualPath(it) }
            ?: workspaceRoot.absolutePath
        val targetDir = File(baseDir, "Extracted_APKs")
        taskCenter.enqueueAndRun(
            type = OperationType.APP_APK_EXTRACT,
            titleAr = "استخراج Base/Split APKs لـ ${record.label}",
            titleEn = "Extract Base/Split APKs for ${record.label}",
            sources = listOfNotNull(record.baseApkPath) + record.splitApkPaths,
            destination = targetDir.absolutePath
        ) { _, _ ->
            when (val res = installedAppsEngine.extractAppApks(record, targetDir)) {
                is AppResult.Success -> {
                    _extractedBundle.value = res.data
                    viewModelScope.launch {
                        refreshPane(activePane().paneId)
                    }
                    AppResult.Success(res.data.extractedFiles)
                }
                is AppResult.Failure -> {
                    viewModelScope.launch {
                        _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
                    }
                    res
                }
            }
        }
    }

    fun refreshSystemCapabilities() {
        _privilegeStatuses.value = privilegeProber.probeAllProviders()
        _lanServerState.value = lanServer.getState()
    }

    fun toggleLanServer() {
        if (_lanServerState.value.isRunning) {
            _lanServerState.value = lanServer.stopServer()
            _statusToast.value = if (_isArabicRtl.value) "تم إيقاف خادم LAN وإغلاق المنفذ فورًا" else "LAN server stopped and socket closed"
        } else {
            when (val res = lanServer.startServer(workspaceRoot)) {
                is AppResult.Success -> {
                    _lanServerState.value = res.data
                    _statusToast.value = if (_isArabicRtl.value) {
                        "خادم LAN نشط على 127.0.0.1:${res.data.port} برمز جلسة محمي"
                    } else {
                        "LAN server started on 127.0.0.1:${res.data.port} with token"
                    }
                }
                is AppResult.Failure -> {
                    _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
                }
            }
        }
    }

    fun validateSampleExtensionManifest(jsonText: String) {
        when (val res = HonestCapabilityRegistry.validateExtensionManifestJson(jsonText)) {
            is AppResult.Success -> {
                _validatedExtension.value = res.data
                _statusToast.value = if (_isArabicRtl.value) {
                    "بيان الإضافة صالح (${res.data.name} v${res.data.version}) — معزول عن صلاحيات Root"
                } else {
                    "Valid extension manifest (${res.data.name} v${res.data.version})"
                }
            }
            is AppResult.Failure -> {
                _statusToast.value = res.error.localizedMessage(_isArabicRtl.value)
            }
        }
    }

    fun clearHistoryAndLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearPathHistory()
            dao.clearOperationLogs()
            taskCenter.clearFinishedTasks()
        }
    }

    fun activePane(): FilePaneState = if (_activePaneId.value == "A") _paneA.value else _paneB.value
    fun inactivePane(): FilePaneState = if (_activePaneId.value == "A") _paneB.value else _paneA.value

    private fun filterAndSort(entries: List<FileEntry>, query: String, config: SortConfig): List<FileEntry> {
        val q = query.trim().lowercase()
        val showHidden = _preferences.value.showHiddenFiles
        val visible = if (showHidden) entries else entries.filterNot { it.name.startsWith(".") }
        val filtered = if (q.isEmpty()) {
            visible
        } else {
            visible.filter { it.name.lowercase().contains(q) || it.extension.lowercase().contains(q) }
        }
        val comparator = Comparator<FileEntry> { a, b ->
            if (config.directoriesFirst && a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val cmp = when (config.field) {
                SortField.NAME -> a.name.lowercase().compareTo(b.name.lowercase())
                SortField.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                SortField.DATE -> a.lastModified.compareTo(b.lastModified)
                SortField.EXTENSION -> a.extension.compareTo(b.extension)
            }
            if (config.ascending) cmp else -cmp
        }
        return filtered.sortedWith(comparator)
    }

    override fun onCleared() {
        super.onCleared()
        lanServer.stopServer()
    }
}
