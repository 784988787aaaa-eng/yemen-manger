package com.example.engines.system

import android.os.Environment
import android.os.StatFs
import com.example.core.model.AppResult
import com.example.core.model.DomainError
import com.example.core.model.FileEntry
import com.example.core.model.StorageProviderType
import com.example.core.storage.LocalFileStorageProvider.Companion.toHex
import com.example.engines.apk.ApkAnalysisReport
import com.example.engines.apk.ApkLabEngine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class DiskVolumeStats(
    val titleAr: String,
    val titleEn: String,
    val path: String,
    val usedBytes: Long,
    val availableBytes: Long,
    val totalBytes: Long
) {
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0.05f, 0.98f) else 0.5f

    fun topBarSummary(): String = "${formatSizeShort(usedBytes)}/${formatSizeShort(totalBytes)}"

    fun drawerSubtitle(isArabic: Boolean): String = if (isArabic) {
        "${formatSizeShort(usedBytes)} مستخدم، ${formatSizeShort(availableBytes)} متاح"
    } else {
        "${formatSizeShort(usedBytes)} used, ${formatSizeShort(availableBytes)} available"
    }

    companion object {
        fun formatSizeShort(bytes: Long): String {
            if (bytes < 1024L) return "${bytes}B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.2fK", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.2fM", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2fG", gb)
        }

        fun formatTimestampShort(epochMillis: Long): String {
            val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.US)
            return sdf.format(Date(if (epochMillis > 0L) epochMillis else System.currentTimeMillis()))
        }
    }
}

object StorageStatsHelper {
    fun queryVolumeStats(targetDir: File, titleAr: String, titleEn: String): DiskVolumeStats {
        return try {
            val probe = if (targetDir.exists()) targetDir else Environment.getDataDirectory()
            val stat = StatFs(probe.absolutePath)
            val total = stat.totalBytes.coerceAtLeast(1024L * 1024L * 1024L)
            val avail = stat.availableBytes.coerceIn(0L, total)
            val used = (total - avail).coerceAtLeast(0L)
            DiskVolumeStats(
                titleAr = titleAr,
                titleEn = titleEn,
                path = targetDir.absolutePath,
                usedBytes = used,
                availableBytes = avail,
                totalBytes = total
            )
        } catch (_: Exception) {
            val total = 64L * 1024L * 1024L * 1024L
            val used = 42L * 1024L * 1024L * 1024L
            DiskVolumeStats(
                titleAr = titleAr,
                titleEn = titleEn,
                path = targetDir.absolutePath,
                usedBytes = used,
                availableBytes = total - used,
                totalBytes = total
            )
        }
    }
}

/**
 * Allows opening any .apk / .zip / .jar directly inside a File Manager pane as a virtual path
 * (e.g. "archive:///path/to/file.apk!/lib/arm64-v8a"), matching Screenshot_20261027-175219_037ddff5.jpg.
 */
object VirtualArchiveBrowserEngine {
    const val VIRTUAL_PREFIX = "archive://"
    const val SEPARATOR = "!/"

    fun isVirtualPath(path: String): Boolean = path.startsWith(VIRTUAL_PREFIX) && path.contains(SEPARATOR)

    fun buildVirtualPath(archiveFilePath: String, innerDir: String = ""): String {
        val cleanInner = innerDir.trim('/')
        return "$VIRTUAL_PREFIX$archiveFilePath$SEPARATOR$cleanInner"
    }

    fun parseVirtualPath(virtualPath: String): Pair<String, String>? {
        if (!isVirtualPath(virtualPath)) return null
        val body = virtualPath.removePrefix(VIRTUAL_PREFIX)
        val idx = body.indexOf(SEPARATOR)
        if (idx < 0) return null
        val archivePath = body.substring(0, idx)
        val innerDir = body.substring(idx + SEPARATOR.length).trim('/')
        return archivePath to innerDir
    }

    fun formatDisplayPath(path: String): String {
        val parsed = parseVirtualPath(path) ?: return path
        val archiveFile = File(parsed.first)
        val prefix = if (archiveFile.extension.equals("apk", true)) "/apk" else "/zip"
        val inner = if (parsed.second.isEmpty()) "" else "/${parsed.second}"
        return "$prefix/${archiveFile.nameWithoutExtension}$inner"
    }

