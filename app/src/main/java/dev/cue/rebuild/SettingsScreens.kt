package dev.cue.rebuild

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(state: CueState, onPhone: () -> Unit) {
    var appearance by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { state.checkBackend() }
    Column(Modifier.fillMaxSize()) {
        Header("Settings", state::back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Group {
                Row(
                    Modifier.fillMaxWidth().clickable { state.open(Screen.Account) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).background(Color(0xFF67493C), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.userName.take(1), fontSize = 26.sp, color = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(state.userName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Local account", fontSize = 14.sp, color = Cue.Muted)
                    }
                    Icon(Glyph.Next, tint = Cue.Muted, size = 18.dp)
                }
            }
            Group {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Agent connection", style = Cue.Title)
                            Text(
                                if (state.checkingBackend) "Checking connection…"
                                else state.backendStatus,
                                color = Cue.Muted,
                                style = Cue.Caption,
                            )
                        }
                        PillButton(
                            "Refresh",
                            Modifier.width(90.dp).height(40.dp),
                            enabled = !state.checkingBackend,
                            primary = false,
                            fontSize = 14.sp,
                            onClick = state::checkBackend,
                        )
                    }
                    HorizontalDivider(color = Cue.Bubble)
                    val agentCount = state.agents.count { !it.group }
                    val messageCount =
                        state.agents.sumOf { it.messages.count { message -> message.user } }
                    Text(
                        "$agentCount ${if (agentCount == 1) "agent" else "agents"} · $messageCount ${if (messageCount == 1) "message" else "messages"}",
                        style = Cue.Label,
                    )
                    Text(
                        "Your conversation history is saved on this device.",
                        style = Cue.Caption,
                        color = Cue.Muted,
                    )
                }
            }
            Group {
                SettingRow(
                    "Apps",
                    Glyph.Box,
                    "Not connected",
                    onClick = { state.open(Screen.Apps) },
                )
                SettingRow("Skills", Glyph.Skill, onClick = { state.open(Screen.Skills) })
                SettingRow(
                    "Email",
                    Glyph.Mail,
                    "Not connected",
                    onClick = { state.open(Screen.Mail) },
                )
                SettingRow("Phone", Glyph.Phone, "Not connected", onClick = onPhone)
                SettingRow(
                    "Computer",
                    Glyph.Computer,
                    "Not connected",
                    onClick = { state.open(Screen.Computer) },
                )
                SettingRow(
                    "Wallet",
                    Glyph.Wallet,
                    "Not connected",
                    divider = false,
                    onClick = { state.open(Screen.Wallet) },
                )
            }
            Group {
                SettingRow(
                    "Language",
                    Glyph.Globe,
                    state.language,
                    onClick = { state.open(Screen.Language) },
                )
                SettingRow(
                    "Appearance",
                    Glyph.Moon,
                    state.appearance,
                    onClick = { appearance = true },
                )
                SettingRow("App icon", Glyph.Square, onClick = { state.open(Screen.AppIcon) })
            }
            Group {
                SettingRow(
                    "Get help",
                    Glyph.Help,
                    onClick = {
                        state.error =
                            "Cue Replica runs locally with your superintelligence backend. Connect the phone over USB and run scripts/run-backend.sh."
                    },
                )
                SettingRow("Version", Glyph.Square, "v${BuildConfig.VERSION_NAME}", divider = false)
            }
        }
    }
    if (appearance)
        AlertDialog(
            onDismissRequest = { appearance = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    listOf("Follow system", "Light", "Dark").forEach { option ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    state.changeAppearance(option)
                                    appearance = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option, Modifier.weight(1f))
                            if (state.appearance == option) Icon(Glyph.Check, tint = Cue.Blue)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { appearance = false }) { Text("Cancel") } },
        )
}

private data class AppEntry(
    val name: String,
    val detail: String,
    val category: String,
    val color: Color,
)

