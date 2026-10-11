package dev.cue.rebuild

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun IntegrationsScreen(state: CueState) {
    val context = LocalContext.current
    val catalog = remember { integrationCatalog(context) }
    val active = LocalNavigationActive.current
    LaunchedEffect(active) { if (active) state.refreshIntegrations() }
    val accounts = state.integrations?.optJSONArray("accounts")
    val cloudConnected =
        (0 until (accounts?.length() ?: 0))
            .mapNotNull { index ->
                accounts!!
                    .getJSONObject(index)
                    .takeIf { it.optString("status") == "connected" }
                    ?.optString("provider")
            }
            .toSet()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var connectedOnly by rememberSaveable { mutableStateOf(false) }
    val results =
        catalog.filter { item ->
            (category == "all" || item.category == category) &&
                (!connectedOnly ||
                    state.deviceAccess.connected(item.id) ||
                    item.id in cloudConnected) &&
                (item.name.contains(query, true) || item.description.contains(query, true))
        }
    Column(Modifier.fillMaxSize()) {
        Header("Integrations", state::back)
        SearchField(query, { query = it }, "Search integrations", Modifier.padding(16.dp, 8.dp))
        Row(
            Modifier.padding(16.dp, 8.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp)
        ) {
            listOf("Available", "Connected").forEachIndexed { i, label ->
                Text(
                    label,
                    Modifier.weight(1f)
                        .clip(CircleShape)
                        .background(
                            if (connectedOnly == (i == 1)) Cue.Bubble else Color.Transparent
                        )
                        .selectable(
                            connectedOnly == (i == 1),
                            role = Role.Tab,
                            onClick = { connectedOnly = i == 1 },
                        )
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    style = Cue.Label,
                )
            }
        }
        LazyRow(
            modifier = Modifier.testTag("integration-categories"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(listOf("all" to "All") + integrationCategories.toList()) { (id, title) ->
                Text(
                    title,
                    Modifier.clip(CircleShape)
                        .background(if (category == id) Cue.Bubble else Color.Transparent)
                        .selectable(category == id, role = Role.Tab, onClick = { category = id })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (category == id) Cue.Ink else Cue.Muted,
                    fontSize = 14.sp,
                )
            }
        }
        // Clip the viewport, so rows scroll beneath a stationary curved edge like the old Apps
        // list.
        LazyColumn(
            Modifier.weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .testTag("integration-list"),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            if (results.isEmpty())
                item {
                    EmptyState(
                        Glyph.Search,
                        if (connectedOnly) "No connected integrations" else "No integrations found",
                        Modifier.padding(top = 60.dp),
                    )
                }
            integrationCategories.forEach { (id, title) ->
                val section = results.filter { it.category == id }
                if (section.isNotEmpty()) {
                    item(key = "section-$id") {
                        Text(
                            title,
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = Cue.Label,
                            color = Cue.Muted,
                        )
                    }
                    items(section, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    state.open(Screen.IntegrationDetail, integrationId = item.id)
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IntegrationIcon(item)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = Cue.Body)
                                Text(
                                    if (item.category == "device") "From this device"
                                    else item.description,
                                    style = Cue.Caption,
                                    color = Cue.Muted,
                                    maxLines = 2,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Glyph.Next, tint = Cue.Muted, size = 18.dp)
                        }
                        if (item.id != section.last().id)
                            HorizontalDivider(
                                Modifier.padding(start = 70.dp, end = 16.dp),
                                color = Cue.Muted.copy(alpha = .09f),
                                thickness = .5.dp,
                            )
                    }
                }
            }
            if (results.isNotEmpty())
                item {
                    Text(
                        "Catalog from eve.dev · Device controls on this phone",
                        Modifier.padding(16.dp),
                        style = Cue.Caption,
                        color = Cue.Muted,
                    )
                }
        }
    }
}