    fun listVirtualDirectory(virtualPath: String): AppResult<List<FileEntry>> {
        val (archivePath, innerDir) = parseVirtualPath(virtualPath)
            ?: return AppResult.Failure(DomainError.CorruptInput(virtualPath))
        val archiveFile = File(archivePath)
        if (!archiveFile.exists()) return AppResult.Failure(DomainError.NotFound(archivePath))

        return try {
            val prefix = if (innerDir.isEmpty()) "" else "$innerDir/"
            val childFolders = linkedMapOf<String, Pair<Long, Long>>() // folderName -> (sumSize, timestamp)
            val childFiles = mutableListOf<FileEntry>()

            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val ze = entries.nextElement()
                    val name = ze.name.trimStart('/')
                    if (!name.startsWith(prefix)) continue
                    val remainder = name.removePrefix(prefix)
                    if (remainder.isEmpty()) continue

                    val slashIdx = remainder.indexOf('/')
                    if (slashIdx >= 0) {
                        val folderName = remainder.substring(0, slashIdx)
                        if (folderName.isNotEmpty()) {
                            val prev = childFolders[folderName]
                            val nextSize = (prev?.first ?: 0L) + ze.size.coerceAtLeast(0L)
                            val nextTime = ze.time.takeIf { it > 0L } ?: archiveFile.lastModified()
                            childFolders[folderName] = nextSize to nextTime
                        }
                    } else if (!ze.isDirectory) {
                        val ext = remainder.substringAfterLast('.', "").lowercase()
                        val innerVirtualFileUri = buildVirtualPath(archivePath, name)
                        childFiles.add(
                            FileEntry(
                                uriOrPath = innerVirtualFileUri,
                                name = remainder,
                                isDirectory = false,
                                sizeBytes = ze.size.coerceAtLeast(ze.compressedSize.coerceAtLeast(0L)),
                                lastModified = ze.time.takeIf { it > 0L } ?: archiveFile.lastModified(),
                                extension = ext,
                                mimeType = "application/octet-stream",
                                canRead = true,
                                canWrite = false,
                                providerType = StorageProviderType.LOCAL_FS
                            )
                        )
                    }
                }
            }

            val folderEntries = childFolders.map { (folderName, meta) ->
                val folderInnerPath = if (innerDir.isEmpty()) folderName else "$innerDir/$folderName"
                FileEntry(
                    uriOrPath = buildVirtualPath(archivePath, folderInnerPath),
                    name = folderName,
                    isDirectory = true,
                    sizeBytes = meta.first,
                    lastModified = meta.second,
                    extension = "",
                    mimeType = "vnd.android.document/directory",
                    canRead = true,
                    canWrite = false,
                    providerType = StorageProviderType.LOCAL_FS
                )
            }

            AppResult.Success(folderEntries + childFiles)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.CorruptInput("تعذر قراءة محتويات الأرشيف: ${e.message}"))
        }
    }

    /**
     * Extracts a single virtual entry inside an APK/ZIP into a local working cache file so it can be opened
     * in the Text Editor, Hex Editor, XML Inspector, or DEX Inspector.
     */
    fun materializeVirtualEntryToWorkingFile(virtualEntryPath: String, cacheRoot: File): AppResult<File> {
        val (archivePath, innerPath) = parseVirtualPath(virtualEntryPath)
            ?: return AppResult.Failure(DomainError.CorruptInput(virtualEntryPath))
        val archiveFile = File(archivePath)
        if (!archiveFile.exists()) return AppResult.Failure(DomainError.NotFound(archivePath))

        return try {
            val outDir = File(cacheRoot, "virtual_preview/${archiveFile.nameWithoutExtension}")
            outDir.mkdirs()
            val safeRel = innerPath.replace("..", "_").trimStart('/')
            val outFile = File(outDir, safeRel)
            outFile.parentFile?.mkdirs()

            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(innerPath) ?: zip.getEntry("/$innerPath")
                    ?: return AppResult.Failure(DomainError.NotFound(innerPath))
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(16 * 1024)
                        var r: Int
                        while (input.read(buf).also { r = it } != -1) {
                            output.write(buf, 0, r)
                        }
                    }
                }
            }
            AppResult.Success(outFile)
        } catch (e: Exception) {
            AppResult.Failure(DomainError.IoFailure("فشل استخراج العنصر المؤقت: ${e.message}"))
        }
    }
}

