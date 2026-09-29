/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of BetterAIChat2.
 *
 * BetterAIChat2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * BetterAIChat2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * BetterAIChat2. If not, see <https://www.gnu.org/licenses/>.
 */

package com.verlintas.baic2.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.verlintas.baic2.designsystem.Baic2Mono

/**
 * Lightweight markdown renderer tuned for streaming model output: fenced code
 * blocks become terminal cards, inline code/bold/links are styled. Tables and
 * other exotic constructs degrade to plain text for now.
 */
sealed interface MarkdownBlock {
    data class Paragraph(val text: String, val kind: Kind) : MarkdownBlock {
        enum class Kind { NORMAL, HEADING1, HEADING2, HEADING3, BULLET, NUMBERED, QUOTE }
    }

    data class Code(val language: String, val code: String) : MarkdownBlock

    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val alignments: List<MarkdownNormalizer.TableAlignment> = emptyList(),
    ) : MarkdownBlock
}

fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val code = StringBuilder()
    val normalLines = mutableListOf<String>()
    var codeLanguage = ""
    var inCode = false

    fun flushNormal() {
        if (normalLines.isEmpty()) return
        blocks += MarkdownBlock.Paragraph(normalLines.joinToString("\n"), MarkdownBlock.Paragraph.Kind.NORMAL)
        normalLines.clear()
    }

    val lines = MarkdownNormalizer.normalize(text).lines()
    var lineIndex = 0
    while (lineIndex < lines.size) {
        val line = lines[lineIndex].trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                inCode = false
                blocks += MarkdownBlock.Code(codeLanguage, code.toString().trimEnd())
                code.clear()
                codeLanguage = ""
            } else {
                flushNormal()
                inCode = true
                codeLanguage = line.trim().removePrefix("```").trim()
            }
            lineIndex++
            continue
        }
        if (inCode) {
            code.appendLine(line)
            lineIndex++
            continue
        }
        if (line.isBlank()) {
            flushNormal()
            lineIndex++
            continue
        }
        if (MarkdownNormalizer.isTableRow(line) &&
            lineIndex + 1 < lines.size &&
            MarkdownNormalizer.isTableSeparator(lines[lineIndex + 1])
        ) {
            flushNormal()
            val header = MarkdownNormalizer.splitTableRow(line)
            val rows = mutableListOf<List<String>>()
            var cursor = lineIndex + 2
            while (cursor < lines.size &&
                lines[cursor].isNotBlank() &&
                MarkdownNormalizer.isTableRow(lines[cursor])
            ) {
                rows += MarkdownNormalizer.splitTableRow(lines[cursor])
                cursor++
            }
            blocks += MarkdownBlock.Table(
                header = header,
                rows = rows,
                alignments = MarkdownNormalizer.tableAlignments(lines[lineIndex + 1]),
            )
            lineIndex = cursor
            continue
        }
        val kind = classify(line)
        when (kind) {
            MarkdownBlock.Paragraph.Kind.NORMAL -> normalLines += line
            else -> {
                flushNormal()
                blocks += MarkdownBlock.Paragraph(line, kind)
            }
        }
        lineIndex++
    }
    if (inCode) {
        blocks += MarkdownBlock.Code(codeLanguage, code.toString().trimEnd())
    }
    flushNormal()
    return blocks
}

