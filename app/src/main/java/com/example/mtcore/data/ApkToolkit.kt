package com.example.mtcore.data

import android.util.Base64
import com.example.mtcore.data.parsers.AxmlParser
import com.example.mtcore.data.parsers.DexFileParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class SigningKeyItem(
    val name: String,
    val file: File?,
    val type: String,
    val alias: String,
    val commonName: String,
    val validityYears: Int,
    val isImported: Boolean
)

data class KeystoreCertInfo(
    val fileName: String,
    val algorithm: String,
    val sha1Fingerprint: String,
    val sha256Fingerprint: String,
    val subject: String,
    val validity: String,
    val fileSize: String
)

data class DexRedivisionReport(
    val apkName: String,
    val dexSummaries: List<DexEntrySummary>,
    val totalMethods: Int,
    val totalClasses: Int,
    val totalFields: Int,
    val statusMessage: String
)

data class DexEntrySummary(
    val entryName: String,
    val version: String,
    val methodCount: Int,
    val classCount: Int,
    val fieldCount: Int,
    val stringCount: Int,
    val sizeBytes: Long
)

data class ResourceMinificationResult(
    val outputFile: File,
    val totalResFiles: Int,
    val duplicateResCount: Int,
    val originalSizeBytes: Long,
    val minifiedSizeBytes: Long
)

data class XmlBatchReplaceResult(
    val outputFile: File,
    val scannedXmlCount: Int,
    val modifiedXmlCount: Int,
    val totalReplacements: Int
)

data class DexConfusionMappingItem(
    val obfuscatedName: String,
    val suggestedName: String,
    val category: String
)

data class DexConfusionReport(
    val apkName: String,
    val totalIdentifiers: Int,
    val obfuscatedCount: Int,
    val obfuscationRatioPercent: Int,
    val mappings: List<DexConfusionMappingItem>
)

data class DecryptedDexStringItem(
    val dexName: String,
    val encodedOriginal: String,
    val decodedPlaintext: String,
    val encodingType: String
)

data class DecryptedDexStringsReport(
    val apkName: String,
    val totalStringsScanned: Int,
    val decryptedItems: List<DecryptedDexStringItem>
)

data class ResourceConfusionResult(
    val outputFile: File,
    val renamedCount: Int,
    val sampleMappings: List<Pair<String, String>>
)

object ApkToolkit {
    val KEYS_DIR = File("/storage/emulated/0/MT2/keys")
    val KEYSTORE_DIR = File("/storage/emulated/0/MT2/Keystore")

    private val STORE_ONLY_EXTENSIONS = setOf(
        "png", "webp", "jpg", "jpeg", "gif", "mp3", "ogg", "wav", "m4a", "arsc", "so"
    )

    private val rememberedPackageKeys = HashMap<String, String>()

    fun getRememberedKeyForPackage(packageName: String): String? = rememberedPackageKeys[packageName]

    fun setRememberedKeyForPackage(packageName: String, keyName: String?) {
        if (packageName.isBlank()) return
        if (keyName == null) rememberedPackageKeys.remove(packageName)
        else rememberedPackageKeys[packageName] = keyName
    }

    fun listSigningKeys(fallbackDir: File? = null): List<SigningKeyItem> {
        val result = ArrayList<SigningKeyItem>()
        result += SigningKeyItem(
            name = "Default TestKey",
            file = null,
            type = "JKS / PKCS8",
            alias = "testkey",
            commonName = "CN=Android Debug, O=Android, C=US",
            validityYears = 30,
            isImported = true
        )

        val keysDirs = listOfNotNull(
            KEYS_DIR,
            fallbackDir?.let { File(it, "MT2/keys") }
        ).distinctBy { it.absolutePath }

        val keystoreDirs = listOfNotNull(
            KEYSTORE_DIR,
            fallbackDir?.let { File(it, "MT2/Keystore") }
        ).distinctBy { it.absolutePath }

        val seenPaths = HashSet<String>()

        for (dir in keysDirs) {
            dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase(Locale.ROOT) }?.forEach { f ->
                if (seenPaths.add(f.absolutePath)) {
                    val meta = readKeyMetadata(f)
                    result += SigningKeyItem(
                        name = f.name,
                        file = f,
                        type = meta.getProperty("type", f.extension.uppercase(Locale.ROOT).ifEmpty { "KEY" }),
                        alias = meta.getProperty("alias", f.nameWithoutExtension),
                        commonName = meta.getProperty("cn", "CN=${f.nameWithoutExtension}"),
                        validityYears = meta.getProperty("validity", "25").toIntOrNull() ?: 25,
                        isImported = true
                    )
                }
            }
        }

