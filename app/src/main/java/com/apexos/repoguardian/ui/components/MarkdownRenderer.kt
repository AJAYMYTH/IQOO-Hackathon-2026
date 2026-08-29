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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
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
import com.apexos.repoguardian.ui.theme.CodeBackground
import com.apexos.repoguardian.ui.theme.StatusInfo

sealed class MarkdownBlock {
    data class Think(val thought: String, val isStreaming: Boolean = false) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class Numbered(val number: String, val text: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onCopyCode: (String) -> Unit
) {
    val blocks = parseMarkdown(content)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Think -> {
                    var isExpanded by remember { mutableStateOf(true) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpanded = !isExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Psychology,
                                        contentDescription = "Thinking",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (block.isStreaming) "Thinking..." else "Thought Process",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (block.isStreaming) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (isExpanded) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                                Text(
                                    text = block.thought,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    ),
                                    color = textColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Header -> {
                    val (style, topSpace) = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge to 12.dp
                        2 -> MaterialTheme.typography.titleMedium to 8.dp
                        else -> MaterialTheme.typography.titleSmall to 6.dp
                    }
                    Spacer(modifier = Modifier.height(topSpace))
                    Text(
                        text = buildAnnotatedMarkdown(block.text, textColor),
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedMarkdown(block.text, textColor),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }

                is MarkdownBlock.Bullet -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = buildAnnotatedMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Numbered -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.number,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = buildAnnotatedMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildAnnotatedMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }

                is MarkdownBlock.Code -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = CodeBackground),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Code,
                                        contentDescription = null,
                                        tint = StatusInfo,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = block.language.ifBlank { "code" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusInfo,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onCopyCode(block.code) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy Code",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Copy Code",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = block.code,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp
                                        ),
                                        color = Color.White.copy(alpha = 0.95f)
                                    )
                                }
                            }
                        }
                    }
                }

                is MarkdownBlock.Table -> {
                    MarkdownTable(
                        table = block,
                        textColor = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    table: MarkdownBlock.Table,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Column {
                // Header Row
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    table.headers.forEachIndexed { index, header ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 100.dp, max = 240.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = buildAnnotatedMarkdown(header, MaterialTheme.colorScheme.primary),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Data Rows
                table.rows.forEachIndexed { rowIndex, row ->
                    val bg = if (rowIndex % 2 == 1) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
                    } else {
                        Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(bg)
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        table.headers.forEachIndexed { colIndex, _ ->
                            val cellText = row.getOrNull(colIndex) ?: ""
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 240.dp)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = buildAnnotatedMarkdown(cellText, textColor),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                    if (rowIndex < table.rows.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isTableDivider(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('-') && !trimmed.contains('|')) return false
    val parts = trimmed.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return false
    return parts.all { col -> col.all { c -> c == '-' || c == ':' || c == ' ' } }
}

private fun splitTableRow(line: String): List<String> {
    var trimmed = line.trim()
    if (trimmed.startsWith('|')) trimmed = trimmed.substring(1)
    if (trimmed.endsWith('|')) trimmed = trimmed.substring(0, trimmed.length - 1)
    return trimmed.split('|').map { it.trim() }
}

fun parseMarkdown(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()

    // Check for <think>...</think> block or in-progress <think>...
    var processedRaw = raw
    val thinkRegex = Regex("<think>([\\s\\S]*?)</think>")
    val thinkMatch = thinkRegex.find(raw)
    if (thinkMatch != null) {
        val thought = thinkMatch.groupValues[1].trim()
        if (thought.isNotBlank()) {
            blocks.add(MarkdownBlock.Think(thought, isStreaming = false))
        }
        processedRaw = raw.replace(thinkMatch.value, "").trim()
    } else if (raw.contains("<think>")) {
        val startIdx = raw.indexOf("<think>")
        val thought = raw.substring(startIdx + 7).trim()
        if (thought.isNotBlank()) {
            blocks.add(MarkdownBlock.Think(thought, isStreaming = true))
        }
        processedRaw = raw.substring(0, startIdx).trim()
    }

    val lines = processedRaw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.Code(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        // Table detection
        if (line.contains('|') && i + 1 < lines.size && isTableDivider(lines[i + 1])) {
            val headers = splitTableRow(line)
            val rows = mutableListOf<List<String>>()
            i += 2 // skip header and divider
            while (i < lines.size && lines[i].contains('|') && lines[i].trim().isNotBlank()) {
                val rowCells = splitTableRow(lines[i])
                if (rowCells.isNotEmpty()) {
                    rows.add(rowCells)
                }
                i++
            }
            blocks.add(MarkdownBlock.Table(headers, rows))
            continue
        }

        // Headers
        when {
            line.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Header(3, line.removePrefix("### ").trim()))
            }
            line.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Header(2, line.removePrefix("## ").trim()))
            }
            line.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Header(1, line.removePrefix("# ").trim()))
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                val clean = line.trimStart().substring(2).trim()
                blocks.add(MarkdownBlock.Bullet(clean))
            }
            line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                val match = Regex("^(\\d+\\.)\\s(.*)").find(line.trimStart())
                if (match != null) {
                    val num = match.groupValues[1]
                    val text = match.groupValues[2]
                    blocks.add(MarkdownBlock.Numbered(num, text))
                } else {
                    blocks.add(MarkdownBlock.Paragraph(line.trim()))
                }
            }
            line.trimStart().startsWith("> ") -> {
                blocks.add(MarkdownBlock.Blockquote(line.trimStart().removePrefix("> ").trim()))
            }
            line.isNotBlank() -> {
                blocks.add(MarkdownBlock.Paragraph(line.trim()))
            }
        }
        i++
    }

    return blocks
}

fun buildAnnotatedMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val pattern = Regex("(\\*\\*|\\*|`)(.*?)\\1")

        val matches = pattern.findAll(text).toList()
        for (match in matches) {
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1
            val delimiter = match.groupValues[1]
            val innerText = match.groupValues[2]

            // Append regular text before match
            if (matchStart > cursor) {
                withStyle(SpanStyle(color = defaultColor)) {
                    append(text.substring(cursor, matchStart))
                }
            }

            when (delimiter) {
                "**" -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(innerText)
                    }
                }
                "*" -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                        append(innerText)
                    }
                }
                "`" -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusInfo,
                            background = Color.Black.copy(alpha = 0.25f)
                        )
                    ) {
                        append(" $innerText ")
                    }
                }
            }

            cursor = matchEnd
        }

        if (cursor < text.length) {
            withStyle(SpanStyle(color = defaultColor)) {
                append(text.substring(cursor))
            }
        }
    }
}