private fun classify(line: String): MarkdownBlock.Paragraph.Kind = when {
    line.startsWith("### ") -> MarkdownBlock.Paragraph.Kind.HEADING3
    line.startsWith("## ") -> MarkdownBlock.Paragraph.Kind.HEADING2
    line.startsWith("# ") -> MarkdownBlock.Paragraph.Kind.HEADING1
    line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") ->
        MarkdownBlock.Paragraph.Kind.BULLET
    Regex("^\\d+[.)]\\s").containsMatchIn(line) -> MarkdownBlock.Paragraph.Kind.NUMBERED
    line.startsWith("> ") -> MarkdownBlock.Paragraph.Kind.QUOTE
    else -> MarkdownBlock.Paragraph.Kind.NORMAL
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkdownMessage(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    val blocks = parseMarkdownBlocks(text)
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            val isLast = index == blocks.lastIndex
            when (block) {
                is MarkdownBlock.Code -> {
                    CodeBlock(language = block.language, code = block.code)
                    if (streaming && isLast) {
                        BlinkingCursor(modifier = Modifier.padding(top = 2.dp))
                    }
                }

                is MarkdownBlock.Table -> {
                    MarkdownTable(block)
                }

                is MarkdownBlock.Paragraph -> {
                    val style = paragraphStyle(block.kind)
                    val prefix = when (block.kind) {
                        MarkdownBlock.Paragraph.Kind.BULLET -> "• "
                        MarkdownBlock.Paragraph.Kind.QUOTE -> null
                        else -> null
                    }
                    val content = block.text
                        .removePrefix("- ")
                        .removePrefix("* ")
                        .removePrefix("• ")
                        .removePrefix("# ")
                        .removePrefix("## ")
                        .removePrefix("### ")
                        .removePrefix("> ")

                    if (streaming && isLast) {
                        FlowRow {
                            Text(
                                text = buildAnnotatedString {
                                    prefix?.let { withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(it) } }
                                    append(inlineMarkdown(content))
                                },
                                style = style,
                                color = paragraphColor(block.kind),
                            )
                            BlinkingCursor()
                        }
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                prefix?.let { withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(it) } }
                                append(inlineMarkdown(content))
                            },
                            style = style,
                            color = paragraphColor(block.kind),
                            modifier = if (block.kind == MarkdownBlock.Paragraph.Kind.QUOTE) {
                                Modifier.padding(start = 2.dp)
                            } else {
                                Modifier
                            },
                        )
                    }
                    if (block.kind == MarkdownBlock.Paragraph.Kind.QUOTE) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
            if (!isLast) {
                Spacer(Modifier.height(if (block is MarkdownBlock.Code) 10.dp else 6.dp))
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    val columnCount = table.header.size.coerceIn(1, MAX_TABLE_COLUMNS)
    val widths = List(columnCount) { column ->
        var longest = displayWidth(table.header.getOrNull(column).orEmpty())
        table.rows.forEach { row ->
            longest = maxOf(longest, displayWidth(row.getOrNull(column).orEmpty()))
        }
        longest.coerceIn(4, 28)
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), shape),
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                table.header.take(columnCount).forEachIndexed { index, cell ->
                    TableCell(cell, widths[index], table.alignments.getOrNull(index), header = true)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            table.rows.forEach { row ->
                Row {
                    widths.indices.forEach { index ->
                        TableCell(row.getOrNull(index).orEmpty(), widths[index], table.alignments.getOrNull(index))
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    widthChars: Int,
    alignment: MarkdownNormalizer.TableAlignment?,
    header: Boolean = false,
) {
    Text(
        text = inlineMarkdown(text),
        style = if (header) {
            Baic2Mono.label.copy(fontWeight = FontWeight.SemiBold)
        } else {
            Baic2Mono.label
        },
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        textAlign = when (alignment) {
            MarkdownNormalizer.TableAlignment.CENTER -> TextAlign.Center
            MarkdownNormalizer.TableAlignment.RIGHT -> TextAlign.End
            else -> TextAlign.Start
        },
        modifier = Modifier
            .width((widthChars * 7.5f).dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

private fun displayWidth(value: String): Int =
    value.sumOf { character -> if (character.code > 0x2E7F) 2 else 1 }

private const val MAX_TABLE_COLUMNS = 8

@Composable
private fun paragraphStyle(kind: MarkdownBlock.Paragraph.Kind) = when (kind) {
    MarkdownBlock.Paragraph.Kind.HEADING1 -> MaterialTheme.typography.titleLarge
    MarkdownBlock.Paragraph.Kind.HEADING2 -> MaterialTheme.typography.titleMedium
    MarkdownBlock.Paragraph.Kind.HEADING3 -> MaterialTheme.typography.titleSmall
    MarkdownBlock.Paragraph.Kind.QUOTE -> MaterialTheme.typography.bodyMedium
    else -> MaterialTheme.typography.bodyLarge
}

@Composable
private fun paragraphColor(kind: MarkdownBlock.Paragraph.Kind): Color = when (kind) {
    MarkdownBlock.Paragraph.Kind.QUOTE -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
private val CODE_REGEX = Regex("`([^`]+)`")
private val LINK_REGEX = Regex("\\[([^]]+)]\\(([^)]+)\\)")
private val ITALIC_REGEX = Regex("(?<!\\*)\\*([^*]+?)\\*(?!\\*)")

/** Inline styles: code, links, bold, italic — in that priority order. */
@Composable
fun inlineMarkdown(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary

    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val remaining = text.substring(cursor)

            val code = CODE_REGEX.find(remaining)
            val link = LINK_REGEX.find(remaining)
            val bold = BOLD_REGEX.find(remaining)
            val italic = ITALIC_REGEX.find(remaining)

            val next = listOfNotNull(code, link, bold, italic)
                .minByOrNull { it.range.first }

            if (next == null) {
                append(remaining)
                break
            }

            append(remaining.substring(0, next.range.first))
            when (next) {
                code -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    ),
                ) { append(next.groupValues[1]) }

                link -> withLink(
                    LinkAnnotation.Url(
                        next.groupValues[2],
                        TextLinkStyles(style = SpanStyle(color = linkColor)),
                    ),
                ) { append(next.groupValues[1]) }

                bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(next.groupValues[1])
                }

                else -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(next.groupValues[1])
                }
            }
            cursor += next.range.last + 1
        }
    }
}

@Composable
fun CodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
            text = code,
            style = Baic2Mono.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun BlinkingCursor(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor-alpha",
    )
    Box(
        modifier = modifier
            .padding(start = 2.dp, top = 3.dp)
            .size(width = 2.dp, height = 15.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary),
    )
}
