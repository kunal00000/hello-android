package dev.cue.rebuild

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

@Composable
fun HomeScreen(state: CueState, onCreate: (Boolean) -> Unit, onScan: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Glyph.Settings, "Settings", onClick = { state.open(Screen.Settings) })
            Spacer(Modifier.weight(1f))
            RoundButton(Glyph.Search, "Search", onClick = { state.open(Screen.Search) })
            Spacer(Modifier.width(10.dp))
            Box {
                RoundButton(Glyph.Plus, "Create", onClick = { menu = !menu })
                if (menu)
                    ActionMenu(
                        listOf(
                            Glyph.Bot to "Agent",
                            Glyph.Scan to "Scan & create",
                            Glyph.Group to "Group Chat",
                        ),
                        { menu = false },
                    ) {
                        when (it) {
                            "Agent" -> onCreate(false)
                            "Group Chat" -> onCreate(true)
                            else -> onScan()
                        }
                    }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp)) {
            items(state.agents.sortedByDescending { it.pinned }, key = { it.id }) { agent ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { state.open(Screen.Chat, agent.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(agent.avatar, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                agent.name,
                                Modifier.weight(1f),
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                            )
                            if (agent.pinned) Icon(Glyph.Pin, tint = Cue.Muted, size = 12.dp)
                            if (agent.messages.isNotEmpty())
                                Text(
                                    agent.messages.last().createdAt.let { time ->
                                        if (time == 0L) ""
                                        else
                                            android.text.format.DateUtils.getRelativeTimeSpanString(
                                                    time,
                                                    System.currentTimeMillis(),
                                                    android.text.format.DateUtils.MINUTE_IN_MILLIS,
                                                )
                                                .toString()
                                    },
                                    color = Cue.Muted,
                                    fontSize = 12.sp,
                                )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            agent.messages.lastOrNull()?.text ?: "Send a message to ${agent.name}",
                            color = Cue.Muted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// Ease opacity at both ends so the fade has no visible start or opaque shelf.
private fun chatEdgeFade(
    color: Color,
    fadeOut: Boolean = false,
    endY: Float = Float.POSITIVE_INFINITY,
): Brush {
    val stops =
        Array(25) { index ->
            val t = index / 24f
            val opacity = t * t * t * (t * (t * 6f - 15f) + 10f)
            t to color.copy(alpha = if (fadeOut) 1f - opacity else opacity)
        }
    return Brush.verticalGradient(*stops, endY = endY)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(state: CueState, agent: Agent) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var draft by remember(agent.id) { mutableStateOf(agent.draft) }
    var attachments by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Message?>(null) }
    val list = rememberLazyListState()
    val busy = agent.id in state.busy
    var files by remember { mutableStateOf(emptyList<Attachment>()) }
    val attach =
        rememberAttachmentActions(
            context,
            {
                if (files.size < 3) files = files + it
                else state.error = "You can attach up to three files."
            },
            { state.error = it },
        )
    val speech =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let {
                    draft = it
                    state.update(state.agent(agent.id).copy(draft = it))
                }
        }
    // Follow a streaming response only while the reader is already at the bottom.
    var followResponse by rememberSaveable(agent.id) { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }
            .collect { (scrolling, more) -> if (scrolling) followResponse = !more }
    }
    // Re-entering a saved page must not replay a scroll intended for new content.
    val lastMessage = agent.messages.lastOrNull()
    val scrollRevision =
        "${agent.messages.size}:${lastMessage?.id}:${lastMessage?.text?.length}:$busy"
    var observedRevision by rememberSaveable(agent.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollRevision) {
        if (observedRevision != scrollRevision) {
            observedRevision = scrollRevision
            if (followResponse && agent.messages.isNotEmpty())
                list.scrollToItem(agent.messages.size - 1)
        }
    }
    val density = LocalDensity.current
    val backdrop = MaterialTheme.colorScheme.background
    var composerHeight by remember { mutableIntStateOf(0) }
    var footerHeight by
        rememberSaveable(density.density, density.fontScale) {
            mutableIntStateOf(with(density) { 100.dp.roundToPx() })
        }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val bubbleMaxWidth = maxWidth * .85f
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    top = 136.dp,
                    end = 16.dp,
                    bottom = with(density) { footerHeight.toDp() } + 12.dp,
                ),
        ) {
            itemsIndexed(agent.messages, key = { _, message -> message.id }) { index, message ->
                val previous = agent.messages.getOrNull(index - 1)
                // Keep nearby messages together; a new speaker or pause starts a new turn.
                val continuesGroup =
                    previous != null &&
                        previous.user == message.user &&
                        !previous.failed &&
                        !message.failed &&
                        previous.createdAt > 0 &&
                        message.createdAt - previous.createdAt in 0L..120_000L
                val gap = if (previous == null) 0.dp else if (continuesGroup) 4.dp else 16.dp
                Column(
                    Modifier.fillMaxWidth().padding(top = gap),
                    horizontalAlignment = if (message.user) Alignment.End else Alignment.Start,
                ) {
                    MessageBubble(
                        message,
                        Modifier.widthIn(max = bubbleMaxWidth)
                            .combinedClickable(onClick = {}, onLongClick = { selected = message }),
                    )
                    if (message.failed)
                        Text(
                            message.failureReason.ifEmpty { "The response could not finish." } +
                                " Tap to retry.",
                            Modifier.clickable {
                                    state.update(
                                        state
                                            .agent(agent.id)
                                            .copy(
                                                messages =
                                                    state.agent(agent.id).messages.filterNot {
                                                        it.id == message.id
                                                    }
                                            )
                                    )
                                    state.send(agent.id, message.text, message.attachments)
                                }
                                .padding(8.dp),
                            color = Color(0xFFD34D4D),
                            fontSize = 12.sp,
                        )
                }
            }
            if (busy) item { Box(Modifier.padding(top = 16.dp)) { TypingIndicator() } }
        }
        Box(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(148.dp)
                    .testTag("chat-header")
                    .excludeNavigationGestures()
                    .background(chatEdgeFade(backdrop, fadeOut = true))
                    .padding(horizontal = 16.dp)
        ) {
            RoundButton(
                Glyph.Back,
                "Back",
                Modifier.padding(top = 2.dp),
                onClick = {
                    keyboard?.hide()
                    state.back()
                },
            )
            Column(
                Modifier.align(Alignment.TopCenter).clickable {
                    keyboard?.hide()
                    state.open(Screen.Agent, agent.id)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(agent.avatar, 48.dp)
                Row(
                    Modifier.offset(y = (-2).dp)
                        .shadow(
                            6.dp,
                            CircleShape,
                            ambientColor = Color.Black.copy(alpha = .06f),
                            spotColor = Color.Black.copy(alpha = .06f),
                        )
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(.5.dp, Cue.Muted.copy(alpha = .13f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(agent.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Glyph.Next, tint = Cue.Muted, size = 14.dp)
                }
            }
            RoundButton(
                Glyph.Computer,
                "Computer",
                Modifier.align(Alignment.TopEnd).padding(top = 2.dp),
                onClick = { state.open(Screen.Computer, agent.id) },
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { footerHeight = it.height }
                .background(
                    chatEdgeFade(
                        backdrop,
                        endY =
                            with(density) {
                                // Let conversation content dissolve behind the collapsed control.
                                // Expanded question/attachment controls still need a solid
                                // backdrop.
                                (if (agent.pendingInputs.isNotEmpty() || files.isNotEmpty()) 80.dp
                                    else 144.dp)
                                    .toPx()
                            },
                    )
                )
                .padding(top = 80.dp)
        ) {
            agent.pendingInputs.firstOrNull()?.let { input ->
                if (input.deviceAction != null) DeviceRequestCard(state, agent, input)
                else
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Reply to continue",
                            style = Cue.Caption,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        input.choices.forEach { choice ->
                            PillButton(
                                choice.label,
                                Modifier.fillMaxWidth(),
                                enabled = !busy,
                                primary = false,
                            ) {
                                followResponse = true
                                state.send(agent.id, choice.label, optionId = choice.id)
                            }
                        }
                    }
            }
            if (files.isNotEmpty())
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    files.forEach { file ->
                        Row(
                            Modifier.background(Cue.Bubble, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Glyph.File, size = 16.dp)
                            Text(file.name, Modifier.padding(horizontal = 6.dp), fontSize = 12.sp)
                            Icon(
                                Glyph.Close,
                                Modifier.clickable { files = files - file }
                                    .semantics { contentDescription = "Remove ${file.name}" },
                                size = 14.dp,
                            )
                        }
                    }
                }
            Box(Modifier.fillMaxWidth().onSizeChanged { composerHeight = it.height }) {
                Composer(
                    draft,
                    {
                        draft = it
                        state.update(state.agent(agent.id).copy(draft = it))
                    },
                    "Send message to ${agent.name}",
                    busy,
                    canSend = files.isNotEmpty(),
                    onSend = {
                        val text = draft
                        if (state.send(agent.id, text, files)) {
                            draft = ""
                            followResponse = true
                            files = emptyList()
                        }
                    },
                    onStop = { state.cancel(agent.id) },
                    onAdd = { attachments = true },
                    onMic = {
                        runCatching {
                                speech.launch(
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                        )
                                )
                            }
                            .onFailure {
                                state.error = "Speech recognition is not available on this device."
                            }
                    },
                )
                if (attachments)
                    ActionMenu(
                        items =
                            listOf(
                                Glyph.Camera to "Camera",
                                Glyph.Image to "Photos",
                                Glyph.Clip to "Files",
                            ),
                        dismiss = { attachments = false },
                        alignment = Alignment.BottomStart,
                        offset = DpOffset(16.dp, -with(density) { composerHeight.toDp() }),
                    ) { label ->
                        attach(label)
                    }
            }
        }
    }
    selected?.let { message ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Message") },
            text = { Text(message.text, maxLines = 8, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(
                    onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Message", message.text))
                        selected = null
                    }
                ) {
                    Text("Copy")
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun MessageBubble(message: Message, modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            modifier
                .excludeNavigationGestures()
                .align(if (message.user) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (message.user) Cue.Blue else Cue.Bubble, RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                message.attachments.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Glyph.File,
                            tint = if (message.user) Color.White else Cue.Ink,
                            size = 18.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            file.name,
                            color = if (message.user) Color.White else Cue.Ink,
                            fontSize = 14.sp,
                        )
                    }
                }
                if (message.text.isNotEmpty())
                    Text(
                        message.text,
                        color = if (message.user) Color.White else Cue.Ink,
                        style = Cue.ChatText,
                    )
            }
        }
    }
}

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
    Row(
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
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
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
        BasicTextField(
            value,
            onChange,
            Modifier.weight(1f).padding(horizontal = 8.dp).semantics {
                contentDescription = placeholder
            },
            textStyle = Cue.ChatText.copy(color = LocalContentColor.current),
            cursorBrush = SolidColor(Cue.Blue),
            maxLines = 5,
            decorationBox = {
                if (value.isEmpty())
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
