package com.example.mtcore.data.parsers

import com.example.mtcore.data.ArscEntry
import com.example.mtcore.data.ArscTableResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object ArscParser {
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_TYPE = 0x0201

    suspend fun parseFile(file: File): ArscTableResult = withContext(Dispatchers.Default) {
        parseBytes(file.readBytes())
    }

    fun parseBytes(bytes: ByteArray): ArscTableResult {
        if (bytes.size < 12) {
            return ArscTableResult("", emptyList(), emptyList())
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val tableType = buf.getShort(0).toInt() and 0xFFFF
        if (tableType != RES_TABLE_TYPE) {
            val pool = AxmlParser.extractStringPool(bytes)
            return ArscTableResult("", emptyList(), pool)
        }
        val headerSize = (buf.getShort(2).toInt() and 0xFFFF).coerceAtLeast(12)
        val totalSize = buf.getInt(4).coerceAtMost(bytes.size)

        var globalStrings = emptyList<String>()
        var packageName = "app"
        val entries = mutableListOf<ArscEntry>()

        var offset = headerSize
        while (offset + 8 <= totalSize) {
            val chunkType = buf.getShort(offset).toInt() and 0xFFFF
            val chunkSize = buf.getInt(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) break

            when (chunkType) {
                RES_STRING_POOL_TYPE -> {
                    globalStrings = AxmlParser.parseStringPoolChunk(buf, offset)
                }
                RES_TABLE_PACKAGE_TYPE -> {
                    val pkgHeaderSize = (buf.getShort(offset + 2).toInt() and 0xFFFF).coerceAtLeast(28)
                    val pkgId = buf.getInt(offset + 8) and 0xFF
                    packageName = readUtf16FixedString(buf, offset + 12, 128).ifBlank { "package_0x${pkgId.toString(16)}" }
                    val typeStringsOffset = buf.getInt(offset + 268)
                    val keyStringsOffset = buf.getInt(offset + 276)

                    val typeStrings = if (typeStringsOffset > 0 && offset + typeStringsOffset + 8 <= bytes.size) {
                        AxmlParser.parseStringPoolChunk(buf, offset + typeStringsOffset)
                    } else emptyList()

                    val keyStrings = if (keyStringsOffset > 0 && offset + keyStringsOffset + 8 <= bytes.size) {
                        AxmlParser.parseStringPoolChunk(buf, offset + keyStringsOffset)
                    } else emptyList()

                    var subOff = offset + pkgHeaderSize
                    val pkgEnd = (offset + chunkSize).coerceAtMost(bytes.size)
                    while (subOff + 8 <= pkgEnd && entries.size < 3500) {
                        val subType = buf.getShort(subOff).toInt() and 0xFFFF
                        val subHeaderSize = buf.getShort(subOff + 2).toInt() and 0xFFFF
                        val subSize = buf.getInt(subOff + 4)
                        if (subSize <= 0 || subOff + subSize > pkgEnd) break

                        if (subType == RES_TABLE_TYPE_TYPE && subOff + 20 <= pkgEnd) {
                            val typeId = buf.get(subOff + 8).toInt() and 0xFF
                            val entryCount = buf.getInt(subOff + 12)
                            val entriesStart = buf.getInt(subOff + 16)
                            val typeName = typeStrings.getOrNull(typeId - 1) ?: "type_$typeId"

                            val offsetsBase = subOff + subHeaderSize
                            val dataBase = subOff + entriesStart
                            val maxCount = entryCount.coerceIn(0, 2000)
                            for (idx in 0 until maxCount) {
                                if (offsetsBase + (idx + 1) * 4 > pkgEnd) break
                                val entryOff = buf.getInt(offsetsBase + idx * 4)
                                if (entryOff == -1 || entryOff < 0) continue
                                val ePos = dataBase + entryOff
                                if (ePos + 8 > subOff + subSize) continue
                                val eSize = buf.getShort(ePos).toInt() and 0xFFFF
                                val eFlags = buf.getShort(ePos + 2).toInt() and 0xFFFF
                                val keyIdx = buf.getInt(ePos + 4)
                                val keyName = keyStrings.getOrNull(keyIdx) ?: "res_$idx"
                                val resId = (pkgId shl 24) or (typeId shl 16) or idx
                                val idHex = String.format(Locale.US, "0x%08x", resId)

                                if ((eFlags and 0x0001) == 0 && ePos + eSize + 8 <= subOff + subSize) {
                                    val valType = buf.get(ePos + eSize + 3).toInt() and 0xFF
                                    val valData = buf.getInt(ePos + eSize + 4)
                                    val formatted = AxmlParser.formatAttributeValue(
                                        valType,
                                        valData,
                                        if (valType == 0x03) valData else -1,
                                        globalStrings
                                    )
                                    entries += ArscEntry(
                                        idHex = idHex,
                                        typeName = typeName,
                                        keyName = keyName,
                                        value = formatted
                                    )
                                }
                            }
                        }
                        subOff += subSize
                    }
                }
            }
            offset += chunkSize
        }

        return ArscTableResult(
            packageName = packageName,
            entries = entries.distinctBy { "${it.idHex}:${it.value}" },
            stringPool = globalStrings
        )
    }

    private fun readUtf16FixedString(buf: ByteBuffer, startPos: Int, maxChars: Int): String {
        val sb = StringBuilder()
        for (i in 0 until maxChars) {
            val pos = startPos + i * 2
            if (pos + 2 > buf.limit()) break
            val ch = buf.getShort(pos).toInt() and 0xFFFF
            if (ch == 0) break
            sb.append(ch.toChar())
        }
        return sb.toString()
    }
}
