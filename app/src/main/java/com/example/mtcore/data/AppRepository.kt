package com.example.mtcore.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceDir: String,
    val splitSourceDirs: List<String> = emptyList(),
    val baseSizeBytes: Long,
    val splitSizeBytes: Long = 0L,
    val isSystemApp: Boolean,
    val signatureScheme: String = "V2",
    val protection: String = "No Detected",
    val targetSdk: Int,
    val minSdk: Int,
    val dataDir: String,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val uid: Int,
    val isInstalled: Boolean = true,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null
) {
    val totalSizeBytes: Long
        get() = baseSizeBytes + splitSizeBytes

    fun formattedBaseSize(): String = AppRepository.formatBytes(baseSizeBytes)
    fun formattedSplitSize(): String = AppRepository.formatBytes(splitSizeBytes)
    fun formattedTotalSize(): String = "${AppRepository.formatBytes(totalSizeBytes)} ($totalSizeBytes B)"
    fun formattedTargetSdk(): String = AppRepository.formatSdkVersion(targetSdk)
    fun formattedMinSdk(): String = AppRepository.formatSdkVersion(minSdk)
    fun formattedFirstInstall(): String = AppRepository.formatTimestamp(firstInstallTime)
    fun formattedLastUpdate(): String = AppRepository.formatTimestamp(lastUpdateTime)
}

object AppRepository {
    private val iconCache = object : LruCache<String, ImageBitmap>(160) {}

    fun getCachedIcon(key: String): ImageBitmap? = synchronized(iconCache) { iconCache.get(key) }

    private fun putCachedIcon(key: String, bitmap: ImageBitmap) {
        synchronized(iconCache) {
            iconCache.put(key, bitmap)
        }
    }

    @Composable
    fun rememberPackageIcon(context: Context, packageName: String, sourceDir: String): State<ImageBitmap?> {
        val cacheKey = "pkg:$packageName:$sourceDir"
        return produceState(initialValue = getCachedIcon(cacheKey), key1 = cacheKey) {
            if (value == null) {
                value = loadIconFromPackageOrApk(context, packageName, sourceDir, cacheKey)
            }
        }
    }

    @Composable
    fun rememberApkFileIcon(context: Context, apkFile: File): State<ImageBitmap?> {
        val cacheKey = "apk:${apkFile.absolutePath}:${apkFile.lastModified()}"
        return produceState(initialValue = getCachedIcon(cacheKey), key1 = cacheKey) {
            if (value == null) {
                value = loadApkIcon(context, apkFile, cacheKey)
            }
        }
    }

    suspend fun loadIconFromPackageOrApk(
        context: Context,
        packageName: String,
        sourceDir: String,
        cacheKey: String = "pkg:$packageName:$sourceDir"
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        getCachedIcon(cacheKey)?.let { return@withContext it }
        val pm = context.packageManager
        val drawable = runCatching {
            pm.getApplicationIcon(packageName)
        }.getOrElse {
            runCatching {
                val info = pm.getPackageArchiveInfo(sourceDir, 0)?.applicationInfo
                if (info != null) {
                    info.sourceDir = sourceDir
                    info.publicSourceDir = sourceDir
                    info.loadIcon(pm)
                } else null
            }.getOrNull()
        }
        drawable?.let { drawableToImageBitmap(it, 128)?.also { bmp -> putCachedIcon(cacheKey, bmp) } }
    }

