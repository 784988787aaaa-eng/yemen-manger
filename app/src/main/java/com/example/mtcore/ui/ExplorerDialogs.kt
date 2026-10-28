package com.example.mtcore.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mtcore.R
import com.example.mtcore.data.CompressionLevel
import com.example.mtcore.data.FileProperties
import java.io.File

private val DialogBlue = Color(0xFF1976D2)
private val DialogBlack = Color(0xFF111111)

enum class OpenWithTool {
    TEXT_EDITOR,
    ARCHIVE_VIEWER,
    HEX_EDITOR,
    XML_FUNCTIONS,
    DEX_FUNCTIONS,
    ARSC_FUNCTIONS,
    APK_VIEWER,
    SCRIPT_EXECUTOR,
    CLASS_FUNCTIONS,
    COMPARISON_RESULT
}

@Composable
private fun LocalizedDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val outerContext = LocalContext.current
    val outerConfig = androidx.compose.ui.platform.LocalConfiguration.current
    val outerDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    Dialog(onDismissRequest = onDismissRequest) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalContext provides outerContext,
            androidx.compose.ui.platform.LocalConfiguration provides outerConfig,
            androidx.compose.ui.platform.LocalLayoutDirection provides outerDirection
        ) {
            content()
        }
    }
}

@Composable
private fun CompactDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit
) {
    LocalizedDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = DialogBlack, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                content()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun OpenWithGridDialog(
    onDismiss: () -> Unit,
    onSelectTool: (OpenWithTool) -> Unit
) {
    data class GridItem(
        val tool: OpenWithTool,
        val labelRes: Int,
        val bgColor: Color,
        val icon: ImageVector?,
        val badgeText: String? = null
    )

    val items = listOf(
        GridItem(OpenWithTool.TEXT_EDITOR, R.string.open_with_text_editor, Color(0xFF4285F4), Icons.Default.Description),
        GridItem(OpenWithTool.ARCHIVE_VIEWER, R.string.open_with_archive_viewer, Color(0xFF6D4C41), Icons.Default.Archive),
        GridItem(OpenWithTool.HEX_EDITOR, R.string.open_with_hex_editor, Color(0xFF546E7A), null, "HEX"),
        GridItem(OpenWithTool.XML_FUNCTIONS, R.string.open_with_xml_functions, Color(0xFF5C6BC0), null, "< >"),
        GridItem(OpenWithTool.DEX_FUNCTIONS, R.string.open_with_dex_functions, Color(0xFF43A047), null, "01"),
        GridItem(OpenWithTool.ARSC_FUNCTIONS, R.string.open_with_arsc_functions, Color(0xFFF9A825), Icons.Default.Widgets),
        GridItem(OpenWithTool.APK_VIEWER, R.string.open_with_apk_viewer, Color(0xFF4CAF50), Icons.Default.Android),
        GridItem(OpenWithTool.SCRIPT_EXECUTOR, R.string.open_with_script_executor, Color(0xFF1E88E5), Icons.Default.Terminal),
        GridItem(OpenWithTool.CLASS_FUNCTIONS, R.string.open_with_class_functions, Color(0xFF7E57C2), Icons.Default.Code),
        GridItem(OpenWithTool.COMPARISON_RESULT, R.string.open_with_diff_viewer, Color(0xFF00897B), null, "DIFF")
    )

    LocalizedDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.open_with),
                    color = DialogBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                items.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowItems.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        onDismiss()
                                        onSelectTool(item.tool)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(item.bgColor, RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.badgeText != null) {
                                        Text(
                                            text = item.badgeText,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    } else if (item.icon != null) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(item.labelRes),
                                    color = DialogBlack,
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
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
fun XmlOpenWithDialog(
    onDismiss: () -> Unit,
    onDecompile: () -> Unit,
    onStringPool: () -> Unit,
    onTranslationMode: () -> Unit = onStringPool
) {
    var selectedOption by remember { mutableIntStateOf(0) }
    CompactDialog(
        title = stringResource(R.string.open_with),
        onDismiss = onDismiss,
        content = {
            val options = listOf(
                stringResource(R.string.axml_decompile),
                stringResource(R.string.axml_string_pool),
                stringResource(R.string.xml_translation_mode)
            )
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = index }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == index,
                            onClick = { selectedOption = index }
                        )
                        Text(label, color = DialogBlack, fontSize = 14.sp)
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = DialogBlue)
            }
            TextButton(onClick = {
                onDismiss()
                when (selectedOption) {
                    0 -> onDecompile()
                    1 -> onStringPool()
                    else -> onTranslationMode()
                }
            }) {
                Text(stringResource(R.string.ok), color = DialogBlue)
            }
        }
    )
}