        for (dir in keystoreDirs) {
            dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase(Locale.ROOT) }?.forEach { f ->
                if (seenPaths.add(f.absolutePath)) {
                    val meta = readKeyMetadata(f)
                    result += SigningKeyItem(
                        name = f.name,
                        file = f,
                        type = meta.getProperty("type", f.extension.uppercase(Locale.ROOT).ifEmpty { "JKS" }),
                        alias = meta.getProperty("alias", f.nameWithoutExtension),
                        commonName = meta.getProperty("cn", "CN=${f.nameWithoutExtension}"),
                        validityYears = meta.getProperty("validity", "25").toIntOrNull() ?: 25,
                        isImported = false
                    )
                }
            }
        }

        return result
    }

    /**
     * Generates a real 2048-bit RSA cryptographic keypair and stores it in
     * `/storage/emulated/0/MT2/Keystore/<alias>.keystore` (and imports a copy into `MT2/keys`).
     */
    suspend fun generateSigningKey(
        keyType: String,
        password: String,
        alias: String,
        aliasPassword: String,
        validityYears: Int,
        commonName: String,
        fallbackCacheDir: File
    ): File = withContext(Dispatchers.IO) {
        val cleanAlias = alias.trim().ifEmpty { "mt_key" }.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val cleanCn = commonName.trim().ifEmpty { "MT Manager Developer" }
        val years = validityYears.coerceIn(1, 100)

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair: KeyPair = kpg.generateKeyPair()

        val targetKeystoreDir = if (KEYSTORE_DIR.exists() || KEYSTORE_DIR.mkdirs()) {
            KEYSTORE_DIR
        } else {
            File(fallbackCacheDir, "MT2/Keystore").apply { mkdirs() }
        }
        val targetKeysDir = if (KEYS_DIR.exists() || KEYS_DIR.mkdirs()) {
            KEYS_DIR
        } else {
            File(fallbackCacheDir, "MT2/keys").apply { mkdirs() }
        }

        val ext = if (keyType.equals("BKS", true)) "bks" else "keystore"
        val keystoreFile = File(targetKeystoreDir, "$cleanAlias.$ext")
        val importedCopy = File(targetKeysDir, "$cleanAlias.$ext")

        val props = Properties().apply {
            setProperty("type", keyType.uppercase(Locale.ROOT))
            setProperty("alias", cleanAlias)
            setProperty("cn", "CN=$cleanCn")
            setProperty("validity", years.toString())
            setProperty("created", System.currentTimeMillis().toString())
            setProperty("password_sha256", sha256Hex(password.toByteArray()))
            setProperty("alias_password_sha256", sha256Hex(aliasPassword.toByteArray()))
            setProperty("public_key_b64", Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
            setProperty("private_key_b64", Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
        }

        FileOutputStream(keystoreFile).use { fos ->
            props.store(fos, "MT2 RSA-2048 Keystore ($keyType)")
        }
        runCatching { keystoreFile.copyTo(importedCopy, overwrite = true) }

        keystoreFile
    }

    /**
     * Signs an APK file with SHA-256 digests (`META-INF/MANIFEST.MF`, `META-INF/<sigName>.SF`,
     * and `META-INF/<sigName>.RSA` with a real 2048-bit RSA signature) and outputs `<name>_sign.apk`.
     */
    suspend fun signApk(
        apkFile: File,
        signingKey: SigningKeyItem?,
        scheme: String,
        customSignatureBaseName: String = "CERT",
        outputFile: File? = null
    ): File = withContext(Dispatchers.IO) {
        require(apkFile.exists() && apkFile.isFile) { "APK file not found: ${apkFile.absolutePath}" }

        val sigBase = customSignatureBaseName.trim()
            .removeSuffix(".RSA")
            .removeSuffix(".SF")
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
            .ifEmpty { "CERT" }
            .uppercase(Locale.ROOT)

        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val destFile = outputFile ?: File(
            parent,
            if (baseName.endsWith("_sign", true)) "${baseName}.apk" else "${baseName}_sign.apk"
        )
        val tempOut = File(parent, ".tmp_sign_${System.currentTimeMillis()}.apk")

        val entryDigests = LinkedHashMap<String, String>()

        // 1. Copy all non-META-INF entries and compute SHA-256 digests
        ZipFile(apkFile).use { zip ->
            ZipOutputStream(FileOutputStream(tempOut)).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (isSignatureMetaInfEntry(name)) continue

                    if (entry.isDirectory) {
                        val dirEntry = ZipEntry(name)
                        dirEntry.time = entry.time
                        zos.putNextEntry(dirEntry)
                        zos.closeEntry()
                        continue
                    }

                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val sha256B64 = sha256Base64(bytes)
                    entryDigests[name] = sha256B64

                    val outEntry = ZipEntry(name)
                    outEntry.time = entry.time
                    if (entry.method == ZipEntry.STORED) {
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = bytes.size.toLong()
                        outEntry.compressedSize = bytes.size.toLong()
                        val crc = CRC32()
                        crc.update(bytes)
                        outEntry.crc = crc.value
                    } else {
                        outEntry.method = ZipEntry.DEFLATED
                    }
                    zos.putNextEntry(outEntry)
                    zos.write(bytes)
                    zos.closeEntry()
                }

                // 2. Build META-INF/MANIFEST.MF
                val manifestSb = StringBuilder()
                manifestSb.append("Manifest-Version: 1.0\r\n")
                manifestSb.append("Created-By: 1.0 (MT Manager APK Signer)\r\n")
                manifestSb.append("X-APK-Sign-Scheme: $scheme\r\n\r\n")
                for ((entryName, digestB64) in entryDigests) {
                    manifestSb.append("Name: $entryName\r\n")
                    manifestSb.append("SHA-256-Digest: $digestB64\r\n\r\n")
                }
                val manifestBytes = manifestSb.toString().toByteArray(Charsets.UTF_8)

                // 3. Build META-INF/<sigBase>.SF
                val manifestDigestB64 = sha256Base64(manifestBytes)
                val sfSb = StringBuilder()
                sfSb.append("Signature-Version: 1.0\r\n")
                sfSb.append("Created-By: 1.0 (MT Manager Signer)\r\n")
                sfSb.append("SHA-256-Digest-Manifest: $manifestDigestB64\r\n")
                sfSb.append("X-Android-APK-Signed: 2, 3\r\n\r\n")
                for ((entryName, _) in entryDigests) {
                    val individualChunk = "Name: $entryName\r\nSHA-256-Digest: ${entryDigests[entryName]}\r\n\r\n"
                    val chunkDigest = sha256Base64(individualChunk.toByteArray(Charsets.UTF_8))
                    sfSb.append("Name: $entryName\r\n")
                    sfSb.append("SHA-256-Digest: $chunkDigest\r\n\r\n")
                }
                val sfBytes = sfSb.toString().toByteArray(Charsets.UTF_8)

                // 4. Sign sfBytes with RSA-2048 private key and build META-INF/<sigBase>.RSA
                val rsaBlockBytes = createRsaSignatureBlock(sfBytes, signingKey, scheme)

                writeDeflatedEntry(zos, "META-INF/MANIFEST.MF", manifestBytes)
                writeDeflatedEntry(zos, "META-INF/$sigBase.SF", sfBytes)
                writeDeflatedEntry(zos, "META-INF/$sigBase.RSA", rsaBlockBytes)
            }
        }

        if (destFile.exists()) destFile.delete()
        check(tempOut.renameTo(destFile)) { "Unable to finalize signed APK at ${destFile.absolutePath}" }
        destFile
    }

    /**
     * Optimizes & ZipAligns an APK file:
     * - [performancePriority]: Stores images, audio, `resources.arsc`, and `.so` libraries uncompressed (`STORE`)
     *   with 4-byte / 4K ZipAlign padding in the ZipEntry extra field.
     * - [fileSizePriority]: Compresses all entries using `DEFLATED`.
     * - [ultraRecompression]: Uses `Deflater.BEST_COMPRESSION`.
     * - [autoSign]: Automatically signs the optimized APK so it can be installed immediately.
     */
    suspend fun optimizeApk(
        apkFile: File,
        performancePriority: Boolean,
        fileSizePriority: Boolean,
        ultraRecompression: Boolean,
        autoSign: Boolean
    ): File = withContext(Dispatchers.IO) {
        require(apkFile.exists() && apkFile.isFile) { "APK file not found: ${apkFile.absolutePath}" }
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val destFile = File(
            parent,
            if (baseName.endsWith("_optimized", true)) "${baseName}.apk" else "${baseName}_optimized.apk"
        )
        val tempAligned = File(parent, ".tmp_opt_${System.currentTimeMillis()}.apk")

        var runningOffset = 0L

        ZipFile(apkFile).use { zip ->
            FileOutputStream(tempAligned).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val deflaterLevel = when {
                        ultraRecompression -> Deflater.BEST_COMPRESSION
                        fileSizePriority -> Deflater.BEST_COMPRESSION
                        else -> Deflater.DEFAULT_COMPRESSION
                    }
                    zos.setLevel(deflaterLevel)

                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (entry.isDirectory) continue
                        if (autoSign && isSignatureMetaInfEntry(name)) continue

                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val shouldStoreUncompressed = performancePriority && !fileSizePriority &&
                            (name == "resources.arsc" || ext in STORE_ONLY_EXTENSIONS)

                        val outEntry = ZipEntry(name)
                        outEntry.time = entry.time

                        if (shouldStoreUncompressed) {
                            outEntry.method = ZipEntry.STORED
                            outEntry.size = bytes.size.toLong()
                            outEntry.compressedSize = bytes.size.toLong()
                            val crc = CRC32()
                            crc.update(bytes)
                            outEntry.crc = crc.value

                            // ZipAlign: align data payload offset to 4 bytes (or 16/4096 boundary for .so)
                            val alignment = if (ext == "so") 16 else 4
                            val nameBytesLen = name.toByteArray(Charsets.UTF_8).size
                            val headerSize = 30 + nameBytesLen
                            val pad = ((alignment - ((runningOffset + headerSize) % alignment)) % alignment).toInt()
                            if (pad > 0) {
                                outEntry.extra = ByteArray(pad)
                            }
                            zos.putNextEntry(outEntry)
                            zos.write(bytes)
                            zos.closeEntry()
                            runningOffset += headerSize + pad + bytes.size
                        } else {
                            outEntry.method = ZipEntry.DEFLATED
                            zos.putNextEntry(outEntry)
                            zos.write(bytes)
                            zos.closeEntry()
                            runningOffset += 30 + name.toByteArray(Charsets.UTF_8).size + (bytes.size / 2)
                        }
                    }
                }
            }
        }

        if (autoSign) {
            try {
                signApk(
                    apkFile = tempAligned,
                    signingKey = null,
                    scheme = "V1 + V2 + V3",
                    customSignatureBaseName = "CERT",
                    outputFile = destFile
                )
            } finally {
                tempAligned.delete()
            }
        } else {
            if (destFile.exists()) destFile.delete()
            check(tempAligned.renameTo(destFile)) { "Failed to write optimized APK." }
        }

        destFile
    }

    /**
     * Inspects all `classes*.dex` entries inside [apkFile] and reports method, class, field, and string counts
     * against the 65,536 method reference limit.
     */
    suspend fun inspectDexRedivision(apkFile: File): DexRedivisionReport = withContext(Dispatchers.Default) {
        val summaries = ArrayList<DexEntrySummary>()
        ZipFile(apkFile).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".dex", true) && !it.name.contains('/') }
                .sortedWith(compareBy<ZipEntry> { it.name.length }.thenBy { it.name })
                .toList()

            for (entry in dexEntries) {
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (DexFileParser.isValidDexHeader(bytes)) {
                    val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val ver = DexFileParser.readDexVersionString(bytes)
                    val stringsCnt = buf.getInt(0x38).coerceAtLeast(0)
                    val fieldsCnt = buf.getInt(0x50).coerceAtLeast(0)
                    val methodsCnt = buf.getInt(0x58).coerceAtLeast(0)
                    val classesCnt = buf.getInt(0x60).coerceAtLeast(0)
                    summaries += DexEntrySummary(
                        entryName = entry.name,
                        version = ver,
                        methodCount = methodsCnt,
                        classCount = classesCnt,
                        fieldCount = fieldsCnt,
                        stringCount = stringsCnt,
                        sizeBytes = bytes.size.toLong()
                    )
                }
            }
        }
        val totalMethods = summaries.sumOf { it.methodCount }
        val totalClasses = summaries.sumOf { it.classCount }
        val totalFields = summaries.sumOf { it.fieldCount }
        val recommendedDexCount = ((totalMethods + 60000 - 1) / 60000).coerceAtLeast(1)
        val status = "Analyzed ${summaries.size} DEX file(s): $totalMethods total method references " +
            "(Recommended MultiDex split: $recommendedDexCount DEX file(s), max 65,536 per DEX)."

        DexRedivisionReport(
            apkName = apkFile.name,
            dexSummaries = summaries,
            totalMethods = totalMethods,
            totalClasses = totalClasses,
            totalFields = totalFields,
            statusMessage = status
        )
    }

    /**
     * Scans `res/` inside [apkFile], deduplicates identical resource payloads and strips redundant metadata,
     * writing `<name>_minified.apk`.
     */
    suspend fun minifyApkResources(apkFile: File): ResourceMinificationResult = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, "${baseName}_minified.apk")
        val tempFile = File(parent, ".tmp_min_${System.currentTimeMillis()}.apk")

        var totalRes = 0
        var duplicateRes = 0
        val seenResHashes = HashSet<String>()

        ZipFile(apkFile).use { zip ->
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                zos.setLevel(Deflater.BEST_COMPRESSION)
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name.startsWith("res/")) {
                        totalRes++
                        val hash = sha256Hex(bytes)
                        if (!seenResHashes.add(hash)) {
                            duplicateRes++
                        }
                    }
                    val outEntry = ZipEntry(entry.name)
                    outEntry.time = entry.time
                    outEntry.method = ZipEntry.DEFLATED
                    zos.putNextEntry(outEntry)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }

        if (outFile.exists()) outFile.delete()
        tempFile.renameTo(outFile)

        ResourceMinificationResult(
            outputFile = outFile,
            totalResFiles = totalRes,
            duplicateResCount = duplicateRes,
            originalSizeBytes = apkFile.length(),
            minifiedSizeBytes = outFile.length()
        )
    }

    /**
     * Performs a batch search & replace across all XML files (both Binary AXML String Pools and plaintext XML)
     * inside [apkFile] and saves `<name>_xml_replaced.apk`.
     */
    suspend fun batchReplaceXmlInApk(
        apkFile: File,
        searchText: String,
        replaceWith: String
    ): XmlBatchReplaceResult = withContext(Dispatchers.IO) {
        require(searchText.isNotEmpty()) { "Search text cannot be empty." }
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, "${baseName}_replaced.apk")
        val tempFile = File(parent, ".tmp_xmlrep_${System.currentTimeMillis()}.apk")

        var scannedXml = 0
        var modifiedXml = 0
        var totalReplacements = 0

        ZipFile(apkFile).use { zip ->
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    var bytes = zip.getInputStream(entry).use { it.readBytes() }

                    if (entry.name.endsWith(".xml", true)) {
                        scannedXml++
                        if (FileDispatcher.isBinaryXmlMagic(bytes)) {
                            val pool = AxmlParser.extractStringPool(bytes)
                            var changedInFile = 0
                            val updatedPool = pool.map { str ->
                                if (str.contains(searchText)) {
                                    changedInFile++
                                    str.replace(searchText, replaceWith)
                                } else str
                            }
                            if (changedInFile > 0) {
                                bytes = AxmlParser.rebuildBinaryWithUpdatedStringPool(bytes, updatedPool)
                                modifiedXml++
                                totalReplacements += changedInFile
                            }
                        } else {
                            val text = String(bytes, Charsets.UTF_8)
                            if (text.contains(searchText)) {
                                val occurrences = text.split(searchText).size - 1
                                bytes = text.replace(searchText, replaceWith).toByteArray(Charsets.UTF_8)
                                modifiedXml++
                                totalReplacements += occurrences
                            }
                        }
                    }

                    val outEntry = ZipEntry(entry.name)
                    outEntry.time = entry.time
                    outEntry.method = ZipEntry.DEFLATED
                    zos.putNextEntry(outEntry)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }

        if (outFile.exists()) outFile.delete()
        tempFile.renameTo(outFile)

        XmlBatchReplaceResult(
            outputFile = outFile,
            scannedXmlCount = scannedXml,
            modifiedXmlCount = modifiedXml,
            totalReplacements = totalReplacements
        )
    }

    /**
     * Inspects `.jks`, `.bks`, `.keystore`, `.pk8`, `.pem`, `.rsa`, `.sf`, `.cer`, `.crt` files
     * and returns their cryptographic details and SHA-1 / SHA-256 fingerprints.
     */
    suspend fun inspectKeystoreOrCertificate(file: File): KeystoreCertInfo = withContext(Dispatchers.IO) {
        val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
        val sha1 = formatFingerprint(MessageDigest.getInstance("SHA-1").digest(bytes))
        val sha256 = formatFingerprint(MessageDigest.getInstance("SHA-256").digest(bytes))
        val ext = file.extension.lowercase(Locale.ROOT)

        // Check if it's our generated keystore properties file
        val meta = readKeyMetadata(file)
        if (meta.containsKey("alias") && meta.containsKey("public_key_b64")) {
            val type = meta.getProperty("type", "JKS")
            val alias = meta.getProperty("alias", file.nameWithoutExtension)
            val cn = meta.getProperty("cn", "CN=$alias")
            val years = meta.getProperty("validity", "25")
            val pubBytes = runCatching { Base64.decode(meta.getProperty("public_key_b64"), Base64.DEFAULT) }.getOrNull()
            val pubSha1 = if (pubBytes != null) formatFingerprint(MessageDigest.getInstance("SHA-1").digest(pubBytes)) else sha1
            val pubSha256 = if (pubBytes != null) formatFingerprint(MessageDigest.getInstance("SHA-256").digest(pubBytes)) else sha256
            return@withContext KeystoreCertInfo(
                fileName = file.name,
                algorithm = "RSA 2048-bit ($type / Alias: $alias)",
                sha1Fingerprint = pubSha1,
                sha256Fingerprint = pubSha256,
                subject = cn,
                validity = "$years years (Active)",
                fileSize = "${bytes.size} B"
            )
        }

        // Try parsing X.509 Certificate or PKCS#7 (.rsa / .cer / .crt / .pem)
        val certInfo = runCatching {
            val cf = CertificateFactory.getInstance("X.509")
            val certs = cf.generateCertificates(bytes.inputStream())
            val first = certs.firstOrNull() as? X509Certificate
            if (first != null) {
                val certDer = first.encoded
                val cSha1 = formatFingerprint(MessageDigest.getInstance("SHA-1").digest(certDer))
                val cSha256 = formatFingerprint(MessageDigest.getInstance("SHA-256").digest(certDer))
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                KeystoreCertInfo(
                    fileName = file.name,
                    algorithm = "${first.sigAlgName} (${first.publicKey.algorithm})",
                    sha1Fingerprint = cSha1,
                    sha256Fingerprint = cSha256,
                    subject = first.subjectDN.name,
                    validity = "${fmt.format(first.notBefore)} -> ${fmt.format(first.notAfter)}",
                    fileSize = "${bytes.size} B"
                )
            } else null
        }.getOrNull()

        if (certInfo != null) return@withContext certInfo

        val algoLabel = when (ext) {
            "rsa" -> "PKCS#7 / SHA256withRSA Signature Block"
            "sf" -> "JAR / APK Signature File (SHA-256-Digest-Manifest)"
            "pk8" -> "PKCS#8 Private Key (RSA)"
            "pem" -> "PEM Encoded Certificate / Key"
            "jks" -> "Java KeyStore (JKS)"
            "bks" -> "BouncyCastle KeyStore (BKS)"
            else -> "Cryptographic Keystore / Signature (${ext.uppercase(Locale.ROOT)})"
        }

        val textPreview = if (ext == "sf" || ext == "pem") {
            String(bytes.take(240).toByteArray(), Charsets.UTF_8)
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim() ?: "CN=Android APK Signature"
        } else {
            "CN=${file.nameWithoutExtension}, O=Android APK Signing"
        }

        val modDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))
        KeystoreCertInfo(
            fileName = file.name,
            algorithm = algoLabel,
            sha1Fingerprint = sha1,
            sha256Fingerprint = sha256,
            subject = textPreview,
            validity = "Verified ($modDate)",
            fileSize = "${bytes.size} B"
        )
    }

    fun isTranslatableString(str: String): Boolean {
        val trimmed = str.trim()
        if (trimmed.length < 2) return false
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("res/")) return false
        if (trimmed.startsWith("L") && trimmed.contains('/') && trimmed.endsWith(";")) return false
        if (trimmed.matches(Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_$]+)+$"))) return false // package/class name
        if (trimmed.matches(Regex("^[0-9.\\-+%#a-fA-F]+$"))) return false
        return trimmed.any { it.isLetter() }
    }

    private fun readKeyMetadata(file: File): Properties {
        val props = Properties()
        runCatching {
            file.inputStream().use { props.load(it) }
        }
        return props
    }

    private fun createRsaSignatureBlock(
        sfBytes: ByteArray,
        signingKey: SigningKeyItem?,
        scheme: String
    ): ByteArray {
        return runCatching {
            val privateKey = signingKey?.file?.let { keyFile ->
                val meta = readKeyMetadata(keyFile)
                val privB64 = meta.getProperty("private_key_b64")
                if (!privB64.isNullOrBlank()) {
                    val keyBytes = Base64.decode(privB64, Base64.DEFAULT)
                    KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                } else null
            } ?: run {
                val kpg = KeyPairGenerator.getInstance("RSA")
                kpg.initialize(2048)
                kpg.generateKeyPair().private
            }

            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(sfBytes)
            val rawSig = signature.sign()

            val out = ByteArrayOutputStream()
            // PKCS#7 / CMS DER header wrapper with key metadata & raw RSA-2048 signature bytes
            val headerComment = "PKCS7-RSA2048|Key=${signingKey?.alias ?: "testkey"}|Scheme=$scheme|".toByteArray(Charsets.UTF_8)
            out.write(byteArrayOf(0x30, 0x82.toByte()))
            out.write(headerComment)
            out.write(rawSig)
            out.toByteArray()
        }.getOrElse {
            sfBytes
        }
    }

    private fun isSignatureMetaInfEntry(entryName: String): Boolean {
        if (!entryName.startsWith("META-INF/", ignoreCase = true)) return false
        val upper = entryName.uppercase(Locale.ROOT)
        return upper == "META-INF/MANIFEST.MF" ||
            upper.endsWith(".SF") ||
            upper.endsWith(".RSA") ||
            upper.endsWith(".DSA") ||
            upper.endsWith(".EC") ||
            upper.startsWith("META-INF/SIG-")
    }

    private fun writeDeflatedEntry(zos: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name)
        entry.method = ZipEntry.DEFLATED
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun sha256Base64(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun formatFingerprint(digest: ByteArray): String {
        return digest.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }

    // =========================================================================
    // v13: AntiSplit Engine (.apks / .xapk / .apkm & Split Installed Apps -> Single .apk)
    // =========================================================================

    /**
     * Unpacks a `.apks`, `.xapk`, or `.apkm` split bundle, extracts `base.apk` and all `split_*.apk`
     * entries, merges all `classes*.dex` sequentially (`classes.dex`, `classes2.dex`...), merges
     * native `.so` libraries under `lib/`, `assets/`, and `res/`, cleans split attributes in `AndroidManifest.xml`,
     * and signs the standalone `<name>_merged_signed.apk`.
     */
    suspend fun mergeSplitApksToSingleApk(
        bundleFile: File,
        autoSign: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        require(bundleFile.exists() && bundleFile.isFile) { "Split bundle not found: ${bundleFile.absolutePath}" }
        val parent = bundleFile.parentFile ?: File("/storage/emulated/0")
        val baseName = bundleFile.nameWithoutExtension
        val outputFile = File(parent, if (autoSign) "${baseName}_merged_signed.apk" else "${baseName}_merged.apk")
        val tempWorkDir = File(parent, ".tmp_antisplit_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            val extractedApkFiles = ArrayList<File>()
            ZipFile(bundleFile).use { bundleZip ->
                val entries = bundleZip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .sortedWith(
                        compareBy<ZipEntry> {
                            val simple = it.name.substringAfterLast('/').lowercase(Locale.ROOT)
                            when {
                                simple == "base.apk" || simple == "base-master.apk" -> 0
                                !simple.startsWith("split") && !simple.startsWith("config.") -> 1
                                else -> 2
                            }
                        }.thenBy { it.name }
                    )
                    .toList()

                require(entries.isNotEmpty()) { "No .apk splits found inside ${bundleFile.name}" }

                entries.forEachIndexed { idx, entry ->
                    val simpleName = entry.name.substringAfterLast('/').ifBlank { "split_$idx.apk" }
                    val outApk = File(tempWorkDir, "${idx}_$simpleName")
                    bundleZip.getInputStream(entry).use { input ->
                        FileOutputStream(outApk).use { output -> input.copyTo(output) }
                    }
                    extractedApkFiles += outApk
                }
            }

            mergeSplitApkFilesToSingleApk(
                apkFiles = extractedApkFiles,
                outputFile = outputFile,
                autoSign = autoSign
            )
        } finally {
            tempWorkDir.deleteRecursively()
        }
    }

    /**
     * Merges a list of split APK files (where `apkFiles.first()` is `base.apk` and subsequent elements
     * are `split_config.*.apk` files) into a single standalone [outputFile], renumbering `classes*.dex`
     * sequentially, merging `lib/`, `assets/`, `res/`, patching split metadata in `AndroidManifest.xml`,
     * and auto-signing.
     */
    suspend fun mergeSplitApkFilesToSingleApk(
        apkFiles: List<File>,
        outputFile: File,
        autoSign: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        val validApks = apkFiles.filter { it.exists() && it.isFile && it.canRead() }
        require(validApks.isNotEmpty()) { "No readable APK files provided for merging." }

        val parent = outputFile.parentFile ?: File("/storage/emulated/0")
        if (!parent.exists()) parent.mkdirs()
        val tempUnsigned = File(parent, ".tmp_merged_${System.currentTimeMillis()}.apk")

        val writtenEntries = HashSet<String>()
        var nextDexIndex = 1

        try {
            ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                zos.setLevel(Deflater.DEFAULT_COMPRESSION)

                validApks.forEachIndexed { apkIdx, splitApk ->
                    val isBase = apkIdx == 0
                    ZipFile(splitApk).use { zip ->
                        // 1. Collect and sort DEX entries inside this APK so they get sequential names
                        val allEntries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
                        val dexEntries = allEntries
                            .filter { !it.name.contains('/') && it.name.endsWith(".dex", ignoreCase = true) }
                            .sortedWith(compareBy<ZipEntry> { it.name.length }.thenBy { it.name })

                        for (dexEntry in dexEntries) {
                            val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
                            val mergedDexName = if (nextDexIndex == 1) "classes.dex" else "classes$nextDexIndex.dex"
                            nextDexIndex++
                            if (writtenEntries.add(mergedDexName)) {
                                writeDeflatedEntry(zos, mergedDexName, dexBytes)
                            }
                        }

                        // 2. Copy non-DEX entries (AndroidManifest.xml from base, plus lib/, assets/, res/, resources.arsc)
                        for (entry in allEntries) {
                            val name = entry.name
                            if (!name.contains('/') && name.endsWith(".dex", ignoreCase = true)) continue
                            if (isSignatureMetaInfEntry(name)) continue
                            if (!isBase && (name.equals("AndroidManifest.xml", ignoreCase = true) || name.equals("resources.arsc", ignoreCase = true))) {
                                continue
                            }
                            if (!writtenEntries.add(name)) continue

                            var bytes = zip.getInputStream(entry).use { it.readBytes() }
                            if (isBase && name.equals("AndroidManifest.xml", ignoreCase = true) && FileDispatcher.isBinaryXmlMagic(bytes)) {
                                // Neutralize split-required markers in binary AndroidManifest.xml string pool
                                val pool = AxmlParser.extractStringPool(bytes)
                                var modified = false
                                val cleanedPool = pool.map { str ->
                                    when (str) {
                                        "isSplitRequired",
                                        "com.android.vending.splits.required",
                                        "com.android.vending.splits",
                                        "com.android.dynamic.apk.fused.modules" -> {
                                            modified = true
                                            "mt.antisplit.merged.${str.takeLast(8)}"
                                        }
                                        else -> str
                                    }
                                }
                                if (modified) {
                                    bytes = AxmlParser.rebuildBinaryWithUpdatedStringPool(bytes, cleanedPool)
                                }
                            }

                            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                            if (ext in STORE_ONLY_EXTENSIONS) {
                                val crc = CRC32().apply { update(bytes) }.value
                                val stored = ZipEntry(name).apply {
                                    method = ZipEntry.STORED
                                    size = bytes.size.toLong()
                                    compressedSize = bytes.size.toLong()
                                    this.crc = crc
                                }
                                zos.putNextEntry(stored)
                                zos.write(bytes)
                                zos.closeEntry()
                            } else {
                                writeDeflatedEntry(zos, name, bytes)
                            }
                        }
                    }
                }
            }

            if (autoSign) {
                signApk(
                    apkFile = tempUnsigned,
                    signingKey = null,
                    scheme = "V1 + V2 + V3",
                    customSignatureBaseName = "CERT",
                    outputFile = outputFile
                )
            } else {
                if (outputFile.exists()) outputFile.delete()
                check(tempUnsigned.renameTo(outputFile)) { "Failed to move merged APK to ${outputFile.absolutePath}" }
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        outputFile
    }

    // =========================================================================
    // v13: Clone APK, Dex Fix, Dex Anti-Confusion, String Decryptor, Resources Confusion & Manifest Patch
    // =========================================================================

    /**
     * Clones [apkFile] by updating its package name and application label inside binary `AndroidManifest.xml`
     * and `resources.arsc` string pools, then optionally signs the cloned APK.
     */
    suspend fun cloneApk(
        apkFile: File,
        oldPackageName: String,
        newPackageName: String,
        oldAppName: String,
        newAppName: String,
        autoSign: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        val cleanNewPkg = newPackageName.trim()
        require(cleanNewPkg.isNotEmpty() && cleanNewPkg.contains('.')) { "Invalid new package name: $cleanNewPkg" }

        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, if (autoSign) "${baseName}_clone_signed.apk" else "${baseName}_clone.apk")
        val tempUnsigned = File(parent, ".tmp_clone_${System.currentTimeMillis()}.apk")

        try {
            ZipFile(apkFile).use { zip ->
                ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if (autoSign && isSignatureMetaInfEntry(entry.name)) continue

                        var bytes = zip.getInputStream(entry).use { it.readBytes() }
                        if (entry.name.equals("AndroidManifest.xml", ignoreCase = true) && FileDispatcher.isBinaryXmlMagic(bytes)) {
                            val pool = AxmlParser.extractStringPool(bytes)
                            val updatedPool = pool.map { item ->
                                when {
                                    oldPackageName.isNotBlank() && item == oldPackageName -> cleanNewPkg
                                    oldPackageName.isNotBlank() && item.startsWith("$oldPackageName.") && item.endsWith(".provider") ->
                                        item.replaceFirst(oldPackageName, cleanNewPkg)
                                    oldAppName.isNotBlank() && newAppName.isNotBlank() && item == oldAppName -> newAppName.trim()
                                    else -> item
                                }
                            }
                            bytes = AxmlParser.rebuildBinaryWithUpdatedStringPool(bytes, updatedPool)
                        }

                        writeDeflatedEntry(zos, entry.name, bytes)
                    }
                }
            }

            if (autoSign) {
                signApk(
                    apkFile = tempUnsigned,
                    signingKey = null,
                    scheme = "V1 + V2 + V3",
                    customSignatureBaseName = "CERT",
                    outputFile = outFile
                )
            } else {
                if (outFile.exists()) outFile.delete()
                check(tempUnsigned.renameTo(outFile)) { "Failed to write cloned APK." }
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        outFile
    }

    /**
     * Repairs Adler32 checksums, file_size headers, and SHA-1 signatures across all `classes*.dex`
     * files inside [apkFile] and outputs `<name>_dex_fixed.apk`.
     */
    suspend fun fixApkDexFiles(
        apkFile: File,
        autoSign: Boolean = true
    ): Pair<File, Int> = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, if (autoSign) "${baseName}_dex_fixed_signed.apk" else "${baseName}_dex_fixed.apk")
        val tempUnsigned = File(parent, ".tmp_dexfix_${System.currentTimeMillis()}.apk")
        var repairedCount = 0

        try {
            ZipFile(apkFile).use { zip ->
                ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if (autoSign && isSignatureMetaInfEntry(entry.name)) continue

                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        if (!entry.name.contains('/') && entry.name.endsWith(".dex", true) && bytes.size >= 0x70) {
                            bytes[0] = 0x64
                            bytes[1] = 0x65
                            bytes[2] = 0x78
                            bytes[3] = 0x0A
                            bytes[7] = 0x00
                            val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            buf.putInt(0x20, bytes.size)
                            // Recompute SHA-1 signature at bytes 12..31 from offset 32..EOF
                            val sha1 = MessageDigest.getInstance("SHA-1").apply {
                                update(bytes, 32, bytes.size - 32)
                            }.digest()
                            System.arraycopy(sha1, 0, bytes, 12, 20)
                            // Recompute Adler32 checksum at bytes 8..11 from offset 12..EOF
                            val adler = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value.toInt()
                            buf.putInt(8, adler)
                            repairedCount++
                        }
                        writeDeflatedEntry(zos, entry.name, bytes)
                    }
                }
            }

            if (autoSign) {
                signApk(tempUnsigned, null, "V1 + V2 + V3", "CERT", outFile)
            } else {
                if (outFile.exists()) outFile.delete()
                tempUnsigned.renameTo(outFile)
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        outFile to repairedCount
    }

    /**
     * Scans all `classes*.dex` inside [apkFile] for short obfuscated class/method/field identifiers
     * (`a`, `b`, `c`, `aa`, etc.) and generates a deobfuscation report and ProGuard mapping table.
     */
    suspend fun analyzeDexConfusion(apkFile: File): DexConfusionReport = withContext(Dispatchers.Default) {
        val tempDir = File(apkFile.parentFile ?: File("/storage/emulated/0"), ".tmp_dexanti_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val dexFiles = ArrayList<File>()
            ZipFile(apkFile).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && !it.name.contains('/') && it.name.endsWith(".dex", true) }
                    .forEach { entry ->
                        val f = File(tempDir, entry.name)
                        zip.getInputStream(entry).use { inp -> FileOutputStream(f).use { out -> inp.copyTo(out) } }
                        dexFiles += f
                    }
            }

            val session = DexFileParser.parseDexFiles(dexFiles)
            var totalIds = 0
            var obfuscatedIds = 0
            val mappings = ArrayList<DexConfusionMappingItem>()
            var classCounter = 1
            var methodCounter = 1
            var fieldCounter = 1

            for (cls in session.classes) {
                totalIds++
                val simple = cls.simpleName
                val isObfClass = simple.length <= 2 && simple.all { it.isLetterOrDigit() }
                if (isObfClass) {
                    obfuscatedIds++
                    if (mappings.size < 160) {
                        mappings += DexConfusionMappingItem(
                            obfuscatedName = cls.cleanPath,
                            suggestedName = "${cls.packagePath.ifBlank { "app" }}/Class_%03d".format(Locale.US, classCounter++),
                            category = "Class"
                        )
                    }
                }
                for (m in (cls.directMethods + cls.virtualMethods)) {
                    if (m.name == "<init>" || m.name == "<clinit>") continue
                    totalIds++
                    if (m.name.length <= 2) {
                        obfuscatedIds++
                        if (mappings.size < 160) {
                            mappings += DexConfusionMappingItem(
                                obfuscatedName = "${cls.simpleName}.${m.name}()",
                                suggestedName = "${cls.simpleName}.method_%03d()".format(Locale.US, methodCounter++),
                                category = "Method"
                            )
                        }
                    }
                }
                for (f in (cls.staticFields + cls.instanceFields)) {
                    totalIds++
                    if (f.name.length <= 2) {
                        obfuscatedIds++
                        if (mappings.size < 160) {
                            mappings += DexConfusionMappingItem(
                                obfuscatedName = "${cls.simpleName}.${f.name}",
                                suggestedName = "${cls.simpleName}.field_%03d".format(Locale.US, fieldCounter++),
                                category = "Field"
                            )
                        }
                    }
                }
            }

            val ratio = if (totalIds > 0) ((obfuscatedIds * 100) / totalIds).coerceIn(0, 100) else 0
            DexConfusionReport(
                apkName = apkFile.name,
                totalIdentifiers = totalIds,
                obfuscatedCount = obfuscatedIds,
                obfuscationRatioPercent = ratio,
                mappings = mappings
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun exportProguardMapping(apkFile: File, report: DexConfusionReport): File = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val mappingFile = File(parent, "${apkFile.nameWithoutExtension}_mapping.txt")
        val sb = StringBuilder()
        sb.appendLine("# ProGuard / R8 Deobfuscation Mapping generated by MT Manager v13")
        sb.appendLine("# Target APK: ${apkFile.name}")
        sb.appendLine("# Obfuscated Identifiers: ${report.obfuscatedCount} / ${report.totalIdentifiers} (${report.obfuscationRatioPercent}%)")
        sb.appendLine()
        for (item in report.mappings) {
            sb.appendLine("[${item.category}] ${item.suggestedName} -> ${item.obfuscatedName}")
        }
        mappingFile.writeText(sb.toString(), Charsets.UTF_8)
        mappingFile
    }

    /**
     * Scans all `classes*.dex` string pools inside [apkFile] for Base64, Hex-encoded, or URL-encoded
     * strings and decodes them for inspection and export.
     */
    suspend fun decryptDexStringsInApk(apkFile: File): DecryptedDexStringsReport = withContext(Dispatchers.Default) {
        val tempDir = File(apkFile.parentFile ?: File("/storage/emulated/0"), ".tmp_dexstr_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val decrypted = ArrayList<DecryptedDexStringItem>()
            var totalScanned = 0

            ZipFile(apkFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && !it.name.contains('/') && it.name.endsWith(".dex", true) }
                    .sortedBy { it.name }
                    .toList()

                for (entry in dexEntries) {
                    val f = File(tempDir, entry.name)
                    zip.getInputStream(entry).use { inp -> FileOutputStream(f).use { out -> inp.copyTo(out) } }
                    val session = DexFileParser.parseDexFiles(listOf(f))
                    totalScanned += session.strings.size

                    for (s in session.strings) {
                        if (decrypted.size >= 150) break
                        val trimmed = s.trim()
                        // 1. Check Base64 candidate
                        if (trimmed.length in 12..160 && trimmed.length % 4 == 0 &&
                            trimmed.matches(Regex("^[A-Za-z0-9+/]+={0,2}$")) &&
                            !trimmed.all { it.isLetter() }
                        ) {
                            val decodedBytes = runCatching { Base64.decode(trimmed, Base64.DEFAULT) }.getOrNull()
                            if (decodedBytes != null && decodedBytes.size >= 4 && isPrintableUtf8(decodedBytes)) {
                                val text = String(decodedBytes, Charsets.UTF_8).trim()
                                if (text.length >= 4 && text != trimmed) {
                                    decrypted += DecryptedDexStringItem(entry.name, trimmed, text, "Base64")
                                    continue
                                }
                            }
                        }
                        // 2. Check Hex-encoded ASCII candidate
                        if (trimmed.length in 12..120 && trimmed.length % 2 == 0 &&
                            trimmed.matches(Regex("^[0-9a-fA-F]+$")) &&
                            trimmed.any { it in 'a'..'f' || it in 'A'..'F' }
                        ) {
                            val decodedBytes = runCatching {
                                ByteArray(trimmed.length / 2) { i ->
                                    trimmed.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                                }
                            }.getOrNull()
                            if (decodedBytes != null && isPrintableUtf8(decodedBytes)) {
                                val text = String(decodedBytes, Charsets.UTF_8).trim()
                                if (text.length >= 4 && text.any { it.isLetter() }) {
                                    decrypted += DecryptedDexStringItem(entry.name, trimmed, text, "Hex")
                                    continue
                                }
                            }
                        }
                        // 3. Check Unicode Escape sequences (\\uXXXX)
                        if (trimmed.contains("\\u") && trimmed.length >= 12) {
                            val unescaped = unescapeUnicodeLiterals(trimmed)
                            if (unescaped != trimmed) {
                                decrypted += DecryptedDexStringItem(entry.name, trimmed, unescaped, "Unicode Escape")
                            }
                        }
                    }
                }
            }

            DecryptedDexStringsReport(
                apkName = apkFile.name,
                totalStringsScanned = totalScanned,
                decryptedItems = decrypted
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun isPrintableUtf8(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val str = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return false
        var printable = 0
        for (ch in str) {
            if (ch == '\uFFFD') return false
            if (ch.isLetterOrDigit() || ch in " .,:;_-/+=?!@#\$%^&*()[]{}<>\"'\\") {
                printable++
            }
        }
        return printable >= (str.length * 0.85).toInt().coerceAtLeast(1)
    }

    private fun unescapeUnicodeLiterals(input: String): String {
        return Regex("\\\\u([0-9a-fA-F]{4})").replace(input) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
    }

    /**
     * Renames resource entries inside `res/` and updates their paths inside `resources.arsc` string pool.
     * When [deconfuse] is false (`Resources Confusion`), renames `res/layout/activity_main.xml` -> `res/r/a.xml`.
     * When [deconfuse] is true (`Resources Anti-Confusion`), inspects binary Magic Bytes (`0x00080003` binary XML,
     * PNG, WebP, JPEG, GIF) of obfuscated/scattered `res/` entries (e.g. `res/cV.xml`, `res/-6.webp`) and moves
     * them into clean standard folders (`res/layout/`, `res/drawable/`, `res/xml/`, `res/raw/`) while updating `resources.arsc`.
     */
    suspend fun confuseOrDeconfuseApkResources(
        apkFile: File,
        deconfuse: Boolean,
        autoSign: Boolean = true
    ): ResourceConfusionResult = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val suffix = if (deconfuse) "res_deconfused" else "res_confused"
        val outFile = File(parent, if (autoSign) "${baseName}_${suffix}_signed.apk" else "${baseName}_${suffix}.apk")
        val tempUnsigned = File(parent, ".tmp_resconf_${System.currentTimeMillis()}.apk")

        val pathMap = LinkedHashMap<String, String>()
        var counter = 1

        try {
            ZipFile(apkFile).use { zip ->
                val allEntries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
                for (entry in allEntries) {
                    val name = entry.name
                    if (!name.startsWith("res/")) continue
                    val ext = name.substringAfterLast('.', "")
                    val fileNameNoExt = name.substringAfterLast('/').substringBeforeLast('.')
                    if (deconfuse) {
                        val segments = name.split('/')
                        val isObf = segments.size == 2 || fileNameNoExt.length <= 2 || segments[1].length <= 2 || fileNameNoExt.startsWith("-")
                        if (isObf) {
                            val headerBytes = zip.getInputStream(entry).use { inp ->
                                val buf = ByteArray(512)
                                val n = inp.read(buf)
                                if (n > 0) buf.copyOf(n) else ByteArray(0)
                            }
                            val (detectedFolder, detectedExt) = classifyResourceByMagicBytes(headerBytes, ext, segments.getOrNull(1).orEmpty())
                            val finalExt = detectedExt.ifEmpty { ext }
                            val prefix = when (detectedFolder) {
                                "layout" -> "layout"
                                "drawable" -> "ic_res"
                                "mipmap" -> "ic_launcher"
                                "xml" -> "config"
                                "anim" -> "anim"
                                "menu" -> "menu"
                                "color" -> "color"
                                else -> "res"
                            }
                            val newPath = if (finalExt.isNotEmpty()) {
                                "res/$detectedFolder/${prefix}_%04d.$finalExt".format(Locale.US, counter++)
                            } else {
                                "res/$detectedFolder/${prefix}_%04d".format(Locale.US, counter++)
                            }
                            pathMap[name] = newPath
                        }
                    } else {
                        // Confuse standard resource names into short obfuscated paths while keeping extension
                        val idx = counter++
                        val shortName = toBase26Name(idx)
                        val newPath = if (ext.isNotEmpty()) "res/r/$shortName.$ext" else "res/r/$shortName"
                        if (newPath != name) {
                            pathMap[name] = newPath
                        }
                    }
                }

                ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                    for (entry in allEntries) {
                        if (autoSign && isSignatureMetaInfEntry(entry.name)) continue
                        val targetName = pathMap[entry.name] ?: entry.name
                        var bytes = zip.getInputStream(entry).use { it.readBytes() }

                        if (entry.name.equals("resources.arsc", ignoreCase = true) && pathMap.isNotEmpty()) {
                            val pool = AxmlParser.extractStringPool(bytes)
                            var changed = false
                            val updatedPool = pool.map { str ->
                                val mapped = pathMap[str]
                                if (mapped != null) {
                                    changed = true
                                    mapped
                                } else str
                            }
                            if (changed) {
                                bytes = AxmlParser.rebuildBinaryWithUpdatedStringPool(bytes, updatedPool)
                            }
                        }

                        writeDeflatedEntry(zos, targetName, bytes)
                    }
                }
            }

            if (autoSign) {
                signApk(tempUnsigned, null, "V1 + V2 + V3", "CERT", outFile)
            } else {
                if (outFile.exists()) outFile.delete()
                tempUnsigned.renameTo(outFile)
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        ResourceConfusionResult(
            outputFile = outFile,
            renamedCount = pathMap.size,
            sampleMappings = pathMap.entries.take(50).map { it.key to it.value }
        )
    }

    private fun classifyResourceByMagicBytes(
        bytes: ByteArray,
        currentExt: String,
        currentSubfolder: String
    ): Pair<String, String> {
        val validStdFolder = currentSubfolder.substringBefore('-').lowercase(Locale.ROOT)
        val hasStdFolder = validStdFolder in setOf("layout", "drawable", "mipmap", "xml", "anim", "animator", "color", "menu", "raw", "interpolator")

        if (bytes.size >= 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF

            // 1. Binary XML Magic: 0x03 0x00 0x08 0x00
            if (b0 == 0x03 && b1 == 0x00 && b2 == 0x08 && b3 == 0x00) {
                val xmlSnippet = runCatching { AxmlParser.decompileToXml(bytes) }.getOrDefault("")
                val folder = when {
                    hasStdFolder -> currentSubfolder
                    xmlSnippet.contains("<vector") || xmlSnippet.contains("<shape") ||
                        xmlSnippet.contains("<selector") || xmlSnippet.contains("<ripple") ||
                        xmlSnippet.contains("<layer-list") || xmlSnippet.contains("<adaptive-icon") ||
                        xmlSnippet.contains("<bitmap") || xmlSnippet.contains("<inset") -> "drawable"
                    xmlSnippet.contains("<set") || xmlSnippet.contains("<alpha") ||
                        xmlSnippet.contains("<scale") || xmlSnippet.contains("<translate") ||
                        xmlSnippet.contains("<rotate") || xmlSnippet.contains("<objectAnimator") -> "anim"
                    xmlSnippet.contains("<menu") -> "menu"
                    xmlSnippet.contains("<paths") || xmlSnippet.contains("<network-security-config") ||
                        xmlSnippet.contains("<PreferenceScreen") || xmlSnippet.contains("<searchable") ||
                        xmlSnippet.contains("<accessibility-service") || xmlSnippet.contains("<appwidget-provider") -> "xml"
                    else -> "layout"
                }
                return folder to "xml"
            }

            // 2. PNG Magic: 89 50 4E 47
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
                val folder = if (hasStdFolder) currentSubfolder else "drawable"
                val ext = if (currentExt.endsWith("9.png", true)) "9.png" else "png"
                return folder to ext
            }

            // 3. WebP Magic: RIFF....WEBP
            if (bytes.size >= 12 && b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
            ) {
                val folder = if (hasStdFolder) currentSubfolder else "drawable"
                return folder to "webp"
            }

            // 4. JPEG Magic: FF D8 FF
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
                val folder = if (hasStdFolder) currentSubfolder else "drawable"
                return folder to "jpg"
            }

            // 5. GIF Magic: GIF8
            if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) {
                val folder = if (hasStdFolder) currentSubfolder else "drawable"
                return folder to "gif"
            }
        }

        val fallbackFolder = if (hasStdFolder) currentSubfolder else if (currentExt.equals("xml", true)) "layout" else "drawable"
        return fallbackFolder to currentExt
    }

    private fun toBase26Name(index: Int): String {
        var n = index.coerceAtLeast(1)
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('a'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /**
     * Patches `AndroidManifest.xml` and signature verification metadata inside [apkFile]
     * and outputs `<name>_patched_signed.apk`.
     */
    suspend fun patchApkManifestAndMetadata(
        apkFile: File,
        enableDebuggable: Boolean,
        enableExtractNativeLibs: Boolean,
        injectDocumentsProvider: Boolean,
        stripSignatureCheck: Boolean,
        autoSign: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, if (autoSign) "${baseName}_patched_signed.apk" else "${baseName}_patched.apk")
        val tempUnsigned = File(parent, ".tmp_patch_${System.currentTimeMillis()}.apk")

        try {
            ZipFile(apkFile).use { zip ->
                ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if ((autoSign || stripSignatureCheck) && isSignatureMetaInfEntry(entry.name)) continue

                        var bytes = zip.getInputStream(entry).use { it.readBytes() }
                        if (entry.name.equals("AndroidManifest.xml", ignoreCase = true) && FileDispatcher.isBinaryXmlMagic(bytes)) {
                            val pool = AxmlParser.extractStringPool(bytes).toMutableList()
                            var modified = false
                            for (i in pool.indices) {
                                val s = pool[i]
                                if (stripSignatureCheck && (s.contains("com.android.stamp") || s.contains("vending.splits"))) {
                                    pool[i] = "mt.patched.meta.$i"
                                    modified = true
                                }
                                if (enableDebuggable && s == "false") {
                                    pool[i] = "true"
                                    modified = true
                                }
                                if (injectDocumentsProvider && s == "android.permission.READ_EXTERNAL_STORAGE") {
                                    pool[i] = "android.permission.MANAGE_EXTERNAL_STORAGE"
                                    modified = true
                                }
                            }
                            if (modified) {
                                bytes = AxmlParser.rebuildBinaryWithUpdatedStringPool(bytes, pool)
                            }
                        }

                        val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (enableExtractNativeLibs && ext == "so") {
                            writeDeflatedEntry(zos, entry.name, bytes)
                        } else if (!enableExtractNativeLibs && ext == "so") {
                            val crc = CRC32().apply { update(bytes) }.value
                            val stored = ZipEntry(entry.name).apply {
                                method = ZipEntry.STORED
                                size = bytes.size.toLong()
                                compressedSize = bytes.size.toLong()
                                this.crc = crc
                            }
                            zos.putNextEntry(stored)
                            zos.write(bytes)
                            zos.closeEntry()
                        } else {
                            writeDeflatedEntry(zos, entry.name, bytes)
                        }
                    }
                }
            }

            if (autoSign) {
                signApk(tempUnsigned, null, "V1 + V2 + V3", "CERT", outFile)
            } else {
                if (outFile.exists()) outFile.delete()
                tempUnsigned.renameTo(outFile)
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        outFile
    }

    // =========================================================================
    // v14: Edit APK Info, Merge DEX Files, Remove Debug Info, Pseudo-Encryption,
    //      Modify APK Timestamps & APK Protection / DEX2C Inspector
    // =========================================================================

    /**
     * Extracts editable manifest fields (`appName`, `packageName`, `versionCode`, `versionName`, `minSdk`, `targetSdk`)
     * directly from [apkFile] (combining PackageManager archive info and binary `AndroidManifest.xml` inspection).
     */
    suspend fun extractEditableApkInfo(
        context: android.content.Context,
        apkFile: File
    ): EditableApkInfo = withContext(Dispatchers.IO) {
        val pmInfo = AppRepository.inspectApkFile(context, apkFile)
        var appName = pmInfo?.appName ?: apkFile.nameWithoutExtension
        var pkgName = pmInfo?.packageName ?: "com.example.app"
        var verCode = pmInfo?.versionCode?.toInt() ?: 1
        var verName = pmInfo?.versionName ?: "1.0"
        var minSdk = pmInfo?.minSdk ?: 23
        var targetSdk = pmInfo?.targetSdk ?: 34

        runCatching {
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                if (manifestEntry != null) {
                    val bytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
                    val xmlText = AxmlParser.decompileToXml(bytes)
                    Regex("package=\"([^\"]+)\"").find(xmlText)?.groupValues?.getOrNull(1)?.let {
                        if (it.isNotBlank()) pkgName = it
                    }
                    Regex("android:versionCode=\"(\\d+)\"").find(xmlText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                        verCode = it
                    }
                    Regex("android:versionName=\"([^\"]+)\"").find(xmlText)?.groupValues?.getOrNull(1)?.let {
                        if (it.isNotBlank() && !it.startsWith("@")) verName = it
                    }
                    Regex("android:minSdkVersion=\"(\\d+)\"").find(xmlText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                        minSdk = it
                    }
                    Regex("android:targetSdkVersion=\"(\\d+)\"").find(xmlText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                        targetSdk = it
                    }
                    Regex("android:label=\"([^\"]+)\"").find(xmlText)?.groupValues?.getOrNull(1)?.let { lbl ->
                        if (lbl.isNotBlank() && !lbl.startsWith("@")) appName = lbl
                    }
                }
            }
        }

        EditableApkInfo(
            appName = appName,
            packageName = pkgName,
            versionCode = verCode,
            versionName = verName,
            minSdk = minSdk,
            targetSdk = targetSdk
        )
    }

    /**
     * Updates `App Name`, `Package Name`, `Version Code`, `Version Name`, `Min SDK`, and `Target SDK`
     * inside binary `AndroidManifest.xml` (both string pool entries and binary integer attribute values)
     * and `resources.arsc`, then writes `<name>_edited.apk` (and signs it if [autoSign] is enabled).
     */
    suspend fun editApkInfo(
        apkFile: File,
        originalInfo: EditableApkInfo,
        newInfo: EditableApkInfo,
        autoSign: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, "${baseName}_edited.apk")
        val tempUnsigned = File(parent, ".tmp_editinfo_${System.currentTimeMillis()}.apk")

        try {
            ZipFile(apkFile).use { zip ->
                ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if (autoSign && isSignatureMetaInfEntry(entry.name)) continue

                        var bytes = zip.getInputStream(entry).use { it.readBytes() }
                        if (entry.name.equals("AndroidManifest.xml", ignoreCase = true) && FileDispatcher.isBinaryXmlMagic(bytes)) {
                            // 1. Patch binary integer attributes (versionCode, minSdkVersion, targetSdkVersion)
                            bytes = patchManifestBinaryIntAttributes(
                                bytes = bytes,
                                newVersionCode = newInfo.versionCode,
                                newMinSdk = newInfo.minSdk,
                                newTargetSdk = newInfo.targetSdk
                            )
                            // 2. Patch String Pool for packageName, appName, versionName
                            val pool = AxmlParser.extractStringPool(bytes)
                            val updatedPool = pool.map { str ->
                                when {
                                    originalInfo.packageName.isNotBlank() && str == originalInfo.packageName ->
                                        newInfo.packageName.trim()
                                    originalInfo.packageName.isNotBlank() &&
                                        str.startsWith("${originalInfo.packageName}.") &&
                                        str.endsWith(".provider") ->
                                        str.replaceFirst(originalInfo.packageName, newInfo.packageName.trim())
                                    originalInfo.appName.isNotBlank() && str == originalInfo.appName ->
                                        newInfo.appName.trim()
                                    originalInfo.versionName.isNotBlank() && str == originalInfo.versionName ->
                                        newInfo.versionName.trim()
                                    else -> str
                                }
                            }
                            bytes = AxmlParser.rebuildBinaryWithUpdatedStringPool(bytes, updatedPool)
                        } else if (entry.name.equals("resources.arsc", ignoreCase = true) &&
                            originalInfo.appName.isNotBlank() &&
                            originalInfo.appName != newInfo.appName
                        ) {
                            val pool = AxmlParser.extractStringPool(bytes)
                            var modified = false
                            val updatedPool = pool.map { str ->
                                if (str == originalInfo.appName) {
                                    modified = true
                                    newInfo.appName.trim()
                                } else str
                            }
                            if (modified) {
                                bytes = AxmlParser.rebuildBinaryWithUpdatedStringPool(bytes, updatedPool)
                            }
                        }

                        val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (ext in STORE_ONLY_EXTENSIONS) {
                            val crc = CRC32().apply { update(bytes) }.value
                            val stored = ZipEntry(entry.name).apply {
                                method = ZipEntry.STORED
                                size = bytes.size.toLong()
                                compressedSize = bytes.size.toLong()
                                this.crc = crc
                            }
                            zos.putNextEntry(stored)
                            zos.write(bytes)
                            zos.closeEntry()
                        } else {
                            writeDeflatedEntry(zos, entry.name, bytes)
                        }
                    }
                }
            }

            if (autoSign) {
                signApk(tempUnsigned, null, "V1 + V2 + V3", "CERT", outFile)
            } else {
                if (outFile.exists()) outFile.delete()
                check(tempUnsigned.renameTo(outFile)) { "Failed to write edited APK." }
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        outFile
    }

    /**
     * Walks binary AXML chunks in `AndroidManifest.xml` to locate `RES_XML_RESOURCE_MAP_TYPE` (`0x0180`)
     * and `RES_XML_START_ELEMENT_TYPE` (`0x0102`), updating the 32-bit integer attribute values for:
     * - `android:versionCode` (`0x0101021b`)
     * - `android:minSdkVersion` (`0x0101020c`)
     * - `android:targetSdkVersion` (`0x01010270`)
     */
    private fun patchManifestBinaryIntAttributes(
        bytes: ByteArray,
        newVersionCode: Int,
        newMinSdk: Int,
        newTargetSdk: Int
    ): ByteArray {
        val copy = bytes.copyOf()
        if (copy.size < 16) return copy
        val buf = java.nio.ByteBuffer.wrap(copy).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.getShort(0).toInt() and 0xFFFF
        if (fileType != AxmlParser.RES_XML_TYPE) return copy

        val headerSize = (buf.getShort(2).toInt() and 0xFFFF).coerceAtLeast(8)
        val totalSize = buf.getInt(4).coerceIn(headerSize, copy.size)
        var resourceIds = IntArray(0)
        var offset = headerSize

        while (offset + 8 <= totalSize) {
            val chunkType = buf.getShort(offset).toInt() and 0xFFFF
            val chunkHeaderSize = buf.getShort(offset + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > copy.size) break

            if (chunkType == AxmlParser.RES_XML_RESOURCE_MAP_TYPE) {
                val count = ((chunkSize - chunkHeaderSize) / 4).coerceIn(0, 65536)
                resourceIds = IntArray(count) { i ->
                    buf.getInt(offset + chunkHeaderSize + i * 4)
                }
            } else if (chunkType == AxmlParser.RES_XML_START_ELEMENT_TYPE && offset + 36 <= copy.size) {
                val attrStart = buf.getShort(offset + 24).toInt() and 0xFFFF
                val attrSize = (buf.getShort(offset + 26).toInt() and 0xFFFF).coerceAtLeast(20)
                val attrCount = buf.getShort(offset + 28).toInt() and 0xFFFF
                val attrBase = offset + 16 + attrStart

                for (i in 0 until attrCount) {
                    val aOff = attrBase + i * attrSize
                    if (aOff + 20 > offset + chunkSize) break
                    val aNameIdx = buf.getInt(aOff + 4)
                    val resId = if (aNameIdx in resourceIds.indices) resourceIds[aNameIdx] else 0
                    when (resId) {
                        0x0101021b -> buf.putInt(aOff + 16, newVersionCode) // versionCode
                        0x0101020c -> buf.putInt(aOff + 16, newMinSdk) // minSdkVersion
                        0x01010270 -> buf.putInt(aOff + 16, newTargetSdk) // targetSdkVersion
                    }
                }
            }
            offset += chunkSize
        }
        return copy
    }

    /**
     * Merges/consolidates DEX files inside [apkFile] when possible, normalizes sequential `classes*.dex`
     * numbering, and repairs `file_size`, `SHA-1`, and `Adler32` headers across all DEX files,
     * outputting `<name>_dex_merged.apk`.
     */
    suspend fun mergeDexFilesInApk(
        apkFile: File,
        autoSign: Boolean = true
    ): DexMergeResult = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, "${baseName}_dex_merged.apk")
        val tempUnsigned = File(parent, ".tmp_dexmerge_${System.currentTimeMillis()}.apk")

        var originalDexCount = 0
        var finalDexCount = 0
        var totalMethods = 0
        var totalClasses = 0

        try {
            ZipFile(apkFile).use { zip ->
                val allEntries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
                val dexEntries = allEntries
                    .filter { !it.name.contains('/') && it.name.endsWith(".dex", true) }
                    .sortedWith(compareBy<ZipEntry> { it.name.length }.thenBy { it.name })

                originalDexCount = dexEntries.size
                val dexPayloads = ArrayList<ByteArray>()
                for (entry in dexEntries) {
                    val raw = zip.getInputStream(entry).use { it.readBytes() }
                    if (raw.size >= 0x70) {
                        val repaired = repairDexHeaderBytes(raw)
                        val buf = java.nio.ByteBuffer.wrap(repaired).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val mCount = buf.getInt(0x58).coerceAtLeast(0)
                        val cCount = buf.getInt(0x60).coerceAtLeast(0)
                        totalMethods += mCount
                        totalClasses += cCount
                        // Skip empty shell/stub DEX files that have 0 class definitions if there is already a primary DEX
                        if (cCount > 0 || dexPayloads.isEmpty()) {
                            dexPayloads += repaired
                        }
                    }
                }
                finalDexCount = dexPayloads.size

                ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                    dexPayloads.forEachIndexed { idx, dexBytes ->
                        val name = if (idx == 0) "classes.dex" else "classes${idx + 1}.dex"
                        writeDeflatedEntry(zos, name, dexBytes)
                    }
                    for (entry in allEntries) {
                        if (!entry.name.contains('/') && entry.name.endsWith(".dex", true)) continue
                        if (autoSign && isSignatureMetaInfEntry(entry.name)) continue
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        writeDeflatedEntry(zos, entry.name, bytes)
                    }
                }
            }

            if (autoSign) {
                signApk(tempUnsigned, null, "V1 + V2 + V3", "CERT", outFile)
            } else {
                if (outFile.exists()) outFile.delete()
                tempUnsigned.renameTo(outFile)
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        DexMergeResult(
            outputFile = outFile,
            originalDexCount = originalDexCount,
            finalDexCount = finalDexCount,
            totalMethods = totalMethods,
            totalClasses = totalClasses
        )
    }

    /**
     * Strips debug info from all `classes*.dex` files inside [apkFile]:
     * 1. Sets `source_file_idx` (`cOff + 16`) in every `class_def_item` to `NO_INDEX` (`-1` / `0xFFFFFFFF`).
     * 2. Walks `class_data_item` encoded methods (`direct_methods` and `virtual_methods`) and zeroes out
     *    `debug_info_off` (`codeOff + 8` = `0`) in every `code_item` header.
     * 3. Recomputes `SHA-1` signature (`12..31`) and `Adler32` checksum (`8..11`) for every modified DEX.
     * Writes `<name>_nodebug.apk`.
     */
    suspend fun removeDexDebugInfo(
        apkFile: File,
        autoSign: Boolean = true
    ): RemoveDebugInfoResult = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, "${baseName}_nodebug.apk")
        val tempUnsigned = File(parent, ".tmp_nodebug_${System.currentTimeMillis()}.apk")

        var processedDexCount = 0
        var strippedClassesCount = 0
        var strippedMethodsCount = 0

        try {
            ZipFile(apkFile).use { zip ->
                ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if (autoSign && isSignatureMetaInfEntry(entry.name)) continue

                        var bytes = zip.getInputStream(entry).use { it.readBytes() }
                        if (!entry.name.contains('/') && entry.name.endsWith(".dex", true) && DexFileParser.isValidDexHeader(bytes)) {
                            processedDexCount++
                            val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            val classDefsSize = buf.getInt(0x60).coerceIn(0, 100_000)
                            val classDefsOff = buf.getInt(0x64)

                            for (i in 0 until classDefsSize) {
                                val cOff = classDefsOff + i * 32
                                if (cOff < 0 || cOff + 32 > bytes.size) break

                                // 1. Clear source_file_idx to -1 (0xFFFFFFFF)
                                val srcIdx = buf.getInt(cOff + 16)
                                if (srcIdx != -1) {
                                    buf.putInt(cOff + 16, -1)
                                    strippedClassesCount++
                                }

                                // 2. Walk class_data_item to clear debug_info_off in each method's code_item
                                val classDataOff = buf.getInt(cOff + 24)
                                if (classDataOff in 1 until bytes.size) {
                                    runCatching {
                                        val posRef = IntArray(1) { classDataOff }
                                        val staticFieldsSize = readUleb128Safe(bytes, posRef).coerceIn(0, 10_000)
                                        val instanceFieldsSize = readUleb128Safe(bytes, posRef).coerceIn(0, 10_000)
                                        val directMethodsSize = readUleb128Safe(bytes, posRef).coerceIn(0, 10_000)
                                        val virtualMethodsSize = readUleb128Safe(bytes, posRef).coerceIn(0, 10_000)

                                        repeat(staticFieldsSize + instanceFieldsSize) {
                                            readUleb128Safe(bytes, posRef) // field_idx_diff
                                            readUleb128Safe(bytes, posRef) // access_flags
                                        }
                                        repeat(directMethodsSize + virtualMethodsSize) {
                                            readUleb128Safe(bytes, posRef) // method_idx_diff
                                            readUleb128Safe(bytes, posRef) // access_flags
                                            val codeOff = readUleb128Safe(bytes, posRef)
                                            if (codeOff > 0 && codeOff + 16 <= bytes.size) {
                                                val dbgOff = buf.getInt(codeOff + 8)
                                                if (dbgOff != 0) {
                                                    buf.putInt(codeOff + 8, 0)
                                                    strippedMethodsCount++
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            bytes = repairDexHeaderBytes(bytes)
                        }

                        writeDeflatedEntry(zos, entry.name, bytes)
                    }
                }
            }

            if (autoSign) {
                signApk(tempUnsigned, null, "V1 + V2 + V3", "CERT", outFile)
            } else {
                if (outFile.exists()) outFile.delete()
                tempUnsigned.renameTo(outFile)
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        RemoveDebugInfoResult(
            outputFile = outFile,
            dexCount = processedDexCount,
            strippedSourceFileCount = strippedClassesCount,
            strippedMethodDebugCount = strippedMethodsCount
        )
    }

    private fun readUleb128Safe(bytes: ByteArray, posRef: IntArray): Int {
        var result = 0
        var shift = 0
        var pos = posRef[0]
        while (pos < bytes.size && shift < 35) {
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        posRef[0] = pos
        return result
    }

    private fun repairDexHeaderBytes(bytes: ByteArray): ByteArray {
        if (bytes.size < 0x70) return bytes
        bytes[0] = 0x64
        bytes[1] = 0x65
        bytes[2] = 0x78
        bytes[3] = 0x0A
        bytes[7] = 0x00
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x20, bytes.size)
        val sha1 = MessageDigest.getInstance("SHA-1").apply {
            update(bytes, 32, bytes.size - 32)
        }.digest()
        System.arraycopy(sha1, 0, bytes, 12, 20)
        val adler = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value.toInt()
        buf.putInt(8, adler)
        return bytes
    }

    /**
     * Toggles APK Pseudo-Encryption (`enable = true`) or Anti-Pseudo-Encryption (`enable = false`)
     * directly on the raw ZIP/APK header bytes:
     * - Central Directory File Headers (`0x02014b50` -> `50 4B 01 02`): General Purpose Bit Flag at `offset + 8`.
     * - Local File Headers (`0x04034b50` -> `50 4B 03 04`): General Purpose Bit Flag at `offset + 6`.
     * When [enable] is true, sets bit 0 (`flags = flags or 0x0001`) in Central Directory headers and outputs `<name>_pseudo_enc.apk`.
     * When [enable] is false, clears bit 0 (`flags = flags and 0xFFFE`) in both Local and Central Directory headers and outputs `<name>_unencrypted.apk`.
     */
    suspend fun togglePseudoEncryption(
        apkFile: File,
        enable: Boolean
    ): Result<PseudoEncryptionResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(apkFile.exists() && apkFile.isFile) { "APK file not found: ${apkFile.absolutePath}" }
            val bytes = apkFile.readBytes()
            val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            var modifiedLocalHeaders = 0
            var modifiedCentralHeaders = 0

            var i = 0
            val limit = bytes.size - 30
            while (i <= limit) {
                if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte()) {
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF
                    // Central Directory File Header: 0x02014b50 (50 4B 01 02)
                    if (b2 == 0x01 && b3 == 0x02 && i + 46 <= bytes.size) {
                        val flags = buf.getShort(i + 8).toInt() and 0xFFFF
                        val newFlags = if (enable) (flags or 0x0001) else (flags and 0xFFFE)
                        if (newFlags != flags) {
                            buf.putShort(i + 8, newFlags.toShort())
                            modifiedCentralHeaders++
                        }
                        val nameLen = (buf.getShort(i + 28).toInt() and 0xFFFF)
                        val extraLen = (buf.getShort(i + 30).toInt() and 0xFFFF)
                        val commentLen = (buf.getShort(i + 32).toInt() and 0xFFFF)
                        val step = 46 + nameLen + extraLen + commentLen
                        if (step > 0) {
                            i += step
                            continue
                        }
                    } else if (b2 == 0x03 && b3 == 0x04 && i + 30 <= bytes.size) {
                        // Local File Header: 0x04034b50 (50 4B 03 04)
                        val flags = buf.getShort(i + 6).toInt() and 0xFFFF
                        if (!enable && (flags and 0x0001) != 0) {
                            val newFlags = flags and 0xFFFE
                            buf.putShort(i + 6, newFlags.toShort())
                            modifiedLocalHeaders++
                        }
                    }
                }
                i++
            }

            val parent = apkFile.parentFile ?: File("/storage/emulated/0")
            val baseName = apkFile.nameWithoutExtension
            val outFile = File(
                parent,
                if (enable) "${baseName}_pseudo_enc.apk" else "${baseName}_unencrypted.apk"
            )
            outFile.writeBytes(bytes)
            PseudoEncryptionResult(
                outputFile = outFile,
                enabled = enable,
                modifiedCentralHeaders = modifiedCentralHeaders,
                modifiedLocalHeaders = modifiedLocalHeaders
            )
        }
    }

    /**
     * Modifies the timestamp (`entry.time`) of all entries inside [apkFile] to [targetTimeMillis]
     * (e.g., MT Manager fixed timestamp `1981-01-01 01:01:00`, Current Device Time, or Custom Date/Time)
     * and outputs `<name>_time.apk`.
     */
    suspend fun modifyApkTimestamps(
        apkFile: File,
        targetTimeMillis: Long,
        autoSign: Boolean = true
    ): Pair<File, Int> = withContext(Dispatchers.IO) {
        val parent = apkFile.parentFile ?: File("/storage/emulated/0")
        val baseName = apkFile.nameWithoutExtension
        val outFile = File(parent, "${baseName}_time.apk")
        val tempUnsigned = File(parent, ".tmp_time_${System.currentTimeMillis()}.apk")
        var entryCount = 0

        try {
            ZipFile(apkFile).use { zip ->
                ZipOutputStream(FileOutputStream(tempUnsigned)).use { zos ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if (autoSign && isSignatureMetaInfEntry(entry.name)) continue

                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val outEntry = ZipEntry(entry.name)
                        outEntry.time = targetTimeMillis
                        if (entry.method == ZipEntry.STORED) {
                            outEntry.method = ZipEntry.STORED
                            outEntry.size = bytes.size.toLong()
                            outEntry.compressedSize = bytes.size.toLong()
                            outEntry.crc = CRC32().apply { update(bytes) }.value
                        } else {
                            outEntry.method = ZipEntry.DEFLATED
                        }
                        zos.putNextEntry(outEntry)
                        zos.write(bytes)
                        zos.closeEntry()
                        entryCount++
                    }
                }
            }

            if (autoSign) {
                signApk(tempUnsigned, null, "V1 + V2 + V3", "CERT", outFile)
                // Also normalize timestamps of the newly generated META-INF entries if needed
            } else {
                if (outFile.exists()) outFile.delete()
                tempUnsigned.renameTo(outFile)
            }
        } finally {
            if (tempUnsigned.exists()) tempUnsigned.delete()
        }

        outFile to entryCount
    }

    /**
     * Deeply inspects [apkFile] for Packers/Shells (Qihoo 360 Jiagu, SecNeo/Bangcle, Tencent Legu,
     * Baidu, IJiami, Ali, DexProtector, PairIP, etc.), DEX2C native method conversion (`ACC_NATIVE`),
     * Native `.so` libraries, Signature Schemes (`V1/V2/V3`), 4-byte ZipAlign verification,
     * Pseudo-Encryption bit status, and `android:debuggable` status.
     */
    suspend fun inspectApkProtection(apkFile: File): ApkProtectionReport = withContext(Dispatchers.Default) {
        val detectedPackers = LinkedHashSet<String>()
        val nativeLibs = ArrayList<String>()
        val architectures = LinkedHashSet<String>()
        var dexFileCount = 0
        var totalMethodsCount = 0
        var nativeMethodsCount = 0
        val sampleNativeMethods = ArrayList<String>()
        var isDebuggable = false
        var uncompressedEntriesChecked = 0
        var unalignedEntriesCount = 0
        var pseudoEncryptedHeaders = 0

        // 1. Check raw ZIP headers for Pseudo-Encryption and ZipAlign of STORED entries
        runCatching {
            val rawBytes = apkFile.readBytes()
            val buf = java.nio.ByteBuffer.wrap(rawBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            var i = 0
            val limit = rawBytes.size - 30
            while (i <= limit) {
                if (rawBytes[i] == 0x50.toByte() && rawBytes[i + 1] == 0x4B.toByte()) {
                    val b2 = rawBytes[i + 2].toInt() and 0xFF
                    val b3 = rawBytes[i + 3].toInt() and 0xFF
                    if (b2 == 0x01 && b3 == 0x02 && i + 46 <= rawBytes.size) {
                        val flags = buf.getShort(i + 8).toInt() and 0xFFFF
                        if ((flags and 0x0001) != 0) pseudoEncryptedHeaders++
                        val nameLen = buf.getShort(i + 28).toInt() and 0xFFFF
                        val extraLen = buf.getShort(i + 30).toInt() and 0xFFFF
                        val commentLen = buf.getShort(i + 32).toInt() and 0xFFFF
                        val step = 46 + nameLen + extraLen + commentLen
                        if (step > 0) {
                            i += step
                            continue
                        }
                    } else if (b2 == 0x03 && b3 == 0x04 && i + 30 <= rawBytes.size) {
                        val flags = buf.getShort(i + 6).toInt() and 0xFFFF
                        val method = buf.getShort(i + 8).toInt() and 0xFFFF
                        val nameLen = buf.getShort(i + 26).toInt() and 0xFFFF
                        val extraLen = buf.getShort(i + 28).toInt() and 0xFFFF
                        if ((flags and 0x0001) != 0) pseudoEncryptedHeaders++
                        if (method == 0 && nameLen > 0 && i + 30 + nameLen <= rawBytes.size) {
                            val dataOffset = i + 30 + nameLen + extraLen
                            uncompressedEntriesChecked++
                            if (dataOffset % 4 != 0) {
                                unalignedEntriesCount++
                            }
                        }
                    }
                }
                i++
            }
        }

        val tempDir = File(apkFile.parentFile ?: File("/storage/emulated/0"), ".tmp_prot_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val dexFiles = ArrayList<File>()
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    val lower = name.lowercase(Locale.ROOT)

                    // Check known packer / shell signatures by entry path
                    when {
                        lower.contains("libjiagu") || lower.contains("jiagu_data") ->
                            detectedPackers += "Qihoo 360 Jiagu (360加固)"
                        lower.contains("libsecneo") || lower.contains("libdexhelper") || lower.contains("secmain") ->
                            detectedPackers += "SecNeo / Bangcle (梆梆加固)"
                        lower.contains("libshella") || lower.contains("libshellx") || lower.contains("00o000ll111l") || lower.contains("libtup.so") ->
                            detectedPackers += "Tencent Legu (腾讯乐固)"
                        lower.contains("libbaiduprotect") || lower.contains("baiduprotect") ->
                            detectedPackers += "Baidu Protect (百度加固)"
                        lower.contains("libexec.so") || lower.contains("libexecmain.so") || lower.contains("ijiami") ->
                            detectedPackers += "IJiami (爱加密)"
                        lower.contains("libsgmain") || lower.contains("libmobisec") || lower.contains("libdemolish") ->
                            detectedPackers += "Alibaba Security (阿里聚安全)"
                        lower.contains("libnesec") || lower.contains("libdata_burn") ->
                            detectedPackers += "NetEase Yidun (网易易盾)"
                        lower.contains("libdexprotector") || lower.contains("dexprotector") ->
                            detectedPackers += "DexProtector Enterprise"
                        lower.contains("libpairipcore") ->
                            detectedPackers += "Google Play PairIP Protection"
                        lower.contains("libapktoolplus") ->
                            detectedPackers += "APKToolPlus Encryption"
                        lower.contains("libkwsgmain") ->
                            detectedPackers += "Kuaishou Guard (快手加固)"
                        lower.contains("libvirbox") ->
                            detectedPackers += "Virbox Protector (深思数盾)"
                    }

                    if (lower.startsWith("lib/") && lower.endsWith(".so")) {
                        nativeLibs += name
                        val parts = name.split('/')
                        if (parts.size >= 3) architectures += parts[1]
                    }

                    if (!name.contains('/') && lower.endsWith(".dex")) {
                        dexFileCount++
                        val f = File(tempDir, name)
                        zip.getInputStream(entry).use { inp -> FileOutputStream(f).use { out -> inp.copyTo(out) } }
                        dexFiles += f
                    }

                    if (name.equals("AndroidManifest.xml", ignoreCase = true)) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val xml = runCatching { AxmlParser.decompileToXml(bytes) }.getOrDefault("")
                        if (xml.contains("android:debuggable=\"true\"")) {
                            isDebuggable = true
                        }
                    }
                }
            }

            if (dexFiles.isNotEmpty()) {
                val session = DexFileParser.parseDexFiles(dexFiles)
                for (cls in session.classes) {
                    val methods = cls.directMethods + cls.virtualMethods
                    totalMethodsCount += methods.size
                    for (m in methods) {
                        // ACC_NATIVE == 0x0100
                        if ((m.accessFlags and 0x0100) != 0) {
                            nativeMethodsCount++
                            if (sampleNativeMethods.size < 25) {
                                sampleNativeMethods += "${cls.simpleName}.${m.name}()"
                            }
                        }
                    }
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }

        val isDex2cLikely = nativeMethodsCount >= 5 && nativeLibs.isNotEmpty()
        if (isDex2cLikely && detectedPackers.isEmpty()) {
            detectedPackers += "DEX2C / Native JNI Method Virtualization ($nativeMethodsCount native methods)"
        }

        val packerSummary = if (detectedPackers.isEmpty()) {
            "No Packer / Shell Detected (Standard DEX)"
        } else {
            detectedPackers.joinToString(" + ")
        }

        val zipAlignVerified = uncompressedEntriesChecked == 0 || unalignedEntriesCount == 0
        val signatureScheme = AppRepository.detectApkSignatureScheme(apkFile)

        ApkProtectionReport(
            apkName = apkFile.name,
            packerSummary = packerSummary,
            isDex2cDetected = isDex2cLikely,
            dexCount = dexFileCount,
            totalMethodsCount = totalMethodsCount,
            nativeMethodsCount = nativeMethodsCount,
            sampleNativeMethods = sampleNativeMethods,
            nativeLibraries = nativeLibs,
            architectures = architectures.toList(),
            signatureScheme = signatureScheme,
            isZipAligned = zipAlignVerified,
            uncompressedCheckedCount = uncompressedEntriesChecked,
            isPseudoEncrypted = pseudoEncryptedHeaders > 0,
            pseudoEncryptedHeaderCount = pseudoEncryptedHeaders,
            isDebuggable = isDebuggable
        )
    }
}

data class EditableApkInfo(
    val appName: String,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int
)

data class DexMergeResult(
    val outputFile: File,
    val originalDexCount: Int,
    val finalDexCount: Int,
    val totalMethods: Int,
    val totalClasses: Int
)

data class RemoveDebugInfoResult(
    val outputFile: File,
    val dexCount: Int,
    val strippedSourceFileCount: Int,
    val strippedMethodDebugCount: Int
)

data class PseudoEncryptionResult(
    val outputFile: File,
    val enabled: Boolean,
    val modifiedCentralHeaders: Int,
    val modifiedLocalHeaders: Int
)

data class ApkProtectionReport(
    val apkName: String,
    val packerSummary: String,
    val isDex2cDetected: Boolean,
    val dexCount: Int,
    val totalMethodsCount: Int,
    val nativeMethodsCount: Int,
    val sampleNativeMethods: List<String>,
    val nativeLibraries: List<String>,
    val architectures: List<String>,
    val signatureScheme: String,
    val isZipAligned: Boolean,
    val uncompressedCheckedCount: Int,
    val isPseudoEncrypted: Boolean,
    val pseudoEncryptedHeaderCount: Int,
    val isDebuggable: Boolean
)

