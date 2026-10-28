package com.example.mtcore.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

enum class NavSymbolKind {
    CLASS,
    FIELD,
    METHOD
}

data class NavSymbolItem(
    val kind: NavSymbolKind,
    val title: String,
    val subtitle: String,
    val startLine: Int, // 1-indexed
    val endLine: Int,   // 1-indexed
    val charOffset: Int
)

object SyntaxHighlighter {
    private val KeywordColor = Color(0xFFA31515)   // Rich Brownish-Red (#A31515)
    private val CommentColor = Color(0xFF808080)   // Gray (#808080)
    private val StringColor = Color(0xFF2E7D32)    // Green (#2E7D32)
    private val NumberColor = Color(0xFF1565C0)    // Blue (#1565C0)
    private val RegisterColor = Color(0xFF6A1B9A)  // Distinct Purple for Smali registers (v0, p0)

    private val KEYWORDS = setOf(
        // Smali directives & modifiers
        ".class", ".super", ".source", ".implements", ".annotation", ".end", ".field",
        ".method", ".registers", ".locals", ".param", ".line", ".prologue",
        "public", "private", "protected", "static", "final", "abstract", "interface",
        "synthetic", "bridge", "constructor", "synchronized", "native", "enum", "annotation",
        "system", "value",
        // Common Smali opcodes
        "return-void", "return", "return-object", "return-wide",
        "const/4", "const/16", "const", "const-wide", "const-string", "const-class",
        "move", "move-wide", "move-object", "move-result", "move-result-object", "move-exception",
        "invoke-direct", "invoke-virtual", "invoke-static", "invoke-interface", "invoke-super",
        "iget", "iget-object", "iget-boolean", "iget-wide",
        "iput", "iput-object", "iput-boolean", "iput-wide",
        "sget", "sget-object", "sput", "sput-object",
        "new-instance", "new-array", "check-cast", "instance-of", "throw", "goto",
        "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le", "if-eqz", "if-nez",
        // Kotlin / Java / Script keywords
        "package", "import", "class", "object", "fun", "val", "var", "if", "else", "when",
        "for", "while", "do", "try", "catch", "finally", "true", "false", "null", "void",
        "int", "long", "boolean", "float", "double", "char", "byte", "short", "override",
        "suspend", "data", "sealed", "companion", "internal", "open", "extends", "implements"
    )

    private val REGISTER_REGEX = Regex("\\b([vp]\\d{1,2})\\b")
    private val HEX_NUMBER_REGEX = Regex("\\b(0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b")
    private val WORD_TOKEN_REGEX = Regex("(\\.[a-zA-Z_][a-zA-Z0-9_-]*|\\b[a-zA-Z_][a-zA-Z0-9_/-]*\\b)")
    private val XML_TAG_REGEX = Regex("(</?[a-zA-Z0-9_:-]+|/?>|\\b[a-zA-Z0-9_:-]+(?=\\s*=))")

    fun createVisualTransformation(fileName: String?): VisualTransformation {
        val isXml = fileName?.endsWith(".xml", ignoreCase = true) == true
        return VisualTransformation { text ->
            TransformedText(
                text = highlight(text.text, isXml),
                offsetMapping = OffsetMapping.Identity
            )
        }
    }

    fun highlight(source: String, isXml: Boolean = false): AnnotatedString {
        if (source.isEmpty()) return AnnotatedString("")
        val builder = AnnotatedString.Builder(source)

        // Process line by line for fast, bounded highlighting
        var lineStart = 0
        val length = source.length
        while (lineStart < length) {
            val lineEnd = source.indexOf('\n', lineStart).let { if (it == -1) length else it }
            highlightLine(builder, source, lineStart, lineEnd, isXml)
            lineStart = lineEnd + 1
        }
        return builder.toAnnotatedString()
    }

