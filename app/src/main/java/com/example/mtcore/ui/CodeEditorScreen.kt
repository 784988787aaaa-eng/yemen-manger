package com.example.mtcore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mtcore.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private val EditorToolbar = Color(0xFF2B2B2B)
private val EditorWhite = Color.White
private val LineNumberBackground = Color(0xFFF5F5F5)
private val SymbolBarBackground = Color(0xFFF5F5F5)
private val SymbolBarText = Color(0xFF111111)
private val EditorGray = Color(0xFF757575)
private val EditorSeparator = Color(0xFFE0E0E0)
private val ActiveEditSkyBlue = Color(0xFF64B5F6)
private val MtBlue = Color(0xFF1976D2)
private val MtUnderlineBlue = Color(0xFF2196F3)
private val MtOptionActiveBg = Color(0xFFBBDEFB)

private val ClassBadgeColor = Color(0xFF4285F4)
private val FieldBadgeColor = Color(0xFFF57C00)
private val MethodBadgeColor = Color(0xFFD32F2F)

private val QUICK_SYMBOLS = listOf(
    "TAB", "/", "-", "+", "*", "=", "<", ">", "(", ")", "{", "}", "[", "]", ";", ":", "\"", "'", "_", "&", "|", "!"
)

@Composable
fun CodeEditorScreen(viewModel: MainViewModel, onBack: (Boolean) -> Unit) {
    val state by viewModel.editor.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Dialog & Menu visibility states
    var showExitDialog by remember { mutableStateOf(false) }
    var showNavigationDialog by remember { mutableStateOf(false) }
    var showSmaliToJavaDialog by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showPencilMenu by remember { mutableStateOf(false) }
    var showFileMenuDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showSyntaxDialog by remember { mutableStateOf(false) }
    var showOpenFilesDialog by remember { mutableStateOf(false) }

    // Find & Replace Panel states
    var showFindReplaceBar by remember { mutableStateOf(false) }
    var showReplaceRow by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Editor Settings & View Modes
    var isEditMode by remember(state.file?.absolutePath) { mutableStateOf(false) }
    var readOnlyMode by remember { mutableStateOf(false) }
    var softWrap by remember { mutableStateOf(false) }
    var smoothMode by remember { mutableStateOf(true) }
    var codeCompletionEnabled by remember { mutableStateOf(true) }
    var selectedSyntax by remember { mutableStateOf(SyntaxLanguage.AUTO) }
    var fontSizeSp by remember { mutableFloatStateOf(12.5f) }
    val lineHeightSp = fontSizeSp * 1.45f

    // Session Pinned / Open Files tracking
    val sessionOpenFiles = remember { mutableStateListOf<File>() }
    val pinnedFilePaths = remember { mutableStateListOf<String>() }
    LaunchedEffect(state.file?.absolutePath) {
        val f = state.file
        if (f != null && sessionOpenFiles.none { it.absolutePath == f.absolutePath }) {
            sessionOpenFiles.add(f)
        }
    }
    val isCurrentFilePinned = state.file?.absolutePath?.let { it in pinnedFilePaths } == true

    // Cursor position history for Previous/Next Position navigation
    val cursorHistory = remember { mutableStateListOf<Int>() }
    var cursorHistoryIndex by remember { mutableIntStateOf(-1) }

    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val sharedScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    var isDraggingFastScroll by remember { mutableStateOf(false) }

    fun recordCursorHistory(pos: Int) {
        val clamped = pos.coerceIn(0, fieldValue.text.length)
        if (cursorHistory.isEmpty() || kotlin.math.abs(cursorHistory.last() - clamped) >= 15) {
            while (cursorHistory.size > cursorHistoryIndex + 1 && cursorHistory.isNotEmpty()) {
                cursorHistory.removeAt(cursorHistory.lastIndex)
            }
            cursorHistory.add(clamped)
            if (cursorHistory.size > 50) cursorHistory.removeAt(0)
            cursorHistoryIndex = cursorHistory.lastIndex
        }
    }

    // Compute search match ranges off UI hot path
    val searchRanges by produceState(
        initialValue = emptyList<IntRange>(),
        fieldValue.text,
        findQuery,
        matchCase,
        wholeWord,
        useRegex
    ) {
        if (findQuery.isEmpty()) {
            value = emptyList()
        } else {
            value = withContext(Dispatchers.Default) {
                computeSearchMatches(
                    text = fieldValue.text,
                    query = findQuery,
                    matchCase = matchCase,
                    wholeWord = wholeWord,
                    useRegex = useRegex
                )
            }
        }
    }

    // Non-blocking Syntax Highlighting on Dispatchers.Default for Zero-Lag 3,000+ line rendering
    val highlightedText by produceState(
        initialValue = remember(state.content, state.file?.name) {
            if (state.content.length <= 24_000) {
                SyntaxHighlighter.highlight(state.content, state.file?.name)
            } else {
                AnnotatedString(state.content)
            }
        },
        fieldValue.text,
        state.file?.name,
        selectedSyntax,
        searchRanges,
        currentMatchIndex,
        smoothMode
    ) {
        val raw = fieldValue.text
        val name = state.file?.name
        value = withContext(Dispatchers.Default) {
            SyntaxHighlighter.highlight(
                source = raw,
                fileName = name,
                language = selectedSyntax,
                searchRanges = searchRanges,
                activeSearchIndex = currentMatchIndex.coerceIn(-1, searchRanges.lastIndex),
                smoothMode = smoothMode
            )
        }
    }

    val visualTransformation = remember(highlightedText, state.file?.name, selectedSyntax) {
        SyntaxHighlighter.fromPreHighlighted(
            preHighlighted = highlightedText,
            fileName = state.file?.name,
            language = selectedSyntax
        )
    }

    val navSymbols by produceState(
        initialValue = emptyList<NavSymbolItem>(),
        fieldValue.text,
        state.file?.name
    ) {
        val raw = fieldValue.text
        val name = state.file?.name
        value = withContext(Dispatchers.Default) {
            SyntaxHighlighter.parseNavigationSymbols(raw, name)
        }
    }

    // Current word prefix & code completions
    val currentWordPrefix = remember(fieldValue.text, fieldValue.selection) {
        if (!fieldValue.selection.collapsed) ""
        else {
            val cursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
            var start = cursor
            while (start > 0) {
                val c = fieldValue.text[start - 1]
                if (c.isLetterOrDigit() || c == '_' || c == '.' || c == '-') start-- else break
            }
            fieldValue.text.substring(start, cursor)
        }
    }

    val completionSuggestions by produceState(
        initialValue = emptyList<String>(),
        fieldValue.text,
        currentWordPrefix,
        codeCompletionEnabled,
        isEditMode
    ) {
        if (!codeCompletionEnabled || !isEditMode || currentWordPrefix.length < 2) {
            value = emptyList()
        } else {
            val raw = fieldValue.text
            val prefix = currentWordPrefix
            value = withContext(Dispatchers.Default) {
                SyntaxHighlighter.extractCompletionWords(raw, prefix)
            }
        }
    }

    LaunchedEffect(state.file, state.content, state.isLoading) {
        if (!state.isLoading && state.error == null && fieldValue.text != state.content) {
            val initCursor = state.cursor.coerceIn(0, state.content.length)
            fieldValue = TextFieldValue(
                text = state.content,
                selection = TextRange(initCursor)
            )
            cursorHistory.clear()
            cursorHistory.add(initCursor)
            cursorHistoryIndex = 0
        }
    }

    LaunchedEffect(isEditMode, readOnlyMode) {
        if (isEditMode && !readOnlyMode) {
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
        if (showFindReplaceBar) {
            showFindReplaceBar = false
        } else if (state.isDirty) {
            showExitDialog = true
        } else {
            onBack(state.hasSavedChangesInSession)
        }
    }

    val cursorLine = remember(fieldValue.text, fieldValue.selection.start) {
        val safeCursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
        var lines = 1
        for (i in 0 until safeCursor) {
            if (fieldValue.text[i] == '\n') lines++
        }
        lines
    }

    val totalLinesCount = remember(fieldValue.text) {
        var lines = 1
        val t = fieldValue.text
        for (i in t.indices) {
            if (t[i] == '\n') lines++
        }
        lines
    }

    // Single AnnotatedString for the entire Line Gutter (Zero-Lag even at 5,000+ lines)
    val gutterAnnotatedString = remember(totalLinesCount, cursorLine) {
        buildAnnotatedString {
            for (line in 1..totalLinesCount) {
                val numStr = line.toString()
                if (line == cursorLine) {
                    pushStyle(SpanStyle(color = MtBlue, fontWeight = FontWeight.Bold))
                    append(numStr)
                    pop()
                } else {
                    append(numStr)
                }
                if (line < totalLinesCount) {
                    append('\n')
                }
            }
        }
    }

    val gutterWidth = remember(totalLinesCount, fontSizeSp) {
        val digits = totalLinesCount.toString().length.coerceAtLeast(2)
        val scale = (fontSizeSp / 12.5f).coerceIn(0.75f, 1.8f)
        ((digits * 8.5f + 16f) * scale).dp.coerceIn(36.dp, 68.dp)
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

    fun scrollToLine(targetLine: Int, animate: Boolean = true) {
        val clamped = targetLine.coerceIn(1, totalLinesCount)
        scope.launch {
            val linePx = with(density) { ((clamped - 1).coerceAtLeast(0) * lineHeightSp).dp.toPx().toInt() }
            if (animate) {
                sharedScrollState.animateScrollTo(linePx)
            } else {
                sharedScrollState.scrollTo(linePx)
            }
        }
    }

    fun jumpToRange(range: IntRange) {
        val safeStart = range.first.coerceIn(0, fieldValue.text.length)
        val safeEnd = (range.last + 1).coerceIn(safeStart, fieldValue.text.length)
        fieldValue = fieldValue.copy(selection = TextRange(safeStart, safeEnd))
        viewModel.updateCursor(safeStart)
        recordCursorHistory(safeStart)
        var targetLine = 1
        for (i in 0 until safeStart) {
            if (fieldValue.text[i] == '\n') targetLine++
        }
        scrollToLine(targetLine, animate = true)
    }

    fun applyContentUpdate(newText: String, newSelection: TextRange) {
        if (readOnlyMode) {
            Toast.makeText(context, context.getString(R.string.menu_read_only), Toast.LENGTH_SHORT).show()
            return
        }
        fieldValue = TextFieldValue(newText, newSelection)
        viewModel.updateContent(newText)
        viewModel.updateCursor(newSelection.start)
        recordCursorHistory(newSelection.start)
    }

    // Line-level manipulation helpers for the Pencil (Edit) menu
    fun currentLineBounds(): Pair<Int, Int> {
        val text = fieldValue.text
        val pos = fieldValue.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', pos).let { if (it == -1) text.length else it }
        return lineStart to lineEnd
    }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("MTCode", text))
    }

    fun readFromClipboard(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }

    fun performLineAction(action: String) {
        if (!isEditMode && !readOnlyMode) isEditMode = true
        val text = fieldValue.text
        val (lineStart, lineEnd) = currentLineBounds()
        val lineContent = text.substring(lineStart, lineEnd)
        val hasSelection = !fieldValue.selection.collapsed
        val selStart = fieldValue.selection.min.coerceIn(0, text.length)
        val selEnd = fieldValue.selection.max.coerceIn(selStart, text.length)

        when (action) {
            "copy_line" -> {
                val target = if (hasSelection) text.substring(selStart, selEnd) else lineContent
                copyToClipboard(target)
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
            "cut_line" -> {
                copyToClipboard(lineContent)
                val removeEnd = if (lineEnd < text.length) lineEnd + 1 else lineEnd
                val removeStart = if (lineEnd == text.length && lineStart > 0) lineStart - 1 else lineStart
                val updated = text.removeRange(removeStart, removeEnd)
                applyContentUpdate(updated, TextRange(removeStart.coerceAtMost(updated.length)))
            }
            "delete_line" -> {
                val removeEnd = if (lineEnd < text.length) lineEnd + 1 else lineEnd
                val removeStart = if (lineEnd == text.length && lineStart > 0) lineStart - 1 else lineStart
                val updated = text.removeRange(removeStart, removeEnd)
                applyContentUpdate(updated, TextRange(removeStart.coerceAtMost(updated.length)))
            }
            "empty_line" -> {
                val updated = text.replaceRange(lineStart, lineEnd, "")
                applyContentUpdate(updated, TextRange(lineStart))
            }
            "replace_line" -> {
                val clip = readFromClipboard()
                val updated = text.replaceRange(lineStart, lineEnd, clip)
                applyContentUpdate(updated, TextRange(lineStart + clip.length))
            }
            "duplicate_line" -> {
                val insert = "\n$lineContent"
                val updated = text.replaceRange(lineEnd, lineEnd, insert)
                applyContentUpdate(updated, TextRange(lineEnd + insert.length))
            }
            "uppercase" -> {
                if (hasSelection) {
                    val rep = text.substring(selStart, selEnd).uppercase(Locale.ROOT)
                    applyContentUpdate(text.replaceRange(selStart, selEnd, rep), TextRange(selStart, selStart + rep.length))
                } else {
                    val rep = lineContent.uppercase(Locale.ROOT)
                    applyContentUpdate(text.replaceRange(lineStart, lineEnd, rep), TextRange(lineStart, lineStart + rep.length))
                }
            }
            "lowercase" -> {
                if (hasSelection) {
                    val rep = text.substring(selStart, selEnd).lowercase(Locale.ROOT)
                    applyContentUpdate(text.replaceRange(selStart, selEnd, rep), TextRange(selStart, selStart + rep.length))
                } else {
                    val rep = lineContent.lowercase(Locale.ROOT)
                    applyContentUpdate(text.replaceRange(lineStart, lineEnd, rep), TextRange(lineStart, lineStart + rep.length))
                }
            }
            "indent" -> {
                val rep = "    $lineContent"
                applyContentUpdate(text.replaceRange(lineStart, lineEnd, rep), TextRange(lineStart + 4))
            }
            "unindent" -> {
                val rep = when {
                    lineContent.startsWith("    ") -> lineContent.removePrefix("    ")
                    lineContent.startsWith("\t") -> lineContent.removePrefix("\t")
                    else -> lineContent.trimStart()
                }
                applyContentUpdate(text.replaceRange(lineStart, lineEnd, rep), TextRange(lineStart))
            }
        }
    }

    fun formatCurrentCode() {
        val ext = state.file?.extension.orEmpty()
        val formatted = CodeFormatter.formatCode(fieldValue.text, ext)
        applyContentUpdate(formatted, TextRange(0))
        Toast.makeText(context, context.getString(R.string.code_formatted_toast), Toast.LENGTH_SHORT).show()
    }

    // Dialogs
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
                recordCursorHistory(targetCursor)
                scrollToLine(symbol.startLine, animate = true)
            }
        )
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
                recordCursorHistory(offset)
                scrollToLine(clamped, animate = true)
            }
        )
    }

    if (showSyntaxDialog) {
        Dialog(onDismissRequest = { showSyntaxDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.menu_syntax),
                        color = Color(0xFF111111),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    SyntaxLanguage.entries.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSyntax = lang
                                    showSyntaxDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSyntax == lang,
                                onClick = {
                                    selectedSyntax = lang
                                    showSyntaxDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = lang.label, color = Color(0xFF111111), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (showFileMenuDialog) {
        val currentFile = state.file
        Dialog(onDismissRequest = { showFileMenuDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = currentFile?.name ?: stringResource(R.string.menu_file),
                        color = Color(0xFF111111),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentFile?.absolutePath.orEmpty(),
                        color = EditorGray,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = "Lines: $totalLinesCount | Chars: ${fieldValue.text.length} | Encoding: UTF-8",
                        color = EditorGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    HorizontalDivider(color = EditorSeparator)
                    TextButton(
                        onClick = {
                            showFileMenuDialog = false
                            viewModel.updateContent(fieldValue.text)
                            viewModel.saveFile { saved, _ ->
                                if (saved) Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save), color = MtBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            showFileMenuDialog = false
                            showSaveAsDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save_as), color = MtBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            showFileMenuDialog = false
                            if (state.isDirty) showExitDialog = true else onBack(state.hasSavedChangesInSession)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.menu_close_file), color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showSaveAsDialog) {
        var newName by remember(state.file?.name) { mutableStateOf(state.file?.name ?: "copy.txt") }
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text(stringResource(R.string.save_as)) },
            text = {
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF111111), fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(MtBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                        .padding(10.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parent = state.file?.parentFile ?: viewModel.activeDirectory()
                    val dest = File(parent, newName.trim().ifEmpty { "output.txt" })
                    runCatching {
                        dest.writeText(fieldValue.text, Charsets.UTF_8)
                        viewModel.markFileModified(dest)
                        viewModel.openFile(dest)
                    }.onSuccess {
                        Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                        showSaveAsDialog = false
                    }.onFailure {
                        Toast.makeText(context, it.message ?: "Error", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showOpenFilesDialog) {
        Dialog(onDismissRequest = { showOpenFilesDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.open_files_drawer_title),
                        color = Color(0xFF111111),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = EditorSeparator)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(sessionOpenFiles) { openFile ->
                            val isCurrent = openFile.absolutePath == state.file?.absolutePath
                            val isPinned = openFile.absolutePath in pinnedFilePaths
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOpenFilesDialog = false
                                        if (!isCurrent) {
                                            viewModel.openFile(openFile)
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (isPinned) MtBlue else Color(0xFFBDBDBD),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = openFile.name,
                                        color = if (isCurrent) MtBlue else Color(0xFF111111),
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = openFile.parent.orEmpty(),
                                        color = EditorGray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = EditorSeparator)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            showOpenFilesDialog = false
                            if (state.isDirty) showExitDialog = true else onBack(state.hasSavedChangesInSession)
                        }) {
                            Text(stringResource(R.string.menu_close_file), color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showOpenFilesDialog = false }) {
                            Text(stringResource(R.string.close), color = MtBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(EditorWhite)) {
        // MT Manager Top Toolbar (#2B2B2B) - Forced LTR so icons & title never flip in Arabic
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(EditorToolbar)
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Drawer / Open Files icon (☰)
                IconButton(
                    onClick = { showOpenFilesDialog = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.open_files_drawer_title),
                        tint = EditorWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 2. File Name + Line:Col (SelectionLength) UTF-8
                val selLen = fieldValue.selection.length
                val posLabel = lineAndColumn(fieldValue.text, fieldValue.selection.start)
                val subtitleLabel = if (selLen > 0) {
                    "$posLabel ($selLen) UTF-8"
                } else {
                    "$posLabel $activeSymbolRangeText"
                }

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
                        subtitleLabel,
                        color = Color(0xFFD6D6D6),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 3. PushPin (Pin File for session)
                IconButton(
                    onClick = {
                        val path = state.file?.absolutePath ?: return@IconButton
                        if (path in pinnedFilePaths) {
                            pinnedFilePaths.remove(path)
                        } else {
                            pinnedFilePaths.add(path)
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pin",
                        tint = if (isCurrentFilePinned) ActiveEditSkyBlue else EditorWhite,
                        modifier = Modifier.size(18.dp)
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

                // 6. Save
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

                // 7. Edit Pencil Icon (Activates Edit Mode + Opens MT Manager Line Actions Menu)
                Box {
                    IconButton(
                        onClick = {
                            if (!isEditMode && !readOnlyMode) {
                                isEditMode = true
                            }
                            showPencilMenu = true
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = if (isEditMode && !readOnlyMode) ActiveEditSkyBlue else EditorWhite,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showPencilMenu,
                        onDismissRequest = { showPencilMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.line_copy)) },
                            onClick = { showPencilMenu = false; performLineAction("copy_line") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.line_cut)) },
                            onClick = { showPencilMenu = false; performLineAction("cut_line") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.line_delete)) },
                            onClick = { showPencilMenu = false; performLineAction("delete_line") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.line_empty)) },
                            onClick = { showPencilMenu = false; performLineAction("empty_line") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.line_replace)) },
                            onClick = { showPencilMenu = false; performLineAction("replace_line") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.line_duplicate)) },
                            onClick = { showPencilMenu = false; performLineAction("duplicate_line") }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.convert_uppercase)) },
                            onClick = { showPencilMenu = false; performLineAction("uppercase") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.convert_lowercase)) },
                            onClick = { showPencilMenu = false; performLineAction("lowercase") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.increase_indent)) },
                            onClick = { showPencilMenu = false; performLineAction("indent") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.decrease_indent)) },
                            onClick = { showPencilMenu = false; performLineAction("unindent") }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.decompile_to_java)) },
                            onClick = {
                                showPencilMenu = false
                                showSmaliToJavaDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.format_prettify_code)) },
                            onClick = {
                                showPencilMenu = false
                                formatCurrentCode()
                            }
                        )
                    }
                }

                // 8. Overflow Menu (⋮) - Full 12 MT Manager Editor Options
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
                        // 1. File
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_file)) },
                            onClick = {
                                showOverflowMenu = false
                                showFileMenuDialog = true
                            }
                        )
                        // 2. Search
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_search)) },
                            onClick = {
                                showOverflowMenu = false
                                showFindReplaceBar = true
                            }
                        )
                        // 3. Syntax
                        DropdownMenuItem(
                            text = { Text("${stringResource(R.string.menu_syntax)} (${selectedSyntax.label})") },
                            onClick = {
                                showOverflowMenu = false
                                showSyntaxDialog = true
                            }
                        )
                        // 4. Previous position
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_prev_position)) },
                            enabled = cursorHistoryIndex > 0,
                            onClick = {
                                showOverflowMenu = false
                                if (cursorHistoryIndex > 0) {
                                    cursorHistoryIndex--
                                    val pos = cursorHistory[cursorHistoryIndex].coerceIn(0, fieldValue.text.length)
                                    fieldValue = fieldValue.copy(selection = TextRange(pos))
                                    val line = fieldValue.text.substring(0, pos).count { it == '\n' } + 1
                                    scrollToLine(line, animate = true)
                                }
                            }
                        )
                        // 5. Next position
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_next_position)) },
                            enabled = cursorHistoryIndex < cursorHistory.lastIndex,
                            onClick = {
                                showOverflowMenu = false
                                if (cursorHistoryIndex < cursorHistory.lastIndex) {
                                    cursorHistoryIndex++
                                    val pos = cursorHistory[cursorHistoryIndex].coerceIn(0, fieldValue.text.length)
                                    fieldValue = fieldValue.copy(selection = TextRange(pos))
                                    val line = fieldValue.text.substring(0, pos).count { it == '\n' } + 1
                                    scrollToLine(line, animate = true)
                                }
                            }
                        )
                        // 6. Jump to line
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_jump_to_line)) },
                            onClick = {
                                showOverflowMenu = false
                                showGoToLineDialog = true
                            }
                        )
                        // 7. Soft wrap (with Checkbox)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_soft_wrap)) },
                            trailingIcon = {
                                Checkbox(checked = softWrap, onCheckedChange = null)
                            },
                            onClick = {
                                softWrap = !softWrap
                                showOverflowMenu = false
                            }
                        )
                        // 8. Read-only mode (with Checkbox)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_read_only)) },
                            trailingIcon = {
                                Checkbox(checked = readOnlyMode, onCheckedChange = null)
                            },
                            onClick = {
                                readOnlyMode = !readOnlyMode
                                if (readOnlyMode) isEditMode = false
                                showOverflowMenu = false
                            }
                        )
                        // 9. Smooth mode (with Checkbox)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_smooth_mode)) },
                            trailingIcon = {
                                Checkbox(checked = smoothMode, onCheckedChange = null)
                            },
                            onClick = {
                                smoothMode = !smoothMode
                                showOverflowMenu = false
                            }
                        )
                        // 10. Code completion (with Checkbox)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_code_completion)) },
                            trailingIcon = {
                                Checkbox(checked = codeCompletionEnabled, onCheckedChange = null)
                            },
                            onClick = {
                                codeCompletionEnabled = !codeCompletionEnabled
                                showOverflowMenu = false
                            }
                        )
                        // 11. Navigation (Compass structure)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.navigation_title)) },
                            onClick = {
                                showOverflowMenu = false
                                showNavigationDialog = true
                            }
                        )
                        // 12. Close file
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_close_file)) },
                            onClick = {
                                showOverflowMenu = false
                                if (state.isDirty) showExitDialog = true else onBack(state.hasSavedChangesInSession)
                            }
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            Text(stringResource(R.string.loading), Modifier.padding(12.dp), color = Color.Black)
        } else if (state.error != null) {
            Text(state.error.orEmpty(), Modifier.padding(12.dp), color = Color.Red)
        } else {
            // Force LTR for line gutter, code body, fast scrollbar, Find & Replace, and Symbol Bar
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    // Main Editor Area with Pinch-to-Zoom + Draggable Fast Scrollbar
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressedCount = event.changes.count { it.pressed }
                                        if (pressedCount >= 2) {
                                            val zoom = event.calculateZoom()
                                            if (zoom != 1f && zoom > 0f) {
                                                fontSizeSp = (fontSizeSp * zoom).coerceIn(7f, 26f)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    ) {
                        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                        Row(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(sharedScrollState)
                        ) {
                            // 1. Single-Text Line Gutter (Zero-Lag for 3,500+ lines)
                            Box(
                                Modifier
                                    .width(gutterWidth)
                                    .background(LineNumberBackground)
                                    .drawBehind {
                                        drawLine(
                                            color = EditorSeparator,
                                            start = Offset(size.width - 1.dp.toPx(), 0f),
                                            end = Offset(size.width - 1.dp.toPx(), size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    .padding(top = 8.dp, bottom = 8.dp, end = 6.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Text(
                                    text = gutterAnnotatedString,
                                    color = EditorGray,
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = lineHeightSp.sp,
                                    fontFamily = FontFamily.Monospace,
                                    softWrap = false
                                )
                            }

                            // 2. Code Body (supports Horizontal Scroll when softWrap == false, or wraps when softWrap == true)
                            val codeContainerModifier = if (softWrap) {
                                Modifier.weight(1f).fillMaxWidth()
                            } else {
                                Modifier.weight(1f).fillMaxWidth().horizontalScroll(horizontalScrollState)
                            }

                            if (!isEditMode || readOnlyMode) {
                                Box(
                                    modifier = codeContainerModifier
                                        .clickable {
                                            if (!readOnlyMode) isEditMode = true
                                        }
                                        .padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
                                ) {
                                    Text(
                                        text = highlightedText,
                                        style = TextStyle(
                                            color = Color(0xFF111111),
                                            fontSize = fontSizeSp.sp,
                                            lineHeight = lineHeightSp.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        softWrap = softWrap,
                                        modifier = if (softWrap) Modifier.fillMaxWidth() else Modifier.width(2400.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = codeContainerModifier
                                        .padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
                                ) {
                                    BasicTextField(
                                        value = fieldValue,
                                        onValueChange = { value ->
                                            fieldValue = value
                                            viewModel.updateContent(value.text)
                                            viewModel.updateCursor(value.selection.start)
                                            recordCursorHistory(value.selection.start)
                                        },
                                        visualTransformation = visualTransformation,
                                        cursorBrush = SolidColor(MtBlue),
                                        modifier = (if (softWrap) Modifier.fillMaxWidth() else Modifier.width(2400.dp))
                                            .focusRequester(focusRequester),
                                        textStyle = TextStyle(
                                            color = Color(0xFF111111),
                                            fontSize = fontSizeSp.sp,
                                            lineHeight = lineHeightSp.sp,
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
                        }

                        // 3. Draggable Fast Scrollbar Thumb & Floating Line Bubble on Right Edge
                        val maxScroll = sharedScrollState.maxValue
                        if (maxScroll > 0) {
                            val thumbHeightDp = 42.dp
                            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
                            val trackRangePx = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)
                            val scrollFraction = (sharedScrollState.value.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
                            val thumbOffsetY = (scrollFraction * trackRangePx).roundToInt()
                            val estimatedVisibleLine = ((scrollFraction * (totalLinesCount - 1)).roundToInt() + 1).coerceIn(1, totalLinesCount)

                            if (isDraggingFastScroll) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EditorToolbar.copy(alpha = 0.92f),
                                    tonalElevation = 6.dp,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset {
                                            IntOffset(
                                                x = with(density) { (-28).dp.roundToPx() },
                                                y = (thumbOffsetY - with(density) { 8.dp.roundToPx() }).coerceAtLeast(8)
                                            )
                                        }
                                ) {
                                    Text(
                                        text = "Line $estimatedVisibleLine / $totalLinesCount",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .fillMaxHeight()
                                    .width(20.dp)
                                    .pointerInput(maxScroll, trackRangePx) {
                                        detectVerticalDragGestures(
                                            onDragStart = { offset ->
                                                isDraggingFastScroll = true
                                                val frac = ((offset.y - thumbHeightPx / 2f) / trackRangePx).coerceIn(0f, 1f)
                                                scope.launch {
                                                    sharedScrollState.scrollTo((frac * maxScroll).roundToInt())
                                                }
                                            },
                                            onDragEnd = { isDraggingFastScroll = false },
                                            onDragCancel = { isDraggingFastScroll = false },
                                            onVerticalDrag = { change, _ ->
                                                change.consume()
                                                val frac = ((change.position.y - thumbHeightPx / 2f) / trackRangePx).coerceIn(0f, 1f)
                                                scope.launch {
                                                    sharedScrollState.scrollTo((frac * maxScroll).roundToInt())
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(0, thumbOffsetY) }
                                        .align(Alignment.TopEnd)
                                        .padding(end = 2.dp)
                                        .width(if (isDraggingFastScroll) 8.dp else 5.dp)
                                        .height(thumbHeightDp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isDraggingFastScroll) MtBlue else Color(0xFF90A4AE).copy(alpha = 0.78f))
                                )
                            }
                        }
                    }

                    // 4. Bottom Find & Replace Panel (MT Manager Style above Symbol Bar)
                    if (showFindReplaceBar) {
                        HorizontalDivider(thickness = 1.dp, color = EditorSeparator)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Row 1: Results: X + [Cc] [W] [.*] toggles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.search_results_count, searchRanges.size),
                                    color = EditorGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                SearchOptionChip(
                                    label = "Cc",
                                    active = matchCase,
                                    onClick = { matchCase = !matchCase; currentMatchIndex = 0 }
                                )
                                Spacer(Modifier.width(6.dp))
                                SearchOptionChip(
                                    label = "W",
                                    active = wholeWord,
                                    onClick = { wholeWord = !wholeWord; currentMatchIndex = 0 }
                                )
                                Spacer(Modifier.width(6.dp))
                                SearchOptionChip(
                                    label = ".*",
                                    active = useRegex,
                                    onClick = { useRegex = !useRegex; currentMatchIndex = 0 }
                                )
                            }

                            // Row 2: Find input with #2196F3 underline + expand arrow for Replace
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = findQuery,
                                    onValueChange = {
                                        findQuery = it
                                        currentMatchIndex = 0
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color(0xFF111111),
                                        fontSize = 13.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(MtBlue),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        Column {
                                            Box(Modifier.padding(vertical = 4.dp)) {
                                                if (findQuery.isEmpty()) {
                                                    Text(stringResource(R.string.find_hint), color = EditorGray, fontSize = 13.sp)
                                                }
                                                inner()
                                            }
                                            HorizontalDivider(thickness = 1.5.dp, color = MtUnderlineBlue)
                                        }
                                    }
                                )
                                IconButton(
                                    onClick = { showReplaceRow = !showReplaceRow },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showReplaceRow) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle Replace",
                                        tint = EditorGray
                                    )
                                }
                            }

                            if (showReplaceRow) {
                                BasicTextField(
                                    value = replaceQuery,
                                    onValueChange = { replaceQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color(0xFF111111),
                                        fontSize = 13.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(MtBlue),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        Column {
                                            Box(Modifier.padding(vertical = 4.dp)) {
                                                if (replaceQuery.isEmpty()) {
                                                    Text(stringResource(R.string.replace_hint), color = EditorGray, fontSize = 13.sp)
                                                }
                                                inner()
                                            }
                                            HorizontalDivider(thickness = 1.5.dp, color = MtUnderlineBlue)
                                        }
                                    }
                                )
                            }

                            // Row 3: PREV | NEXT | REP | ALL | X
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        if (searchRanges.isNotEmpty()) {
                                            currentMatchIndex = (currentMatchIndex - 1 + searchRanges.size) % searchRanges.size
                                            jumpToRange(searchRanges[currentMatchIndex])
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.btn_prev), color = MtBlue, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = {
                                        if (searchRanges.isNotEmpty()) {
                                            currentMatchIndex = (currentMatchIndex + 1) % searchRanges.size
                                            jumpToRange(searchRanges[currentMatchIndex])
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.btn_next), color = MtBlue, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = {
                                        if (!showReplaceRow) {
                                            showReplaceRow = true
                                        } else if (searchRanges.isNotEmpty()) {
                                            val idx = currentMatchIndex.coerceIn(0, searchRanges.lastIndex)
                                            val range = searchRanges[idx]
                                            val updated = fieldValue.text.replaceRange(range.first, range.last + 1, replaceQuery)
                                            applyContentUpdate(updated, TextRange(range.first + replaceQuery.length))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.btn_rep), color = MtBlue, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = {
                                        if (!showReplaceRow) {
                                            showReplaceRow = true
                                        } else if (searchRanges.isNotEmpty()) {
                                            val sb = StringBuilder(fieldValue.text)
                                            for (i in searchRanges.indices.reversed()) {
                                                val r = searchRanges[i]
                                                sb.replace(r.first, r.last + 1, replaceQuery)
                                            }
                                            val updated = sb.toString()
                                            applyContentUpdate(updated, TextRange(0))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.btn_all), color = MtBlue, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { showFindReplaceBar = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = MtBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // 5. Optional Live Code Completion Strip
                    if (completionSuggestions.isNotEmpty()) {
                        HorizontalDivider(thickness = 0.6.dp, color = EditorSeparator)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE3F2FD))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            completionSuggestions.forEach { word ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.clickable {
                                        val cursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
                                        val replaceStart = (cursor - currentWordPrefix.length).coerceAtLeast(0)
                                        val updated = fieldValue.text.replaceRange(replaceStart, cursor, word)
                                        applyContentUpdate(updated, TextRange(replaceStart + word.length))
                                    }
                                ) {
                                    Text(
                                        text = word,
                                        color = MtBlue,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Bottom Horizontally Scrollable Quick Symbol Bar (#F5F5F5 bg, #111111 text)
                    HorizontalDivider(thickness = 0.8.dp, color = EditorSeparator)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(SymbolBarBackground)
                            .horizontalScroll(rememberScrollState())
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QUICK_SYMBOLS.forEach { symbol ->
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        if (readOnlyMode) return@clickable
                                        if (!isEditMode) isEditMode = true
                                        val start = fieldValue.selection.min.coerceIn(0, fieldValue.text.length)
                                        val end = fieldValue.selection.max.coerceIn(start, fieldValue.text.length)
                                        val insertText = if (symbol == "TAB") "    " else symbol
                                        val updated = fieldValue.text.replaceRange(start, end, insertText)
                                        val cursor = start + insertText.length
                                        applyContentUpdate(updated, TextRange(cursor))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = symbol,
                                    color = SymbolBarText,
                                    fontSize = 13.5.sp,
                                    fontFamily = FontFamily.Monospace,
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

@Composable
private fun SearchOptionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (active) MtOptionActiveBg else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (active) MtBlue else EditorGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

private fun computeSearchMatches(
    text: String,
    query: String,
    matchCase: Boolean,
    wholeWord: Boolean,
    useRegex: Boolean
): List<IntRange> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val results = ArrayList<IntRange>()
    if (useRegex || wholeWord) {
        val patternStr = if (useRegex) {
            if (wholeWord) "\\b(?:$query)\\b" else query
        } else {
            "\\b${Regex.escape(query)}\\b"
        }
        val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val regex = runCatching { Regex(patternStr, options) }.getOrNull() ?: return emptyList()
        for (m in regex.findAll(text)) {
            if (!m.range.isEmpty()) {
                results.add(m.range)
                if (results.size >= 2000) break
            }
        }
    } else {
        var startIndex = 0
        val qLen = query.length
        while (startIndex <= text.length - qLen) {
            val found = text.indexOf(query, startIndex, ignoreCase = !matchCase)
            if (found < 0) break
            results.add(found until (found + qLen))
            if (results.size >= 2000) break
            startIndex = found + qLen.coerceAtLeast(1)
        }
    }
    return results
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
                            cursorBrush = SolidColor(MtBlue),
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
                        Text(stringResource(R.string.close), color = MtBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun lineAndColumn(text: String, cursor: Int): String {
    val safeCursor = cursor.coerceIn(0, text.length)
    var line = 1
    var lastNewline = -1
    for (i in 0 until safeCursor) {
        if (text[i] == '\n') {
            line++
            lastNewline = i
        }
    }
    val column = safeCursor - lastNewline
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
                    cursorBrush = SolidColor(MtBlue),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    decorationBox = { inner ->
                        Column {
                            inner()
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(thickness = 1.5.dp, color = MtBlue)
                        }
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = MtBlue, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            val parsed = lineInput.toIntOrNull() ?: currentLine
                            onGoToLine(parsed)
                        }
                    ) {
                        Text(stringResource(R.string.ok), color = MtBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
