package com.example.mtcore.data

import com.example.mtcore.data.parsers.ArscParser
import com.example.mtcore.data.parsers.AxmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

data class ArscEntry(
    val idHex: String,
    val typeName: String,
    val keyName: String,
    val value: String
)

data class ArscTableResult(
    val packageName: String,
    val entries: List<ArscEntry>,
    val stringPool: List<String>
)

object BinaryXmlDecompiler {
    fun isBinaryXml(file: File): Boolean = AxmlParser.isBinaryXml(file)

    suspend fun decompileFile(file: File): String = AxmlParser.decompileFile(file)

    fun decompile(bytes: ByteArray): String = AxmlParser.decompileToXml(bytes)

    suspend fun extractStringPoolFromFile(file: File): List<String> = withContext(Dispatchers.Default) {
        AxmlParser.extractStringPool(file.readBytes())
    }

    fun extractStringPool(bytes: ByteArray): List<String> = AxmlParser.extractStringPool(bytes)

    suspend fun saveUpdatedStringPoolToFile(file: File, updatedStrings: List<String>): Boolean = withContext(Dispatchers.IO) {
        val original = file.readBytes()
        val rebuilt = AxmlParser.rebuildBinaryWithUpdatedStringPool(original, updatedStrings)
        file.writeBytes(rebuilt)
        true
    }

    suspend fun parseArscFile(file: File): ArscTableResult = ArscParser.parseFile(file)

    fun parseArscResources(bytes: ByteArray): ArscTableResult = ArscParser.parseBytes(bytes)

    suspend fun formatHexDump(file: File, maxBytes: Int = 64 * 1024): String = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext ""
        val length = file.length().coerceAtMost(maxBytes.toLong()).toInt()
        val buffer = ByteArray(length)
        RandomAccessFile(file, "r").use { raf ->
            raf.readFully(buffer, 0, length)
        }
        val sb = StringBuilder(length * 4)
        var offset = 0
        while (offset < length) {
            val rowLen = (length - offset).coerceAtMost(16)
            sb.append(String.format(Locale.US, "%08X  ", offset))
            for (i in 0 until 16) {
                if (i < rowLen) {
                    sb.append(String.format(Locale.US, "%02X ", buffer[offset + i].toInt() and 0xFF))
                } else {
                    sb.append("   ")
                }
                if (i == 7) sb.append(' ')
            }
            sb.append(" |")
            for (i in 0 until rowLen) {
                val b = buffer[offset + i].toInt() and 0xFF
                sb.append(if (b in 0x20..0x7E) b.toChar() else '.')
            }
            sb.append("|\n")
            offset += 16
        }
        if (file.length() > maxBytes) {
            sb.append(String.format(Locale.US, "\n--- Showing first %d KB of %d bytes ---", maxBytes / 1024, file.length()))
        }
        sb.toString()
    }
}
