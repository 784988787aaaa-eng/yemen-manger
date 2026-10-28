package com.example.mtcore.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mtcore.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val EditorToolbar = Color(0xFF2B2B2B)
private val EditorWhite = Color.White
private val LineNumberBackground = Color(0xFFF5F5F5)
private val EditorGray = Color(0xFF757575)
private val EditorSeparator = Color(0xFFE0E0E0)
private val ActiveEditSkyBlue = Color(0xFF64B5F6)

private val ClassBadgeColor = Color(0xFF4285F4)
private val FieldBadgeColor = Color(0xFFF57C00)
private val MethodBadgeColor = Color(0xFFD32F2F)

@Composable
fun CodeEditorScreen(viewModel: MainViewModel, onBack: (Boolean) -> Unit) {
    val state by viewModel.editor.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var showExitDialog by remember { mutableStateOf(false) }
    var showNavigationDialog by remember { mutableStateOf(false) }
    var showSmaliToJavaDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFindReplaceBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    var isEditMode by remember(state.file?.absolutePath) { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val sharedScrollState = rememberScrollState()

    val highlightedText = remember(fieldValue.text, state.file?.name) {
        SyntaxHighlighter.highlight(fieldValue.text, state.file?.name)
    }

    val visualTransformation = remember(state.file?.name) {
        SyntaxHighlighter.createVisualTransformation(state.file?.name)
    }

    val navSymbols = remember(fieldValue.text, state.file?.name) {
        SyntaxHighlighter.parseNavigationSymbols(fieldValue.text, state.file?.name)
    }

    LaunchedEffect(state.file, state.content, state.isLoading) {
        if (!state.isLoading && state.error == null && fieldValue.text != state.content) {
            fieldValue = TextFieldValue(
                text = state.content,
                selection = TextRange(state.cursor.coerceIn(0, state.content.length))
            )
        }
    }

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            delay(60)
            runCatching {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        } else {
            runCatching {
                keyboardController?.hide()
            }
        }
    }

    BackHandler {
        if (state.isDirty) {
            showExitDialog = true
        } else {
            onBack(state.hasSavedChangesInSession)
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes)) },
            text = { Text(stringResource(R.string.save_changes_before_leaving)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateContent(fieldValue.text)
                    viewModel.saveFile { saved, _ ->
                        if (saved) {
                            Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                            showExitDialog = false
                            onBack(true)
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitDialog = false
                        viewModel.discardAndClose()
                        onBack(false)
                    }) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.cancel_titlecase)) }
                }
            }
        )
    }

    if (showNavigationDialog) {
        CodeNavigationDialog(
            symbols = navSymbols,
            onDismiss = { showNavigationDialog = false },
            onSelectSymbol = { symbol ->
                showNavigationDialog = false
                val targetCursor = symbol.charOffset.coerceIn(0, fieldValue.text.length)
                fieldValue = fieldValue.copy(selection = TextRange(targetCursor))
                viewModel.updateCursor(targetCursor)
                scope.launch {
                    val linePx = with(density) { ((symbol.startLine - 1).coerceAtLeast(0) * 20).dp.toPx().toInt() }
                    sharedScrollState.animateScrollTo(linePx)
                }
            }
        )
    }

    val cursorLine = remember(fieldValue.text, fieldValue.selection.start) {
        val safeCursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
        fieldValue.text.substring(0, safeCursor).count { it == '\n' } + 1
    }

    val activeSymbolRangeText = remember(cursorLine, navSymbols) {
        val enclosingMethod = navSymbols.firstOrNull {
            it.kind == NavSymbolKind.METHOD && cursorLine in it.startLine..it.endLine
        }
        if (enclosingMethod != null) {
            "[${enclosingMethod.startLine} - ${enclosingMethod.endLine}] : ${enclosingMethod.title}"
        } else {
            "UTF-8"
        }
    }

    val totalLinesCount = remember(fieldValue.text) {
        fieldValue.text.count { it == '\n' } + 1
    }

    val findMatchOffsets = remember(fieldValue.text, findQuery, matchCase) {
        if (findQuery.isEmpty()) emptyList()
        else {
            val list = ArrayList<Int>()
            var startIndex = 0
            while (startIndex <= fieldValue.text.length - findQuery.length) {
                val found = fieldValue.text.indexOf(findQuery, startIndex, ignoreCase = !matchCase)
                if (found < 0) break
                list += found
                startIndex = found + findQuery.length.coerceAtLeast(1)
            }
            list
        }
    }

    fun jumpToOffset(charOffset: Int) {
        val safeOffset = charOffset.coerceIn(0, fieldValue.text.length)
        val endSel = (safeOffset + findQuery.length).coerceIn(safeOffset, fieldValue.text.length)
        fieldValue = fieldValue.copy(selection = TextRange(safeOffset, endSel))
        viewModel.updateCursor(safeOffset)
        val targetLine = fieldValue.text.substring(0, safeOffset).count { it == '\n' } + 1
        scope.launch {
            val linePx = with(density) { ((targetLine - 1).coerceAtLeast(0) * 20).dp.toPx().toInt() }
            sharedScrollState.animateScrollTo(linePx)
        }
    }

    fun formatCurrentCode() {
        val ext = state.file?.extension.orEmpty()
        val formatted = CodeFormatter.formatCode(fieldValue.text, ext)
        fieldValue = TextFieldValue(formatted, TextRange(0))
        viewModel.updateContent(formatted)
        Toast.makeText(context, context.getString(R.string.code_formatted_toast), Toast.LENGTH_SHORT).show()
    }

    if (showSmaliToJavaDialog) {
        SmaliToJavaPreviewDialog(
            smaliFile = state.file,
            smaliContent = fieldValue.text,
            onDismiss = { showSmaliToJavaDialog = false },
            onSavedJavaFile = { savedJava ->
                viewModel.markFileModified(savedJava)
                viewModel.openFile(savedJava)
            }
        )
    }

    if (showGoToLineDialog) {
        GoToLineDialog(
            maxLine = totalLinesCount,
            currentLine = cursorLine,
            onDismiss = { showGoToLineDialog = false },
            onGoToLine = { targetLine ->
                showGoToLineDialog = false
                val clamped = targetLine.coerceIn(1, totalLinesCount)
                var offset = 0
                var current = 1
                while (current < clamped && offset < fieldValue.text.length) {
                    val nl = fieldValue.text.indexOf('\n', offset)
                    if (nl < 0) {
                        offset = fieldValue.text.length
                        break
                    }
                    offset = nl + 1
                    current++
                }
                fieldValue = fieldValue.copy(selection = TextRange(offset))
                viewModel.updateCursor(offset)
                scope.launch {
                    val linePx = with(density) { ((clamped - 1).coerceAtLeast(0) * 20).dp.toPx().toInt() }
                    sharedScrollState.animateScrollTo(linePx)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(EditorWhite)) {
        // Compact Top Toolbar (8 icons @ 34.dp + 19.dp icon size so File Name & Edit Pencil are always visible)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(EditorToolbar)
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Back Button
                IconButton(
                    onClick = {
                        if (state.isDirty) showExitDialog = true else onBack(state.hasSavedChangesInSession)
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = EditorWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 2. File Name + Line:Column & Enclosing Method
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clickable { showGoToLineDialog = true }
                ) {
                    Text(
                        (if (state.isDirty) "*" else "") + (state.file?.name ?: stringResource(R.string.editor)),
                        color = EditorWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${lineAndColumn(fieldValue.text, fieldValue.selection.start)} $activeSymbolRangeText",
                        color = Color(0xFFD6D6D6),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 3. Compass (Navigation)
                IconButton(
                    onClick = { showNavigationDialog = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Explore,
                        contentDescription = stringResource(R.string.navigation_title),
                        tint = EditorWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 4. Undo
                IconButton(
                    onClick = {
                        viewModel.undo()
                        val updated = viewModel.editor.value.content
                        fieldValue = TextFieldValue(
                            text = updated,
                            selection = TextRange(fieldValue.selection.start.coerceIn(0, updated.length))
                        )
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = EditorWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 5. Redo
                IconButton(
                    onClick = {
                        viewModel.redo()
                        val updated = viewModel.editor.value.content
                        fieldValue = TextFieldValue(
                            text = updated,
                            selection = TextRange(fieldValue.selection.start.coerceIn(0, updated.length))
                        )
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Redo,
                        contentDescription = stringResource(R.string.redo),
                        tint = EditorWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 6. Edit Pencil Icon (Toggles/Activates Edit Mode & Opens Keyboard)
                IconButton(
                    onClick = {
                        isEditMode = !isEditMode
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = if (isEditMode) ActiveEditSkyBlue else EditorWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 7. Save
                IconButton(
                    onClick = {
                        viewModel.updateContent(fieldValue.text)
                        viewModel.saveFile { saved, _ ->
                            if (saved) Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(R.string.save),
                        tint = EditorWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 8. Search (Opens Find & Replace Bar below top bar)
                IconButton(
                    onClick = {
                        showFindReplaceBar = !showFindReplaceBar
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.find_and_replace),
                        tint = if (showFindReplaceBar) ActiveEditSkyBlue else EditorWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 9. Overflow Menu (MoreVert)
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = EditorWhite,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.decompile_to_java)) },
                            onClick = {
                                showOverflowMenu = false
                                showSmaliToJavaDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.go_to_line)) },
                            onClick = {
                                showOverflowMenu = false
                                showGoToLineDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.format_prettify_code)) },
                            onClick = {
                                showOverflowMenu = false
                                formatCurrentCode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.toggle_read_edit_mode)) },
                            onClick = {
                                showOverflowMenu = false
                                isEditMode = !isEditMode
                            }
                        )
                    }
                }
            }
        }

        // Inline Find & Replace Bar
        if (showFindReplaceBar) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF2F4F7))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BasicTextField(
                            value = findQuery,
                            onValueChange = {
                                findQuery = it
                                currentMatchIndex = 0
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Color(0xFF111111), fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF1976D2)),
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (findQuery.isEmpty()) {
                                        Text(stringResource(R.string.find_hint), color = EditorGray, fontSize = 12.sp)
                                    }
                                    inner()
                                }
                            }
                        )
                        val matchCount = findMatchOffsets.size
                        val counterText = if (matchCount == 0) "0/0" else "${(currentMatchIndex.coerceIn(0, matchCount - 1)) + 1}/$matchCount"
                        Text(counterText, color = EditorGray, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (matchCase) Color(0xFF1976D2) else Color(0xFFE0E0E0),
                            modifier = Modifier.clickable { matchCase = !matchCase }
                        ) {
                            Text(
                                text = "Aa",
                                color = if (matchCase) Color.White else Color(0xFF333333),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (findMatchOffsets.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex - 1 + findMatchOffsets.size) % findMatchOffsets.size
                                    jumpToOffset(findMatchOffsets[currentMatchIndex])
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", tint = Color(0xFF333333))
                        }
                        IconButton(
                            onClick = {
                                if (findMatchOffsets.isNotEmpty()) {
                                    currentMatchIndex = (currentMatchIndex + 1) % findMatchOffsets.size
                                    jumpToOffset(findMatchOffsets[currentMatchIndex])
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", tint = Color(0xFF333333))
                        }
                        IconButton(
                            onClick = { showFindReplaceBar = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BasicTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color(0xFF111111), fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Color(0xFF1976D2)),
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (replaceQuery.isEmpty()) {
                                        Text(stringResource(R.string.replace_hint), color = EditorGray, fontSize = 12.sp)
                                    }
                                    inner()
                                }
                            }
                        )
                        TextButton(
                            enabled = findMatchOffsets.isNotEmpty(),
                            onClick = {
                                if (findMatchOffsets.isNotEmpty()) {
                                    val idx = currentMatchIndex.coerceIn(0, findMatchOffsets.lastIndex)
                                    val pos = findMatchOffsets[idx]
                                    val updated = fieldValue.text.replaceRange(pos, pos + findQuery.length, replaceQuery)
                                    fieldValue = TextFieldValue(updated, TextRange(pos + replaceQuery.length))
                                    viewModel.updateContent(updated)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.replace_one), color = Color(0xFF1976D2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            enabled = findMatchOffsets.isNotEmpty(),
                            onClick = {
                                if (findQuery.isNotEmpty()) {
                                    val updated = fieldValue.text.replace(findQuery, replaceQuery, ignoreCase = !matchCase)
                                    fieldValue = TextFieldValue(updated, TextRange(0))
                                    viewModel.updateContent(updated)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.replace_all), color = Color(0xFF1976D2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.8.dp, color = EditorSeparator)
            }
        }

        if (state.isLoading) {
            Text(stringResource(R.string.loading), Modifier.padding(12.dp), color = Color.Black)
        } else if (state.error != null) {
            Text(state.error.orEmpty(), Modifier.padding(12.dp), color = Color.Red)
        } else {
            val lineCount = totalLinesCount
            // Force LTR for line numbers, code body, and bottom symbol bar regardless of Arabic/English UI locale
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(sharedScrollState)
                    ) {
                        // Line Numbers Column with right separator border
                        Column(
                            Modifier
                                .width(44.dp)
                                .background(LineNumberBackground)
                                .drawBehind {
                                    drawLine(
                                        color = EditorSeparator,
                                        start = Offset(size.width - 1.dp.toPx(), 0f),
                                        end = Offset(size.width - 1.dp.toPx(), size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                .padding(top = 8.dp, bottom = 8.dp, end = 7.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            repeat(lineCount) { line ->
                                val isCurrentLine = (line + 1) == cursorLine
                                Text(
                                    text = (line + 1).toString(),
                                    color = if (isCurrentLine) Color(0xFF1976D2) else EditorGray,
                                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (!isEditMode) {
                            // Fast Colored Read Mode: guarantees 100% immediate syntax-highlighted rendering
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clickable { isEditMode = true }
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = highlightedText,
                                    style = TextStyle(
                                        color = Color(0xFF111111),
                                        fontSize = 12.sp,
                                        lineHeight = 20.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            // Edit Mode: BasicTextField without forced height, with FocusRequester & VisualTransformation
                            BasicTextField(
                                value = fieldValue,
                                onValueChange = { value ->
                                    fieldValue = value
                                    viewModel.updateContent(value.text)
                                    viewModel.updateCursor(value.selection.start)
                                },
                                visualTransformation = visualTransformation,
                                cursorBrush = SolidColor(Color(0xFF1976D2)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .focusRequester(focusRequester),
                                textStyle = TextStyle(
                                    color = Color(0xFF111111),
                                    fontSize = 12.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Text
                                )
                            )
                        }
                    }

                    // Bottom Quick Symbol Bar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(EditorToolbar)
                            .horizontalScroll(rememberScrollState())
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("\t", "{", "}", "(", ")", ";", ".", ":", "\"", "/", "-", "+", "*", "=", "<", ">", "#").forEach { symbol ->
                            TextButton(onClick = {
                                if (!isEditMode) isEditMode = true
                                val start = fieldValue.selection.min
                                val end = fieldValue.selection.max
                                val insertText = if (symbol == "\t") "    " else symbol
                                val updated = fieldValue.text.replaceRange(start, end, insertText)
                                val cursor = start + insertText.length
                                fieldValue = TextFieldValue(updated, TextRange(cursor))
                                viewModel.updateContent(updated)
                                viewModel.updateCursor(cursor)
                            }) {
                                Text(if (symbol == "\t") "TAB" else symbol, color = EditorWhite, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeNavigationDialog(
    symbols: List<NavSymbolItem>,
    onDismiss: () -> Unit,
    onSelectSymbol: (NavSymbolItem) -> Unit
) {
    var searchExpanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(symbols, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) symbols else symbols.filter {
            it.title.lowercase(Locale.ROOT).contains(q) ||
                it.subtitle.lowercase(Locale.ROOT).contains(q)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 520.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (searchExpanded) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF757575), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color(0xFF111111), fontSize = 14.sp),
                            cursorBrush = SolidColor(Color(0xFF1976D2)),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(stringResource(R.string.search_hint), color = Color(0xFF9E9E9E), fontSize = 13.sp)
                                }
                                inner()
                            }
                        )
                        IconButton(
                            onClick = {
                                query = ""
                                searchExpanded = false
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.navigation_title),
                            color = Color(0xFF111111),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { searchExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint), tint = Color(0xFF333333), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(thickness = 0.6.dp, color = EditorSeparator)

                if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_items_found), color = Color(0xFF757575), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(filtered) { item ->
                            val badgeColor = when (item.kind) {
                                NavSymbolKind.CLASS -> ClassBadgeColor
                                NavSymbolKind.FIELD -> FieldBadgeColor
                                NavSymbolKind.METHOD -> MethodBadgeColor
                            }
                            val badgeLetter = when (item.kind) {
                                NavSymbolKind.CLASS -> "C"
                                NavSymbolKind.FIELD -> "F"
                                NavSymbolKind.METHOD -> "M"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSymbol(item) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeLetter,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = Color(0xFF111111),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.subtitle.isNotEmpty()) {
                                        Text(
                                            text = item.subtitle,
                                            color = Color(0xFF757575),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = ":${item.startLine}",
                                    color = Color(0xFF757575),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = EditorSeparator)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun lineAndColumn(text: String, cursor: Int): String {
    val safeCursor = cursor.coerceIn(0, text.length)
    val beforeCursor = text.substring(0, safeCursor)
    val line = beforeCursor.count { it == '\n' } + 1
    val column = safeCursor - beforeCursor.lastIndexOf('\n')
    return "$line:$column"
}

@Composable
private fun GoToLineDialog(
    maxLine: Int,
    currentLine: Int,
    onDismiss: () -> Unit,
    onGoToLine: (Int) -> Unit
) {
    var lineInput by remember { mutableStateOf(currentLine.toString()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.go_to_line),
                    color = Color(0xFF111111),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.enter_line_number, maxLine),
                    color = EditorGray,
                    fontSize = 12.5.sp
                )
                BasicTextField(
                    value = lineInput,
                    onValueChange = { lineInput = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color(0xFF111111), fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Color(0xFF1976D2)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    decorationBox = { inner ->
                        Column {
                            inner()
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(thickness = 1.5.dp, color = Color(0xFF1976D2))
                        }
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            val parsed = lineInput.toIntOrNull() ?: currentLine
                            onGoToLine(parsed)
                        }
                    ) {
                        Text(stringResource(R.string.ok), color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
