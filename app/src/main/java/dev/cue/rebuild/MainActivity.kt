package dev.cue.rebuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            val state: CueState = viewModel()
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
    val pageColor =
        when (route.screen) {
            Screen.Home,
            Screen.Chat -> MaterialTheme.colorScheme.background
            Screen.Agent,
            Screen.Settings,
            Screen.Skills,
            Screen.Messenger,
            Screen.Account ->
                if (
                    isSystemInDarkTheme() && state.appearance != "Light" ||
                        state.appearance == "Dark"
                )
                    MaterialTheme.colorScheme.background
                else Cue.Settings
            else ->
                if (state.appearance == "Dark") MaterialTheme.colorScheme.background else Cue.Page
        }
    SwipeNavigation(
        gestureKey = state.routes,
        onBack =
            when {
                route.screen == Screen.Home -> ({ state.open(Screen.Settings) })
                state.routes.size > 1 -> state::back
                else -> null
            },
        onForward =
            when {
                route.screen == Screen.Chat -> ({
                        if (state.forwardRoutes.firstOrNull() == Route(Screen.Agent, route.agentId))
                            state.forward()
                        else state.open(Screen.Agent, route.agentId)
                    })
                route.screen in listOf(Screen.Home, Screen.Settings, Screen.Agent) &&
                    state.forwardRoutes.isNotEmpty() -> state::forward
                else -> null
            },
        enabled =
            sheet == null &&
                state.error == null &&
                WindowInsets.ime.getBottom(LocalDensity.current) == 0,
        modifier =
            Modifier.fillMaxSize().background(pageColor).systemBarsPadding().testTag("navigation"),
    ) {
        AnimatedContent(
            targetState = state.routes,
            transitionSpec = {
                val forward = targetState.size > initialState.size
                (slideInHorizontally(tween(Cue.NavigationMs, easing = Cue.Ease)) {
                    if (forward) it else -it / 3
                } + fadeIn(tween(Cue.FeedbackMs))) togetherWith
                    (slideOutHorizontally(tween(Cue.NavigationMs, easing = Cue.Ease)) {
                        if (forward) -it / 3 else it
                    } + fadeOut(tween(Cue.FeedbackMs)))
            },
            label = "navigation",
        ) { routes ->
            val current = routes.last()
            val agent = state.agent(current.agentId)
            val background =
                when (current.screen) {
                    Screen.Home,
                    Screen.Chat -> MaterialTheme.colorScheme.background
                    Screen.Agent,
                    Screen.Settings,
                    Screen.Skills,
                    Screen.Messenger,
                    Screen.Account -> Cue.Settings
                    else -> Cue.Page
                }
            Box(
                Modifier.fillMaxSize()
                    .background(
                        if (state.appearance == "Dark") MaterialTheme.colorScheme.background
                        else background
                    )
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
                    Screen.Agent ->
                        AgentScreen(state, agent, { sheet = "avatar" }, { sheet = "phone" })
                    Screen.Settings -> SettingsScreen(state, { sheet = "phone" })
                    Screen.Instructions -> InstructionsScreen(state, agent)
                    Screen.Memory -> MemoryScreen(state, agent)
                    Screen.Apps -> AppsScreen(state)
                    Screen.Skills -> SkillsScreen(state)
                    Screen.Messenger -> MessengerScreen(state)
                    Screen.Mail -> MailScreen(state, agent)
                    Screen.Computer -> ComputerScreen(state, agent)
                    Screen.Search -> SearchScreen(state)
                    else -> OtherScreen(state, current.screen, agent)
                }
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
