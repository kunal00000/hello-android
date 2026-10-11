package dev.cue.rebuild

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class MarkdownFormat(val label: String, val description: String) {
    Bold("B", "Bold"),
    Italic("I", "Italic"),
    Strike("S", "Strikethrough"),
    Code("<>", "Inline code"),
    Link("Link", "Insert link"),
    Heading("H", "Heading"),
    Bullet("•", "Bullet list"),
    Numbered("1.", "Numbered list"),
    Task("☐", "Task list"),
    Quote("❯", "Quote"),
    CodeBlock("```", "Code block"),
}

/** Operates on the selection, so formatting never replaces the rest of a draft. */
fun formatMarkdown(value: TextFieldValue, format: MarkdownFormat): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val selected = text.substring(start, end)
    fun replace(from: Int, to: Int, inserted: String, selection: TextRange) =
        TextFieldValue(text.replaceRange(from, to, inserted), selection)
    fun wrap(marker: String): TextFieldValue {
        if (
            start >= marker.length &&
                text.substring(start - marker.length, start) == marker &&
                text.substring(end).startsWith(marker)
        ) {
            return replace(
                start - marker.length,
                end + marker.length,
                selected,
                TextRange(start - marker.length, end - marker.length),
            )
        }
        if (
            selected.length >= marker.length * 2 &&
                selected.startsWith(marker) &&
                selected.endsWith(marker)
        ) {
            val inner = selected.substring(marker.length, selected.length - marker.length)
            return replace(start, end, inner, TextRange(start, start + inner.length))
        }
        return replace(
            start,
            end,
            marker + selected + marker,
            TextRange(start + marker.length, end + marker.length),
        )
    }
    return when (format) {
        MarkdownFormat.Bold -> wrap("**")
        MarkdownFormat.Italic -> wrap("_")
        MarkdownFormat.Strike -> wrap("~~")
        MarkdownFormat.Code ->
            wrap(
                "`".repeat((Regex("`+").findAll(selected).maxOfOrNull { it.value.length } ?: 0) + 1)
            )
        MarkdownFormat.Link -> {
            val label = selected.ifEmpty { "link text" }
            val inserted = "[$label](https://)"
            val urlStart = start + label.length + 3
            replace(start, end, inserted, TextRange(urlStart, urlStart + 8))
        }
        MarkdownFormat.CodeBlock -> {
            val fence =
                "`"
                    .repeat(
                        maxOf(
                            3,
                            (Regex("`+").findAll(selected).maxOfOrNull { it.value.length } ?: 0) + 1,
                        )
                    )
            val before = if (start > 0 && text[start - 1] != '\n') "\n\n" else ""
            val after = if (end < text.length && text[end] != '\n') "\n\n" else ""
            val prefix = "$before$fence\n"
            replace(
                start,
                end,
                "$prefix$selected\n$fence$after",
                TextRange(start + prefix.length, end + prefix.length),
            )
        }
        else -> {
            val from = text.lastIndexOf('\n', start - 1).let { if (start == 0) 0 else it + 1 }
            val selectionEnd = if (end > start && text.getOrNull(end - 1) == '\n') end - 1 else end
            val to = text.indexOf('\n', selectionEnd).let { if (it < 0) text.length else it }
            val lines = text.substring(from, to).split('\n')
            val prefix =
                when (format) {
                    MarkdownFormat.Heading -> "## "
                    MarkdownFormat.Bullet -> "- "
                    MarkdownFormat.Numbered -> "1. "
                    MarkdownFormat.Task -> "- [ ] "
                    else -> "> "
                }
            val numberedPrefix = Regex("^\\d+[.)] ")
            val prefixes =
                lines.map {
                    if (format == MarkdownFormat.Numbered) numberedPrefix.find(it)?.value.orEmpty()
                    else prefix.takeIf { p -> it.startsWith(p) }.orEmpty()
                }
            val remove = prefixes.all { it.isNotEmpty() }
            val inserted =
                lines
                    .mapIndexed { index, line ->
                        if (remove) line.removePrefix(prefixes[index])
                        else
                            (if (format == MarkdownFormat.Numbered) "${index + 1}. " else prefix) +
                                line
                    }
                    .joinToString("\n")
            val selection =
                if (start == end)
                    TextRange(
                        (start + if (remove) -prefixes.first().length else prefix.length).coerceIn(
                            from,
                            from + inserted.length,
                        )
                    )
                else TextRange(from, from + inserted.length)
            replace(from, to, inserted, selection)
        }
    }
}

/** Continue a list on Return; Return on an empty item exits it. Leave IME composition alone. */
fun continueMarkdownList(previous: TextFieldValue, next: TextFieldValue): TextFieldValue {
    if (next.composition != null || !previous.selection.collapsed || !next.selection.collapsed)
        return next
    val cursor = previous.selection.start
    if (next.text != previous.text.substring(0, cursor) + "\n" + previous.text.substring(cursor))
        return next
    var fence: String? = null
    for (line in previous.text.substring(0, cursor).lineSequence()) {
        val marker = Regex("^ {0,3}(`{3,}|~{3,})").find(line)?.groupValues?.get(1) ?: continue
        if (fence == null) fence = marker
        else if (marker.first() == fence.first() && marker.length >= fence.length) fence = null
    }
    if (fence != null) return next
    val lineStart =
        previous.text.lastIndexOf('\n', cursor - 1).let { if (cursor == 0) 0 else it + 1 }
    val line = previous.text.substring(lineStart, cursor)
    val match =
        Regex("^(\\s*)(?:(- \\[[ xX]\\] )|([-+*] )|(\\d+)([.)] )|(> ))(.*)$").matchEntire(line)
            ?: return next
    if (match.groupValues[7].isBlank()) {
        return TextFieldValue(next.text.removeRange(lineStart, cursor + 1), TextRange(lineStart))
    }
    val marker =
        when {
            match.groupValues[2].isNotEmpty() -> "- [ ] "
            match.groupValues[3].isNotEmpty() -> match.groupValues[3]
            match.groupValues[4].isNotEmpty() ->
                "${(match.groupValues[4].toLongOrNull() ?: return next) + 1}${match.groupValues[5]}"
            else -> "> "
        }
    val prefix = match.groupValues[1] + marker
    return TextFieldValue(
        next.text.substring(0, cursor + 1) + prefix + next.text.substring(cursor + 1),
        TextRange(cursor + 1 + prefix.length),
    )
}
