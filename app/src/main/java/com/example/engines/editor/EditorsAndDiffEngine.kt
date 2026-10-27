package com.example.engines.editor

import com.example.core.model.AppResult
import com.example.core.model.DomainError
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import kotlin.math.min

enum class SupportedEncoding(val displayName: String, val charset: Charset) {
    UTF_8("UTF-8", Charsets.UTF_8),
    WINDOWS_1256("Windows-1256 (Arabic)", Charset.forName("windows-1256")),
    ISO_8859_1("ISO-8859-1 (Latin)", Charsets.ISO_8859_1),
    UTF_16LE("UTF-16LE", Charsets.UTF_16LE),
    UTF_16BE("UTF-16BE", Charsets.UTF_16BE)
}

data class TextDocumentSession(
    val sourcePath: String,
    val fileName: String,
    val encoding: SupportedEncoding,
    val text: String,
    val isTruncatedReadOnly: Boolean,
    val originalByteSize: Long,
    val undoDepth: Int,
    val redoDepth: Int,
    val maxUndoLimit: Int,
    val isDirty: Boolean
)

/**
 * FR-ED-002: Bounded Undo/Redo history manager. Never claims unbounded memory.
 */
class BoundedUndoRedoStack(
    val maxSteps: Int = 50,
    val maxTotalChars: Int = 2_000_000
) {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var totalStoredChars = 0

    fun pushState(previousText: String) {
        if (undoStack.lastOrNull() == previousText) return
        undoStack.addLast(previousText)
        totalStoredChars += previousText.length
        redoStack.clear()

        while (undoStack.size > maxSteps || (totalStoredChars > maxTotalChars && undoStack.size > 1)) {
            val removed = undoStack.removeFirst()
            totalStoredChars -= removed.length
        }
    }

    fun undo(currentText: String): String? {
        if (undoStack.isEmpty()) return null
        val prev = undoStack.removeLast()
        totalStoredChars -= prev.length
        redoStack.addLast(currentText)
        return prev
    }

    fun redo(currentText: String): String? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(currentText)
        totalStoredChars += currentText.length
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        totalStoredChars = 0
    }

    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size
}

class TextEditorEngine(
    private val maxEditableBytes: Long = 1_048_576L // 1 MB full edit limit; larger opens truncated preview
) {
    fun loadTextFile(
        path: String,
        encoding: SupportedEncoding = SupportedEncoding.UTF_8
    ): AppResult<TextDocumentSession> {
        val file = File(path)
        if (!file.exists() || file.isDirectory) return AppResult.Failure(DomainError.NotFound(path))
        if (!file.canRead()) return AppResult.Failure(DomainError.PermissionDenied(path))

        return try {
            val size = file.length()
            val isTruncated = size > maxEditableBytes
            val bytesToRead = min(size, maxEditableBytes).toInt()
            val buffer = ByteArray(bytesToRead)
            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < bytesToRead) {
                    val r = input.read(buffer, offset, bytesToRead - offset)
                    if (r == -1) break
                    offset += r
                }
            }
            val decoded = String(buffer, encoding.charset)
            val warnings = if (isTruncated) {
                listOf("حجم الملف ($size بايت) يتجاوز حد التحرير الكامل ($maxEditableBytes بايت). تم فتح معاينة قراءة فقط لأول 1 ميجابايت.")
            } else emptyList()

            AppResult.Success(
                TextDocumentSession(
                    sourcePath = path,
                    fileName = file.name,
                    encoding = encoding,
                    text = decoded,
                    isTruncatedReadOnly = isTruncated,
                    originalByteSize = size,
                    undoDepth = 0,
                    redoDepth = 0,
                    maxUndoLimit = 50,
                    isDirty = false
                ),
                warnings = warnings
            )
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Failed loading text file"))
        }
    }

    fun findAndReplace(
        content: String,
        findQuery: String,
        replaceWith: String,
        useRegex: Boolean,
        replaceAll: Boolean = true
    ): AppResult<Pair<String, Int>> {
        if (findQuery.isEmpty()) return AppResult.Success(Pair(content, 0))
        return try {
            if (useRegex) {
                val regex = Regex(findQuery)
                val matches = regex.findAll(content).count()
                if (matches == 0) return AppResult.Success(Pair(content, 0))
                val updated = if (replaceAll) {
                    regex.replace(content, replaceWith)
                } else {
                    regex.replaceFirst(content, replaceWith)
                }
                AppResult.Success(Pair(updated, if (replaceAll) matches else 1))
            } else {
                var count = 0
                var startIndex = 0
                while (true) {
                    val idx = content.indexOf(findQuery, startIndex)
                    if (idx < 0) break
                    count++
                    startIndex = idx + findQuery.length
                    if (!replaceAll) break
                }
                if (count == 0) return AppResult.Success(Pair(content, 0))
                val updated = if (replaceAll) {
                    content.replace(findQuery, replaceWith)
                } else {
                    content.replaceFirst(findQuery, replaceWith)
                }
                AppResult.Success(Pair(updated, count))
            }
        } catch (e: Exception) {
            AppResult.Failure(DomainError.ValidationFailed("Regex syntax error: ${e.message}"))
        }
    }
}

