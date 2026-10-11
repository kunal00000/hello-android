package dev.cue.rebuild

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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

@OptIn(ExperimentalLayoutApi::class)
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var handleDrag by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    // Visibility also works for floating keyboards, whose bottom inset is zero.
    val keyboardVisible = WindowInsets.isImeVisible
    val hardwareKeyboard =
        LocalConfiguration.current.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS
    val editing = focused && (keyboardVisible || hardwareKeyboard) || preview
    LaunchedEffect(keyboardVisible) {
        if (!keyboardVisible && !preview && !hardwareKeyboard) {
            expanded = false
            formatting = false
        }
    }
    val send = value.isNotBlank() || canSend
    LaunchedEffect(value) {
        if (editor.text != value) {
            if (value.isEmpty()) {
                expanded = false
                formatting = false
            }
            editor = TextFieldValue(value, TextRange(value.length))
        }
        if (value.isEmpty()) preview = false
    }
    fun edit(next: TextFieldValue) {
        editor = next
        if (next.text != value) onChange(next.text)
    }
    fun resumeEditing() {
        preview = false
        focus.requestFocus()
        keyboard?.show()
    }
    fun applyFormat(format: MarkdownFormat) {
        edit(formatMarkdown(editor, format))
        resumeEditing()
    }
    BackHandler(expanded || preview || formatting) {
        when {
            preview -> resumeEditing()
            expanded -> expanded = false
            else -> formatting = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().excludeNavigationGestures().testTag("composer")) {
        // The chat provides the space above the IME. Leave room for the grip and action row.
        val expandedFieldHeight = (maxHeight - 92.dp).coerceIn(56.dp, 520.dp)
        val border = Cue.Ink.copy(alpha = .10f)
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Cue.ComposerSurface,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .drawBehind {
                    // An open outline joins the keyboard/system bar without a lower boundary.
                    val r = 20.dp.toPx()
                    val path =
                        Path().apply {
                            moveTo(0f, size.height)
                            lineTo(0f, r)
                            arcTo(Rect(0f, 0f, r * 2, r * 2), 180f, 90f, false)
                            lineTo(size.width - r, 0f)
                            arcTo(Rect(size.width - r * 2, 0f, size.width, r * 2), 270f, 90f, false)
                            lineTo(size.width, size.height)
                        }
                    drawPath(path, border, style = Stroke(1.dp.toPx()))
                }
                .animateContentSize(tween(180))
                .padding(6.dp)
        ) {
            if (editing) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .draggable(
                            state = rememberDraggableState { handleDrag += it },
                            orientation = Orientation.Vertical,
                            onDragStarted = { handleDrag = 0f },
                            onDragStopped = {
                                val threshold = with(density) { 24.dp.toPx() }
                                if (handleDrag < -threshold) expanded = true
                                else if (handleDrag > threshold) expanded = false
                            },
                        )
                        .semantics {
                            contentDescription =
                                if (expanded) "Collapse composer" else "Expand composer"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(36.dp, 3.dp)
                            .background(Cue.Ink.copy(alpha = .14f), CircleShape)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!editing && onAdd != null) {
                    ComposerAction("Add attachment", onClick = onAdd, tonal = true) {
                        Icon(Glyph.Plus, size = 22.dp)
                    }
                }
                // One field stays mounted as the layout changes, retaining the cursor, selection
                // and IME.
                Box(
                    Modifier.weight(1f)
                        .padding(horizontal = 12.dp, vertical = if (editing) 8.dp else 0.dp)
                        .then(
                            if (expanded && editing) Modifier.height(expandedFieldHeight)
                            else Modifier
                        )
                ) {
                    BasicTextField(
                        editor,
                        { edit(continueMarkdownList(editor, it)) },
                        Modifier.fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { focused = it.isFocused }
                            .then(
                                if (preview) Modifier.height(0.dp).clearAndSetSemantics {}
                                else if (expanded && editing) Modifier.fillMaxHeight() else Modifier
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
                        textStyle = Cue.ChatText.copy(color = Cue.Ink),
                        cursorBrush = SolidColor(Cue.Blue),
                        maxLines = if (!editing) 1 else if (expanded) Int.MAX_VALUE else 5,
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
                    if (preview) {
                        Box(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (expanded) Modifier.fillMaxHeight()
                                    else Modifier.heightIn(max = 200.dp)
                                )
                                .verticalScroll(rememberScrollState())
                                .testTag("markdown-preview")
                        ) {
                            MarkdownText(value)
                        }
                    }
                }
                if (!editing) {
                    ComposerSendAction(busy, send, onMic, onSend, onStop)
                }
            }
            if (editing) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (formatting) {
                        ComposerAction(
                            "Close formatting",
                            tonal = true,
                            onClick = { formatting = false },
                        ) {
                            Icon(Glyph.Close, size = 20.dp)
                        }
                        Row(
                            Modifier.weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .testTag("markdown-tools")
                        ) {
                            MarkdownFormat.entries.forEach { format ->
                                ComposerAction(
                                    format.description,
                                    enabled = !preview,
                                    onClick = { applyFormat(format) },
                                ) {
                                    Text(
                                        format.label,
                                        color = Cue.Ink.copy(alpha = if (preview) .35f else 1f),
                                        fontSize = 16.sp,
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
                    } else {
                        if (onAdd != null)
                            ComposerAction("Add attachment", tonal = true, onClick = onAdd) {
                                Icon(Glyph.Plus, size = 22.dp)
                            }
                        ComposerAction("Markdown formatting", onClick = { formatting = true }) {
                            Text("Aa", fontSize = 19.sp, color = Cue.Muted)
                        }
                        if (onMic != null)
                            ComposerAction("Voice input", onClick = onMic) {
                                Icon(Glyph.Mic, tint = Cue.Muted, size = 22.dp)
                            }
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            if (preview) resumeEditing()
                            else {
                                preview = true
                                keyboard?.hide()
                            }
                        },
                        enabled = value.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            if (preview) "Edit" else "Preview",
                            fontSize = 13.sp,
                            color = if (value.isNotBlank()) Cue.Blue else Cue.Faint,
                        )
                    }
                    ComposerSendAction(busy, send, null, onSend, onStop)
                }
            }
        }
    }
}

/** Shared hit target and press feedback for the compact, editing and formatting rows. */
@Composable
private fun ComposerAction(
    description: String,
    enabled: Boolean = true,
    tonal: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(42.dp)
                .background(
                    if (tonal) Cue.Ink.copy(alpha = .045f) else Color.Transparent,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun ComposerSendAction(
    busy: Boolean,
    send: Boolean,
    onMic: (() -> Unit)?,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val description =
        if (busy) "Stop generating"
        else if (send || onMic == null) "Send message" else "Voice input"
    val active = busy || send
    Box(
        Modifier.size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = active || onMic != null, role = Role.Button) {
                when {
                    busy -> onStop()
                    send -> onSend()
                    else -> onMic?.invoke()
                }
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp)
                .background(if (active) Cue.Blue else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (busy) Glyph.Stop else if (send || onMic == null) Glyph.Up else Glyph.Mic,
                tint = if (active) Color.White else if (onMic != null) Cue.Muted else Cue.Faint,
                size = 22.dp,
            )
        }
    }
}
