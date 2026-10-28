package com.example.mtcore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mtcore.R
import com.example.mtcore.data.BinaryXmlDecompiler
import com.example.mtcore.data.DexParser
import com.example.mtcore.data.parsers.DexHeaderProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

private val ViewerToolbarBg = Color(0xFF2B2B2B)
private val ViewerBlue = Color(0xFF1976D2)
private val ViewerDivider = Color(0xFFE0E0E0)

/**
 * Full-screen String Pool editor (`StringPoolScreen`) matching the video:
 * - Dark #2B2B2B top bar with Back button, "String pool" title, instant Search icon, and Save / Overflow menu.
 * - LazyColumn displaying all strings separated by thin #E0E0E0 lines.
 * - Clicking any string opens the "Edit" dialog with an underlined text field (#2196F3) and CANCEL / OK buttons,
 *   updating the string pool and rebuilding the binary file on save!
 */
@Composable
fun StringPoolScreen(
    file: File,
    initialTranslationMode: Boolean = false,
    onSavedBinary: (File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val stringsList = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(true) }
    var isDirty by remember { mutableStateOf(false) }
    var translationOnly by remember(initialTranslationMode) { mutableStateOf(initialTranslationMode) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var showOverflow by remember { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath) {
        isLoading = true
        val extracted = if (file.extension.equals("dex", true)) {
            DexParser.parseDexFiles(listOf(file)).strings
        } else {
            BinaryXmlDecompiler.extractStringPoolFromFile(file)
        }
        stringsList.clear()
        stringsList.addAll(extracted)
        isLoading = false
    }

    fun savePoolChanges(andExit: Boolean = false) {
        scope.launch {
            runCatching {
                BinaryXmlDecompiler.saveUpdatedStringPoolToFile(file, stringsList.toList())
            }.onSuccess {
                isDirty = false
                Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                onSavedBinary(file)
                if (andExit) onBack()
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: context.getString(R.string.unable_to_save_file), Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        if (searchActive) {
            searchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    if (editingIndex in stringsList.indices) {
        val currentVal = stringsList[editingIndex]
        StringPoolEditDialog(
            initialValue = currentVal,
            onDismiss = { editingIndex = -1 },
            onConfirm = { updatedValue ->
                if (updatedValue != currentVal) {
                    stringsList[editingIndex] = updatedValue
                    isDirty = true
                }
                editingIndex = -1
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // Top Bar (#2B2B2B)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ViewerToolbarBg)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (searchActive) {
                    searchActive = false
                    searchQuery = ""
                } else {
                    onBack()
                }
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            if (searchActive) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(stringResource(R.string.search_hint), color = Color(0xFFBDBDBD), fontSize = 14.sp)
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }
            } else {
                val barTitle = if (translationOnly) {
                    stringResource(R.string.translation_mode)
                } else {
                    stringResource(R.string.string_pool_title)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = (if (isDirty) "*" else "") + barTitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${file.name} (${stringsList.size})",
                        color = Color(0xFFD0D0D0),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { searchActive = true }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint), tint = Color.White)
                }
            }
            if (isDirty) {
                IconButton(onClick = { savePoolChanges(andExit = false) }) {
                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save), tint = Color.White)
                }
            }
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more), tint = Color.White)
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save)) },
                        onClick = {
                            showOverflow = false
                            savePoolChanges(andExit = false)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (translationOnly) stringResource(R.string.string_pool_title)
                                else stringResource(R.string.translation_mode)
                            )
                        },
                        onClick = {
                            showOverflow = false
                            translationOnly = !translationOnly
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_all)) },
                        onClick = {
                            showOverflow = false
                            val allText = stringsList.joinToString("\n")
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("String pool", allText))
                            Toast.makeText(context, context.getString(R.string.copied_to_clipboard, "${stringsList.size} strings"), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ViewerBlue, strokeWidth = 3.dp)
            }
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            val indexedItems = remember(stringsList.size, q, isDirty, translationOnly) {
                stringsList.mapIndexedNotNull { index, value ->
                    val passesTranslation = !translationOnly || com.example.mtcore.data.ApkToolkit.isTranslatableString(value)
                    val passesQuery = q.isEmpty() || value.lowercase(Locale.ROOT).contains(q)
                    if (passesTranslation && passesQuery) {
                        index to value
                    } else null
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(indexedItems, key = { _, pair -> pair.first }) { _, (realIndex, textValue) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingIndex = realIndex }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = textValue,
                            color = Color(0xFF111111),
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(thickness = 0.6.dp, color = ViewerDivider)
                }
            }
        }
    }
}

