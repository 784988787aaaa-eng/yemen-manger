package com.example.mtcore.data

import com.example.mtcore.domain.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

 data class DirectoryStats(
    val folders: Int,
    val files: Int,
    val freeBytes: Long,
    val totalBytes: Long,
    val isArchive: Boolean = false
)

data class FileProperties(
    val path: String,
    val size: String,
    val modified: String,
    val readable: Boolean,
    val writable: Boolean
)

enum class CompressionLevel {
    STORE,
    FAST,
    NORMAL,
    MAXIMUM,
    ULTRA
}

class FileRepository(private val cacheDir: File) {
    companion object {
        val ROOT_DIRECTORY = File("/storage/emulated/0/")
        private const val MOUNT_MARKER = ".mt_mount_marker"
        val EXTENSIONS = setOf(
            "txt", "md", "properties", "json", "xml", "kt", "kts", "java", "gradle",
            "bat", "sh", "js", "ts", "html", "css", "py", "c", "cpp", "h", "hpp",
            "yml", "yaml", "ini", "conf", "env", "gitignore", "csv", "toml", "sql", "log",
            "smali"
        )
    }

    private data class Mount(
        val archive: File,
        val root: File,
        val archiveModified: Long,
        val archiveLength: Long
    )

    private val mounts = ConcurrentHashMap<String, Mount>()

    suspend fun list(directory: File, modifiedPaths: Set<String> = emptySet()): List<FileItem> = withContext(Dispatchers.IO) {
        require(directory.isDirectory && directory.canRead()) {
            "Cannot read directory: ${directory.absolutePath}"
        }
        val result = mutableListOf<FileItem>()
        if (isArchiveRoot(directory)) {
            val archive = archiveForDirectory(directory)
            val parent = archive?.parentFile
            if (parent != null) {
                result += FileItem(parent, "..", isDirectory = true, isParent = true)
            }
        } else if (directory.parentFile != null) {
            result += FileItem(directory.parentFile, "..", isDirectory = true, isParent = true)
        }
        val children = directory.listFiles()
            ?.filterNot { it.name == MOUNT_MARKER }
            ?: throw IllegalStateException("Cannot list directory: ${directory.absolutePath}")
        result += children
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
            .map { FileItem(it, isRecentlyModified = it.absolutePath in modifiedPaths) }
        result
    }

    suspend fun stats(directory: File): DirectoryStats = withContext(Dispatchers.IO) {
        val children = directory.listFiles().orEmpty().filterNot { it.name == MOUNT_MARKER }
        val archive = archiveForDirectory(directory) != null
        DirectoryStats(
            folders = children.count { it.isDirectory },
            files = children.count { it.isFile },
            freeBytes = if (archive) 0L else directory.usableSpace,
            totalBytes = if (archive) 0L else directory.totalSpace,
            isArchive = archive
        )
    }

