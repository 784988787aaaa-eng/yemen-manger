package com.example.core.storage

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.core.model.AppResult
import com.example.core.model.DomainError
import com.example.core.model.FileEntry
import com.example.core.model.FileHashReport
import com.example.core.model.ProviderCapabilities
import com.example.core.model.StorageProviderType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

interface StorageProvider {
    val providerType: StorageProviderType
    fun capabilities(): ProviderCapabilities
    fun list(uriOrPath: String): AppResult<List<FileEntry>>
    fun metadata(uriOrPath: String): AppResult<FileEntry>
    fun createDirectory(parentUriOrPath: String, name: String): AppResult<FileEntry>
    fun createNewFile(parentUriOrPath: String, name: String, initialBytes: ByteArray = ByteArray(0)): AppResult<FileEntry>
    fun rename(uriOrPath: String, newName: String): AppResult<FileEntry>
    fun delete(uriOrPath: String): AppResult<Boolean>
    fun moveToTrash(uriOrPath: String): AppResult<FileEntry>
    fun copyStreaming(
        sourceUriOrPath: String,
        destDirUriOrPath: String,
        newName: String? = null,
        overwrite: Boolean = false,
        onProgress: (Float?, Long, Long?) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): AppResult<FileEntry>
    fun move(
        sourceUriOrPath: String,
        destDirUriOrPath: String,
        overwrite: Boolean = false,
        onProgress: (Float?, Long, Long?) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): AppResult<FileEntry>
    fun computeHashes(
        uriOrPath: String,
        onProgress: (Float?, Long, Long?) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): AppResult<FileHashReport>
    fun search(
        rootUriOrPath: String,
        nameQuery: String,
        extensionFilter: String = "",
        minSizeBytes: Long = 0L,
        isCancelled: () -> Boolean = { false }
    ): AppResult<List<FileEntry>>
    fun writeAtomicWorkingCopy(
        targetPath: String,
        bytes: ByteArray,
        replaceOriginal: Boolean = false
    ): AppResult<FileEntry>
}

class LocalFileStorageProvider(private val trashRootDir: File) : StorageProvider {
    override val providerType: StorageProviderType = StorageProviderType.LOCAL_FS

    init {
        if (!trashRootDir.exists()) {
            trashRootDir.mkdirs()
        }
    }

