package com.example.core.storage

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds binary AndroidManifest.xml data for APK rebuild operations. */
object BinaryAxmlBuilder {
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
}