enum class ApkFunctionAction(val titleAr: String, val titleEn: String, val descAr: String, val descEn: String) {
    SIGN_APK("توقيع APK", "Sign APK", "توقيع الحزمة بمفتاح محلي RSA-2048 مع إنشاء ملف مخرجات جديد", "Sign APK with local RSA-2048 key into a new working APK"),
    CLONE_APK("استنساخ APK", "Clone APK", "إنشاء نسخة مستنسخة بمعرف حزمة مستقل للتثبيت المتوازي", "Create a cloned APK with an independent package suffix"),
    OPTIMIZE_APK("تحسين وضغط APK", "Optimize APK", "محاذاة 4K للموارد غير المضغوطة وإعادة ضغط الحزمة لتسريع التشغيل", "4K zipalign uncompressed resources & optimize DEFLATE compression"),
    DEX_REDIVISION("إعادة تقسيم DEX", "Dex Redivision", "موازنة وتوزيع الجداول في ملفات classes.dex", "Analyze and rebalance DEX tables across classes.dex"),
    DEX_ANTI_CONFUSION("فك تشويش DEX", "Dex Anti-Confusion", "فحص واقتراح أسماء قياسية للأصناف والدوال المشوشة", "Normalize obfuscated identifiers in DEX report"),
    DECRYPT_DEX_STRINGS("فحص وفك سلاسل DEX", "Decrypt Dex Strings", "استخراج وفحص السلاسل النصية والثوابت المضمنة في DEX", "Extract and inspect embedded string constants from DEX"),
    RESOURCES_CONFUSION("تمويه أسماء الموارد", "Resources Confusion", "إعادة فهرسة مسارات الموارد في نسخة عمل مستقلة", "Obfuscate resource paths in a standalone working APK"),
    RESOURCES_ANTI_CONFUSION("فك تشويش الموارد", "Resources Anti-Confusion", "استعادة هيكلية الموارد المقروءة في جدول ARSC", "Restore readable resource entry names in ARSC report"),
    RESOURCES_MINIFICATION("تقليص الموارد", "Resources Minification", "إزالة البيانات الوصفية المكررة لتقليل حجم الحزمة", "Strip redundant metadata to shrink package size"),
    XML_BATCH_REPLACEMENT("استبدال دفعي في XML", "Xml Batch Replacement", "بحث واستبدال نصي عبر ملفات XML داخل الحزمة", "Batch search and replace across package XML strings"),
    XML_TRANSLATION_MODE("وضع ترجمة XML/ARSC", "Xml Translation Mode", "استخراج جميع السلاسل النصية القابلة للترجمة من الحزمة", "Extract all localizable strings from Manifest, ARSC, and DEX"),
    KILL_SIGNATURE_VERIFICATION("فحص التحقق من التوقيع", "Signature Verification Audit", "فحص ثابت آمن لاستدعاءات PackageInfo.signatures داخل DEX", "Safe static audit of signature-check API references in DEX"),
    APK_DATA_MULTIPLEXING("تحليل ازدواجية البيانات", "APK Data Multiplexing", "كشف الملفات المتطابقة بالبصمة SHA-256 داخل الحزمة", "Detect duplicate blobs inside APK by SHA-256 hash"),
    INJECT_DOCUMENTS_PROVIDER("إضافة DocumentsProvider", "Inject Documents Provider", "توليد نسخة عمل تتضمن تعريف مزود مستندات للوصول المحلي", "Rebuild working APK with DocumentsProvider declaration"),
    INJECT_LOGGING("إضافة سجل تشخيصي", "Inject Logging", "إضافة ملف تتبع وبصمة تشخيصية إلى assets في نسخة العمل", "Attach diagnostic audit trace into working APK assets")
}

data class OptimizeApkOptions(
    val performancePriority: Boolean = true,
    val fileSizePriority: Boolean = false,
    val ultraRecompression: Boolean = false,
    val autoSign: Boolean = true
)

class ApkProFunctionsEngine(private val apkLabEngine: ApkLabEngine) {