/**
 * FR-ED-003: Windowed Hex Viewer & Patch Engine.
 * Reads fixed pages (default 256 bytes, 16 rows x 16 bytes) via RandomAccessFile.
 */
data class HexRow(
    val offset: Long,
    val offsetHex: String,
    val bytes: List<Byte>,
    val asciiRepresentation: String
)

data class HexPageWindow(
    val filePath: String,
    val fileSize: Long,
    val pageOffset: Long,
    val pageSize: Int,
    val rows: List<HexRow>,
    val stagedPatches: Map<Long, Byte>
)

class HexEngine(val pageSize: Int = 256) {

    fun readWindow(
        path: String,
        pageOffset: Long,
        stagedPatches: Map<Long, Byte> = emptyMap()
    ): AppResult<HexPageWindow> {
        val file = File(path)
        if (!file.exists() || file.isDirectory) return AppResult.Failure(DomainError.NotFound(path))
        if (!file.canRead()) return AppResult.Failure(DomainError.PermissionDenied(path))

        return try {
            val totalSize = file.length()
            val safeOffset = pageOffset.coerceIn(0L, (totalSize - 1).coerceAtLeast(0L))
            val lengthToRead = min(pageSize.toLong(), (totalSize - safeOffset).coerceAtLeast(0L)).toInt()
            val rawBytes = ByteArray(lengthToRead)

            if (lengthToRead > 0) {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(safeOffset)
                    raf.readFully(rawBytes)
                }
            }

            // Apply staged patches in view
            for (i in rawBytes.indices) {
                val absPos = safeOffset + i
                stagedPatches[absPos]?.let { patchedByte ->
                    rawBytes[i] = patchedByte
                }
            }

            val rows = mutableListOf<HexRow>()
            var idx = 0
            while (idx < rawBytes.size) {
                val rowEnd = min(idx + 16, rawBytes.size)
                val slice = rawBytes.slice(idx until rowEnd)
                val rowOffset = safeOffset + idx
                val ascii = buildString {
                    for (b in slice) {
                        val c = b.toInt() and 0xFF
                        append(if (c in 32..126) c.toChar() else '.')
                    }
                }
                rows.add(
                    HexRow(
                        offset = rowOffset,
                        offsetHex = "%08X".format(rowOffset),
                        bytes = slice,
                        asciiRepresentation = ascii
                    )
                )
                idx += 16
            }

            AppResult.Success(
                HexPageWindow(
                    filePath = path,
                    fileSize = totalSize,
                    pageOffset = safeOffset,
                    pageSize = pageSize,
                    rows = rows,
                    stagedPatches = stagedPatches
                )
            )
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Hex read error"))
        }
    }

    /**
     * Searches for a hex pattern (e.g. "50 4B 03 04" or "4D5A") or ASCII string in streaming chunks.
     */
    fun searchBytes(
        path: String,
        query: String,
        isHexPattern: Boolean,
        maxMatches: Int = 50
    ): AppResult<List<Long>> {
        val file = File(path)
        if (!file.exists() || file.isDirectory) return AppResult.Failure(DomainError.NotFound(path))

        val needle: ByteArray = if (isHexPattern) {
            parseHexString(query) ?: return AppResult.Failure(DomainError.ValidationFailed("صيغة بايتات Hex غير صالحة: $query"))
        } else {
            query.toByteArray(Charsets.UTF_8)
        }
        if (needle.isEmpty()) return AppResult.Success(emptyList())

        return try {
            val matches = mutableListOf<Long>()
            FileInputStream(file).use { input ->
                val chunkSize = 32 * 1024
                val overlap = needle.size - 1
                var buffer = ByteArray(chunkSize + overlap)
                var filePos = 0L
                var carryCount = 0

                while (true) {
                    val read = input.read(buffer, carryCount, chunkSize)
                    if (read == -1) break
                    val validLen = carryCount + read
                    for (i in 0..(validLen - needle.size)) {
                        var found = true
                        for (j in needle.indices) {
                            if (buffer[i + j] != needle[j]) {
                                found = false
                                break
                            }
                        }
                        if (found) {
                            matches.add(filePos - carryCount + i)
                            if (matches.size >= maxMatches) return AppResult.Success(matches)
                        }
                    }
                    filePos += read
                    if (validLen >= overlap && overlap > 0) {
                        System.arraycopy(buffer, validLen - overlap, buffer, 0, overlap)
                        carryCount = overlap
                    } else {
                        carryCount = 0
                    }
                }
            }
            AppResult.Success(matches)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Byte search failed"))
        }
    }

    /**
     * FR-ED-004: Writes patched file to `.ym_work` first, verifies length, and replaces original only if requested.
     */
    fun commitPatchesSafely(
        sourcePath: String,
        patches: Map<Long, Byte>,
        replaceOriginal: Boolean
    ): AppResult<String> {
        val src = File(sourcePath)
        if (!src.exists() || src.isDirectory) return AppResult.Failure(DomainError.NotFound(sourcePath))
        if (patches.isEmpty()) return AppResult.Failure(DomainError.ValidationFailed("لا توجد تعديلات بايتات معلقة للحفظ"))

        val parent = src.parentFile ?: return AppResult.Failure(DomainError.PermissionDenied(sourcePath))
        val workingFile = File(parent, "${src.name}.ym_work")
        return try {
            src.copyTo(workingFile, overwrite = true)
            RandomAccessFile(workingFile, "rw").use { raf ->
                val len = raf.length()
                for ((offset, byteVal) in patches) {
                    if (offset < 0 || offset >= len) {
                        return AppResult.Failure(DomainError.ValidationFailed("Offset $offset out of bounds (0..$len)"))
                    }
                    raf.seek(offset)
                    raf.writeByte(byteVal.toInt() and 0xFF)
                }
                raf.fd.sync()
            }
            if (replaceOriginal) {
                val backup = File(parent, "${src.name}.ym_bak")
                src.copyTo(backup, overwrite = true)
                workingFile.copyTo(src, overwrite = true)
                workingFile.delete()
                AppResult.Success(src.absolutePath, warnings = listOf("تم حفظ نسخة احتياطية ${backup.name} وتحديث الملف الأصلي"))
            } else {
                AppResult.Success(workingFile.absolutePath)
            }
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Patch commit failed"))
        }
    }

    companion object {
        fun parseHexString(hex: String): ByteArray? {
            val cleaned = hex.replace(" ", "").replace("0x", "").replace(",", "").trim()
            if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
            val out = ByteArray(cleaned.length / 2)
            for (i in out.indices) {
                val pair = cleaned.substring(i * 2, i * 2 + 2)
                val v = pair.toIntOrNull(16) ?: return null
                out[i] = v.toByte()
            }
            return out
        }
    }
}

