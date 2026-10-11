package dev.cue.rebuild

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class MarkdownEditingTest {
    @Test
    fun wrapsSelectionAndTogglesWithoutLosingSurroundingText() {
        val value = TextFieldValue("hello world!", TextRange(6, 11))
        val bold = formatMarkdown(value, MarkdownFormat.Bold)
        assertEquals("hello **world**!", bold.text)
        assertEquals(TextRange(8, 13), bold.selection)
        assertEquals(value, formatMarkdown(bold, MarkdownFormat.Bold))
    }

    @Test
    fun cursorStaysInsideEmptyMarkers() {
        val result = formatMarkdown(TextFieldValue("hello ", TextRange(6)), MarkdownFormat.Italic)
        assertEquals("hello __", result.text)
        assertEquals(TextRange(7), result.selection)
    }

    @Test
    fun linkSelectsDestinationAndPreservesLabel() {
        val result =
            formatMarkdown(TextFieldValue("read docs", TextRange(5, 9)), MarkdownFormat.Link)
        assertEquals("read [docs](https://)", result.text)
        assertEquals("https://", result.text.substring(result.selection.min, result.selection.max))
    }

    @Test
    fun numberedListFormatsWholeSelectedLinesAndToggles() {
        val result =
            formatMarkdown(
                TextFieldValue("first\nsecond\nuntouched", TextRange(0, 12)),
                MarkdownFormat.Numbered,
            )
        assertEquals("1. first\n2. second\nuntouched", result.text)
        assertEquals(
            "first\nsecond\nuntouched",
            formatMarkdown(result, MarkdownFormat.Numbered).text,
        )
    }

    @Test
    fun returnContinuesNumbersTasksAndIndentedBullets() {
        for ((input, expected) in
            listOf(
                "9. item" to "9. item\n10. ",
                "- [x] done" to "- [x] done\n- [ ] ",
                "  - item" to "  - item\n  - ",
                "> quote" to "> quote\n> ",
            )) {
            val result = enter(input)
            assertEquals(expected, result.text)
            assertEquals(TextRange(expected.length), result.selection)
        }
    }

    @Test
    fun emptyItemExitsListAndCodeFencesStayLiteral() {
        assertEquals("- item\n", enter("- item\n- ").text)
        assertEquals("```\n- literal\n", enter("```\n- literal").text)
        assertEquals("```\n- code\n```\n- item\n- ", enter("```\n- code\n```\n- item").text)
    }

    @Test
    fun pastingOrComposingDoesNotRewriteText() {
        val old = TextFieldValue("- item", TextRange(6))
        val paste = TextFieldValue("- item\nmore\ntext", TextRange(16))
        assertEquals(paste, continueMarkdownList(old, paste))
        val composing = TextFieldValue("- item\n", TextRange(7), TextRange(0, 7))
        assertEquals(composing, continueMarkdownList(old, composing))
    }

    @Test
    fun codeBlockContainsBackticksWithoutClosingFence() {
        val text = "a ``` b"
        val result =
            formatMarkdown(
                TextFieldValue(text, TextRange(0, text.length)),
                MarkdownFormat.CodeBlock,
            )
        assertEquals("````\na ``` b\n````", result.text)
        assertEquals(text, result.text.substring(result.selection.min, result.selection.max))
    }

    private fun enter(text: String) =
        continueMarkdownList(
            TextFieldValue(text, TextRange(text.length)),
            TextFieldValue("$text\n", TextRange(text.length + 1)),
        )
}