    override fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        supportsStreamingRead = true,
        supportsAtomicTempWrite = true,
        supportsDirectRename = true,
        supportsRandomAccess = true,
        supportsTrashBin = true
    )

    override fun list(uriOrPath: String): AppResult<List<FileEntry>> {
        if (uriOrPath.startsWith("content://")) {
            return AppResult.Failure(DomainError.ValidationFailed("URI is a SAF content URI, not a local filesystem path"))
        }
        val dir = File(uriOrPath)
        if (!dir.exists()) return AppResult.Failure(DomainError.NotFound(uriOrPath))
        if (!dir.canRead()) return AppResult.Failure(DomainError.PermissionDenied(uriOrPath))
        if (!dir.isDirectory) return AppResult.Failure(DomainError.ValidationFailed("Not a directory: $uriOrPath"))

        val children = dir.listFiles() ?: return AppResult.Failure(DomainError.PermissionDenied(uriOrPath))
        val entries = children.map { it.toFileEntry() }
        return AppResult.Success(entries)
    }

    override fun metadata(uriOrPath: String): AppResult<FileEntry> {
        val file = File(uriOrPath)
        if (!file.exists()) return AppResult.Failure(DomainError.NotFound(uriOrPath))
        return AppResult.Success(file.toFileEntry())
    }

    override fun createDirectory(parentUriOrPath: String, name: String): AppResult<FileEntry> {
        val sanitized = sanitizeName(name) ?: return AppResult.Failure(DomainError.ValidationFailed("اسم المجلد غير صالح: $name"))
        val parent = File(parentUriOrPath)
        if (!parent.exists() || !parent.isDirectory) return AppResult.Failure(DomainError.NotFound(parentUriOrPath))
        if (!parent.canWrite()) return AppResult.Failure(DomainError.PermissionDenied(parentUriOrPath))

        val target = File(parent, sanitized)
        if (target.exists()) return AppResult.Failure(DomainError.DestinationExists(target.absolutePath))
        return if (target.mkdirs()) {
            AppResult.Success(target.toFileEntry())
        } else {
            AppResult.Failure(DomainError.IoFailure("Failed to create folder ${target.absolutePath}"))
        }
    }

    override fun createNewFile(parentUriOrPath: String, name: String, initialBytes: ByteArray): AppResult<FileEntry> {
        val sanitized = sanitizeName(name) ?: return AppResult.Failure(DomainError.ValidationFailed("اسم الملف غير صالح: $name"))
        val parent = File(parentUriOrPath)
        if (!parent.exists() || !parent.isDirectory) return AppResult.Failure(DomainError.NotFound(parentUriOrPath))
        if (!parent.canWrite()) return AppResult.Failure(DomainError.PermissionDenied(parentUriOrPath))

        val target = File(parent, sanitized)
        if (target.exists()) return AppResult.Failure(DomainError.DestinationExists(target.absolutePath))
        return try {
            target.writeBytes(initialBytes)
            AppResult.Success(target.toFileEntry())
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Write error"))
        }
    }

    override fun rename(uriOrPath: String, newName: String): AppResult<FileEntry> {
        val sanitized = sanitizeName(newName) ?: return AppResult.Failure(DomainError.ValidationFailed("الاسم الجديد غير صالح: $newName"))
        val src = File(uriOrPath)
        if (!src.exists()) return AppResult.Failure(DomainError.NotFound(uriOrPath))
        val parent = src.parentFile ?: return AppResult.Failure(DomainError.PermissionDenied(uriOrPath))
        val dest = File(parent, sanitized)
        if (dest.exists()) return AppResult.Failure(DomainError.DestinationExists(dest.absolutePath))
        return if (src.renameTo(dest)) {
            AppResult.Success(dest.toFileEntry())
        } else {
            AppResult.Failure(DomainError.IoFailure("Rename failed for $uriOrPath"))
        }
    }

    override fun delete(uriOrPath: String): AppResult<Boolean> {
        val target = File(uriOrPath)
        if (!target.exists()) return AppResult.Failure(DomainError.NotFound(uriOrPath))
        val deleted = target.deleteRecursively()
        return if (deleted) {
            AppResult.Success(true)
        } else {
            AppResult.Failure(DomainError.PermissionDenied("Unable to delete $uriOrPath"))
        }
    }

    override fun moveToTrash(uriOrPath: String): AppResult<FileEntry> {
        val src = File(uriOrPath)
        if (!src.exists()) return AppResult.Failure(DomainError.NotFound(uriOrPath))
        if (!trashRootDir.exists()) trashRootDir.mkdirs()
        val destName = "${System.currentTimeMillis()}_${src.name}"
        val dest = File(trashRootDir, destName)
        if (src.renameTo(dest)) {
            return AppResult.Success(dest.toFileEntry())
        }
        // Fallback to copy + delete if across mount points
        val copied = copyStreaming(uriOrPath, trashRootDir.absolutePath, destName, overwrite = false)
        return when (copied) {
            is AppResult.Success -> {
                if (src.deleteRecursively()) {
                    copied
                } else {
                    AppResult.Success(
                        copied.data,
                        warnings = listOf("تم النسخ إلى سلة المحذوفات ولكن تعذر حذف الأصل تلقائيًا")
                    )
                }
            }
            is AppResult.Failure -> copied
        }
    }

    override fun copyStreaming(
        sourceUriOrPath: String,
        destDirUriOrPath: String,
        newName: String?,
        overwrite: Boolean,
        onProgress: (Float?, Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ): AppResult<FileEntry> {
        val src = File(sourceUriOrPath)
        if (!src.exists()) return AppResult.Failure(DomainError.NotFound(sourceUriOrPath))
        if (!src.canRead()) return AppResult.Failure(DomainError.PermissionDenied(sourceUriOrPath))

        val destDir = File(destDirUriOrPath)
        if (!destDir.exists() || !destDir.isDirectory) return AppResult.Failure(DomainError.NotFound(destDirUriOrPath))
        if (!destDir.canWrite()) return AppResult.Failure(DomainError.PermissionDenied(destDirUriOrPath))

        val targetName = newName ?: src.name
        val destFile = File(destDir, targetName)
        if (src.canonicalPath == destFile.canonicalPath) {
            return AppResult.Failure(DomainError.DestinationExists("المصدر والوجهة متطابقان: ${destFile.name}"))
        }
        if (destFile.exists() && !overwrite) {
            return AppResult.Failure(DomainError.DestinationExists(destFile.absolutePath))
        }

        return if (src.isDirectory) {
            copyDirectoryRecursive(src, destFile, overwrite, onProgress, isCancelled)
        } else {
            copySingleFileStreaming(src, destFile, overwrite, onProgress, isCancelled)
        }
    }

    private fun copySingleFileStreaming(
        src: File,
        destFile: File,
        overwrite: Boolean,
        onProgress: (Float?, Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ): AppResult<FileEntry> {
        val totalSize = src.length()
        val usable = destFile.parentFile?.usableSpace ?: Long.MAX_VALUE
        if (totalSize > usable) {
            return AppResult.Failure(DomainError.InsufficientSpace("Required $totalSize bytes, available $usable bytes"))
        }

        val tempFile = File(destFile.parentFile, ".${destFile.name}.ym_copy_tmp")
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (isCancelled()) {
                            tempFile.delete()
                            return AppResult.Failure(DomainError.Cancelled(src.name))
                        }
                        output.write(buffer, 0, read)
                        copied += read
                        val fraction = if (totalSize > 0) copied.toFloat() / totalSize.toFloat() else 1f
                        onProgress(fraction, copied, totalSize)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (tempFile.length() != totalSize) {
                tempFile.delete()
                return AppResult.Failure(DomainError.ValidationFailed("Size mismatch after copy"))
            }
            if (destFile.exists() && overwrite) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = overwrite)
                tempFile.delete()
            }
            return AppResult.Success(destFile.toFileEntry())
        } catch (e: Exception) {
            tempFile.delete()
            return AppResult.Failure(DomainError.IoFailure(e.message ?: "Streaming copy failed"))
        }
    }

    private fun copyDirectoryRecursive(
        srcDir: File,
        destDir: File,
        overwrite: Boolean,
        onProgress: (Float?, Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ): AppResult<FileEntry> {
        if (destDir.canonicalPath.startsWith(srcDir.canonicalPath + File.separator)) {
            return AppResult.Failure(DomainError.ValidationFailed("لا يمكن نسخ المجلد إلى داخل نفسه"))
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            return AppResult.Failure(DomainError.IoFailure("Cannot create directory ${destDir.absolutePath}"))
        }
        val warnings = mutableListOf<String>()
        val children = srcDir.listFiles().orEmpty()
        for ((index, child) in children.withIndex()) {
            if (isCancelled()) {
                return AppResult.Failure(DomainError.Cancelled(srcDir.name))
            }
            val targetChild = File(destDir, child.name)
            val res = if (child.isDirectory) {
                copyDirectoryRecursive(child, targetChild, overwrite, onProgress, isCancelled)
            } else {
                copySingleFileStreaming(child, targetChild, overwrite, onProgress, isCancelled)
            }
            when (res) {
                is AppResult.Success -> warnings.addAll(res.warnings)
                is AppResult.Failure -> warnings.add("${child.name}: ${res.error.code}")
            }
            onProgress((index + 1).toFloat() / children.size.coerceAtLeast(1), index.toLong() + 1, children.size.toLong())
        }
        return AppResult.Success(destDir.toFileEntry(), warnings)
    }

    override fun move(
        sourceUriOrPath: String,
        destDirUriOrPath: String,
        overwrite: Boolean,
        onProgress: (Float?, Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ): AppResult<FileEntry> {
        val src = File(sourceUriOrPath)
        if (!src.exists()) return AppResult.Failure(DomainError.NotFound(sourceUriOrPath))
        val destDir = File(destDirUriOrPath)
        if (!destDir.exists() || !destDir.isDirectory) return AppResult.Failure(DomainError.NotFound(destDirUriOrPath))

        val target = File(destDir, src.name)
        if (target.exists() && !overwrite) {
            return AppResult.Failure(DomainError.DestinationExists(target.absolutePath))
        }
        if (!target.exists() && src.renameTo(target)) {
            onProgress(1f, target.length(), target.length())
            return AppResult.Success(target.toFileEntry())
        }
        // Fallback: safe verified copy then delete source only if copy succeeded without warnings
        val copyResult = copyStreaming(sourceUriOrPath, destDirUriOrPath, src.name, overwrite, onProgress, isCancelled)
        return when (copyResult) {
            is AppResult.Success -> {
                if (copyResult.warnings.isEmpty()) {
                    if (src.deleteRecursively()) {
                        copyResult
                    } else {
                        AppResult.Success(
                            copyResult.data,
                            warnings = listOf("تم نسخ الملف للوجهة، ولكن تعذر حذف المصدر الأصلي")
                        )
                    }
                } else {
                    AppResult.Success(
                        copyResult.data,
                        warnings = copyResult.warnings + "تم الإبقاء على المصدر الأصلي لوجود تحذيرات أثناء النقل"
                    )
                }
            }
            is AppResult.Failure -> copyResult
        }
    }

    override fun computeHashes(
        uriOrPath: String,
        onProgress: (Float?, Long, Long?) -> Unit,
        isCancelled: () -> Boolean
    ): AppResult<FileHashReport> {
        val file = File(uriOrPath)
        if (!file.exists() || file.isDirectory) return AppResult.Failure(DomainError.ValidationFailed("Hashes require a regular file"))
        if (!file.canRead()) return AppResult.Failure(DomainError.PermissionDenied(uriOrPath))

        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")
            val crc32 = CRC32()
            val total = file.length()
            var processed = 0L

            FileInputStream(file).use { input ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (isCancelled()) return AppResult.Failure(DomainError.Cancelled(file.name))
                    md5.update(buffer, 0, read)
                    sha1.update(buffer, 0, read)
                    sha256.update(buffer, 0, read)
                    crc32.update(buffer, 0, read)
                    processed += read
                    val fraction = if (total > 0) processed.toFloat() / total.toFloat() else 1f
                    onProgress(fraction, processed, total)
                }
            }
            AppResult.Success(
                FileHashReport(
                    uriOrPath = uriOrPath,
                    sizeBytes = total,
                    md5 = md5.digest().toHex(),
                    sha1 = sha1.digest().toHex(),
                    sha256 = sha256.digest().toHex(),
                    crc32Hex = java.lang.Long.toHexString(crc32.value).uppercase().padStart(8, '0')
                )
            )
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Hash calculation error"))
        }
    }

    override fun search(
        rootUriOrPath: String,
        nameQuery: String,
        extensionFilter: String,
        minSizeBytes: Long,
        isCancelled: () -> Boolean
    ): AppResult<List<FileEntry>> {
        val root = File(rootUriOrPath)
        if (!root.exists() || !root.isDirectory) return AppResult.Failure(DomainError.NotFound(rootUriOrPath))
        val results = mutableListOf<FileEntry>()
        val normalizedQuery = nameQuery.trim().lowercase()
        val normalizedExt = extensionFilter.trim().removePrefix(".").lowercase()

        fun walkDir(dir: File, depth: Int) {
            if (depth > 12 || isCancelled() || results.size >= 500) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (isCancelled()) return
                val matchesName = normalizedQuery.isEmpty() || f.name.lowercase().contains(normalizedQuery)
                val matchesExt = normalizedExt.isEmpty() || f.extension.lowercase() == normalizedExt
                val matchesSize = f.length() >= minSizeBytes
                if (matchesName && matchesExt && matchesSize) {
                    results.add(f.toFileEntry())
                }
                if (f.isDirectory) {
                    walkDir(f, depth + 1)
                }
            }
        }

        walkDir(root, 0)
        if (isCancelled()) return AppResult.Failure(DomainError.Cancelled("Search"))
        return AppResult.Success(results)
    }

    /**
     * FR-ED-004: Default saves write to a `.ym_work` working copy and verify size before optional replacement.
     */
    override fun writeAtomicWorkingCopy(
        targetPath: String,
        bytes: ByteArray,
        replaceOriginal: Boolean
    ): AppResult<FileEntry> {
        val original = File(targetPath)
        val parent = original.parentFile ?: return AppResult.Failure(DomainError.ValidationFailed("Invalid target path"))
        if (!parent.exists() || !parent.canWrite()) {
            return AppResult.Failure(DomainError.PermissionDenied(parent.absolutePath))
        }
        val workingFile = File(parent, "${original.name}.ym_work")
        return try {
            FileOutputStream(workingFile).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            if (workingFile.length() != bytes.size.toLong()) {
                workingFile.delete()
                return AppResult.Failure(DomainError.ValidationFailed("Written working copy size verification failed"))
            }
            if (replaceOriginal) {
                val backupFile = File(parent, "${original.name}.ym_bak")
                if (original.exists()) {
                    original.copyTo(backupFile, overwrite = true)
                }
                if (!workingFile.renameTo(original)) {
                    workingFile.copyTo(original, overwrite = true)
                    workingFile.delete()
                }
                AppResult.Success(
                    original.toFileEntry(),
                    warnings = listOf("تم التحقق من المخرجات وحفظ نسخة احتياطية (${backupFile.name}) قبل تحديث الأصل")
                )
            } else {
                AppResult.Success(workingFile.toFileEntry())
            }
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Failed writing working copy"))
        }
    }

    private fun sanitizeName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == ".." || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("\u0000")) {
            return null
        }
        return trimmed
    }

    companion object {
        fun File.toFileEntry(): FileEntry {
            val ext = extension.lowercase()
            val mime = if (isDirectory) {
                "inode/directory"
            } else {
                when (ext) {
                    "apk" -> "application/vnd.android.package-archive"
                    "dex" -> "application/octet-stream"
                    "arsc" -> "application/octet-stream"
                    "json" -> "application/json"
                    "xml" -> "text/xml"
                    "html", "htm" -> "text/html"
                    "txt", "md", "smali", "kts", "kt", "java", "properties", "csv" -> "text/plain"
                    "zip", "jar" -> "application/zip"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "webp" -> "image/webp"
                    else -> "application/octet-stream"
                }
            }
            return FileEntry(
                uriOrPath = absolutePath,
                name = name.ifEmpty { "/" },
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) (listFiles()?.size?.toLong() ?: 0L) else length(),
                lastModified = lastModified(),
                extension = ext,
                mimeType = mime,
                canRead = canRead(),
                canWrite = canWrite(),
                providerType = StorageProviderType.LOCAL_FS
            )
        }

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}