@Composable
fun IntegrationIcon(item: Integration, large: Boolean = false) {
    Box(
        Modifier.size(if (large) 64.dp else 40.dp)
            .clip(RoundedCornerShape(if (large) 20.dp else 12.dp))
            .background(if (item.icon != null) Color.White else MaterialTheme.colorScheme.surface)
            .border(
                .5.dp,
                Cue.Muted.copy(alpha = .12f),
                RoundedCornerShape(if (large) 20.dp else 12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (item.icon != null)
            Asset(item.icon, Modifier.size(if (large) 42.dp else 26.dp), folder = "integrations")
        else Icon(item.glyph, size = if (large) 32.dp else 24.dp)
    }
}

@Composable
fun IntegrationDetailScreen(state: CueState, id: String?) {
    val context = LocalContext.current
    val item = remember(id) { integrationCatalog(context).firstOrNull { it.id == id } }
    if (item == null) {
        Column {
            Header("Integration", state::back)
            EmptyState(Glyph.Box, "Integration not found")
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Header("", state::back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            IntegrationIcon(item, large = true)
            Text(
                item.name,
                Modifier.padding(top = 20.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                item.description,
                Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                style = Cue.Body,
                color = Cue.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (item.category == "device") DeviceIntegrationControls(state, item)
            else CloudIntegrationControls(state, item)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DeviceIntegrationControls(state: CueState, item: Integration) {
    val context = LocalContext.current
    val access = state.deviceAccess
    val actions = remember(item.id) { deviceActions.filter { it.integrationId == item.id } }
    var refresh by remember { mutableIntStateOf(0) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var connecting by rememberSaveable { mutableStateOf(false) }
    var connectionIssue by rememberSaveable { mutableStateOf<String?>(null) }
    fun finishConnection() {
        refresh++
        if (!connecting) return
        connecting = false
        if (actions.all { access.permitted(it) }) {
            access.connect(item.id, true)
            connectionIssue = null
        } else {
            connectionIssue =
                "Permission wasn't granted. Try connecting again, or enable access in Settings."
        }
    }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            finishConnection()
        }
    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            finishConnection()
        }
    fun openSettings() {
        connectionIssue = null
        val intent =
            when {
                item.id == "device-notifications" ->
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                item.id == "device-health" && android.os.Build.VERSION.SDK_INT >= 34 ->
                    Intent(
                            android.health.connect.HealthConnectManager
                                .ACTION_MANAGE_HEALTH_PERMISSIONS
                        )
                        .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                else ->
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
            }
        runCatching { settings.launch(intent) }
            .onFailure {
                connecting = false
                connectionIssue = "Settings couldn't be opened on this phone."
            }
    }
    fun connect() {
        setup = false
        connecting = true
        connectionIssue = null
        val missing = actions.filter { !access.permitted(it) }
        when {
            missing.isEmpty() -> finishConnection()
            item.id == "device-health" && android.os.Build.VERSION.SDK_INT < 34 -> {
                connecting = false
                connectionIssue = "Health Connect requires Android 14 or newer."
            }
            item.id == "device-notifications" -> openSettings()
            else ->
                permissions.launch(missing.mapNotNull { it.permission }.distinct().toTypedArray())
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val enabled = access.connected(item.id)
    val missing = remember(refresh, access.revision) { actions.filter { !access.permitted(it) } }
    if (connectionIssue != null) {
        Text(
            connectionIssue!!,
            Modifier.padding(12.dp),
            style = Cue.Caption,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            onClick = {
                connecting = !enabled
                openSettings()
            }
        ) {
            Text("Open settings")
        }
    }
    if (!enabled) {
        Group {
            Text("Connect this device", Modifier.padding(16.dp), style = Cue.Label)
            Text(
                "Connect to grant the required device permission, then choose which actions Cue can use in chat.",
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                style = Cue.Caption,
                color = Cue.Muted,
            )
        }
        Spacer(Modifier.height(20.dp))
        PillButton(
            "Connect",
            Modifier.fillMaxWidth(),
            enabled = !connecting,
            onClick = { setup = true },
        )
    } else {
        Group {
            SettingRow(android.os.Build.MODEL, Glyph.Phone, "This device")
            Text(
                "Manage",
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button) { openSettings() }
                    .padding(16.dp),
                style = Cue.Body,
                color = Cue.Blue,
            )
        }
        Text(
            if (missing.isEmpty()) "You can manage Cue's access in your device settings."
            else "Device permission is turned off. Use Manage to restore access.",
            Modifier.fillMaxWidth().padding(12.dp),
            style = Cue.Caption,
            color = Cue.Muted,
        )
        Spacer(Modifier.height(24.dp))
        for (writes in listOf(false, true)) {
            val section = actions.filter { it.writes == writes }
            if (section.isEmpty()) continue
            Text(
                if (writes) "Actions in other apps" else "Read permissions",
                Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 10.dp),
                style = Cue.Caption,
                color = Cue.Muted,
            )
            Group {
                section.forEachIndexed { index, action ->
                    PermissionRow(action.title, access.policy(action), index < section.lastIndex) {
                        access.setPolicy(action, it)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (actions.any { it.writes })
            Text(
                "Prepared messages, calls, contacts and events open in their device app for you to finish.",
                Modifier.padding(12.dp),
                style = Cue.Caption,
                color = Cue.Muted,
            )
        Spacer(Modifier.height(16.dp))
        Group {
            SettingRow(
                "Disconnect",
                divider = false,
                onClick = { access.connect(item.id, false) },
                trailing = { Icon(Glyph.Close, tint = MaterialTheme.colorScheme.error) },
            )
        }
    }
    if (setup)
        CueSheet(onDismissRequest = { setup = false }, height = 500.dp) { dismiss ->
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth()) {
                    RoundButton(Glyph.Close, "Close integration setup", onClick = dismiss)
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))
                    IntegrationIcon(item, true)
                    Text(
                        "Connect ${item.name}",
                        Modifier.padding(vertical = 20.dp),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Start with Ask for every action. You can switch individual actions to Allow or Deny after connecting.",
                        style = Cue.Body,
                        color = Cue.Muted,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Requested data is sent to your personal backend and AI provider. Nothing is synced in the background.",
                        Modifier.padding(top = 16.dp, bottom = 16.dp),
                        style = Cue.Caption,
                        color = Cue.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                PillButton("Connect this device", Modifier.fillMaxWidth(), onClick = { connect() })
            }
        }
}

@Composable
fun PermissionRow(
    title: String,
    policy: AccessPolicy,
    divider: Boolean = true,
    onChange: (AccessPolicy) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            title,
            divider = divider,
            onClick = { show = true },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(policy.name, style = Cue.Label, color = Cue.Muted)
                    Spacer(Modifier.width(6.dp))
                    Icon(Glyph.Selector, size = 16.dp, tint = Cue.Muted.copy(alpha = .6f))
                }
            },
        )
        if (show)
            ActionMenu(
                AccessPolicy.entries.map { null to it.name },
                { show = false },
                alignment = Alignment.TopEnd,
            ) {
                onChange(AccessPolicy.valueOf(it))
            }
    }
}

@Composable
private fun CloudIntegrationControls(state: CueState, item: Integration) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = LocalNavigationActive.current
    LaunchedEffect(active) { if (active) state.refreshIntegrations() }
    DisposableEffect(lifecycle, active) {
        val observer = LifecycleEventObserver { _, event ->
            if (active && event == Lifecycle.Event.ON_RESUME) state.refreshIntegrations()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val snapshot = state.integrations
    val allAccounts = snapshot?.optJSONArray("accounts")
    val accounts =
        (0 until (allAccounts?.length() ?: 0))
            .map { allAccounts!!.getJSONObject(it) }
            .filter { it.optString("provider") == item.id }
    val providers = snapshot?.optJSONArray("providers")
    val connectable =
        (0 until (providers?.length() ?: 0)).any {
            providers!!.getJSONObject(it).let { p ->
                p.optString("id") == item.id && p.optBoolean("connectable")
            }
        }
    var confirmDisconnect by remember { mutableStateOf<org.json.JSONObject?>(null) }
    fun action(
        name: String,
        accountId: String? = null,
        tool: String? = null,
        policy: AccessPolicy? = null,
    ) {
        state.integrationAction(
            org.json
                .JSONObject()
                .put("action", name)
                .put("provider", item.id)
                .put("accountId", accountId)
                .put("tool", tool)
                .put("policy", policy?.name)
        ) { result ->
            result
                .optString("authorizationUrl")
                .takeIf { it.startsWith("https://") }
                ?.let { uri.openUri(it) }
        }
    }
    if (state.integrationsError != null) {
        Text(
            state.integrationsError!!,
            Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.error,
            style = Cue.Caption,
        )
        TextButton(onClick = state::refreshIntegrations) { Text("Retry") }
    }
    if (state.integrationsBusy)
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
    accounts.forEach { account ->
        val accountId = account.getString("id")
        val status = account.optString("status")
        Text(
            "Account",
            Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 10.dp),
            style = Cue.Caption,
            color = Cue.Muted,
        )
        Group {
            SettingRow(account.optString("label", item.name), Glyph.User, divider = false)
            Text(
                when (status) {
                    "connected" -> "Connected"
                    "disconnected" -> "Cue access disabled"
                    else -> "Finish sign-in, then refresh"
                },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = Cue.Caption,
                color = Cue.Muted,
            )
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(
                    enabled = !state.integrationsBusy && status != "disconnected",
                    onClick = { action("refresh", accountId) },
                ) {
                    Text("Refresh account")
                }
                TextButton(
                    enabled = !state.integrationsBusy,
                    onClick = { confirmDisconnect = account },
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        val tools = account.optJSONArray("tools") ?: org.json.JSONArray()
        val policies = account.optJSONObject("policies") ?: org.json.JSONObject()
        for (readOnly in listOf(true, false)) {
            val group =
                (0 until tools.length())
                    .map { tools.getJSONObject(it) }
                    .filter { it.optBoolean("readOnly") == readOnly }
            if (group.isEmpty() || status != "connected") continue
            Text(
                if (readOnly) "Read permissions" else "Write & other permissions",
                Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 10.dp),
                style = Cue.Caption,
                color = Cue.Muted,
            )
            Group {
                group.forEachIndexed { index, tool ->
                    val name = tool.getString("name")
                    PermissionRow(
                        tool.optString("title", name),
                        AccessPolicy.valueOf(policies.optString(name, "Ask")),
                        index < group.lastIndex,
                    ) { policy ->
                        if (!state.integrationsBusy) action("permission", accountId, name, policy)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (connectable) {
        PillButton(
            if (accounts.isEmpty()) "Connect ${item.name}" else "Add another account",
            Modifier.fillMaxWidth(),
            enabled = !state.integrationsBusy,
            onClick = { action("connect") },
        )
        Text(
            "You sign in with the provider. Every tool starts with Ask; change individual permissions after connecting.",
            Modifier.padding(12.dp),
            style = Cue.Caption,
            color = Cue.Muted,
        )
    } else if (item.category == "connectors" && snapshot == null && !state.hasBackendCredential) {
        PillButton(
            "Connect backend",
            Modifier.fillMaxWidth(),
            onClick = { state.open(Screen.Backend) },
        )
    } else if (!state.integrationsBusy) {
        Group {
            Text(
                when (item.category) {
                    "channels" ->
                        "This channel needs its own bot or service account, credentials, and inbound routing before it can receive messages."
                    "extensions" ->
                        "This capability is installed and configured on your agent's backend."
                    "memory" -> "Memory providers need backend storage and provider configuration."
                    "observability" ->
                        "Trace destinations need backend credentials and an exporter."
                    else ->
                        "This provider needs additional setup before account linking is available."
                },
                Modifier.padding(16.dp),
                style = Cue.Body,
                color = Cue.Muted,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    PillButton(
        "Setup guide",
        Modifier.fillMaxWidth(),
        primary = false,
        onClick = { uri.openUri(item.url) },
    )
    confirmDisconnect?.let { account ->
        AlertDialog(
            onDismissRequest = { confirmDisconnect = null },
            title = { Text("Disconnect ${item.name}?") },
            text = { Text("Cue will stop using this account and revoke its connection.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        action("disconnect", account.getString("id"))
                        confirmDisconnect = null
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = null }) { Text("Cancel") }
            },
        )
    }
}
