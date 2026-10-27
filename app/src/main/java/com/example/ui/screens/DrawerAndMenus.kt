package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.i18n.AppStrings
import com.example.engines.system.DiskVolumeStats
import com.example.ui.viewmodel.DrawerTool
import com.example.ui.viewmodel.MainSection
import com.example.ui.viewmodel.UserPreferencesState
import com.example.ui.viewmodel.YemenManagerViewModel

val HeaderCharcoalColor = Color(0xFF26292E)
val DrawerHeaderColor = Color(0xFF2B2E33)
val DrawerBadgeColor = Color(0xFF26292E)
val AccentBlueColor = Color(0xFF3B99FC)
val VipBannerBgColor = Color(0xFFFDF0B6)

@Composable
fun YemenNavigationDrawerContent(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    rootStats: DiskVolumeStats,
    storageStats: DiskVolumeStats,
    enabledTools: Set<DrawerTool>,
    preferences: UserPreferencesState,
    onCloseDrawer: () -> Unit,
    onRequestMountSaf: () -> Unit
) {
    var localExpanded by remember { mutableStateOf(true) }
    var toolsExpanded by remember { mutableStateOf(true) }
    var showDrawerOverflowMenu by remember { mutableStateOf(false) }
    var showNetworkSubmenuDialog by remember { mutableStateOf(false) }
    var selectedNetworkProtocolForAdd by remember { mutableStateOf<String?>(null) }
    var showManageToolsDialog by remember { mutableStateOf(false) }
    var showProDetailsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(305.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("navigation_drawer_content")
    ) {
        // Dark Charcoal Drawer Header matching Screenshot_20261027-175227_1ea4b39d.jpg
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DrawerHeaderColor)
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = strings.appName,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = strings.appName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (strings.isArabic) "الوضع المحلي المتقدم" else "Local-First Pro",
                            color = Color(0xFFB8BDC4),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleLanguage() },
                        modifier = Modifier.size(36.dp).testTag("drawer_language_toggle")
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = strings.languageToggleLabel,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier.size(36.dp).testTag("drawer_theme_toggle")
                    ) {
                        Icon(
                            Icons.Default.DarkMode,
                            contentDescription = strings.themeToggleLabel,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showDrawerOverflowMenu = true },
                            modifier = Modifier.size(36.dp).testTag("drawer_overflow_menu_button")
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Drawer 3-dots Popup Menu matching Screenshot_20261027-163413_d4b6d640.jpg
                        DropdownMenu(
                            expanded = showDrawerOverflowMenu,
                            onDismissRequest = { showDrawerOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(strings.menuThemeFollowsSystem)
                                        Spacer(Modifier.width(8.dp))
                                        Checkbox(
                                            checked = preferences.themeFollowsSystem,
                                            onCheckedChange = { checked ->
                                                viewModel.updatePreferences { it.copy(themeFollowsSystem = checked) }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = AccentBlueColor)
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                                onClick = {
                                    viewModel.updatePreferences { it.copy(themeFollowsSystem = !it.themeFollowsSystem) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.menuAddNetworkStorage) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                onClick = {
                                    showDrawerOverflowMenu = false
                                    showNetworkSubmenuDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.menuAddLocalStorage) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showDrawerOverflowMenu = false
                                    onCloseDrawer()
                                    onRequestMountSaf()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.menuAddNetworkGroup) },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    showDrawerOverflowMenu = false
                                    viewModel.openDrawerTool(DrawerTool.REMOTE_MANAGER)
                                    onCloseDrawer()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.menuManageToolsGroup) },
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                                onClick = {
                                    showDrawerOverflowMenu = false
                                    showManageToolsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (strings.isArabic) "الأذونات وتحسين البطارية" else "Permissions & Battery") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                onClick = {
                                    showDrawerOverflowMenu = false
                                    onCloseDrawer()
                                    viewModel.openPermissionsCenterDialog()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.menuPreferences) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showDrawerOverflowMenu = false
                                    viewModel.selectSection(MainSection.SETTINGS)
                                    onCloseDrawer()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Scrollable Drawer Body
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            // Collapsible Local Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { localExpanded = !localExpanded }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.drawerLocalSection,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (localExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (localExpanded) {
                item {
                    StorageDrawerItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = strings.drawerRootDir,
                        subtitle = rootStats.drawerSubtitle(strings.isArabic),
                        progress = rootStats.usedFraction,
                        onClick = {
                            viewModel.selectSection(MainSection.FILES)
                            viewModel.navigatePane(viewModel.activePaneId.value, viewModel.workspaceRoot.parentFile?.absolutePath ?: viewModel.workspaceRoot.absolutePath)
                            onCloseDrawer()
                        }
                    )
                }
                item {
                    StorageDrawerItem(
                        icon = Icons.Default.SdStorage,
                        title = strings.drawerStorageDir,
                        subtitle = storageStats.drawerSubtitle(strings.isArabic),
                        progress = storageStats.usedFraction,
                        onClick = {
                            viewModel.selectSection(MainSection.FILES)
                            viewModel.navigatePane(viewModel.activePaneId.value, viewModel.workspaceRoot.absolutePath)
                            onCloseDrawer()
                        }
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }

            // Collapsible Tools Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toolsExpanded = !toolsExpanded }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.drawerToolsSection,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (toolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (toolsExpanded) {
                // Pro Toolkit Banner matching the warm gold row in Screenshot_20261027-175227_1ea4b39d.jpg
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VipBannerBgColor)
                            .clickable { showProDetailsDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(DrawerBadgeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = strings.drawerProBanner,
                                color = Color(0xFF1E2228),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = strings.drawerSeeDetails,
                                color = Color(0xFF8C6D1F),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                val allToolsMeta = listOf(
                    Triple(DrawerTool.RECYCLE_BIN, strings.toolRecycleBin, Icons.Default.Delete),
                    Triple(DrawerTool.PLUGIN_MANAGER, strings.toolPluginManager, Icons.Default.Extension),
                    Triple(DrawerTool.REMOTE_MANAGER, strings.toolRemoteManager, Icons.Default.Description),
                    Triple(DrawerTool.SCREEN_COLOR_PICKER, strings.toolScreenColorPicker, Icons.Default.Colorize),
                    Triple(DrawerTool.SIGNING_KEY, strings.toolSigningKey, Icons.Default.Key),
                    Triple(DrawerTool.COMMON_PASSWORDS, strings.toolCommonPasswords, Icons.Default.Lock),
                    Triple(DrawerTool.INSTALLED_APPS, strings.toolInstalledApps, Icons.Default.Apps),
                    Triple(DrawerTool.TEXT_EDITOR, strings.toolTextEditor, Icons.Default.Description),
                    Triple(DrawerTool.TERMINAL_SIMULATOR, strings.toolTerminalSimulator, Icons.Default.Terminal),
                    Triple(DrawerTool.APK_MCP, strings.toolApkMcp, Icons.Default.Android),
                    Triple(DrawerTool.ACTIVITY_RECORD, strings.toolActivityRecord, Icons.Default.History),
                    Triple(DrawerTool.SMALI_INSTRUCTIONS, strings.toolSmaliInstructions, Icons.Default.Code)
                ).filter { enabledTools.contains(it.first) }

                items(allToolsMeta, key = { it.first.id }) { (tool, title, icon) ->
                    DrawerToolRow(
                        title = title,
                        icon = icon,
                        testTag = "drawer_tool_${tool.id}",
                        onClick = {
                            viewModel.openDrawerTool(tool)
                            onCloseDrawer()
                        }
                    )
                }
            }
        }
    }

    // Submenu Dialog: Add network storage (Screenshot_20261027-163418_7b7f95d9.jpg)
    if (showNetworkSubmenuDialog) {
        val protocols = listOf(
            "FTP",
            "FTPS (TLS)",
            "SFTP (SSH)",
            "SMB (LAN)",
            "WebDav",
            "Object Storage (S3)"
        )
        AlertDialog(
            onDismissRequest = { showNetworkSubmenuDialog = false },
            title = { Text(strings.menuAddNetworkStorage, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    protocols.forEach { proto ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNetworkSubmenuDialog = false
                                    selectedNetworkProtocolForAdd = proto
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(proto, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNetworkSubmenuDialog = false }) {
                    Text(strings.confirmCancel, color = AccentBlueColor)
                }
            }
        )
    }

    selectedNetworkProtocolForAdd?.let { proto ->
        var hostInput by remember { mutableStateOf("192.168.1.100:2121") }
        AlertDialog(
            onDismissRequest = { selectedNetworkProtocolForAdd = null },
            title = { Text("${strings.menuAddNetworkStorage} — $proto") },
            text = {
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text(if (strings.isArabic) "العنوان والمنفذ (Host:Port)" else "Host : Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addNetworkProfile(proto, hostInput)
                        selectedNetworkProtocolForAdd = null
                    }
                ) { Text(strings.confirmProceed, color = AccentBlueColor) }
            },
            dismissButton = {
                TextButton(onClick = { selectedNetworkProtocolForAdd = null }) {
                    Text(strings.confirmCancel, color = AccentBlueColor)
                }
            }
        )
    }

    // Manage tools group dialog (Screenshot_20261027-163501_d7a614c2.jpg)
    if (showManageToolsDialog) {
        var tempSelection by remember(enabledTools) { mutableStateOf(enabledTools) }
        val allTools = listOf(
            DrawerTool.RECYCLE_BIN to strings.toolRecycleBin,
            DrawerTool.PLUGIN_MANAGER to strings.toolPluginManager,
            DrawerTool.REMOTE_MANAGER to strings.toolRemoteManager,
            DrawerTool.SCREEN_COLOR_PICKER to strings.toolScreenColorPicker,
            DrawerTool.SIGNING_KEY to strings.toolSigningKey,
            DrawerTool.COMMON_PASSWORDS to strings.toolCommonPasswords,
            DrawerTool.INSTALLED_APPS to strings.toolInstalledApps,
            DrawerTool.TEXT_EDITOR to strings.toolTextEditor,
            DrawerTool.TERMINAL_SIMULATOR to strings.toolTerminalSimulator,
            DrawerTool.APK_MCP to strings.toolApkMcp,
            DrawerTool.ACTIVITY_RECORD to strings.toolActivityRecord,
            DrawerTool.SMALI_INSTRUCTIONS to strings.toolSmaliInstructions
        )
        AlertDialog(
            onDismissRequest = { showManageToolsDialog = false },
            title = { Text(strings.menuManageToolsGroup, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(allTools, key = { it.first.id }) { (tool, label) ->
                        val checked = tempSelection.contains(tool)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelection = if (checked) tempSelection - tool else tempSelection + tool
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    tempSelection = if (isChecked) tempSelection + tool else tempSelection - tool
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AccentBlueColor)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateEnabledTools(tempSelection)
                        showManageToolsDialog = false
                    }
                ) { Text(strings.confirmProceed, color = AccentBlueColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showManageToolsDialog = false }) {
                    Text(strings.confirmCancel, color = AccentBlueColor)
                }
            }
        )
    }

    if (showProDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showProDetailsDialog = false },
            title = { Text("Yemen Manager Pro Suite") },
            text = {
                Text(
                    if (strings.isArabic) {
                        "جميع الخدمات الاحترافية مفعلة بالكامل محليًا دون إنترنت:\n" +
                            "• مدير الملفات المزدوج السريع + تصفح مباشر داخل APK/ZIP\n" +
                            "• مختبر تحليل وتوقيع وضغط ومحاذاة APK (4K Align)\n" +
                            "• فاحص DEX وتعليمات Smali ومستخرج النصوص والترجمة\n" +
                            "• محرر النصوص المتقدم، محرر Hex الثنائي، وأداة المقارنة Diff"
                    } else {
                        "All Pro Toolkit services are 100% unlocked offline:\n" +
                            "• Dual-Pane File Manager + In-Pane Virtual APK/ZIP Browsing\n" +
                            "• APK Static Analyzer, 4K Optimizer, Cloner & RSA-2048 Signer\n" +
                            "• DEX Inspector, Smali Instructions & Translation Extractor\n" +
                            "• Multi-Encoding Text Editor, Binary Hex Editor & Diff Tool"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showProDetailsDialog = false }) {
                    Text(strings.confirmProceed, color = AccentBlueColor)
                }
            }
        )
    }
}