    private fun highlightLine(
        builder: AnnotatedString.Builder,
        source: String,
        start: Int,
        end: Int,
        isXml: Boolean
    ) {
        if (start >= end) return
        val line = source.substring(start, end)

        // 1. Check XML comments
        if (isXml) {
            val commentStart = line.indexOf("<!--")
            if (commentStart >= 0) {
                builder.addStyle(SpanStyle(color = CommentColor), start + commentStart, end)
                return
            }
            for (match in XML_TAG_REGEX.findAll(line)) {
                builder.addStyle(
                    SpanStyle(color = KeywordColor),
                    start + match.range.first,
                    start + match.range.last + 1
                )
            }
            highlightStrings(builder, line, start)
            return
        }

        // 2. Check # or // comments outside strings
        val commentIdx = findCommentStart(line)
        val codePartEnd = if (commentIdx >= 0) commentIdx else line.length
        val codeLine = line.substring(0, codePartEnd)

        // 3. Keywords & Directives
        for (match in WORD_TOKEN_REGEX.findAll(codeLine)) {
            val token = match.value
            if (token in KEYWORDS) {
                builder.addStyle(
                    SpanStyle(
                        color = KeywordColor,
                        fontWeight = if (token.startsWith('.')) FontWeight.Medium else FontWeight.Normal
                    ),
                    start + match.range.first,
                    start + match.range.last + 1
                )
            }
        }

        // 4. Numbers & Hex constants (0x...)
        for (match in HEX_NUMBER_REGEX.findAll(codeLine)) {
            builder.addStyle(
                SpanStyle(color = NumberColor),
                start + match.range.first,
                start + match.range.last + 1
            )
        }

        // 5. Smali registers (v0..v15, p0..p15)
        for (match in REGISTER_REGEX.findAll(codeLine)) {
            builder.addStyle(
                SpanStyle(color = RegisterColor, fontWeight = FontWeight.Medium),
                start + match.range.first,
                start + match.range.last + 1
            )
        }

        // 6. String literals ("...")
        highlightStrings(builder, codeLine, start)

        // 7. Trailing comment (# ... or // ...)
        if (commentIdx >= 0) {
            builder.addStyle(
                SpanStyle(color = CommentColor),
                start + commentIdx,
                end
            )
        }
    }

    private fun highlightStrings(builder: AnnotatedString.Builder, line: String, lineOffset: Int) {
        var i = 0
        while (i < line.length) {
            if (line[i] == '"') {
                val strStart = i
                i++
                while (i < line.length) {
                    if (line[i] == '\\') {
                        i += 2
                        continue
                    }
                    if (line[i] == '"') {
                        i++
                        break
                    }
                    i++
                }
                builder.addStyle(
                    SpanStyle(color = StringColor),
                    lineOffset + strStart,
                    lineOffset + i.coerceAtMost(line.length)
                )
            } else {
                i++
            }
        }
    }

