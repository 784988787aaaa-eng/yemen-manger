package com.example.mtcore.data

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

enum class DispatchedFileType {
    BINARY_XML,
    PLAIN_XML,
    DEX,
    ARSC,
    APK,
    SPLIT_APK_BUNDLE,
    ARCHIVE,
    KEYSTORE_OR_CERT,
    IMAGE,
    FONT,
    BINARY_HEX,
    CODE_TEXT,
    UNKNOWN
}

object FileDispatcher {
    private val KEYSTORE_CERT_EXTENSIONS = setOf(
        "jks", "bks", "keystore", "pk8", "pem", "rsa", "sf", "dsa", "ec", "cer", "crt", "der"
    )

    private val IMAGE_EXTENSIONS = setOf(
        "png", "webp", "jpg", "jpeg", "gif", "bmp", "ico", "svg"
    )

    private val FONT_EXTENSIONS = setOf(
        "ttf", "otf"
    )

    private val BINARY_HEX_EXTENSIONS = setOf(
        "so", "bin", "dat", "class", "odex", "oat"
    )

    private val SPLIT_APK_BUNDLE_EXTENSIONS = setOf(
        "apks", "xapk", "apkm"
    )

    private val ARCHIVE_EXTENSIONS = setOf(
        "zip", "jar", "aab"
    )

    private val CODE_TEXT_EXTENSIONS = setOf(
        "kt", "kts", "java", "smali", "json", "py", "js", "ts", "html", "css",
        "c", "cpp", "h", "hpp", "sh", "bat", "gradle", "properties", "yml", "yaml",
        "toml", "md", "txt", "sql", "pro", "cfg", "ini", "conf", "env", "gitignore",
        "csv", "log"
    )

    /**
     * Inspects the first 8..256 bytes (Magic Bytes) and file extension of [file]
     * and returns its exact [DispatchedFileType] so the UI routes it without
     * showing irrelevant dialogs.
     */
    fun inspect(file: File): DispatchedFileType {
        if (!file.exists() || !file.isFile) return DispatchedFileType.UNKNOWN

        val ext = file.extension.lowercase(Locale.ROOT)
        val header = readHeaderBytes(file, 256)

        // 1. APK package
        if (ext == "apk") {
            return DispatchedFileType.APK
        }

        // 1b. Split APK Bundle (.apks, .xapk, .apkm)
        if (ext in SPLIT_APK_BUNDLE_EXTENSIONS) {
            return DispatchedFileType.SPLIT_APK_BUNDLE
        }

        // 2. Archive packages (.zip, .jar, .aab)
        if (ext in ARCHIVE_EXTENSIONS) {
            return DispatchedFileType.ARCHIVE
        }

        // 3. Keystore / Certificate / Signature files (.jks, .bks, .keystore, .pk8, .pem, .rsa, .sf)
        if (ext in KEYSTORE_CERT_EXTENSIONS || isJksMagic(header)) {
            return DispatchedFileType.KEYSTORE_OR_CERT
        }

        // 4. Dalvik Executable (.dex or magic "dex\n035\0".."041\0")
        if (isDexMagic(header) || ext == "dex") {
            return DispatchedFileType.DEX
        }

        // 5. Android Resource Table (.arsc or RES_TABLE_TYPE 0x0002)
        if (isArscMagic(header) || ext == "arsc") {
            return DispatchedFileType.ARSC
        }

        // 6. Binary XML vs Plaintext XML
        if (isBinaryXmlMagic(header)) {
            return DispatchedFileType.BINARY_XML
        }
        if (ext == "xml") {
            return if (containsBinaryNullBytes(header)) {
                DispatchedFileType.BINARY_XML
            } else {
                DispatchedFileType.PLAIN_XML
            }
        }
        if (ext == "svg") {
            return DispatchedFileType.IMAGE
        }
        if (startsWithPlainXmlTag(header)) {
            return DispatchedFileType.PLAIN_XML
        }

        // 7. Images & Fonts
        if (ext in IMAGE_EXTENSIONS || isImageMagic(header)) {
            return DispatchedFileType.IMAGE
        }
        if (ext in FONT_EXTENSIONS || isFontMagic(header)) {
            return DispatchedFileType.FONT
        }

        // 8. Native / Compiled Binary files (.so, .bin, .dat, .class, ELF, CAFEBABE)
        if (ext in BINARY_HEX_EXTENSIONS || isElfOrClassMagic(header)) {
            return DispatchedFileType.BINARY_HEX
        }

        // 9. Code & Text files
        if (ext in CODE_TEXT_EXTENSIONS || file.name.equals("gradlew", true) || file.name.equals("Dockerfile", true)) {
            return DispatchedFileType.CODE_TEXT
        }

        // Heuristic text check for extensionless or custom text files
        if (header.isNotEmpty() && !containsBinaryNullBytes(header)) {
            return DispatchedFileType.CODE_TEXT
        }

        return DispatchedFileType.UNKNOWN
    }