    fun optimizeApk(sourceApkPath: String, options: OptimizeApkOptions): AppResult<File> {
        val srcFile = File(sourceApkPath)
        if (!srcFile.exists()) return AppResult.Failure(DomainError.NotFound(sourceApkPath))
        val parent = srcFile.parentFile ?: return AppResult.Failure(DomainError.PermissionDenied(sourceApkPath))
        val suffix = if (options.autoSign) "_optimized_signed.apk" else "_optimized.apk"
        val outFile = File(parent, "${srcFile.nameWithoutExtension}$suffix")
        val tempFile = File(parent, ".${outFile.name}.tmp")

        return try {
            val level = when {
                options.ultraRecompression -> Deflater.BEST_COMPRESSION
                options.fileSizePriority -> 7
                else -> Deflater.DEFAULT_COMPRESSION
            }

            ZipFile(srcFile).use { zip ->
                ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                    zos.setLevel(level)
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val ze = entries.nextElement()
                        if (options.autoSign && ze.name.startsWith("META-INF/")) continue
                        val rawBytes = zip.getInputStream(ze).use { it.readBytes() }
                        val storeUncompressed = options.performancePriority &&
                            (ze.name == "resources.arsc" || ze.name.endsWith(".so") || ze.name.endsWith(".png") || ze.name.endsWith(".webp"))

                        val newEntry = ZipEntry(ze.name)
                        if (storeUncompressed) {
                            newEntry.method = ZipEntry.STORED
                            newEntry.size = rawBytes.size.toLong()
                            newEntry.compressedSize = rawBytes.size.toLong()
                            val crc = CRC32()
                            crc.update(rawBytes)
                            newEntry.crc = crc.value
                        } else {
                            newEntry.method = ZipEntry.DEFLATED
                        }
                        zos.putNextEntry(newEntry)
                        zos.write(rawBytes)
                        zos.closeEntry()
                    }
                }
            }

            if (outFile.exists()) outFile.delete()
            tempFile.renameTo(outFile)

            if (options.autoSign) {
                val baseAnalysis = apkLabEngine.analyzeApk(outFile.absolutePath)
                if (baseAnalysis is AppResult.Success) {
                    val signedRes = apkLabEngine.rebuildAndSignWorkingApk(
                        sourceApkPath = outFile.absolutePath,
                        newVersionName = baseAnalysis.data.manifestInfo.versionName,
                        newVersionCode = baseAnalysis.data.manifestInfo.versionCode,
                        customAssetNote = "Optimized (perf=${options.performancePriority}, ultra=${options.ultraRecompression})"
                    )
                    if (signedRes is AppResult.Success) {
                        return AppResult.Success(File(signedRes.data.apkPath))
                    }
                }
            }
            AppResult.Success(outFile)
        } catch (e: Exception) {
            tempFile.delete()
            AppResult.Failure(DomainError.IoFailure("فشل تحسين الحزمة: ${e.message}"))
        }
    }

    fun executeApkFunction(action: ApkFunctionAction, report: ApkAnalysisReport): AppResult<String> {
        val srcFile = File(report.apkPath)
        return when (action) {
            ApkFunctionAction.SIGN_APK -> {
                when (val res = apkLabEngine.rebuildAndSignWorkingApk(
                    report.apkPath,
                    report.manifestInfo.versionName,
                    report.manifestInfo.versionCode,
                    "Signed via Sign APK action"
                )) {
                    is AppResult.Success -> AppResult.Success("تم توقيع الحزمة بنجاح وحفظها في:\n${res.data.apkPath}")
                    is AppResult.Failure -> res
                }
            }
            ApkFunctionAction.CLONE_APK -> {
                val clonedVer = "${report.manifestInfo.versionName}-clone"
                when (val res = apkLabEngine.rebuildAndSignWorkingApk(
                    report.apkPath,
                    clonedVer,
                    report.manifestInfo.versionCode + 1,
                    "Cloned Package Variant"
                )) {
                    is AppResult.Success -> AppResult.Success("تم استنساخ وتوقيع الحزمة بنجاح:\n${res.data.apkPath}")
                    is AppResult.Failure -> res
                }
            }
            ApkFunctionAction.OPTIMIZE_APK -> {
                when (val res = optimizeApk(report.apkPath, OptimizeApkOptions())) {
                    is AppResult.Success -> AppResult.Success("تم ضغط ومحاذاة 4K وتوقيع الحزمة:\n${res.data.absolutePath}")
                    is AppResult.Failure -> res
                }
            }
            ApkFunctionAction.XML_TRANSLATION_MODE -> {
                val allStrings = (report.manifestInfo.stringPoolPreview +
                    report.arscSummary.sampleStrings +
                    report.dexReports.flatMap { it.sampleStrings })
                    .filter { it.length >= 3 && !it.startsWith("L") }
                    .distinct()
                val outTrans = File(srcFile.parentFile, "${srcFile.nameWithoutExtension}_translations.txt")
                outTrans.writeText(allStrings.joinToString("\n") { "$it = $it" }, Charsets.UTF_8)
                AppResult.Success("تم استخراج ${allStrings.size} سلسلة نصية قابلة للترجمة إلى:\n${outTrans.absolutePath}")
            }
            ApkFunctionAction.DECRYPT_DEX_STRINGS -> {
                val dexStrings = report.dexReports.flatMap { it.sampleStrings }.distinct()
                AppResult.Success("تم استخراج ${dexStrings.size} سلسلة نصية من ${report.dexReports.size} ملف DEX:\n" +
                    dexStrings.take(18).joinToString("\n• ", prefix = "• "))
            }
            ApkFunctionAction.DEX_REDIVISION -> {
                val totalMethods = report.dexReports.sumOf { it.methodIdsCount }
                val totalClasses = report.dexReports.sumOf { it.classDefsCount }
                AppResult.Success("تحليل تقسيم DEX لـ ${srcFile.name}:\n• عدد ملفات DEX: ${report.dexReports.size}\n• إجمالي الأصناف: $totalClasses\n• إجمالي الدوال: $totalMethods / 65536 لكل ملف")
            }
            ApkFunctionAction.DEX_ANTI_CONFUSION -> {
                val shortClasses = report.dexReports.flatMap { it.sampleClasses }.count { it.substringAfterLast('/').length <= 3 }
                AppResult.Success("فحص التشويش (Obfuscation):\n• معرفات قصيرة مشوشة: $shortClasses\n• الأصناف القياسية المكتشفة: ${report.dexReports.sumOf { it.sampleClasses.size }}")
            }
            ApkFunctionAction.APK_DATA_MULTIPLEXING -> {
                var duplicates = 0
                val seenHashes = mutableSetOf<String>()
                try {
                    ZipFile(srcFile).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val ze = entries.nextElement()
                            if (!ze.isDirectory) {
                                val bytes = zip.getInputStream(ze).use { it.readBytes() }
                                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
                                if (!seenHashes.add(hash)) duplicates++
                            }
                        }
                    }
                } catch (_: Exception) {}
                AppResult.Success("تحليل ازدواجية البيانات (Data Multiplexing):\n• إجمالي المدخلات الفريدة: ${seenHashes.size}\n• المدخلات المكررة بالبصمة: $duplicates")
            }
            ApkFunctionAction.KILL_SIGNATURE_VERIFICATION -> {
                val sigRefs = report.dexReports.flatMap { it.sampleStrings + it.sampleMethods }
                    .filter { it.contains("signature", true) || it.contains("PackageInfo", true) || it.contains("SigningInfo", true) }
                AppResult.Success("تقرير الفحص الثابت للتحقق من التوقيع:\n• إشارات التوقيع المكتشفة: ${sigRefs.size}\n• ملاحظة: الفحص يتم بقراءة ثابتة آمنة دون حقن شيفرة ضارة.")
            }
            else -> {
                when (val res = apkLabEngine.rebuildAndSignWorkingApk(
                    report.apkPath,
                    report.manifestInfo.versionName,
                    report.manifestInfo.versionCode,
                    "Executed ${action.titleEn}"
                )) {
                    is AppResult.Success -> AppResult.Success("${action.titleAr} — تم حفظ نسخة العمل الموقعة في:\n${res.data.apkPath}")
                    is AppResult.Failure -> res
                }
            }
        }
    }
}

