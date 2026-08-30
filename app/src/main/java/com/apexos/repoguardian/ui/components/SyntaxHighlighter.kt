package com.apexos.repoguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexos.repoguardian.ui.theme.*

/**
 * High-performance, zero-overhead syntax highlighting engine for Repo Guardian.
 * Supports Kotlin, Java, C/C++, Rust, Python, JavaScript, TypeScript, YAML, JSON, SQL, Shell, and Git Diffs.
 */
object SyntaxHighlighter {

    // Palette tuned for Developer Dark Slate theme
    val ColorKeyword = Color(0xFFC792EA)       // Electric Violet
    val ColorString = Color(0xFF9ECE6A)        // Soft Mint / Lime
    val ColorNumber = Color(0xFFFF9E64)        // Warm Amber / Coral
    val ColorComment = Color(0xFF7E89A0)       // Muted Slate (Italic)
    val ColorType = Color(0xFF4EC9B0)          // Teal / Cyan
    val ColorFunction = Color(0xFFE0AF68)      // Gold / Yellow
    val ColorAnnotation = Color(0xFFF7768E)    // Rose Pink
    val ColorOperator = Color(0xFF89DDFF)      // Sky Blue
    val ColorProperty = Color(0xFF7AA2F7)      // Ice Blue
    val ColorPlain = Color(0xFFECEFF4)         // Crisp Off-White
    val ColorDiffAdd = Color(0xFF3FB950)       // GitHub Green
    val ColorDiffDelete = Color(0xFFF85149)    // GitHub Red
    val ColorDiffHeader = Color(0xFF58A6FF)    // GitHub Blue

    private val kotlinKeywords = setOf(
        "fun", "val", "var", "class", "interface", "object", "enum", "package", "import",
        "return", "if", "else", "when", "for", "while", "try", "catch", "finally", "throw",
        "override", "private", "public", "protected", "internal", "companion", "sealed",
        "data", "abstract", "suspend", "inline", "reified", "typealias", "const", "lateinit",
        "by", "is", "as", "in", "null", "true", "false", "this", "super", "constructor",
        "init", "get", "set", "open", "operator", "tailrec", "vararg", "yield"
    )

    private val javaCppKeywords = setOf(
        "public", "private", "protected", "static", "final", "void", "int", "boolean",
        "double", "float", "long", "short", "byte", "char", "class", "interface",
        "extends", "implements", "new", "return", "if", "else", "switch", "case",
        "default", "for", "while", "do", "break", "continue", "try", "catch", "finally",
        "throw", "throws", "import", "package", "this", "super", "null", "true", "false",
        "auto", "constexpr", "nullptr", "struct", "template", "typename", "using", "namespace",
        "virtual", "override", "const", "enum", "extern", "sizeof", "typedef", "volatile"
    )

    private val pythonKeywords = setOf(
        "def", "class", "import", "from", "as", "return", "if", "elif", "else", "for",
        "while", "try", "except", "finally", "raise", "with", "async", "await", "yield",
        "lambda", "pass", "break", "continue", "global", "nonlocal", "assert", "True",
        "False", "None", "self", "cls", "in", "is", "not", "and", "or"
    )

    private val jsTsKeywords = setOf(
        "function", "const", "let", "var", "class", "interface", "type", "enum",
        "import", "export", "from", "as", "default", "return", "if", "else", "switch",
        "case", "for", "while", "try", "catch", "finally", "throw", "async", "await",
        "yield", "new", "this", "super", "typeof", "instanceof", "true", "false",
        "null", "undefined", "extends", "implements", "readonly", "declare", "abstract"
    )

