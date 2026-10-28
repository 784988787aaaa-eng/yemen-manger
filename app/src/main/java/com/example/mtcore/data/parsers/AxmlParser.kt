package com.example.mtcore.data.parsers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale

object AxmlParser {
    const val RES_STRING_POOL_TYPE = 0x0001
    const val RES_TABLE_TYPE = 0x0002
    const val RES_XML_TYPE = 0x0003
    const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    const val RES_XML_START_ELEMENT_TYPE = 0x0102
    const val RES_XML_END_ELEMENT_TYPE = 0x0103
    const val RES_XML_CDATA_TYPE = 0x0104
    const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    const val RES_TABLE_PACKAGE_TYPE = 0x0200

    const val UTF8_FLAG = 1 shl 8

    // Comprehensive android.R.attr.* map for AndroidManifest.xml and res/layout/*.xml
    private val ANDROID_ATTR_NAMES = mapOf(
        0x01010000 to "theme",
        0x01010001 to "label",
        0x01010002 to "icon",
        0x01010003 to "name",
        0x01010006 to "permission",
        0x0101000a to "readPermission",
        0x0101000b to "writePermission",
        0x0101000e to "enabled",
        0x0101000f to "debuggable",
        0x01010010 to "exported",
        0x01010011 to "process",
        0x01010012 to "taskAffinity",
        0x01010013 to "multiprocess",
        0x01010014 to "finishOnTaskLaunch",
        0x01010015 to "clearTaskOnLaunch",
        0x01010016 to "stateNotNeeded",
        0x01010017 to "excludeFromRecents",
        0x01010018 to "authorities",
        0x01010019 to "syncable",
        0x0101001a to "initOrder",
        0x0101001b to "grantUriPermissions",
        0x0101001c to "priority",
        0x0101001d to "launchMode",
        0x0101001e to "screenOrientation",
        0x0101001f to "configChanges",
        0x01010020 to "description",
        0x01010024 to "value",
        0x01010025 to "resource",
        0x01010026 to "mimeType",
        0x01010027 to "scheme",
        0x01010028 to "host",
        0x01010029 to "port",
        0x0101002a to "path",
        0x0101002b to "pathPrefix",
        0x0101002c to "pathPattern",
        0x01010095 to "textSize",
        0x01010097 to "textStyle",
        0x01010098 to "textColor",
        0x010100af to "gravity",
        0x010100c4 to "orientation",
        0x010100d0 to "id",
        0x010100d4 to "background",
        0x010100d5 to "padding",
        0x010100d6 to "paddingLeft",
        0x010100d7 to "paddingTop",
        0x010100d8 to "paddingRight",
        0x010100d9 to "paddingBottom",
        0x010100da to "focusable",
        0x010100dc to "visibility",
        0x010100e5 to "clickable",
        0x010100f4 to "layout_width",
        0x010100f5 to "layout_height",
        0x010100f6 to "layout_margin",
        0x010100f7 to "layout_marginLeft",
        0x010100f8 to "layout_marginTop",
        0x010100f9 to "layout_marginRight",
        0x010100fa to "layout_marginBottom",
        0x01010119 to "src",
        0x0101014f to "text",
        0x01010150 to "hint",
        0x01010181 to "layout_weight",
        0x0101020c to "minSdkVersion",
        0x0101021b to "versionCode",
        0x0101021c to "versionName",
        0x0101022b to "windowSoftInputMode",
        0x01010270 to "targetSdkVersion",
        0x01010272 to "allowBackup",
        0x01010280 to "hardwareAccelerated",
        0x01010281 to "installLocation",
        0x01010392 to "largeHeap",
        0x010103a6 to "supportsRtl",
        0x010103b3 to "paddingStart",
        0x010103b4 to "paddingEnd",
        0x0101048c to "extractNativeLibs",
        0x010104ea to "roundIcon",
        0x010104f0 to "appComponentFactory",
        0x0101052c to "networkSecurityConfig",
        0x01010540 to "requestLegacyExternalStorage",
        0x01010572 to "compileSdkVersion",
        0x01010573 to "compileSdkVersionCodename"
    )