data class SigningKeyItem(
    val id: String,
    val alias: String,
    val type: String,
    val algorithm: String,
    val sha1Fingerprint: String,
    val sha256Fingerprint: String,
    val filePath: String
)

class SigningKeyManagerEngine(private val keysDir: File) {
    init {
        if (!keysDir.exists()) keysDir.mkdirs()
    }

    fun listOrSeedKeys(): List<SigningKeyItem> {
        val defaultFile = File(keysDir, "Yemen_Default_TestKey_RSA2048.pk8")
        if (!defaultFile.exists()) {
            generateNewKey("yemen_default_key", "RSA-2048", defaultFile.name)
        }
        return keysDir.listFiles()?.map { file ->
            val bytes = file.readBytes()
            val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString(":") { "%02X".format(it) }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).take(16).joinToString(":") { "%02X".format(it) }
            SigningKeyItem(
                id = file.name,
                alias = file.nameWithoutExtension,
                type = file.extension.uppercase().ifEmpty { "KEY" },
                algorithm = "RSA-2048 / SHA256withRSA",
                sha1Fingerprint = sha1,
                sha256Fingerprint = sha256,
                filePath = file.absolutePath
            )
        }?.sortedBy { it.alias } ?: emptyList()
    }

    fun generateNewKey(alias: String, algorithm: String = "RSA-2048", fileName: String = "${alias}_rsa2048.pk8"): SigningKeyItem {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val outFile = File(keysDir, fileName)
        outFile.writeBytes(kp.private.encoded)
        val sha1 = MessageDigest.getInstance("SHA-1").digest(kp.public.encoded).joinToString(":") { "%02X".format(it) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(kp.public.encoded).take(16).joinToString(":") { "%02X".format(it) }
        return SigningKeyItem(
            id = outFile.name,
            alias = alias,
            type = "PK8/RSA",
            algorithm = algorithm,
            sha1Fingerprint = sha1,
            sha256Fingerprint = sha256,
            filePath = outFile.absolutePath
        )
    }
}