private val apps =
    listOf(
        AppEntry(
            "MyFitnessPal",
            "Explore your MyFitnessPal nutrition data",
            "Health",
            Color(0xFF006BFF),
        ),
        AppEntry(
            "Withings",
            "Explore your Withings body, activity, and sleep",
            "Health",
            Color.Black,
        ),
        AppEntry(
            "Polar",
            "Explore your Polar training, activity, and recovery",
            "Health",
            Color(0xFFEC1535),
        ),
        AppEntry(
            "TrainingPeaks",
            "Explore your TrainingPeaks workouts",
            "Health",
            Color(0xFF3A76FF),
        ),
        AppEntry("Plaid", "Connect Plaid to securely read transactions", "Finance", Color.Black),
        AppEntry(
            "Clue",
            "Explore your Clue cycle and reproductive health",
            "Health",
            Color(0xFFFF4850),
        ),
        AppEntry(
            "Cronometer",
            "Explore your Cronometer nutrition and activity",
            "Health",
            Color(0xFFFF7045),
        ),
        AppEntry(
            "Fitbit",
            "Explore your Fitbit activity, sleep, and health",
            "Health",
            Color(0xFF00AABC),
        ),
        AppEntry("Oura", "Explore your sleep and readiness", "Health", Color.Black),
        AppEntry("Gmail", "Read and manage your email", "Productivity", Color(0xFFEA4335)),
        AppEntry(
            "Google Calendar",
            "Manage events and your schedule",
            "Productivity",
            Color(0xFF4285F4),
        ),
        AppEntry(
            "Google Drive",
            "Find and work with your files",
            "Productivity",
            Color(0xFF0F9D58),
        ),
        AppEntry("GitHub", "Work with repositories and issues", "Productivity", Color.Black),
        AppEntry(
            "Slack",
            "Search conversations in your workspace",
            "Productivity",
            Color(0xFF611F69),
        ),
    )

@Composable
fun AppsScreen(state: CueState) {
    var query by remember { mutableStateOf("") }
    val categories = listOf("All", "Finance", "Health", "Travel", "Productivity")
    val pager = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()
    val tabs = rememberLazyListState()
    LaunchedEffect(pager.currentPage) { tabs.animateScrollToItem(pager.currentPage) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    Column {
        Header("Apps", state::back)
        SearchField(query, { query = it }, "Search apps", Modifier.padding(16.dp, 8.dp))
        LazyRow(
            state = tabs,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(categories) { index, name ->
                Text(
                    name,
                    Modifier.clip(CircleShape)
                        .background(
                            if (pager.currentPage == index) Cue.Bubble else Color.Transparent
                        )
                        .selectable(
                            selected = pager.currentPage == index,
                            role = Role.Tab,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        )
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    color = if (pager.currentPage == index) Cue.Ink else Cue.Muted,
                    fontSize = 14.sp,
                )
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("app-categories"),
            verticalAlignment = Alignment.Top,
        ) { page ->
            val category = categories[page]
            val results =
                apps.filter {
                    (category == "All" || it.category == category) && it.name.contains(query, true)
                }
            if (results.isEmpty())
                EmptyState(Glyph.Search, "No apps found", Modifier.padding(top = 100.dp))
            else
                LazyColumn(
                    Modifier.padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    items(results, key = { it.name }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = app }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp)
                                    .border(1.dp, Cue.Bubble, RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val asset =
                                    when (app.name) {
                                        "Gmail" -> "gmail_icon"
                                        "Google Calendar" -> "google_calendar_icon"
                                        "GitHub" -> "github_icon"
                                        else -> null
                                    }
                                if (asset != null) Asset(asset, Modifier.size(25.dp))
                                else
                                    Box(
                                        Modifier.size(25.dp)
                                            .background(app.color, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            app.name.take(1),
                                            color = Color.White,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp, end = 14.dp)) {
                                Text(app.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    app.detail,
                                    color = Cue.Muted,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("Not connected", color = Cue.Muted, fontSize = 12.sp)
                        }
                        HorizontalDivider(
                            Modifier.padding(start = 68.dp, end = 16.dp),
                            color = Cue.Bubble,
                            thickness = .5.dp,
                        )
                    }
                }
        }
    }
    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(app.name) },
            text = {
                Text("${app.detail}. This connection is not available from the local backend yet.")
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } },
        )
    }
}