    fun isBinaryXml(file: File): Boolean {
        if (!file.isFile || file.length() < 8L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(8)
                val read = raf.read(header)
                if (read < 4) return@use false
                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val magicInt = buf.getInt(0)
                val typeShort = buf.getShort(0).toInt() and 0xFFFF
                if (magicInt == 0x00080003 || typeShort == RES_XML_TYPE) return@use true
                // Also check if .xml file contains binary NUL bytes in first 256 bytes
                if (file.extension.equals("xml", true)) {
                    val sample = ByteArray(256.coerceAtMost(file.length().toInt()))
                    raf.seek(0)
                    val n = raf.read(sample)
                    (0 until n).any { sample[it] == 0.toByte() }
                } else {
                    false
                }
            }
        }.getOrDefault(false)
    }

    suspend fun decompileFile(file: File): String = withContext(Dispatchers.Default) {
        val bytes = file.readBytes()
        decompileToXml(bytes)
    }

    fun decompileToXml(bytes: ByteArray): String {
        if (bytes.size < 8) return ""
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.getShort(0).toInt() and 0xFFFF
        if (fileType != RES_XML_TYPE) {
            // Already plain text XML
            return String(bytes, StandardCharsets.UTF_8)
        }

        val headerSize = (buf.getShort(2).toInt() and 0xFFFF).coerceAtLeast(8)
        val totalSize = buf.getInt(4).coerceIn(headerSize, bytes.size)

        var strings = emptyList<String>()
        var resourceIds = IntArray(0)
        val activeNamespaces = LinkedHashMap<String, String>() // uri -> prefix
        val pendingNamespaces = mutableListOf<Pair<String, String>>() // prefix, uri

        val sb = StringBuilder(4096)
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

        var offset = headerSize
        var indent = 0

        try {
            while (offset + 8 <= totalSize) {
                val chunkType = buf.getShort(offset).toInt() and 0xFFFF
                val chunkHeaderSize = buf.getShort(offset + 2).toInt() and 0xFFFF
                val chunkSize = buf.getInt(offset + 4)
                if (chunkSize <= 0 || offset + chunkSize > bytes.size) break

                when (chunkType) {
                    RES_STRING_POOL_TYPE -> {
                        strings = parseStringPoolChunk(buf, offset)
                    }
                    RES_XML_RESOURCE_MAP_TYPE -> {
                        val count = ((chunkSize - chunkHeaderSize) / 4).coerceIn(0, 65536)
                        resourceIds = IntArray(count) { i ->
                            buf.getInt(offset + chunkHeaderSize + i * 4)
                        }
                    }
                    RES_XML_START_NAMESPACE_TYPE -> {
                        if (offset + 24 <= bytes.size) {
                            val prefixIdx = buf.getInt(offset + 16)
                            val uriIdx = buf.getInt(offset + 20)
                            val prefix = strings.getOrNull(prefixIdx).orEmpty()
                            val uri = strings.getOrNull(uriIdx).orEmpty()
                            if (prefix.isNotEmpty() && uri.isNotEmpty()) {
                                activeNamespaces[uri] = prefix
                                pendingNamespaces += prefix to uri
                            }
                        }
                    }
                    RES_XML_END_NAMESPACE_TYPE -> {
                        if (offset + 24 <= bytes.size) {
                            val uriIdx = buf.getInt(offset + 20)
                            val uri = strings.getOrNull(uriIdx).orEmpty()
                            activeNamespaces.remove(uri)
                        }
                    }
                    RES_XML_START_ELEMENT_TYPE -> {
                        if (offset + 36 <= bytes.size) {
                            val nsIdx = buf.getInt(offset + 16)
                            val nameIdx = buf.getInt(offset + 20)
                            val attrStart = buf.getShort(offset + 24).toInt() and 0xFFFF
                            val attrSize = (buf.getShort(offset + 26).toInt() and 0xFFFF).coerceAtLeast(20)
                            val attrCount = buf.getShort(offset + 28).toInt() and 0xFFFF

                            val elemNs = strings.getOrNull(nsIdx)?.let { activeNamespaces[it] }
                            val rawName = strings.getOrNull(nameIdx).orEmpty().ifEmpty { "node" }
                            val elemName = if (!elemNs.isNullOrEmpty()) "$elemNs:$rawName" else rawName

                            val indentStr = "    ".repeat(indent)
                            sb.append(indentStr).append("<").append(elemName)

                            val attrIndent = if (attrCount + pendingNamespaces.size > 1) "\n$indentStr    " else " "

                            for ((prefix, uri) in pendingNamespaces) {
                                sb.append(attrIndent)
                                    .append("xmlns:")
                                    .append(prefix)
                                    .append("=\"")
                                    .append(escapeXml(uri))
                                    .append("\"")
                            }
                            pendingNamespaces.clear()

                            val attrBase = offset + 16 + attrStart
                            for (i in 0 until attrCount) {
                                val aOff = attrBase + i * attrSize
                                if (aOff + 20 > offset + chunkSize) break
                                val aNsIdx = buf.getInt(aOff)
                                val aNameIdx = buf.getInt(aOff + 4)
                                val aRawValIdx = buf.getInt(aOff + 8)
                                val aValueType = buf.get(aOff + 15).toInt() and 0xFF
                                val aData = buf.getInt(aOff + 16)

                                val attrPrefix = strings.getOrNull(aNsIdx)?.let { activeNamespaces[it] }
                                    ?: if (aNameIdx in resourceIds.indices && (resourceIds[aNameIdx] ushr 24) == 0x01) "android" else null
                                var attrName = strings.getOrNull(aNameIdx).orEmpty()
                                if (attrName.isEmpty() && aNameIdx in resourceIds.indices) {
                                    val resId = resourceIds[aNameIdx]
                                    attrName = ANDROID_ATTR_NAMES[resId] ?: String.format(Locale.US, "attr_0x%08x", resId)
                                }
                                if (attrName.isEmpty()) attrName = "attr_$i"

                                val fullAttrName = if (!attrPrefix.isNullOrEmpty() && !attrName.contains(':')) {
                                    "$attrPrefix:$attrName"
                                } else {
                                    attrName
                                }

                                val valueStr = formatAttributeValue(aValueType, aData, aRawValIdx, strings)
                                sb.append(attrIndent)
                                    .append(fullAttrName)
                                    .append("=\"")
                                    .append(escapeXml(valueStr))
                                    .append("\"")
                            }

                            sb.append(">\n")
                            indent++
                        }
                    }
                    RES_XML_END_ELEMENT_TYPE -> {
                        if (offset + 24 <= bytes.size) {
                            indent = (indent - 1).coerceAtLeast(0)
                            val nsIdx = buf.getInt(offset + 16)
                            val nameIdx = buf.getInt(offset + 20)
                            val elemNs = strings.getOrNull(nsIdx)?.let { activeNamespaces[it] }
                            val rawName = strings.getOrNull(nameIdx).orEmpty().ifEmpty { "node" }
                            val elemName = if (!elemNs.isNullOrEmpty()) "$elemNs:$rawName" else rawName
                            sb.append("    ".repeat(indent)).append("</").append(elemName).append(">\n")
                        }
                    }
                    RES_XML_CDATA_TYPE -> {
                        if (offset + 20 <= bytes.size) {
                            val dataIdx = buf.getInt(offset + 16)
                            val text = strings.getOrNull(dataIdx).orEmpty()
                            if (text.isNotBlank()) {
                                sb.append("    ".repeat(indent)).append(escapeXml(text)).append("\n")
                            }
                        }
                    }
                }
                offset += chunkSize
            }
        } catch (_: Exception) {
            // Return decoded XML up to this point
        }

        return sb.toString()
    }

    fun extractStringPool(bytes: ByteArray): List<String> {
        if (bytes.size < 8) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rootType = buf.getShort(0).toInt() and 0xFFFF
        val rootHeaderSize = (buf.getShort(2).toInt() and 0xFFFF).coerceAtLeast(8)
        val totalSize = buf.getInt(4).coerceIn(rootHeaderSize, bytes.size)

        if (rootType == RES_STRING_POOL_TYPE) {
            return parseStringPoolChunk(buf, 0)
        }

        var offset = rootHeaderSize
        while (offset + 8 <= totalSize) {
            val chunkType = buf.getShort(offset).toInt() and 0xFFFF
            val chunkSize = buf.getInt(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) break
            if (chunkType == RES_STRING_POOL_TYPE) {
                return parseStringPoolChunk(buf, offset)
            }
            offset += chunkSize
        }

        return extractPrintableStrings(bytes)
    }

    /**
     * Rebuilds the first RES_STRING_POOL_TYPE (0x0001) chunk inside a Binary XML (AXML) or ARSC file
     * with the modified list of strings, preserving all subsequent chunks (Resource Map, XML nodes, etc.)
     * and updating the outer container size so the modified binary file can be saved directly into the APK!
     */
    fun rebuildBinaryWithUpdatedStringPool(bytes: ByteArray, updatedStrings: List<String>): ByteArray {
        if (bytes.size < 8) return bytes
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rootType = buf.getShort(0).toInt() and 0xFFFF
        val rootHeaderSize = (buf.getShort(2).toInt() and 0xFFFF).coerceAtLeast(8)
        val totalSize = buf.getInt(4).coerceIn(rootHeaderSize, bytes.size)

        var offset = rootHeaderSize
        while (offset + 28 <= totalSize) {
            val chunkType = buf.getShort(offset).toInt() and 0xFFFF
            val chunkSize = buf.getInt(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) break

            if (chunkType == RES_STRING_POOL_TYPE) {
                val flags = buf.getInt(offset + 16)
                val isUtf8 = (flags and UTF8_FLAG) != 0
                val newPoolChunk = buildStringPoolChunk(updatedStrings, isUtf8)
                val out = ByteArrayOutputStream(bytes.size + 512)
                // Write file header + bytes before string pool chunk
                out.write(bytes, 0, offset)
                // Write new string pool chunk
                out.write(newPoolChunk)
                // Write remaining chunks after the old string pool chunk
                val afterPoolOffset = offset + chunkSize
                if (afterPoolOffset < bytes.size) {
                    out.write(bytes, afterPoolOffset, bytes.size - afterPoolOffset)
                }
                val result = out.toByteArray()
                // Update total file size at offset 4 (Little-Endian Int)
                ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(4, result.size)
                return result
            }
            offset += chunkSize
        }
        return bytes
    }

    private fun buildStringPoolChunk(strings: List<String>, isUtf8: Boolean): ByteArray {
        val headerSize = 28
        val stringCount = strings.size
        val offsetsBytes = ByteArray(stringCount * 4)
        val offsetsBuf = ByteBuffer.wrap(offsetsBytes).order(ByteOrder.LITTLE_ENDIAN)
        val stringDataOut = ByteArrayOutputStream(strings.sumOf { it.length * 2 + 4 })

        for (i in 0 until stringCount) {
            offsetsBuf.putInt(i * 4, stringDataOut.size())
            val s = strings[i]
            if (isUtf8) {
                val utf16Len = s.length
                val utf8Bytes = s.toByteArray(StandardCharsets.UTF_8)
                val utf8Len = utf8Bytes.size
                writeLength8(stringDataOut, utf16Len)
                writeLength8(stringDataOut, utf8Len)
                stringDataOut.write(utf8Bytes)
                stringDataOut.write(0)
            } else {
                val utf16Len = s.length
                writeLength16(stringDataOut, utf16Len)
                val utf16Bytes = s.toByteArray(StandardCharsets.UTF_16LE)
                stringDataOut.write(utf16Bytes)
                stringDataOut.write(0)
                stringDataOut.write(0)
            }
        }

        // 4-byte align string data
        while (stringDataOut.size() % 4 != 0) {
            stringDataOut.write(0)
        }

        val stringsData = stringDataOut.toByteArray()
        val stringsStart = headerSize + offsetsBytes.size
        val totalChunkSize = stringsStart + stringsData.size

        val chunkBytes = ByteArray(totalChunkSize)
        val chunkBuf = ByteBuffer.wrap(chunkBytes).order(ByteOrder.LITTLE_ENDIAN)
        chunkBuf.putShort(0, RES_STRING_POOL_TYPE.toShort())
        chunkBuf.putShort(2, headerSize.toShort())
        chunkBuf.putInt(4, totalChunkSize)
        chunkBuf.putInt(8, stringCount)
        chunkBuf.putInt(12, 0) // styleCount = 0
        chunkBuf.putInt(16, if (isUtf8) UTF8_FLAG else 0)
        chunkBuf.putInt(20, stringsStart)
        chunkBuf.putInt(24, 0) // stylesStart = 0

        System.arraycopy(offsetsBytes, 0, chunkBytes, headerSize, offsetsBytes.size)
        System.arraycopy(stringsData, 0, chunkBytes, stringsStart, stringsData.size)
        return chunkBytes
    }

    private fun writeLength8(out: ByteArrayOutputStream, len: Int) {
        if (len >= 0x80) {
            out.write(((len shr 8) and 0x7F) or 0x80)
            out.write(len and 0xFF)
        } else {
            out.write(len and 0x7F)
        }
    }

    private fun writeLength16(out: ByteArrayOutputStream, len: Int) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        if (len >= 0x8000) {
            val hi = ((len shr 16) and 0x7FFF) or 0x8000
            val lo = len and 0xFFFF
            b.putShort(0, hi.toShort())
            b.putShort(2, lo.toShort())
            out.write(b.array(), 0, 4)
        } else {
            b.putShort(0, (len and 0x7FFF).toShort())
            out.write(b.array(), 0, 2)
        }
    }

    internal fun parseStringPoolChunk(buf: ByteBuffer, chunkOffset: Int): List<String> {
        if (chunkOffset + 28 > buf.limit()) return emptyList()
        val headerSize = (buf.getShort(chunkOffset + 2).toInt() and 0xFFFF).coerceAtLeast(28)
        val chunkSize = buf.getInt(chunkOffset + 4)
        val stringCount = buf.getInt(chunkOffset + 8).coerceIn(0, 300_000)
        val flags = buf.getInt(chunkOffset + 16)
        val stringsStart = buf.getInt(chunkOffset + 20)
        val isUtf8 = (flags and UTF8_FLAG) != 0

        val offsetsBase = chunkOffset + headerSize
        val stringsBase = chunkOffset + stringsStart
        val chunkEnd = (chunkOffset + chunkSize).coerceAtMost(buf.limit())

        val result = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val idxPos = offsetsBase + i * 4
            if (idxPos + 4 > chunkEnd) break
            val strOff = buf.getInt(idxPos)
            val pos = stringsBase + strOff
            if (pos < 0 || pos >= chunkEnd) {
                result.add("")
                continue
            }
            val decoded = runCatching {
                if (isUtf8) readUtf8PoolString(buf, pos, chunkEnd) else readUtf16PoolString(buf, pos, chunkEnd)
            }.getOrDefault("")
            result.add(decoded)
        }
        return result
    }

    private fun readUtf8PoolString(buf: ByteBuffer, startPos: Int, limit: Int): String {
        var pos = startPos
        val b0 = buf.get(pos).toInt() and 0xFF
        pos += if ((b0 and 0x80) != 0) 2 else 1
        if (pos >= limit) return ""

        val l0 = buf.get(pos).toInt() and 0xFF
        val byteLen: Int
        if ((l0 and 0x80) != 0) {
            if (pos + 1 >= limit) return ""
            val l1 = buf.get(pos + 1).toInt() and 0xFF
            byteLen = ((l0 and 0x7F) shl 8) or l1
            pos += 2
        } else {
            byteLen = l0
            pos += 1
        }
        val safeLen = byteLen.coerceIn(0, (limit - pos).coerceAtLeast(0))
        val bytes = ByteArray(safeLen)
        val dup = buf.duplicate()
        dup.position(pos)
        dup.get(bytes, 0, safeLen)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun readUtf16PoolString(buf: ByteBuffer, startPos: Int, limit: Int): String {
        var pos = startPos
        if (pos + 2 > limit) return ""
        val u0 = buf.getShort(pos).toInt() and 0xFFFF
        val charLen: Int
        if ((u0 and 0x8000) != 0) {
            if (pos + 4 > limit) return ""
            val u1 = buf.getShort(pos + 2).toInt() and 0xFFFF
            charLen = ((u0 and 0x7FFF) shl 16) or u1
            pos += 4
        } else {
            charLen = u0
            pos += 2
        }
        val byteLen = (charLen * 2).coerceIn(0, (limit - pos).coerceAtLeast(0))
        val bytes = ByteArray(byteLen)
        val dup = buf.duplicate()
        dup.position(pos)
        dup.get(bytes, 0, byteLen)
        return String(bytes, StandardCharsets.UTF_16LE)
    }

    internal fun formatAttributeValue(type: Int, data: Int, rawIdx: Int, strings: List<String>): String {
        if (rawIdx in strings.indices && type == 0x03) {
            return strings[rawIdx]
        }
        return when (type) {
            0x00 -> ""
            0x01 -> String.format(Locale.US, "@0x%08x", data)
            0x02 -> String.format(Locale.US, "?0x%08x", data)
            0x03 -> strings.getOrNull(data).orEmpty()
            0x04 -> java.lang.Float.intBitsToFloat(data).toString()
            0x05 -> formatDimension(data)
            0x06 -> formatFraction(data)
            0x10 -> data.toString()
            0x11 -> String.format(Locale.US, "0x%x", data)
            0x12 -> if (data != 0) "true" else "false"
            0x1C, 0x1D, 0x1E, 0x1F -> String.format(Locale.US, "#%08x", data)
            else -> if (rawIdx in strings.indices) strings[rawIdx] else String.format(Locale.US, "0x%x", data)
        }
    }

    private fun formatDimension(data: Int): String {
        val units = arrayOf("px", "dp", "sp", "pt", "in", "mm")
        val unitIdx = (data and 0xF).coerceIn(0, units.lastIndex)
        val value = complexToFloat(data)
        return String.format(Locale.US, "%.1f%s", value, units[unitIdx])
    }

    private fun formatFraction(data: Int): String {
        val type = (data and 0xF)
        val suffix = if (type == 0) "%" else "%p"
        val value = complexToFloat(data) * 100f
        return String.format(Locale.US, "%.1f%s", value, suffix)
    }

    private fun complexToFloat(complex: Int): Float {
        val mantissa = (complex and 0xFFFFFF00.toInt()) shr 8
        val radix = (complex shr 4) and 0x3
        val mult = when (radix) {
            0 -> 1.0f
            1 -> 1.0f / (1 shl 7)
            2 -> 1.0f / (1 shl 15)
            else -> 1.0f / (1 shl 23)
        }
        return mantissa * mult
    }

    private fun escapeXml(value: String): String {
        return buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }

    internal fun extractPrintableStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) {
                sb.append(c.toChar())
            } else {
                if (sb.length >= 3) {
                    result += sb.toString()
                    if (result.size >= 4000) break
                }
                sb.setLength(0)
            }
        }
        return result.distinct()
    }
}
