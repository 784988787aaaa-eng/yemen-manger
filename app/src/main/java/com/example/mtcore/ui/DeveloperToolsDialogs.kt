package com.example.mtcore.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mtcore.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

private val DevBlue = Color(0xFF1976D2)
private val DevDark = Color(0xFF111111)
private val DevGray = Color(0xFF757575)

// ============================================================================
// 1. Smali to Java Decompiler (JADX / CFR style AST & Dalvik -> Java converter)
// ============================================================================

object SmaliToJavaDecompiler {

    fun decompile(smaliText: String): String {
        val lines = smaliText.lines()
        var packageName = ""
        var classModifiers = "public class"
        var className = "DecompiledClass"
        var superClass = "Object"
        val interfaces = ArrayList<String>()
        val fields = ArrayList<String>()
        val methods = ArrayList<String>()

        var inMethod = false
        var currentMethodHeader = ""
        val currentMethodBody = ArrayList<String>()
        val regValues = HashMap<String, String>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            when {
                line.startsWith(".class ") -> {
                    val tokens = line.removePrefix(".class ").trim().split(Regex("\\s+"))
                    val desc = tokens.lastOrNull() ?: "LDecompiledClass;"
                    val mods = tokens.dropLast(1).filter { it !in setOf("super", "synthetic", "bridge") }
                    val cleanPath = desc.removePrefix("L").removeSuffix(";")
                    packageName = cleanPath.substringBeforeLast('/', "").replace('/', '.')
                    className = cleanPath.substringAfterLast('/')
                    val isInterface = "interface" in mods
                    val visibility = mods.firstOrNull { it in setOf("public", "private", "protected") } ?: "public"
                    val extraMods = mods.filter { it in setOf("final", "abstract", "static") }.joinToString(" ")
                    classModifiers = buildString {
                        append(visibility)
                        if (extraMods.isNotEmpty()) append(" ").append(extraMods)
                        append(if (isInterface) " interface" else " class")
                    }
                }
                line.startsWith(".super ") -> {
                    val desc = line.removePrefix(".super ").trim()
                    superClass = descriptorToJavaType(desc)
                }
                line.startsWith(".implements ") -> {
                    val desc = line.removePrefix(".implements ").trim()
                    interfaces += descriptorToJavaType(desc)
                }
                line.startsWith(".field ") -> {
                    val content = line.removePrefix(".field ").trim()
                    val beforeValue = content.substringBefore(" = ").trim()
                    val initValue = content.substringAfter(" = ", "").trim()
                    val parts = beforeValue.split(Regex("\\s+"))
                    val nameAndType = parts.lastOrNull() ?: ""
                    val mods = parts.dropLast(1).filter { it !in setOf("synthetic", "enum") }.joinToString(" ")
                    val fieldName = nameAndType.substringBefore(':')
                    val fieldType = descriptorToJavaType(nameAndType.substringAfter(':', "Ljava/lang/Object;"))
                    val prefix = if (mods.isNotEmpty()) "$mods " else "private "
                    val suffix = if (initValue.isNotEmpty()) " = $initValue;" else ";"
                    fields += "    $prefix$fieldType $fieldName$suffix"
                }
                line.startsWith(".method ") -> {
                    inMethod = true
                    currentMethodBody.clear()
                    regValues.clear()
                    val content = line.removePrefix(".method ").trim()
                    val parts = content.split(Regex("\\s+"))
                    val sig = parts.lastOrNull() ?: "method()V"
                    val mods = parts.dropLast(1).filter { it !in setOf("bridge", "synthetic", "constructor", "declared-synchronized") }
                    val methodNameRaw = sig.substringBefore('(')
                    val argsRaw = sig.substringAfter('(', "").substringBefore(')')
                    val retRaw = sig.substringAfter(')', "V")
                    val paramTypes = parseParamDescriptors(argsRaw)
                    val isStatic = "static" in mods
                    var pIndex = if (isStatic) 0 else 1
                    val paramsFormatted = paramTypes.mapIndexed { idx, type ->
                        val pReg = "p$pIndex"
                        val argName = "arg$idx"
                        regValues[pReg] = argName
                        pIndex++
                        "$type $argName"
                    }.joinToString(", ")
                    if (!isStatic) {
                        regValues["p0"] = "this"
                    }

                    val modStr = mods.joinToString(" ").ifEmpty { "public" }
                    currentMethodHeader = when (methodNameRaw) {
                        "<init>" -> "    $modStr $className($paramsFormatted)"
                        "<clinit>" -> "    static"
                        else -> "    $modStr ${descriptorToJavaType(retRaw)} $methodNameRaw($paramsFormatted)"
                    }
                }
                line == ".end method" -> {
                    if (inMethod) {
                        val sb = StringBuilder()
                        sb.appendLine("$currentMethodHeader {")
                        if (currentMethodBody.isEmpty()) {
                            sb.appendLine("        // empty method")
                        } else {
                            currentMethodBody.forEach { stmt -> sb.appendLine("        $stmt") }
                        }
                        sb.append("    }")
                        methods += sb.toString()
                        inMethod = false
                    }
                }
                inMethod -> {
                    translateDalvikInstruction(line, regValues)?.let { stmt ->
                        currentMethodBody += stmt
                    }
                }
            }
        }