@Composable
fun SkillsScreen(state: CueState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { importSkill(context, uri) } }
                        .onSuccess { state.addSkill(it) }
                        .onFailure { state.error = it.message ?: "Could not read that skill file." }
                }
        }
    Column {
        Header("Skills", state::back)
        Group(Modifier.padding(16.dp)) {
            SettingRow(
                "Add Skills",
                Glyph.Plus,
                divider = false,
                onClick = { picker.launch("text/*") },
                trailing = {},
            )
        }
        if (state.skills.isEmpty())
            Text(
                "No Skills yet",
                Modifier.fillMaxWidth().padding(top = 24.dp),
                fontSize = 16.sp,
                color = Cue.Muted,
                textAlign = TextAlign.Center,
            )
        else
            Column(Modifier.padding(16.dp)) {
                state.skills.forEach { skill ->
                    Group {
                        SettingRow(
                            skill.lineSequence().first().trimStart('#', ' '),
                            Glyph.Skill,
                            onClick = { state.error = skill },
                            trailing = {
                                Icon(
                                    Glyph.Trash,
                                    Modifier.clickable { state.removeSkill(skill) },
                                    Cue.Muted,
                                )
                            },
                        )
                    }
                }
            }
    }
}

@Composable
fun MailScreen(state: CueState, agent: Agent) {
    var sent by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Header(
            "Mail",
            state::back,
            subtitle = "${agent.name.lowercase()} · Local account",
            trailing = {
                RoundButton(
                    Glyph.More,
                    "Mail options",
                    onClick = { state.error = "Mail delivery is not configured for this agent." },
                )
            },
        )
        Row(
            Modifier.padding(16.dp, 12.dp)
                .fillMaxWidth()
                .height(36.dp)
                .background(Cue.Bubble, CircleShape)
                .padding(2.dp)
        ) {
            listOf("Inbox", "Sent").forEachIndexed { index, label ->
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (sent == (index == 1)) MaterialTheme.colorScheme.surface
                            else Color.Transparent,
                            CircleShape,
                        )
                        .clickable { sent = index == 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 16.sp,
                        color = if (sent == (index == 1)) Cue.Ink else Cue.Muted,
                        fontWeight =
                            if (sent == (index == 1)) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            EmptyState(
                Glyph.Mail,
                "Email isn’t connected",
                Modifier.align(Alignment.Center).offset(y = (-70).dp),
            )
        }
    }
}

@Composable
fun ComputerScreen(state: CueState, agent: Agent) {
    val context = LocalContext.current
    val bitmap = remember {
        context.assets
            .open("cue/agent_sandbox_desktop.png")
            .use { BitmapFactory.decodeStream(it) }
            .asImageBitmap()
    }
    Column(Modifier.fillMaxSize()) {
        Header(
            "Computer",
            state::back,
            trailing = {
                RoundButton(
                    Glyph.Plus,
                    "Add computer",
                    onClick = { state.error = "Cloud computer provisioning is not connected yet." },
                )
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.padding(16.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${agent.name}'s computer",
                    fontSize = 16.sp,
                    color = Cue.Muted,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Not connected",
                    Modifier.background(Cue.Blue.copy(alpha = .08f), CircleShape)
                        .padding(6.dp, 2.dp),
                    fontSize = 12.sp,
                    color = Cue.Blue,
                )
            }
            Group {
                Box(Modifier.fillMaxWidth().height(222.dp)) {
                    Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Desktop preview", color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Glyph.Next, tint = Color.White, size = 18.dp)
                    }
                }
                SettingRow(
                    "Open terminal",
                    Glyph.Terminal,
                    value = "Not connected",
                    divider = false,
                )
            }
            Spacer(Modifier.height(48.dp))
            Icon(Glyph.Computer, Modifier.align(Alignment.CenterHorizontally), size = 48.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "Your agent can use its backend tools in chat. A remote desktop is not connected yet.",
                Modifier.fillMaxWidth(),
                fontSize = 16.sp,
                color = Cue.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            PillButton(
                "Not connected",
                modifier = Modifier.align(Alignment.CenterHorizontally).width(190.dp),
                enabled = false,
                onClick = { state.error = "Cloud computer provisioning is not connected yet." },
            )
        }
    }
}

