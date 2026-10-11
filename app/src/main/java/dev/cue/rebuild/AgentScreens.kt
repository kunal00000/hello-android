package dev.cue.rebuild

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

@Composable
fun AgentScreen(state: CueState, agent: Agent, onAvatar: () -> Unit, onPhone: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Header(
            "",
            state::back,
            trailing = {
                Box {
                    RoundButton(Glyph.More, "More", onClick = { menu = true })
                    if (menu)
                        ActionMenu(
                            listOf(
                                Glyph.Swap to "Change Avatar",
                                Glyph.Edit to "Rename",
                                Glyph.Pin to
                                    if (agent.pinned) "Unpin this Agent" else "Pin this Agent",
                                Glyph.Copy to "Copy Agent ID",
                            ),
                            { menu = false },
                        ) {
                            when (it) {
                                "Change Avatar" -> onAvatar()
                                "Rename" -> rename = true
                                "Copy Agent ID" ->
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("Agent ID", agent.id))
                                else -> state.update(agent.copy(pinned = !agent.pinned))
                            }
                        }
                }
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(agent.avatar, 80.dp, Modifier.clickable(onClick = onAvatar))
                Spacer(Modifier.height(12.dp))
                Text(agent.name, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                        "Mail" to Glyph.Mail,
                        "Computer" to Glyph.Computer,
                        "Phone" to Glyph.Phone,
                        "Payment" to Glyph.Wallet,
                    )
                    .forEach { (label, glyph) ->
                        Column(
                            Modifier.width(108.dp)
                                .height(86.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    when (label) {
                                        "Mail" -> state.open(Screen.Mail)
                                        "Computer" -> state.open(Screen.Computer)
                                        "Phone" -> onPhone()
                                        else -> state.open(Screen.Wallet)
                                    }
                                }
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                Icon(glyph, size = 30.dp)
                                if (label == "Phone" || label == "Payment")
                                    Box(
                                        Modifier.align(Alignment.BottomEnd)
                                            .offset(x = 4.dp, y = 2.dp)
                                            .size(14.dp)
                                            .background(Cue.Blue, CircleShape)
                                            .border(
                                                .7.dp,
                                                MaterialTheme.colorScheme.surface,
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Glyph.Plus, tint = Color.White, size = 10.dp)
                                    }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 14.sp)
                        }
                    }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Configuration",
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Cue.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Group(Modifier.padding(horizontal = Cue.PageInset)) {
                SettingRow(
                    "Instructions",
                    Glyph.Book,
                    onClick = { state.open(Screen.Instructions) },
                )
                SettingRow(
                    "Memory",
                    Glyph.Memory,
                    divider = false,
                    onClick = { state.open(Screen.Memory) },
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Integrations",
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Cue.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Group(Modifier.padding(horizontal = Cue.PageInset)) {
                SettingRow(
                    "Channels, apps & devices",
                    Glyph.Box,
                    divider = false,
                    onClick = { state.open(Screen.Integrations) },
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Files",
                Modifier.padding(horizontal = 24.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Cue.Muted,
            )
            Spacer(Modifier.height(12.dp))
            val files = agent.messages.flatMap { it.attachments }.distinctBy { it.path }
            Group(Modifier.padding(horizontal = Cue.PageInset)) {
                if (files.isEmpty()) {
                    EmptyState(Glyph.File, "No files yet", Modifier.padding(vertical = 28.dp))
                } else {
                    files.forEachIndexed { index, file ->
                        SettingRow(
                            file.name,
                            Glyph.File,
                            divider = index < files.lastIndex,
                            onClick = { openAttachment(context, file) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (rename)
        RenameDialog(agent.name, { rename = false }) {
            state.update(agent.copy(name = it))
            rename = false
        }
}

@Composable
fun RenameDialog(initial: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = dismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Group(Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Rename", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .height(48.dp)
                        .background(Cue.Bubble, CircleShape)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        text,
                        { text = it },
                        Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = Cue.Ink),
                    )
                    Icon(Glyph.Close, Modifier.clickable { text = "" }, Cue.Muted, 16.dp)
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillButton("Cancel", Modifier.weight(1f), primary = false, onClick = dismiss)
                    PillButton(
                        "Save",
                        Modifier.weight(1f),
                        enabled = text.isNotBlank() && text.trim() != initial,
                        onClick = { save(text.trim()) },
                    )
                }
            }
        }
    }
}

@Composable
fun InstructionsScreen(state: CueState, agent: Agent) {
    var text by remember(agent.id) { mutableStateOf(agent.instructions) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Header(
            "Instructions",
            state::back,
            trailing = {
                RoundButton(
                    Glyph.Check,
                    "Save instructions",
                    enabled = text != agent.instructions,
                    onClick = {
                        state.update(agent.copy(instructions = text))
                        state.back()
                    },
                )
            },
        )
        BasicTextField(
            text,
            { text = it },
            Modifier.padding(16.dp, 8.dp, 16.dp, 16.dp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp))
                .padding(16.dp),
            textStyle =
                TextStyle(color = LocalContentColor.current, fontSize = 16.sp, lineHeight = 24.sp),
            cursorBrush = SolidColor(Cue.Blue),
            decorationBox = {
                if (text.isEmpty())
                    Text(
                        "Tell the Agent how to work, what to prioritize, and what to avoid",
                        color = Cue.Faint,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                    )
                it()
            },
        )
    }
}

@Composable
fun MemoryScreen(state: CueState, agent: Agent) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Header("Memory", state::back)
        Box(Modifier.weight(1f)) {
            if (agent.memories.isEmpty())
                EmptyState(
                    Glyph.Memory,
                    "No memories yet",
                    Modifier.align(Alignment.Center).offset(y = (-54).dp),
                )
            else
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(agent.memories) { memory ->
                        Group {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(memory, Modifier.weight(1f))
                                Icon(
                                    Glyph.Trash,
                                    Modifier.clickable {
                                        state.update(agent.copy(memories = agent.memories - memory))
                                    },
                                    Cue.Muted,
                                    20.dp,
                                )
                            }
                        }
                    }
                }
        }
        Composer(
            text,
            { text = it },
            "Ask ${agent.name} to remember...",
            onSend = {
                state.remember(agent.id, text)
                text = ""
            },
        )
    }
}