    fun isTextFile(file: File): Boolean {
        if (!file.isFile || file.length() >= 8L * 1024L * 1024L) return false
        if (file.extension.lowercase(Locale.ROOT) in EXTENSIONS) return true
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(1024)
                val count = input.read(buffer).coerceAtLeast(0)
                (0 until count).none { buffer[it] == 0.toByte() }
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun delete(files: List<File>): List<File> = withContext(Dispatchers.IO) {
        files.distinctBy { it.absolutePath }.map { file ->
            check(file.exists()) { "Item no longer exists: ${file.absolutePath}" }
            check(file.deleteRecursively()) { "Unable to delete: ${file.absolutePath}" }
            file
        }
    }

    suspend fun readText(file: File): String = withContext(Dispatchers.IO) {
        file.readText(Charsets.UTF_8)
    }

    suspend fun saveText(file: File, content: String) = withContext(Dispatchers.IO) {
        check(file.isFile) { "File no longer exists: ${file.absolutePath}" }
        file.writeText(content, Charsets.UTF_8)
    }

    suspend fun createFile(parent: File, name: String): File = withContext(Dispatchers.IO) {
        validateName(name)
        val target = File(parent, name)
        check(target.createNewFile()) { "File already exists: $name" }
        target
    }

    suspend fun createDirectory(parent: File, name: String): File = withContext(Dispatchers.IO) {
        validateName(name)
        val target = File(parent, name)
        check(target.mkdir()) { "Cannot create folder: $name" }
        target
    }

    suspend fun rename(source: File, newName: String): File = withContext(Dispatchers.IO) {
        validateName(newName)
        val target = File(source.parentFile ?: error("Missing parent directory"), newName)
        check(!target.exists()) { "An item named $newName already exists." }
        check(source.renameTo(target)) { "Rename failed." }
        target
    }

    suspend fun copy(source: File, targetDirectory: File, replace: Boolean = false, keepBoth: Boolean = false): File = withContext(Dispatchers.IO) {
        var target = File(targetDirectory, source.name)
        if (keepBoth && target.exists()) target = uniqueTarget(targetDirectory, source.name)
        if (target.exists() && !replace) throw IllegalStateException("Target already exists: ${target.name}")
        if (target.exists() && replace) target.deleteRecursively()
        copyRecursively(source, target)
        target
    }

    suspend fun move(source: File, targetDirectory: File, replace: Boolean = false): File = withContext(Dispatchers.IO) {
        var target = File(targetDirectory, source.name)
        if (target.exists() && !replace) throw IllegalStateException("Target already exists: ${target.name}")
        if (target.exists()) target.deleteRecursively()
        if (!source.renameTo(target)) {
            copyRecursively(source, target)
            source.deleteRecursively()
        }
        target
    }

    fun hasConflict(source: File, targetDirectory: File): Boolean {
        return File(targetDirectory, source.name).exists()
    }

    fun properties(file: File): FileProperties {
        val size = formatSize(if (file.isDirectory) directorySize(file) else file.length())
        val modified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(file.lastModified()))
        return FileProperties(file.absolutePath, size, modified, file.canRead(), file.canWrite())
    }

