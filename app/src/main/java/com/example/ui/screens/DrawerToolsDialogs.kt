package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.i18n.AppStrings
import com.example.core.model.FileEntry
import com.example.engines.system.DiskVolumeStats
import com.example.engines.system.InstalledAppRecord
import com.example.engines.system.LanServerState
import com.example.engines.system.SigningKeyItem
import com.example.engines.system.SmaliOpcodeEntry
import com.example.ui.viewmodel.DrawerTool
import com.example.ui.viewmodel.YemenManagerViewModel

@Composable
fun ActiveDrawerToolModalHost(
    activeTool: DrawerTool?,
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    trashEntries: List<FileEntry>,
    signingKeys: List<SigningKeyItem>,
    commonPasswords: List<String>,
    networkProfiles: List<String>,
    lanState: LanServerState,
    terminalTranscript: List<String>,
    installedApps: List<InstalledAppRecord>,
    smaliOpcodes: List<SmaliOpcodeEntry>
) {
    when (activeTool) {
        DrawerTool.RECYCLE_BIN -> RecycleBinToolDialog(
            entries = trashEntries,
            strings = strings,
            onRestore = { viewModel.restoreFromTrash(it) },
            onEmptyAll = { viewModel.emptyRecycleBin() },
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        DrawerTool.PLUGIN_MANAGER -> PluginManagerToolDialog(
            viewModel = viewModel,
            strings = strings,
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        DrawerTool.REMOTE_MANAGER -> RemoteManagerToolDialog(
            lanState = lanState,
            networkProfiles = networkProfiles,
            strings = strings,
            onToggleLanServer = { viewModel.toggleLanServer() },
            onAddProfile = { proto, host -> viewModel.addNetworkProfile(proto, host) },
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        DrawerTool.SCREEN_COLOR_PICKER -> ScreenColorPickerToolDialog(
            strings = strings,
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        DrawerTool.SIGNING_KEY -> SigningKeyToolDialog(
            keys = signingKeys,
            strings = strings,
            onGenerateKey = { viewModel.generateNewSigningKey(it) },
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        DrawerTool.COMMON_PASSWORDS -> CommonPasswordsToolDialog(
            passwords = commonPasswords,
            strings = strings,
            onAddPassword = { viewModel.addCommonPassword(it) },
            onRemovePassword = { viewModel.removeCommonPassword(it) },
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        DrawerTool.TERMINAL_SIMULATOR -> TerminalSimulatorToolDialog(
            transcript = terminalTranscript,
            strings = strings,
            onExecute = { viewModel.runTerminalCommand(it) },
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        DrawerTool.ACTIVITY_RECORD -> ActivityRecordToolDialog(
            apps = installedApps,
            strings = strings,
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        DrawerTool.SMALI_INSTRUCTIONS -> SmaliInstructionsToolDialog(
            opcodes = smaliOpcodes,
            strings = strings,
            onDismiss = { viewModel.dismissDrawerToolDialog() }
        )
        else -> Unit
    }
}

@Composable
private fun RecycleBinToolDialog(
    entries: List<FileEntry>,
    strings: AppStrings,
    onRestore: (FileEntry) -> Unit,
    onEmptyAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("recycle_bin_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.toolRecycleBin, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = onEmptyAll) {
                            Text(if (strings.isArabic) "إفراغ السلة" else "Empty All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (strings.isArabic) "سلة المحذوفات فارغة" else "Recycle Bin is empty",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(entries, key = { it.uriOrPath }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(DiskVolumeStats.formatSizeShort(item.sizeBytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onRestore(item) }) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore", tint = AccentBlueColor)
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(strings.confirmProceed, color = AccentBlueColor) }
                }
            }
        }
    }
}

@Composable
private fun PluginManagerToolDialog(
    viewModel: YemenManagerViewModel,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    var manifestJson by remember {
        mutableStateOf("""{"id":"ym.plugin.arsc_translator","name":"ARSC String Translator","version":"1.2.0","minCoreVersion":"1.0.0","permissions":["READ_WORKSPACE"]}""")
    }
    val builtInPlugins = listOf(
        "YM-Plugin: AXML String Pool Decoder v1.0.0 (Active • Sandboxed)",
        "YM-Plugin: DEX Checksum & String Inspector v1.0.0 (Active • Sandboxed)",
        "YM-Plugin: ZipSlip Security Guard v1.0.0 (Active • Core)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.toolPluginManager, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                builtInPlugins.forEach { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Text(p, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                    }
                }
                OutlinedTextField(
                    value = manifestJson,
                    onValueChange = { manifestJson = it },
                    label = { Text(if (strings.isArabic) "فحص وتسجيل بيان إضافة (JSON)" else "Validate Plugin Manifest (JSON)") },
                    textStyle = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { viewModel.validateSampleExtensionManifest(manifestJson) }) {
                        Text(if (strings.isArabic) "فحص العزل الأمني" else "Validate Sandbox")
                    }
                    TextButton(onClick = onDismiss) { Text(strings.confirmProceed, color = AccentBlueColor) }
                }
            }
        }
    }
}

