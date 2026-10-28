package com.example.mtcore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mtcore.data.AppRepository
import com.example.mtcore.data.BinaryXmlDecompiler
import com.example.mtcore.data.CompressionLevel
import com.example.mtcore.data.DexClassDef
import com.example.mtcore.data.DexParser
import com.example.mtcore.data.DirectoryStats
import com.example.mtcore.data.FileProperties
import com.example.mtcore.data.FileRepository
import com.example.mtcore.data.InstalledAppInfo
import com.example.mtcore.domain.FileItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

data class ExplorerState(
    val leftDirectory: File = FileRepository.ROOT_DIRECTORY,
    val rightDirectory: File = FileRepository.ROOT_DIRECTORY,
    val leftFiles: List<FileItem> = emptyList(),
    val rightFiles: List<FileItem> = emptyList(),
    val activePane: Int = 0,
    val stats: DirectoryStats = DirectoryStats(0, 0, 0, 0),
    val leftSelected: Set<String> = emptySet(),
    val rightSelected: Set<String> = emptySet(),
    val error: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
) {
    val activeSelected: Set<String>
        get() = if (activePane == 0) leftSelected else rightSelected
}

data class EditorState(
    val file: File? = null,
    val content: String = "",
    val savedContent: String = "",
    val cursor: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSavedChangesInSession: Boolean = false
) {
    val isDirty: Boolean
        get() = content != savedContent
}

class MainViewModel(val appCacheDir: File) : ViewModel() {
    private val cacheDir: File = appCacheDir
    private val repository = FileRepository(cacheDir)

    private data class History(
        val back: ArrayDeque<File> = ArrayDeque(),
        val forward: ArrayDeque<File> = ArrayDeque()
    )

    private val histories = arrayOf(History(), History())
    private val modifiedPaths = linkedSetOf<String>()
    private val decompiledOriginMap = HashMap<String, File>()
    private val _explorer = MutableStateFlow(ExplorerState())
    val explorer = _explorer.asStateFlow()
    private val _editor = MutableStateFlow(EditorState())
    val editor = _editor.asStateFlow()
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var lastRecordedContent = ""

    init {
        refreshAll()
    }

    fun dispatchFile(file: File): com.example.mtcore.data.DispatchedFileType =
        com.example.mtcore.data.FileDispatcher.inspect(file)

    fun isTextFile(file: File): Boolean = repository.isTextFile(file)
    fun isApkFile(file: File): Boolean = file.isFile && file.extension.equals("apk", true)
    fun isDexFile(file: File): Boolean =
        file.isFile && (file.extension.equals("dex", true) || dispatchFile(file) == com.example.mtcore.data.DispatchedFileType.DEX)
    fun isArscFile(file: File): Boolean =
        file.isFile && (file.extension.equals("arsc", true) || dispatchFile(file) == com.example.mtcore.data.DispatchedFileType.ARSC)
    fun isXmlFile(file: File): Boolean = file.isFile && file.extension.equals("xml", true)
    fun isBinaryXmlFile(file: File): Boolean =
        file.isFile && dispatchFile(file) == com.example.mtcore.data.DispatchedFileType.BINARY_XML
    fun isImageFile(file: File): Boolean =
        file.isFile && dispatchFile(file) == com.example.mtcore.data.DispatchedFileType.IMAGE
    fun isFontFile(file: File): Boolean =
        file.isFile && dispatchFile(file) == com.example.mtcore.data.DispatchedFileType.FONT
    fun isBinaryOrNativeFile(file: File): Boolean =
        file.isFile && dispatchFile(file) == com.example.mtcore.data.DispatchedFileType.BINARY_HEX
    fun isArchiveFile(file: File): Boolean =
        file.isFile && file.extension.lowercase(Locale.ROOT) in setOf("zip", "apk", "apks", "xapk", "apkm", "jar", "aab")
    fun isArchivePath(file: File): Boolean = repository.isArchivePath(file)

    fun archiveFor(file: File): File? {
        val origin = decompiledOriginMap[file.absolutePath] ?: file
        return repository.archiveFor(origin)
    }

    fun resolveOriginFile(file: File): File = decompiledOriginMap[file.absolutePath] ?: file

    fun markFileModified(file: File) {
        val origin = resolveOriginFile(file)
        modifiedPaths += origin.absolutePath
        refreshAll()
    }