@Composable
private fun StorageDrawerItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    progress: Float,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DrawerBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = AccentBlueColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerToolRow(
    title: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DrawerBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Top-Right 3-Dots Overflow Menu matching:
 * - Standard Folder Mode (Screenshot_20261027-175215_8a6f4de4.jpg)
 * - Virtual APK/Archive Mode (Screenshot_20261027-175219_037ddff5.jpg)
 */
@Composable
fun TopOverflowDropdownMenu(
    expanded: Boolean,
    isVirtualArchiveMode: Boolean,
    strings: AppStrings,
    viewModel: YemenManagerViewModel,
    onDismiss: () -> Unit,
    onOpenSearchDialog: () -> Unit,
    onOpenFilterDialog: () -> Unit,
    onOpenSortDialog: () -> Unit,
    onOpenCharsetDialog: () -> Unit
) {
    val activePaneId = viewModel.activePaneId.value
    val activePath = viewModel.activePane().currentPath

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(strings.refresh) },
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            onClick = {
                onDismiss()
                viewModel.refreshPane(activePaneId)
            }
        )
        DropdownMenuItem(
            text = { Text(strings.menuSearch) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            onClick = {
                onDismiss()
                onOpenSearchDialog()
            }
        )
        if (isVirtualArchiveMode) {
            DropdownMenuItem(
                text = { Text(strings.menuSearchXml) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onOpenSearchDialog()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(strings.menuFilter) },
            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
            onClick = {
                onDismiss()
                onOpenFilterDialog()
            }
        )
        DropdownMenuItem(
            text = { Text(strings.menuSort) },
            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
            onClick = {
                onDismiss()
                onOpenSortDialog()
            }
        )

        if (isVirtualArchiveMode) {
            DropdownMenuItem(
                text = { Text(strings.menuCharset) },
                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onOpenCharsetDialog()
                }
            )
            DropdownMenuItem(
                text = { Text(strings.menuBackToRoot) },
                leadingIcon = { Icon(Icons.Default.FirstPage, contentDescription = null) },
                onClick = {
                    onDismiss()
                    viewModel.closeVirtualArchiveInActivePane()
                }
            )
            DropdownMenuItem(
                text = { Text(strings.menuHide) },
                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                onClick = {
                    onDismiss()
                    viewModel.closeVirtualArchiveInActivePane()
                }
            )
            DropdownMenuItem(
                text = { Text(strings.menuCloseFile) },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                onClick = {
                    onDismiss()
                    viewModel.closeVirtualArchiveInActivePane()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text(strings.menuOpenTerminal) },
                leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                onClick = {
                    onDismiss()
                    viewModel.openDrawerTool(DrawerTool.TERMINAL_SIMULATOR)
                }
            )
            DropdownMenuItem(
                text = { Text(strings.menuHiddenFiles) },
                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                onClick = {
                    onDismiss()
                    viewModel.updatePreferences { it.copy(showHiddenFiles = !it.showHiddenFiles) }
                    viewModel.refreshPane("A")
                    viewModel.refreshPane("B")
                }
            )
            DropdownMenuItem(
                text = { Text(strings.menuAddBookmark) },
                leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                onClick = {
                    onDismiss()
                    viewModel.toggleBookmark(activePath)
                }
            )
            DropdownMenuItem(
                text = { Text(strings.menuSetAsHome) },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                onClick = {
                    onDismiss()
                    viewModel.updatePreferences {
                        if (activePaneId == "A") it.copy(startupPathLeft = activePath)
                        else it.copy(startupPathRight = activePath)
                    }
                }
            )
        }

        DropdownMenuItem(
            text = { Text(strings.menuSwapWindows) },
            leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            onClick = {
                onDismiss()
                viewModel.swapPanes()
            }
        )
        DropdownMenuItem(
            text = { Text(if (strings.isArabic) "الأذونات وتحسين البطارية" else "Permissions & Battery") },
            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
            onClick = {
                onDismiss()
                viewModel.openPermissionsCenterDialog()
            }
        )
        DropdownMenuItem(
            text = { Text(strings.menuPreferences) },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = {
                onDismiss()
                viewModel.selectSection(MainSection.SETTINGS)
            }
        )
        DropdownMenuItem(
            text = { Text(strings.menuExit) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
            onClick = {
                onDismiss()
            }
        )
    }
}