@Composable
fun ArscOpenWithDialog(
    onDismiss: () -> Unit,
    onSelectMode: (String) -> Unit,
    onOpenStringPool: () -> Unit = {},
    onOpenTranslationMode: () -> Unit = onOpenStringPool
) {
    LocalizedDialog(onDismissRequest = onDismiss) {
        val stringPoolLabel = stringResource(R.string.axml_string_pool)
        val translationLabel = stringResource(R.string.translation_mode)
        val options = listOf(
            stringResource(R.string.arsc_editor_plus),
            stringResource(R.string.arsc_editor),
            translationLabel,
            stringResource(R.string.resource_querier),
            stringPoolLabel
        )
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.open_with),
                    color = DialogBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                when (label) {
                                    stringPoolLabel -> onOpenStringPool()
                                    translationLabel -> onOpenTranslationMode()
                                    else -> onSelectMode(label)
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(0xFFF9A825), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = DialogBlack, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DexOpenWithDialog(
    onDismiss: () -> Unit,
    onOpenDexEditorPlus: () -> Unit,
    onRepairDexFile: () -> Unit,
    onOpenDexStrings: () -> Unit,
    onShowDexProperty: () -> Unit
) {
    LocalizedDialog(onDismissRequest = onDismiss) {
        val options = listOf(
            stringResource(R.string.dex_editor_plus) to onOpenDexEditorPlus,
            stringResource(R.string.dex_editor) to onOpenDexEditorPlus,
            stringResource(R.string.repair_dex_file) to onRepairDexFile,
            stringResource(R.string.dex_property) to onShowDexProperty,
            stringResource(R.string.dex2jar) to onOpenDexEditorPlus,
            stringResource(R.string.dex2smali) to onOpenDexEditorPlus,
            stringResource(R.string.decrypt_dex_strings) to onOpenDexStrings,
            stringResource(R.string.translation_mode) to onOpenDexStrings
        )

        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.open_with),
                    color = DialogBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { (label, action) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                action()
                            }
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(0xFF43A047), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("01", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = DialogBlack, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MultiDexDialog(
    dexFiles: List<File>,
    initialSelected: File,
    onDismiss: () -> Unit,
    onConfirm: (List<File>) -> Unit
) {
    var selectedPaths by remember(dexFiles, initialSelected) {
        mutableStateOf(dexFiles.map { it.absolutePath }.toSet())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.multidex_title),
                    color = DialogBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                    items(dexFiles, key = { it.absolutePath }) { file ->
                        val isChecked = file.absolutePath in selectedPaths
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = selectedPaths.toMutableSet()
                                    if (!next.add(file.absolutePath)) next.remove(file.absolutePath)
                                    selectedPaths = next
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val next = selectedPaths.toMutableSet()
                                    if (checked) next.add(file.absolutePath) else next.remove(file.absolutePath)
                                    selectedPaths = next
                                }
                            )
                            Text(
                                text = file.name,
                                color = DialogBlack,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatFileSize(file.length()),
                                color = Color(0xFF757575),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allSelected = selectedPaths.size == dexFiles.size
                    TextButton(onClick = {
                        selectedPaths = if (allSelected) {
                            emptySet()
                        } else {
                            dexFiles.map { it.absolutePath }.toSet()
                        }
                    }) {
                        Text(
                            text = if (allSelected) stringResource(R.string.invert_selection_caps) else stringResource(R.string.select_all_caps),
                            color = DialogBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel), color = DialogBlue)
                        }
                        TextButton(
                            onClick = {
                                val chosen = dexFiles.filter { it.absolutePath in selectedPaths }
                                    .ifEmpty { listOf(initialSelected) }
                                onDismiss()
                                onConfirm(chosen)
                            }
                        ) {
                            Text(stringResource(R.string.ok), color = DialogBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractingProgressDialog() {
    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = DialogBlue, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(stringResource(R.string.extracting_progress), color = DialogBlack, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun CreateDialog(onDismiss: () -> Unit, onCreateFile: (String) -> Unit, onCreateFolder: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val enterNameError = stringResource(R.string.enter_name)
    CompactDialog(stringResource(R.string.create_file_title), onDismiss, content = {
        Column {
            UnderlinedField(value = name, onValueChange = { name = it; error = null }, hint = stringResource(R.string.name))
            error?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
        }
    }, actions = {
        TextButton(onClick = { if (name.isBlank()) error = enterNameError else onCreateFile(name) }) { Text(stringResource(R.string.file_label), color = DialogBlue) }
        TextButton(onClick = { if (name.isBlank()) error = enterNameError else onCreateFolder(name) }) { Text(stringResource(R.string.folder_label), color = DialogBlue) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = DialogBlue) }
    })
}

@Composable
fun JumpDialog(initialPath: String, onDismiss: () -> Unit, onJump: (String) -> Unit) {
    val context = LocalContext.current
    var path by remember(initialPath) { mutableStateOf(TextFieldValue(initialPath, TextRange(0, initialPath.length))) }
    CompactDialog(stringResource(R.string.jump_to_path), onDismiss, content = {
        UnderlinedTextField(value = path, onValueChange = { path = it }, hint = stringResource(R.string.path))
    }, actions = {
        TextButton(onClick = { val clip = readClipboard(context); path = TextFieldValue(clip, TextRange(0, clip.length)) }) { Text(stringResource(R.string.paste), color = DialogBlue) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = DialogBlue) }
        TextButton(onClick = { onJump(path.text) }) { Text(stringResource(R.string.ok), color = DialogBlue) }
    })
}

@Composable
fun RenameDialog(initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val context = LocalContext.current
    var name by remember(initialName) { mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length))) }
    CompactDialog(stringResource(R.string.rename), onDismiss, content = {
        UnderlinedTextField(value = name, onValueChange = { name = it }, hint = stringResource(R.string.filename))
    }, actions = {
        TextButton(onClick = { val clip = readClipboard(context); name = TextFieldValue(clip, TextRange(0, clip.length)) }) { Text(stringResource(R.string.paste), color = DialogBlue) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = DialogBlue) }
        TextButton(onClick = { onRename(name.text) }) { Text(stringResource(R.string.ok), color = DialogBlue) }
    })
}

@Composable
fun PropertiesDialog(properties: FileProperties, onDismiss: () -> Unit) {
    CompactDialog(stringResource(R.string.properties_title), onDismiss, content = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.path_value, properties.path), color = DialogBlack, fontSize = 12.sp)
            Text(stringResource(R.string.size_value, properties.size), color = DialogBlack, fontSize = 13.sp)
            Text(stringResource(R.string.modified_value, properties.modified), color = DialogBlack, fontSize = 13.sp)
            Text(stringResource(R.string.readable_value, properties.readable), color = DialogBlack, fontSize = 13.sp)
            Text(stringResource(R.string.writable_value, properties.writable), color = DialogBlack, fontSize = 13.sp)
        }
    }, actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok), color = DialogBlue) } })
}

