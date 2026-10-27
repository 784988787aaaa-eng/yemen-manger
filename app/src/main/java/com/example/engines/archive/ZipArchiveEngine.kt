package com.example.engines.archive

import com.example.core.model.AppResult
import com.example.core.model.DomainError
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveEntryInfo(
    val entryPath: String,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32Hex: String,
    val compressionRatio: Double,
    val isSuspiciousTraversal: Boolean,
    val isSuspiciousBombRatio: Boolean
)

data class ArchiveInspectionReport(
    val archivePath: String,
    val totalEntries: Int,
    val totalCompressedBytes: Long,
    val totalUncompressedBytes: Long,
    val entries: List<ArchiveEntryInfo>,
    val securityWarnings: List<String>
)

/**
 * FR-AR-001..006: Streaming ZIP Archive Inspector, Extractor, and Creator
 * with strict Zip Slip path traversal and Zip Bomb expansion ratio safeguards.
 */
class ZipArchiveEngine(
    val maxExpansionRatio: Double = 100.0,
    val maxTotalExtractedBytes: Long = 256L * 1024L * 1024L, // 256 MB per extraction task
    val maxEntryCount: Int = 10_000
) {

    fun inspectArchive(archivePath: String): AppResult<ArchiveInspectionReport> {
        val file = File(archivePath)
        if (!file.exists() || file.isDirectory) return AppResult.Failure(DomainError.NotFound(archivePath))
        if (!file.canRead()) return AppResult.Failure(DomainError.PermissionDenied(archivePath))

        // Check header & encryption flag before parsing
        val headerCheck = checkZipSignatureAndEncryption(file)
        if (headerCheck is AppResult.Failure) return headerCheck

        return try {
            val entries = mutableListOf<ArchiveEntryInfo>()
            val warnings = mutableListOf<String>()
            var totalComp = 0L
            var totalUncomp = 0L

            ZipFile(file).use { zip ->
                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    if (entries.size >= maxEntryCount) {
                        warnings.add("تجاوز عدد المدخلات الحد الأقصى المسموح للمعاينة ($maxEntryCount)")
                        break
                    }
                    val ze = enumEntries.nextElement()
                    val comp = ze.compressedSize.coerceAtLeast(0L)
                    val uncomp = ze.size.coerceAtLeast(0L)
                    totalComp += comp
                    totalUncomp += uncomp

                    val ratio = if (comp > 0) uncomp.toDouble() / comp.toDouble() else 1.0
                    val isTraversal = isUnsafeEntryPath(ze.name)
                    val isBomb = ratio > maxExpansionRatio && uncomp > 1024 * 1024

                    if (isTraversal) {
                        warnings.add("تحذير أمني (Zip Slip): المدخل '${ze.name}' يحتوي على مسار خروج غير آمن!")
                    }
                    if (isBomb) {
                        warnings.add("تحذير أمني (Zip Bomb): المدخل '${ze.name}' يتجاوز نسبة التوسع الآمنة (${ratio.toInt()}:1)")
                    }

                    entries.add(
                        ArchiveEntryInfo(
                            entryPath = ze.name,
                            isDirectory = ze.isDirectory,
                            compressedSize = comp,
                            uncompressedSize = uncomp,
                            crc32Hex = if (ze.crc >= 0) java.lang.Long.toHexString(ze.crc).uppercase().padStart(8, '0') else "N/A",
                            compressionRatio = ratio,
                            isSuspiciousTraversal = isTraversal,
                            isSuspiciousBombRatio = isBomb
                        )
                    )
                }
            }

            AppResult.Success(
                ArchiveInspectionReport(
                    archivePath = archivePath,
                    totalEntries = entries.size,
                    totalCompressedBytes = totalComp,
                    totalUncompressedBytes = totalUncomp,
                    entries = entries,
                    securityWarnings = warnings
                ),
                warnings = warnings
            )
        } catch (e: Exception) {
            AppResult.Failure(DomainError.CorruptInput("تعذر قراءة بنية الأرشيف: ${e.message}"))
        }
    }

    /**
     * FR-AR-002 & FR-AR-005: Safe streaming extraction with canonical path validation
     * and runtime actual decompressed byte counting (never trusting ZIP header sizes alone).
     */
    fun extractEntries(
        archivePath: String,
        destinationDirPath: String,
        selectedEntryPaths: Set<String>? = null, // null means extract all
        onProgress: (Float?, Long, Long?) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): AppResult<List<String>> {
        val archiveFile = File(archivePath)
        if (!archiveFile.exists()) return AppResult.Failure(DomainError.NotFound(archivePath))

        val headerCheck = checkZipSignatureAndEncryption(archiveFile)
        if (headerCheck is AppResult.Failure) return headerCheck

        val destRoot = File(destinationDirPath)
        if (!destRoot.exists() && !destRoot.mkdirs()) {
            return AppResult.Failure(DomainError.PermissionDenied(destinationDirPath))
        }
        val destCanonicalRoot = destRoot.canonicalPath + File.separator

        val extractedFiles = mutableListOf<String>()
        var totalBytesWritten = 0L
        var entryCounter = 0

        return try {
            ZipInputStream(FileInputStream(archiveFile)).use { zis ->
                val buffer = ByteArray(16 * 1024)
                var ze: ZipEntry? = zis.nextEntry
                while (ze != null) {
                    if (isCancelled()) {
                        return AppResult.Failure(DomainError.Cancelled("Archive extraction"), extractedFiles)
                    }
                    entryCounter++
                    if (entryCounter > maxEntryCount) {
                        return AppResult.Failure(
                            DomainError.ValidationFailed("Archive exceeds max entry count ($maxEntryCount)"),
                            extractedFiles
                        )
                    }

                    val entryName = ze.name
                    val shouldExtract = selectedEntryPaths == null || selectedEntryPaths.contains(entryName)
                    if (shouldExtract) {
                        if (isUnsafeEntryPath(entryName)) {
                            return AppResult.Failure(
                                DomainError.ValidationFailed("Zip Slip blocked for unsafe entry: $entryName"),
                                extractedFiles
                            )
                        }
                        val targetFile = File(destRoot, entryName)
                        val targetCanonical = targetFile.canonicalPath
                        if (!targetCanonical.startsWith(destCanonicalRoot) && targetCanonical != destRoot.canonicalPath) {
                            return AppResult.Failure(
                                DomainError.ValidationFailed("Zip Slip canonical boundary violation: $entryName"),
                                extractedFiles
                            )
                        }

                        if (ze.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            var entryBytesWritten = 0L
                            val declaredCompressed = ze.compressedSize.coerceAtLeast(1L)

                            FileOutputStream(targetFile).use { fos ->
                                var read: Int
                                while (zis.read(buffer).also { read = it } != -1) {
                                    if (isCancelled()) {
                                        fos.close()
                                        targetFile.delete()
                                        return AppResult.Failure(DomainError.Cancelled(entryName), extractedFiles)
                                    }
                                    entryBytesWritten += read
                                    totalBytesWritten += read

                                    if (totalBytesWritten > maxTotalExtractedBytes) {
                                        fos.close()
                                        targetFile.delete()
                                        return AppResult.Failure(
                                            DomainError.ValidationFailed("Zip Bomb blocked: total extracted bytes exceeded limit ($maxTotalExtractedBytes bytes)"),
                                            extractedFiles
                                        )
                                    }
                                    if (entryBytesWritten > 1024 * 1024 && ze.compressedSize > 0) {
                                        val liveRatio = entryBytesWritten.toDouble() / declaredCompressed.toDouble()
                                        if (liveRatio > maxExpansionRatio) {
                                            fos.close()
                                            targetFile.delete()
                                            return AppResult.Failure(
                                                DomainError.ValidationFailed("Zip Bomb blocked: expansion ratio exceeded $maxExpansionRatio:1 on $entryName"),
                                                extractedFiles
                                            )
                                        }
                                    }
                                    fos.write(buffer, 0, read)
                                    onProgress(null, totalBytesWritten, maxTotalExtractedBytes)
                                }
                            }
                            extractedFiles.add(targetFile.absolutePath)
                        }
                    }
                    zis.closeEntry()
                    ze = zis.nextEntry
                }
            }
            AppResult.Success(extractedFiles)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.CorruptInput("Extraction failed: ${e.message}"), extractedFiles)
        }
    }

    /**
     * FR-AR-003: Create a new ZIP archive from selected source files/directories.
     */
    fun createZipArchive(
        sourcePaths: List<String>,
        destinationZipPath: String,
        overwrite: Boolean = false,
        onProgress: (Float?, Long, Long?) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): AppResult<String> {
        if (sourcePaths.isEmpty()) {
            return AppResult.Failure(DomainError.ValidationFailed("لم يتم تحديد أي ملفات لإنشاء الأرشيف"))
        }
        val destZip = File(destinationZipPath)
        if (destZip.exists() && !overwrite) {
            return AppResult.Failure(DomainError.DestinationExists(destinationZipPath))
        }
        val tempZip = File(destZip.parentFile, ".${destZip.name}.ym_zip_tmp")
        var totalWritten = 0L

        return try {
            ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
                val buffer = ByteArray(16 * 1024)
                fun addFileOrDir(file: File, entryPrefix: String) {
                    if (isCancelled() || !file.exists()) return
                    val entryName = if (entryPrefix.isEmpty()) file.name else "$entryPrefix/${file.name}"
                    if (file.isDirectory) {
                        val children = file.listFiles().orEmpty()
                        if (children.isEmpty()) {
                            zos.putNextEntry(ZipEntry("$entryName/"))
                            zos.closeEntry()
                        } else {
                            for (child in children) {
                                addFileOrDir(child, entryName)
                            }
                        }
                    } else {
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { fis ->
                            var read: Int
                            while (fis.read(buffer).also { read = it } != -1) {
                                if (isCancelled()) return
                                zos.write(buffer, 0, read)
                                totalWritten += read
                                onProgress(null, totalWritten, null)
                            }
                        }
                        zos.closeEntry()
                    }
                }

                for (path in sourcePaths) {
                    if (isCancelled()) {
                        tempZip.delete()
                        return AppResult.Failure(DomainError.Cancelled("Archive creation"))
                    }
                    addFileOrDir(File(path), "")
                }
            }
            if (destZip.exists() && overwrite) destZip.delete()
            if (!tempZip.renameTo(destZip)) {
                tempZip.copyTo(destZip, overwrite = overwrite)
                tempZip.delete()
            }
            AppResult.Success(destZip.absolutePath)
        } catch (e: Exception) {
            tempZip.delete()
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Failed creating ZIP"))
        }
    }

    fun isUnsafeEntryPath(entryName: String): Boolean {
        if (entryName.isBlank()) return true
        if (entryName.startsWith("/") || entryName.startsWith("\\")) return true
        if (entryName.contains("\\")) return true
        if (entryName.contains("\u0000")) return true
        val segments = entryName.split("/")
        return segments.any { it == ".." }
    }

    private fun checkZipSignatureAndEncryption(file: File): AppResult<Boolean> {
        if (file.length() < 4) {
            return AppResult.Failure(DomainError.CorruptInput("الملف أصغر من ترويسة ZIP صالحة"))
        }
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val b0 = raf.read()
                val b1 = raf.read()
                val b2 = raf.read()
                val b3 = raf.read()
                // PK\x03\x04 or PK\x05\x06 (empty zip)
                val isPk = b0 == 0x50 && b1 == 0x4B && ((b2 == 0x03 && b3 == 0x04) || (b2 == 0x05 && b3 == 0x06))
                if (!isPk) {
                    return AppResult.Failure(DomainError.UnsupportedFormat("الترويسة ليست أرشيف ZIP قياسي (PK\\x03\\x04)"))
                }
                if (file.length() >= 8 && b2 == 0x03 && b3 == 0x04) {
                    raf.seek(6)
                    val gpFlagLow = raf.read()
                    val gpFlagHigh = raf.read()
                    val gpFlags = (gpFlagHigh shl 8) or gpFlagLow
                    if ((gpFlags and 0x01) != 0) {
                        return AppResult.Failure(DomainError.UnsupportedFormat("الأرشيف مشفر بكلمة مرور (Encrypted ZIP bit 0 set)"))
                    }
                }
            }
            AppResult.Success(true)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Header check error"))
        }
    }
}
