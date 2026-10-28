package com.example.mtcore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mtcore.R
import com.example.mtcore.data.ApkToolkit
import com.example.mtcore.data.DexRedivisionReport
import com.example.mtcore.data.KeystoreCertInfo
import com.example.mtcore.data.SigningKeyItem
import kotlinx.coroutines.launch
import java.io.File

import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shuffle
import com.example.mtcore.data.ApkProtectionReport
import com.example.mtcore.data.AppRepository
import com.example.mtcore.data.DecryptedDexStringsReport
import com.example.mtcore.data.DexConfusionReport
import com.example.mtcore.data.EditableApkInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FuncBlue = Color(0xFF1976D2)
private val FuncDark = Color(0xFF111111)
private val FuncGray = Color(0xFF757575)
private val FuncToolbarBg = Color(0xFF2B2B2B)

enum class ApkFunctionAction {
    EDIT_APK_INFO,
    SIGN_APK,
    CLONE_APK,
    MERGE_DEX_FILES,
    DEX_REDIVISION,
    REMOVE_DEX_DEBUG_INFO,
    DEX_FIX,
    DEX_ANTI_CONFUSION,
    DECRYPT_DEX_STRINGS,
    OPTIMIZE_APK,
    RESOURCES_CONFUSION,
    RESOURCES_ANTI_CONFUSION,
    RESOURCES_MINIFICATION,
    APK_PSEUDO_ENCRYPTION,
    APK_ANTI_PSEUDO_ENCRYPTION,
    XML_BATCH_REPLACEMENT,
    XML_TRANSLATION_MODE,
    APK_PROTECTION_INSPECTOR,
    MODIFY_APK_TIMESTAMPS,
    APK_PATCH_MANIFEST,
    PROPERTIES
}

private data class ApkFunctionCategoryGroup(
    val title: String,
    val accentColor: Color,
    val badgeBgColor: Color,
    val items: List<Triple<ApkFunctionAction, String, Pair<ImageVector, Color>>>
)

