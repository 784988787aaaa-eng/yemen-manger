package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.i18n.AppStrings
import com.example.core.model.FileEntry
import com.example.engines.apk.ApkAnalysisReport
import com.example.engines.system.ApkFunctionAction
import com.example.engines.system.DiskVolumeStats
import com.example.engines.system.OptimizeApkOptions
import com.example.ui.viewmodel.YemenManagerViewModel
import java.io.File

/**
 * 2-Column File Action Grid Dialog matching Screenshot_20261027-163612_8a6573a1.jpg & 163619_71038912.jpg
 */
@Composable
fun FileContextActionGridDialog(
    entry: FileEntry,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onCopyToOtherPane: () -> Unit,
    onMoveToOtherPane: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onTools: () -> Unit,
    onCompress: () -> Unit,
    onProperties: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onAddBookmark: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("file_context_action_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top gray info banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = strings.fileMenuHintBanner,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val rows = listOf(
                    Triple(Icons.AutoMirrored.Filled.ArrowBack, strings.actionCopyBullet, onCopyToOtherPane) to
                        Triple(Icons.AutoMirrored.Filled.DriveFileMove, strings.actionMoveBullet, onMoveToOtherPane),
                    Triple(Icons.Default.Delete, strings.actionDelete, onDelete) to
                        Triple(Icons.Default.Edit, strings.actionRename, onRename),
                    Triple(Icons.Default.Build, strings.actionTools, onTools) to
                        Triple(Icons.Default.Archive, strings.actionCompress, onCompress),
                    Triple(Icons.Default.Info, strings.actionProperties, onProperties) to
                        Triple(Icons.Default.Share, strings.actionShare, onShare),
                    Triple(Icons.Default.OpenInNew, strings.actionOpenWith, onOpenWith) to
                        Triple(Icons.Default.BookmarkBorder, strings.actionAddBookmark, onAddBookmark)
                )

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    rows.forEach { (leftItem, rightItem) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContextGridActionCell(
                                icon = leftItem.first,
                                label = leftItem.second,
                                onClick = {
                                    onDismiss()
                                    leftItem.third()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ContextGridActionCell(
                                icon = rightItem.first,
                                label = rightItem.second,
                                onClick = {
                                    onDismiss()
                                    rightItem.third()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextGridActionCell(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class OpenWithToolItem(
    val id: String,
    val labelAr: String,
    val labelEn: String,
    val badgeColor: Color,
    val badgeText: String? = null,
    val icon: ImageVector = Icons.Default.Description
)

/**
 * 3-Column Colorful "Open with..." Dialog matching Screenshot_20261027-163643_acdc744f.jpg & 163645_627892d8.jpg
 */
@Composable
fun OpenWithColorfulGridDialog(
    entry: FileEntry,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSelectTool: (String) -> Unit,
    onOpenExternalApp: () -> Unit
) {
    val tools = listOf(
        OpenWithToolItem("text_editor", "محرر النصوص", "Text editor", Color(0xFF4A89DC), icon = Icons.Default.Description),
        OpenWithToolItem("archive_viewer", "عارض الأرشيف", "Archive viewer", Color(0xFF8D6E63), icon = Icons.Default.Archive),
        OpenWithToolItem("image_viewer", "عارض الصور", "Image viewer", Color(0xFF48CFAD), icon = Icons.Default.Image),

        OpenWithToolItem("hex_editor", "محرر Hex", "Hex editor", Color(0xFF607D8B), badgeText = "01"),
        OpenWithToolItem("music_player", "مشغل الصوت", "Music player", Color(0xFFED5565), icon = Icons.Default.Audiotrack),
        OpenWithToolItem("video_player", "مشغل الفيديو", "Video player", Color(0xFFFC6E51), icon = Icons.Default.PlayCircleFilled),

        OpenWithToolItem("xml_functions", "وظائف XML", "Xml functions", Color(0xFF4A89DC), badgeText = "<>"),
        OpenWithToolItem("dex_functions", "وظائف DEX", "Dex functions", Color(0xFF37BC9B), badgeText = "01"),
        OpenWithToolItem("arsc_functions", "وظائف ARSC", "Arsc functions", Color(0xFFF6BB42), icon = Icons.Default.Layers),

        OpenWithToolItem("apk_viewer", "عارض APK", "Apk viewer", Color(0xFF8CC152), icon = Icons.Default.Android),
        OpenWithToolItem("apks_functions", "وظائف APKS", "Apks functions", Color(0xFF8D6E63), icon = Icons.Default.Android),
        OpenWithToolItem("script_executor", "منفذ السكربت", "Script executor", Color(0xFF4A89DC), badgeText = ">_"),

        OpenWithToolItem("font_viewer", "عارض الخطوط", "Font viewer", Color(0xFF5C6BC0), badgeText = "TT"),
        OpenWithToolItem("signature_importer", "مستورد التوقيع", "Signature Importer", Color(0xFF607D8B), icon = Icons.Default.Key),
        OpenWithToolItem("plugin_viewer", "عارض الإضافات", "YM-Plugin viewer", Color(0xFF37BC9B), icon = Icons.Default.Extension),

        OpenWithToolItem("svg_functions", "وظائف SVG", "Svg functions", Color(0xFF48CFAD), icon = Icons.Default.Image),
        OpenWithToolItem("nine_patch", "محرر 9-Patch", "9-Patch editor", Color(0xFF37BC9B), badgeText = ".9"),
        OpenWithToolItem("jar_functions", "وظائف JAR", "Jar functions", Color(0xFF8D6E63), badgeText = "JAR"),

        OpenWithToolItem("class_functions", "وظائف Class", "Class functions", Color(0xFF607D8B), badgeText = "CLS"),
        OpenWithToolItem("comparison_result", "نتيجة المقارنة", "Comparison result", Color(0xFF8CC152), icon = Icons.AutoMirrored.Filled.CompareArrows)
    )

    val rows = tools.chunked(3)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("open_with_grid_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.actionOpenWith,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.height(370.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(rows) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (i in 0 until 3) {
                                val item = rowItems.getOrNull(i)
                                if (item != null) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                onDismiss()
                                                onSelectTool(item.id)
                                            }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(item.badgeColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.badgeText != null) {
                                                Text(
                                                    text = item.badgeText,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = if (strings.isArabic) item.labelAr else item.labelEn,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenExternalApp()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (strings.isArabic) "فتح باستخدام تطبيق مثبت" else "Open with an installed app")
                }
            }
        }
    }
}

/**
 * DEX "Open with" Radio Dialog matching Screenshot_20261027-163636_e2502d06.jpg
 */
@Composable
fun DexOpenWithDialog(
    dexEntryName: String,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirmOption: (String) -> Unit
) {
    val options = listOf(
        "dex_editor_plus" to (if (strings.isArabic) "محرر Dex المتقدم (Dex Editor Plus)" else "Dex Editor plus"),
        "dex_editor" to (if (strings.isArabic) "محرر Dex" else "Dex Editor"),
        "repair_dex" to (if (strings.isArabic) "إصلاح ترويسة وبصمة ملف Dex" else "Repair dex file"),
        "dex_property" to (if (strings.isArabic) "خصائص وإحصائيات Dex" else "Dex property"),
        "dex2jar" to (if (strings.isArabic) "تحويل Dex إلى Jar (Dex2Jar)" else "Dex2Jar"),
        "dex2smali" to (if (strings.isArabic) "تفكيك Dex إلى Smali (Dex2Smali)" else "Dex2Smali"),
        "decrypt_dex_strings" to (if (strings.isArabic) "فك واستخراج سلاسل Dex النصية" else "Decrypt dex strings"),
        "translation_mode" to (if (strings.isArabic) "وضع الترجمة النصية" else "Translation mode")
    )
    var selectedId by remember { mutableStateOf(options.first().first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (strings.isArabic) "فتح ملف DEX بواسطة ($dexEntryName)" else "Open with",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == id,
                            onClick = { selectedId = id },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentBlueColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirmOption(selectedId)
                }
            ) {
                Text(strings.confirmProceed, color = AccentBlueColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.confirmCancel, color = AccentBlueColor)
            }
        }
    )
}

/**
 * APK Summary Info Dialog matching Screenshot_20261027-163714_48b195c0.jpg
 * Includes App Icon, App Name, Version, metadata rows, and FUNCTION | VIEW | INSTALL buttons.
 */
@Composable
fun ApkSummaryInfoDialog(
    report: ApkAnalysisReport,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onClickFunctionButton: () -> Unit,
    onClickViewInsidePane: () -> Unit,
    onClickOpenFullApkLab: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val apkFile = File(report.apkPath)
    val appTitle = apkFile.nameWithoutExtension
        .replace("_", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val sigScheme = buildString {
        if (report.signatureReport.hasV1JarSignature) append("V1")
        if (report.signatureReport.hasV2OrV3SigningBlock) {
            if (isNotEmpty()) append("/")
            append("V2")
        }
        if (isEmpty()) append("Unsigned")
    }

    val rows = listOf(
        (if (strings.isArabic) "اسم الحزمة:" else "Package name:") to report.manifestInfo.packageName,
        (if (strings.isArabic) "رقم الإصدار:" else "Version code:") to report.manifestInfo.versionCode.toString(),
        (if (strings.isArabic) "حجم الملف:" else "File size:") to "${DiskVolumeStats.formatSizeShort(report.fileSize)} (${String.format("%,d", report.fileSize)} B)",
        (if (strings.isArabic) "التوقيع:" else "Signature:") to sigScheme,
        (if (strings.isArabic) "الحماية:" else "Protection:") to (if (strings.isArabic) "غير مكتشف (No Detected)" else "No Detected"),
        (if (strings.isArabic) "Target SDK:" else "Target SDK:") to "Android ${ (report.manifestInfo.targetSdk - 20).coerceAtLeast(7) } (API ${report.manifestInfo.targetSdk})",
        (if (strings.isArabic) "Minimum SDK:" else "Minimum SDK:") to "Android ${ (report.manifestInfo.minSdk - 17).coerceAtLeast(7) } (API ${report.manifestInfo.minSdk})",
        (if (strings.isArabic) "الحالة:" else "Installed:") to "${report.manifestInfo.versionName} (${report.manifestInfo.versionCode})",
        (if (strings.isArabic) "مجلد البيانات 1:" else "Data directory 1:") to "/data/user/0/${report.manifestInfo.packageName}",
        (if (strings.isArabic) "مجلد البيانات 2:" else "Data directory 2:") to "/sdcard/Android/data/${report.manifestInfo.packageName}",
        (if (strings.isArabic) "مسار APK:" else "APK path:") to report.apkPath,
        (if (strings.isArabic) "بصمة SHA-256:" else "SHA-256:") to report.sha256Hex.take(24) + "...",
        (if (strings.isArabic) "آخر تحديث:" else "Last update:") to DiskVolumeStats.formatTimestampShort(apkFile.lastModified()),
        "UID:" to "${android.os.Process.myUid()}"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("apk_summary_info_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header: App Icon + Title + Version
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF4CAF50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Android,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = appTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = report.manifestInfo.versionName,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = {
                        onDismiss()
                        onClickOpenFullApkLab()
                    }) {
                        Text(if (strings.isArabic) "المختبر" else "LAB", color = AccentBlueColor, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (strings.isArabic) "اضغط على أي سطر لنسخ القيمة" else "Tap any item to copy its value",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rows) { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(AnnotatedString(value))
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(118.dp)
                            )
                            Text(
                                text = value,
                                fontSize = 13.sp,
                                color = if (label.contains("Data directory") || label.contains("مجلد البيانات")) AccentBlueColor else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(6.dp))

                // Bottom Buttons: FUNCTION | VIEW | INSTALL matching Screenshot_20261027-163714_48b195c0.jpg
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onClickFunctionButton()
                        },
                        modifier = Modifier.testTag("apk_dialog_function_button")
                    ) {
                        Text(
                            text = if (strings.isArabic) "الوظائف (FUNCTION)" else "FUNCTION",
                            color = AccentBlueColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onClickViewInsidePane()
                            },
                            modifier = Modifier.testTag("apk_dialog_view_button")
                        ) {
                            Text(
                                text = if (strings.isArabic) "استعراض (VIEW)" else "VIEW",
                                color = AccentBlueColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = {
                                onDismiss()
                                onClickOpenFullApkLab()
                            },
                            modifier = Modifier.testTag("apk_dialog_install_button")
                        ) {
                            Text(
                                text = if (strings.isArabic) "توقيع/تثبيت" else "INSTALL",
                                color = AccentBlueColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * APK Functions List Dialog matching Screenshot_20261027-163722_944be1af.jpg & 163724_0b095443.jpg
 */
@Composable
fun ApkFunctionsMenuDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSelectFunction: (ApkFunctionAction) -> Unit
) {
    val actions = ApkFunctionAction.entries

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("apk_functions_menu_dialog")
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = if (strings.isArabic) "وظائف ومعالجة حزمة APK" else "APK Functions",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(actions, key = { it.name }) { action ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onSelectFunction(action)
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = if (strings.isArabic) "${action.titleAr} (${action.titleEn})" else action.titleEn,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (strings.isArabic) action.descAr else action.descEn,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Optimize APK Dialog matching Screenshot_20261027-163734_0144b70b.jpg
 */
@Composable
fun OptimizeApkDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirmOptimize: (OptimizeApkOptions) -> Unit
) {
    var performancePriority by remember { mutableStateOf(true) }
    var fileSizePriority by remember { mutableStateOf(false) }
    var ultraRecompression by remember { mutableStateOf(false) }
    var autoSign by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("optimize_apk_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (strings.isArabic) "تحسين حزمة APK (Optimize APK)" else "Optimize APK",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (strings.isArabic) "أولوية الأداء (Performance priority)" else "Performance priority",
                        fontSize = 15.sp
                    )
                    Switch(
                        checked = performancePriority,
                        onCheckedChange = {
                            performancePriority = it
                            if (it) fileSizePriority = false
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentBlueColor)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (strings.isArabic) "أولوية حجم الملف (File size priority)" else "File size priority",
                        fontSize = 15.sp
                    )
                    Switch(
                        checked = fileSizePriority,
                        onCheckedChange = {
                            fileSizePriority = it
                            if (it) performancePriority = false
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentBlueColor)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (strings.isArabic) "تفعيل إعادة الضغط الفائق (Ultra recompression)" else "Enable ultra recompression",
                        fontSize = 15.sp
                    )
                    Switch(
                        checked = ultraRecompression,
                        onCheckedChange = { ultraRecompression = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentBlueColor)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (strings.isArabic) {
                        "شرح الخيارات:\n" +
                            "• أولوية الأداء: إلغاء ضغط resources.arsc وملفات الصور والمكتبات مع محاذاة 4K لتسريع الإقلاع وتقليل استهلاك الذاكرة.\n" +
                            "• أولوية الحجم: ضغط جميع الموارد بأعلى نسبة DEFLATE لتقليل حجم ملف APK.\n" +
                            "• إعادة الضغط الفائق: فك ضغط بيانات DEFLATE وإعادة ضغطها بالمستوى 9."
                    } else {
                        "Explanation of the above options:\n" +
                            "Performance priority: Uncompress resources.arsc, images, and libraries with 4K alignment to improve startup speed and reduce memory footprint.\n" +
                            "File size priority: Compress all resources to minimize APK size.\n" +
                            "Ultra recompression: Re-compress all DEFLATE data at level 9."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { autoSign = !autoSign }
                    ) {
                        Checkbox(
                            checked = autoSign,
                            onCheckedChange = { autoSign = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentBlueColor)
                        )
                        Text(
                            text = if (strings.isArabic) "توقيع تلقائي (AUTO SIGN)" else "AUTO SIGN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(strings.confirmCancel, color = AccentBlueColor)
                        }
                        TextButton(
                            onClick = {
                                onDismiss()
                                onConfirmOptimize(
                                    OptimizeApkOptions(
                                        performancePriority = performancePriority,
                                        fileSizePriority = fileSizePriority,
                                        ultraRecompression = ultraRecompression,
                                        autoSign = autoSign
                                    )
                                )
                            }
                        ) {
                            Text(strings.confirmProceed, color = AccentBlueColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
