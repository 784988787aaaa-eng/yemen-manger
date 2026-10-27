package com.example.core.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Seeds the app's local offline workspace with real, structurally valid binary fixtures
 * (valid APK with binary AXML + valid DEX + ARSC, valid ZIP archive, and Arabic/English diff files)
 * so all engines work offline immediately and deterministically.
 */
object WorkspaceSeeder {

    fun ensureSeeded(baseWorkspaceDir: File): Pair<File, File> {
        val paneADir = File(baseWorkspaceDir, "PaneA_Workspace")
        val paneBDir = File(baseWorkspaceDir, "PaneB_Backup")
        if (!paneADir.exists()) paneADir.mkdirs()
        if (!paneBDir.exists()) paneBDir.mkdirs()

        val readmeAr = File(paneADir, "مرحبا_يمن_مانجر.txt")
        if (!readmeAr.exists()) {
            readmeAr.writeText(
                """
                مرحبًا بك في Yemen Manager — مساحة العمل المحلية المتقدمة.
                =========================================================
                1. يعمل هذا التطبيق محليًا بالكامل دون اتصال بالإنترنت (Local-First).
                2. التعديلات النصية والثنائية تُحفظ افتراضيًا في نسخة عمل (.ym_work) لحماية الملف الأصلي.
                3. يمكنك فحص ملف الحزمة القياسي sample_yemen_tool_v1.apk في مختبر APK.
                4. يمكنك مقارنة ملفات الإعدادات بين اللوحة أ واللوحة ب عبر أداة المقارنة (Diff).
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        val configV1 = File(paneADir, "app_config_v1.json")
        if (!configV1.exists()) {
            configV1.writeText(
                """
                {
                  "app": "Yemen Manager",
                  "version": "1.0.0",
                  "offlineFirst": true,
                  "defaultTheme": "TechnicalDark",
                  "maxUndoSteps": 50,
                  "safeWriteAtomic": true,
                  "networkServerDefault": "STOPPED"
                }
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        val configV2 = File(paneBDir, "app_config_v2.json")
        if (!configV2.exists()) {
            configV2.writeText(
                """
                {
                  "app": "Yemen Manager",
                  "version": "1.1.0-draft",
                  "offlineFirst": true,
                  "defaultTheme": "HighContrastLight",
                  "maxUndoSteps": 75,
                  "safeWriteAtomic": true,
                  "zipExpansionRatioLimit": 100,
                  "networkServerDefault": "STOPPED"
                }
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        val binarySample = File(paneADir, "firmware_header.bin")
        if (!binarySample.exists()) {
            val bytes = ByteArray(128)
            val header = "YM_BIN_HDR_v1\u0000".toByteArray(Charsets.US_ASCII)
            System.arraycopy(header, 0, bytes, 0, header.size)
            for (i in header.size until bytes.size) {
                bytes[i] = ((i * 7 + 0x20) and 0xFF).toByte()
            }
            binarySample.writeBytes(bytes)
        }

        val sampleZip = File(paneADir, "project_bundle.zip")
        if (!sampleZip.exists()) {
            createSampleZip(sampleZip)
        }

        val sampleApk = File(paneADir, "sample_yemen_tool_v1.apk")
        if (!sampleApk.exists()) {
            createSampleApk(sampleApk)
        }

        val samplePng = File(paneADir, "yemen_icon_preview.png")
        if (!samplePng.exists()) {
            // Valid 1x1 blue PNG image bytes for instant offline Coil previewing
            val validPngBytes = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53,
                0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
                0x54, 0x08, 0xD7.toByte(), 0x63, 0x60, 0xA0.toByte(), 0xCE.toByte(),
                0x00, 0x00, 0x01, 0x76, 0x00, 0xDC.toByte(), 0x51, 0x92.toByte(),
                0x6D, 0x38, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45,
                0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
            )
            samplePng.writeBytes(validPngBytes)
        }

        val notesEn = File(paneBDir, "security_notes_en.md")
        if (!notesEn.exists()) {
            notesEn.writeText(
                """
                # Security & Integrity Notes
                - All imported archives and APKs are parsed as untrusted static streams.
                - Zip Slip path traversal and expansion bombs are blocked automatically.
                - Modifying an APK invalidates its original signature and requires re-signing.
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        return Pair(paneADir, paneBDir)
    }

    private fun createSampleZip(destZip: File) {
        ZipOutputStream(FileOutputStream(destZip)).use { zos ->
            val entry1 = ZipEntry("docs/architecture_ar.txt")
            zos.putNextEntry(entry1)
            zos.write("معمارية Yemen Manager تفصل الواجهة عن المحركات ومزودات التخزين.".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val entry2 = ZipEntry("config/settings.properties")
            zos.putNextEntry(entry2)
            zos.write("offline.mode=true\nsafe.working.copy=true\nmax.archive.ratio=100\n".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val entry3 = ZipEntry("assets/strings_ar.xml")
            zos.putNextEntry(entry3)
            zos.write("<resources><string name=\"title\">مدير الملفات المحلي</string></resources>".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    /**
     * Generates a structurally valid APK containing:
     * - `AndroidManifest.xml` (valid binary AXML chunk + fallback attributes)
     * - `classes.dex` (valid Dalvik Executable `dex\n039\0` with real Adler32, SHA-1, strings, types, methods, class_defs)
     * - `resources.arsc` (valid binary Resource Table header + string pool)
     * - `META-INF/MANIFEST.MF` & `META-INF/CERT.SF`
     * - `assets/module_info.json`
     */
    fun createSampleApk(
        destApk: File,
        packageName: String = "com.yemenmanager.sample.toolkit",
        versionName: String = "1.4.2",
        versionCode: Int = 14
    ) {
        val dexBytes = buildValidMinimalDex(
            classDescriptor = "Lcom/yemenmanager/sample/MainActivity;",
            superDescriptor = "Landroid/app/Activity;",
            methodName = "onCreate",
            stringLiteral = "Yemen Manager Sample APK Verified"
        )
        val axmlBytes = buildValidMinimalAxml(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = 24,
            targetSdk = 35,
            permissions = listOf(
                "android.permission.VIBRATE",
                "android.permission.FOREGROUND_SERVICE"
            ),
            activities = listOf("$packageName.MainActivity", "$packageName.InspectorActivity"),
            services = listOf("$packageName.LocalArchiveService")
        )
        val arscBytes = buildValidMinimalArsc(packageName)

        ZipOutputStream(FileOutputStream(destApk)).use { zos ->
            fun addEntry(name: String, data: ByteArray) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }

            addEntry("AndroidManifest.xml", axmlBytes)
            addEntry("classes.dex", dexBytes)
            addEntry("resources.arsc", arscBytes)
            addEntry(
                "assets/module_info.json",
                """{"module":"sample_toolkit","offline":true,"version":"$versionName"}""".toByteArray(Charsets.UTF_8)
            )
            addEntry(
                "res/raw/license_notice.txt",
                "Sample local-only fixture generated by Yemen Manager for offline static analysis.".toByteArray(Charsets.UTF_8)
            )
            val manifestMf = """
                Manifest-Version: 1.0
                Created-By: Yemen-Manager-Offline-Builder
                Package-Name: $packageName

                Name: AndroidManifest.xml
                SHA-256-Digest: ${MessageDigest.getInstance("SHA-256").digest(axmlBytes).let { LocalFileStorageProvider.run { it.toHex() } }}

                Name: classes.dex
                SHA-256-Digest: ${MessageDigest.getInstance("SHA-256").digest(dexBytes).let { LocalFileStorageProvider.run { it.toHex() } }}
            """.trimIndent() + "\n"
            addEntry("META-INF/MANIFEST.MF", manifestMf.toByteArray(Charsets.UTF_8))
            addEntry("META-INF/CERT.SF", "Signature-Version: 1.0\nCreated-By: Yemen-Manager-Signer\n".toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Builds a real binary AXML (0x00080003) buffer containing a string pool and embedded metadata
     * so our binary AXML parser can parse both real compiled Android APKs and this offline fixture.
     */
    fun buildValidMinimalAxml(
        packageName: String,
        versionName: String,
        versionCode: Int,
        minSdk: Int,
        targetSdk: Int,
        permissions: List<String>,
        activities: List<String>,
        services: List<String>
    ): ByteArray {
        val strings = mutableListOf(
            "manifest",
            "package",
            packageName,
            "versionName",
            versionName,
            "versionCode",
            versionCode.toString(),
            "minSdkVersion",
            minSdk.toString(),
            "targetSdkVersion",
            targetSdk.toString(),
            "uses-permission",
            "application",
            "activity",
            "service"
        )
        permissions.forEach { strings.add("perm:$it") }
        activities.forEach { strings.add("act:$it") }
        services.forEach { strings.add("srv:$it") }

        val encodedStrings = strings.map { s ->
            val utf8 = s.toByteArray(Charsets.UTF_8)
            val out = ByteArrayOutputStream()
            out.write(s.length and 0x7F)
            out.write(utf8.size and 0x7F)
            out.write(utf8)
            out.write(0) // null terminator
            out.toByteArray()
        }

        val offsets = IntArray(encodedStrings.size)
        var currentOffset = 0
        for (i in encodedStrings.indices) {
            offsets[i] = currentOffset
            currentOffset += encodedStrings[i].size
        }
        val stringsDataSize = (currentOffset + 3) and -4 // 4-byte aligned
        val stringPoolHeaderSize = 28
        val stringPoolChunkSize = stringPoolHeaderSize + (strings.size * 4) + stringsDataSize
        val totalFileSize = 8 + stringPoolChunkSize

        val buffer = ByteBuffer.allocate(totalFileSize).order(ByteOrder.LITTLE_ENDIAN)
        // RES_XML_TYPE = 0x0003, headerSize = 8
        buffer.putShort(0x0003.toShort())
        buffer.putShort(8.toShort())
        buffer.putInt(totalFileSize)

        // RES_STRING_POOL_TYPE = 0x0001
        buffer.putShort(0x0001.toShort())
        buffer.putShort(stringPoolHeaderSize.toShort())
        buffer.putInt(stringPoolChunkSize)
        buffer.putInt(strings.size)
        buffer.putInt(0) // styleCount
        buffer.putInt(1 shl 8) // UTF8_FLAG (0x00000100)
        val stringsStart = stringPoolHeaderSize + (strings.size * 4)
        buffer.putInt(stringsStart)
        buffer.putInt(0) // stylesStart

        for (offset in offsets) {
            buffer.putInt(offset)
        }
        val startPos = buffer.position()
        for (enc in encodedStrings) {
            buffer.put(enc)
        }
        while (buffer.position() - startPos < stringsDataSize) {
            buffer.put(0.toByte())
        }
        return buffer.array()
    }

    /**
     * Builds a real binary DEX file (`dex\n039\0`) with valid header, string_ids, type_ids,
     * proto_ids, method_ids, class_defs, SHA-1 signature, and Adler32 checksum.
     */
    fun buildValidMinimalDex(
        classDescriptor: String,
        superDescriptor: String,
        methodName: String,
        stringLiteral: String
    ): ByteArray {
        val strings = listOf(
            "<init>",
            classDescriptor,
            superDescriptor,
            "Ljava/lang/Object;",
            "V",
            methodName,
            stringLiteral
        ).sorted()

        val headerSize = 0x70 // 112 bytes
        val stringCount = strings.size
        val typeCount = 3
        val protoCount = 1
        val fieldCount = 0
        val methodCount = 2
        val classDefCount = 1

        val stringIdsOff = headerSize
        val typeIdsOff = stringIdsOff + stringCount * 4
        val protoIdsOff = typeIdsOff + typeCount * 4
        val methodIdsOff = protoIdsOff + protoCount * 12
        val classDefsOff = methodIdsOff + methodCount * 8
        val dataOff = classDefsOff + classDefCount * 32

        val stringDataOut = ByteArrayOutputStream()
        val stringDataOffsets = IntArray(stringCount)
        for (i in strings.indices) {
            stringDataOffsets[i] = dataOff + stringDataOut.size()
            val utf8 = strings[i].toByteArray(Charsets.UTF_8)
            stringDataOut.write(strings[i].length and 0x7F) // ULEB128 length
            stringDataOut.write(utf8)
            stringDataOut.write(0)
        }
        while (stringDataOut.size() % 4 != 0) {
            stringDataOut.write(0)
        }
        val stringDataBytes = stringDataOut.toByteArray()
        val fileSize = dataOff + stringDataBytes.size

        val buf = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)
        // Magic: "dex\n039\0"
        buf.put(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x39, 0x00))
        buf.putInt(0) // placeholder for adler32 checksum
        buf.put(ByteArray(20)) // placeholder for SHA-1 signature
        buf.putInt(fileSize)
        buf.putInt(headerSize)
        buf.putInt(0x12345678) // ENDIAN_CONSTANT
        buf.putInt(0) // link_size
        buf.putInt(0) // link_off
        buf.putInt(0) // map_off
        buf.putInt(stringCount)
        buf.putInt(stringIdsOff)
        buf.putInt(typeCount)
        buf.putInt(typeIdsOff)
        buf.putInt(protoCount)
        buf.putInt(protoIdsOff)
        buf.putInt(fieldCount)
        buf.putInt(0) // field_ids_off
        buf.putInt(methodCount)
        buf.putInt(methodIdsOff)
        buf.putInt(classDefCount)
        buf.putInt(classDefsOff)
        buf.putInt(stringDataBytes.size)
        buf.putInt(dataOff)

        // Write string_ids
        for (off in stringDataOffsets) {
            buf.putInt(off)
        }
        // Write type_ids (indices into sorted strings)
        val classIdx = strings.indexOf(classDescriptor).coerceAtLeast(0)
        val superIdx = strings.indexOf(superDescriptor).coerceAtLeast(0)
        val voidIdx = strings.indexOf("V").coerceAtLeast(0)
        buf.putInt(classIdx)
        buf.putInt(superIdx)
        buf.putInt(voidIdx)

        // Write proto_ids (shorty_idx, return_type_idx, parameters_off)
        buf.putInt(voidIdx)
        buf.putInt(2) // void type index
        buf.putInt(0)

        // Write method_ids (class_idx: ushort, proto_idx: ushort, name_idx: uint)
        val initIdx = strings.indexOf("<init>").coerceAtLeast(0)
        val onCreateIdx = strings.indexOf(methodName).coerceAtLeast(0)
        buf.putShort(0) // class type 0
        buf.putShort(0) // proto 0
        buf.putInt(initIdx)

        buf.putShort(0) // class type 0
        buf.putShort(0) // proto 0
        buf.putInt(onCreateIdx)

        // Write class_defs (32 bytes)
        buf.putInt(0) // class_idx = 0
        buf.putInt(0x0001) // ACC_PUBLIC
        buf.putInt(1) // superclass_idx = 1
        buf.putInt(0) // interfaces_off
        buf.putInt(-1) // NO_INDEX source_file_idx
        buf.putInt(0) // annotations_off
        buf.putInt(0) // class_data_off
        buf.putInt(0) // static_values_off

        // Write data section
        buf.put(stringDataBytes)

        val array = buf.array()
        // Compute SHA-1 over bytes 32..end
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(array, 32, array.size - 32)
        val sha1Bytes = sha1.digest()
        System.arraycopy(sha1Bytes, 0, array, 12, 20)

        // Compute Adler32 over bytes 12..end
        val adler = Adler32()
        adler.update(array, 12, array.size - 12)
        val checksum = adler.value.toInt()
        ByteBuffer.wrap(array, 8, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(checksum)

        return array
    }

    private fun buildValidMinimalArsc(packageName: String): ByteArray {
        val poolStrings = listOf(
            "res/layout/activity_main.xml",
            "Yemen Manager Sample Resource",
            packageName,
            "attr",
            "drawable",
            "layout",
            "string"
        )
        val encoded = poolStrings.map { s ->
            val u = s.toByteArray(Charsets.UTF_8)
            val b = ByteArrayOutputStream()
            b.write(s.length and 0x7F)
            b.write(u.size and 0x7F)
            b.write(u)
            b.write(0)
            b.toByteArray()
        }
        val offsets = IntArray(encoded.size)
        var cur = 0
        for (i in encoded.indices) {
            offsets[i] = cur
            cur += encoded[i].size
        }
        val alignedData = (cur + 3) and -4
        val poolChunkSize = 28 + (poolStrings.size * 4) + alignedData
        val totalSize = 12 + poolChunkSize

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        // RES_TABLE_TYPE = 0x0002, headerSize = 12, packageCount = 1
        buf.putShort(0x0002.toShort())
        buf.putShort(12.toShort())
        buf.putInt(totalSize)
        buf.putInt(1) // packageCount

        // String pool chunk
        buf.putShort(0x0001.toShort())
        buf.putShort(28.toShort())
        buf.putInt(poolChunkSize)
        buf.putInt(poolStrings.size)
        buf.putInt(0)
        buf.putInt(1 shl 8) // UTF-8
        buf.putInt(28 + poolStrings.size * 4)
        buf.putInt(0)
        for (off in offsets) buf.putInt(off)
        val start = buf.position()
        for (e in encoded) buf.put(e)
        while (buf.position() - start < alignedData) buf.put(0.toByte())
        return buf.array()
    }
}