/**
 * FR-FM-004: SAF Storage Provider. Never assumes a content:// URI is a raw filesystem path.
 */
class SafStorageProvider(private val context: Context) {
    val providerType: StorageProviderType = StorageProviderType.SAF_URI

    fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        supportsStreamingRead = true,
        supportsAtomicTempWrite = false,
        supportsDirectRename = true,
        supportsRandomAccess = false,
        supportsTrashBin = false
    )

    fun isSafUri(uriOrPath: String): Boolean = uriOrPath.startsWith("content://")

    fun inspectUriMetadata(uriString: String): AppResult<FileEntry> {
        if (!isSafUri(uriString)) {
            return AppResult.Failure(DomainError.ValidationFailed("Expected content:// URI"))
        }
        return try {
            val uri = Uri.parse(uriString)
            val resolver: ContentResolver = context.contentResolver
            var displayName = uri.lastPathSegment ?: "saf_document"
            var size = 0L
            val mime = resolver.getType(uri) ?: "application/octet-stream"

            val cursor: Cursor? = resolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) displayName = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
            val ext = displayName.substringAfterLast('.', "")
            AppResult.Success(
                FileEntry(
                    uriOrPath = uriString,
                    name = displayName,
                    isDirectory = isDir,
                    sizeBytes = size,
                    lastModified = System.currentTimeMillis(),
                    extension = ext.lowercase(),
                    mimeType = mime,
                    canRead = true,
                    canWrite = true,
                    providerType = StorageProviderType.SAF_URI
                )
            )
        } catch (se: SecurityException) {
            AppResult.Failure(DomainError.PermissionDenied(se.message ?: uriString))
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: uriString))
        }
    }

    fun openInputStream(uriString: String): AppResult<InputStream> {
        return try {
            val stream = context.contentResolver.openInputStream(Uri.parse(uriString))
                ?: return AppResult.Failure(DomainError.NotFound(uriString))
            AppResult.Success(stream)
        } catch (se: SecurityException) {
            AppResult.Failure(DomainError.PermissionDenied(se.message ?: uriString))
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: uriString))
        }
    }
}
