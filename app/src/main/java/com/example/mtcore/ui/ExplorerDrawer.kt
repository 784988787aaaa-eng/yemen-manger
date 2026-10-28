package com.example.mtcore.ui

import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mtcore.R
import java.io.File
import java.util.Locale

private val DrawerHeaderBg = Color(0xFF2B2B2B)
private val CircleIconBg = Color(0xFF262626)
private val ProgressBlue = Color(0xFF2196F3)
private val PrimaryText = Color(0xFF111111)
private val SecondaryText = Color(0xFF757575)
private val SectionDivider = Color(0xFFEAEAEA)

private data class VolumeUsage(
    val usedBytes: Long,
    val availableBytes: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val percentText: String
        get() = "${(fraction * 100f).toInt()}%"
}

private fun readVolumeUsage(path: String): VolumeUsage {
    return runCatching {
        val stat = StatFs(path)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        val used = (total - avail).coerceAtLeast(0L)
        VolumeUsage(usedBytes = used, availableBytes = avail, totalBytes = total)
    }.getOrElse {
        val file = File(path)
        val total = file.totalSpace
        val avail = file.usableSpace
        val used = (total - avail).coerceAtLeast(0L)
        VolumeUsage(usedBytes = used, availableBytes = avail, totalBytes = total)
    }
}

private fun formatGigabytes(bytes: Long): String {
    return String.format(Locale.US, "%.2fG", bytes / (1024.0 * 1024.0 * 1024.0))
}

@Composable
fun ExplorerDrawerContent(
    languageCode: String,
    onToggleLanguage: () -> Unit,
    onNavigateRoot: () -> Unit,
    onNavigateStorage: () -> Unit,
    onOpenInstalledApps: () -> Unit,
    onOpenSigningKey: () -> Unit = {},
    onOpenTextEditor: () -> Unit,
    onOpenTerminalSimulator: () -> Unit = {},
    onOpenSmaliReference: () -> Unit = {},
    onOpenColorPicker: () -> Unit = {},
    onOpenUtilityDialog: (String, String) -> Unit = { _, _ -> },
    onCloseDrawer: () -> Unit
) {
    val context = LocalContext.current
    var localExpanded by remember { mutableStateOf(true) }
    var toolsExpanded by remember { mutableStateOf(true) }

    val rootUsage = remember { readVolumeUsage(Environment.getRootDirectory().absolutePath) }
    val storageUsage = remember { readVolumeUsage("/storage/emulated/0") }

    Surface(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(),
        color = Color.White,
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // 1. Drawer Header (#2B2B2B)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DrawerHeaderBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF424242)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = Color(0xFFE0E0E0),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF3D3D3D),
                    modifier = Modifier.clickable { onToggleLanguage() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = if (languageCode == "en") stringResource(R.string.switch_to_arabic) else stringResource(R.string.switch_to_english),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (languageCode == "en") "AR" else "EN",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Scrollable Collapsible Sections
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Section 1: Local
                CollapsibleSectionHeader(
                    title = stringResource(R.string.drawer_local),
                    expanded = localExpanded,
                    onToggle = { localExpanded = !localExpanded }
                )
                AnimatedVisibility(visible = localExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        StorageVolumeDrawerItem(
                            icon = Icons.Default.Memory,
                            title = stringResource(R.string.drawer_root_directory),
                            usage = rootUsage,
                            onClick = {
                                onCloseDrawer()
                                onNavigateRoot()
                            }
                        )
                        StorageVolumeDrawerItem(
                            icon = Icons.Default.SdStorage,
                            title = stringResource(R.string.drawer_storage_directory),
                            usage = storageUsage,
                            onClick = {
                                onCloseDrawer()
                                onNavigateStorage()
                            }
                        )
                    }
                }

                HorizontalDivider(thickness = 0.8.dp, color = SectionDivider, modifier = Modifier.padding(vertical = 4.dp))

                // Section 2: Tools (No Purchase VIP button!)
                CollapsibleSectionHeader(
                    title = stringResource(R.string.drawer_tools),
                    expanded = toolsExpanded,
                    onToggle = { toolsExpanded = !toolsExpanded }
                )
                AnimatedVisibility(visible = toolsExpanded) {
                    val tools = listOf<Triple<ImageVector, Int, () -> Unit>>(
                        Triple(Icons.Default.Delete, R.string.tool_recycle_bin) {
                            onCloseDrawer()
                            onOpenUtilityDialog(context.getString(R.string.tool_recycle_bin), "recycle_bin")
                        },
                        Triple(Icons.Default.Extension, R.string.tool_plugin_manager) {
                            onCloseDrawer()
                            onOpenUtilityDialog(context.getString(R.string.tool_plugin_manager), "plugin_manager")
                        },
                        Triple(Icons.Default.Wifi, R.string.tool_remote_manager) {
                            onCloseDrawer()
                            onOpenUtilityDialog(context.getString(R.string.tool_remote_manager), "remote_manager")
                        },
                        Triple(Icons.Default.Colorize, R.string.tool_color_picker) {
                            onCloseDrawer()
                            onOpenColorPicker()
                        },
                        Triple(Icons.Default.Key, R.string.tool_signing_key) {
                            onCloseDrawer()
                            onOpenSigningKey()
                        },
                        Triple(Icons.Default.Lock, R.string.tool_common_passwords) {
                            onCloseDrawer()
                            onOpenUtilityDialog(context.getString(R.string.tool_common_passwords), "common_passwords")
                        },
                        Triple(Icons.Default.Android, R.string.tool_installed_apps) {
                            onCloseDrawer()
                            onOpenInstalledApps()
                        },
                        Triple(Icons.Default.Description, R.string.tool_text_editor) {
                            onCloseDrawer()
                            onOpenTextEditor()
                        },
                        Triple(Icons.Default.Terminal, R.string.tool_terminal_simulator) {
                            onCloseDrawer()
                            onOpenTerminalSimulator()
                        },
                        Triple(Icons.Default.Storage, R.string.tool_apk_mcp) {
                            onCloseDrawer()
                            onOpenUtilityDialog(context.getString(R.string.tool_apk_mcp), "apk_mcp")
                        },
                        Triple(Icons.Default.CompassCalibration, R.string.tool_activity_record) {
                            onCloseDrawer()
                            onOpenUtilityDialog(context.getString(R.string.tool_activity_record), "activity_record")
                        },
                        Triple(Icons.Default.Search, R.string.tool_smali_instructions) {
                            onCloseDrawer()
                            onOpenSmaliReference()
                        }
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        tools.forEach { (icon, titleRes, action) ->
                            ToolDrawerRow(
                                icon = icon,
                                title = stringResource(titleRes),
                                onClick = action
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = SecondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = SecondaryText,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StorageVolumeDrawerItem(
    icon: ImageVector,
    title: String,
    usage: VolumeUsage,
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
                .size(36.dp)
                .clip(CircleShape)
                .background(CircleIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { usage.fraction },
                    color = ProgressBlue,
                    trackColor = Color(0xFFE0E0E0),
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = usage.percentText,
                    color = SecondaryText,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.storage_usage_summary,
                    formatGigabytes(usage.usedBytes),
                    formatGigabytes(usage.availableBytes)
                ),
                color = SecondaryText,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ToolDrawerRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(CircleIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = PrimaryText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