    suspend fun loadApkIcon(
        context: Context,
        apkFile: File,
        cacheKey: String = "apk:${apkFile.absolutePath}:${apkFile.lastModified()}"
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        getCachedIcon(cacheKey)?.let { return@withContext it }
        if (!apkFile.isFile || !apkFile.canRead()) return@withContext null
        val pm = context.packageManager
        val drawable = runCatching {
            val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            val appInfo = pkgInfo?.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
                appInfo.loadIcon(pm)
            } else null
        }.getOrNull()
        drawable?.let { drawableToImageBitmap(it, 112)?.also { bmp -> putCachedIcon(cacheKey, bmp) } }
    }

    suspend fun getInstalledApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES
        }
        val packages = runCatching { pm.getInstalledPackages(flags) }.getOrElse {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }
        packages.mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
            val baseFile = File(sourceDir)
            val baseSize = if (baseFile.exists()) baseFile.length() else 0L
            val splits = appInfo.splitSourceDirs?.toList().orEmpty()
            val splitSize = splits.sumOf { path -> File(path).let { if (it.exists()) it.length() else 0L } }
            val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(pkg.packageName)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 23
            InstalledAppInfo(
                appName = label,
                packageName = pkg.packageName,
                versionName = pkg.versionName ?: "1.0",
                versionCode = vCode,
                sourceDir = sourceDir,
                splitSourceDirs = splits,
                baseSizeBytes = baseSize,
                splitSizeBytes = splitSize,
                isSystemApp = isSystem,
                signatureScheme = detectApkSignatureScheme(baseFile),
                protection = "No Detected",
                targetSdk = appInfo.targetSdkVersion,
                minSdk = minSdk,
                dataDir = appInfo.dataDir ?: "/data/user/0/${pkg.packageName}",
                firstInstallTime = pkg.firstInstallTime,
                lastUpdateTime = pkg.lastUpdateTime,
                uid = appInfo.uid,
                isInstalled = true,
                installedVersionName = pkg.versionName ?: "1.0",
                installedVersionCode = vCode
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
    }

    suspend fun inspectApkFile(context: Context, apkFile: File): InstalledAppInfo? = withContext(Dispatchers.IO) {
        if (!apkFile.isFile || !apkFile.canRead()) return@withContext null
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES
        }
        val pkg: PackageInfo = runCatching {
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }.getOrNull() ?: runCatching {
            pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }.getOrNull() ?: return@withContext null

        val appInfo = pkg.applicationInfo ?: ApplicationInfo()
        appInfo.sourceDir = apkFile.absolutePath
        appInfo.publicSourceDir = apkFile.absolutePath

        val label = runCatching { appInfo.loadLabel(pm).toString() }
            .getOrDefault(apkFile.nameWithoutExtension)
            .ifBlank { apkFile.nameWithoutExtension }

        val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkg.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkg.versionCode.toLong()
        }
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 23

        val installedPkg = runCatching { pm.getPackageInfo(pkg.packageName, 0) }.getOrNull()
        val installedVCode = installedPkg?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        }

        InstalledAppInfo(
            appName = label,
            packageName = pkg.packageName ?: "unknown",
            versionName = pkg.versionName ?: "1.0",
            versionCode = vCode,
            sourceDir = apkFile.absolutePath,
            splitSourceDirs = emptyList(),
            baseSizeBytes = apkFile.length(),
            splitSizeBytes = 0L,
            isSystemApp = false,
            signatureScheme = detectApkSignatureScheme(apkFile),
            protection = detectQuickApkProtection(apkFile),
            targetSdk = appInfo.targetSdkVersion.takeIf { it > 0 } ?: 35,
            minSdk = minSdk.takeIf { it > 0 } ?: 23,
            dataDir = appInfo.dataDir ?: "/data/user/0/${pkg.packageName}",
            firstInstallTime = apkFile.lastModified(),
            lastUpdateTime = apkFile.lastModified(),
            uid = appInfo.uid,
            isInstalled = installedPkg != null,
            installedVersionName = installedPkg?.versionName,
            installedVersionCode = installedVCode
        )
    }

    suspend fun extractApk(app: InstalledAppInfo): File = withContext(Dispatchers.IO) {
        val source = File(app.sourceDir)
        check(source.exists() && source.canRead()) { "Source APK cannot be read: ${app.sourceDir}" }
        val targetDir = File("/storage/emulated/0/MT2/apks")
        if (!targetDir.exists()) {
            check(targetDir.mkdirs() || targetDir.exists()) { "Cannot create directory: ${targetDir.absolutePath}" }
        }
        val cleanName = app.appName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { app.packageName }
        val cleanVer = app.versionName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { app.versionCode.toString() }
        val targetFile = File(targetDir, "${cleanName}_${cleanVer}.apk")
        FileInputStream(source).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        targetFile
    }

    /**
     * Packages `base.apk` (`app.sourceDir`) and all `split_*.apk` files (`app.splitSourceDirs`)
     * into a single `.apks` archive in `/storage/emulated/0/MT2/apks/<name>_<ver>.apks`.
     */
    suspend fun extractSplitApksBundle(app: InstalledAppInfo): File = withContext(Dispatchers.IO) {
        val baseSource = File(app.sourceDir)
        check(baseSource.exists() && baseSource.canRead()) { "Base APK cannot be read: ${app.sourceDir}" }
        val targetDir = File("/storage/emulated/0/MT2/apks")
        if (!targetDir.exists()) {
            check(targetDir.mkdirs() || targetDir.exists()) { "Cannot create directory: ${targetDir.absolutePath}" }
        }
        val cleanName = app.appName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { app.packageName }
        val cleanVer = app.versionName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { app.versionCode.toString() }
        val bundleFile = File(targetDir, "${cleanName}_${cleanVer}.apks")

        ZipOutputStream(FileOutputStream(bundleFile)).use { zos ->
            val baseEntry = ZipEntry("base.apk")
            zos.putNextEntry(baseEntry)
            FileInputStream(baseSource).use { it.copyTo(zos) }
            zos.closeEntry()

            val usedNames = HashSet<String>().apply { add("base.apk") }
            app.splitSourceDirs.forEachIndexed { idx, splitPath ->
                val splitFile = File(splitPath)
                if (splitFile.exists() && splitFile.canRead()) {
                    var entryName = splitFile.name.ifBlank { "split_$idx.apk" }
                    if (!usedNames.add(entryName)) {
                        entryName = "split_${idx}_$entryName"
                        usedNames.add(entryName)
                    }
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(splitFile).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        bundleFile
    }

    /**
     * Merges `base.apk` (`app.sourceDir`) and all `split_*.apk` (`app.splitSourceDirs`) directly
     * into a standalone signed `.apk` in `/storage/emulated/0/MT2/apks/<name>_<ver>_merged.apk`.
     */
    suspend fun extractAndMergeSplitApk(app: InstalledAppInfo): File = withContext(Dispatchers.IO) {
        val baseSource = File(app.sourceDir)
        check(baseSource.exists() && baseSource.canRead()) { "Base APK cannot be read: ${app.sourceDir}" }
        val targetDir = File("/storage/emulated/0/MT2/apks")
        if (!targetDir.exists()) {
            check(targetDir.mkdirs() || targetDir.exists()) { "Cannot create directory: ${targetDir.absolutePath}" }
        }
        val cleanName = app.appName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { app.packageName }
        val cleanVer = app.versionName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { app.versionCode.toString() }
        val outFile = File(targetDir, "${cleanName}_${cleanVer}_merged.apk")
        val allSplitFiles = buildList {
            add(baseSource)
            app.splitSourceDirs.forEach { path ->
                val f = File(path)
                if (f.exists() && f.canRead()) add(f)
            }
        }
        ApkToolkit.mergeSplitApkFilesToSingleApk(
            apkFiles = allSplitFiles,
            outputFile = outFile,
            autoSign = true
        )
    }

    fun detectApkSignatureScheme(apkFile: File): String {
        if (!apkFile.isFile || !apkFile.canRead()) return "V2"
        val schemes = mutableListOf<String>()
        runCatching {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name.uppercase(Locale.ROOT)
                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        schemes += "V1"
                        break
                    }
                }
            }
        }
        runCatching {
            RandomAccessFile(apkFile, "r").use { raf ->
                val len = raf.length()
                if (len > 64) {
                    val searchSize = minOf(len, 65558L).toInt()
                    val buf = ByteArray(searchSize)
                    raf.seek(len - searchSize)
                    raf.readFully(buf)
                    var eocdOffset = -1
                    for (i in searchSize - 22 downTo 0) {
                        if (buf[i] == 0x50.toByte() && buf[i + 1] == 0x4b.toByte() && buf[i + 2] == 0x05.toByte() && buf[i + 3] == 0x06.toByte()) {
                            eocdOffset = i
                            break
                        }
                    }
                    if (eocdOffset >= 0) {
                        val cdOffset = ByteBuffer.wrap(buf, eocdOffset + 16, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                        if (cdOffset >= 32) {
                            val footer = ByteArray(24)
                            raf.seek(cdOffset - 24)
                            raf.readFully(footer)
                            val magic = String(footer, 8, 16, Charsets.US_ASCII)
                            if (magic == "APK Sig Block 42") {
                                val blockSize = ByteBuffer.wrap(footer, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
                                val blockStart = cdOffset - blockSize - 8
                                if (blockStart >= 0 && blockSize in 32..(4 * 1024 * 1024)) {
                                    var pos = blockStart + 8
                                    val pairsEnd = cdOffset - 24
                                    while (pos + 12 <= pairsEnd) {
                                        raf.seek(pos)
                                        val pairHeader = ByteArray(12)
                                        raf.readFully(pairHeader)
                                        val pairLen = ByteBuffer.wrap(pairHeader, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
                                        val id = ByteBuffer.wrap(pairHeader, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                                        if (id == 0x7109871a) schemes += "V2"
                                        if (id == -0x0fac9740) schemes += "V3" // 0xf05368c0
                                        if (pairLen <= 4) break
                                        pos += 8 + pairLen
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (schemes.isEmpty()) schemes += "V2"
        return schemes.distinct().joinToString(", ")
    }

    fun detectQuickApkProtection(apkFile: File): String {
        if (!apkFile.isFile || !apkFile.canRead()) return "No Detected"
        val found = LinkedHashSet<String>()
        runCatching {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val lower = entries.nextElement().name.lowercase(Locale.ROOT)
                    when {
                        lower.contains("libjiagu") || lower.contains("jiagu_data") -> found += "360 Jiagu"
                        lower.contains("libsecneo") || lower.contains("libdexhelper") -> found += "SecNeo / Bangcle"
                        lower.contains("libshella") || lower.contains("libshellx") || lower.contains("libtup.so") -> found += "Tencent Legu"
                        lower.contains("libbaiduprotect") -> found += "Baidu Protect"
                        lower.contains("libexec.so") || lower.contains("ijiami") -> found += "IJiami"
                        lower.contains("libsgmain") || lower.contains("libmobisec") -> found += "Ali Security"
                        lower.contains("libnesec") -> found += "NetEase Yidun"
                        lower.contains("libdexprotector") -> found += "DexProtector"
                        lower.contains("libpairipcore") -> found += "Google PairIP"
                        lower.contains("libkwsgmain") -> found += "Kuaishou Guard"
                        lower.contains("libvirbox") -> found += "Virbox"
                    }
                }
            }
        }
        return if (found.isEmpty()) "No Detected" else found.joinToString(" + ")
    }

    private fun drawableToImageBitmap(drawable: Drawable, maxSizePx: Int): ImageBitmap? = runCatching {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return@runCatching drawable.bitmap.asImageBitmap()
        }
        val width = drawable.intrinsicWidth.coerceIn(1, maxSizePx)
        val height = drawable.intrinsicHeight.coerceIn(1, maxSizePx)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()

    fun formatBytes(bytes: Long): String {
        var value = bytes.toDouble()
        val units = arrayOf("B", "KB", "MB", "GB")
        var idx = 0
        while (value >= 1024.0 && idx < units.lastIndex) {
            value /= 1024.0
            idx++
        }
        return if (idx == 0) "${value.toLong()}B" else String.format(Locale.US, "%.2f%s", value, units[idx])
    }

    fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
    }

    fun formatSdkVersion(sdk: Int): String {
        val androidVer = when (sdk) {
            36 -> "16"
            35 -> "15"
            34 -> "14"
            33 -> "13"
            32 -> "12L"
            31 -> "12"
            30 -> "11"
            29 -> "10"
            28 -> "9"
            27 -> "8.1"
            26 -> "8.0"
            25 -> "7.1"
            24 -> "7.0"
            23 -> "6.0"
            22 -> "5.1"
            21 -> "5.0"
            else -> "$sdk"
        }
        return "Android $androidVer (API $sdk)"
    }
}