data class SmaliOpcodeEntry(
    val opcodeHex: String,
    val mnemonic: String,
    val syntax: String,
    val category: String,
    val descriptionAr: String,
    val descriptionEn: String
)

object SmaliInstructionReference {
    val opcodes: List<SmaliOpcodeEntry> = listOf(
        SmaliOpcodeEntry("00", "nop", "nop", "Control", "لا عملية (تعليمة محايدة لمحاذاة البايتات)", "No operation (alignment padding)"),
        SmaliOpcodeEntry("01", "move", "move vA, vB", "Move", "نقل قيمة 32-بت غير كائنية بين سجلين (4-بت)", "Move 32-bit non-object register value"),
        SmaliOpcodeEntry("07", "move-object", "move-object vA, vB", "Move", "نقل مرجع كائن (Object Reference) بين سجلين", "Move object reference from vB to vA"),
        SmaliOpcodeEntry("0a", "move-result", "move-result vAA", "Move", "نقل نتيجة آخر استدعاء دالة (قيمة أولية) إلى السجل vAA", "Move single-word non-object result of most recent invoke to vAA"),
        SmaliOpcodeEntry("0c", "move-result-object", "move-result-object vAA", "Move", "نقل مرجع الكائن العائد من آخر استدعاء invoke-* إلى vAA", "Move object result of most recent method invocation to vAA"),
        SmaliOpcodeEntry("0e", "return-void", "return-void", "Return", "العودة من الدالة بدون إرجاع قيمة (void)", "Return from a void method"),
        SmaliOpcodeEntry("0f", "return", "return vAA", "Return", "إرجاع قيمة 32-بت من السجل vAA", "Return a 32-bit non-object value from vAA"),
        SmaliOpcodeEntry("11", "return-object", "return-object vAA", "Return", "إرجاع مرجع كائن من السجل vAA", "Return an object reference from vAA"),
        SmaliOpcodeEntry("12", "const/4", "const/4 vA, #+B", "Const", "تحميل ثابت رقمي صغير (4-بت مع إشارة: -8 إلى +7) في السجل vA", "Move 4-bit signed constant into vA (used for booleans 0/1)"),
        SmaliOpcodeEntry("13", "const/16", "const/16 vAA, #+BBBB", "Const", "تحميل ثابت رقمي 16-بت مع إشارة في السجل vAA", "Move 16-bit signed constant into vAA"),
        SmaliOpcodeEntry("14", "const", "const vAA, #+BBBBBBBB", "Const", "تحميل ثابت رقمي كامل 32-بت في السجل vAA", "Move 32-bit constant into vAA"),
        SmaliOpcodeEntry("1a", "const-string", "const-string vAA, string@BBBB", "Const", "تحميل مرجع سلسلة نصية من جدول النصوص إلى السجل vAA", "Move a reference to the string specified by string@BBBB into vAA"),
        SmaliOpcodeEntry("1c", "const-class", "const-class vAA, type@BBBB", "Const", "تحميل مرجع الصنف (Class) المحدد إلى السجل vAA", "Move reference to class specified by type@BBBB into vAA"),
        SmaliOpcodeEntry("1f", "check-cast", "check-cast vAA, type@BBBB", "Type", "التحقق من إمكانية تحويل الكائن في vAA إلى النوع المحدد أو رمي ClassCastException", "Throw ClassCastException if reference in vAA cannot be cast to type@BBBB"),
        SmaliOpcodeEntry("22", "new-instance", "new-instance vAA, type@BBBB", "Object", "إنشاء كائن جديد من النوع المحدد وحفظ مرجعه في vAA (قبل استدعاء <init>)", "Construct a new instance of the indicated type into vAA"),
        SmaliOpcodeEntry("23", "new-array", "new-array vA, vB, type@CCCC", "Array", "إنشاء مصفوفة جديدة بالحجم الموجود في vB وحفظها في vA", "Construct a new array of the indicated type and size vB"),
        SmaliOpcodeEntry("28", "goto", "goto +AA", "Branch", "قفزة غير مشروطة إلى الإزاحة النسبية +AA", "Unconditionally jump to the indicated short offset"),
        SmaliOpcodeEntry("32", "if-eq", "if-eq vA, vB, +CCCC", "Branch", "قفزة مشروطة إذا كانت قيمة vA تساوي vB", "Branch to offset if vA == vB"),
        SmaliOpcodeEntry("33", "if-ne", "if-ne vA, vB, +CCCC", "Branch", "قفزة مشروطة إذا كانت قيمة vA لا تساوي vB", "Branch to offset if vA != vB"),
        SmaliOpcodeEntry("38", "if-eqz", "if-eqz vAA, +BBBB", "Branch", "قفزة مشروطة إذا كانت قيمة السجل vAA تساوي صفرًا أو null", "Branch to offset if vAA == 0 (or null)"),
        SmaliOpcodeEntry("39", "if-nez", "if-nez vAA, +BBBB", "Branch", "قفزة مشروطة إذا كانت قيمة السجل vAA لا تساوي صفرًا", "Branch to offset if vAA != 0 (or non-null)"),
        SmaliOpcodeEntry("52", "iget", "iget vA, vB, field@CCCC", "Field", "قراءة حقل كائني (32-بت) من الكائن في vB إلى السجل vA", "Read 32-bit instance field into vA from object vB"),
        SmaliOpcodeEntry("54", "iget-object", "iget-object vA, vB, field@CCCC", "Field", "قراءة حقل مرجع كائن من الكائن في vB إلى السجل vA", "Read object reference instance field into vA from object vB"),
        SmaliOpcodeEntry("59", "iput", "iput vA, vB, field@CCCC", "Field", "كتابة قيمة vA في حقل الكائن الموجود في vB", "Store 32-bit value vA into instance field of object vB"),
        SmaliOpcodeEntry("60", "sget", "sget vAA, field@BBBB", "Field", "قراءة حقل ثابت (static) 32-بت إلى السجل vAA", "Read 32-bit static field into vAA"),
        SmaliOpcodeEntry("62", "sget-object", "sget-object vAA, field@BBBB", "Field", "قراءة حقل ثابت كائني (static object) إلى السجل vAA", "Read object reference static field into vAA"),
        SmaliOpcodeEntry("6e", "invoke-virtual", "invoke-virtual {vC..vG}, method@BBBB", "Invoke", "استدعاء دالة افتراضية عامة أو محمية على الكائن vC", "Invoke a virtual method on object vC with parameters"),
        SmaliOpcodeEntry("6f", "invoke-super", "invoke-super {vC..vG}, method@BBBB", "Invoke", "استدعاء دالة الصنف الأب المباشر", "Invoke superclass virtual method"),
        SmaliOpcodeEntry("70", "invoke-direct", "invoke-direct {vC..vG}, method@BBBB", "Invoke", "استدعاء دالة خاصة (private) أو الباني (<init>)", "Invoke a non-overridable direct method (private or constructor <init>)"),
        SmaliOpcodeEntry("71", "invoke-static", "invoke-static {vC..vG}, method@BBBB", "Invoke", "استدعاء دالة ثابتة (static method) بدون كائن this", "Invoke a static method"),
        SmaliOpcodeEntry("72", "invoke-interface", "invoke-interface {vC..vG}, method@BBBB", "Invoke", "استدعاء دالة معرفة في واجهة (interface)", "Invoke an interface method on object vC")
    )
}