val AvatarFaces =
    listOf(
        "wide_eyes",
        "curious",
        "side_eye",
        "skeptical",
        "bashful",
        "cheerful",
        "wink",
        "focused",
        "puzzled",
        "stern",
    )
val AvatarShapes =
    listOf(
        "square",
        "chat",
        "heart",
        "star",
        "briefcase",
        "triangle",
        "fire",
        "badge",
        "bread",
        "moon",
    )
val AvatarColors =
    listOf(
        0xFF8E8E8E,
        0xFF248BFF,
        0xFFF75555,
        0xFFFFC000,
        0xFFB17A4D,
        0xFF56AE86,
        0xFFB681DB,
        0xFFEF90BD,
    )

@Composable
fun AvatarEditor(initial: AvatarSpec, dismiss: () -> Unit, save: (AvatarSpec) -> Unit) {
    var avatar by remember { mutableStateOf(initial) }
    var colors by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Glyph.Close, "Cancel avatar", onClick = dismiss)
            Text(
                "Change avatar",
                Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            RoundButton(
                Glyph.Check,
                "Save avatar",
                background = Cue.Blue,
                tint = Color.White,
                onClick = { save(avatar) },
            )
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Avatar(avatar, 96.dp)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.size(36.dp)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red,
                            )
                        ),
                        CircleShape,
                    )
                    .padding(3.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .background(Color(avatar.color), CircleShape)
                    .clickable(role = Role.Button) { colors = !colors }
                    .semantics {
                        contentDescription = "Avatar color"
                        stateDescription = if (colors) "Expanded" else "Collapsed"
                    }
            )
            if (colors)
                Row(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AvatarColors.forEachIndexed { index, color ->
                        Box(
                            Modifier.size(26.dp)
                                .background(Color(color), CircleShape)
                                .selectable(avatar.color == color, role = Role.RadioButton) {
                                    avatar = avatar.copy(color = color)
                                }
                                .semantics { contentDescription = "Color ${index + 1}" }
                        )
                    }
                }
            Spacer(Modifier.height(32.dp))
            AvatarFaces.chunked(5).forEach { faces ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    faces.forEach { face ->
                        Box(
                            Modifier.size(56.dp)
                                .background(
                                    if (avatar.face == face) Cue.Page else Color.Transparent,
                                    RoundedCornerShape(14.dp),
                                )
                                .selectable(avatar.face == face, role = Role.RadioButton) {
                                    avatar = avatar.copy(face = face)
                                }
                                .semantics {
                                    contentDescription = "Face ${face.replace('_', ' ')}"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(48.dp).background(Cue.Page, CircleShape)) {
                                Asset("agent_avatar_face_$face", Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            HorizontalDivider(Modifier.padding(24.dp, 16.dp), color = Cue.Bubble)
            AvatarShapes.chunked(5).forEachIndexed { row, shapes ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    shapes.forEachIndexed { i, shape ->
                        Box(
                            Modifier.size(56.dp)
                                .background(
                                    if (avatar.shape == shape) Cue.Page else Color.Transparent,
                                    RoundedCornerShape(14.dp),
                                )
                                .selectable(avatar.shape == shape, role = Role.RadioButton) {
                                    avatar = avatar.copy(shape = shape)
                                }
                                .semantics {
                                    contentDescription = "Shape ${shape.replace('_', ' ')}"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Asset(
                                "agent_avatar_shape_$shape",
                                Modifier.size(48.dp),
                                Color(AvatarColors[if (row == 0) i else listOf(1, 2, 0, 4, 3)[i]]),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CreateAgentScreen(dismiss: () -> Unit, create: (String, AvatarSpec) -> Unit) {
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AvatarSpec("bread", "side_eye", 0xFF248BFF)) }
    var editing by remember { mutableStateOf(false) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (editing) {
        AvatarEditor(avatar, { editing = false }) {
            avatar = it
            editing = false
        }
        return
    }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column(
        Modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Glyph.Close, "Close create", onClick = dismiss)
            Text(
                "Create Agent",
                Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            PillButton(
                "Create",
                Modifier.width(70.dp),
                name.isNotBlank(),
                onClick = { create(name, avatar) },
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            Avatar(avatar, 120.dp)
            RoundButton(
                Glyph.Edit,
                "Edit avatar",
                Modifier.align(Alignment.BottomEnd),
                onClick = { editing = true },
            )
        }
        Spacer(Modifier.height(36.dp))
        BasicTextField(
            name,
            { name = it },
            Modifier.focusRequester(focus)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(Cue.Page, CircleShape)
                .padding(16.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = Cue.Ink, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Cue.Blue),
            decorationBox = {
                Box(contentAlignment = Alignment.Center) {
                    if (name.isEmpty()) Text("Agent name", color = Cue.Faint, fontSize = 16.sp)
                    it()
                }
            },
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun CreateGroupScreen(state: CueState, dismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Glyph.Close, "Close group", onClick = dismiss)
            Text(
                "Create Group Chat",
                Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            PillButton(
                "Create",
                Modifier.width(70.dp).height(40.dp),
                selected.isNotEmpty(),
                onClick = {
                    state.createGroup(selected)
                    dismiss()
                },
            )
        }
        BasicTextField(
            query,
            { query = it },
            Modifier.padding(16.dp, 8.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(Cue.Bubble, CircleShape)
                .padding(16.dp)
                .focusRequester(focus),
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = Cue.Ink),
            cursorBrush = SolidColor(Cue.Blue),
            decorationBox = {
                if (query.isEmpty()) Text("Search Agent", color = Cue.Faint, fontSize = 16.sp)
                it()
            },
        )
        if (query.isNotBlank())
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(
                    state.agents.filter { !it.group && it.name.contains(query, true) },
                    key = { it.id },
                ) { agent ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(agent.id in selected, role = Role.Checkbox) {
                                selected =
                                    if (agent.id in selected) selected - agent.id
                                    else selected + agent.id
                            }
                            .semantics { contentDescription = "Select ${agent.name}" }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(agent.avatar, 40.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(agent.name, fontSize = 16.sp)
                            Text(
                                "${agent.name.lowercase()} · Local agent",
                                fontSize = 14.sp,
                                color = Cue.Muted,
                            )
                        }
                        Box(
                            Modifier.size(24.dp)
                                .border(
                                    1.5.dp,
                                    if (agent.id in selected) Cue.Blue else Cue.Muted,
                                    CircleShape,
                                )
                                .background(
                                    if (agent.id in selected) Cue.Blue else Color.Transparent,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (agent.id in selected)
                                Icon(Glyph.Check, tint = Color.White, size = 16.dp)
                        }
                    }
                }
            }
    }
}
