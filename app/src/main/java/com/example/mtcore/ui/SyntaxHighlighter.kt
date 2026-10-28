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

enum class SyntaxLanguage(val label: String) {
    AUTO("Auto"),
    KOTLIN("Kotlin"),
    JAVA("Java"),
    XML("XML"),
    SMALI("Smali"),
    JSON("JSON"),
    SHELL("Shell"),
    NONE("None")
}

object SyntaxHighlighter {
    private val KeywordColor = Color(0xFFA31515)   // Rich Brownish-Red (#A31515)
    private val AttributeColor = Color(0xFF7B1FA2) // Distinct Purple/Red for XML attributes (#7B1FA2)
    private val CommentColor = Color(0xFF808080)   // Gray (#808080)
    private val StringColor = Color(0xFF2E7D32)    // Clear Green (#2E7D32)
    private val NumberColor = Color(0xFF1565C0)    // Blue (#1565C0)
    private val RegisterColor = Color(0xFF6A1B9A)  // Purple for Smali registers (v0, p0) (#6A1B9A)
    private val SymbolColor = Color(0xFF111111)    // Black (#111111)
    private val SearchMatchBg = Color(0xFFFFF59D)  // Soft Yellow search highlight
    private val ActiveSearchMatchBg = Color(0xFFFFB74D) // Orange active search match highlight

    private val KEYWORDS = setOf(
        // Smali directives & modifiers
        ".class", ".super", ".source", ".implements", ".annotation", ".end", ".field",
        ".method", ".registers", ".locals", ".param", ".line", ".prologue", ".catch", ".catchall",
        "method", "subannotation", "sparse-switch", "packed-switch", "array-data",
        "public", "private", "protected", "static", "final", "abstract", "interface",
        "synthetic", "bridge", "constructor", "synchronized", "native", "enum", "annotation",
        "system", "value", "declared-synchronized", "varargs", "transient", "volatile",
        // Common Smali opcodes
        "return-void", "return", "return-object", "return-wide",
        "const/4", "const/16", "const/high16", "const", "const-wide", "const-wide/16", "const-wide/32",
        "const-string", "const-string/jumbo", "const-class",
        "move", "move-wide", "move-object", "move-result", "move-result-object", "move-result-wide", "move-exception",
        "invoke-direct", "invoke-virtual", "invoke-static", "invoke-interface", "invoke-super",
        "invoke-direct/range", "invoke-virtual/range", "invoke-static/range", "invoke-interface/range",
        "iget", "iget-object", "iget-boolean", "iget-wide", "iget-byte", "iget-char", "iget-short",
        "iput", "iput-object", "iput-boolean", "iput-wide", "iput-byte", "iput-char", "iput-short",
        "sget", "sget-object", "sget-boolean", "sget-wide", "sput", "sput-object", "sput-boolean", "sput-wide",
        "new-instance", "new-array", "filled-new-array", "check-cast", "instance-of", "throw", "goto", "goto/16",
        "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le", "if-eqz", "if-nez", "if-ltz", "if-gez", "if-gtz", "if-lez",
        "nop", "array-length", "monitor-enter", "monitor-exit",
        // Kotlin / Java / Script keywords
        "package", "import", "class", "object", "fun", "val", "var", "if", "else", "when",
        "for", "while", "do", "try", "catch", "finally", "true", "false", "null", "void",
        "int", "long", "boolean", "float", "double", "char", "byte", "short", "override",
        "suspend", "data", "sealed", "companion", "internal", "open", "extends", "implements",
        "new", "this", "super", "switch", "case", "default", "break", "continue", "throws",
        // Shell keywords
        "echo", "export", "fi", "then", "elif", "esac", "done", "in", "function", "local"
    )

    fun createVisualTransformation(fileName: String?): VisualTransformation {
        return VisualTransformation { text ->
            TransformedText(
                text = highlight(text.text, fileName),
                offsetMapping = OffsetMapping.Identity
            )
        }
    }