enum class ConflictChoice { REPLACE, SKIP, KEEP_BOTH }

@Composable
fun ConflictDialog(source: File, destination: File, onChoice: (ConflictChoice) -> Unit, onDismiss: () -> Unit) {
    var choice by remember(source.absolutePath, destination.absolutePath) { mutableStateOf(ConflictChoice.REPLACE) }
    CompactDialog(stringResource(R.string.file_conflict), onDismiss, content = {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.source_name, source.name), color = DialogBlack, fontSize = 13.sp)
            Text(stringResource(R.string.source_stats, formatFileDate(source), formatFileSize(source.length())), color = Color.Gray, fontSize = 11.sp)
            Text(stringResource(R.string.destination_name, destination.name), color = DialogBlack, fontSize = 13.sp)
            Text(stringResource(R.string.source_stats, formatFileDate(destination), formatFileSize(destination.length())), color = Color.Gray, fontSize = 11.sp)
            listOf(ConflictChoice.REPLACE to stringResource(R.string.replace_destination), ConflictChoice.SKIP to stringResource(R.string.skip_item), ConflictChoice.KEEP_BOTH to stringResource(R.string.keep_both)).forEach { (option, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = choice == option, onClick = { choice = option })
                    Text(label, color = DialogBlack, fontSize = 13.sp)
                }
            }
        }
    }, actions = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = DialogBlue) }
        TextButton(onClick = { onChoice(choice) }) { Text(stringResource(R.string.continue_label), color = DialogBlue) }
    })
}