    private val sqlKeywords = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "join", "left", "right", "inner", "outer", "cross", "on", "group", "by", "having",
        "order", "asc", "desc", "limit", "offset", "union", "all", "create", "table",
        "alter", "drop", "index", "primary", "key", "foreign", "references", "not", "null",
        "and", "or", "in", "exists", "like", "between", "is", "case", "when", "then", "else",
        "end", "as", "distinct", "count", "sum", "avg", "min", "max"
    )

    fun highlight(code: String, language: String): AnnotatedString {
        val lang = language.lowercase().trim()
        return when {
            lang == "diff" || lang == "patch" -> highlightDiff(code)
            lang == "yaml" || lang == "yml" -> highlightYaml(code)
            lang == "json" -> highlightJson(code)
            lang == "sql" -> highlightCodeWithKeywords(code, sqlKeywords, isCaseInsensitive = true)
            lang == "python" || lang == "py" -> highlightCodeWithKeywords(code, pythonKeywords)
            lang == "javascript" || lang == "js" || lang == "typescript" || lang == "ts" || lang == "jsx" || lang == "tsx" ->
                highlightCodeWithKeywords(code, jsTsKeywords)
            lang == "c" || lang == "cpp" || lang == "c++" || lang == "h" || lang == "hpp" || lang == "java" || lang == "c#" || lang == "cs" ->
                highlightCodeWithKeywords(code, javaCppKeywords)
            else -> highlightCodeWithKeywords(code, kotlinKeywords) // Default to Kotlin/General
        }
    }

    private fun highlightDiff(code: String): AnnotatedString {
        return buildAnnotatedString {
            code.lines().forEachIndexed { index, line ->
                if (index > 0) append("\n")
                when {
                    line.startsWith("+") -> {
                        withStyle(SpanStyle(color = ColorDiffAdd, fontWeight = FontWeight.Medium)) {
                            append(line)
                        }
                    }
                    line.startsWith("-") -> {
                        withStyle(SpanStyle(color = ColorDiffDelete, fontWeight = FontWeight.Medium)) {
                            append(line)
                        }
                    }
                    line.startsWith("@@") -> {
                        withStyle(SpanStyle(color = ColorDiffHeader, fontStyle = FontStyle.Italic)) {
                            append(line)
                        }
                    }
                    line.startsWith("---") || line.startsWith("+++") -> {
                        withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold)) {
                            append(line)
                        }
                    }
                    else -> {
                        withStyle(SpanStyle(color = ColorPlain.copy(alpha = 0.8f))) {
                            append(line)
                        }
                    }
                }
            }
        }
    }

    private fun highlightYaml(code: String): AnnotatedString {
        return buildAnnotatedString {
            code.lines().forEachIndexed { index, line ->
                if (index > 0) append("\n")

                val trimmed = line.trimStart()
                val indent = line.substring(0, line.length - trimmed.length)
                append(indent)

                if (trimmed.startsWith("#")) {
                    withStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic)) {
                        append(trimmed)
                    }
                    return@forEachIndexed
                }

                val colonIndex = trimmed.indexOf(':')
                if (colonIndex != -1) {
                    val key = trimmed.substring(0, colonIndex)
                    val value = trimmed.substring(colonIndex + 1)

                    withStyle(SpanStyle(color = ColorProperty, fontWeight = FontWeight.SemiBold)) {
                        append(key)
                    }
                    withStyle(SpanStyle(color = ColorOperator)) {
                        append(":")
                    }

                    if (value.isNotEmpty()) {
                        val trimmedVal = value.trim()
                        val valIndent = value.substring(0, value.length - trimmedVal.length)
                        append(valIndent)

                        when {
                            trimmedVal.startsWith("\"") || trimmedVal.startsWith("'") -> {
                                withStyle(SpanStyle(color = ColorString)) {
                                    append(trimmedVal)
                                }
                            }
                            trimmedVal.matches(Regex("^-?\\d+(\\.\\d+)?$")) -> {
                                withStyle(SpanStyle(color = ColorNumber)) {
                                    append(trimmedVal)
                                }
                            }
                            trimmedVal.equals("true", ignoreCase = true) || trimmedVal.equals("false", ignoreCase = true) -> {
                                withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold)) {
                                    append(trimmedVal)
                                }
                            }
                            else -> {
                                withStyle(SpanStyle(color = ColorPlain)) {
                                    append(trimmedVal)
                                }
                            }
                        }
                    }
                } else {
                    withStyle(SpanStyle(color = ColorPlain)) {
                        append(trimmed)
                    }
                }
            }
        }
    }

    private fun highlightJson(code: String): AnnotatedString {
        val pattern = Regex("(\"(?:[^\"\\\\]|\\\\.)*\")\\s*(:)?|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|\\b(true|false|null)\\b|([{}\\[\\],])")
        return buildAnnotatedString {
            var cursor = 0
            for (match in pattern.findAll(code)) {
                if (match.range.first > cursor) {
                    withStyle(SpanStyle(color = ColorPlain)) {
                        append(code.substring(cursor, match.range.first))
                    }
                }

                val fullText = match.value
                val str = match.groups[1]?.value
                val isKey = match.groups[2] != null
                val num = match.groups[3]?.value
                val bool = match.groups[4]?.value
                val punct = match.groups[5]?.value

                when {
                    str != null && isKey -> {
                        withStyle(SpanStyle(color = ColorProperty, fontWeight = FontWeight.SemiBold)) {
                            append(str)
                        }
                        withStyle(SpanStyle(color = ColorOperator)) {
                            append(":")
                        }
                    }
                    str != null -> {
                        withStyle(SpanStyle(color = ColorString)) {
                            append(str)
                        }
                    }
                    num != null -> {
                        withStyle(SpanStyle(color = ColorNumber)) {
                            append(num)
                        }
                    }
                    bool != null -> {
                        withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold)) {
                            append(bool)
                        }
                    }
                    punct != null -> {
                        withStyle(SpanStyle(color = ColorOperator)) {
                            append(punct)
                        }
                    }
                    else -> {
                        withStyle(SpanStyle(color = ColorPlain)) {
                            append(fullText)
                        }
                    }
                }

                cursor = match.range.last + 1
            }

            if (cursor < code.length) {
                withStyle(SpanStyle(color = ColorPlain)) {
                    append(code.substring(cursor))
                }
            }
        }
    }

    private fun highlightCodeWithKeywords(
        code: String,
        keywords: Set<String>,
        isCaseInsensitive: Boolean = false
    ): AnnotatedString {
        val tokenRegex = Regex(
            "(//.*|/\\*[\\s\\S]*?\\*/|#.*)" +                               // 1: Comments
            "|(\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')" + // 2: Strings
            "|(@[a-zA-Z0-9_.]+)" +                                           // 3: Annotations / Decorators
            "|(\\b0x[0-9a-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b)" + // 4: Numbers
            "|(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)(?=\\s*\\()" +                   // 5: Function calls
            "|(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)" +                              // 6: Identifiers / Keywords / Types
            "|([+\\-*/%=&|!<>?:;.,{}()\\[\\]])"                              // 7: Operators
        )

        return buildAnnotatedString {
            var cursor = 0

            for (match in tokenRegex.findAll(code)) {
                if (match.range.first > cursor) {
                    withStyle(SpanStyle(color = ColorPlain)) {
                        append(code.substring(cursor, match.range.first))
                    }
                }

                val comment = match.groups[1]?.value
                val stringVal = match.groups[2]?.value
                val annotation = match.groups[3]?.value
                val number = match.groups[4]?.value
                val funcCall = match.groups[5]?.value
                val identifier = match.groups[6]?.value
                val operator = match.groups[7]?.value

                when {
                    comment != null -> {
                        withStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic)) {
                            append(comment)
                        }
                    }
                    stringVal != null -> {
                        withStyle(SpanStyle(color = ColorString)) {
                            append(stringVal)
                        }
                    }
                    annotation != null -> {
                        withStyle(SpanStyle(color = ColorAnnotation, fontWeight = FontWeight.SemiBold)) {
                            append(annotation)
                        }
                    }
                    number != null -> {
                        withStyle(SpanStyle(color = ColorNumber)) {
                            append(number)
                        }
                    }
                    funcCall != null -> {
                        val checkKey = if (isCaseInsensitive) funcCall.lowercase() else funcCall
                        if (keywords.contains(checkKey)) {
                            withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold)) {
                                append(funcCall)
                            }
                        } else {
                            withStyle(SpanStyle(color = ColorFunction, fontWeight = FontWeight.Medium)) {
                                append(funcCall)
                            }
                        }
                    }
                    identifier != null -> {
                        val checkKey = if (isCaseInsensitive) identifier.lowercase() else identifier
                        when {
                            keywords.contains(checkKey) -> {
                                withStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold)) {
                                    append(identifier)
                                }
                            }
                            identifier.first().isUpperCase() -> {
                                withStyle(SpanStyle(color = ColorType, fontWeight = FontWeight.Medium)) {
                                    append(identifier)
                                }
                            }
                            else -> {
                                withStyle(SpanStyle(color = ColorPlain)) {
                                    append(identifier)
                                }
                            }
                        }
                    }
                    operator != null -> {
                        withStyle(SpanStyle(color = ColorOperator)) {
                            append(operator)
                        }
                    }
                    else -> {
                        withStyle(SpanStyle(color = ColorPlain)) {
                            append(match.value)
                        }
                    }
                }

                cursor = match.range.last + 1
            }

            if (cursor < code.length) {
                withStyle(SpanStyle(color = ColorPlain)) {
                    append(code.substring(cursor))
                }
            }
        }
    }
}

/**
 * Production Code Snippet UI Component with styled line numbers,
 * syntax highlighting, language badge, and instant copy button.
 */
@Composable
fun CodeSnippetView(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true,
    onCopy: ((String) -> Unit)? = null
) {
    var copied by remember { mutableStateOf(false) }
    val highlightedCode = remember(code, language) {
        SyntaxHighlighter.highlight(code, language)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CodeBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header: Language badge + Copy button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = BrandEmeraldLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = language.ifBlank { "code" }.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandEmeraldLight,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                if (onCopy != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (copied) StatusPass.copy(alpha = 0.2f) else BrandSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (copied) StatusPass else BrandBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onCopy(code)
                                copied = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (copied) "Copied" else "Copy Code",
                                tint = if (copied) StatusPass else BrandOnBgMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = if (copied) StatusPass else BrandOnBgMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = BrandBorder,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Code Content with Line Numbers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Row {
                        if (showLineNumbers) {
                            val lineCount = code.lines().size
                            val lineNumbersText = (1..lineCount).joinToString("\n")

                            Text(
                                text = lineNumbersText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp
                                ),
                                color = BrandOnBgSubtle.copy(alpha = 0.5f),
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }

                        Text(
                            text = highlightedCode,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