    fun minifyApkResources(apkFile: File, onResult: (File?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { com.example.mtcore.data.ApkToolkit.minifyApkResources(apkFile) }
                .onSuccess { res ->
                    modifiedPaths += res.outputFile.absolutePath
                    refreshAll()
                    val msg = "Scanned ${res.totalResFiles} res files (${res.duplicateResCount} duplicates) -> ${res.outputFile.name}"
                    onResult(res.outputFile, msg)
                }
                .onFailure { err ->
                    onResult(null, err.message ?: "Resources minification failed.")
                }
        }
    }

    fun extractApkTargetForTranslation(apkFile: File, onResult: (File?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val root = repository.mountZip(apkFile)
                val arsc = File(root, "resources.arsc")
                val manifest = File(root, "AndroidManifest.xml")
                when {
                    arsc.exists() -> arsc
                    manifest.exists() -> manifest
                    else -> error("No resources.arsc or AndroidManifest.xml found in ${apkFile.name}")
                }
            }.onSuccess { target ->
                onResult(target, null)
            }.onFailure { err ->
                onResult(null, err.message)
            }
        }
    }

    fun displayPath(directory: File): String = repository.displayPath(directory)
    fun resolveDisplayPath(path: String): File? = repository.resolveDisplayPath(path)
    fun activeDirectory(): File = directoryFor(_explorer.value.activePane)

    fun siblingDexFiles(dexFile: File): List<File> {
        val parent = dexFile.parentFile ?: return listOf(dexFile)
        val all = parent.listFiles()
            ?.filter { it.isFile && it.extension.equals("dex", true) }
            ?.sortedWith(compareBy<File> { it.name.length }.thenBy { it.name.lowercase(Locale.ROOT) })
            .orEmpty()
        return if (all.isEmpty()) listOf(dexFile) else all
    }

    fun repairDexFile(dexFile: File, onResult: (String?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { DexParser.repairDexFile(dexFile) }
                .onSuccess { checksumHex ->
                    modifiedPaths += dexFile.absolutePath
                    refreshAll()
                    onResult(checksumHex, null)
                }
                .onFailure { err ->
                    onResult(null, err.message ?: "Failed to repair DEX file.")
                }
        }
    }

    private fun directoryFor(pane: Int): File {
        return if (pane == 0) _explorer.value.leftDirectory else _explorer.value.rightDirectory
    }

    fun refreshAll() {
        refreshPane(0)
        refreshPane(1)
        refreshStats()
    }

    fun setActivePane(pane: Int) {
        _explorer.value = _explorer.value.copy(activePane = pane.coerceIn(0, 1))
        refreshStats()
        updateHistoryButtons()
    }

    fun selectFile(file: File?, pane: Int = _explorer.value.activePane) {
        val state = _explorer.value
        val selection = file?.let { setOf(it.absolutePath) } ?: emptySet()
        _explorer.value = if (pane == 0) state.copy(leftSelected = selection) else state.copy(rightSelected = selection)
    }

    fun ensureFileSelected(file: File, pane: Int = _explorer.value.activePane) {
        val state = _explorer.value
        val current = if (pane == 0) state.leftSelected else state.rightSelected
        if (file.absolutePath in current) return
        val updated = current + file.absolutePath
        _explorer.value = if (pane == 0) state.copy(leftSelected = updated) else state.copy(rightSelected = updated)
    }

    fun toggleSelection(item: FileItem, pane: Int = _explorer.value.activePane) {
        if (item.isParent) return
        val state = _explorer.value
        val current = if (pane == 0) state.leftSelected else state.rightSelected
        val selected = current.toMutableSet()
        val path = item.file.absolutePath
        if (!selected.add(path)) selected.remove(path)
        _explorer.value = if (pane == 0) state.copy(leftSelected = selected) else state.copy(rightSelected = selected)
    }

    fun selectAll() {
        val state = _explorer.value
        val pane = state.activePane
        val files = if (pane == 0) state.leftFiles else state.rightFiles
        val selected = files.filterNot { it.isParent }.map { it.file.absolutePath }.toSet()
        _explorer.value = if (pane == 0) state.copy(leftSelected = selected) else state.copy(rightSelected = selected)
    }

    fun invertSelection() {
        val state = _explorer.value
        val pane = state.activePane
        val files = if (pane == 0) state.leftFiles else state.rightFiles
        val current = state.activeSelected
        val selected = files.filterNot { it.isParent || it.file.absolutePath in current }.map { it.file.absolutePath }.toSet()
        _explorer.value = if (pane == 0) state.copy(leftSelected = selected) else state.copy(rightSelected = selected)
    }

    fun clearSelection() {
        val state = _explorer.value
        _explorer.value = if (state.activePane == 0) state.copy(leftSelected = emptySet()) else state.copy(rightSelected = emptySet())
    }

    fun selectSimilar() {
        val state = _explorer.value
        val pane = state.activePane
        val files = if (pane == 0) state.leftFiles else state.rightFiles
        val selected = state.activeSelected.mapNotNull { path -> files.firstOrNull { it.file.absolutePath == path } }
        val seed = selected.firstOrNull() ?: return
        val extension = seed.file.extension.lowercase(Locale.ROOT)
        val isDirectory = seed.isDirectory
        val matches = files.filterNot { it.isParent }.filter { item ->
            if (isDirectory) item.isDirectory else !item.isDirectory && item.file.extension.lowercase(Locale.ROOT) == extension
        }.map { it.file.absolutePath }.toSet()
        _explorer.value = if (pane == 0) state.copy(leftSelected = matches) else state.copy(rightSelected = matches)
    }

    fun deleteSelected(target: File? = null, onResult: (String?) -> Unit) {
        val state = _explorer.value
        val files = if (state.activeSelected.isNotEmpty()) state.activeSelected.map { File(it) } else listOfNotNull(target)
        if (files.isEmpty()) { onResult("No items selected."); return }
        viewModelScope.launch {
            runCatching { repository.delete(files) }
                .onSuccess {
                    clearSelection()
                    refreshAll()
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "Delete failed.") }
        }
    }

    fun refreshPane(pane: Int) {
        viewModelScope.launch {
            try {
                val items = repository.list(directoryFor(pane), modifiedPaths)
                _explorer.value = if (pane == 0) {
                    _explorer.value.copy(leftFiles = items, error = null)
                } else {
                    _explorer.value.copy(rightFiles = items, error = null)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _explorer.value = _explorer.value.copy(error = error.message ?: "Unable to read directory.")
            }
        }
    }

    private fun refreshStats() {
        viewModelScope.launch {
            runCatching { repository.stats(activeDirectory()) }
                .onSuccess { stats -> _explorer.value = _explorer.value.copy(stats = stats) }
                .onFailure { error -> _explorer.value = _explorer.value.copy(error = error.message ?: "Unable to read statistics.") }
        }
    }

    fun navigateTo(directory: File, pane: Int, recordHistory: Boolean = true) {
        if (!directory.isDirectory || !directory.canRead()) return
        val previous = directoryFor(pane)
        if (recordHistory && previous.absolutePath != directory.absolutePath) {
            val history = histories[pane]
            history.back.addLast(previous)
            while (history.back.size > 100) history.back.removeFirst()
            history.forward.clear()
        }
        _explorer.value = if (pane == 0) {
            _explorer.value.copy(leftDirectory = directory, leftSelected = emptySet())
        } else {
            _explorer.value.copy(rightDirectory = directory, rightSelected = emptySet())
        }
        refreshPane(pane)
        if (_explorer.value.activePane == pane) refreshStats()
        updateHistoryButtons()
    }

    fun navigateToPath(path: String, pane: Int): Boolean {
        val target = repository.resolveDisplayPath(path) ?: File(path.trim().removeSuffix("/").ifEmpty { "/" }).takeIf { it.isDirectory && it.canRead() }
        if (target == null) return false
        navigateTo(target, pane)
        return true
    }

    fun canGoUp(pane: Int = _explorer.value.activePane): Boolean {
        return repository.realParent(directoryFor(pane)) != null
    }

    fun goUp(pane: Int) {
        val parent = repository.realParent(directoryFor(pane)) ?: return
        navigateTo(parent, pane)
    }

    fun backHistory() {
        val pane = _explorer.value.activePane
        val history = histories[pane]
        if (history.back.isEmpty()) return
        history.forward.addLast(directoryFor(pane))
        navigateTo(history.back.removeLast(), pane, false)
    }

    fun forwardHistory() {
        val pane = _explorer.value.activePane
        val history = histories[pane]
        if (history.forward.isEmpty()) return
        history.back.addLast(directoryFor(pane))
        navigateTo(history.forward.removeLast(), pane, false)
    }

    private fun updateHistoryButtons() {
        val history = histories[_explorer.value.activePane]
        _explorer.value = _explorer.value.copy(
            canGoBack = history.back.isNotEmpty(),
            canGoForward = history.forward.isNotEmpty()
        )
    }

    fun syncOtherPaneToActive() {
        val state = _explorer.value
        val otherPane = 1 - state.activePane
        val source = directoryFor(state.activePane)
        val destination = directoryFor(otherPane)
        if (source.absolutePath != destination.absolutePath) {
            histories[otherPane].back.addLast(destination)
            histories[otherPane].forward.clear()
            _explorer.value = if (otherPane == 0) {
                _explorer.value.copy(leftDirectory = source)
            } else {
                _explorer.value.copy(rightDirectory = source)
            }
            refreshPane(otherPane)
        }
        refreshStats()
        updateHistoryButtons()
    }

    fun createFile(name: String, onResult: (String?) -> Unit) {
        val pane = _explorer.value.activePane
        viewModelScope.launch {
            runCatching { repository.createFile(directoryFor(pane), name) }
                .onSuccess { file ->
                    modifiedPaths += file.absolutePath
                    refreshPane(pane)
                    refreshStats()
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "Unable to create file.") }
        }
    }

    fun createFolder(name: String, onResult: (String?) -> Unit) {
        val pane = _explorer.value.activePane
        viewModelScope.launch {
            runCatching { repository.createDirectory(directoryFor(pane), name) }
                .onSuccess { directory ->
                    modifiedPaths += directory.absolutePath
                    navigateTo(directory, pane)
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "Unable to create folder.") }
        }
    }

    fun rename(file: File, name: String, onResult: (String?) -> Unit) {
        val pane = _explorer.value.activePane
        viewModelScope.launch {
            runCatching { repository.rename(file, name) }
                .onSuccess { renamed ->
                    modifiedPaths += renamed.absolutePath
                    refreshPane(pane)
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "Rename failed.") }
        }
    }

    fun properties(file: File): FileProperties = repository.properties(file)

    fun hasConflictInTarget(file: File): Boolean {
        val targetPane = 1 - _explorer.value.activePane
        return repository.hasConflict(file, directoryFor(targetPane))
    }

    fun copySelected(file: File, replace: Boolean = false, keepBoth: Boolean = false, onResult: (String?) -> Unit) {
        val state = _explorer.value
        val targetPane = 1 - state.activePane
        val sources = state.activeSelected.takeIf { it.isNotEmpty() }?.map(::File) ?: listOf(file)
        viewModelScope.launch {
            runCatching {
                sources.forEach { source ->
                    val copied = repository.copy(source, directoryFor(targetPane), replace, keepBoth)
                    modifiedPaths += copied.absolutePath
                }
            }.onSuccess {
                clearSelection()
                refreshPane(targetPane)
                onResult(null)
            }.onFailure { onResult(it.message ?: "Copy failed.") }
        }
    }

    fun moveSelected(file: File, replace: Boolean, onResult: (String?) -> Unit) {
        val state = _explorer.value
        val sourcePane = state.activePane
        val targetPane = 1 - sourcePane
        val sources = state.activeSelected.takeIf { it.isNotEmpty() }?.map(::File) ?: listOf(file)
        viewModelScope.launch {
            runCatching {
                sources.forEach { source ->
                    val moved = repository.move(source, directoryFor(targetPane), replace)
                    modifiedPaths += moved.absolutePath
                }
            }.onSuccess {
                clearSelection()
                refreshPane(sourcePane)
                refreshPane(targetPane)
                onResult(null)
            }.onFailure { onResult(it.message ?: "Move failed.") }
        }
    }

    fun openZip(file: File, pane: Int) {
        viewModelScope.launch {
            runCatching { repository.mountZip(file) }
                .onSuccess { navigateTo(it, pane) }
                .onFailure { _explorer.value = _explorer.value.copy(error = it.message ?: "Cannot open archive.") }
        }
    }

    fun openFile(file: File) {
        val dispatched = dispatchFile(file)
        if (dispatched == com.example.mtcore.data.DispatchedFileType.BINARY_XML) {
            decompileBinaryXmlToEditor(file) {}
            return
        }
        val canReadAsText = dispatched == com.example.mtcore.data.DispatchedFileType.PLAIN_XML ||
            dispatched == com.example.mtcore.data.DispatchedFileType.CODE_TEXT ||
            repository.isTextFile(file)
        if (!canReadAsText) {
            _editor.value = EditorState(file = file, error = "Unsupported or binary file: ${file.name}")
            return
        }
        viewModelScope.launch {
            undoStack.clear()
            redoStack.clear()
            lastRecordedContent = ""
            _editor.value = EditorState(file = file, isLoading = true)
            runCatching { withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) } }
                .onSuccess { content ->
                    lastRecordedContent = content
                    _editor.value = EditorState(file = file, content = content, savedContent = content)
                }
                .onFailure { _editor.value = EditorState(file = file, error = it.message ?: "Unable to open file.") }
        }
    }

    fun decompileBinaryXmlToEditor(file: File, onReady: (File) -> Unit) {
        viewModelScope.launch {
            undoStack.clear()
            redoStack.clear()
            lastRecordedContent = ""
            _editor.value = EditorState(file = file, isLoading = true)
            runCatching {
                val xmlText = BinaryXmlDecompiler.decompileFile(file)
                val targetDir = File(cacheDir, "decompiled_xml").apply { mkdirs() }
                val outFile = File(targetDir, file.name)
                withContext(Dispatchers.IO) {
                    outFile.writeText(xmlText, Charsets.UTF_8)
                }
                decompiledOriginMap[outFile.absolutePath] = file
                outFile to xmlText
            }.onSuccess { (outFile, xmlText) ->
                lastRecordedContent = xmlText
                _editor.value = EditorState(file = outFile, content = xmlText, savedContent = xmlText)
                onReady(outFile)
            }.onFailure { error ->
                _editor.value = EditorState(file = file, error = error.message ?: "Failed to decompile XML.")
            }
        }
    }

    fun openDexClassInEditor(dexClass: DexClassDef, onReady: (File) -> Unit) {
        viewModelScope.launch {
            undoStack.clear()
            redoStack.clear()
            lastRecordedContent = ""
            val smaliDir = File(cacheDir, "smali_session").apply { mkdirs() }
            val safeName = dexClass.simpleName.replace(Regex("[^a-zA-Z0-9_$-]"), "_") + ".smali"
            val smaliFile = File(smaliDir, safeName)
            _editor.value = EditorState(file = smaliFile, isLoading = true)
            runCatching {
                val smaliText = DexParser.generateSmaliForClass(dexClass)
                withContext(Dispatchers.IO) {
                    smaliFile.writeText(smaliText, Charsets.UTF_8)
                }
                smaliText
            }.onSuccess { smaliText ->
                lastRecordedContent = smaliText
                _editor.value = EditorState(file = smaliFile, content = smaliText, savedContent = smaliText)
                onReady(smaliFile)
            }.onFailure { error ->
                _editor.value = EditorState(file = smaliFile, error = error.message ?: "Failed to generate Smali.")
            }
        }
    }

    fun updateContent(content: String) {
        val state = _editor.value
        if (state.content == content) return
        if (content != lastRecordedContent) {
            undoStack.addLast(lastRecordedContent)
            while (undoStack.size > 100) undoStack.removeFirst()
            redoStack.clear()
            lastRecordedContent = content
        }
        _editor.value = state.copy(content = content, error = null)
    }

    fun updateCursor(position: Int) {
        _editor.value = _editor.value.copy(cursor = position.coerceAtLeast(0))
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val state = _editor.value
        redoStack.addLast(state.content)
        val previous = undoStack.removeLast()
        lastRecordedContent = previous
        _editor.value = state.copy(content = previous, cursor = previous.length)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val state = _editor.value
        undoStack.addLast(state.content)
        val next = redoStack.removeLast()
        lastRecordedContent = next
        _editor.value = state.copy(content = next, cursor = next.length)
    }

    fun saveFile(onSaved: (Boolean, String?) -> Unit = { _, _ -> }) {
        val state = _editor.value
        val file = state.file ?: return
        viewModelScope.launch {
            runCatching {
                repository.saveText(file, state.content)
                decompiledOriginMap[file.absolutePath]?.let { origin ->
                    modifiedPaths += origin.absolutePath
                }
            }.onSuccess {
                modifiedPaths += file.absolutePath
                _editor.value = _editor.value.copy(
                    savedContent = state.content,
                    error = null,
                    hasSavedChangesInSession = true
                )
                refreshAll()
                onSaved(true, null)
            }.onFailure { error ->
                _editor.value = _editor.value.copy(error = error.message ?: "Unable to save file.")
                onSaved(false, error.message)
            }
        }
    }

    fun addToArchive(archive: File, source: File, entryName: String = source.name, level: CompressionLevel = CompressionLevel.NORMAL, deleteSource: Boolean = false, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                require(entryName.isNotBlank() && !entryName.contains('/') && !entryName.contains('\\')) { "Invalid archive entry name." }
                val root = repository.mountZip(archive)
                val staging = if (entryName == source.name) source else File(source.parentFile, entryName).also { target ->
                    check(!target.exists()) { "An item named $entryName already exists." }
                    check(if (source.isDirectory) source.copyRecursively(target, overwrite = false) else source.copyTo(target, overwrite = false).exists()) { "Unable to stage renamed archive entry." }
                }
                try {
                    repository.copy(staging, root, replace = false, keepBoth = true)
                    repository.updateArchive(archive, root, level)
                } finally {
                    if (staging != source) staging.deleteRecursively()
                }
                if (deleteSource) check(source.deleteRecursively()) { "Added to archive, but source could not be deleted." }
            }.onSuccess {
                modifiedPaths += archive.absolutePath
                refreshAll()
                onResult(null)
            }.onFailure { error -> onResult(error.message ?: "Unable to add file to archive.") }
        }
    }

    fun updateArchive(file: File, onResult: (Boolean, String?) -> Unit) {
        val origin = resolveOriginFile(file)
        val archive = repository.archiveFor(origin)
        if (archive == null) {
            onResult(false, "Archive not found.")
            return
        }
        val root = repository.mountRootFor(archive)
        viewModelScope.launch {
            runCatching { repository.updateArchive(archive, root) }
                .onSuccess {
                    modifiedPaths += archive.absolutePath
                    refreshAll()
                    onResult(true, null)
                }
                .onFailure { onResult(false, it.message ?: "Archive update failed.") }
        }
    }

    fun compress(
        source: File,
        filename: String,
        level: CompressionLevel,
        deleteSource: Boolean,
        onResult: (String?) -> Unit
    ) {
        val parent = source.parentFile ?: activeDirectory()
        val archive = File(parent, if (filename.endsWith(".zip", true)) filename else "$filename.zip")
        viewModelScope.launch {
            runCatching { repository.compress(source, archive, level, deleteSource) }
                .onSuccess {
                    modifiedPaths += archive.absolutePath
                    refreshAll()
                    onResult(null)
                }
                .onFailure { onResult(it.message ?: "Compression failed.") }
        }
    }

    fun extractInstalledApk(app: InstalledAppInfo, onResult: (File?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { AppRepository.extractApk(app) }
                .onSuccess { extracted ->
                    modifiedPaths += extracted.absolutePath
                    refreshAll()
                    onResult(extracted, null)
                }
                .onFailure { error ->
                    onResult(null, error.message ?: "Failed to extract APK.")
                }
        }
    }

    fun extractInstalledSplitApksBundle(app: InstalledAppInfo, onResult: (File?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { AppRepository.extractSplitApksBundle(app) }
                .onSuccess { extracted ->
                    modifiedPaths += extracted.absolutePath
                    refreshAll()
                    onResult(extracted, null)
                }
                .onFailure { error ->
                    onResult(null, error.message ?: "Failed to extract Split APK bundle.")
                }
        }
    }

    fun extractAndMergeInstalledSplitApk(app: InstalledAppInfo, onResult: (File?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { AppRepository.extractAndMergeSplitApk(app) }
                .onSuccess { merged ->
                    modifiedPaths += merged.absolutePath
                    refreshAll()
                    onResult(merged, null)
                }
                .onFailure { error ->
                    onResult(null, error.message ?: "Failed to merge Split APKs.")
                }
        }
    }

    fun locateExtractedFile(file: File) {
        modifiedPaths += file.absolutePath
        val parent = file.parentFile ?: return
        val pane = _explorer.value.activePane
        navigateTo(parent, pane)
    }

    fun openOrCreateScratchFile(): File {
        _editor.value.file?.takeIf { it.exists() && it.isFile }?.let { return it }
        val baseDir = File("/storage/emulated/0/MT2").takeIf { it.exists() || it.mkdirs() } ?: cacheDir
        val scratch = File(baseDir, "untitled.txt")
        if (!scratch.exists()) {
            runCatching { scratch.writeText("") }
        }
        openFile(scratch)
        return scratch
    }

    fun discardAndClose() {
        _editor.value = EditorState()
        undoStack.clear()
        redoStack.clear()
    }
}
