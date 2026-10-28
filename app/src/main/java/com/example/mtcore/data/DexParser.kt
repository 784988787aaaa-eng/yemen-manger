package com.example.mtcore.data

import com.example.mtcore.data.parsers.DexFileParser
import com.example.mtcore.data.parsers.DexHeaderProperties
import java.io.File

data class DexFieldInfo(
    val name: String,
    val typeDescriptor: String,
    val accessFlags: Int
)

data class DexMethodInfo(
    val name: String,
    val returnType: String,
    val parameterTypes: List<String>,
    val accessFlags: Int,
    val codeOff: Int
) {
    val signature: String
        get() = "$name(${parameterTypes.joinToString("")})$returnType"
}

data class DexClassDef(
    val sourceDexName: String,
    val sourceDexPath: String,
    val classDescriptor: String,
    val accessFlags: Int,
    val superclassDescriptor: String,
    val interfaces: List<String>,
    val sourceFile: String?,
    val staticFields: List<DexFieldInfo>,
    val instanceFields: List<DexFieldInfo>,
    val directMethods: List<DexMethodInfo>,
    val virtualMethods: List<DexMethodInfo>
) {
    val cleanPath: String
        get() = classDescriptor.removePrefix("L").removeSuffix(";")

    val packagePath: String
        get() = cleanPath.substringBeforeLast('/', "")

    val simpleName: String
        get() = cleanPath.substringAfterLast('/')
}

data class DexParseSession(
    val dexFiles: List<File>,
    val classes: List<DexClassDef>,
    val strings: List<String>
)

object DexParser {
    suspend fun parseDexFiles(files: List<File>): DexParseSession = DexFileParser.parseDexFiles(files)

    suspend fun generateSmaliForClass(dexClass: DexClassDef): String = DexFileParser.generateSmaliForClass(dexClass)

    suspend fun inspectDexProperties(file: File): DexHeaderProperties = DexFileParser.inspectDexProperties(file)

    suspend fun repairDexFile(file: File): String = DexFileParser.repairDexFile(file)
}