    suspend fun mountZip(archive: File): File = withContext(Dispatchers.IO) {
        val archiveExts = setOf("zip", "apk", "apks", "xapk", "apkm", "jar", "aab")
        require(archive.isFile && archive.extension.lowercase(Locale.ROOT) in archiveExts) { "Not a supported archive." }
        val key = archive.absolutePath
        val existing = mounts[key]
        if (existing != null && existing.archiveModified == archive.lastModified() && existing.archiveLength == archive.length() && existing.root.isDirectory) {
            return@withContext existing.root
        }
        val root = mountRoot(archive)
        if (root.exists()) root.deleteRecursively()
        check(root.mkdirs()) { "Cannot create ZIP mount directory." }
        ZipFile(archive).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val output = File(root, entry.name).canonicalFile
                check(output.path == root.canonicalPath || output.path.startsWith(root.canonicalPath + File.separator)) {
                    "Unsafe ZIP entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(output).use { destination -> input.copyTo(destination) }
                    }
                }
            }
        }
        File(root, MOUNT_MARKER).writeText(key)
        mounts[key] = Mount(archive, root, archive.lastModified(), archive.length())
        root
    }

    fun isArchiveRoot(directory: File): Boolean {
        return mounts.values.any { it.root.absolutePath == directory.absolutePath }
    }

    fun isArchivePath(file: File): Boolean = archiveForDirectory(file) != null

    fun archiveFor(file: File): File? = archiveForDirectory(file)

    fun displayPath(directory: File): String {
        val mount = findMount(directory) ?: return directory.absolutePath.ensureTrailingSlash()
        val relative = directory.relativeTo(mount.root).invariantSeparatorsPath.trim('/')
        val prefix = mount.archive.absolutePath.ensureTrailingSlash()
        return if (relative.isEmpty()) prefix else "$prefix$relative/"
    }

    fun resolveDisplayPath(path: String): File? {
        val normalized = path.trim().ensureTrailingSlash()
        for (mount in mounts.values) {
            val prefix = mount.archive.absolutePath.ensureTrailingSlash()
            if (normalized.startsWith(prefix)) {
                val relative = normalized.removePrefix(prefix).trim('/')
                val resolved = if (relative.isEmpty()) mount.root else File(mount.root, relative)
                return resolved.takeIf { it.isDirectory && it.canRead() }
            }
        }
        return null
    }

    fun realParent(directory: File): File? {
        if (isArchiveRoot(directory)) return archiveForDirectory(directory)?.parentFile
        return directory.parentFile
    }

    suspend fun compress(
        source: File,
        archive: File,
        level: CompressionLevel,
        deleteSource: Boolean
    ) = withContext(Dispatchers.IO) {
        val temp = File(archive.parentFile, archive.name + ".tmp")
        val zipLevel = when (level) {
            CompressionLevel.STORE -> 0
            CompressionLevel.FAST -> 1
            CompressionLevel.NORMAL -> 6
            CompressionLevel.MAXIMUM -> 9
            CompressionLevel.ULTRA -> 9
        }
        ZipOutputStream(FileOutputStream(temp)).use { zip ->
            zip.setLevel(zipLevel)
            val baseName = source.name
            val items = if (source.isDirectory) source.walkTopDown().toList() else listOf(source)
            for (item in items) {
                if (item == source.parentFile) continue
                val relative = if (item == source) baseName else "$baseName/${item.relativeTo(source).invariantSeparatorsPath}"
                val entryName = if (item.isDirectory) "$relative/" else relative
                zip.putNextEntry(ZipEntry(entryName))
                if (item.isFile) FileInputStream(item).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        if (!temp.renameTo(archive)) {
            archive.delete()
            check(temp.renameTo(archive)) { "Cannot write archive." }
        }
        if (deleteSource) check(source.deleteRecursively()) { "Archive created, but source could not be deleted." }
    }

    suspend fun updateArchive(archive: File, mountRoot: File, level: CompressionLevel = CompressionLevel.NORMAL) = withContext(Dispatchers.IO) {
        val temp = File(archive.parentFile, archive.name + ".tmp")
        ZipOutputStream(FileOutputStream(temp)).use { zip ->
            zip.setLevel(when (level) {
                CompressionLevel.STORE -> java.util.zip.Deflater.NO_COMPRESSION
                CompressionLevel.FAST -> java.util.zip.Deflater.BEST_SPEED
                CompressionLevel.NORMAL -> java.util.zip.Deflater.DEFAULT_COMPRESSION
                CompressionLevel.MAXIMUM, CompressionLevel.ULTRA -> java.util.zip.Deflater.BEST_COMPRESSION
            })
            mountRoot.walkTopDown()
                .filter { it.absolutePath != mountRoot.absolutePath && it.name != MOUNT_MARKER }
                .forEach { file ->
                    val relative = file.relativeTo(mountRoot).invariantSeparatorsPath
                    val name = if (file.isDirectory) "$relative/" else relative
                    zip.putNextEntry(ZipEntry(name))
                    if (file.isFile) FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        if (!temp.renameTo(archive)) {
            archive.delete()
            check(temp.renameTo(archive)) { "Unable to replace archive." }
        }
        mounts[archive.absolutePath] = Mount(archive, mountRoot, archive.lastModified(), archive.length())
    }

    fun mountRootFor(archive: File): File = mountRoot(archive)

    private fun mountRoot(archive: File): File {
        val key = "${archive.nameWithoutExtension}_${archive.absolutePath.hashCode().toUInt().toString(16)}"
        return File(cacheDir, "zip_mount/$key")
    }

    private fun findMount(directory: File): Mount? {
        return mounts.values.firstOrNull {
            directory.absolutePath == it.root.absolutePath || directory.absolutePath.startsWith(it.root.absolutePath + File.separator)
        }
    }

    private fun archiveForDirectory(directory: File): File? = findMount(directory)?.archive

    private fun validateName(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')) {
            "Invalid file name."
        }
    }

    private fun copyRecursively(source: File, target: File) {
        if (source.isDirectory) {
            check(target.mkdirs() || target.isDirectory) { "Cannot create ${target.absolutePath}" }
            source.listFiles().orEmpty().forEach { child -> copyRecursively(child, File(target, child.name)) }
        } else {
            target.parentFile?.mkdirs()
            FileInputStream(source).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        }
    }

    private fun uniqueTarget(directory: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidateName = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            val candidate = File(directory, candidateName)
            if (!candidate.exists()) return candidate
            index += 1
        }
    }

    private fun directorySize(directory: File): Long = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun formatSize(bytes: Long): String {
        var value = bytes.toDouble()
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return if (index == 0) "${value.toLong()} B" else String.format(Locale.US, "%.2f %s", value, units[index])
    }

    private fun String.ensureTrailingSlash(): String = trimEnd('/') + "/"

}