/**
 * FR-ED-005: Text & Binary Diff Engine with bounded memory checks.
 */
enum class DiffLineType { UNCHANGED, ADDED, REMOVED }

data class DiffLine(
    val type: DiffLineType,
    val leftLineNumber: Int?,
    val rightLineNumber: Int?,
    val content: String
)

data class BinaryDiffSummary(
    val leftSize: Long,
    val rightSize: Long,
    val differingBytesCount: Long,
    val firstDiffOffset: Long?,
    val sampleByteDifferences: List<String>
)

data class FileComparisonReport(
    val leftPath: String,
    val rightPath: String,
    val isTextComparison: Boolean,
    val textLines: List<DiffLine>,
    val addedCount: Int,
    val removedCount: Int,
    val unchangedCount: Int,
    val binarySummary: BinaryDiffSummary,
    val limitNote: String? = null
)

class DiffEngine(private val maxTextLines: Int = 600) {

    fun compareFiles(leftPath: String, rightPath: String): AppResult<FileComparisonReport> {
        val left = File(leftPath)
        val right = File(rightPath)
        if (!left.exists()) return AppResult.Failure(DomainError.NotFound(leftPath))
        if (!right.exists()) return AppResult.Failure(DomainError.NotFound(rightPath))

        return try {
            val binSummary = computeBinaryDiffStreaming(left, right)
            val isText = isLikelyText(left) && isLikelyText(right) && left.length() <= 512_000 && right.length() <= 512_000
            if (!isText) {
                return AppResult.Success(
                    FileComparisonReport(
                        leftPath = leftPath,
                        rightPath = rightPath,
                        isTextComparison = false,
                        textLines = emptyList(),
                        addedCount = 0,
                        removedCount = 0,
                        unchangedCount = 0,
                        binarySummary = binSummary,
                        limitNote = "تم إجراء مقارنة ثنائية متدفقة (Binary Diff) نظراً لنوع الملف أو حجمه."
                    )
                )
            }

            val leftLinesAll = left.readLines(Charsets.UTF_8)
            val rightLinesAll = right.readLines(Charsets.UTF_8)
            val truncated = leftLinesAll.size > maxTextLines || rightLinesAll.size > maxTextLines
            val a = leftLinesAll.take(maxTextLines)
            val b = rightLinesAll.take(maxTextLines)

            val diffLines = computeLcsLines(a, b)
            val added = diffLines.count { it.type == DiffLineType.ADDED }
            val removed = diffLines.count { it.type == DiffLineType.REMOVED }
            val unchanged = diffLines.count { it.type == DiffLineType.UNCHANGED }

            AppResult.Success(
                FileComparisonReport(
                    leftPath = leftPath,
                    rightPath = rightPath,
                    isTextComparison = true,
                    textLines = diffLines,
                    addedCount = added,
                    removedCount = removed,
                    unchangedCount = unchanged,
                    binarySummary = binSummary,
                    limitNote = if (truncated) "تم اقتصار المقارنة السطرية على أول $maxTextLines سطر للحفاظ على الذاكرة." else null
                )
            )
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure(e.message ?: "Diff failed"))
        }
    }

    private fun computeBinaryDiffStreaming(left: File, right: File): BinaryDiffSummary {
        var diffCount = 0L
        var firstOffset: Long? = null
        val samples = mutableListOf<String>()
        var pos = 0L

        FileInputStream(left).use { inA ->
            FileInputStream(right).use { inB ->
                val bufA = ByteArray(8192)
                val bufB = ByteArray(8192)
                while (true) {
                    val rA = inA.read(bufA)
                    val rB = inB.read(bufB)
                    if (rA == -1 && rB == -1) break
                    val common = min(rA.coerceAtLeast(0), rB.coerceAtLeast(0))
                    for (i in 0 until common) {
                        if (bufA[i] != bufB[i]) {
                            diffCount++
                            val absOffset = pos + i
                            if (firstOffset == null) firstOffset = absOffset
                            if (samples.size < 8) {
                                samples.add("0x%06X: %02X ≠ %02X".format(absOffset, bufA[i].toInt() and 0xFF, bufB[i].toInt() and 0xFF))
                            }
                        }
                    }
                    val extra = kotlin.math.abs(rA.coerceAtLeast(0) - rB.coerceAtLeast(0))
                    if (extra > 0) {
                        diffCount += extra
                        if (firstOffset == null) firstOffset = pos + common
                    }
                    pos += kotlin.math.max(rA.coerceAtLeast(0), rB.coerceAtLeast(0))
                }
            }
        }
        return BinaryDiffSummary(
            leftSize = left.length(),
            rightSize = right.length(),
            differingBytesCount = diffCount,
            firstDiffOffset = firstOffset,
            sampleByteDifferences = samples
        )
    }

    private fun computeLcsLines(a: List<String>, b: List<String>): List<DiffLine> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            if (a[i] == b[j]) {
                out.add(DiffLine(DiffLineType.UNCHANGED, i + 1, j + 1, a[i]))
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add(DiffLine(DiffLineType.REMOVED, i + 1, null, a[i]))
                i++
            } else {
                out.add(DiffLine(DiffLineType.ADDED, null, j + 1, b[j]))
                j++
            }
        }
        while (i < m) {
            out.add(DiffLine(DiffLineType.REMOVED, i + 1, null, a[i]))
            i++
        }
        while (j < n) {
            out.add(DiffLine(DiffLineType.ADDED, null, j + 1, b[j]))
            j++
        }
        return out
    }

    private fun isLikelyText(file: File): Boolean {
        if (file.length() == 0L) return true
        val sample = ByteArray(min(512L, file.length()).toInt())
        FileInputStream(file).use { it.read(sample) }
        return sample.none { it == 0.toByte() }
    }
}
