package com.example.mtcore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.mtcore.data.ArscTableResult
import com.example.mtcore.data.BinaryXmlDecompiler
import com.example.mtcore.data.DexClassDef
import com.example.mtcore.data.DexParseSession
import com.example.mtcore.data.DexParser
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val DexToolbarBg = Color(0xFF2B2B2B)
private val DexBlue = Color(0xFF1976D2)
private val ClassCircleBlue = Color(0xFF4285F4)
private val FolderBoxBlack = Color(0xFF262626)
private val DividerLight = Color(0xFFE0E0E0)

private sealed class DexTreeRow {
    data class FolderNode(
        val fullPath: String,
        val name: String,
        val depth: Int,
        val expanded: Boolean,
        val childCount: Int
    ) : DexTreeRow()

    data class ClassNode(
        val dexClass: DexClassDef,
        val depth: Int
    ) : DexTreeRow()
}

@Composable
fun DexEditorScreen(
    dexFiles: List<File>,
    onOpenSmaliClass: (DexClassDef) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var isLoading by remember { mutableStateOf(true) }
    var session by remember { mutableStateOf<DexParseSession?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val expandedFolders = remember { mutableStateOf(setOf<String>()) }
    val historyClasses = remember { mutableStateListOf<DexClassDef>() }
    var searchQuery by remember { mutableStateOf("") }
    var stringFilter by remember { mutableStateOf("") }

    LaunchedEffect(dexFiles) {
        isLoading = true
        val parsed = DexParser.parseDexFiles(dexFiles)
        session = parsed
        // Auto-expand top-level packages if there are only a few
        val topFolders = parsed.classes
            .map { it.packagePath.substringBefore('/') }
            .filter { it.isNotEmpty() }
            .distinct()
        if (topFolders.size == 1) {
            expandedFolders.value = setOf(topFolders.first())
        }
        isLoading = false
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            onExit()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Surface(
                modifier = Modifier
                    .width(290.dp)
                    .fillMaxHeight(),
                color = Color.White,
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DexToolbarBg)
                            .statusBarsPadding()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dex_editor_plus),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.temporary_project) +
                                " (${dexFiles.joinToString(", ") { it.name }})",
                            color = Color(0xFFBDBDBD),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(R.string.tab_history) + " (${historyClasses.size})",
                        color = Color(0xFF757575),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    HorizontalDivider(thickness = 0.6.dp, color = DividerLight)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(historyClasses, key = { it.classDescriptor }) { cls ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { drawerState.close() }
                                        onOpenSmaliClass(cls)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ClassBadge()
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cls.simpleName, color = Color(0xFF111111), fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(cls.packagePath.replace('/', '.'), color = Color(0xFF757575), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                        }
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            // Top Bar (#2B2B2B)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DexToolbarBg)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dex_editor_plus),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.temporary_project),
                        color = Color(0xFFD0D0D0),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more), tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_all)) },
                            onClick = {
                                showOverflowMenu = false
                                Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_as_project)) },
                            onClick = {
                                showOverflowMenu = false
                                Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup)) },
                            onClick = {
                                showOverflowMenu = false
                                Toast.makeText(context, context.getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reset_project)) },
                            onClick = {
                                showOverflowMenu = false
                                expandedFolders.value = emptySet()
                                historyClasses.clear()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.exit)) },
                            onClick = {
                                showOverflowMenu = false
                                onExit()
                            }
                        )
                    }
                }
            }

            // 4 Tabs (EXPLORER, HISTORY, SEARCH, STRINGS)
            val tabTitles = listOf(
                stringResource(R.string.tab_explorer),
                stringResource(R.string.tab_history),
                stringResource(R.string.tab_search),
                stringResource(R.string.tab_strings)
            )
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFF111111),
                indicator = { tabPositions ->
                    if (selectedTab in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 2.5.dp,
                            color = DexBlue
                        )
                    }
                }
            ) {
                tabTitles.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == idx) DexBlue else Color(0xFF444444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
            HorizontalDivider(thickness = 0.8.dp, color = DividerLight)

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = DexBlue, strokeWidth = 3.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.extracting_progress), color = Color(0xFF444444), fontSize = 13.sp)
                    }
                }
            } else {
                val currentSession = session
                when (selectedTab) {
                    0 -> {
                        val rows = remember(currentSession, expandedFolders.value) {
                            buildDexTreeRows(currentSession?.classes.orEmpty(), expandedFolders.value)
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(
                                items = rows,
                                key = { row ->
                                    when (row) {
                                        is DexTreeRow.FolderNode -> "folder:${row.fullPath}"
                                        is DexTreeRow.ClassNode -> "class:${row.dexClass.sourceDexName}:${row.dexClass.classDescriptor}"
                                    }
                                }
                            ) { row ->
                                when (row) {
                                    is DexTreeRow.FolderNode -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val next = expandedFolders.value.toMutableSet()
                                                    if (!next.add(row.fullPath)) {
                                                        // Collapse folder and any open descendants
                                                        next.removeAll { it == row.fullPath || it.startsWith("${row.fullPath}/") }
                                                    }
                                                    expandedFolders.value = next
                                                }
                                                .padding(
                                                    start = (8 + row.depth * 16).dp,
                                                    end = 10.dp,
                                                    top = 6.dp,
                                                    bottom = 6.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (row.expanded) "⌟" else ">",
                                                color = Color(0xFF555555),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(16.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(FolderBoxBlack, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(9.dp))
                                            Text(
                                                text = row.name,
                                                color = Color(0xFF111111),
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                                    }
                                    is DexTreeRow.ClassNode -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (historyClasses.none { it.classDescriptor == row.dexClass.classDescriptor }) {
                                                        historyClasses.add(0, row.dexClass)
                                                    }
                                                    onOpenSmaliClass(row.dexClass)
                                                }
                                                .padding(
                                                    start = (24 + row.depth * 16).dp,
                                                    end = 10.dp,
                                                    top = 6.dp,
                                                    bottom = 6.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ClassBadge()
                                            Spacer(Modifier.width(9.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = row.dexClass.simpleName,
                                                    color = Color(0xFF111111),
                                                    fontSize = 13.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val methodsCount = row.dexClass.directMethods.size + row.dexClass.virtualMethods.size
                                                val fieldsCount = row.dexClass.staticFields.size + row.dexClass.instanceFields.size
                                                Text(
                                                    text = "${row.dexClass.sourceDexName}  •  M:$methodsCount  F:$fieldsCount",
                                                    color = Color(0xFF757575),
                                                    fontSize = 10.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // HISTORY tab
                        if (historyClasses.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_items_found), color = Color(0xFF757575), fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(historyClasses, key = { it.classDescriptor }) { cls ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenSmaliClass(cls) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ClassBadge()
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(cls.simpleName, color = Color(0xFF111111), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            Text(cls.classDescriptor, color = Color(0xFF757575), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                                }
                            }
                        }
                    }

                    2 -> {
                        // SEARCH tab
                        Column(Modifier.fillMaxSize()) {
                            SearchBarRow(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                hint = stringResource(R.string.search_hint)
                            )
                            HorizontalDivider(thickness = 0.8.dp, color = DividerLight)
                            val q = searchQuery.trim().lowercase(Locale.ROOT)
                            val matchedClasses = remember(currentSession, q) {
                                if (q.isEmpty()) {
                                    currentSession?.classes.orEmpty().take(200)
                                } else {
                                    currentSession?.classes.orEmpty().filter { cls ->
                                        cls.cleanPath.lowercase(Locale.ROOT).contains(q) ||
                                            cls.directMethods.any { it.name.lowercase(Locale.ROOT).contains(q) } ||
                                            cls.virtualMethods.any { it.name.lowercase(Locale.ROOT).contains(q) } ||
                                            cls.staticFields.any { it.name.lowercase(Locale.ROOT).contains(q) } ||
                                            cls.instanceFields.any { it.name.lowercase(Locale.ROOT).contains(q) }
                                    }.take(300)
                                }
                            }
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(matchedClasses, key = { "${it.sourceDexName}:${it.classDescriptor}" }) { cls ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (historyClasses.none { it.classDescriptor == cls.classDescriptor }) {
                                                    historyClasses.add(0, cls)
                                                }
                                                onOpenSmaliClass(cls)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ClassBadge()
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(cls.simpleName, color = Color(0xFF111111), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                            Text(cls.classDescriptor, color = Color(0xFF757575), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                                }
                            }
                        }
                    }

                    3 -> {
                        // STRINGS tab
                        Column(Modifier.fillMaxSize()) {
                            SearchBarRow(
                                query = stringFilter,
                                onQueryChange = { stringFilter = it },
                                hint = stringResource(R.string.search_hint)
                            )
                            HorizontalDivider(thickness = 0.8.dp, color = DividerLight)
                            val q = stringFilter.trim().lowercase(Locale.ROOT)
                            val matchedStrings = remember(currentSession, q) {
                                val all = currentSession?.strings.orEmpty()
                                if (q.isEmpty()) all.take(500) else all.filter { it.lowercase(Locale.ROOT).contains(q) }.take(500)
                            }
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(matchedStrings.size) { index ->
                                    val str = matchedStrings[index]
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                copyToClipboard(context, str)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = str,
                                            color = Color(0xFF2E7D32),
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassBadge() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(ClassCircleBlue),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "C",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F7F7))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF757575), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFF111111), fontSize = 13.5.sp),
            cursorBrush = SolidColor(DexBlue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(hint, color = Color(0xFF9E9E9E), fontSize = 13.sp)
                }
                inner()
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color(0xFF757575),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

private fun buildDexTreeRows(
    classes: List<DexClassDef>,
    expandedFolders: Set<String>
): List<DexTreeRow> {
    val folderChildren = HashMap<String, MutableSet<String>>()
    val classChildren = HashMap<String, MutableList<DexClassDef>>()

    for (cls in classes) {
        val pkg = cls.packagePath
        if (pkg.isEmpty()) {
            classChildren.getOrPut("") { mutableListOf() }.add(cls)
        } else {
            val parts = pkg.split('/')
            var currentPath = ""
            for (part in parts) {
                val nextPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                folderChildren.getOrPut(currentPath) { linkedSetOf() }.add(nextPath)
                currentPath = nextPath
            }
            classChildren.getOrPut(pkg) { mutableListOf() }.add(cls)
        }
    }

    val rows = mutableListOf<DexTreeRow>()

    fun traverse(parentPath: String, depth: Int) {
        val subfolders = folderChildren[parentPath]?.sorted() ?: emptyList()
        for (folderPath in subfolders) {
            val folderName = folderPath.substringAfterLast('/')
            val isExpanded = folderPath in expandedFolders
            val count = (folderChildren[folderPath]?.size ?: 0) + (classChildren[folderPath]?.size ?: 0)
            rows += DexTreeRow.FolderNode(
                fullPath = folderPath,
                name = folderName,
                depth = depth,
                expanded = isExpanded,
                childCount = count
            )
            if (isExpanded) {
                traverse(folderPath, depth + 1)
            }
        }
        val directClasses = classChildren[parentPath]?.sortedBy { it.simpleName.lowercase(Locale.ROOT) } ?: emptyList()
        for (cls in directClasses) {
            rows += DexTreeRow.ClassNode(dexClass = cls, depth = depth)
        }
    }

    traverse("", 0)
    return rows
}

@Composable
fun HexViewerDialog(
    file: File,
    onDismiss: () -> Unit
) {
    var hexText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(file.absolutePath) {
        loading = true
        hexText = BinaryXmlDecompiler.formatHexDump(file)
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 560.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.hex_editor_title, file.name),
                    color = Color(0xFF111111),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 0.6.dp, color = DividerLight)
                if (loading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DexBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = hexText,
                            color = Color(0xFF111111),
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                HorizontalDivider(thickness = 0.6.dp, color = DividerLight)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = DexBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StringPoolDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var strings by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(file.absolutePath) {
        loading = true
        strings = BinaryXmlDecompiler.extractStringPoolFromFile(file)
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 560.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.string_pool_title, strings.size),
                    color = Color(0xFF111111),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                SearchBarRow(
                    query = query,
                    onQueryChange = { query = it },
                    hint = stringResource(R.string.search_hint)
                )
                HorizontalDivider(thickness = 0.6.dp, color = DividerLight)
                if (loading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DexBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    val filtered = remember(strings, query) {
                        val q = query.trim().lowercase(Locale.ROOT)
                        if (q.isEmpty()) strings else strings.filter { it.lowercase(Locale.ROOT).contains(q) }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(filtered.size) { index ->
                            val item = filtered[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { copyToClipboard(context, item) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    color = Color(0xFF757575),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(46.dp)
                                )
                                Text(
                                    text = item,
                                    color = Color(0xFF111111),
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = DexBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ArscExplorerDialog(
    file: File,
    modeTitle: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<ArscTableResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(file.absolutePath) {
        loading = true
        result = BinaryXmlDecompiler.parseArscFile(file)
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "$modeTitle - ${result?.packageName.orEmpty().ifEmpty { file.name }}",
                    color = Color(0xFF111111),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                SearchBarRow(
                    query = query,
                    onQueryChange = { query = it },
                    hint = stringResource(R.string.search_hint)
                )
                HorizontalDivider(thickness = 0.6.dp, color = DividerLight)
                if (loading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DexBlue, strokeWidth = 2.5.dp)
                    }
                } else {
                    val entries = result?.entries.orEmpty()
                    val pool = result?.stringPool.orEmpty()
                    val q = query.trim().lowercase(Locale.ROOT)
                    if (entries.isNotEmpty()) {
                        val filtered = remember(entries, q) {
                            if (q.isEmpty()) entries else entries.filter {
                                it.idHex.lowercase(Locale.ROOT).contains(q) ||
                                    it.typeName.lowercase(Locale.ROOT).contains(q) ||
                                    it.keyName.lowercase(Locale.ROOT).contains(q) ||
                                    it.value.lowercase(Locale.ROOT).contains(q)
                            }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(filtered.size) { idx ->
                                val entry = filtered[idx]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            copyToClipboard(context, "${entry.idHex} @${entry.typeName}/${entry.keyName} = ${entry.value}")
                                        }
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.idHex,
                                            color = Color(0xFF1565C0),
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "@${entry.typeName}/${entry.keyName}",
                                            color = Color(0xFFA31515),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (entry.value.isNotEmpty()) {
                                        Text(
                                            text = entry.value,
                                            color = Color(0xFF2E7D32),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                            }
                        }
                    } else {
                        val filteredStrings = remember(pool, q) {
                            if (q.isEmpty()) pool else pool.filter { it.lowercase(Locale.ROOT).contains(q) }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(filteredStrings.size) { idx ->
                                val item = filteredStrings[idx]
                                Text(
                                    text = item,
                                    color = Color(0xFF111111),
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { copyToClipboard(context, item) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = DividerLight)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = DexBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("MT Manager", text))
    Toast.makeText(context, context.getString(R.string.copied_to_clipboard, text.take(40)), Toast.LENGTH_SHORT).show()
}