    /**
     * Zero-lag VisualTransformation that reuses the pre-computed [preHighlighted] AnnotatedString
     * when the text matches, avoiding expensive regex recomputation on the UI thread during scrolling/typing.
     */
    fun fromPreHighlighted(
        preHighlighted: AnnotatedString,
        fileName: String?,
        language: SyntaxLanguage = SyntaxLanguage.AUTO
    ): VisualTransformation {
        return VisualTransformation { text ->
            val raw = text.text
            val styled = if (preHighlighted.text == raw) {
                preHighlighted
            } else if (raw.length > 80_000) {
                // Fast character-level pass for very large edits before background coroutine finishes
                highlight(raw, fileName, language, emptyList(), -1, smoothMode = true)
            } else {
                highlight(raw, fileName, language, emptyList(), -1, smoothMode = true)
            }
            TransformedText(
                text = styled,
                offsetMapping = OffsetMapping.Identity
            )
        }
    }

    fun detectLanguage(source: String, fileName: String?, explicit: SyntaxLanguage): SyntaxLanguage {
        if (explicit != SyntaxLanguage.AUTO) return explicit
        val lower = fileName?.lowercase().orEmpty()
        val trimmed = source.trimStart()
        return when {
            lower.endsWith(".xml") || trimmed.startsWith("<?xml") || trimmed.startsWith("<manifest") || trimmed.startsWith("<resources") -> SyntaxLanguage.XML
            lower.endsWith(".smali") || trimmed.startsWith(".class ") -> SyntaxLanguage.SMALI
            lower.endsWith(".kt") || lower.endsWith(".kts") -> SyntaxLanguage.KOTLIN
            lower.endsWith(".java") -> SyntaxLanguage.JAVA
            lower.endsWith(".json") || (trimmed.startsWith("{") && trimmed.endsWith("}")) -> SyntaxLanguage.JSON
            lower.endsWith(".sh") || trimmed.startsWith("#!/") -> SyntaxLanguage.SHELL
            else -> SyntaxLanguage.KOTLIN
        }
    }

    fun highlight(source: String, fileName: String?): AnnotatedString {
        return highlight(
            source = source,
            fileName = fileName,
            language = SyntaxLanguage.AUTO,
            searchRanges = emptyList(),
            activeSearchIndex = -1,
            smoothMode = true
        )
    }

    fun highlight(source: String, isXml: Boolean = false): AnnotatedString {
        return highlight(
            source = source,
            fileName = if (isXml) "file.xml" else "file.kt",
            language = if (isXml) SyntaxLanguage.XML else SyntaxLanguage.AUTO,
            searchRanges = emptyList(),
            activeSearchIndex = -1,
            smoothMode = true
        )
    }

    fun highlight(
        source: String,
        fileName: String?,
        language: SyntaxLanguage = SyntaxLanguage.AUTO,
        searchRanges: List<IntRange> = emptyList(),
        activeSearchIndex: Int = -1,
        smoothMode: Boolean = true
    ): AnnotatedString {
        if (source.isEmpty()) return AnnotatedString("")
        val resolvedLang = detectLanguage(source, fileName, language)
        if (resolvedLang == SyntaxLanguage.NONE) {
            val builder = AnnotatedString.Builder(source)
            applySearchHighlights(builder, source.length, searchRanges, activeSearchIndex)
            return builder.toAnnotatedString()
        }

        val builder = AnnotatedString.Builder(source)
        val isXml = resolvedLang == SyntaxLanguage.XML
        val length = source.length
        // Smooth mode caps detailed line highlighting past 250,000 chars so massive 10,000+ line files never lag
        val maxHighlightOffset = if (smoothMode) length.coerceAtMost(250_000) else length

        var lineStart = 0
        while (lineStart < maxHighlightOffset) {
            val lineEnd = source.indexOf('\n', lineStart).let { if (it == -1) length else it }
            if (isXml) {
                highlightXmlLineFast(builder, source, lineStart, lineEnd)
            } else {
                highlightCodeLineFast(builder, source, lineStart, lineEnd)
            }
            lineStart = lineEnd + 1
        }

        applySearchHighlights(builder, length, searchRanges, activeSearchIndex)
        return builder.toAnnotatedString()
    }