        val out = StringBuilder()
        if (packageName.isNotEmpty()) {
            out.appendLine("package $packageName;")
            out.appendLine()
        }
        out.append("$classModifiers $className")
        if (superClass.isNotEmpty() && superClass != "Object" && superClass != "java.lang.Object") {
            out.append(" extends $superClass")
        }
        if (interfaces.isNotEmpty()) {
            out.append(" implements ${interfaces.joinToString(", ")}")
        }
        out.appendLine(" {")
        if (fields.isNotEmpty()) {
            out.appendLine()
            fields.forEach { out.appendLine(it) }
        }
        if (methods.isNotEmpty()) {
            out.appendLine()
            methods.forEachIndexed { idx, m ->
                out.appendLine(m)
                if (idx < methods.lastIndex) out.appendLine()
            }
        }
        out.appendLine("}")
        return out.toString()
    }

    private fun translateDalvikInstruction(
        line: String,
        regValues: MutableMap<String, String>
    ): String? {
        if (line.startsWith(".") || line.startsWith(":")) return null
        val opcode = line.substringBefore(' ').trim()
        val rest = line.substringAfter(' ', "").trim()

        return when {
            opcode == "return-void" -> "return;"
            opcode.startsWith("return") -> {
                val reg = rest.trim()
                val expr = regValues[reg] ?: reg
                "return $expr;"
            }
            opcode == "const-string" || opcode == "const-string/jumbo" -> {
                val reg = rest.substringBefore(',').trim()
                val strLiteral = rest.substringAfter(',').trim()
                regValues[reg] = strLiteral
                "String $reg = $strLiteral;"
            }
            opcode.startsWith("const") -> {
                val reg = rest.substringBefore(',').trim()
                val lit = rest.substringAfter(',').trim()
                regValues[reg] = lit
                "int $reg = $lit;"
            }
            opcode == "new-instance" -> {
                val reg = rest.substringBefore(',').trim()
                val typeDesc = rest.substringAfter(',').trim()
                val javaType = descriptorToJavaType(typeDesc)
                regValues[reg] = "new $javaType()"
                null
            }
            opcode.startsWith("iget") -> {
                val parts = rest.split(',').map { it.trim() }
                if (parts.size >= 3) {
                    val destReg = parts[0]
                    val objReg = regValues[parts[1]] ?: parts[1]
                    val fieldName = parts[2].substringAfter("->").substringBefore(':')
                    val fieldType = descriptorToJavaType(parts[2].substringAfter(':', "Ljava/lang/Object;"))
                    val expr = "$objReg.$fieldName"
                    regValues[destReg] = expr
                    "$fieldType $destReg = $expr;"
                } else null
            }
            opcode.startsWith("iput") -> {
                val parts = rest.split(',').map { it.trim() }
                if (parts.size >= 3) {
                    val valExpr = regValues[parts[0]] ?: parts[0]
                    val objExpr = regValues[parts[1]] ?: parts[1]
                    val fieldName = parts[2].substringAfter("->").substringBefore(':')
                    "$objExpr.$fieldName = $valExpr;"
                } else null
            }
            opcode.startsWith("sget") -> {
                val parts = rest.split(',').map { it.trim() }
                if (parts.size >= 2) {
                    val destReg = parts[0]
                    val ref = parts[1]
                    val owner = descriptorToJavaType(ref.substringBefore("->"))
                    val fieldName = ref.substringAfter("->").substringBefore(':')
                    val fieldType = descriptorToJavaType(ref.substringAfter(':', "Ljava/lang/Object;"))
                    val expr = "$owner.$fieldName"
                    regValues[destReg] = expr
                    "$fieldType $destReg = $expr;"
                } else null
            }
            opcode.startsWith("sput") -> {
                val parts = rest.split(',').map { it.trim() }
                if (parts.size >= 2) {
                    val valExpr = regValues[parts[0]] ?: parts[0]
                    val ref = parts[1]
                    val owner = descriptorToJavaType(ref.substringBefore("->"))
                    val fieldName = ref.substringAfter("->").substringBefore(':')
                    "$owner.$fieldName = $valExpr;"
                } else null
            }
            opcode.startsWith("invoke-") -> {
                val regsRaw = rest.substringAfter('{', "").substringBefore('}')
                val regs = regsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val targetRef = rest.substringAfter("},", "").trim()
                val ownerType = descriptorToJavaType(targetRef.substringBefore("->"))
                val methodName = targetRef.substringAfter("->").substringBefore('(')
                val isStatic = opcode.contains("static")
                if (methodName == "<init>") {
                    val receiver = regs.firstOrNull() ?: "v0"
                    val args = regs.drop(1).joinToString(", ") { regValues[it] ?: it }
                    if (receiver == "p0") {
                        if (args.isNotEmpty()) "super($args);" else null
                    } else {
                        "$ownerType $receiver = new $ownerType($args);"
                    }
                } else {
                    val callArgs = (if (isStatic) regs else regs.drop(1)).joinToString(", ") { regValues[it] ?: it }
                    val receiver = if (isStatic) ownerType else (regValues[regs.firstOrNull().orEmpty()] ?: regs.firstOrNull() ?: ownerType)
                    "$receiver.$methodName($callArgs);"
                }
            }
            opcode.startsWith("if-") -> {
                val parts = rest.split(',').map { it.trim() }
                val condReg = regValues[parts.firstOrNull().orEmpty()] ?: parts.firstOrNull().orEmpty()
                val label = parts.lastOrNull().orEmpty()
                val op = when (opcode) {
                    "if-eqz" -> "== 0"
                    "if-nez" -> "!= 0"
                    "if-ltz" -> "< 0"
                    "if-gez" -> ">= 0"
                    "if-gtz" -> "> 0"
                    "if-lez" -> "<= 0"
                    else -> "/* $opcode */"
                }
                "if ($condReg $op) /* goto $label */;"
            }
            else -> null
        }
    }

    private fun parseParamDescriptors(raw: String): List<String> {
        val result = ArrayList<String>()
        var i = 0
        while (i < raw.length) {
            var arrayDim = 0
            while (i < raw.length && raw[i] == '[') {
                arrayDim++
                i++
            }
            if (i >= raw.length) break
            val baseType = if (raw[i] == 'L') {
                val end = raw.indexOf(';', i).let { if (it < 0) raw.length else it + 1 }
                val desc = raw.substring(i, end)
                i = end
                descriptorToJavaType(desc)
            } else {
                val desc = raw[i].toString()
                i++
                descriptorToJavaType(desc)
            }
            result += baseType + "[]".repeat(arrayDim)
        }
        return result
    }

    fun descriptorToJavaType(desc: String): String {
        val trimmed = desc.trim()
        if (trimmed.isEmpty()) return "void"
        var arrayDim = 0
        var idx = 0
        while (idx < trimmed.length && trimmed[idx] == '[') {
            arrayDim++
            idx++
        }
        val core = trimmed.substring(idx)
        val base = when {
            core == "V" -> "void"
            core == "Z" -> "boolean"
            core == "B" -> "byte"
            core == "S" -> "short"
            core == "C" -> "char"
            core == "I" -> "int"
            core == "J" -> "long"
            core == "F" -> "float"
            core == "D" -> "double"
            core.startsWith("L") && core.endsWith(";") -> {
                val full = core.substring(1, core.length - 1).replace('/', '.')
                full.substringAfterLast('.')
            }
            else -> core.replace('/', '.')
        }
        return base + "[]".repeat(arrayDim)
    }
}

