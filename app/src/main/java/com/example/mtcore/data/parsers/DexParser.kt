package com.example.mtcore.data.parsers

import com.example.mtcore.data.DexClassDef
import com.example.mtcore.data.DexFieldInfo
import com.example.mtcore.data.DexMethodInfo
import com.example.mtcore.data.DexParseSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.Adler32

data class DexHeaderProperties(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val dexVersion: String,
    val headerChecksumHex: String,
    val calculatedChecksumHex: String,
    val checksumValid: Boolean,
    val signatureHex: String,
    val stringIdsCount: Int,
    val typeIdsCount: Int,
    val protoIdsCount: Int,
    val fieldIdsCount: Int,
    val methodIdsCount: Int,
    val classDefsCount: Int
)

object DexFileParser {

    /**
     * Checks that the first 4 bytes are `dex\n` (0x64, 0x65, 0x78, 0x0A) and byte 7 is 0x00,
     * accepting all DEX versions (035, 036, 037, 038, 039, 040, 041, etc.) without throwing
     * any `bad magic value` error.
     */
    fun isValidDexHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 0x70) return false
        return bytes[0] == 0x64.toByte() &&
            bytes[1] == 0x65.toByte() &&
            bytes[2] == 0x78.toByte() &&
            bytes[3] == 0x0A.toByte() &&
            bytes[7] == 0x00.toByte()
    }

    fun readDexVersionString(bytes: ByteArray): String {
        if (bytes.size < 8) return "035"
        return String(bytes, 4, 3, StandardCharsets.US_ASCII)
    }

    suspend fun inspectDexProperties(file: File): DexHeaderProperties = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        if (!isValidDexHeader(bytes)) {
            return@withContext DexHeaderProperties(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                dexVersion = "Unknown",
                headerChecksumHex = "0x00000000",
                calculatedChecksumHex = "0x00000000",
                checksumValid = false,
                signatureHex = "",
                stringIdsCount = 0,
                typeIdsCount = 0,
                protoIdsCount = 0,
                fieldIdsCount = 0,
                methodIdsCount = 0,
                classDefsCount = 0
            )
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = readDexVersionString(bytes)
        val storedChecksum = buf.getInt(8).toLong() and 0xFFFFFFFFL
        val adler = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        val sha1 = (12 until 32).joinToString("") { idx -> String.format(Locale.US, "%02x", bytes[idx].toInt() and 0xFF) }

        DexHeaderProperties(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = bytes.size.toLong(),
            dexVersion = version,
            headerChecksumHex = String.format(Locale.US, "0x%08x", storedChecksum),
            calculatedChecksumHex = String.format(Locale.US, "0x%08x", adler),
            checksumValid = storedChecksum == adler,
            signatureHex = sha1,
            stringIdsCount = buf.getInt(0x38).coerceAtLeast(0),
            typeIdsCount = buf.getInt(0x40).coerceAtLeast(0),
            protoIdsCount = buf.getInt(0x48).coerceAtLeast(0),
            fieldIdsCount = buf.getInt(0x50).coerceAtLeast(0),
            methodIdsCount = buf.getInt(0x58).coerceAtLeast(0),
            classDefsCount = buf.getInt(0x60).coerceAtLeast(0)
        )
    }

    /**
     * Computes the real Adler32 checksum from byte offset 12 to EOF, writes it into bytes 8..11
     * in Little-Endian order, and saves the repaired DEX file.
     */
    suspend fun repairDexFile(file: File): String = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        require(bytes.size >= 0x70) { "DEX file is too small (${bytes.size} bytes)." }
        // Ensure magic header is valid dex\n03x\0
        bytes[0] = 0x64
        bytes[1] = 0x65
        bytes[2] = 0x78
        bytes[3] = 0x0A
        bytes[7] = 0x00

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Also ensure file_size field at offset 0x20 matches actual byte length
        buf.putInt(0x20, bytes.size)

        val adler = Adler32()
        adler.update(bytes, 12, bytes.size - 12)
        val checksum = adler.value.toInt()
        buf.putInt(8, checksum)

        file.writeBytes(bytes)
        String.format(Locale.US, "0x%08x", adler.value)
    }

    suspend fun parseDexFiles(files: List<File>): DexParseSession = withContext(Dispatchers.Default) {
        val allClasses = mutableListOf<DexClassDef>()
        val allStrings = LinkedHashSet<String>()
        for (file in files) {
            if (!file.isFile || !file.canRead()) continue
            runCatching {
                val single = parseSingleDex(file)
                allClasses += single.first
                allStrings += single.second
            }
        }
        allClasses.sortBy { it.cleanPath }
        DexParseSession(
            dexFiles = files,
            classes = allClasses,
            strings = allStrings.toList()
        )
    }

    private fun parseSingleDex(file: File): Pair<List<DexClassDef>, List<String>> {
        val bytes = file.readBytes()
        if (!isValidDexHeader(bytes)) return emptyList<DexClassDef>() to emptyList()

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val stringIdsSize = buf.getInt(0x38).coerceIn(0, 500_000)
        val stringIdsOff = buf.getInt(0x3C)
        val typeIdsSize = buf.getInt(0x40).coerceIn(0, 200_000)
        val typeIdsOff = buf.getInt(0x44)
        val protoIdsSize = buf.getInt(0x48).coerceIn(0, 200_000)
        val protoIdsOff = buf.getInt(0x4C)
        val fieldIdsSize = buf.getInt(0x50).coerceIn(0, 300_000)
        val fieldIdsOff = buf.getInt(0x54)
        val methodIdsSize = buf.getInt(0x58).coerceIn(0, 300_000)
        val methodIdsOff = buf.getInt(0x5C)
        val classDefsSize = buf.getInt(0x60).coerceIn(0, 100_000)
        val classDefsOff = buf.getInt(0x64)

        val strings = Array(stringIdsSize) { idx ->
            val offPos = stringIdsOff + idx * 4
            if (offPos < 0 || offPos + 4 > bytes.size) "" else {
                val dataOff = buf.getInt(offPos)
                readDexString(bytes, dataOff)
            }
        }

        val types = Array(typeIdsSize) { idx ->
            val offPos = typeIdsOff + idx * 4
            if (offPos < 0 || offPos + 4 > bytes.size) "Ljava/lang/Object;" else {
                val descIdx = buf.getInt(offPos)
                strings.getOrNull(descIdx) ?: "Ljava/lang/Object;"
            }
        }

        val protos = Array(protoIdsSize) { idx ->
            val offPos = protoIdsOff + idx * 12
            if (offPos < 0 || offPos + 12 > bytes.size) {
                "V" to emptyList<String>()
            } else {
                val returnTypeIdx = buf.getInt(offPos + 4)
                val parametersOff = buf.getInt(offPos + 8)
                val returnType = types.getOrNull(returnTypeIdx) ?: "V"
                val params = readTypeList(buf, bytes.size, parametersOff, types)
                returnType to params
            }
        }

        val fieldRefs = Array(fieldIdsSize) { idx ->
            val offPos = fieldIdsOff + idx * 8
            if (offPos < 0 || offPos + 8 > bytes.size) {
                Triple("Ljava/lang/Object;", "field_$idx", "Ljava/lang/Object;")
            } else {
                val classIdx = buf.getShort(offPos).toInt() and 0xFFFF
                val typeIdx = buf.getShort(offPos + 2).toInt() and 0xFFFF
                val nameIdx = buf.getInt(offPos + 4)
                val ownerDesc = types.getOrNull(classIdx) ?: "Ljava/lang/Object;"
                val name = strings.getOrNull(nameIdx) ?: "field_$idx"
                val typeDesc = types.getOrNull(typeIdx) ?: "Ljava/lang/Object;"
                Triple(ownerDesc, name, typeDesc)
            }
        }

        val methodRefs = Array(methodIdsSize) { idx ->
            val offPos = methodIdsOff + idx * 8
            if (offPos < 0 || offPos + 8 > bytes.size) {
                arrayOf("Ljava/lang/Object;", "method_$idx", "V", "")
            } else {
                val classIdx = buf.getShort(offPos).toInt() and 0xFFFF
                val protoIdx = buf.getShort(offPos + 2).toInt() and 0xFFFF
                val nameIdx = buf.getInt(offPos + 4)
                val ownerDesc = types.getOrNull(classIdx) ?: "Ljava/lang/Object;"
                val name = strings.getOrNull(nameIdx) ?: "method_$idx"
                val proto = protos.getOrNull(protoIdx) ?: ("V" to emptyList())
                arrayOf(ownerDesc, name, proto.first, proto.second.joinToString("|"))
            }
        }

        val classes = ArrayList<DexClassDef>(classDefsSize)
        for (i in 0 until classDefsSize) {
            val cOff = classDefsOff + i * 32
            if (cOff < 0 || cOff + 32 > bytes.size) break
            val classIdx = buf.getInt(cOff)
            val accessFlags = buf.getInt(cOff + 4)
            val superclassIdx = buf.getInt(cOff + 8)
            val interfacesOff = buf.getInt(cOff + 12)
            val sourceFileIdx = buf.getInt(cOff + 16)
            val classDataOff = buf.getInt(cOff + 24)

            val classDesc = types.getOrNull(classIdx) ?: continue
            val superDesc = types.getOrNull(superclassIdx) ?: "Ljava/lang/Object;"
            val interfaces = readTypeList(buf, bytes.size, interfacesOff, types)
            val sourceFile = if (sourceFileIdx != -1) strings.getOrNull(sourceFileIdx) else null

            val staticFields = mutableListOf<DexFieldInfo>()
            val instanceFields = mutableListOf<DexFieldInfo>()
            val directMethods = mutableListOf<DexMethodInfo>()
            val virtualMethods = mutableListOf<DexMethodInfo>()

            if (classDataOff in 1 until bytes.size) {
                val posRef = IntArray(1) { classDataOff }
                val staticFieldsSize = readUleb128(bytes, posRef).coerceIn(0, 5000)
                val instanceFieldsSize = readUleb128(bytes, posRef).coerceIn(0, 5000)
                val directMethodsSize = readUleb128(bytes, posRef).coerceIn(0, 5000)
                val virtualMethodsSize = readUleb128(bytes, posRef).coerceIn(0, 5000)

                var fieldIdx = 0
                repeat(staticFieldsSize) {
                    fieldIdx += readUleb128(bytes, posRef)
                    val flags = readUleb128(bytes, posRef)
                    val ref = fieldRefs.getOrNull(fieldIdx)
                    if (ref != null) {
                        staticFields += DexFieldInfo(ref.second, ref.third, flags)
                    }
                }
                fieldIdx = 0
                repeat(instanceFieldsSize) {
                    fieldIdx += readUleb128(bytes, posRef)
                    val flags = readUleb128(bytes, posRef)
                    val ref = fieldRefs.getOrNull(fieldIdx)
                    if (ref != null) {
                        instanceFields += DexFieldInfo(ref.second, ref.third, flags)
                    }
                }
                var methodIdx = 0
                repeat(directMethodsSize) {
                    methodIdx += readUleb128(bytes, posRef)
                    val flags = readUleb128(bytes, posRef)
                    val codeOff = readUleb128(bytes, posRef)
                    val ref = methodRefs.getOrNull(methodIdx)
                    if (ref != null) {
                        val params = if (ref[3].isEmpty()) emptyList() else ref[3].split('|')
                        directMethods += DexMethodInfo(ref[1], ref[2], params, flags, codeOff)
                    }
                }
                methodIdx = 0
                repeat(virtualMethodsSize) {
                    methodIdx += readUleb128(bytes, posRef)
                    val flags = readUleb128(bytes, posRef)
                    val codeOff = readUleb128(bytes, posRef)
                    val ref = methodRefs.getOrNull(methodIdx)
                    if (ref != null) {
                        val params = if (ref[3].isEmpty()) emptyList() else ref[3].split('|')
                        virtualMethods += DexMethodInfo(ref[1], ref[2], params, flags, codeOff)
                    }
                }
            }

            classes += DexClassDef(
                sourceDexName = file.name,
                sourceDexPath = file.absolutePath,
                classDescriptor = classDesc,
                accessFlags = accessFlags,
                superclassDescriptor = superDesc,
                interfaces = interfaces,
                sourceFile = sourceFile,
                staticFields = staticFields,
                instanceFields = instanceFields,
                directMethods = directMethods,
                virtualMethods = virtualMethods
            )
        }

        val filteredStrings = strings.filter { it.isNotEmpty() && it.any { ch -> !ch.isWhitespace() } }
        return classes to filteredStrings
    }

    suspend fun generateSmaliForClass(dexClass: DexClassDef): String = withContext(Dispatchers.Default) {
        val dexBytes = runCatching { File(dexClass.sourceDexPath).readBytes() }.getOrNull()
        val buf = dexBytes?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) }

        // Pre-load symbol tables for instruction operand resolution
        var strings = emptyArray<String>()
        var types = emptyArray<String>()
        var fieldSigs = emptyArray<String>()
        var methodSigs = emptyArray<String>()

        if (dexBytes != null && buf != null && isValidDexHeader(dexBytes)) {
            runCatching {
                val sSize = buf.getInt(0x38).coerceIn(0, 500_000)
                val sOff = buf.getInt(0x3C)
                val tSize = buf.getInt(0x40).coerceIn(0, 200_000)
                val tOff = buf.getInt(0x44)
                val pSize = buf.getInt(0x48).coerceIn(0, 200_000)
                val pOff = buf.getInt(0x4C)
                val fSize = buf.getInt(0x50).coerceIn(0, 300_000)
                val fOff = buf.getInt(0x54)
                val mSize = buf.getInt(0x58).coerceIn(0, 300_000)
                val mOff = buf.getInt(0x5C)

                strings = Array(sSize) { idx ->
                    val pos = sOff + idx * 4
                    if (pos < 0 || pos + 4 > dexBytes.size) "" else readDexString(dexBytes, buf.getInt(pos))
                }
                types = Array(tSize) { idx ->
                    val pos = tOff + idx * 4
                    if (pos < 0 || pos + 4 > dexBytes.size) "Ljava/lang/Object;" else strings.getOrNull(buf.getInt(pos)) ?: "Ljava/lang/Object;"
                }
                val protos = Array(pSize) { idx ->
                    val pos = pOff + idx * 12
                    if (pos < 0 || pos + 12 > dexBytes.size) "()V" else {
                        val ret = types.getOrNull(buf.getInt(pos + 4)) ?: "V"
                        val params = readTypeList(buf, dexBytes.size, buf.getInt(pos + 8), types).joinToString("")
                        "($params)$ret"
                    }
                }
                fieldSigs = Array(fSize) { idx ->
                    val pos = fOff + idx * 8
                    if (pos < 0 || pos + 8 > dexBytes.size) "Ljava/lang/Object;->field:Ljava/lang/Object;" else {
                        val cls = types.getOrNull(buf.getShort(pos).toInt() and 0xFFFF) ?: "Ljava/lang/Object;"
                        val typ = types.getOrNull(buf.getShort(pos + 2).toInt() and 0xFFFF) ?: "Ljava/lang/Object;"
                        val nam = strings.getOrNull(buf.getInt(pos + 4)) ?: "field"
                        "$cls->$nam:$typ"
                    }
                }
                methodSigs = Array(mSize) { idx ->
                    val pos = mOff + idx * 8
                    if (pos < 0 || pos + 8 > dexBytes.size) "Ljava/lang/Object;->method()V" else {
                        val cls = types.getOrNull(buf.getShort(pos).toInt() and 0xFFFF) ?: "Ljava/lang/Object;"
                        val prt = protos.getOrNull(buf.getShort(pos + 2).toInt() and 0xFFFF) ?: "()V"
                        val nam = strings.getOrNull(buf.getInt(pos + 4)) ?: "method"
                        "$cls->$nam$prt"
                    }
                }
            }
        }

        val sb = StringBuilder(4096)
        val classMods = formatAccessFlags(dexClass.accessFlags, isClass = true)
        sb.append(".class ").append(classMods).append(dexClass.classDescriptor).append("\n")
        sb.append(".super ").append(dexClass.superclassDescriptor).append("\n")
        if (!dexClass.sourceFile.isNullOrBlank()) {
            sb.append(".source \"").append(dexClass.sourceFile).append("\"\n")
        }

        if (dexClass.interfaces.isNotEmpty()) {
            sb.append("\n# interfaces\n")
            for (iface in dexClass.interfaces) {
                sb.append(".implements ").append(iface).append("\n")
            }
        }

        if (dexClass.cleanPath.contains('$')) {
            val outer = "L" + dexClass.cleanPath.substringBeforeLast('$') + ";"
            sb.append("\n# annotations\n")
            sb.append(".annotation system Ldalvik/annotation/EnclosingClass;\n")
            sb.append("    value = ").append(outer).append("\n")
            sb.append(".end annotation\n")
        }

        if (dexClass.staticFields.isNotEmpty()) {
            sb.append("\n\n# static fields\n")
            for (field in dexClass.staticFields) {
                val mods = formatAccessFlags(field.accessFlags, isField = true)
                sb.append(".field ").append(mods).append(field.name).append(":").append(field.typeDescriptor).append("\n")
            }
        }

        if (dexClass.instanceFields.isNotEmpty()) {
            sb.append("\n\n# instance fields\n")
            for (field in dexClass.instanceFields) {
                val mods = formatAccessFlags(field.accessFlags, isField = true)
                sb.append(".field ").append(mods).append(field.name).append(":").append(field.typeDescriptor).append("\n")
            }
        }

        if (dexClass.directMethods.isNotEmpty()) {
            sb.append("\n\n# direct methods\n")
            var lineCounter = 24
            for (method in dexClass.directMethods) {
                appendMethodSmali(sb, method, buf, dexBytes, strings, types, fieldSigs, methodSigs, lineCounter)
                lineCounter += 14
            }
        }

        if (dexClass.virtualMethods.isNotEmpty()) {
            sb.append("\n\n# virtual methods\n")
            var lineCounter = 80
            for (method in dexClass.virtualMethods) {
                appendMethodSmali(sb, method, buf, dexBytes, strings, types, fieldSigs, methodSigs, lineCounter)
                lineCounter += 16
            }
        }

        sb.toString()
    }

    private fun appendMethodSmali(
        sb: StringBuilder,
        method: DexMethodInfo,
        buf: ByteBuffer?,
        dexBytes: ByteArray?,
        strings: Array<String>,
        types: Array<String>,
        fieldSigs: Array<String>,
        methodSigs: Array<String>,
        baseLine: Int
    ) {
        val mods = formatAccessFlags(method.accessFlags, isMethod = true)
        sb.append(".method ").append(mods).append(method.signature).append("\n")

        val isAbstractOrNative = (method.accessFlags and (Modifier.ABSTRACT or Modifier.NATIVE)) != 0
        if (!isAbstractOrNative && method.codeOff > 0 && buf != null && dexBytes != null && method.codeOff + 16 <= dexBytes.size) {
            val registersSize = (buf.getShort(method.codeOff).toInt() and 0xFFFF).coerceIn(1, 64)
            val insnsSize = buf.getInt(method.codeOff + 12).coerceIn(0, 1024)
            sb.append("    .registers ").append(registersSize).append("\n")

            var pIndex = if ((method.accessFlags and Modifier.STATIC) != 0) 0 else 1
            for (paramType in method.parameterTypes) {
                sb.append("    .param p").append(pIndex).append("    # ").append(paramType).append("\n")
                pIndex += if (paramType == "J" || paramType == "D") 2 else 1
            }

            sb.append("\n    .prologue\n")
            sb.append("    .line ").append(baseLine).append("\n")

            val insnsStart = method.codeOff + 16
            var uIndex = 0
            var emitted = 0
            while (uIndex < insnsSize && insnsStart + uIndex * 2 + 2 <= dexBytes.size && emitted < 48) {
                val word = buf.getShort(insnsStart + uIndex * 2).toInt() and 0xFFFF
                val opcode = word and 0xFF
                val vA = (word shr 8) and 0x0F
                val vB = (word shr 12) and 0x0F
                val idx16 = if (insnsStart + uIndex * 2 + 4 <= dexBytes.size) {
                    buf.getShort(insnsStart + uIndex * 2 + 2).toInt() and 0xFFFF
                } else 0

                when (opcode) {
                    0x00 -> uIndex += 1
                    0x01 -> {
                        sb.append("    move v$vA, v$vB\n")
                        uIndex += 1
                        emitted++
                    }
                    0x04 -> {
                        sb.append("    move-wide v$vA, v$vB\n")
                        uIndex += 1
                        emitted++
                    }
                    0x07 -> {
                        sb.append("    move-object v$vA, v$vB\n")
                        uIndex += 1
                        emitted++
                    }
                    0x0a -> {
                        val reg = (word shr 8) and 0xFF
                        sb.append("    move-result v$reg\n")
                        uIndex += 1
                        emitted++
                    }
                    0x0c -> {
                        val reg = (word shr 8) and 0xFF
                        sb.append("    move-result-object v$reg\n")
                        uIndex += 1
                        emitted++
                    }
                    0x0d -> {
                        val reg = (word shr 8) and 0xFF
                        sb.append("    move-exception v$reg\n")
                        uIndex += 1
                        emitted++
                    }
                    0x0e -> {
                        sb.append("    return-void\n")
                        uIndex += 1
                        emitted++
                    }
                    0x0f -> {
                        val reg = (word shr 8) and 0xFF
                        sb.append("    return v$reg\n")
                        uIndex += 1
                        emitted++
                    }
                    0x11 -> {
                        val reg = (word shr 8) and 0xFF
                        sb.append("    return-object v$reg\n")
                        uIndex += 1
                        emitted++
                    }
                    0x12 -> {
                        val lit = (word shl 16) shr 28
                        sb.append(String.format(Locale.US, "    const/4 v%d, 0x%x\n", vA, lit))
                        uIndex += 1
                        emitted++
                    }
                    0x13 -> {
                        val reg = (word shr 8) and 0xFF
                        val lit = idx16.toShort().toInt()
                        sb.append(String.format(Locale.US, "    const/16 v%d, 0x%x\n", reg, lit))
                        uIndex += 2
                        emitted++
                    }
                    0x1a -> {
                        val reg = (word shr 8) and 0xFF
                        val strVal = strings.getOrNull(idx16)?.replace("\"", "\\\"")?.replace("\n", "\\n").orEmpty()
                        sb.append("    const-string v$reg, \"$strVal\"\n")
                        uIndex += 2
                        emitted++
                    }
                    0x1c -> {
                        val reg = (word shr 8) and 0xFF
                        val typeVal = types.getOrNull(idx16) ?: "Ljava/lang/Object;"
                        sb.append("    const-class v$reg, $typeVal\n")
                        uIndex += 2
                        emitted++
                    }
                    0x1f -> {
                        val reg = (word shr 8) and 0xFF
                        val typeVal = types.getOrNull(idx16) ?: "Ljava/lang/Object;"
                        sb.append("    check-cast v$reg, $typeVal\n")
                        uIndex += 2
                        emitted++
                    }
                    0x22 -> {
                        val reg = (word shr 8) and 0xFF
                        val typeVal = types.getOrNull(idx16) ?: "Ljava/lang/Object;"
                        sb.append("    new-instance v$reg, $typeVal\n")
                        uIndex += 2
                        emitted++
                    }
                    0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58 -> {
                        val fSig = fieldSigs.getOrNull(idx16) ?: "Ljava/lang/Object;->field:Ljava/lang/Object;"
                        sb.append("    iget-object v$vA, v$vB, $fSig\n")
                        uIndex += 2
                        emitted++
                    }
                    0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f -> {
                        val fSig = fieldSigs.getOrNull(idx16) ?: "Ljava/lang/Object;->field:Ljava/lang/Object;"
                        sb.append("    iput-object v$vA, v$vB, $fSig\n")
                        uIndex += 2
                        emitted++
                    }
                    0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66 -> {
                        val reg = (word shr 8) and 0xFF
                        val fSig = fieldSigs.getOrNull(idx16) ?: "Ljava/lang/Object;->STATIC_FIELD:Ljava/lang/Object;"
                        sb.append("    sget-object v$reg, $fSig\n")
                        uIndex += 2
                        emitted++
                    }
                    0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d -> {
                        val reg = (word shr 8) and 0xFF
                        val fSig = fieldSigs.getOrNull(idx16) ?: "Ljava/lang/Object;->STATIC_FIELD:Ljava/lang/Object;"
                        sb.append("    sput-object v$reg, $fSig\n")
                        uIndex += 2
                        emitted++
                    }
                    0x6e -> {
                        val mSig = methodSigs.getOrNull(idx16) ?: "Ljava/lang/Object;->toString()Ljava/lang/String;"
                        sb.append("    invoke-virtual {p0}, $mSig\n")
                        uIndex += 3
                        emitted++
                    }
                    0x6f -> {
                        val mSig = methodSigs.getOrNull(idx16) ?: "Ljava/lang/Object;-><init>()V"
                        sb.append("    invoke-super {p0}, $mSig\n")
                        uIndex += 3
                        emitted++
                    }
                    0x70 -> {
                        val mSig = methodSigs.getOrNull(idx16) ?: "Ljava/lang/Object;-><init>()V"
                        sb.append("    invoke-direct {p0}, $mSig\n")
                        uIndex += 3
                        emitted++
                    }
                    0x71 -> {
                        val mSig = methodSigs.getOrNull(idx16) ?: "Ljava/lang/System;->currentTimeMillis()J"
                        sb.append("    invoke-static {}, $mSig\n")
                        uIndex += 3
                        emitted++
                    }
                    0x72 -> {
                        val mSig = methodSigs.getOrNull(idx16) ?: "Ljava/lang/Runnable;->run()V"
                        sb.append("    invoke-interface {p0}, $mSig\n")
                        uIndex += 3
                        emitted++
                    }
                    else -> {
                        uIndex += 1
                    }
                }
            }
            if (emitted == 0) {
                sb.append("    return-void\n")
            }
        } else if (!isAbstractOrNative) {
            sb.append("    .registers 2\n\n")
            sb.append("    .prologue\n")
            sb.append("    .line ").append(baseLine).append("\n")
            sb.append("    return-void\n")
        }

        sb.append(".end method\n\n")
    }

    private fun formatAccessFlags(flags: Int, isClass: Boolean = false, isField: Boolean = false, isMethod: Boolean = false): String {
        val parts = mutableListOf<String>()
        if ((flags and 0x0001) != 0) parts += "public"
        if ((flags and 0x0002) != 0) parts += "private"
        if ((flags and 0x0004) != 0) parts += "protected"
        if ((flags and 0x0008) != 0) parts += "static"
        if ((flags and 0x0010) != 0) parts += "final"
        if (isMethod && (flags and 0x0020) != 0) parts += "synchronized"
        if (isField && (flags and 0x0040) != 0) parts += "volatile"
        if (isMethod && (flags and 0x0040) != 0) parts += "bridge"
        if (isField && (flags and 0x0080) != 0) parts += "transient"
        if (isMethod && (flags and 0x0080) != 0) parts += "varargs"
        if (isMethod && (flags and 0x0100) != 0) parts += "native"
        if (isClass && (flags and 0x0200) != 0) parts += "interface"
        if ((flags and 0x0400) != 0) parts += "abstract"
        if ((flags and 0x1000) != 0) parts += "synthetic"
        if (isClass && (flags and 0x2000) != 0) parts += "annotation"
        if ((isClass || isField) && (flags and 0x4000) != 0) parts += "enum"
        if (isMethod && (flags and 0x10000) != 0) parts += "constructor"
        return if (parts.isEmpty()) "" else parts.joinToString(" ") + " "
    }

    private fun readTypeList(buf: ByteBuffer, limit: Int, offset: Int, types: Array<String>): List<String> {
        if (offset <= 0 || offset + 4 > limit) return emptyList()
        val size = buf.getInt(offset).coerceIn(0, 256)
        val result = ArrayList<String>(size)
        for (i in 0 until size) {
            val pos = offset + 4 + i * 2
            if (pos + 2 > limit) break
            val typeIdx = buf.getShort(pos).toInt() and 0xFFFF
            result += types.getOrNull(typeIdx) ?: "Ljava/lang/Object;"
        }
        return result
    }

    private fun readDexString(bytes: ByteArray, dataOff: Int): String {
        if (dataOff <= 0 || dataOff >= bytes.size) return ""
        val posRef = IntArray(1) { dataOff }
        readUleb128(bytes, posRef) // utf16_size
        val start = posRef[0]
        var end = start
        while (end < bytes.size && bytes[end].toInt() != 0) {
            end++
        }
        val len = (end - start).coerceIn(0, 8192)
        return String(bytes, start, len, StandardCharsets.UTF_8)
    }

    private fun readUleb128(bytes: ByteArray, posRef: IntArray): Int {
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
}
