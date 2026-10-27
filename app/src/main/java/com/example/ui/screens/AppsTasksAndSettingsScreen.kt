package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.AppStrings
import com.example.core.model.CapabilityEntry
import com.example.core.model.CapabilityState
import com.example.core.model.OperationStatus
import com.example.core.model.OperationTask
import com.example.data.local.OperationLogEntity
import com.example.engines.system.DiskVolumeStats
import com.example.engines.system.ExtensionManifest
import com.example.engines.system.ExtractedPackageBundle
import com.example.engines.system.InstalledAppRecord
import com.example.engines.system.LanServerState
import com.example.engines.system.PermissionsAndSystemIntentsEngine
import com.example.engines.system.PrivilegeProviderStatus
import com.example.ui.viewmodel.YemenManagerViewModel
import java.io.File

/**
 * Installed Apps Screen matching Screenshot_20261027-163622_f2af8651.jpg & 163628_de40ee48.jpg
 */
@Composable
fun InstalledAppsScreen(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    apps: List<InstalledAppRecord>,
    extractedBundle: ExtractedPackageBundle?
) {
    var showSystemApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppForDialog by remember { mutableStateOf<InstalledAppRecord?>(null) }

    val filtered = apps.filter { app ->
        app.isSystemApp == showSystemApps &&
            (searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("installed_apps_screen")
    ) {
        // Dark Charcoal Tab Row: USER APP | SYSTEM APP matching Screenshot_20261027-163622_f2af8651.jpg
        TabRow(
            selectedTabIndex = if (showSystemApps) 1 else 0,
            containerColor = HeaderCharcoalColor,
            contentColor = Color.White,
            indicator = { tabPositions ->
                val idx = if (showSystemApps) 1 else 0
                if (idx < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
                        color = AccentBlueColor,
                        height = 3.dp
                    )
                }
            }
        ) {
            Tab(
                selected = !showSystemApps,
                onClick = { showSystemApps = false },
                text = {
                    Text(
                        text = strings.userAppsTab,
                        fontWeight = FontWeight.Bold,
                        color = if (!showSystemApps) AccentBlueColor else Color(0xFFB8BDC4)
                    )
                },
                modifier = Modifier.testTag("user_apps_chip")
            )
            Tab(
                selected = showSystemApps,
                onClick = { showSystemApps = true },
                text = {
                    Text(
                        text = strings.systemAppsTab,
                        fontWeight = FontWeight.Bold,
                        color = if (showSystemApps) AccentBlueColor else Color(0xFFB8BDC4)
                    )
                },
                modifier = Modifier.testTag("system_apps_chip")
            )
        }

        // Search input row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(strings.searchPlaceholder) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("installed_apps_search_input")
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (strings.isArabic) "لا توجد تطبيقات مطابقة (${apps.size} إجمالي)" else "No packages match filter (${apps.size} total loaded).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("installed_apps_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val baseSize = app.baseApkPath?.let { File(it).length() } ?: (8L * 1024L * 1024L)
                    val splitSize = app.splitApkPaths.sumOf { File(it).length() }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAppForDialog = app }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (app.isSystemApp) Color(0xFF607D8B) else Color(0xFF4CAF50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Android,
                                    contentDescription = app.label,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = app.label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (app.splitApkPaths.isNotEmpty()) {
                                            Surface(
                                                color = Color(0xFF26A69A).copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "SPLIT+${DiskVolumeStats.formatSizeShort(splitSize)}",
                                                    color = Color(0xFF00897B),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = "${app.versionName}  ${DiskVolumeStats.formatSizeShort(baseSize)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = app.packageName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.extractInstalledApp(app) },
                                        modifier = Modifier.size(28.dp).testTag("extract_app_${app.packageName}")
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Extract APK",
                                            tint = AccentBlueColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // App Info Dialog matching Screenshot_20261027-163628_de40ee48.jpg (MORE | EXTRACT APK)
    selectedAppForDialog?.let { app ->
        val clipboard = LocalClipboardManager.current
        val baseSize = app.baseApkPath?.let { File(it).length() } ?: 0L
        val rows = listOf(
            (if (strings.isArabic) "اسم الحزمة:" else "Package name:") to app.packageName,
            (if (strings.isArabic) "رقم الإصدار:" else "Version code:") to app.versionCode.toString(),
            (if (strings.isArabic) "التوقيع:" else "Signature:") to "V2/V3 Verified",
            "Target SDK:" to "Android API ${app.targetSdk}",
            "Minimum SDK:" to "Android API ${app.minSdk}",
            (if (strings.isArabic) "حجم الحزمة:" else "Base APK size:") to "${DiskVolumeStats.formatSizeShort(baseSize)} (${app.splitApkPaths.size} splits)",
            (if (strings.isArabic) "مجلد البيانات 1:" else "Data directory 1:") to "/data/user/0/${app.packageName}",
            (if (strings.isArabic) "مجلد البيانات 2:" else "Data directory 2:") to "/sdcard/Android/data/${app.packageName}",
            (if (strings.isArabic) "مسار APK:" else "APK path:") to (app.baseApkPath ?: "/data/app/${app.packageName}/base.apk"),
            (if (strings.isArabic) "الصلاحيات:" else "Permissions:") to "${app.grantedPermissions.size} Granted / ${app.declaredPermissions.size} Declared"
        )

        Dialog(onDismissRequest = { selectedAppForDialog = null }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF4CAF50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(app.label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(app.versionName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.height(250.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(rows) { (k, v) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { clipboard.setText(AnnotatedString(v)) }
                            ) {
                                Text(k, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(115.dp))
                                Text(
                                    text = v,
                                    fontSize = 13.sp,
                                    color = if (k.contains("Data directory") || k.contains("مجلد البيانات")) AccentBlueColor else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            app.baseApkPath?.let { path ->
                                if (File(path).exists()) {
                                    viewModel.analyzeApkFile(path, switchScreen = true)
                                }
                            }
                            selectedAppForDialog = null
                        }) {
                            Text(if (strings.isArabic) "المزيد (MORE)" else "MORE", color = AccentBlueColor, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                viewModel.extractInstalledApp(app)
                                selectedAppForDialog = null
                            }
                        ) {
                            Text(if (strings.isArabic) "استخراج APK" else "EXTRACT APK", color = AccentBlueColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Extracted APK "Info" Dialog with LOCATE | CLOSE matching Screenshot_20261027-163622_f2af8651.jpg
    extractedBundle?.let { bundle ->
        val firstExtractedFile = bundle.extractedFiles.firstOrNull() ?: bundle.outputDirectory
        AlertDialog(
            onDismissRequest = { viewModel.clearExtractedBundleDialog() },
            title = { Text(if (strings.isArabic) "معلومات الاستخراج (Info)" else "Info", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (strings.isArabic) {
                        "تم حفظ الملف في:\n$firstExtractedFile\n\n${bundle.splitNotice}"
                    } else {
                        "The file has been saved to $firstExtractedFile"
                    },
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearExtractedBundleDialog()
                    }
                ) {
                    Text(if (strings.isArabic) "إغلاق" else "CLOSE", color = AccentBlueColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearExtractedBundleDialog()
                        viewModel.locateFileInActivePane(firstExtractedFile)
                    }
                ) {
                    Text(if (strings.isArabic) "تحديد الموقع (LOCATE)" else "LOCATE", color = AccentBlueColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun TaskCenterScreen(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    tasks: List<OperationTask>,
    logs: List<OperationLogEntity>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("task_center_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.taskCenterTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { viewModel.clearHistoryAndLogs() },
                modifier = Modifier.testTag("clear_tasks_button")
            ) {
                Text(strings.clearCompletedTasks)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("tasks_and_logs_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks, key = { it.operationId }) { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (strings.isArabic) task.titleAr else task.titleEn,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = task.status.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (task.status) {
                                        OperationStatus.SUCCEEDED -> MaterialTheme.colorScheme.secondary
                                        OperationStatus.FAILED -> MaterialTheme.colorScheme.error
                                        else -> AccentBlueColor
                                    }
                                )
                                if (task.status == OperationStatus.RUNNING || task.status == OperationStatus.QUEUED) {
                                    IconButton(onClick = { viewModel.taskCenter.cancelTask(task.operationId) }) {
                                        Icon(Icons.Default.Cancel, contentDescription = strings.cancelOperation)
                                    }
                                }
                            }
                        }

                        if (task.status == OperationStatus.RUNNING) {
                            if (task.progressFraction != null) {
                                LinearProgressIndicator(
                                    progress = { task.progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        task.errorMessage?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        task.warnings.forEach { warn ->
                            Text(warn, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = task.recoverabilityNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (logs.isNotEmpty()) {
                item {
                    Text(
                        text = if (strings.isArabic) "سجل التدقيق الدائم (${logs.size})" else "Persistent Audit Log (${logs.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(logs, key = { it.operationId }) { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${log.operationType}: ${log.summary}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(log.status, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Full Preferences & Capabilities Screen matching Screenshot_20261027-163450 through 163549
 */
@Composable
fun SettingsAndCapabilitiesScreen(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    isArabic: Boolean,
    isDarkTheme: Boolean,
    privileges: List<PrivilegeProviderStatus>,
    lanState: LanServerState,
    capabilities: List<CapabilityEntry>,
    validatedExtension: ExtensionManifest?
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val permissionsSnapshot by viewModel.permissionsSnapshot.collectAsStateWithLifecycle()
    var showLanguagePickerDialog by remember { mutableStateOf(false) }
    var showAboutManualDialog by remember { mutableStateOf(false) }
    var sampleExtensionJson by remember {
        mutableStateOf(
            """{"id":"ym.ext.string_translator","name":"Localization Helper","version":"1.0.0","minCoreVersion":"1.0.0","permissions":["READ_WORKSPACE"]}"""
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("settings_and_capabilities_screen")
    ) {
        // Quick Language & Theme Bar (preserves toggle_language_button & toggle_theme_button testTags)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleLanguage() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlueColor),
                    modifier = Modifier.weight(1f).testTag("toggle_language_button")
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isArabic) "English (LTR)" else "العربية (RTL)")
                }
                OutlinedButton(
                    onClick = { viewModel.toggleTheme() },
                    modifier = Modifier.weight(1f).testTag("toggle_theme_button")
                ) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isDarkTheme) "Light Mode" else "Dark Mode")
                }
            }
            PermissionsQuickBannerCard(
                snapshot = permissionsSnapshot,
                isArabic = isArabic,
                onOpenPermissionsModal = { viewModel.openPermissionsCenterDialog() },
                onRequestBatteryExemption = {
                    PermissionsAndSystemIntentsEngine.launchIgnoreBatteryOptimizations(context)
                    viewModel.refreshPermissionsSnapshot()
                },
                onRequestAllFilesAccess = {
                    PermissionsAndSystemIntentsEngine.launchAllFilesAccessSettings(context)
                    viewModel.refreshPermissionsSnapshot()
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        // Section 1: Startup (Screenshot_20261027-163427_6be0927e.jpg)
        item {
            PreferenceSectionHeader(if (isArabic) "بدء التشغيل (Startup)" else "Startup")
            PreferenceSwitchRow(
                title = if (isArabic) "طلب صلاحية الجذر (Request root permission)" else "Request root permission",
                subtitle = if (isArabic) "طلب صلاحية Root عند بدء التشغيل للعمليات المتقدمة." else "Request root permissions at startup. After modifying this setting, restart to take effect.",
                checked = prefs.requestRootPermission,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(requestRootPermission = c) } }
            )
            PreferenceSwitchRow(
                title = if (isArabic) "طلب صلاحية الصدفة (Request shell permission)" else "Request shell permission",
                subtitle = if (isArabic) "استخدام Shizuku للحصول على صلاحية ADB Shell عند عدم توفر Root." else "If root permission is not obtained at startup, try to call Shizuku to obtain shell permission.",
                checked = prefs.requestShellPermission,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(requestShellPermission = c) } }
            )
            PreferenceClickRow(
                title = if (isArabic) "تخصيص أمر su (Customize su command)" else "Customize su command",
                subtitle = if (isArabic) "الأمر المستخدم لطلب صلاحية الجذر (${prefs.customSuCommand})" else "Command used to request root permission, leave blank for automatic identification."
            )
            PreferenceClickRow(
                title = if (isArabic) "مسار البداية - النافذة اليسرى" else "Startup path - left window",
                subtitle = prefs.startupPathLeft
            )
            PreferenceClickRow(
                title = if (isArabic) "مسار البداية - النافذة اليمنى" else "Startup path - right window",
                subtitle = prefs.startupPathRight
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        // Section 2: Appearance (Screenshot_20261027-163519_faa919b7.jpg)
        item {
            PreferenceSectionHeader(if (isArabic) "المظهر (Appearance)" else "Appearance")
            PreferenceClickRow(
                title = if (isArabic) "أيقونة المشغل (Launcher icon)" else "Launcher icon",
                subtitle = if (isArabic) "افتراضي (Yemen Manager Adaptive)" else "Default"
            )
            PreferenceClickRow(
                title = if (isArabic) "لون السمة (Theme color)" else "Theme color",
                subtitle = if (isDarkTheme) "Dark Charcoal" else "Default",
                onClick = { viewModel.toggleTheme() }
            )
            PreferenceClickRow(
                title = if (isArabic) "حجم قائمة الملفات (File list size)" else "File list size",
                subtitle = prefs.fileListSize
            )
            PreferenceClickRow(
                title = if (isArabic) "الحد الأقصى لأسطر اسم الملف" else "Max lines of file name",
                subtitle = prefs.maxLinesFileName
            )
            PreferenceSwitchRow(
                title = if (isArabic) "إخفاء رموز الصلاحيات في القائمة" else "Disable permission in file list",
                subtitle = if (isArabic) "عدم عرض صلاحيات القراءة والكتابة مثل rwx بجانب الملفات." else "Do not display file read and write permissions, such as rwx.",
                checked = prefs.disablePermissionInFileList,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(disablePermissionInFileList = c) } }
            )
            PreferenceClickRow(
                title = if (isArabic) "تنسيق التاريخ والوقت (Date time format)" else "Date time format",
                subtitle = "26-10-27 16:35"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        // Section 3: General (Screenshot_20261027-163522_07ad0b82.jpg)
        item {
            PreferenceSectionHeader(if (isArabic) "عام (General)" else "General")
            PreferenceSwitchRow(
                title = if (isArabic) "إنشاء ملف نسخ احتياطي (.bak / .ym_work)" else "Generate backup file",
                subtitle = if (isArabic) "الاحتفاظ بنسخة احتياطية تلقائيًا عند تعديل الملفات أو توقيع الحزم." else "Generate .bak file when modifying files or signing files.",
                checked = prefs.generateBackupFile,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(generateBackupFile = c) } }
            )
            PreferenceSwitchRow(
                title = if (isArabic) "الحفاظ على وقت تعديل الملف" else "Preserve file time",
                subtitle = if (isArabic) "الاحتفاظ بتاريخ التعديل الأصلي عند نسخ الملفات أو استخراجها." else "Preserve last modified time when copying or extracting files.",
                checked = prefs.preserveFileTime,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(preserveFileTime = c) } }
            )
            PreferenceSwitchRow(
                title = if (isArabic) "تفعيل مفضلة مربعات الإدخال" else "Enable input box favorites feature",
                subtitle = if (isArabic) "عرض زر المفضلة داخل حقول البحث والاستبدال." else "Display a favorite button inside the input box.",
                checked = prefs.enableInputBoxFavorites,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(enableInputBoxFavorites = c) } }
            )
            PreferenceClickRow(
                title = if (isArabic) "مجلد العمل المخصص لـ Yemen Manager" else "Custom YM directory",
                subtitle = viewModel.workspaceRoot.absolutePath
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        // Section 4: Recycle Bin (Screenshot_20261027-163526_7f429fac.jpg)
        item {
            PreferenceSectionHeader(if (isArabic) "سلة المحذوفات (Recycle Bin)" else "Recycle Bin")
            PreferenceSwitchRow(
                title = if (isArabic) "تفعيل ميزة سلة المحذوفات" else "Enable recycle bin feature",
                subtitle = if (isArabic) "إمكانية نقل الملفات لسلة المحذوفات بدلاً من الحذف الفوري." else "When deleting files in local storage, you can choose to move them to the recycle bin.",
                checked = prefs.enableRecycleBin,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(enableRecycleBin = c) } }
            )
            PreferenceSwitchRow(
                title = if (isArabic) "النقل لسلة المحذوفات افتراضيًا" else "Move to recycle bin by default",
                subtitle = if (isArabic) "تحديد خيار النقل للسلة تلقائيًا في نافذة الحذف." else "In the delete dialog, check Move to Recycle Bin by default.",
                checked = prefs.moveToRecycleBinByDefault,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(moveToRecycleBinByDefault = c) } }
            )
            PreferenceClickRow(
                title = if (isArabic) "تنظيف سلة المحذوفات تلقائيًا" else "Automatically clean recycle bin files",
                subtitle = prefs.autoCleanRecycleBinDays
            )
            PreferenceSwitchRow(
                title = if (isArabic) "عرض تحذير الحذف" else "Show deletion warning",
                subtitle = if (isArabic) "عرض تنبيه تأكيدي باللون الأحمر عند الحذف النهائي." else "Show red warning text when deleting files permanently.",
                checked = prefs.showDeletionWarning,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(showDeletionWarning = c) } }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        // Section 5: Installation (Screenshot_20261027-163528_e5a84441.jpg)
        item {
            PreferenceSectionHeader(if (isArabic) "التثبيت (Installation)" else "Installation")
            PreferenceSwitchRow(
                title = if (isArabic) "التحقق من صلاحية توقيع APK قبل التثبيت" else "APK installation verification",
                subtitle = if (isArabic) "فحص التوقيع وتوافق SDK قبل تمرير الحزمة للمثبت." else "Check whether the signature and SDK version of the APK meet installation requirements.",
                checked = prefs.apkInstallVerification,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(apkInstallVerification = c) } }
            )
            PreferenceSwitchRow(
                title = if (isArabic) "تأكيد مزدوج قبل التثبيت" else "Double confirmation before installing",
                subtitle = if (isArabic) "عرض نافذة تأكيد إضافية عند الضغط على زر التثبيت." else "Display a confirmation dialog when clicking the install button.",
                checked = prefs.doubleConfirmBeforeInstalling,
                onCheckedChange = { c -> viewModel.updatePreferences { it.copy(doubleConfirmBeforeInstalling = c) } }
            )
            PreferenceClickRow(
                title = if (isArabic) "استخدام Shizuku/Dhizuku/Root للتثبيت" else "Use Shizuku/Dhizuku/Root to install",
                subtitle = prefs.installerMethod
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        // Section 5B: Local Backup & Recovery (matching Screenshot_20261027-163450_9c10f5b2.jpg & 163453_f3fc043d.jpg)
        item {
            PreferenceSectionHeader(if (isArabic) "النسخ الاحتياطي المحلي (Local Backup)" else "Local backup")
            PreferenceClickRow(
                title = if (isArabic) "حفظ نسخة احتياطية للإعدادات محليًا" else "Backup local data",
                subtitle = if (isArabic) "حفظ بياناتك وإعداداتك المحلية في ملف JSON داخل مساحة العمل." else "Back up your local data and preferences to local storage.",
                onClick = { viewModel.exportLocalSettingsBackup() }
            )
            PreferenceClickRow(
                title = if (isArabic) "استعادة الإعدادات من النسخة الاحتياطية" else "Recover from local backup",
                subtitle = if (isArabic) "استرجاع بياناتك وإعداداتك المحفوظة إلى جهازك المحلي." else "Restore your backed-up data to your local device.",
                onClick = { viewModel.restoreLocalSettingsBackup() }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        // Section 6: Other & Language (Screenshot_20261027-163450_9c10f5b2.jpg)
        item {
            PreferenceSectionHeader(if (isArabic) "أخرى (Other)" else "Other")
            PreferenceClickRow(
                title = if (isArabic) "اللغة (Language)" else "Language",
                subtitle = if (isArabic) "العربية (Arabic RTL)" else "English (LTR)",
                onClick = { showLanguagePickerDialog = true }
            )
            PreferenceClickRow(
                title = if (isArabic) "دليل الاستخدام والمعمارية (Manual & About)" else "About & Architecture Manual",
                subtitle = "Yemen Manager v1.0.0 • 100% Offline Local-First",
                onClick = { showAboutManualDialog = true }
            )
            PreferenceClickRow(
                title = if (isArabic) "شكر وتقدير ومشاريع المصدر المفتوح" else "Acknowledgement",
                subtitle = if (isArabic) "مكونات Android Jetpack القياسية والمكتبات المحلية المعتمدة." else "Android Jetpack & verified offline open-source components."
            )
            PreferenceClickRow(
                title = if (isArabic) "اتفاقية المستخدم وسياسة الخصوصية" else "User Agreement and Privacy Policy",
                subtitle = if (isArabic) "لا يتم جمع أو إرسال أي بيانات خارج الجهاز." else "100% local execution. Zero telemetry or external tracking."
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        // Section 7: Local LAN Server & Sandboxed Extensions & Honest Capability Matrix
        item {
            PreferenceSectionHeader(strings.lanServerSectionTitle)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (lanState.isRunning) {
                        "ACTIVE: http://${lanState.bindAddress}:${lanState.port}/?token=${lanState.sessionToken}"
                    } else {
                        "STOPPED (Zero background sockets or automatic connections)."
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (lanState.isRunning) AccentBlueColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.toggleLanServer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (lanState.isRunning) MaterialTheme.colorScheme.error else AccentBlueColor
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("lan_server_toggle_button")
                ) {
                    Icon(Icons.Default.Router, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (lanState.isRunning) strings.stopLanServer else strings.startLanServer)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        item {
            PreferenceSectionHeader(strings.extensionsSectionTitle)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sampleExtensionJson,
                    onValueChange = { sampleExtensionJson = it },
                    textStyle = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().testTag("extension_manifest_input")
                )
                OutlinedButton(
                    onClick = { viewModel.validateSampleExtensionManifest(sampleExtensionJson) },
                    modifier = Modifier.fillMaxWidth().testTag("validate_extension_button")
                ) {
                    Text(if (isArabic) "التحقق من بيان الإضافة المعزولة (FR-EXT-002)" else "Validate Extension Manifest (FR-EXT-002)")
                }
                validatedExtension?.let { ext ->
                    Text(
                        text = "Verified: ${ext.name} (${ext.id} v${ext.version}) • Permissions: ${ext.requestedPermissions.joinToString()}",
                        fontSize = 12.sp,
                        color = AccentBlueColor
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }

        item {
            PreferenceSectionHeader(strings.privilegeSectionTitle)
        }
        items(privileges, key = { it.id }) { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isArabic) p.nameAr else p.nameEn, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(if (isArabic) p.detailAr else p.detailEn, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    imageVector = if (p.isAvailable) Icons.Default.CheckCircle else Icons.Default.Security,
                    contentDescription = null,
                    tint = if (p.isAvailable) AccentBlueColor else MaterialTheme.colorScheme.outline
                )
            }
        }

        item {
            PreferenceSectionHeader(strings.capabilityMatrixTitle)
        }
        items(capabilities, key = { it.id }) { cap ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) cap.featureAr else cap.featureEn,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isArabic) cap.state.labelAr else cap.state.labelEn,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (cap.state) {
                            CapabilityState.COMPLETED -> AccentBlueColor
                            CapabilityState.EXPERIMENTAL -> Color(0xFFF6BB42)
                            CapabilityState.NOT_SUPPORTED -> MaterialTheme.colorScheme.error
                            CapabilityState.PLANNED -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Text("SRS: ${cap.srsRef}", fontSize = 11.sp, color = AccentBlueColor)
                Text(if (isArabic) cap.notesAr else cap.notesEn, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showLanguagePickerDialog) {
        AlertDialog(
            onDismissRequest = { showLanguagePickerDialog = false },
            title = { Text(if (isArabic) "اختر اللغة (Language)" else "Select Language") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setLanguageArabic(true)
                                showLanguagePickerDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isArabic,
                            onClick = {
                                viewModel.setLanguageArabic(true)
                                showLanguagePickerDialog = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentBlueColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("العربية (Arabic — RTL)")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setLanguageArabic(false)
                                showLanguagePickerDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isArabic,
                            onClick = {
                                viewModel.setLanguageArabic(false)
                                showLanguagePickerDialog = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentBlueColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("English (LTR)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePickerDialog = false }) {
                    Text(strings.confirmCancel, color = AccentBlueColor)
                }
            }
        )
    }

    if (showAboutManualDialog) {
        AlertDialog(
            onDismissRequest = { showAboutManualDialog = false },
            title = { Text("Yemen Manager v1.0.0") },
            text = {
                Text(
                    if (isArabic) {
                        "حقيبة هندسية محلية متكاملة لإدارة الملفات وفحص الأرشيفات وتحليل وتوقيع حزم APK على أندرويد.\n\n" +
                            "• مدير ملفات ثنائي النوافذ مع تصفح افتراضي مباشر داخل APK/ZIP\n" +
                            "• حماية صارمة للملفات الأصلية (الكتابة على نسخة عمل أولاً)\n" +
                            "• يعمل محليًا 100% باللغتين العربية والإنجليزية"
                    } else {
                        "Complete Local-First Android File Manager, Archive Inspector & APK Engineering Toolkit.\n\n" +
                            "• Dual-Pane File Manager with direct in-pane virtual APK/ZIP browsing\n" +
                            "• Strict non-destructive working-copy safety model\n" +
                            "• 100% Offline bilingual (Arabic RTL & English LTR)"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutManualDialog = false }) {
                    Text(strings.confirmProceed, color = AccentBlueColor)
                }
            }
        )
    }
}

@Composable
private fun PreferenceSectionHeader(title: String) {
    Text(
        text = title,
        color = AccentBlueColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AccentBlueColor)
        )
    }
}

@Composable
private fun PreferenceClickRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