@Composable
fun SmaliToJavaPreviewDialog(
    smaliFile: File?,
    smaliContent: String,
    onDismiss: () -> Unit,
    onSavedJavaFile: (File) -> Unit
) {
    val context = LocalContext.current
    val javaCode = remember(smaliContent) { SmaliToJavaDecompiler.decompile(smaliContent) }
    val highlightedJava = remember(javaCode) { SyntaxHighlighter.highlight(javaCode, false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${stringResource(R.string.decompile_to_java)} - ${smaliFile?.nameWithoutExtension ?: "Class"}.java",
                    color = DevDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp)
                        .background(Color(0xFFF8F9FA), RoundedCornerShape(4.dp))
                        .border(0.8.dp, Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = highlightedJava,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("java_source", javaCode))
                            Toast.makeText(context, context.getString(R.string.copied_to_clipboard, "Java"), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(stringResource(R.string.copy_java), color = DevBlue, fontWeight = FontWeight.Bold)
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close), color = DevBlue, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                val parent = smaliFile?.parentFile ?: File("/storage/emulated/0/MT2").apply { mkdirs() }
                                val baseName = smaliFile?.nameWithoutExtension ?: "DecompiledClass"
                                val outJava = File(parent, "$baseName.java")
                                runCatching {
                                    outJava.writeText(javaCode, Charsets.UTF_8)
                                }.onSuccess {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.java_saved_success, outJava.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                    onSavedJavaFile(outJava)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.save_as_java), color = DevBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. Code Formatter / Prettifier (XML, JSON, Java/Kotlin/Smali)
// ============================================================================

object CodeFormatter {
    fun formatCode(text: String, extension: String): String {
        val ext = extension.lowercase(Locale.ROOT)
        return when (ext) {
            "json" -> formatJson(text)
            "xml", "html", "svg" -> formatXml(text)
            else -> formatBracesAndIndents(text)
        }
    }

    private fun formatJson(raw: String): String {
        val sb = StringBuilder()
        var indent = 0
        var inQuotes = false
        var escaped = false
        for (ch in raw.trim()) {
            when {
                escaped -> {
                    sb.append(ch)
                    escaped = false
                }
                ch == '\\' -> {
                    sb.append(ch)
                    escaped = true
                }
                ch == '"' -> {
                    sb.append(ch)
                    inQuotes = !inQuotes
                }
                !inQuotes && (ch == '{' || ch == '[') -> {
                    sb.append(ch).append('\n')
                    indent++
                    sb.append("    ".repeat(indent))
                }
                !inQuotes && (ch == '}' || ch == ']') -> {
                    sb.append('\n')
                    indent = (indent - 1).coerceAtLeast(0)
                    sb.append("    ".repeat(indent)).append(ch)
                }
                !inQuotes && ch == ',' -> {
                    sb.append(ch).append('\n').append("    ".repeat(indent))
                }
                !inQuotes && ch == ':' -> {
                    sb.append(": ")
                }
                !inQuotes && ch.isWhitespace() -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun formatXml(raw: String): String {
        val lines = raw.replace(">\\s*<".toRegex(), ">\n<").lines()
        val sb = StringBuilder()
        var indent = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("</")) {
                indent = (indent - 1).coerceAtLeast(0)
            }
            sb.append("    ".repeat(indent)).appendLine(trimmed)
            if (trimmed.startsWith("<") &&
                !trimmed.startsWith("</") &&
                !trimmed.startsWith("<?") &&
                !trimmed.startsWith("<!") &&
                !trimmed.endsWith("/>") &&
                !trimmed.contains("</")
            ) {
                indent++
            }
        }
        return sb.toString().trimEnd()
    }

    private fun formatBracesAndIndents(raw: String): String {
        val sb = StringBuilder()
        var indent = 0
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                sb.appendLine()
                continue
            }
            if (trimmed.startsWith("}") || trimmed.startsWith(".end ")) {
                indent = (indent - 1).coerceAtLeast(0)
            }
            sb.append("    ".repeat(indent)).appendLine(trimmed)
            if (trimmed.endsWith("{") || trimmed.startsWith(".method ")) {
                indent++
            }
        }
        return sb.toString().trimEnd()
    }
}

// ============================================================================
// 3. Terminal Simulator Dialog (`ProcessBuilder("sh", "-c", command)`)
// ============================================================================

@Composable
fun TerminalSimulatorDialog(
    initialDirectory: File,
    onDismiss: () -> Unit
) {
    var commandInput by remember { mutableStateOf("") }
    var currentDir by remember {
        mutableStateOf(
            if (initialDirectory.exists() && initialDirectory.isDirectory) initialDirectory
            else File("/storage/emulated/0")
        )
    }
    val logs = remember {
        mutableStateListOf(
            "MT Manager Terminal Simulator (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT})",
            "Working directory: ${currentDir.absolutePath}",
            "Type any shell command or tap a chip below."
        )
    }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun executeCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty() || isRunning) return
        logs += "$ $trimmed"
        if (trimmed.startsWith("cd ")) {
            val targetPath = trimmed.removePrefix("cd ").trim()
            val nextDir = if (targetPath.startsWith("/")) File(targetPath) else File(currentDir, targetPath)
            if (nextDir.exists() && nextDir.isDirectory) {
                currentDir = nextDir.canonicalFile
                logs += "Changed directory to ${currentDir.absolutePath}"
            } else {
                logs += "cd: no such directory: $targetPath"
            }
            return
        }
        isRunning = true
        scope.launch {
            val output = withContext(Dispatchers.IO) {
                runCatching {
                    val pb = ProcessBuilder("sh", "-c", trimmed)
                    if (currentDir.exists() && currentDir.isDirectory) {
                        pb.directory(currentDir)
                    }
                    pb.redirectErrorStream(true)
                    val proc = pb.start()
                    val text = proc.inputStream.bufferedReader().readText()
                    val code = proc.waitFor()
                    buildString {
                        if (text.isNotBlank()) appendLine(text.trimEnd())
                        append("[Exit code: $code]")
                    }
                }.getOrElse { err ->
                    "Error: ${err.message}"
                }
            }
            logs += output
            isRunning = false
        }
    }

    val quickChips = listOf("ls -la", "pwd", "df -h", "id", "uname -a", "getprop ro.build.version.release", "ps")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF1A1A1A),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tool_terminal_simulator),
                        color = Color(0xFF4CAF50),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(onClick = { logs.clear() }) {
                        Text(stringResource(R.string.terminal_clear), color = Color(0xFF90CAF9), fontSize = 12.sp)
                    }
                }

                // Quick command chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickChips.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2D2D2D),
                            modifier = Modifier.clickable {
                                commandInput = chip
                                executeCommand(chip)
                            }
                        ) {
                            Text(
                                text = chip,
                                color = Color(0xFFE0E0E0),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Console output box (#121212)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(Color(0xFF121212), RoundedCornerShape(4.dp))
                        .border(0.7.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(logs) { _, entry ->
                        val isCmd = entry.startsWith("$ ")
                        Text(
                            text = entry,
                            color = if (isCmd) Color(0xFF81C784) else Color(0xFFEEEEEE),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Command input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF262626), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$ ", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (commandInput.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.terminal_command_hint),
                                        color = Color(0xFF757575),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    IconButton(
                        onClick = {
                            val cmd = commandInput
                            commandInput = ""
                            executeCommand(cmd)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.terminal_run), tint = Color(0xFF4CAF50))
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. Smali Instructions Interactive Reference Dictionary
// ============================================================================

data class SmaliOpcodeEntry(
    val hex: String,
    val mnemonic: String,
    val syntax: String,
    val description: String,
    val example: String
)

private val SMALI_OPCODES_DICTIONARY = listOf(
    SmaliOpcodeEntry("0x00", "nop", "nop", "No operation.", "nop"),
    SmaliOpcodeEntry("0x01", "move", "move vA, vB", "Moves the content of one non-object register to another.", "move v0, v1"),
    SmaliOpcodeEntry("0x07", "move-object", "move-object vA, vB", "Moves an object reference from vB to vA.", "move-object v0, p1"),
    SmaliOpcodeEntry("0x0a", "move-result", "move-result vAA", "Moves the single-word result of the preceding method invocation into vAA.", "move-result v0"),
    SmaliOpcodeEntry("0x0c", "move-result-object", "move-result-object vAA", "Moves the object result of the preceding method call into vAA.", "move-result-object v1"),
    SmaliOpcodeEntry("0x0e", "return-void", "return-void", "Returns from a void method.", "return-void"),
    SmaliOpcodeEntry("0x0f", "return", "return vAA", "Returns a 32-bit primitive value from a method (e.g. boolean 1 = true, 0 = false).", "const/4 v0, 0x1\nreturn v0"),
    SmaliOpcodeEntry("0x11", "return-object", "return-object vAA", "Returns an object reference in vAA.", "return-object v0"),
    SmaliOpcodeEntry("0x12", "const/4", "const/4 vA, #+B", "Moves a 4-bit signed integer (-8..7) into vA.", "const/4 v0, 0x1"),
    SmaliOpcodeEntry("0x13", "const/16", "const/16 vAA, #+BBBB", "Moves a 16-bit signed integer into vAA.", "const/16 v0, 0x3e8"),
    SmaliOpcodeEntry("0x14", "const", "const vAA, #+BBBBBBBB", "Moves a 32-bit constant integer/float into vAA.", "const v0, 0x7f080001"),
    SmaliOpcodeEntry("0x1a", "const-string", "const-string vAA, \"string\"", "Loads a string reference from the string pool into vAA.", "const-string v0, \"MT Manager\""),
    SmaliOpcodeEntry("0x1c", "const-class", "const-class vAA, LType;", "Loads a Class reference into vAA.", "const-class v0, Ljava/lang/String;"),
    SmaliOpcodeEntry("0x1f", "check-cast", "check-cast vAA, LType;", "Throws ClassCastException if object in vAA cannot be cast to Type.", "check-cast v0, Landroid/widget/TextView;"),
    SmaliOpcodeEntry("0x22", "new-instance", "new-instance vAA, LType;", "Allocates a new instance of the specified class.", "new-instance v0, Ljava/lang/StringBuilder;"),
    SmaliOpcodeEntry("0x28", "goto", "goto :label", "Unconditionally jumps to target label.", "goto :cond_0"),
    SmaliOpcodeEntry("0x32", "if-eq", "if-eq vA, vB, :label", "Branches to :label if vA == vB.", "if-eq v0, v1, :cond_equal"),
    SmaliOpcodeEntry("0x33", "if-ne", "if-ne vA, vB, :label", "Branches to :label if vA != vB.", "if-ne v0, v1, :cond_diff"),
    SmaliOpcodeEntry("0x38", "if-eqz", "if-eqz vAA, :label", "Branches to :label if vAA == 0 (or null).", "if-eqz v0, :cond_zero"),
    SmaliOpcodeEntry("0x39", "if-nez", "if-nez vAA, :label", "Branches to :label if vAA != 0 (or non-null).", "if-nez v0, :cond_nonzero"),
    SmaliOpcodeEntry("0x52", "iget", "iget vA, vB, LClass;->field:I", "Reads an int/primitive instance field from object vB into vA.", "iget v0, p0, Lcom/app/Main;->count:I"),
    SmaliOpcodeEntry("0x54", "iget-object", "iget-object vA, vB, LClass;->field:LType;", "Reads an object instance field from vB into vA.", "iget-object v0, p0, Lcom/app/Main;->title:Ljava/lang/String;"),
    SmaliOpcodeEntry("0x59", "iput", "iput vA, vB, LClass;->field:I", "Stores primitive value vA into instance field on object vB.", "iput v0, p0, Lcom/app/Main;->count:I"),
    SmaliOpcodeEntry("0x5b", "iput-object", "iput-object vA, vB, LClass;->field:LType;", "Stores object reference vA into instance field on object vB.", "iput-object v0, p0, Lcom/app/Main;->title:Ljava/lang/String;"),
    SmaliOpcodeEntry("0x60", "sget", "sget vAA, LClass;->field:I", "Reads a static primitive field into vAA.", "sget v0, Landroid/os/Build\$VERSION;->SDK_INT:I"),
    SmaliOpcodeEntry("0x62", "sget-object", "sget-object vAA, LClass;->field:LType;", "Reads a static object field into vAA.", "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;"),
    SmaliOpcodeEntry("0x67", "sput", "sput vAA, LClass;->field:I", "Stores vAA into a static primitive field.", "sput v0, Lcom/app/Config;->DEBUG:Z"),
    SmaliOpcodeEntry("0x6e", "invoke-virtual", "invoke-virtual {vC, ...}, LClass;->method(...)R", "Invokes a virtual (non-private, non-static) method on receiver vC.", "invoke-virtual {v0}, Ljava/lang/String;->length()I"),
    SmaliOpcodeEntry("0x6f", "invoke-super", "invoke-super {p0, ...}, LSuper;->method(...)R", "Invokes the superclass implementation of a virtual method.", "invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V"),
    SmaliOpcodeEntry("0x70", "invoke-direct", "invoke-direct {vC, ...}, LClass;-><init>(...)V", "Invokes a constructor (<init>) or private method.", "invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V"),
    SmaliOpcodeEntry("0x71", "invoke-static", "invoke-static {...}, LClass;->method(...)R", "Invokes a static method.", "invoke-static {v0}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;"),
    SmaliOpcodeEntry("0x72", "invoke-interface", "invoke-interface {vC, ...}, LInterface;->method(...)R", "Invokes an interface method.", "invoke-interface {v0}, Ljava/util/List;->size()I")
)

@Composable
fun SmaliReferenceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) SMALI_OPCODES_DICTIONARY
        else SMALI_OPCODES_DICTIONARY.filter {
            it.mnemonic.contains(q, true) ||
                it.hex.contains(q, true) ||
                it.description.contains(q, true) ||
                it.syntax.contains(q, true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 410.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.tool_smali_instructions),
                    color = DevDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3F3F3), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DevGray, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = DevDark, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(DevBlue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(stringResource(R.string.search_hint), color = DevGray, fontSize = 13.sp)
                                }
                                inner()
                            }
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.hex + it.mnemonic }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8F9FA), RoundedCornerShape(4.dp))
                                .border(0.6.dp, Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("smali_opcode", item.example))
                                    Toast.makeText(context, context.getString(R.string.copied_to_clipboard, item.mnemonic), Toast.LENGTH_SHORT).show()
                                }
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.mnemonic} (${item.hex})",
                                    color = Color(0xFF0033B3),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = item.syntax,
                                    color = Color(0xFF067D17),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(item.description, color = DevDark, fontSize = 12.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = item.example,
                                color = Color(0xFF555555),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = DevBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. Screen Color Picker & Smali/Android Color Converter
// ============================================================================

@Composable
fun ColorPickerConverterDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var alpha by remember { mutableFloatStateOf(255f) }
    var red by remember { mutableFloatStateOf(25f) }
    var green by remember { mutableFloatStateOf(118f) }
    var blue by remember { mutableFloatStateOf(210f) }

    val aInt = alpha.toInt().coerceIn(0, 255)
    val rInt = red.toInt().coerceIn(0, 255)
    val gInt = green.toInt().coerceIn(0, 255)
    val bInt = blue.toInt().coerceIn(0, 255)

    val argbLong = ((aInt.toLong() and 0xFF) shl 24) or
        ((rInt.toLong() and 0xFF) shl 16) or
        ((gInt.toLong() and 0xFF) shl 8) or
        (bInt.toLong() and 0xFF)
    val signedInt = argbLong.toInt()

    val hexString = String.format(Locale.US, "#%02X%02X%02X%02X", aInt, rInt, gInt, bInt)
    val smaliHex = if (signedInt < 0) {
        "-0x${(-signedInt.toLong()).toString(16)}"
    } else {
        "0x${signedInt.toString(16)}"
    }
    val smaliSummary = "$smaliHex ($signedInt)"
    val rgbSummary = "argb($aInt, $rInt, $gInt, $bInt)"

    var hexInput by remember(hexString) { mutableStateOf(hexString) }

    fun copyValue(value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("color_val", value))
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard, value), Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.tool_color_picker),
                    color = DevDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Color Preview Swatch
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(rInt, gInt, bInt, aInt))
                        .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hexString,
                        color = if (rInt + gInt + bInt > 380) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Hex input
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.color_hex_label), color = DevGray, fontSize = 12.sp, modifier = Modifier.width(120.dp))
                    BasicTextField(
                        value = hexInput,
                        onValueChange = { next ->
                            hexInput = next
                            val clean = next.trim().removePrefix("#").removePrefix("0x")
                            if (clean.length == 6 || clean.length == 8) {
                                runCatching {
                                    val parsed = clean.toLong(16)
                                    if (clean.length == 8) {
                                        alpha = ((parsed shr 24) and 0xFF).toFloat()
                                        red = ((parsed shr 16) and 0xFF).toFloat()
                                        green = ((parsed shr 8) and 0xFF).toFloat()
                                        blue = (parsed and 0xFF).toFloat()
                                    } else {
                                        alpha = 255f
                                        red = ((parsed shr 16) and 0xFF).toFloat()
                                        green = ((parsed shr 8) and 0xFF).toFloat()
                                        blue = (parsed and 0xFF).toFloat()
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = DevDark, fontSize = 13.5.sp, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { copyValue(hexString) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevBlue, modifier = Modifier.size(16.dp))
                    }
                }

                // Smali Int value
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.color_smali_label), color = DevGray, fontSize = 12.sp, modifier = Modifier.width(120.dp))
                    Text(smaliSummary, color = DevDark, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    IconButton(onClick = { copyValue(smaliHex) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevBlue, modifier = Modifier.size(16.dp))
                    }
                }

                // ARGB summary
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.color_rgb_label), color = DevGray, fontSize = 12.sp, modifier = Modifier.width(120.dp))
                    Text(rgbSummary, color = DevDark, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    IconButton(onClick = { copyValue(rgbSummary) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevBlue, modifier = Modifier.size(16.dp))
                    }
                }

                // Sliders
                ColorChannelSlider("A", aInt, alpha, Color(0xFF616161)) { alpha = it }
                ColorChannelSlider("R", rInt, red, Color(0xFFE53935)) { red = it }
                ColorChannelSlider("G", gInt, green, Color(0xFF43A047)) { green = it }
                ColorChannelSlider("B", bInt, blue, Color(0xFF1E88E5)) { blue = it }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = DevBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    intVal: Int,
    value: Float,
    tint: Color,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$label:$intVal", color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.width(48.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f).height(26.dp)
        )
    }
}