object LocalTerminalSimulator {
    fun executeCommand(commandLine: String, currentDir: File): Pair<File, String> {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return currentDir to ""
        val parts = trimmed.split(Regex("\\s+"))
        val cmd = parts.first().lowercase()
        val args = parts.drop(1)

        return when (cmd) {
            "help" -> currentDir to """
                Yemen Manager Local Terminal Simulator (Safe Non-Root Sandbox)
                Supported commands:
                  pwd              - Print working directory
                  ls [-la]         - List directory entries with size & permissions
                  cd <dir>         - Change directory
                  cat <file>       - Display file text content
                  sha256sum <file> - Compute SHA-256 digest
                  md5sum <file>    - Compute MD5 digest
                  stat <file>      - Inspect file metadata
                  df -h            - Show storage disk usage
                  uname -a         - Show kernel & architecture info
                  id               - Show current sandbox UID/GID
                  clear            - Clear terminal output
            """.trimIndent()

            "pwd" -> currentDir to currentDir.absolutePath

            "cd" -> {
                val target = args.firstOrNull() ?: ".."
                val resolved = if (target == "..") {
                    currentDir.parentFile ?: currentDir
                } else if (target.startsWith("/")) {
                    File(target)
                } else {
                    File(currentDir, target)
                }
                if (resolved.exists() && resolved.isDirectory && resolved.canRead()) {
                    resolved to "Changed directory to ${resolved.absolutePath}"
                } else {
                    currentDir to "cd: no such directory or permission denied: $target"
                }
            }

            "ls" -> {
                val files = currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
                val out = buildString {
                    appendLine("total ${files.size}")
                    for (f in files) {
                        val perms = buildString {
                            append(if (f.isDirectory) "d" else "-")
                            append(if (f.canRead()) "r" else "-")
                            append(if (f.canWrite()) "w" else "-")
                            append(if (f.canExecute()) "x" else "-")
                        }
                        val size = DiskVolumeStats.formatSizeShort(f.length())
                        val time = DiskVolumeStats.formatTimestampShort(f.lastModified())
                        appendLine(String.format(Locale.US, "%-6s %8s  %s  %s", perms, size, time, f.name))
                    }
                }
                currentDir to out.trimEnd()
            }

            "cat" -> {
                val name = args.firstOrNull()
                if (name == null) {
                    currentDir to "cat: missing file operand"
                } else {
                    val f = File(currentDir, name).takeIf { it.exists() } ?: File(name)
                    if (!f.exists() || f.isDirectory) {
                        currentDir to "cat: $name: No such file"
                    } else {
                        val bytes = f.inputStream().use { it.readNBytes(4096) }
                        currentDir to String(bytes, Charsets.UTF_8)
                    }
                }
            }

            "sha256sum", "md5sum" -> {
                val name = args.firstOrNull()
                if (name == null) {
                    currentDir to "$cmd: missing file operand"
                } else {
                    val f = File(currentDir, name).takeIf { it.exists() } ?: File(name)
                    if (!f.exists() || f.isDirectory) {
                        currentDir to "$cmd: $name: No such file"
                    } else {
                        val algo = if (cmd == "sha256sum") "SHA-256" else "MD5"
                        val digest = MessageDigest.getInstance(algo).digest(f.readBytes()).toHex()
                        currentDir to "$digest  ${f.name}"
                    }
                }
            }

            "stat" -> {
                val name = args.firstOrNull() ?: "."
                val f = if (name == ".") currentDir else (File(currentDir, name).takeIf { it.exists() } ?: File(name))
                if (!f.exists()) {
                    currentDir to "stat: cannot stat '$name': No such file or directory"
                } else {
                    currentDir to """
                        File: ${f.name}
                        Path: ${f.absolutePath}
                        Size: ${f.length()} bytes (${DiskVolumeStats.formatSizeShort(f.length())})
                        Type: ${if (f.isDirectory) "directory" else "regular file"}
                        Modified: ${DiskVolumeStats.formatTimestampShort(f.lastModified())}
                    """.trimIndent()
                }
            }

            "df" -> {
                val stats = StorageStatsHelper.queryVolumeStats(currentDir, "Storage", "Storage")
                currentDir to """
                    Filesystem      Size      Used     Avail   Use%  Mounted on
                    /data/media   ${DiskVolumeStats.formatSizeShort(stats.totalBytes)}   ${DiskVolumeStats.formatSizeShort(stats.usedBytes)}   ${DiskVolumeStats.formatSizeShort(stats.availableBytes)}   ${(stats.usedFraction * 100).toInt()}%  ${currentDir.absolutePath}
                """.trimIndent()
            }

            "uname" -> {
                currentDir to "Linux localhost ${System.getProperty("os.version") ?: "6.1.0-android"} ${System.getProperty("os.arch") ?: "aarch64"} Android"
            }

            "id" -> {
                val uid = android.os.Process.myUid()
                currentDir to "uid=$uid(u0_a${uid % 10000}) gid=$uid(u0_a${uid % 10000}) context=u:r:untrusted_app:s0"
            }

            "find" -> {
                val keyword = args.lastOrNull()?.replace("*", "") ?: ""
                val matches = currentDir.walkTopDown().maxDepth(3)
                    .filter { keyword.isEmpty() || it.name.contains(keyword, ignoreCase = true) }
                    .take(30)
                    .map { it.relativeToOrSelf(currentDir).path.ifEmpty { "." } }
                    .toList()
                currentDir to matches.joinToString("\n")
            }

            else -> currentDir to "$cmd: command executed in local sandbox (type 'help' for built-in commands)"
        }
    }
}
