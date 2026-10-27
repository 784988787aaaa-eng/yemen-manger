package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.i18n.AppStrings
import com.example.core.model.FileEntry
import com.example.engines.system.DiskVolumeStats
import com.example.engines.system.PermissionsAndSystemIntentsEngine
import com.example.engines.system.SystemPermissionItem
import com.example.engines.system.SystemPermissionsSnapshot
import java.io.File

/**
 * Interactive Permissions, Storage & Battery Optimization Center Dialog.
 * Automatically refreshes permission statuses when returning from system settings (ON_RESUME).
 */
@Composable
fun PermissionsAndBatteryCenterDialog(
    snapshot: SystemPermissionsSnapshot,
    strings: AppStrings,
    onRefreshSnapshot: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-refresh when the user returns from system permission settings screens
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshSnapshot()
    }

    fun triggerPermissionAction(itemId: String) {
        when (itemId) {
            "all_files_access" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    PermissionsAndSystemIntentsEngine.launchAllFilesAccessSettings(context)
                } else {
                    runtimePermissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }
            "battery_optimization" -> {
                PermissionsAndSystemIntentsEngine.launchIgnoreBatteryOptimizations(context)
            }
            "install_unknown_apps" -> {
                PermissionsAndSystemIntentsEngine.launchInstallUnknownAppsSettings(context)
            }
            "post_notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    PermissionsAndSystemIntentsEngine.launchAppDetailsSettings(context)
                }
            }
            else -> {
                PermissionsAndSystemIntentsEngine.launchAppDetailsSettings(context)
            }
        }
        onRefreshSnapshot()
    }

    fun requestAllPendingPermissions() {
        val runtimePerms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !snapshot.allFilesAccessGranted) {
            runtimePerms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            runtimePerms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !snapshot.notificationsGranted) {
            runtimePerms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (runtimePerms.isNotEmpty()) {
            runtimePermissionsLauncher.launch(runtimePerms.toTypedArray())
        }
        when {
            !snapshot.allFilesAccessGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                PermissionsAndSystemIntentsEngine.launchAllFilesAccessSettings(context)
            }
            !snapshot.batteryOptimizationIgnored -> {
                PermissionsAndSystemIntentsEngine.launchIgnoreBatteryOptimizations(context)
            }
            !snapshot.canInstallPackages -> {
                PermissionsAndSystemIntentsEngine.launchInstallUnknownAppsSettings(context)
            }
        }
        onRefreshSnapshot()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("permissions_and_battery_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AccentBlueColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (strings.isArabic) "مركز الأذونات وتحسين البطارية" else "Permissions & Battery Optimization",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (strings.isArabic) {
                                    "الأذونات الممنوحة: ${snapshot.grantedCount} من ${snapshot.totalCount} • عمل محلي 100%"
                                } else {
                                    "Granted: ${snapshot.grantedCount} / ${snapshot.totalCount} • 100% Offline Ready"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = onRefreshSnapshot,
                        modifier = Modifier.testTag("refresh_permissions_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentBlueColor, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Quick "Grant All" action button
                Button(
                    onClick = { requestAllPendingPermissions() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (snapshot.allCriticalGranted) Color(0xFF2E7D32) else AccentBlueColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_all_permissions_button")
                ) {
                    Icon(
                        imageVector = if (snapshot.allCriticalGranted) Icons.Default.CheckCircle else Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (snapshot.allCriticalGranted) {
                            if (strings.isArabic) "جميع الأذونات الأساسية والبطارية مفعلة بالكامل ✓" else "All Critical Storage & Battery Permissions Active ✓"
                        } else {
                            if (strings.isArabic) "منح جميع الأذونات اللازمة واستثناء البطارية الآن" else "Grant All Required Permissions & Battery Exemption"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                // Scrollable list of individual permission cards
                LazyColumn(
                    modifier = Modifier.height(330.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(snapshot.items, key = { it.id }) { item ->
                        PermissionItemRowCard(
                            item = item,
                            isArabic = strings.isArabic,
                            onActionClick = { triggerPermissionAction(item.id) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { PermissionsAndSystemIntentsEngine.launchAppDetailsSettings(context) },
                        modifier = Modifier.testTag("open_app_system_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = AccentBlueColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (strings.isArabic) "إعدادات النظام للتطبيق" else "System App Settings",
                            color = AccentBlueColor,
                            fontSize = 13.sp
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_permissions_dialog_button")
                    ) {
                        Text(
                            text = strings.confirmProceed,
                            color = AccentBlueColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItemRowCard(
    item: SystemPermissionItem,
    isArabic: Boolean,
    onActionClick: () -> Unit
) {
    val icon = when (item.id) {
        "all_files_access" -> Icons.Default.FolderOpen
        "battery_optimization" -> Icons.Default.BatteryChargingFull
        "install_unknown_apps" -> Icons.Default.InstallMobile
        "post_notifications" -> Icons.Default.NotificationsActive
        else -> Icons.Default.Security
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActionClick() }
            .testTag("permission_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (item.isGranted) Color(0xFF2E7D32) else AccentBlueColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) item.titleAr else item.titleEn,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = if (item.isGranted) Color(0xFF2E7D32).copy(alpha = 0.15f) else AccentBlueColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { onActionClick() }
                ) {
                    Text(
                        text = if (isArabic) item.actionLabelAr else item.actionLabelEn,
                        color = if (item.isGranted) Color(0xFF2E7D32) else AccentBlueColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isArabic) item.descriptionAr else item.descriptionEn,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Summary Banner Card for Preferences & Drawer to give one-tap access to Storage, APK Install & Battery Optimization permissions.
 */
@Composable
fun PermissionsQuickBannerCard(
    snapshot: SystemPermissionsSnapshot,
    isArabic: Boolean,
    onOpenPermissionsModal: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("permissions_quick_banner_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = AccentBlueColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "أذونات النظام وتحسين البطارية" else "System Permissions & Battery Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = "${snapshot.grantedCount}/${snapshot.totalCount} ✓",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (snapshot.allCriticalGranted) Color(0xFF2E7D32) else AccentBlueColor
                )
            }

            Text(
                text = if (isArabic) {
                    "تأكد من منح إذن الوصول لجميع الملفات واستثناء تحسين البطارية وتثبيت الحزم لضمان أقصى سرعة واستقرار دون إنترنت."
                } else {
                    "Grant All Files Access, Ignore Battery Optimizations, and APK Install permissions for maximum offline performance."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenPermissionsModal,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlueColor),
                    modifier = Modifier.weight(1f).testTag("open_permissions_center_button")
                ) {
                    Text(
                        text = if (isArabic) "فحص وطلب الأذونات" else "Manage Permissions",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onRequestBatteryExemption,
                    modifier = Modifier.weight(1f).testTag("battery_optimization_quick_button")
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isArabic) "تحسين البطارية" else "Battery Opt.",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Built-in Offline Image Viewer powered by Coil (`io.coil-kt:coil-compose`).
 * Supports zooming, inspecting file metadata, sharing via FileProvider, or jumping to the Hex Editor.
 */
@Composable
fun BuiltInCoilImageViewerDialog(
    entry: FileEntry,
    resolvedFilePath: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onOpenInHex: () -> Unit,
    onShareFile: () -> Unit
) {
    val context = LocalContext.current
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val imageFile = File(resolvedFilePath)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("coil_image_viewer_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = Color(0xFF48CFAD),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = entry.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${DiskVolumeStats.formatSizeShort(entry.sizeBytes)} • Coil Offline Decoder",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            zoomScale = when (zoomScale) {
                                1f -> 1.5f
                                1.5f -> 2f
                                else -> 1f
                            }
                        }
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = AccentBlueColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${zoomScale}x", color = AccentBlueColor, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1D22)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageFile.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageFile)
                                .crossfade(false)
                                .build(),
                            contentDescription = entry.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale
                                )
                        )
                    } else {
                        Text(
                            text = resolvedFilePath,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onOpenInHex()
                            }
                        ) {
                            Text(if (strings.isArabic) "فحص Hex" else "Hex View", color = AccentBlueColor)
                        }
                        TextButton(
                            onClick = {
                                onShareFile()
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = AccentBlueColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.actionShare, color = AccentBlueColor)
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text(strings.confirmProceed, color = AccentBlueColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
