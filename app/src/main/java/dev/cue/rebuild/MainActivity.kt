package dev.cue.rebuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private var integrationReturn by mutableIntStateOf(0)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.scheme == "cue" && intent.data?.host == "integrations") integrationReturn++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            val state: CueState = viewModel()
            LaunchedEffect(integrationReturn) {
                if (intent.data?.scheme == "cue" && intent.data?.host == "integrations") {
                    if (
                        state.route.screen != Screen.IntegrationDetail &&
                            state.route.screen != Screen.Integrations
                    )
                        state.open(Screen.Integrations)
                    state.refreshIntegrations()
                }
            }
            val dark =
                state.appearance == "Dark" ||
                    state.appearance == "Follow system" && isSystemInDarkTheme()
            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                enableEdgeToEdge(
                    statusBarStyle =
                        if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            ),
                    navigationBarStyle =
                        if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            ),
                )
            }
            CueTheme(dark) { CueApp(state) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CueApp(state: CueState) {
    var sheet by remember { mutableStateOf<String?>(null) }
    val route = state.route
    BackHandler(state.routes.size > 1) { state.back() }
    SwipeNavigation(
        route = route,
        left = state.leftRoute,
        right = state.rightRoute,
        direction = state.navigationDirection,
        onNavigate = state::navigateToNeighbor,
        enabled =
            sheet == null &&
                state.error == null &&
                WindowInsets.ime.getBottom(LocalDensity.current) == 0,
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("navigation"),
    ) { current ->
        val agent = state.agent(current.agentId)
        val background =
            when (current.screen) {
                Screen.Home,
                Screen.Chat -> MaterialTheme.colorScheme.background
                Screen.Agent,
                Screen.Settings,
                Screen.Skills,
                Screen.Messenger,
                Screen.Account,
                Screen.Integrations,
                Screen.IntegrationDetail -> Cue.Settings
                else -> Cue.Page
            }
        Box(
            Modifier.fillMaxSize()
                .background(
                    if (state.appearance == "Dark") MaterialTheme.colorScheme.background
                    else background
                )
                // Move the background behind the system bars with its page, keeping controls inset.
                .systemBarsPadding()
        ) {
            when (current.screen) {
                Screen.Home ->
                    HomeScreen(
                        state,
                        { sheet = if (it) "group" else "create" },
                        {
                            state.error =
                                "Scan & create needs camera and QR provisioning support, which is not configured yet."
                        },
                    )
                Screen.Chat -> ChatScreen(state, agent)
                Screen.Agent -> AgentScreen(state, agent, { sheet = "avatar" }, { sheet = "phone" })
                Screen.Backend -> BackendConnectionScreen(state)
                Screen.Settings -> SettingsScreen(state, { sheet = "phone" })
                Screen.Instructions -> InstructionsScreen(state, agent)
                Screen.Memory -> MemoryScreen(state, agent)
                Screen.Skills -> SkillsScreen(state)
                Screen.Messenger -> MessengerScreen(state)
                Screen.Integrations -> IntegrationsScreen(state)
                Screen.IntegrationDetail -> IntegrationDetailScreen(state, current.integrationId)
                Screen.Mail -> MailScreen(state, agent)
                Screen.Computer -> ComputerScreen(state, agent)
                Screen.Search -> SearchScreen(state)
                else -> OtherScreen(state, current.screen, agent)
            }
        }
    }
    if (sheet != null) {
        CueSheet(
            onDismissRequest = { sheet = null },
            height = if (sheet == "phone") 520.dp else null,
        ) { dismiss ->
            when (sheet) {
                "avatar" ->
                    AvatarEditor(state.agent().avatar, dismiss) {
                        state.update(state.agent().copy(avatar = it))
                        dismiss()
                    }
                "group" -> CreateGroupScreen(state, dismiss)
                "phone" -> PhoneSheet(state.agent(), dismiss)
                else ->
                    CreateAgentScreen(dismiss) { name, avatar ->
                        state.create(name, avatar)
                        dismiss()
                    }
            }
        }
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = { state.error = null },
            title = { Text("Cue") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { state.error = null }) { Text("OK") } },
        )
    }
}
