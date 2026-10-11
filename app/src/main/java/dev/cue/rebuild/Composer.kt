package dev.cue.rebuild

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

@Composable
fun Composer(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    busy: Boolean = false,
    onSend: () -> Unit,
    onStop: () -> Unit = {},
    onAdd: (() -> Unit)? = null,
    onMic: (() -> Unit)? = null,
    canSend: Boolean = false,
) {
    var editor by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(value, TextRange(value.length)))
        }
    var formatting by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(value) {
        if (editor.text != value) editor = TextFieldValue(value, TextRange(value.length))
        if (value.isEmpty()) preview = false
    }
    fun edit(next: TextFieldValue) {
        editor = next
        if (next.text != value) onChange(next.text)
    }
    fun applyFormat(format: MarkdownFormat) {
        edit(formatMarkdown(editor, format))
        focus.requestFocus()
        keyboard?.show()
    }
    Column(
        Modifier.fillMaxWidth()
            .excludeNavigationGestures()
            .testTag("composer")
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            .shadow(
                20.dp,
                RoundedCornerShape(28.dp),
                ambientColor = Color.Black.copy(alpha = .08f),
                spotColor = Color.Black.copy(alpha = .05f),
            )
            .background(Cue.ComposerSurface, RoundedCornerShape(28.dp))
            .border(1.dp, Cue.Ink.copy(alpha = .10f), RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        AnimatedVisibility(formatting) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .testTag("markdown-tools")
                    ) {
                        MarkdownFormat.entries.forEach { format ->
                            Box(
                                Modifier.height(40.dp)
                                    .widthIn(min = 40.dp)
                                    .clickable(enabled = !preview, role = Role.Button) {
                                        applyFormat(format)
                                    }
                                    .semantics { contentDescription = format.description }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    format.label,
                                    color = Cue.Ink.copy(alpha = if (preview) .35f else 1f),
                                    fontSize = 15.sp,
                                    fontWeight =
                                        if (format == MarkdownFormat.Bold) FontWeight.Bold
                                        else FontWeight.Medium,
                                    fontStyle =
                                        if (format == MarkdownFormat.Italic) FontStyle.Italic
                                        else FontStyle.Normal,
                                    textDecoration =
                                        if (format == MarkdownFormat.Strike)
                                            TextDecoration.LineThrough
                                        else TextDecoration.None,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            preview = !preview
                            if (preview) keyboard?.hide()
                            else {
                                focus.requestFocus()
                                keyboard?.show()
                            }
                        },
                        enabled = value.isNotBlank(),
                    ) {
                        Text(if (preview) "Edit" else "Preview", fontSize = 13.sp, color = Cue.Blue)
                    }
                }
                HorizontalDivider(
                    color = Cue.Ink.copy(alpha = .07f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        if (preview) {
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                    .testTag("markdown-preview")
            ) {
                MarkdownText(value)
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onAdd != null)
                Box(
                    Modifier.size(36.dp).clickable(role = Role.Button, onClick = onAdd).semantics {
                        contentDescription = "Add attachment"
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Glyph.Plus, size = 24.dp)
                }
            else Spacer(Modifier.width(10.dp))
            // Keep the field in composition during preview so selection and IME state survive.
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                BasicTextField(
                    editor,
                    { edit(continueMarkdownList(editor, it)) },
                    Modifier.fillMaxWidth()
                        .focusRequester(focus)
                        .then(
                            if (preview) Modifier.height(0.dp).clearAndSetSemantics {} else Modifier
                        )
                        .semantics { if (!preview) contentDescription = placeholder }
                        .onPreviewKeyEvent { event ->
                            if (
                                preview ||
                                    event.type != KeyEventType.KeyDown ||
                                    !event.isCtrlPressed
                            )
                                false
                            else {
                                val format =
                                    when (event.key) {
                                        Key.B -> MarkdownFormat.Bold
                                        Key.I -> MarkdownFormat.Italic
                                        Key.K -> MarkdownFormat.Link
                                        else -> null
                                    }
                                if (format != null) applyFormat(format)
                                format != null
                            }
                        },
                    readOnly = preview,
                    textStyle = Cue.ChatText.copy(color = LocalContentColor.current),
                    cursorBrush = SolidColor(Cue.Blue),
                    maxLines = 5,
                    decorationBox = {
                        if (value.isEmpty() && !preview)
                            Text(
                                placeholder,
                                style = Cue.ChatText,
                                color = Cue.Faint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        it()
                    },
                )
                if (preview) Text("Markdown preview", style = Cue.Caption, color = Cue.Muted)
            }
            Box(
                Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(if (formatting) Cue.Ink.copy(alpha = .06f) else Color.Transparent)
                    .clickable(role = Role.Button) {
                        formatting = !formatting
                        if (!formatting) preview = false
                    }
                    .semantics {
                        contentDescription = "Markdown formatting"
                        selected = formatting
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Aa", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Cue.Muted)
            }
            val send = value.isNotBlank() || canSend
            Box(
                Modifier.size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (send) Cue.Blue else if (onAdd == null) Cue.Bubble else Color.Transparent
                    )
                    .semantics {
                        contentDescription =
                            if (busy) "Stop generating"
                            else if (send || onMic == null) "Send message" else "Voice input"
                    }
                    .clickable(enabled = busy || send || onMic != null) {
                        when {
                            busy -> onStop()
                            send -> onSend()
                            else -> onMic?.invoke()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (busy) Glyph.Stop else if (send || onMic == null) Glyph.Up else Glyph.Mic,
                    tint =
                        if (send) Color.White
                        else if (onMic == null) Cue.Faint else LocalContentColor.current,
                    size = 22.dp,
                )
            }
        }
    }
}