@Composable
private fun StringPoolEditDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var fieldValue by remember(initialValue) {
        mutableStateOf(TextFieldValue(initialValue, TextRange(0, initialValue.length)))
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
                    text = stringResource(R.string.edit),
                    color = Color(0xFF111111),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    textStyle = TextStyle(color = Color(0xFF111111), fontSize = 14.5.sp),
                    cursorBrush = SolidColor(Color(0xFF2196F3)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    decorationBox = { inner ->
                        Column {
                            inner()
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(thickness = 1.5.dp, color = Color(0xFF2196F3))
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = ViewerBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { onConfirm(fieldValue.text) }) {
                        Text(stringResource(R.string.ok), color = ViewerBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Real DEX Property Dialog showing version (035/037/039), file size, Adler32 checksum, and exact table counts.
 */
@Composable
fun DexPropertyDialog(
    dexFile: File,
    onDismiss: () -> Unit
) {
    var props by remember { mutableStateOf<DexHeaderProperties?>(null) }

    LaunchedEffect(dexFile.absolutePath) {
        props = DexParser.inspectDexProperties(dexFile)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${stringResource(R.string.dex_property)} - ${dexFile.name}",
                    color = Color(0xFF111111),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val p = props
                if (p == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ViewerBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    DexPropRow(stringResource(R.string.dex_version_label), p.dexVersion)
                    DexPropRow(stringResource(R.string.file_size_label), "${p.fileSize} B")
                    DexPropRow(
                        stringResource(R.string.dex_checksum_label),
                        "${p.headerChecksumHex} (${if (p.checksumValid) "Valid" else "Calc: ${p.calculatedChecksumHex}"})"
                    )
                    DexPropRow(stringResource(R.string.dex_strings_count_label), p.stringIdsCount.toString())
                    DexPropRow(stringResource(R.string.dex_types_count_label), p.typeIdsCount.toString())
                    DexPropRow(stringResource(R.string.dex_protos_count_label), p.protoIdsCount.toString())
                    DexPropRow(stringResource(R.string.dex_fields_count_label), p.fieldIdsCount.toString())
                    DexPropRow(stringResource(R.string.dex_methods_count_label), p.methodIdsCount.toString())
                    DexPropRow(stringResource(R.string.dex_classes_count_label), p.classDefsCount.toString())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok), color = ViewerBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DexPropRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF666666), fontSize = 13.sp)
        Text(value, color = Color(0xFF111111), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Built-in Image Viewer Dialog with actual dimensions (Width × Height), file size, and Zoom/Pan support.
 */
@Composable
fun ImageViewerDialog(
    file: File,
    onDismiss: () -> Unit
) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var dimensions by remember { mutableStateOf("") }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                val decoded = BitmapFactory.decodeFile(file.absolutePath)
                if (decoded != null) {
                    dimensions = "${decoded.width} × ${decoded.height} px"
                    bitmap = decoded.asImageBitmap()
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 560.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ViewerToolbarBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$dimensions  •  ${file.length()} B", color = Color(0xFFBDBDBD), fontSize = 11.sp)
                    }
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                        Text("${(scale * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(0.5f, 6f)
                                scale = newScale
                                offset = if (newScale > 1f) offset + pan else Offset.Zero
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = file.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    } else {
                        Text(stringResource(R.string.loading), color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Built-in Font Viewer Dialog (`FontViewerDialog`) for `.ttf` and `.otf` files (e.g. `GT.ttf`, `HM.ttf`).
 * Loads the real Typeface from file and renders live English, Arabic, digits, and custom sample text!
 */
@Composable
fun FontViewerDialog(
    file: File,
    onDismiss: () -> Unit
) {
    var fontFamily by remember { mutableStateOf<FontFamily?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var customSample by remember { mutableStateOf("") }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tf = Typeface.createFromFile(file)
                FontFamily(tf)
            }.onSuccess { loaded ->
                fontFamily = loaded
            }.onFailure { err ->
                loadError = err.message ?: "Unable to load font"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 560.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "${stringResource(R.string.font_viewer_title)} - ${file.name}",
                    color = Color(0xFF111111),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = customSample,
                    onValueChange = { customSample = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF111111), fontSize = 14.sp),
                    cursorBrush = SolidColor(ViewerBlue),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    decorationBox = { inner ->
                        Column {
                            if (customSample.isEmpty()) {
                                Text(stringResource(R.string.font_sample_hint), color = Color(0xFF9E9E9E), fontSize = 13.sp)
                            }
                            inner()
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(thickness = 1.dp, color = ViewerBlue)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))

                val ff = fontFamily
                if (loadError != null) {
                    Text(loadError.orEmpty(), color = Color.Red, fontSize = 13.sp)
                } else if (ff == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ViewerBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (customSample.isNotEmpty()) {
                            Text(customSample, fontFamily = ff, fontSize = 24.sp, color = Color(0xFF1976D2))
                            HorizontalDivider(thickness = 0.5.dp, color = ViewerDivider)
                        }
                        Text("ABCDEFGHIJKLMNOPQRSTUVWXYZ", fontFamily = ff, fontSize = 17.sp, color = Color(0xFF111111))
                        Text("abcdefghijklmnopqrstuvwxyz", fontFamily = ff, fontSize = 17.sp, color = Color(0xFF111111))
                        Text("0123456789 !@#$%^&*()_+-=[]{}", fontFamily = ff, fontSize = 16.sp, color = Color(0xFF333333))
                        Text("أبجد هوز حطي كلمن سعفص قرشت ثخذ ضظغ", fontFamily = ff, fontSize = 18.sp, color = Color(0xFF111111))
                        HorizontalDivider(thickness = 0.5.dp, color = ViewerDivider)
                        listOf(12, 16, 20, 26, 32).forEach { spSize ->
                            Text(
                                text = "${spSize}sp: The quick brown fox / نص تجريبي 123",
                                fontFamily = ff,
                                fontSize = spSize.sp,
                                color = Color(0xFF222222)
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = ViewerBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Full-screen interactive Hex Editor (`HexEditorScreen`) displaying Offset | Hex | ASCII in 3 aligned columns
 * with search, byte editing, and saving!
 */
@Composable
fun HexEditorScreen(
    file: File,
    onSavedFile: (File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rawBytes by remember { mutableStateOf(ByteArray(0)) }
    var isLoading by remember { mutableStateOf(true) }
    var isDirty by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editingRowIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(file.absolutePath) {
        isLoading = true
        rawBytes = withContext(Dispatchers.IO) {
            if (!file.isFile) ByteArray(0) else {
                val maxLen = file.length().coerceAtMost(256 * 1024L).toInt()
                val buf = ByteArray(maxLen)
                RandomAccessFile(file, "r").use { it.readFully(buf, 0, maxLen) }
                buf
            }
        }
        isLoading = false
    }

    BackHandler {
        if (searchActive) {
            searchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val totalRows = (rawBytes.size + 15) / 16

    if (editingRowIndex in 0 until totalRows) {
        val rowStart = editingRowIndex * 16
        val rowEnd = (rowStart + 16).coerceAtMost(rawBytes.size)
        val slice = rawBytes.copyOfRange(rowStart, rowEnd)
        val initialHex = slice.joinToString(" ") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
        EditHexRowDialog(
            offsetHex = String.format(Locale.US, "%08X", rowStart),
            initialHex = initialHex,
            byteCount = slice.size,
            onDismiss = { editingRowIndex = -1 },
            onConfirm = { newSlice ->
                val updated = rawBytes.copyOf()
                System.arraycopy(newSlice, 0, updated, rowStart, newSlice.size.coerceAtMost(rowEnd - rowStart))
                rawBytes = updated
                isDirty = true
                editingRowIndex = -1
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ViewerToolbarBg)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (searchActive) {
                    searchActive = false
                    searchQuery = ""
                } else {
                    onBack()
                }
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            if (searchActive) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(stringResource(R.string.search_hint), color = Color(0xFFBDBDBD), fontSize = 13.sp)
                        }
                        inner()
                    }
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = (if (isDirty) "*" else "") + stringResource(R.string.hex_editor_title, file.name),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${rawBytes.size} bytes",
                        color = Color(0xFFD0D0D0),
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = { searchActive = true }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint), tint = Color.White)
                }
            }
            IconButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        RandomAccessFile(file, "rw").use { raf ->
                            raf.seek(0)
                            raf.write(rawBytes)
                        }
                    }
                    isDirty = false
                    Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                    onSavedFile(file)
                }
            }) {
                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save), tint = Color.White)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ViewerBlue, strokeWidth = 3.dp)
            }
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            val matchingRows = remember(rawBytes, q) {
                (0 until totalRows).filter { rowIdx ->
                    if (q.isEmpty()) true else {
                        val start = rowIdx * 16
                        val end = (start + 16).coerceAtMost(rawBytes.size)
                        val hexStr = (start until end).joinToString(" ") { String.format(Locale.US, "%02x", rawBytes[it].toInt() and 0xFF) }
                        val asciiStr = (start until end).map {
                            val b = rawBytes[it].toInt() and 0xFF
                            if (b in 0x20..0x7E) b.toChar() else '.'
                        }.joinToString("")
                        hexStr.contains(q) || asciiStr.lowercase(Locale.ROOT).contains(q)
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(matchingRows, key = { _, r -> r }) { _, rowIdx ->
                    val start = rowIdx * 16
                    val end = (start + 16).coerceAtMost(rawBytes.size)
                    val offsetText = String.format(Locale.US, "%08X", start)
                    val hexText = buildString(48) {
                        for (i in 0 until 16) {
                            if (start + i < end) {
                                append(String.format(Locale.US, "%02X ", rawBytes[start + i].toInt() and 0xFF))
                            } else {
                                append("   ")
                            }
                            if (i == 7) append(' ')
                        }
                    }
                    val asciiText = buildString(16) {
                        for (i in start until end) {
                            val b = rawBytes[i].toInt() and 0xFF
                            append(if (b in 0x20..0x7E) b.toChar() else '.')
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingRowIndex = rowIdx }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = offsetText,
                            color = Color(0xFF1565C0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(68.dp)
                        )
                        Text(
                            text = hexText,
                            color = Color(0xFF111111),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = asciiText,
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(114.dp)
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = ViewerDivider)
                }
            }
        }
    }
}

@Composable
private fun EditHexRowDialog(
    offsetHex: String,
    initialHex: String,
    byteCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit
) {
    var hexField by remember(initialHex) {
        mutableStateOf(TextFieldValue(initialHex, TextRange(0, initialHex.length)))
    }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.edit_bytes_title, offsetHex),
                    color = Color(0xFF111111),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = hexField,
                    onValueChange = { hexField = it; error = null },
                    textStyle = TextStyle(color = Color(0xFF111111), fontSize = 13.5.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(ViewerBlue),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    decorationBox = { inner ->
                        Column {
                            inner()
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(thickness = 1.5.dp, color = ViewerBlue)
                        }
                    }
                )
                error?.let {
                    Text(it, color = Color.Red, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = ViewerBlue)
                    }
                    TextButton(onClick = {
                        val tokens = hexField.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        val parsed = runCatching {
                            ByteArray(byteCount) { idx ->
                                val tok = tokens.getOrNull(idx) ?: "00"
                                tok.toInt(16).toByte()
                            }
                        }.getOrNull()
                        if (parsed == null) {
                            error = "Invalid hex byte format"
                        } else {
                            onConfirm(parsed)
                        }
                    }) {
                        Text(stringResource(R.string.ok), color = ViewerBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
