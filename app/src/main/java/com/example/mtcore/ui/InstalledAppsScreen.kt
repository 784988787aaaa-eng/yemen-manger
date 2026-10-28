package com.example.mtcore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.mtcore.R
import com.example.mtcore.data.AppRepository
import com.example.mtcore.data.InstalledAppInfo
import java.io.File

private val TopBarBg = Color(0xFF2B2B2B)
private val ScreenBg = Color(0xFFF2F2F2)
private val ActionBlue = Color(0xFF1976D2)
private val LabelDark = Color(0xFF222222)
private val ValueGray = Color(0xFF757575)

@Composable
fun InstalledAppsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLocateExtractedApk: (File) -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var extractedApkFile by remember { mutableStateOf<File?>(null) }

    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        allApps = AppRepository.getInstalledApps(context)
        isLoading = false
    }

    val filteredApps = remember(allApps, selectedTab, searchQuery) {
        val tabFiltered = allApps.filter { app ->
            if (selectedTab == 0) !app.isSystemApp else app.isSystemApp
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            tabFiltered
        } else {
            tabFiltered.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    val userAppsCount = remember(allApps) { allApps.count { !it.isSystemApp } }
    val systemAppsCount = remember(allApps) { allApps.count { it.isSystemApp } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Top Bar (#2B2B2B)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TopBarBg)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isSearchActive) {
                        isSearchActive = false
                        searchQuery = ""
                    } else {
                        onBack()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }

                if (isSearchActive) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_apps_hint),
                                        color = Color(0xFFBDBDBD),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    IconButton(onClick = {
                        if (searchQuery.isNotEmpty()) {
                            searchQuery = ""
                        } else {
                            isSearchActive = false
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.installed_apps),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_apps_hint),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = Color.White
                        )
                    }
                }
            }

            // TabRow (USER APP / SYSTEM APP)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = TopBarBg,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 2.5.dp,
                            color = Color.White
                        )
                    }
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "${stringResource(R.string.user_app)} ($userAppsCount)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) Color.White else Color(0xFFB0B0B0)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "${stringResource(R.string.system_app)} ($systemAppsCount)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) Color.White else Color(0xFFB0B0B0)
                        )
                    }
                )
            }
        }

        // Content Area
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = ActionBlue)
                    Text(
                        text = stringResource(R.string.loading),
                        color = ValueGray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredApps,
                    key = { app -> app.packageName }
                ) { app ->
                    InstalledAppCard(
                        app = app,
                        onClick = { selectedApp = app }
                    )
                }
            }
        }
    }

    var splitExtractTarget by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var isMergingSplitApp by remember { mutableStateOf(false) }

    // App Details & Extract APK Dialog
    selectedApp?.let { app ->
        AppDetailsDialog(
            app = app,
            onDismiss = { selectedApp = null },
            onExtractApk = {
                if (app.splitSourceDirs.isNotEmpty()) {
                    selectedApp = null
                    splitExtractTarget = app
                } else {
                    viewModel.extractInstalledApk(app) { savedFile, error ->
                        selectedApp = null
                        if (savedFile != null) {
                            extractedApkFile = savedFile
                        } else if (error != null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.extract_failed, error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
    }

    splitExtractTarget?.let { splitApp ->
        SplitApkExtractOptionsDialog(
            app = splitApp,
            isProcessing = isMergingSplitApp,
            onDismiss = { if (!isMergingSplitApp) splitExtractTarget = null },
            onExtractApksBundle = {
                isMergingSplitApp = true
                viewModel.extractInstalledSplitApksBundle(splitApp) { savedFile, error ->
                    isMergingSplitApp = false
                    splitExtractTarget = null
                    if (savedFile != null) {
                        extractedApkFile = savedFile
                    } else if (error != null) {
                        Toast.makeText(context, context.getString(R.string.extract_failed, error), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onMergeAntiSplit = {
                isMergingSplitApp = true
                viewModel.extractAndMergeInstalledSplitApk(splitApp) { savedFile, error ->
                    isMergingSplitApp = false
                    splitExtractTarget = null
                    if (savedFile != null) {
                        extractedApkFile = savedFile
                    } else if (error != null) {
                        Toast.makeText(context, context.getString(R.string.extract_failed, error), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onExtractBaseOnly = {
                isMergingSplitApp = true
                viewModel.extractInstalledApk(splitApp) { savedFile, error ->
                    isMergingSplitApp = false
                    splitExtractTarget = null
                    if (savedFile != null) {
                        extractedApkFile = savedFile
                    } else if (error != null) {
                        Toast.makeText(context, context.getString(R.string.extract_failed, error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Info Dialog after APK extraction with LOCATE and CLOSE
    extractedApkFile?.let { file ->
        ExtractedApkInfoDialog(
            savedFile = file,
            onDismiss = { extractedApkFile = null },
            onLocate = {
                val target = file
                extractedApkFile = null
                onLocateExtractedApk(target)
            }
        )
    }
}

@Composable
private fun SplitApkExtractOptionsDialog(
    app: InstalledAppInfo,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onExtractApksBundle: () -> Unit,
    onMergeAntiSplit: () -> Unit,
    onExtractBaseOnly: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${stringResource(R.string.extract_split_mode_title)} (${app.splitSourceDirs.size + 1} APKs)",
                    color = Color(0xFF111111),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE0E0E0))

                if (isProcessing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = ActionBlue, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.antisplit_merging_message), color = LabelDark, fontSize = 13.sp)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.extract_split_bundle_apks),
                        color = ActionBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExtractApksBundle() }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.extract_and_merge_antisplit),
                        color = Color(0xFF2E7D32),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMergeAntiSplit() }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.extract_base_only),
                        color = LabelDark,
                        fontSize = 13.5.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExtractBaseOnly() }
                            .padding(vertical = 8.dp)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !isProcessing, onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = ActionBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppCard(
    app: InstalledAppInfo,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap by AppRepository.rememberPackageIcon(context, app.packageName, app.sourceDir)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF262626)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = Color(0xFF111111),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val sizeLine = buildString {
                    append(app.versionName)
                    append("  ")
                    append(app.formattedBaseSize())
                    if (app.splitSizeBytes > 0L) {
                        append("  SPLIT+")
                        append(app.formattedSplitSize())
                    }
                }
                Text(
                    text = sizeLine,
                    color = ValueGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    color = ValueGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AppDetailsDialog(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    onExtractApk: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap by AppRepository.rememberPackageIcon(context, app.packageName, app.sourceDir)
    var showMoreMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header: Icon (44.dp) + App Name (16.sp Bold) + versionName
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap!!,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF262626)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            color = Color(0xFF111111),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.versionName,
                            color = ValueGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE5E5E5))
                Spacer(Modifier.height(8.dp))

                // 2-Column Metadata Table
                val rows = listOf(
                    stringResource(R.string.package_name_label) to app.packageName,
                    stringResource(R.string.version_code_label) to app.versionCode.toString(),
                    stringResource(R.string.file_size_label) to app.formattedTotalSize(),
                    stringResource(R.string.signature_label) to app.signatureScheme,
                    stringResource(R.string.protection_label) to stringResource(R.string.no_detected),
                    stringResource(R.string.target_sdk_label) to app.formattedTargetSdk(),
                    stringResource(R.string.min_sdk_label) to app.formattedMinSdk(),
                    stringResource(R.string.data_directory_label) to app.dataDir,
                    stringResource(R.string.apk_path_label) to app.sourceDir,
                    stringResource(R.string.first_install_label) to app.formattedFirstInstall(),
                    stringResource(R.string.last_update_label) to app.formattedLastUpdate(),
                    stringResource(R.string.uid_label) to app.uid.toString()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rows.forEach { (label, value) ->
                        MetadataKeyValueRow(
                            label = label,
                            value = value,
                            onLongCopy = { copyToClipboardWithToast(context, value) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom Action Row: MORE on the left, EXTRACT APK on the right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        TextButton(onClick = { showMoreMenu = true }) {
                            Text(
                                text = stringResource(R.string.more_caps),
                                color = ActionBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.launch_app)) },
                                onClick = {
                                    showMoreMenu = false
                                    onDismiss()
                                    launchInstalledApp(context, app.packageName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.app_details)) },
                                onClick = {
                                    showMoreMenu = false
                                    onDismiss()
                                    openSystemAppDetails(context, app.packageName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.uninstall_app)) },
                                onClick = {
                                    showMoreMenu = false
                                    onDismiss()
                                    requestUninstallApp(context, app.packageName)
                                }
                            )
                        }
                    }

                    TextButton(onClick = onExtractApk) {
                        Text(
                            text = stringResource(R.string.extract_apk),
                            color = ActionBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractedApkInfoDialog(
    savedFile: File,
    onDismiss: () -> Unit,
    onLocate: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.info),
                    color = Color(0xFF111111),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.apk_saved_message, savedFile.absolutePath),
                    color = LabelDark,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onLocate) {
                        Text(
                            text = stringResource(R.string.locate),
                            color = ActionBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.close),
                            color = ActionBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApkFileDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onViewArchive: () -> Unit,
    onInstallApk: () -> Unit,
    onShowProperties: () -> Unit,
    onOpenFunctionMenu: (String) -> Unit = { onShowProperties() }
) {
    val context = LocalContext.current
    val iconBitmap by AppRepository.rememberApkFileIcon(context, apkFile)
    var apkInfo by remember(apkFile.absolutePath) { mutableStateOf<InstalledAppInfo?>(null) }
    var isLoading by remember(apkFile.absolutePath) { mutableStateOf(true) }

    LaunchedEffect(apkFile.absolutePath) {
        isLoading = true
        apkInfo = AppRepository.inspectApkFile(context, apkFile)
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val info = apkInfo
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap!!,
                            contentDescription = info?.appName ?: apkFile.name,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF6D4C41)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info?.appName ?: apkFile.nameWithoutExtension,
                            color = Color(0xFF111111),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = info?.versionName ?: apkFile.name,
                            color = ValueGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE5E5E5))
                Spacer(Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ActionBlue, modifier = Modifier.size(28.dp))
                    }
                } else if (info != null) {
                    val installedStatus = if (info.isInstalled) {
                        stringResource(
                            R.string.installed_yes,
                            info.installedVersionName ?: info.versionName,
                            info.installedVersionCode ?: info.versionCode
                        )
                    } else {
                        stringResource(R.string.installed_no)
                    }
                    val rows = listOf(
                        stringResource(R.string.package_name_label) to info.packageName,
                        stringResource(R.string.version_code_label) to info.versionCode.toString(),
                        stringResource(R.string.installed_status_label) to installedStatus,
                        stringResource(R.string.file_size_label) to info.formattedTotalSize(),
                        stringResource(R.string.signature_label) to info.signatureScheme,
                        stringResource(R.string.protection_label) to (if (info.protection == "No Detected") stringResource(R.string.no_detected) else info.protection),
                        stringResource(R.string.target_sdk_label) to info.formattedTargetSdk(),
                        stringResource(R.string.min_sdk_label) to info.formattedMinSdk(),
                        stringResource(R.string.apk_path_label) to apkFile.absolutePath,
                        stringResource(R.string.last_update_label) to info.formattedLastUpdate()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rows.forEach { (label, value) ->
                            MetadataKeyValueRow(
                                label = label,
                                value = value,
                                onLongCopy = { copyToClipboardWithToast(context, value) }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetadataKeyValueRow(
                            label = stringResource(R.string.apk_path_label),
                            value = apkFile.absolutePath,
                            onLongCopy = { copyToClipboardWithToast(context, apkFile.absolutePath) }
                        )
                        MetadataKeyValueRow(
                            label = stringResource(R.string.file_size_label),
                            value = AppRepository.formatBytes(apkFile.length()),
                            onLongCopy = { copyToClipboardWithToast(context, apkFile.length().toString()) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom 3 Buttons: FUNCTION on left, VIEW + INSTALL on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        val pkg = info?.packageName.orEmpty()
                        onDismiss()
                        onOpenFunctionMenu(pkg)
                    }) {
                        Text(
                            text = stringResource(R.string.function_caps),
                            color = ActionBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            onDismiss()
                            onViewArchive()
                        }) {
                            Text(
                                text = stringResource(R.string.view_caps),
                                color = ActionBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = {
                            onDismiss()
                            onInstallApk()
                        }) {
                            Text(
                                text = stringResource(R.string.install_caps),
                                color = ActionBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MetadataKeyValueRow(
    label: String,
    value: String,
    onLongCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongCopy
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = LabelDark,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(108.dp)
        )
        Text(
            text = value,
            color = ValueGray,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun copyToClipboardWithToast(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("mt_info", text))
    Toast.makeText(
        context,
        context.getString(R.string.copied_to_clipboard, text),
        Toast.LENGTH_SHORT
    ).show()
}

fun launchInstalledApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, context.getString(R.string.unable_to_launch_app), Toast.LENGTH_SHORT).show()
            }
    } else {
        Toast.makeText(context, context.getString(R.string.unable_to_launch_app), Toast.LENGTH_SHORT).show()
    }
}

fun openSystemAppDetails(context: Context, packageName: String) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

fun requestUninstallApp(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

fun installApkFile(context: Context, apkFile: File) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
    }.getOrElse {
        Uri.fromFile(apkFile)
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, it.message ?: "Unable to launch installer", Toast.LENGTH_SHORT).show()
        }
}