@Composable
fun ApkFunctionMenuDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onSelectAction: (ApkFunctionAction) -> Unit
) {
    val categories = listOf(
        // 1. Blue Category: Signing, Cloning & APK Info
        ApkFunctionCategoryGroup(
            title = stringResource(R.string.func_cat_signing_info),
            accentColor = Color(0xFF1565C0),
            badgeBgColor = Color(0xFFE3F2FD),
            items = listOf(
                Triple(ApkFunctionAction.EDIT_APK_INFO, stringResource(R.string.edit_apk_info), Icons.Default.Edit to Color(0xFF1976D2)),
                Triple(ApkFunctionAction.SIGN_APK, stringResource(R.string.sign_apk), Icons.Default.Key to Color(0xFF1565C0)),
                Triple(ApkFunctionAction.CLONE_APK, stringResource(R.string.clone_apk), Icons.Default.ContentCopy to Color(0xFF0288D1))
            )
        ),
        // 2. Green Category: DEX Engineering & Anti-Confusion
        ApkFunctionCategoryGroup(
            title = stringResource(R.string.func_cat_dex_tools),
            accentColor = Color(0xFF2E7D32),
            badgeBgColor = Color(0xFFE8F5E9),
            items = listOf(
                Triple(ApkFunctionAction.MERGE_DEX_FILES, stringResource(R.string.merge_dex_files), Icons.Default.MergeType to Color(0xFF2E7D32)),
                Triple(ApkFunctionAction.DEX_REDIVISION, stringResource(R.string.dex_redivision), Icons.Default.Memory to Color(0xFF43A047)),
                Triple(ApkFunctionAction.REMOVE_DEX_DEBUG_INFO, stringResource(R.string.remove_dex_debug_info), Icons.Default.CleaningServices to Color(0xFF388E3C)),
                Triple(ApkFunctionAction.DEX_FIX, stringResource(R.string.dex_fix), Icons.Default.Build to Color(0xFF1B5E20)),
                Triple(ApkFunctionAction.DEX_ANTI_CONFUSION, stringResource(R.string.dex_anti_confusion), Icons.Default.Code to Color(0xFF00796B)),
                Triple(ApkFunctionAction.DECRYPT_DEX_STRINGS, stringResource(R.string.decrypt_dex_strings), Icons.Default.LockOpen to Color(0xFF00897B))
            )
        ),
        // 3. Orange / Red Category: Resources, Optimization & Pseudo-Encryption
        ApkFunctionCategoryGroup(
            title = stringResource(R.string.func_cat_res_opt_enc),
            accentColor = Color(0xFFE65100),
            badgeBgColor = Color(0xFFFFF3E0),
            items = listOf(
                Triple(ApkFunctionAction.OPTIMIZE_APK, stringResource(R.string.optimize_apk), Icons.Default.Speed to Color(0xFFEF6C00)),
                Triple(ApkFunctionAction.RESOURCES_CONFUSION, stringResource(R.string.resources_confusion), Icons.Default.Shuffle to Color(0xFFFB8C00)),
                Triple(ApkFunctionAction.RESOURCES_ANTI_CONFUSION, stringResource(R.string.resources_anti_confusion), Icons.Default.Widgets to Color(0xFFF57C00)),
                Triple(ApkFunctionAction.RESOURCES_MINIFICATION, stringResource(R.string.resources_minification), Icons.Default.Widgets to Color(0xFFE65100)),
                Triple(ApkFunctionAction.APK_PSEUDO_ENCRYPTION, stringResource(R.string.apk_pseudo_encryption), Icons.Default.EnhancedEncryption to Color(0xFFD32F2F)),
                Triple(ApkFunctionAction.APK_ANTI_PSEUDO_ENCRYPTION, stringResource(R.string.apk_anti_pseudo_encryption), Icons.Default.NoEncryption to Color(0xFFC62828)),
                Triple(ApkFunctionAction.XML_BATCH_REPLACEMENT, stringResource(R.string.xml_batch_replacement), Icons.Default.FindReplace to Color(0xFFD84315)),
                Triple(ApkFunctionAction.XML_TRANSLATION_MODE, stringResource(R.string.apk_xml_translation_mode), Icons.Default.Language to Color(0xFFBF360C))
            )
        ),
        // 4. Dark Gray Category: Security Inspector, Timestamps, Manifest Patch & Properties
        ApkFunctionCategoryGroup(
            title = stringResource(R.string.func_cat_inspect_time),
            accentColor = Color(0xFF37474F),
            badgeBgColor = Color(0xFFECEFF1),
            items = listOf(
                Triple(ApkFunctionAction.APK_PROTECTION_INSPECTOR, stringResource(R.string.apk_protection_inspector), Icons.Default.Security to Color(0xFF263238)),
                Triple(ApkFunctionAction.MODIFY_APK_TIMESTAMPS, stringResource(R.string.modify_apk_timestamps), Icons.Default.AccessTime to Color(0xFF37474F)),
                Triple(ApkFunctionAction.APK_PATCH_MANIFEST, stringResource(R.string.apk_patch_manifest_editor), Icons.Default.BugReport to Color(0xFF455A64)),
                Triple(ApkFunctionAction.PROPERTIES, stringResource(R.string.properties_title), Icons.Default.Info to Color(0xFF546E7A))
            )
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.function_caps)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                categories.forEach { category ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAFAFA), RoundedCornerShape(6.dp))
                            .padding(vertical = 6.dp, horizontal = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(category.badgeBgColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = category.title,
                                color = category.accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        category.items.forEach { (action, label, iconPair) ->
                            val (icon, bg) = iconPair
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onSelectAction(action)
                                    }
                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(bg, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    color = FuncDark,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Matches the video's `Sign APK` dialog (00:08 - 00:20):
 * - Signing Key dropdown (Default TestKey + user keys in MT2/keys & MT2/Keystore)
 * - Scheme dropdown (V1 + V2 + V3, V1 + V2, V1 only, V2 only)
 * - Switch: Customize V1 signature data filename
 * - Switch: Remember signing options for this package name: <pkg>
 * - Bottom buttons: MANAGE on left, CANCEL and OK on right.
 */
@Composable
fun SignApkDialog(
    apkFile: File,
    packageName: String,
    fallbackCacheDir: File,
    onDismiss: () -> Unit,
    onOpenKeyManager: () -> Unit,
    onSignedResult: (File?, String?) -> Unit
) {
    var keys by remember { mutableStateOf(ApkToolkit.listSigningKeys(fallbackCacheDir)) }
    val rememberedKeyName = remember(packageName) { ApkToolkit.getRememberedKeyForPackage(packageName) }
    var selectedKeyIndex by remember(keys) {
        val idx = keys.indexOfFirst { it.name == rememberedKeyName }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }
    var keyMenuExpanded by remember { mutableStateOf(false) }

    val schemes = listOf(
        stringResource(R.string.scheme_v1_v2_v3),
        stringResource(R.string.scheme_v1_v2),
        stringResource(R.string.scheme_v1_only),
        stringResource(R.string.scheme_v2_only)
    )
    var selectedSchemeIndex by remember { mutableIntStateOf(0) }
    var schemeMenuExpanded by remember { mutableStateOf(false) }

    var customizeV1Name by remember { mutableStateOf(false) }
    var customV1Name by remember { mutableStateOf("CERT") }
    var rememberForPkg by remember { mutableStateOf(rememberedKeyName != null) }
    var isSigning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isSigning) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.sign_apk),
                    color = FuncDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                // Signing Key Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.signing_key_label)}:",
                        color = FuncDark,
                        fontSize = 13.5.sp,
                        modifier = Modifier.width(96.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { keyMenuExpanded = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val currentKey = keys.getOrNull(selectedKeyIndex)
                            Text(
                                text = currentKey?.name ?: stringResource(R.string.default_test_key),
                                color = FuncDark,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = FuncDark)
                        }
                        DropdownMenu(
                            expanded = keyMenuExpanded,
                            onDismissRequest = { keyMenuExpanded = false }
                        ) {
                            keys.forEachIndexed { index, item ->
                                DropdownMenuItem(
                                    text = { Text("${item.name} (${item.alias})") },
                                    onClick = {
                                        selectedKeyIndex = index
                                        keyMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Scheme Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.scheme_label)}:",
                        color = FuncDark,
                        fontSize = 13.5.sp,
                        modifier = Modifier.width(96.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { schemeMenuExpanded = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = schemes[selectedSchemeIndex],
                                color = FuncDark,
                                fontSize = 13.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = FuncDark)
                        }
                        DropdownMenu(
                            expanded = schemeMenuExpanded,
                            onDismissRequest = { schemeMenuExpanded = false }
                        ) {
                            schemes.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedSchemeIndex = index
                                        schemeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Switch 1: Customize V1 signature data filename
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { customizeV1Name = !customizeV1Name },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.customize_v1_signature_filename),
                        color = FuncDark,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = customizeV1Name,
                        onCheckedChange = { customizeV1Name = it }
                    )
                }

                AnimatedVisibility(visible = customizeV1Name) {
                    FuncUnderlinedInput(
                        value = customV1Name,
                        onValueChange = { customV1Name = it },
                        hint = stringResource(R.string.v1_signature_filename_hint)
                    )
                }

                // Switch 2: Remember signing options for this package name
                val displayPkg = packageName.ifBlank { apkFile.nameWithoutExtension }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberForPkg = !rememberForPkg },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.remember_signing_options_for_pkg, displayPkg),
                        color = FuncDark,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = rememberForPkg,
                        onCheckedChange = { rememberForPkg = it }
                    )
                }

                if (isSigning) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                // Bottom Action Row: MANAGE on left, CANCEL & OK on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = !isSigning,
                        onClick = {
                            onDismiss()
                            onOpenKeyManager()
                        }
                    ) {
                        Text(stringResource(R.string.manage_caps), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                    Row {
                        TextButton(
                            enabled = !isSigning,
                            onClick = onDismiss
                        ) {
                            Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            enabled = !isSigning,
                            onClick = {
                                val chosenKey = keys.getOrNull(selectedKeyIndex)
                                if (rememberForPkg) {
                                    ApkToolkit.setRememberedKeyForPackage(displayPkg, chosenKey?.name)
                                } else {
                                    ApkToolkit.setRememberedKeyForPackage(displayPkg, null)
                                }
                                isSigning = true
                                scope.launch {
                                    runCatching {
                                        ApkToolkit.signApk(
                                            apkFile = apkFile,
                                            signingKey = chosenKey,
                                            scheme = schemes[selectedSchemeIndex],
                                            customSignatureBaseName = if (customizeV1Name) customV1Name else "CERT"
                                        )
                                    }.onSuccess { signedFile ->
                                        isSigning = false
                                        onDismiss()
                                        onSignedResult(signedFile, null)
                                    }.onFailure { err ->
                                        isSigning = false
                                        onSignedResult(null, err.message ?: "Failed to sign APK.")
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen `Signing Key` Manager (`SigningKeyScreen`) matching the video (00:11 - 00:15):
 * - Top bar (#2B2B2B) with Back arrow and "Signing Key" title.
 * - Section 1: `Imported (MT2/keys)` with folder icon.
 * - Section 2: `Importable (MT2/Keystore)` with `+` button and folder icon.
 * - Clicking `+` opens `GenerateKeyDialog` (no VIP restriction!) to generate an RSA 2048-bit key.
 */
@Composable
fun SigningKeyScreen(
    fallbackCacheDir: File,
    onBack: () -> Unit,
    onNavigateToDirectory: (File) -> Unit
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val allKeys = remember(refreshTick) { ApkToolkit.listSigningKeys(fallbackCacheDir) }
    val importedKeys = remember(allKeys) { allKeys.filter { it.isImported } }
    val keystoreKeys = remember(allKeys) { allKeys.filter { !it.isImported } }

    var showGenerateDialog by remember { mutableStateOf(false) }
    var inspectFileTarget by remember { mutableStateOf<File?>(null) }

    BackHandler { onBack() }

    if (showGenerateDialog) {
        GenerateKeyDialog(
            fallbackCacheDir = fallbackCacheDir,
            onDismiss = { showGenerateDialog = false },
            onKeyGenerated = { savedKeystoreFile ->
                showGenerateDialog = false
                refreshTick++
                Toast.makeText(
                    context,
                    context.getString(R.string.key_generated_success, savedKeystoreFile.absolutePath),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    inspectFileTarget?.let { keyFile ->
        KeystoreCertificateInspectorDialog(
            file = keyFile,
            onDismiss = { inspectFileTarget = null }
        )
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FuncToolbarBg)
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Text(
                text = stringResource(R.string.signing_key_label),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            IconButton(onClick = { showGenerateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.generate_key_title), tint = Color.White)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section 1: Imported (MT2/keys)
            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.imported_keys_section),
                                color = FuncBlue,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    val dir = ApkToolkit.KEYS_DIR.apply { mkdirs() }
                                    onBack()
                                    onNavigateToDirectory(dir)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = FuncGray, modifier = Modifier.size(20.dp))
                            }
                        }
                        HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE5E5E5))
                        importedKeys.forEach { item ->
                            SigningKeyRow(
                                item = item,
                                onClick = {
                                    if (item.file != null) {
                                        inspectFileTarget = item.file
                                    } else {
                                        Toast.makeText(context, "${item.name}: ${item.commonName}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Section 2: Importable (MT2/Keystore) with + and Folder icon
            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.importable_keystore_section),
                                color = FuncBlue,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showGenerateDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.generate_key_title), tint = FuncDark, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val dir = ApkToolkit.KEYSTORE_DIR.apply { mkdirs() }
                                    onBack()
                                    onNavigateToDirectory(dir)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = FuncGray, modifier = Modifier.size(20.dp))
                            }
                        }
                        HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE5E5E5))
                        if (keystoreKeys.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_keys_found),
                                color = FuncGray,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            keystoreKeys.forEach { item ->
                                SigningKeyRow(
                                    item = item,
                                    onClick = { item.file?.let { inspectFileTarget = it } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SigningKeyRow(
    item: SigningKeyItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = FuncDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${item.type} • Alias: ${item.alias} • ${item.commonName}", color = FuncGray, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * `Generate key` Dialog matching the video (00:13 - 00:15):
 * - RadioButtons: JKS and BKS
 * - Fields: Password, Alias, Alias Password
 * - Switch: More options (Validity (years) & Common Name)
 * - Generates real RSA 2048-bit keypair in `/storage/emulated/0/MT2/Keystore/<alias>.keystore`.
 */
@Composable
fun GenerateKeyDialog(
    fallbackCacheDir: File,
    onDismiss: () -> Unit,
    onKeyGenerated: (File) -> Unit
) {
    var keyType by remember { mutableStateOf("JKS") }
    var password by remember { mutableStateOf("123456") }
    var alias by remember { mutableStateOf("mt_release_key") }
    var aliasPassword by remember { mutableStateOf("123456") }
    var moreOptions by remember { mutableStateOf(false) }
    var validityYears by remember { mutableStateOf("25") }
    var commonName by remember { mutableStateOf("Android Developer") }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isGenerating) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.generate_key_title),
                    color = FuncDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                // RadioButtons: JKS / BKS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { keyType = "JKS" }
                    ) {
                        RadioButton(selected = keyType == "JKS", onClick = { keyType = "JKS" })
                        Text(stringResource(R.string.key_type_jks), color = FuncDark, fontSize = 14.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { keyType = "BKS" }
                    ) {
                        RadioButton(selected = keyType == "BKS", onClick = { keyType = "BKS" })
                        Text(stringResource(R.string.key_type_bks), color = FuncDark, fontSize = 14.sp)
                    }
                }

                Text(stringResource(R.string.password_label), color = FuncGray, fontSize = 12.sp)
                FuncUnderlinedInput(value = password, onValueChange = { password = it }, hint = stringResource(R.string.password_label))

                Text(stringResource(R.string.alias_label), color = FuncGray, fontSize = 12.sp)
                FuncUnderlinedInput(value = alias, onValueChange = { alias = it }, hint = stringResource(R.string.alias_label))

                Text(stringResource(R.string.alias_password_label), color = FuncGray, fontSize = 12.sp)
                FuncUnderlinedInput(value = aliasPassword, onValueChange = { aliasPassword = it }, hint = stringResource(R.string.alias_password_label))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { moreOptions = !moreOptions },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.more_options_label),
                        color = FuncDark,
                        fontSize = 13.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = moreOptions, onCheckedChange = { moreOptions = it })
                }

                AnimatedVisibility(visible = moreOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.validity_years_label), color = FuncGray, fontSize = 12.sp)
                        FuncUnderlinedInput(value = validityYears, onValueChange = { validityYears = it }, hint = "25")

                        Text(stringResource(R.string.common_name_label), color = FuncGray, fontSize = 12.sp)
                        FuncUnderlinedInput(value = commonName, onValueChange = { commonName = it }, hint = "CN")
                    }
                }

                if (isGenerating) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(enabled = !isGenerating, onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        enabled = !isGenerating,
                        onClick = {
                            isGenerating = true
                            scope.launch {
                                runCatching {
                                    ApkToolkit.generateSigningKey(
                                        keyType = keyType,
                                        password = password,
                                        alias = alias,
                                        aliasPassword = aliasPassword,
                                        validityYears = validityYears.toIntOrNull() ?: 25,
                                        commonName = commonName,
                                        fallbackCacheDir = fallbackCacheDir
                                    )
                                }.onSuccess { file ->
                                    isGenerating = false
                                    onKeyGenerated(file)
                                }.onFailure {
                                    isGenerating = false
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Matches the video's `Optimize APK` dialog (00:31 - 00:34) verbatim:
 * - 3 Switches:
 *   1. Performance priority (disables File size priority when turned on)
 *   2. File size priority (disables Performance priority when turned on)
 *   3. Enable ultra recompression
 * - Explanatory text paragraphs 1, 2, 3 underneath
 * - Bottom row: Checkbox `AUTO SIGN` on the left, `CANCEL` and `OK` on the right.
 */
@Composable
fun OptimizeApkDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onOptimizedResult: (File?, String?) -> Unit
) {
    var performancePriority by remember { mutableStateOf(true) }
    var fileSizePriority by remember { mutableStateOf(false) }
    var ultraRecompression by remember { mutableStateOf(false) }
    var autoSign by remember { mutableStateOf(true) }
    var isOptimizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isOptimizing) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.optimize_apk),
                    color = FuncDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                // Switch 1: Performance priority
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !performancePriority
                            performancePriority = next
                            if (next) fileSizePriority = false
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.performance_priority),
                        color = FuncDark,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = performancePriority,
                        onCheckedChange = { checked ->
                            performancePriority = checked
                            if (checked) fileSizePriority = false
                        }
                    )
                }

                // Switch 2: File size priority
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !fileSizePriority
                            fileSizePriority = next
                            if (next) performancePriority = false
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.file_size_priority),
                        color = FuncDark,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = fileSizePriority,
                        onCheckedChange = { checked ->
                            fileSizePriority = checked
                            if (checked) performancePriority = false
                        }
                    )
                }

                // Switch 3: Enable ultra recompression
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ultraRecompression = !ultraRecompression },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.enable_ultra_recompression),
                        color = FuncDark,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = ultraRecompression,
                        onCheckedChange = { ultraRecompression = it }
                    )
                }

                HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE0E0E0))

                // 3 Explanatory Paragraphs
                Text(
                    text = stringResource(R.string.optimize_desc_performance),
                    color = FuncGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Text(
                    text = stringResource(R.string.optimize_desc_filesize),
                    color = FuncGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Text(
                    text = stringResource(R.string.optimize_desc_ultra),
                    color = FuncGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (isOptimizing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                // Bottom Row: Checkbox AUTO SIGN on left, CANCEL & OK on right
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
                            onCheckedChange = { autoSign = it }
                        )
                        Text(
                            text = stringResource(R.string.auto_sign_caps),
                            color = FuncDark,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row {
                        TextButton(enabled = !isOptimizing, onClick = onDismiss) {
                            Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            enabled = !isOptimizing,
                            onClick = {
                                isOptimizing = true
                                scope.launch {
                                    runCatching {
                                        ApkToolkit.optimizeApk(
                                            apkFile = apkFile,
                                            performancePriority = performancePriority,
                                            fileSizePriority = fileSizePriority,
                                            ultraRecompression = ultraRecompression,
                                            autoSign = autoSign
                                        )
                                    }.onSuccess { optimizedFile ->
                                        isOptimizing = false
                                        onDismiss()
                                        onOptimizedResult(optimizedFile, null)
                                    }.onFailure { err ->
                                        isOptimizing = false
                                        onOptimizedResult(null, err.message ?: "Failed to optimize APK.")
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DexRedivisionDialog(
    apkFile: File,
    onDismiss: () -> Unit
) {
    var report by remember { mutableStateOf<DexRedivisionReport?>(null) }

    LaunchedEffect(apkFile.absolutePath) {
        report = runCatching { ApkToolkit.inspectDexRedivision(apkFile) }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.dex_redivision)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val r = report
                if (r == null) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FuncBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    Text(r.statusMessage, color = FuncGray, fontSize = 12.5.sp)
                    LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(r.dexSummaries, key = { it.entryName }) { dex ->
                            val ratio = (dex.methodCount.toFloat() / 65536f).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F7F7), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${dex.entryName} (v${dex.version})", color = FuncDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${dex.methodCount} / 65536 methods", color = FuncBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    color = if (ratio > 0.9f) Color(0xFFE53935) else FuncBlue,
                                    trackColor = Color(0xFFE0E0E0),
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Classes: ${dex.classCount} • Fields: ${dex.fieldCount} • Strings: ${dex.stringCount}",
                                    color = FuncGray,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun XmlBatchReplacementDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onCompleted: (File?, String?) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isRunning) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.xml_batch_replacement),
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.search_text_label), color = FuncGray, fontSize = 12.sp)
                FuncUnderlinedInput(value = searchText, onValueChange = { searchText = it }, hint = stringResource(R.string.search_text_label))

                Text(stringResource(R.string.replace_with_label), color = FuncGray, fontSize = 12.sp)
                FuncUnderlinedInput(value = replaceWith, onValueChange = { replaceWith = it }, hint = stringResource(R.string.replace_with_label))

                if (isRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !isRunning, onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        enabled = !isRunning && searchText.isNotEmpty(),
                        onClick = {
                            isRunning = true
                            scope.launch {
                                runCatching {
                                    ApkToolkit.batchReplaceXmlInApk(apkFile, searchText, replaceWith)
                                }.onSuccess { res ->
                                    isRunning = false
                                    onDismiss()
                                    onCompleted(
                                        res.outputFile,
                                        "Modified ${res.modifiedXmlCount}/${res.scannedXmlCount} XML files (${res.totalReplacements} replacements) -> ${res.outputFile.name}"
                                    )
                                }.onFailure { err ->
                                    isRunning = false
                                    onCompleted(null, err.message ?: "Batch replacement failed.")
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun KeystoreCertificateInspectorDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<KeystoreCertInfo?>(null) }

    LaunchedEffect(file.absolutePath) {
        info = ApkToolkit.inspectKeystoreOrCertificate(file)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.keystore_inspector_title)} - ${file.name}",
                    color = FuncDark,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
                val cert = info
                if (cert == null) {
                    Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FuncBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    val rows = listOf(
                        stringResource(R.string.cert_algorithm_label) to cert.algorithm,
                        stringResource(R.string.cert_subject_label) to cert.subject,
                        stringResource(R.string.cert_validity_label) to cert.validity,
                        stringResource(R.string.cert_sha1_label) to cert.sha1Fingerprint,
                        stringResource(R.string.cert_sha256_label) to cert.sha256Fingerprint,
                        stringResource(R.string.file_size_label) to cert.fileSize
                    )
                    rows.forEach { (label, value) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText(label, value))
                                    Toast.makeText(context, context.getString(R.string.copied_to_clipboard, value), Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(label, color = FuncGray, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Text(value, color = FuncDark, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FuncUnderlinedInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = FuncDark, fontSize = 14.sp),
        cursorBrush = SolidColor(FuncBlue),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        decorationBox = { inner ->
            Column {
                Box {
                    if (value.isEmpty()) {
                        Text(hint, color = Color(0xFF9E9E9E), fontSize = 13.5.sp)
                    }
                    inner()
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(thickness = 1.5.dp, color = FuncBlue)
            }
        }
    )
}

// ============================================================================
// v13: Split APK Bundle Dialog (AntiSplit) & Advanced APK FUNCTION Dialogs
// ============================================================================

@Composable
fun SplitApkBundleDialog(
    bundleFile: File,
    onDismiss: () -> Unit,
    onMergeResult: (File?, String?) -> Unit,
    onViewArchive: () -> Unit,
    onInstallSplit: () -> Unit
) {
    var autoSign by remember { mutableStateOf(true) }
    var isMerging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isMerging) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2E7D32), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MergeType, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.split_apk_bundle_title, bundleFile.extension.uppercase()),
                            color = FuncDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${bundleFile.name} • ${AppRepository.formatBytes(bundleFile.length())}",
                            color = FuncGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE0E0E0))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoSign = !autoSign },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = autoSign, onCheckedChange = { autoSign = it })
                    Text(
                        text = stringResource(R.string.auto_sign_caps),
                        color = FuncDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isMerging) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.antisplit_merging_message), color = FuncDark, fontSize = 13.sp)
                    }
                }

                // 3 Specialized Action Buttons: MERGE TO APK (ANTISPLIT) | VIEW ARCHIVE | INSTALL SPLIT
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = !isMerging,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            isMerging = true
                            scope.launch {
                                runCatching {
                                    ApkToolkit.mergeSplitApksToSingleApk(bundleFile, autoSign = autoSign)
                                }.onSuccess { mergedApk ->
                                    isMerging = false
                                    onDismiss()
                                    onMergeResult(mergedApk, null)
                                }.onFailure { err ->
                                    isMerging = false
                                    onMergeResult(null, err.message ?: "AntiSplit merge failed.")
                                }
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.merge_to_apk_antisplit),
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            enabled = !isMerging,
                            onClick = {
                                onDismiss()
                                onViewArchive()
                            }
                        ) {
                            Text(stringResource(R.string.view_archive_caps), color = FuncBlue, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            enabled = !isMerging,
                            onClick = {
                                onDismiss()
                                onInstallSplit()
                            }
                        ) {
                            Text(stringResource(R.string.install_split_caps), color = FuncBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloneApkDialog(
    apkFile: File,
    initialPackageName: String,
    initialAppName: String,
    onDismiss: () -> Unit,
    onClonedResult: (File?, String?) -> Unit
) {
    val basePkg = initialPackageName.ifBlank { "com.example.${apkFile.nameWithoutExtension.lowercase()}" }
    val baseApp = initialAppName.ifBlank { apkFile.nameWithoutExtension }
    var newPackageName by remember(basePkg) { mutableStateOf("${basePkg}.clone") }
    var newAppName by remember(baseApp) { mutableStateOf("$baseApp 2") }
    var autoSign by remember { mutableStateOf(true) }
    var isCloning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isCloning) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${stringResource(R.string.clone_apk)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(stringResource(R.string.new_package_name_label), color = FuncGray, fontSize = 12.sp)
                FuncUnderlinedInput(
                    value = newPackageName,
                    onValueChange = { newPackageName = it },
                    hint = "com.example.app.clone"
                )

                Text(stringResource(R.string.new_app_name_label), color = FuncGray, fontSize = 12.sp)
                FuncUnderlinedInput(
                    value = newAppName,
                    onValueChange = { newAppName = it },
                    hint = "App Clone"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoSign = !autoSign },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.auto_sign_cloned_apk),
                        color = FuncDark,
                        fontSize = 13.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = autoSign, onCheckedChange = { autoSign = it })
                }

                if (isCloning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !isCloning, onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        enabled = !isCloning && newPackageName.isNotBlank(),
                        onClick = {
                            isCloning = true
                            scope.launch {
                                runCatching {
                                    ApkToolkit.cloneApk(
                                        apkFile = apkFile,
                                        oldPackageName = basePkg,
                                        newPackageName = newPackageName,
                                        oldAppName = baseApp,
                                        newAppName = newAppName,
                                        autoSign = autoSign
                                    )
                                }.onSuccess { clonedFile ->
                                    isCloning = false
                                    onDismiss()
                                    onClonedResult(clonedFile, null)
                                }.onFailure { err ->
                                    isCloning = false
                                    onClonedResult(null, err.message ?: "Clone APK failed.")
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DexAntiConfusionDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onExportedMapping: (File) -> Unit
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<DexConfusionReport?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(apkFile.absolutePath) {
        report = runCatching { ApkToolkit.analyzeDexConfusion(apkFile) }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 410.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.dex_anti_confusion)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val r = report
                if (r == null) {
                    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FuncBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    Text(
                        text = "Obfuscated Identifiers: ${r.obfuscatedCount} / ${r.totalIdentifiers} (${r.obfuscationRatioPercent}%)",
                        color = FuncBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(r.mappings) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F7F7), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "[${item.category}] ${item.obfuscatedName}",
                                    color = Color(0xFFB71C1C),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "-> ${item.suggestedName}",
                                    color = Color(0xFF1B5E20),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = report != null,
                        onClick = {
                            val current = report ?: return@TextButton
                            scope.launch {
                                runCatching { ApkToolkit.exportProguardMapping(apkFile, current) }
                                    .onSuccess { file ->
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.mapping_exported_success, file.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onExportedMapping(file)
                                        onDismiss()
                                    }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.export_proguard_mapping), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DecryptDexStringsDialog(
    apkFile: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<DecryptedDexStringsReport?>(null) }

    LaunchedEffect(apkFile.absolutePath) {
        report = runCatching { ApkToolkit.decryptDexStringsInApk(apkFile) }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 410.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.decrypt_dex_strings)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val r = report
                if (r == null) {
                    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FuncBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    Text(
                        text = "Scanned ${r.totalStringsScanned} DEX strings • Decoded ${r.decryptedItems.size} encoded literal(s)",
                        color = FuncGray,
                        fontSize = 12.5.sp
                    )
                    if (r.decryptedItems.isEmpty()) {
                        Text(
                            text = "No Base64 / Hex / Unicode-escaped strings detected in DEX pools.",
                            color = FuncDark,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(r.decryptedItems) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF7F7F7), RoundedCornerShape(4.dp))
                                        .clickable {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("decrypted_str", item.decodedPlaintext))
                                            Toast.makeText(context, context.getString(R.string.copied_to_clipboard, item.decodedPlaintext), Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "${item.dexName} • ${item.encodingType}",
                                        color = FuncBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = item.encodedOriginal,
                                        color = FuncGray,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "-> ${item.decodedPlaintext}",
                                        color = Color(0xFF1B5E20),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ApkPatchManifestDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onPatchedResult: (File?, String?) -> Unit
) {
    var enableDebuggable by remember { mutableStateOf(true) }
    var enableExtractNativeLibs by remember { mutableStateOf(true) }
    var injectDocumentsProvider by remember { mutableStateOf(false) }
    var stripSignatureCheck by remember { mutableStateOf(true) }
    var autoSign by remember { mutableStateOf(true) }
    var isPatching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isPatching) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.apk_patch_manifest_editor)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                PatchSwitchRow(stringResource(R.string.patch_enable_debuggable), enableDebuggable) { enableDebuggable = it }
                PatchSwitchRow(stringResource(R.string.patch_extract_native_libs), enableExtractNativeLibs) { enableExtractNativeLibs = it }
                PatchSwitchRow(stringResource(R.string.patch_inject_documents_provider), injectDocumentsProvider) { injectDocumentsProvider = it }
                PatchSwitchRow(stringResource(R.string.patch_strip_signature_check), stripSignatureCheck) { stripSignatureCheck = it }

                if (isPatching) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { autoSign = !autoSign }
                    ) {
                        Checkbox(checked = autoSign, onCheckedChange = { autoSign = it })
                        Text(stringResource(R.string.auto_sign_caps), color = FuncDark, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Row {
                        TextButton(enabled = !isPatching, onClick = onDismiss) {
                            Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            enabled = !isPatching,
                            onClick = {
                                isPatching = true
                                scope.launch {
                                    runCatching {
                                        ApkToolkit.patchApkManifestAndMetadata(
                                            apkFile = apkFile,
                                            enableDebuggable = enableDebuggable,
                                            enableExtractNativeLibs = enableExtractNativeLibs,
                                            injectDocumentsProvider = injectDocumentsProvider,
                                            stripSignatureCheck = stripSignatureCheck,
                                            autoSign = autoSign
                                        )
                                    }.onSuccess { patchedFile ->
                                        isPatching = false
                                        onDismiss()
                                        onPatchedResult(patchedFile, null)
                                    }.onFailure { err ->
                                        isPatching = false
                                        onPatchedResult(null, err.message ?: "Manifest patch failed.")
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatchSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = FuncDark, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ============================================================================
// v14: Edit APK Info, Merge DEX Files, Remove Debug Info, Modify Timestamps,
//      and APK Protection & DEX2C Inspector Dialogs
// ============================================================================

@Composable
fun EditApkInfoDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onEditedResult: (File?, String?) -> Unit
) {
    val context = LocalContext.current
    var originalInfo by remember { mutableStateOf<EditableApkInfo?>(null) }
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("1") }
    var versionName by remember { mutableStateOf("1.0") }
    var minSdk by remember { mutableStateOf("23") }
    var targetSdk by remember { mutableStateOf("34") }
    var autoSign by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(apkFile.absolutePath) {
        isLoading = true
        val extracted = ApkToolkit.extractEditableApkInfo(context, apkFile)
        originalInfo = extracted
        appName = extracted.appName
        packageName = extracted.packageName
        versionCode = extracted.versionCode.toString()
        versionName = extracted.versionName
        minSdk = extracted.minSdk.toString()
        targetSdk = extracted.targetSdk.toString()
        isLoading = false
    }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.edit_apk_info)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FuncBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    Text(stringResource(R.string.app_name_field_label), color = FuncGray, fontSize = 12.sp)
                    FuncUnderlinedInput(value = appName, onValueChange = { appName = it }, hint = "App Name")

                    Text(stringResource(R.string.package_name_field_label), color = FuncGray, fontSize = 12.sp)
                    FuncUnderlinedInput(value = packageName, onValueChange = { packageName = it }, hint = "com.example.app")

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.version_code_field_label), color = FuncGray, fontSize = 12.sp)
                            FuncUnderlinedInput(value = versionCode, onValueChange = { versionCode = it }, hint = "1")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.version_name_field_label), color = FuncGray, fontSize = 12.sp)
                            FuncUnderlinedInput(value = versionName, onValueChange = { versionName = it }, hint = "1.0")
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.min_sdk_field_label), color = FuncGray, fontSize = 12.sp)
                            FuncUnderlinedInput(value = minSdk, onValueChange = { minSdk = it }, hint = "23")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.target_sdk_field_label), color = FuncGray, fontSize = 12.sp)
                            FuncUnderlinedInput(value = targetSdk, onValueChange = { targetSdk = it }, hint = "34")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoSign = !autoSign },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.auto_sign_after_saving),
                            color = FuncDark,
                            fontSize = 13.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = autoSign, onCheckedChange = { autoSign = it })
                    }
                }

                if (isSaving) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !isSaving, onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        enabled = !isLoading && !isSaving && packageName.isNotBlank(),
                        onClick = {
                            val orig = originalInfo ?: return@TextButton
                            val updated = EditableApkInfo(
                                appName = appName.ifBlank { orig.appName },
                                packageName = packageName.trim(),
                                versionCode = versionCode.toIntOrNull() ?: orig.versionCode,
                                versionName = versionName.ifBlank { orig.versionName },
                                minSdk = minSdk.toIntOrNull() ?: orig.minSdk,
                                targetSdk = targetSdk.toIntOrNull() ?: orig.targetSdk
                            )
                            isSaving = true
                            scope.launch {
                                runCatching {
                                    ApkToolkit.editApkInfo(
                                        apkFile = apkFile,
                                        originalInfo = orig,
                                        newInfo = updated,
                                        autoSign = autoSign
                                    )
                                }.onSuccess { editedFile ->
                                    isSaving = false
                                    onDismiss()
                                    onEditedResult(editedFile, null)
                                }.onFailure { err ->
                                    isSaving = false
                                    onEditedResult(null, err.message ?: "Failed to edit APK info.")
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MergeDexFilesDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onMergedResult: (File?, String?) -> Unit
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<DexRedivisionReport?>(null) }
    var autoSign by remember { mutableStateOf(true) }
    var isMerging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(apkFile.absolutePath) {
        report = runCatching { ApkToolkit.inspectDexRedivision(apkFile) }.getOrNull()
    }

    Dialog(onDismissRequest = { if (!isMerging) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.merge_dex_files)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val r = report
                if (r == null) {
                    Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FuncBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    Text(r.statusMessage, color = FuncGray, fontSize = 12.5.sp)
                    LazyColumn(Modifier.heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(r.dexSummaries, key = { it.entryName }) { dex ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${dex.entryName} (v${dex.version})", color = FuncDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${dex.classCount} classes • ${dex.methodCount} methods", color = Color(0xFF2E7D32), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { autoSign = !autoSign },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = autoSign, onCheckedChange = { autoSign = it })
                    Text(stringResource(R.string.auto_sign_caps), color = FuncDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (isMerging) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !isMerging, onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        enabled = !isMerging && report != null,
                        onClick = {
                            isMerging = true
                            scope.launch {
                                runCatching {
                                    ApkToolkit.mergeDexFilesInApk(apkFile, autoSign = autoSign)
                                }.onSuccess { res ->
                                    isMerging = false
                                    onDismiss()
                                    onMergedResult(
                                        res.outputFile,
                                        context.getString(
                                            R.string.dex_merged_success,
                                            res.outputFile.name,
                                            res.originalDexCount,
                                            res.finalDexCount,
                                            res.totalMethods
                                        )
                                    )
                                }.onFailure { err ->
                                    isMerging = false
                                    onMergedResult(null, err.message ?: "Failed to merge DEX files.")
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ModifyApkTimestampsDialog(
    apkFile: File,
    onDismiss: () -> Unit,
    onModifiedResult: (File?, String?) -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0 = MT Fixed 1981-01-01 01:01:00, 1 = Current Device Time, 2 = Custom
    var customDateStr by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
    }
    var autoSign by remember { mutableStateOf(true) }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isRunning) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.modify_apk_timestamps)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                val options = listOf(
                    0 to stringResource(R.string.timestamp_mode_mt_fixed),
                    1 to stringResource(R.string.timestamp_mode_current),
                    2 to stringResource(R.string.timestamp_mode_custom)
                )
                options.forEach { (idx, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = idx }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == idx, onClick = { mode = idx })
                        Text(label, color = FuncDark, fontSize = 13.5.sp)
                    }
                }

                AnimatedVisibility(visible = mode == 2) {
                    FuncUnderlinedInput(
                        value = customDateStr,
                        onValueChange = { customDateStr = it },
                        hint = "1981-01-01 01:01:00"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { autoSign = !autoSign },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = autoSign, onCheckedChange = { autoSign = it })
                    Text(stringResource(R.string.auto_sign_caps), color = FuncDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (isRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = FuncBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.loading), color = FuncDark, fontSize = 13.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !isRunning, onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        enabled = !isRunning,
                        onClick = {
                            val targetMillis = when (mode) {
                                0 -> {
                                    Calendar.getInstance().apply {
                                        set(1981, Calendar.JANUARY, 1, 1, 1, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                }
                                1 -> System.currentTimeMillis()
                                else -> {
                                    runCatching {
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(customDateStr.trim())?.time
                                    }.getOrNull() ?: System.currentTimeMillis()
                                }
                            }
                            isRunning = true
                            scope.launch {
                                runCatching {
                                    ApkToolkit.modifyApkTimestamps(apkFile, targetMillis, autoSign = autoSign)
                                }.onSuccess { (outFile, count) ->
                                    isRunning = false
                                    onDismiss()
                                    onModifiedResult(
                                        outFile,
                                        context.getString(R.string.timestamps_modified_success, count, outFile.name)
                                    )
                                }.onFailure { err ->
                                    isRunning = false
                                    onModifiedResult(null, err.message ?: "Failed to modify APK timestamps.")
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ApkProtectionInspectorDialog(
    apkFile: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<ApkProtectionReport?>(null) }

    LaunchedEffect(apkFile.absolutePath) {
        report = runCatching { ApkToolkit.inspectApkProtection(apkFile) }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 410.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.apk_protection_inspector)} - ${apkFile.name}",
                    color = FuncDark,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
                val r = report
                if (r == null) {
                    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FuncBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    val dex2cValue = if (r.isDex2cDetected) {
                        "Detected (${r.nativeMethodsCount} native / ${r.totalMethodsCount} methods across ${r.dexCount} DEX)"
                    } else {
                        "Standard (${r.nativeMethodsCount} native / ${r.totalMethodsCount} methods across ${r.dexCount} DEX)"
                    }
                    val nativeLibsValue = if (r.nativeLibraries.isEmpty()) {
                        "None (Pure Java/Kotlin DEX)"
                    } else {
                        "${r.nativeLibraries.size} .so file(s) [${r.architectures.joinToString(", ")}]\n" +
                            r.nativeLibraries.take(10).joinToString("\n")
                    }
                    val zipAlignValue = if (r.isZipAligned) {
                        "Aligned (Verified ${r.uncompressedCheckedCount} STORED entries)"
                    } else {
                        "Unaligned (Needs Optimize APK / ZipAlign)"
                    }
                    val pseudoEncValue = if (r.isPseudoEncrypted) {
                        "Enabled (${r.pseudoEncryptedHeaderCount} encrypted flag headers)"
                    } else {
                        "Not Enabled (0x0000 Standard)"
                    }
                    val rows = listOf(
                        stringResource(R.string.packer_shell_status_label) to r.packerSummary,
                        stringResource(R.string.dex2c_status_label) to dex2cValue,
                        stringResource(R.string.native_libs_status_label) to nativeLibsValue,
                        stringResource(R.string.signature_label) to r.signatureScheme,
                        stringResource(R.string.zipalign_status_label) to zipAlignValue,
                        stringResource(R.string.pseudo_enc_status_label) to pseudoEncValue,
                        stringResource(R.string.debuggable_status_label) to if (r.isDebuggable) "true (Debuggable)" else "false (Release)"
                    )

                    rows.forEach { (label, value) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF7F7F7), RoundedCornerShape(4.dp))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText(label, value))
                                    Toast.makeText(context, context.getString(R.string.copied_to_clipboard, value), Toast.LENGTH_SHORT).show()
                                }
                                .padding(8.dp)
                        ) {
                            Text(label, color = FuncBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Text(value, color = FuncDark, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (r.sampleNativeMethods.isNotEmpty()) {
                        Text(
                            text = "JNI / Native Methods Sample (${r.sampleNativeMethods.size}):",
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = r.sampleNativeMethods.joinToString("\n"),
                            color = FuncDark,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F8E9), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = FuncBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