// ============================================================================
// 6. Comparison Result / Diff Viewer Dialog
// ============================================================================

enum class DiffLineStatus {
    SAME,
    ADDED,
    REMOVED
}

data class DiffLineEntry(
    val leftLineNum: Int?,
    val rightLineNum: Int?,
    val prefix: String,
    val content: String,
    val status: DiffLineStatus
)

@Composable
fun DiffViewerDialog(
    leftFile: File,
    rightFile: File,
    onDismiss: () -> Unit
) {
    var diffEntries by remember { mutableStateOf<List<DiffLineEntry>>(emptyList()) }
    var addedCount by remember { mutableStateOf(0) }
    var removedCount by remember { mutableStateOf(0) }

    LaunchedEffect(leftFile.absolutePath, rightFile.absolutePath) {
        withContext(Dispatchers.Default) {
            val leftLines = runCatching { leftFile.readLines() }.getOrDefault(emptyList())
            val rightLines = runCatching { rightFile.readLines() }.getOrDefault(emptyList())
            val maxLines = maxOf(leftLines.size, rightLines.size).coerceAtMost(800)
            val result = ArrayList<DiffLineEntry>()
            var add = 0
            var rem = 0
            for (i in 0 until maxLines) {
                val l = leftLines.getOrNull(i)
                val r = rightLines.getOrNull(i)
                when {
                    l == r && l != null -> {
                        result += DiffLineEntry(i + 1, i + 1, " ", l, DiffLineStatus.SAME)
                    }
                    l != null && r == null -> {
                        rem++
                        result += DiffLineEntry(i + 1, null, "-", l, DiffLineStatus.REMOVED)
                    }
                    l == null && r != null -> {
                        add++
                        result += DiffLineEntry(null, i + 1, "+", r, DiffLineStatus.ADDED)
                    }
                    l != null && r != null -> {
                        rem++
                        add++
                        result += DiffLineEntry(i + 1, null, "-", l, DiffLineStatus.REMOVED)
                        result += DiffLineEntry(null, i + 1, "+", r, DiffLineStatus.ADDED)
                    }
                }
            }
            addedCount = add
            removedCount = rem
            diffEntries = result
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.comparison_result_title),
                    color = DevDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${leftFile.name}  vs  ${rightFile.name}   (+$addedCount / -$removedCount)",
                    color = DevGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .border(0.7.dp, Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                ) {
                    itemsIndexed(diffEntries) { _, item ->
                        val bg = when (item.status) {
                            DiffLineStatus.ADDED -> Color(0xFFE8F5E9)
                            DiffLineStatus.REMOVED -> Color(0xFFFFEBEE)
                            DiffLineStatus.SAME -> Color.White
                        }
                        val fg = when (item.status) {
                            DiffLineStatus.ADDED -> Color(0xFF1B5E20)
                            DiffLineStatus.REMOVED -> Color(0xFFB71C1C)
                            DiffLineStatus.SAME -> Color(0xFF333333)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val numStr = "${item.leftLineNum ?: ""}:${item.rightLineNum ?: ""}".padEnd(7)
                            Text(
                                text = "$numStr ${item.prefix} ${item.content}",
                                color = fg,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = DevBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 7. Drawer Utility Tool Inspector Dialog (Activity Record, Remote Manager, etc.)
// ============================================================================

@Composable
fun DrawerUtilityToolDialog(
    title: String,
    toolType: String,
    cacheDir: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lines = remember(toolType) {
        when (toolType) {
            "activity_record" -> {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
                val availMb = memInfo.availMem / (1024 * 1024)
                val totalMb = memInfo.totalMem / (1024 * 1024)
                listOf(
                    "Foreground Activity" to "${context.packageName}.MainActivity",
                    "Process ID (PID) / UID" to "${Process.myPid()} / ${Process.myUid()}",
                    "Device Model" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                    "Android SDK / ABI" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
                    "System Memory" to "${availMb}MB available / ${totalMb}MB total"
                )
            }
            "remote_manager" -> {
                val ip = runCatching {
                    Collections.list(NetworkInterface.getNetworkInterfaces())
                        .asSequence()
                        .flatMap { Collections.list(it.inetAddresses).asSequence() }
                        .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
                        ?.hostAddress
                }.getOrNull() ?: "127.0.0.1"
                listOf(
                    "Local LAN IP" to ip,
                    "FTP Server Endpoint" to "ftp://$ip:2121/",
                    "Root Path" to "/storage/emulated/0",
                    "Authentication" to "Anonymous (Read/Write local session)"
                )
            }
            "recycle_bin" -> {
                val tempFiles = cacheDir.listFiles()?.toList().orEmpty()
                listOf(
                    "Recycle / Temp Directory" to cacheDir.absolutePath,
                    "Cached Session Items" to "${tempFiles.size} item(s)",
                    "Total Cached Size" to "${tempFiles.sumOf { it.length() } / 1024} KB"
                )
            }
            else -> {
                listOf(
                    "Module" to title,
                    "Workspace Directory" to "/storage/emulated/0/MT2",
                    "Engine Status" to "Active (Built-in Native Engine v13)"
                )
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = DevDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(thickness = 0.6.dp, color = Color(0xFFE0E0E0))
                lines.forEach { (k, v) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(k, color = DevGray, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Text(v, color = DevDark, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok), color = DevBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