    private fun applySearchHighlights(
        builder: AnnotatedString.Builder,
        textLength: Int,
        searchRanges: List<IntRange>,
        activeSearchIndex: Int
    ) {
        if (searchRanges.isEmpty()) return
        for (i in searchRanges.indices) {
            val r = searchRanges[i]
            val start = r.first.coerceIn(0, textLength)
            val end = (r.last + 1).coerceIn(start, textLength)
            if (start < end) {
                val bg = if (i == activeSearchIndex) ActiveSearchMatchBg else SearchMatchBg
                builder.addStyle(
                    SpanStyle(background = bg, color = Color(0xFF111111), fontWeight = FontWeight.Bold),
                    start,
                    end
                )
            }
        }
    }

    /**
     * Zero-Regex character-level XML line highlighter for instant 3,000+ line AXML/Manifest rendering.
     */
    private fun highlightXmlLineFast(
        builder: AnnotatedString.Builder,
        source: String,
        start: Int,
        end: Int
    ) {
        if (start >= end) return
        val commentIdx = source.indexOf("<!--", start)
        if (commentIdx in start until end) {
            builder.addStyle(SpanStyle(color = CommentColor), commentIdx, end)
            return
        }

        var i = start
        while (i < end) {
            val ch = source[i]
            when {
                ch == '"' -> {
                    val strStart = i
                    i++
                    while (i < end) {
                        if (source[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (source[i] == '"') {
                            i++
                            break
                        }
                        i++
                    }
                    builder.addStyle(
                        SpanStyle(color = StringColor),
                        strStart,
                        i.coerceAtMost(end)
                    )
                }
                ch == '<' -> {
                    val tagStart = i
                    i++
                    if (i < end && (source[i] == '/' || source[i] == '?')) i++
                    while (i < end && isXmlNameChar(source[i])) i++
                    builder.addStyle(
                        SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold),
                        tagStart,
                        i
                    )
                }
                ch == '>' -> {
                    builder.addStyle(
                        SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold),
                        i,
                        i + 1
                    )
                    i++
                }
                (ch == '/' || ch == '?') && i + 1 < end && source[i + 1] == '>' -> {
                    builder.addStyle(
                        SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold),
                        i,
                        i + 2
                    )
                    i += 2
                }
                ch == '=' -> {
                    builder.addStyle(
                        SpanStyle(color = SymbolColor, fontWeight = FontWeight.Medium),
                        i,
                        i + 1
                    )
                    i++
                }
                isXmlNameStart(ch) -> {
                    val attrStart = i
                    i++
                    while (i < end && isXmlNameChar(source[i])) i++
                    var probe = i
                    while (probe < end && (source[probe] == ' ' || source[probe] == '\t')) probe++
                    if (probe < end && source[probe] == '=') {
                        builder.addStyle(
                            SpanStyle(color = AttributeColor, fontWeight = FontWeight.Medium),
                            attrStart,
                            i
                        )
                    }
                }
                else -> i++
            }
        }
    }

    /**
     * Zero-Regex character-level Smali/Kotlin/Java/JSON/Shell highlighter for instant 3,500+ line rendering.
     */
    private fun highlightCodeLineFast(
        builder: AnnotatedString.Builder,
        source: String,
        start: Int,
        end: Int
    ) {
        if (start >= end) return
        val commentPos = findCommentStartRange(source, start, end)
        val codeEnd = if (commentPos >= 0) commentPos else end

        var i = start
        while (i < codeEnd) {
            val ch = source[i]
            when {
                ch == '"' || ch == '\'' -> {
                    val quote = ch
                    val strStart = i
                    i++
                    while (i < codeEnd) {
                        if (source[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (source[i] == quote) {
                            i++
                            break
                        }
                        i++
                    }
                    builder.addStyle(
                        SpanStyle(color = StringColor),
                        strStart,
                        i.coerceAtMost(codeEnd)
                    )
                }
                (ch == '.' && i + 1 < codeEnd && source[i + 1].isLetter()) || ch.isLetter() || ch == '_' -> {
                    val tokStart = i
                    i++
                    while (i < codeEnd && (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '-' || source[i] == '/')) {
                        i++
                    }
                    val token = source.substring(tokStart, i)
                    when {
                        token in KEYWORDS -> {
                            builder.addStyle(
                                SpanStyle(color = KeywordColor, fontWeight = FontWeight.Medium),
                                tokStart,
                                i
                            )
                        }
                        isSmaliRegister(token) -> {
                            builder.addStyle(
                                SpanStyle(color = RegisterColor, fontWeight = FontWeight.Medium),
                                tokStart,
                                i
                            )
                        }
                    }
                }
                ch.isDigit() || (ch == '-' && i + 1 < codeEnd && source[i + 1].isDigit()) -> {
                    val numStart = i
                    i++
                    if (i < codeEnd && (source[i] == 'x' || source[i] == 'X')) {
                        i++
                        while (i < codeEnd && isHexDigit(source[i])) i++
                    } else {
                        while (i < codeEnd && (source[i].isDigit() || source[i] == '.')) i++
                    }
                    if (i < codeEnd && (source[i] == 'L' || source[i] == 'f' || source[i] == 'F')) i++
                    builder.addStyle(
                        SpanStyle(color = NumberColor),
                        numStart,
                        i
                    )
                }
                else -> i++
            }
        }

        if (commentPos >= 0) {
            builder.addStyle(
                SpanStyle(color = CommentColor),
                commentPos,
                end
            )
        }
    }

    private fun isXmlNameStart(ch: Char): Boolean = ch.isLetter() || ch == '_' || ch == ':'
    private fun isXmlNameChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == ':' || ch == '.'
    private fun isHexDigit(ch: Char): Boolean = ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F'

    private fun isSmaliRegister(token: String): Boolean {
        if (token.length !in 2..4) return false
        val first = token[0]
        if (first != 'v' && first != 'p') return false
        for (idx in 1 until token.length) {
            if (!token[idx].isDigit()) return false
        }
        return true
    }

    private fun findCommentStartRange(source: String, start: Int, end: Int): Int {
        var inQuotes = false
        var i = start
        while (i < end) {
            val ch = source[i]
            if (ch == '\\' && inQuotes) {
                i += 2
                continue
            }
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (ch == '#') return i
                if (ch == '/' && i + 1 < end && source[i + 1] == '/') return i
            }
            i++
        }
        return -1
    }

    fun extractCompletionWords(source: String, prefix: String): List<String> {
        val clean = prefix.trim()
        if (clean.length < 2) return emptyList()
        val matches = LinkedHashSet<String>()
        for (kw in KEYWORDS) {
            if (kw.startsWith(clean, ignoreCase = true) && kw != clean) {
                matches.add(kw)
                if (matches.size >= 8) return matches.toList()
            }
        }
        val limit = source.length.coerceAtMost(40_000)
        var i = 0
        while (i < limit && matches.size < 10) {
            val ch = source[i]
            if (ch.isLetter() || ch == '_' || ch == '.') {
                val start = i
                i++
                while (i < limit && (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '-')) i++
                val len = i - start
                if (len in 3..36) {
                    val word = source.substring(start, i)
                    if (word.startsWith(clean, ignoreCase = true) && word != clean) {
                        matches.add(word)
                    }
                }
            } else {
                i++
            }
        }
        return matches.toList()
    }

    fun parseNavigationSymbols(text: String, fileName: String?): List<NavSymbolItem> {
        if (text.isEmpty()) return emptyList()
        val symbols = mutableListOf<NavSymbolItem>()
        val lines = text.split('\n')

        val isXml = fileName?.endsWith(".xml", true) == true ||
            text.trimStart().startsWith("<?xml") ||
            text.trimStart().startsWith("<manifest")

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
                (trimmed.contains("class ") || trimmed.contains("interface ") || trimmed.contains("object ")) &&
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
                trimmed.contains("fun ") &&
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
                (trimmed.startsWith("val ") || trimmed.startsWith("var ") || trimmed.contains(" val ") || trimmed.contains(" var ")) &&
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
                isXml && trimmed.startsWith("<") &&
                    trimmed.matches(Regex("^<(manifest|application|activity|service|receiver|provider|uses-permission|uses-sdk)\\b.*")) -> {
                    val tag = trimmed.removePrefix("<").substringBefore(' ').substringBefore('>')
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