@Composable
fun CreateArchiveDialog(initialName: String, onDismiss: () -> Unit, onCreate: (String, CompressionLevel, Boolean) -> Unit) {
    var filename by remember(initialName) { mutableStateOf(initialName) }
    var level by remember { mutableStateOf(CompressionLevel.NORMAL) }
    var expanded by remember { mutableStateOf(false) }
    var deleteSource by remember { mutableStateOf(false) }
    CompactDialog(stringResource(R.string.create_archive_title), onDismiss, content = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.filename), color = DialogBlack, fontSize = 13.sp)
            UnderlinedField(value = filename, onValueChange = { filename = it }, hint = stringResource(R.string.filename))
            Text(stringResource(R.string.level), color = DialogBlack, fontSize = 13.sp)
            Button(onClick = { expanded = true }, colors = ButtonDefaults.buttonColors(containerColor = DialogBlue)) { Text(stringResource(level.labelRes()), fontSize = 13.sp) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CompressionLevel.entries.forEach { option -> DropdownMenuItem(text = { Text(stringResource(option.labelRes())) }, onClick = { level = option; expanded = false }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.delete_source_after_compression), modifier = Modifier.weight(1f), color = DialogBlack, fontSize = 12.sp)
                Switch(checked = deleteSource, onCheckedChange = { deleteSource = it })
            }
        }
    }, actions = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = DialogBlue) }
        TextButton(onClick = { onCreate(filename, level, deleteSource) }) { Text(stringResource(R.string.ok), color = DialogBlue) }
    })
}

@Composable
fun AddToZipDialog(archive: File, item: File, onDismiss: () -> Unit, onAdd: (String, CompressionLevel, Boolean) -> Unit) {
    var entryName by remember(item.absolutePath) { mutableStateOf(item.name) }
    var level by remember { mutableStateOf(CompressionLevel.NORMAL) }
    var expanded by remember { mutableStateOf(false) }
    var deleteSource by remember { mutableStateOf(false) }
    CompactDialog(stringResource(R.string.add_to_zip), onDismiss, content = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.archive_name, archive.name), color = DialogBlack, fontSize = 12.sp)
            Text(stringResource(R.string.rename_entry), color = DialogBlack, fontSize = 13.sp)
            UnderlinedField(value = entryName, onValueChange = { entryName = it }, hint = item.name)
            Text(stringResource(R.string.level), color = DialogBlack, fontSize = 13.sp)
            Button(onClick = { expanded = true }, colors = ButtonDefaults.buttonColors(containerColor = DialogBlue)) { Text(stringResource(level.labelRes()), fontSize = 13.sp) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CompressionLevel.entries.forEach { option -> DropdownMenuItem(text = { Text(stringResource(option.labelRes())) }, onClick = { level = option; expanded = false }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.delete_source_after_add), modifier = Modifier.weight(1f), color = DialogBlack, fontSize = 12.sp)
                Switch(checked = deleteSource, onCheckedChange = { deleteSource = it })
            }
        }
    }, actions = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = DialogBlue) }
        TextButton(onClick = { if (entryName.isNotBlank() && !entryName.contains("/") && !entryName.contains("\\")) onAdd(entryName, level, deleteSource) }) { Text(stringResource(R.string.add), color = DialogBlue) }
    })
}

@Composable
private fun UnderlinedTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            textStyle = TextStyle(color = DialogBlack, fontSize = 14.sp),
            decorationBox = { innerTextField ->
                Column {
                    if (value.text.isEmpty()) Text(hint, color = Color.Gray, fontSize = 13.sp)
                    innerTextField()
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFF2196F3))
                }
            }
        )
    }
}

@Composable
private fun UnderlinedField(value: String, onValueChange: (String) -> Unit, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            textStyle = TextStyle(color = DialogBlack, fontSize = 14.sp),
            decorationBox = { innerTextField ->
                Column {
                    if (value.isEmpty()) Text(hint, color = Color.Gray, fontSize = 13.sp)
                    innerTextField()
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFF2196F3))
                }
            }
        )
    }
}

private fun readClipboard(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}

private fun formatFileDate(file: File): String = java.text.SimpleDateFormat("yy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(file.lastModified()))

private fun formatFileSize(bytes: Long): String {
    var size = bytes.toDouble()
    val units = arrayOf("B", "KB", "MB", "GB")
    var index = 0
    while (size >= 1024 && index < units.lastIndex) { size /= 1024; index += 1 }
    return if (index == 0) "${size.toLong()}${units[index]}" else String.format(java.util.Locale.US, "%.2f%s", size, units[index])
}

private fun CompressionLevel.labelRes(): Int = when (this) {
    CompressionLevel.STORE -> R.string.store
    CompressionLevel.FAST -> R.string.fast
    CompressionLevel.NORMAL -> R.string.normal
    CompressionLevel.MAXIMUM -> R.string.maximum
    CompressionLevel.ULTRA -> R.string.ultra
}