    private fun readHeaderBytes(file: File, maxLen: Int): ByteArray {
        return runCatching {
            val len = minOf(file.length().toInt().coerceAtLeast(0), maxLen)
            if (len == 0) return ByteArray(0)
            val buf = ByteArray(len)
            FileInputStream(file).use { fis ->
                var offset = 0
                while (offset < len) {
                    val read = fis.read(buf, offset, len - offset)
                    if (read <= 0) break
                    offset += read
                }
                if (offset < len) buf.copyOf(offset) else buf
            }
        }.getOrDefault(ByteArray(0))
    }

    /**
     * Checks Android Binary XML magic `0x00080003` (`RES_XML_TYPE = 0x0003`, `headerSize = 0x0008`).
     */
    fun isBinaryXmlMagic(header: ByteArray): Boolean {
        if (header.size < 8) return false
        val magic = ByteBuffer.wrap(header, 0, 8).order(ByteOrder.LITTLE_ENDIAN).int
        if (magic == 0x00080003) return true
        val type = (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8)
        val headerSize = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
        return type == 0x0003 && headerSize == 0x0008
    }

    /**
     * Checks DEX magic `dex\n035\0` .. `dex\n041\0`.
     */
    fun isDexMagic(header: ByteArray): Boolean {
        if (header.size < 8) return false
        return header[0] == 0x64.toByte() && // 'd'
            header[1] == 0x65.toByte() &&    // 'e'
            header[2] == 0x78.toByte() &&    // 'x'
            header[3] == 0x0A.toByte() &&    // '\n'
            header[7] == 0x00.toByte()
    }

    /**
     * Checks Android Resource Table magic `RES_TABLE_TYPE = 0x0002`, `headerSize = 0x000C`.
     */
    fun isArscMagic(header: ByteArray): Boolean {
        if (header.size < 8) return false
        val type = (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8)
        val headerSize = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
        return type == 0x0002 && headerSize == 0x000C
    }

    private fun isJksMagic(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return (header[0].toInt() and 0xFF) == 0xFE &&
            (header[1].toInt() and 0xFF) == 0xED &&
            (header[2].toInt() and 0xFF) == 0xFE &&
            (header[3].toInt() and 0xFF) == 0xED
    }

    private fun isImageMagic(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val b2 = header[2].toInt() and 0xFF
        val b3 = header[3].toInt() and 0xFF
        // PNG: 89 50 4E 47
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return true
        // JPEG: FF D8 FF
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return true
        // GIF: 'G' 'I' 'F' '8'
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) return true
        return false
    }

    private fun isFontMagic(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val b2 = header[2].toInt() and 0xFF
        val b3 = header[3].toInt() and 0xFF
        // TrueType 00 01 00 00 or OpenType 'O' 'T' 'T' 'O'
        if (b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) return true
        if (b0 == 0x4F && b1 == 0x54 && b2 == 0x54 && b3 == 0x4F) return true
        return false
    }

    private fun isElfOrClassMagic(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val b2 = header[2].toInt() and 0xFF
        val b3 = header[3].toInt() and 0xFF
        // ELF: 7F 'E' 'L' 'F'
        if (b0 == 0x7F && b1 == 0x45 && b2 == 0x4C && b3 == 0x46) return true
        // Java Class: CA FE BA BE
        if (b0 == 0xCA && b1 == 0xFE && b2 == 0xBA && b3 == 0xBE) return true
        return false
    }

    private fun startsWithPlainXmlTag(header: ByteArray): Boolean {
        if (header.isEmpty() || containsBinaryNullBytes(header)) return false
        val text = String(header, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return text.startsWith("<?xml", ignoreCase = true)
    }

    private fun containsBinaryNullBytes(header: ByteArray): Boolean {
        return header.any { it == 0.toByte() }
    }
}