@Composable
private fun RemoteManagerToolDialog(
    lanState: LanServerState,
    networkProfiles: List<String>,
    strings: AppStrings,
    onToggleLanServer: () -> Unit,
    onAddProfile: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var newHost by remember { mutableStateOf("192.168.1.50:2121") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Router, contentDescription = null, tint = AccentBlueColor)
                    Text(strings.toolRemoteManager, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (lanState.isRunning) {
                                "LAN Server ACTIVE: http://${lanState.bindAddress}:${lanState.port}/?token=${lanState.sessionToken}"
                            } else {
                                if (strings.isArabic) "خادم النقل المحلي متوقف (آمن افتراضيًا)" else "Local LAN Server is stopped (Safe default)"
                            },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Button(
                            onClick = onToggleLanServer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (lanState.isRunning) MaterialTheme.colorScheme.error else AccentBlueColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (lanState.isRunning) strings.stopLanServer else strings.startLanServer)
                        }
                    }
                }

                Text(
                    text = if (strings.isArabic) "اتصالات التخزين الشبكي المحفوظة" else "Saved Remote Connections",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(modifier = Modifier.height(120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(networkProfiles) { profile ->
                        Text("• $profile", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newHost,
                        onValueChange = { newHost = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onAddProfile("FTP", newHost) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = AccentBlueColor)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(strings.confirmProceed, color = AccentBlueColor) }
                }
            }
        }
    }
}

