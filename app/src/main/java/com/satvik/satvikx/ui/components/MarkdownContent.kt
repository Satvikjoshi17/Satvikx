package com.satvik.satvikx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.PrimaryNeon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.theme.SurfaceVariantDark
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary

/**
 * Parses markdown inline tokens (bold, italics, inline code, strikethrough, links, bare URLs)
 * into a rich Compose AnnotatedString styled to match the Stark cyberpunk design system.
 */
fun parseMarkdownInline(
    text: String,
    primaryColor: Color = TextPrimary,
    accentColor: Color = ArcCyanBright,
    codeHighlightColor: Color = Color(0x2800E5FF)
): AnnotatedString {
    return buildAnnotatedString {
        // Matches **bold**, __bold__, `code`, ~~strike~~, [label](url), *italic*, _italic_, https://...
        val pattern = Regex(
            """(\*\*(?:[^*]|\*(?!\*))+\*\*|__(?:[^_]|_(?!_))+__|`[^`]+`|~~[^~]+~~|\[([^\]]+)\]\(([^)]+)\)|\*(?:[^*])+\*|_(?:[^_])+_|https?://[^\s]+)"""
        )

        var lastIndex = 0
        pattern.findAll(text).forEach { matchResult ->
            val matchRange = matchResult.range
            if (matchRange.first > lastIndex) {
                withStyle(SpanStyle(color = primaryColor)) {
                    append(text.substring(lastIndex, matchRange.first))
                }
            }

            val value = matchResult.value
            when {
                // Bold (**text** or __text__)
                (value.startsWith("**") && value.endsWith("**")) ||
                (value.startsWith("__") && value.endsWith("__")) -> {
                    val inner = value.substring(2, value.length - 2)
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    ) {
                        append(inner)
                    }
                }
                // Inline Code (`code`)
                value.startsWith("`") && value.endsWith("`") -> {
                    val inner = value.substring(1, value.length - 1)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = accentColor,
                            background = codeHighlightColor
                        )
                    ) {
                        append(" $inner ")
                    }
                }
                // Strikethrough (~~text~~)
                value.startsWith("~~") && value.endsWith("~~") -> {
                    val inner = value.substring(2, value.length - 2)
                    withStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.LineThrough,
                            color = TextSecondary
                        )
                    ) {
                        append(inner)
                    }
                }
                // Markdown Link [label](url)
                value.startsWith("[") && value.contains("](") && value.endsWith(")") -> {
                    val label = value.substringAfter("[").substringBefore("](")
                    val url = value.substringAfter("](").substringBeforeLast(")")
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(
                        SpanStyle(
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(label)
                    }
                    pop()
                }
                // Italic (*text* or _text_)
                (value.startsWith("*") && value.endsWith("*")) ||
                (value.startsWith("_") && value.endsWith("_")) -> {
                    val inner = value.substring(1, value.length - 1)
                    withStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = primaryColor
                        )
                    ) {
                        append(inner)
                    }
                }
                // Bare URL (https://...)
                value.startsWith("http://") || value.startsWith("https://") -> {
                    pushStringAnnotation(tag = "URL", annotation = value)
                    withStyle(
                        SpanStyle(
                            color = accentColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(value)
                    }
                    pop()
                }
                else -> {
                    withStyle(SpanStyle(color = primaryColor)) {
                        append(value)
                    }
                }
            }
            lastIndex = matchRange.last + 1
        }

        if (lastIndex < text.length) {
            withStyle(SpanStyle(color = primaryColor)) {
                append(text.substring(lastIndex))
            }
        }
    }
}

/**
 * Full Markdown Document Renderer for release notes, changelogs, and documentation.
 * Formats headings, bulleted lists, numbered lists, code blocks, blockquotes, horizontal dividers,
 * bold, italics, inline code chips, and links.
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(content) {
        content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    }

    Column(modifier = modifier) {
        var inCodeBlock = false
        val codeBlockLines = remember { mutableListOf<String>() }

        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()

            // Handle fenced code blocks
            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    val codeContent = codeBlockLines.joinToString("\n")
                    codeBlockLines.clear()
                    inCodeBlock = false
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceDark,
                        border = BorderStroke(0.5.dp, SurfaceVariantDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = codeContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ArcCyanBright,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    inCodeBlock = true
                    codeBlockLines.clear()
                }
                return@forEach
            }

            if (inCodeBlock) {
                codeBlockLines.add(rawLine)
                return@forEach
            }

            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Horizontal Rule
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    HorizontalDivider(
                        color = SurfaceVariantDark,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                // H1 Heading
                trimmed.startsWith("# ") -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("# ").trim(), primaryColor = Color.White),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                // H2 Heading
                trimmed.startsWith("## ") -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("## ").trim(), primaryColor = ArcCyanBright),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = ArcCyanBright
                        )
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                // H3 Heading
                trimmed.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("### ").trim(), primaryColor = StarkGold),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = StarkGold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // H4 Heading
                trimmed.startsWith("#### ") -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("#### ").trim(), primaryColor = TextPrimary),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Blockquote
                trimmed.startsWith("> ") -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .background(ArcCyanBright, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseMarkdownInline(trimmed.removePrefix("> ").trim(), primaryColor = TextSecondary),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }
                }

                // Bullet Lists (*, -, +)
                trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ") -> {
                    val bulletContent = when {
                        trimmed.startsWith("* ") -> trimmed.removePrefix("* ")
                        trimmed.startsWith("- ") -> trimmed.removePrefix("- ")
                        else -> trimmed.removePrefix("+ ")
                    }.trim()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(PrimaryNeon)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseMarkdownInline(bulletContent, primaryColor = TextPrimary),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }

                // Numbered Lists (1., 2., etc.)
                trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val num = trimmed.substringBefore(".")
                    val content = trimmed.substringAfter(".").trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "$num.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ArcCyanBright,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = parseMarkdownInline(content, primaryColor = TextPrimary),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Standard Paragraph / Text
                else -> {
                    Text(
                        text = parseMarkdownInline(trimmed, primaryColor = TextSecondary),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        // Handle unclosed code block if file ends inside code block
        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            val codeContent = codeBlockLines.joinToString("\n")
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceDark,
                border = BorderStroke(0.5.dp, SurfaceVariantDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = codeContent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = ArcCyanBright,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

