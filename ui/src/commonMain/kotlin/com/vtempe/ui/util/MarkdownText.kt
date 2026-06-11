package com.vtempe.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text

/**
 * Renders a subset of Markdown inline:
 *  **bold**, *italic*, `- bullet` / `• bullet` lines, `# heading` lines.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    Text(
        text = parseSimpleMarkdown(text),
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
    )
}

fun parseSimpleMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.split('\n')
    lines.forEachIndexed { lineIdx, line ->
        if (lineIdx > 0) append('\n')
        val trimmed = line.trimEnd()
        when {
            trimmed.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineMarkdown(trimmed.removePrefix("### "))
                }
            }
            trimmed.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineMarkdown(trimmed.removePrefix("## "))
                }
            }
            trimmed.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineMarkdown(trimmed.removePrefix("# "))
                }
            }
            trimmed.startsWith("- ") -> {
                append("• ")
                appendInlineMarkdown(trimmed.removePrefix("- "))
            }
            trimmed.startsWith("• ") -> {
                append("• ")
                appendInlineMarkdown(trimmed.removePrefix("• "))
            }
            trimmed.startsWith("* ") -> {
                append("• ")
                appendInlineMarkdown(trimmed.removePrefix("* "))
            }
            else -> appendInlineMarkdown(trimmed)
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // **bold**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // *italic*
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end >= 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