@Composable
private fun ScreenColorPickerToolDialog(
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var r by remember { mutableFloatStateOf(59f) }
    var g by remember { mutableFloatStateOf(153f) }
    var b by remember { mutableFloatStateOf(252f) }

    val rInt = r.toInt().coerceIn(0, 255)
    val gInt = g.toInt().coerceIn(0, 255)
    val bInt = b.toInt().coerceIn(0, 255)
    val hexRgb = String.format("#%02X%02X%02X", rInt, gInt, bInt)
    val hexArgb = String.format("#FF%02X%02X%02X", rInt, gInt, bInt)
    val composeColor = Color(rInt, gInt, bInt)

    val presets = listOf(
        Triple(38, 41, 46),
        Triple(59, 153, 252),
        Triple(76, 175, 80),
        Triple(255, 193, 7),
        Triple(239, 83, 80),
        Triple(141, 110, 99),
        Triple(0, 150, 136),
        Triple(92, 107, 192)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("screen_color_picker_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.toolScreenColorPicker, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(composeColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { clipboard.setText(AnnotatedString(hexRgb)) }
                        ) {
                            Text("HEX: $hexRgb", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentBlueColor)
                        }
                        Text("ARGB: $hexArgb", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("RGB($rInt, $gInt, $bInt)", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Quick Preset Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presets.forEach { (pr, pg, pb) ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(pr, pg, pb))
                                .clickable {
                                    r = pr.toFloat()
                                    g = pg.toFloat()
                                    b = pb.toFloat()
                                }
                        )
                    }
                }

                Text("R: $rInt", fontSize = 12.sp)
                Slider(value = r, onValueChange = { r = it }, valueRange = 0f..255f)
                Text("G: $gInt", fontSize = 12.sp)
                Slider(value = g, onValueChange = { g = it }, valueRange = 0f..255f)
                Text("B: $bInt", fontSize = 12.sp)
                Slider(value = b, onValueChange = { b = it }, valueRange = 0f..255f)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(hexRgb))
                        onDismiss()
                    }) {
                        Text(if (strings.isArabic) "نسخ اللون وإغلاق" else "COPY & CLOSE", color = AccentBlueColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SigningKeyToolDialog(
    keys: List<SigningKeyItem>,
    strings: AppStrings,
    onGenerateKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var aliasInput by remember { mutableStateOf("custom_release_key") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("signing_key_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = AccentBlueColor)
                    Text(strings.toolSigningKey, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                LazyColumn(modifier = Modifier.height(210.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(keys, key = { it.id }) { key ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${key.alias} (${key.type})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(key.algorithm, fontSize = 11.sp, color = AccentBlueColor)
                                Text("SHA-1: ${key.sha1Fingerprint.take(29)}...", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("SHA-256: ${key.sha256Fingerprint}...", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text(if (strings.isArabic) "اسم المفتاح الجديد (Alias)" else "New Key Alias") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onGenerateKey(aliasInput) }) {
                        Text(if (strings.isArabic) "توليد" else "Generate")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(strings.confirmProceed, color = AccentBlueColor) }
                }
            }
        }
    }
}

@Composable
private fun CommonPasswordsToolDialog(
    passwords: List<String>,
    strings: AppStrings,
    onAddPassword: (String) -> Unit,
    onRemovePassword: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var newPwd by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("common_passwords_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.toolCommonPasswords, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (strings.isArabic) "كلمات المرور المحفوظة محليًا لفك الأرشيفات ومفاتيح التوقيع:" else "Locally stored passwords for archives & signing keys:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(modifier = Modifier.height(190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(passwords, key = { it }) { pwd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pwd, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                            Row {
                                IconButton(onClick = { clipboard.setText(AnnotatedString(pwd)) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp), tint = AccentBlueColor)
                                }
                                IconButton(onClick = { onRemovePassword(pwd) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it },
                        placeholder = { Text(if (strings.isArabic) "أضف كلمة مرور شائعة..." else "Add password...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        onAddPassword(newPwd)
                        newPwd = ""
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(strings.confirmProceed, color = AccentBlueColor) }
                }
            }
        }
    }
}

@Composable
private fun TerminalSimulatorToolDialog(
    transcript: List<String>,
    strings: AppStrings,
    onExecute: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cmdInput by remember { mutableStateOf("ls -la") }
    val quickCommands = listOf("ls -la", "pwd", "df -h", "uname -a", "id", "find .", "help", "clear")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF15181E),
            modifier = Modifier.fillMaxWidth().testTag("terminal_simulator_dialog")
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ">_ ${strings.toolTerminalSimulator}",
                        color = Color(0xFF4AF626),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    TextButton(onClick = onDismiss) {
                        Text(strings.confirmProceed, color = AccentBlueColor)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0D0F12))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(transcript) { line ->
                        Text(
                            text = line,
                            color = if (line.startsWith("$")) Color(0xFF4DA3FF) else Color(0xFFE2E8F0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickCommands.forEach { qc ->
                        AssistChip(
                            onClick = { onExecute(qc) },
                            label = { Text(qc, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = cmdInput,
                        onValueChange = { cmdInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onExecute(cmdInput)
                            cmdInput = ""
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Run", tint = Color(0xFF4AF626))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRecordToolDialog(
    apps: List<InstalledAppRecord>,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("activity_record_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.toolActivityRecord, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (strings.isArabic)
                        "النشاط الحالي: com.example.MainActivity (Yemen Manager)\nحزم التطبيقات المكتشفة في الجلسة (${apps.size}):"
                    else
                        "Active Foreground: com.example.MainActivity (Yemen Manager)\nInspectable Package Records (${apps.size}):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(apps.take(40), key = { it.packageName }) { app ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(app.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(app.packageName, fontSize = 11.sp, color = AccentBlueColor, fontFamily = FontFamily.Monospace)
                                Text("v${app.versionName} • Target API ${app.targetSdk} • Perms: ${app.grantedPermissions.size}/${app.declaredPermissions.size}", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(strings.confirmProceed, color = AccentBlueColor) }
                }
            }
        }
    }
}

@Composable
private fun SmaliInstructionsToolDialog(
    opcodes: List<SmaliOpcodeEntry>,
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = opcodes.filter {
        search.isBlank() ||
            it.mnemonic.contains(search, ignoreCase = true) ||
            it.opcodeHex.contains(search, ignoreCase = true) ||
            it.descriptionAr.contains(search, ignoreCase = true) ||
            it.descriptionEn.contains(search, ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag("smali_instructions_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.toolSmaliInstructions, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(if (strings.isArabic) "ابحث عن تعليمة Smali (مثل invoke، const، if-eqz)..." else "Search opcode or mnemonic (e.g. invoke, const, if-eqz)...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                LazyColumn(modifier = Modifier.height(310.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.opcodeHex }) { op ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "0x${op.opcodeHex}  ${op.mnemonic}",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentBlueColor,
                                        fontSize = 14.sp
                                    )
                                    Text(op.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(op.syntax, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text(
                                    text = if (strings.isArabic) op.descriptionAr else op.descriptionEn,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(strings.confirmProceed, color = AccentBlueColor) }
                }
            }
        }
    }
}