    private fun findCommentStart(line: String): Int {
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\\' && inQuotes) {
                i += 2
                continue
            }
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (ch == '#') return i
                if (ch == '/' && i + 1 < line.length && line[i + 1] == '/') return i
            }
            i++
        }
        return -1
    }

    fun parseNavigationSymbols(text: String, fileName: String?): List<NavSymbolItem> {
        if (text.isEmpty()) return emptyList()
        val symbols = mutableListOf<NavSymbolItem>()
        val lines = text.split('\n')

        var currentOffset = 0
        var activeMethodTitle: String? = null
        var activeMethodSubtitle: String = ""
        var activeMethodStartLine = 1
        var activeMethodOffset = 0

        for (index in lines.indices) {
            val lineNum = index + 1
            val rawLine = lines[index]
            val trimmed = rawLine.trim()

            when {
                // Smali .class
                trimmed.startsWith(".class ") -> {
                    val desc = trimmed.substringAfterLast(' ').removePrefix("L").removeSuffix(";")
                    val simple = desc.substringAfterLast('/')
                    symbols += NavSymbolItem(
                        kind = NavSymbolKind.CLASS,
                        title = simple,
                        subtitle = desc.replace('/', '.'),
                        startLine = lineNum,
                        endLine = lines.size,
                        charOffset = currentOffset
                    )
                }
                // Smali .field
                trimmed.startsWith(".field ") -> {
                    val sig = trimmed.substringAfterLast(' ')
                    val fName = sig.substringBefore(':')
                    val fType = sig.substringAfter(':', "")
                    symbols += NavSymbolItem(
                        kind = NavSymbolKind.FIELD,
                        title = fName,
                        subtitle = fType,
                        startLine = lineNum,
                        endLine = lineNum,
                        charOffset = currentOffset
                    )
                }
                // Smali .method
                trimmed.startsWith(".method ") -> {
                    val sig = trimmed.substringAfterLast(' ')
                    val mName = sig.substringBefore('(')
                    val paramsAndRet = "(" + sig.substringAfter('(', "")
                    activeMethodTitle = "$mName()"
                    activeMethodSubtitle = paramsAndRet
                    activeMethodStartLine = lineNum
                    activeMethodOffset = currentOffset
                }
                trimmed.startsWith(".end method") && activeMethodTitle != null -> {
                    symbols += NavSymbolItem(
                        kind = NavSymbolKind.METHOD,
                        title = activeMethodTitle!!,
                        subtitle = activeMethodSubtitle,
                        startLine = activeMethodStartLine,
                        endLine = lineNum,
                        charOffset = activeMethodOffset
                    )
                    activeMethodTitle = null
                }
                // Kotlin / Java class / interface / object
                trimmed.matches(Regex("^(public |private |internal |abstract |final |data |sealed |open )*(class|interface|object)\\s+[A-Za-z0-9_]+.*")) -> {
                    val name = trimmed.replace(Regex("^.*\\b(class|interface|object)\\s+"), "")
                        .substringBefore('(')
                        .substringBefore(':')
                        .substringBefore('{')
                        .trim()
                    if (name.isNotEmpty()) {
                        symbols += NavSymbolItem(
                            kind = NavSymbolKind.CLASS,
                            title = name,
                            subtitle = fileName.orEmpty(),
                            startLine = lineNum,
                            endLine = lines.size,
                            charOffset = currentOffset
                        )
                    }
                }
                // Kotlin / Java function
                trimmed.matches(Regex("^(public |private |protected |internal |override |suspend |inline |static |final )*fun\\s+[A-Za-z0-9_<>]+\\s*\\(.*")) -> {
                    val afterFun = trimmed.substringAfter("fun ").trim()
                    val mName = afterFun.substringBefore('(').trim()
                    val params = "(" + afterFun.substringAfter('(', "").substringBefore('{').trim()
                    symbols += NavSymbolItem(
                        kind = NavSymbolKind.METHOD,
                        title = "$mName()",
                        subtitle = params,
                        startLine = lineNum,
                        endLine = (lineNum + 12).coerceAtMost(lines.size),
                        charOffset = currentOffset
                    )
                }
                // Kotlin val / var property
                trimmed.matches(Regex("^(private |public |internal |const |lateinit )*(val|var)\\s+[A-Za-z0-9_]+.*")) -> {
                    val afterVal = trimmed.replace(Regex("^.*\\b(val|var)\\s+"), "")
                    val fName = afterVal.substringBefore(':').substringBefore('=').trim()
                    if (fName.isNotEmpty() && !rawLine.startsWith("        ")) {
                        symbols += NavSymbolItem(
                            kind = NavSymbolKind.FIELD,
                            title = fName,
                            subtitle = afterVal.substringAfter(':', "").substringBefore('=').trim(),
                            startLine = lineNum,
                            endLine = lineNum,
                            charOffset = currentOffset
                        )
                    }
                }
                // XML key tags (activity, service, receiver, provider, uses-permission, application)
                (fileName?.endsWith(".xml", true) == true) &&
                    trimmed.matches(Regex("^<(manifest|application|activity|service|receiver|provider|uses-permission|uses-sdk)\\b.*")) -> {
                    val tag = trimmed.removePrefix("<").substringBefore(' ').substringBefore('>')
                    // Look ahead up to 6 lines for android:name="..."
                    val snippet = lines.subList(index, (index + 6).coerceAtMost(lines.size)).joinToString(" ")
                    val attrName = Regex("android:name=\"([^\"]+)\"").find(snippet)?.groupValues?.getOrNull(1).orEmpty()
                    symbols += NavSymbolItem(
                        kind = if (tag == "manifest" || tag == "application") NavSymbolKind.CLASS else NavSymbolKind.METHOD,
                        title = if (attrName.isNotEmpty()) "$tag: ${attrName.substringAfterLast('.')}" else "<$tag>",
                        subtitle = attrName.ifEmpty { "Line $lineNum" },
                        startLine = lineNum,
                        endLine = lineNum,
                        charOffset = currentOffset
                    )
                }
            }

            currentOffset += rawLine.length + 1
        }

        if (activeMethodTitle != null) {
            symbols += NavSymbolItem(
                kind = NavSymbolKind.METHOD,
                title = activeMethodTitle!!,
                subtitle = activeMethodSubtitle,
                startLine = activeMethodStartLine,
                endLine = lines.size,
                charOffset = activeMethodOffset
            )
        }

        return symbols
    }
}