@Composable
fun PhoneSheet(agent: Agent, dismiss: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        RoundButton(Glyph.Close, "Close phone setup", onClick = dismiss)
        Spacer(Modifier.height(32.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(agent.avatar, 64.dp)
            Icon(Glyph.More, Modifier.padding(16.dp), tint = Cue.Faint)
            Icon(Glyph.Phone, size = 64.dp)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Assign a virtual phone for ${agent.name}",
            Modifier.fillMaxWidth(),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "A phone provider isn’t connected yet. Calls and texts will become available after setup. You can still ask ${agent.name} to draft a message in chat.",
            Modifier.fillMaxWidth(),
            style = Cue.Body,
            color = Cue.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        PillButton("Not connected", Modifier.fillMaxWidth(), enabled = false, onClick = {})
        Spacer(Modifier.height(8.dp))
        PillButton("Done", Modifier.fillMaxWidth(), primary = false, onClick = dismiss)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SearchScreen(state: CueState) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, "Search tasks", Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Icon(Glyph.Close, Modifier.clickable { state.back() }, size = 24.dp)
        }
        if (query.isNotBlank())
            LazyColumn {
                items(
                    state.agents.filter {
                        it.name.contains(query, true) ||
                            it.messages.any { message -> message.text.contains(query, true) }
                    }
                ) { agent ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                state.back()
                                state.open(Screen.Chat, agent.id)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(agent.avatar, 40.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(agent.name)
                            Text(
                                agent.messages.firstOrNull { it.text.contains(query, true) }?.text
                                    ?: "Agent",
                                color = Cue.Muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
    }
}

@Composable
fun OtherScreen(state: CueState, screen: Screen, agent: Agent) {
    var rename by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Header(
            when (screen) {
                Screen.AppIcon -> "App icon"
                else -> screen.name
            },
            state::back,
        )
        when (screen) {
            Screen.Language ->
                Column(Modifier.padding(16.dp)) {
                    Group {
                        listOf(
                                "English",
                                "简体中文",
                                "繁體中文",
                                "日本語",
                                "한국어",
                                "Español",
                                "Français",
                                "Deutsch",
                                "Português",
                                "Italiano",
                            )
                            .forEach { language ->
                                SettingRow(
                                    language,
                                    onClick = {
                                        state.changeLanguage(language)
                                        state.back()
                                    },
                                    trailing = {
                                        if (state.language == language)
                                            Icon(Glyph.Check, tint = Cue.Blue)
                                    },
                                )
                            }
                    }
                }
            Screen.Account ->
                Column(Modifier.padding(16.dp)) {
                    Group {
                        SettingRow("Name", value = state.userName, onClick = { rename = true })
                        SettingRow("Email", value = "Local account", divider = false)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Your agents and conversations are stored on this device.",
                        color = Cue.Muted,
                    )
                }
            Screen.Wallet ->
                Box(Modifier.weight(1f)) {
                    EmptyState(
                        Glyph.Wallet,
                        "No payment methods",
                        Modifier.align(Alignment.Center).offset(y = (-70).dp),
                    )
                    PillButton(
                        "Add payment method",
                        Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                        onClick = {
                            state.error = "Payment services are not configured for this build."
                        },
                    )
                }
            Screen.AppIcon -> {
                val names =
                    listOf(
                        "gray_gear",
                        "gray_square",
                        "blue_bubble",
                        "blue_triangle",
                        "red_heart",
                        "red_flame",
                        "yellow_star",
                        "yellow_circle",
                        "brown_bell",
                        "brown_briefcase",
                    )
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    names.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            row.forEach { name ->
                                val bitmap =
                                    remember(name) {
                                        context.assets
                                            .open("cue/cue_app_icon_$name.png")
                                            .use { BitmapFactory.decodeStream(it) }
                                            .asImageBitmap()
                                    }
                                Image(
                                    bitmap,
                                    name,
                                    Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).clickable {
                                        selectLauncherIcon(context, name)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }
    if (rename)
        RenameDialog(state.userName, { rename = false }) {
            state.setName(it)
            rename = false
        }
}

private fun selectLauncherIcon(context: android.content.Context, name: String) {
    val names =
        listOf(
            "gray_gear",
            "gray_square",
            "blue_bubble",
            "blue_triangle",
            "red_heart",
            "red_flame",
            "yellow_star",
            "yellow_circle",
            "brown_bell",
            "brown_briefcase",
        )
    fun component(value: String) =
        android.content.ComponentName(
            context.packageName,
            context.packageName +
                ".launcher." +
                value.split("_").joinToString("") { it.replaceFirstChar(Char::uppercaseChar) },
        )
    val manager = context.packageManager
    manager.setComponentEnabledSetting(
        component(name),
        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        android.content.pm.PackageManager.DONT_KILL_APP,
    )
    names
        .filter { it != name }
        .forEach { other ->
            manager.setComponentEnabledSetting(
                component(other),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
        }
}
