package dev.cue.rebuild

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CueFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(): CueState {
        val state = CueState(compose.activity.application, "cue-test-${System.nanoTime()}")
        compose.setContent { CueTheme(dark = state.appearance == "Dark") { CueApp(state) } }
        return state
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Dialogs/sheets occupy a separate window; capture the complete device surface.
        android.os.SystemClock.sleep(300)
        val bitmap =
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: error("Could not capture the device screen")
        val dir = File(compose.activity.filesDir, "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun navigationAndInstructionPersistence() {
        val state = launch()
        capture("home")
        compose.onNodeWithText("Luna").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Chat, state.route.screen)
        capture("chat-empty")
        compose.onNodeWithContentDescription("Add attachment").performClick()
        capture("attachments-menu")
        compose.runOnIdle { state.changeAppearance("Dark") }
        capture("attachments-menu-dark")
        compose.runOnIdle { state.changeAppearance("Light") }
        InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Camera").assertDoesNotExist()
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
        // A populated, scrolled conversation makes both floating fades visible in visual QA.
        compose.runOnIdle {
            state.update(
                state
                    .agent()
                    .copy(
                        messages =
                            List(12) { index ->
                                Message(
                                    text =
                                        if (index % 2 == 0)
                                            "Can we keep this plan simple and leave some time to explore?"
                                        else
                                            "Absolutely. Start with a slow morning, choose one place you really want to visit, and leave the afternoon open. A little room to wander makes a better day.",
                                    user = index % 2 == 0,
                                )
                            }
                    )
            )
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        capture("chat-overlay")
        compose.runOnIdle { state.changeAppearance("Dark") }
        capture("chat-overlay-dark")
        compose.runOnIdle {
            state.changeAppearance("Light")
            state.update(state.agent().copy(messages = emptyList()))
        }
        compose.onNodeWithText("Luna").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Agent, state.route.screen)
        capture("agent")
        compose.runOnIdle { state.changeAppearance("Dark") }
        capture("agent-dark")
        compose.runOnIdle { state.changeAppearance("Light") }
        compose.onNodeWithText("Instructions").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Always give concise answers.")
        compose.onNodeWithContentDescription("Save instructions").performClick()
        compose.waitForIdle()
        assertEquals("Always give concise answers.", state.agent().instructions)
        compose.onNodeWithText("Instructions").performClick()
        compose.onNodeWithText("Always give concise answers.").assertExists()
        capture("instructions")
    }

    @Test
    fun avatarSheetCancelAndSave() {
        val state = launch()
        compose.onNodeWithText("Luna").performClick()
        compose.onNodeWithText("Luna").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        capture("agent-menu")
        compose.runOnIdle { state.changeAppearance("Dark") }
        capture("agent-menu-dark")
        compose.runOnIdle { state.changeAppearance("Light") }
        compose.onNodeWithText("Change Avatar").performClick()
        capture("avatar")
        compose.onNodeWithContentDescription("Shape bread").performClick()
        compose.onNodeWithContentDescription("Cancel avatar").performClick()
        compose.waitForIdle()
        assertEquals(AvatarSpec(), state.agent().avatar)
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Change Avatar").performClick()
        compose.onNodeWithContentDescription("Shape bread").performClick()
        compose.onNodeWithContentDescription("Save avatar").performClick()
        compose.runOnIdle { assertEquals("bread", state.agent().avatar.shape) }
    }

    @Test
    fun groupSelectionRequiresAMember() {
        val state = launch()
        compose.onNodeWithContentDescription("Create").performClick()
        compose.onNodeWithText("Group Chat", substring = false).performClick()
        compose.onNodeWithText("Create", substring = false).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Luna")
        compose.onNodeWithContentDescription("Select Luna").performClick()
        compose.onNodeWithText("Create", substring = false).performClick()
        compose.runOnIdle {
            assertTrue(state.agent().group)
            assertEquals(listOf("luna"), state.agent().members)
        }
    }

    @Test
    fun memoryAndSearch() {
        val state = launch()
        compose.runOnIdle { state.open(Screen.Memory, "luna") }
        compose.onNode(hasSetTextAction()).performTextInput("I prefer tea.")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.onNodeWithText("I prefer tea.", substring = false).assertExists()
        capture("memory")
        compose.runOnIdle {
            state.home()
            state.open(Screen.Search)
        }
        compose.onNode(hasSetTextAction()).performTextInput("Luna")
        compose.onNode(hasText("Luna") and hasSetTextAction().not()).assertExists()
    }

    @Test
    fun settingsAndNewAgentValidation() {
        val state = launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        capture("settings")
        compose.runOnIdle { state.changeAppearance("Dark") }
        capture("settings-dark")
        compose.runOnIdle {
            state.changeAppearance("Light")
            state.home()
        }
        compose.onNodeWithContentDescription("Create").performClick()
        capture("home-menu")
        compose.runOnIdle { state.changeAppearance("Dark") }
        capture("home-menu-dark")
        compose.runOnIdle { state.changeAppearance("Light") }
        compose.onNodeWithText("Agent", substring = false).performClick()
        compose.onNodeWithText("Create", substring = false).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Orbit")
        capture("create")
        compose.onNodeWithText("Create", substring = false).performClick()
        compose.waitForIdle()
        assertEquals("Orbit", state.agent().name)
        assertEquals(Screen.Chat, state.route.screen)
    }

    @Test
    fun messengerSetupSheetsStayUnconnectedAndDismiss() {
        val state = launch()
        compose.runOnIdle { state.open(Screen.Messenger, "luna") }
        capture("messenger-list")
        listOf("Telegram", "LINE", "Slack").forEachIndexed { index, service ->
            compose.onNodeWithText(service, substring = false).performClick()
            compose.onNodeWithText("Connect $service").assertIsDisplayed()
            compose.onNodeWithText("$service connections are not available yet.").assertExists()
            if (index == 0) {
                capture("messenger-sheet")
                compose.runOnIdle { state.changeAppearance("Dark") }
                capture("messenger-sheet-dark")
                compose.runOnIdle { state.changeAppearance("Light") }
                compose.onNodeWithContentDescription("Close messenger setup").performClick()
            } else if (index == 1) {
                InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            } else {
                compose.onNodeWithText("Done").performClick()
            }
            compose.onNodeWithText("Connect $service").assertDoesNotExist()
            compose.runOnIdle { assertEquals(Screen.Messenger, state.route.screen) }
        }
    }

    @Test
    fun phoneSheetSupportsCloseAndDragDismissal() {
        val state = launch()
        compose.runOnIdle { state.open(Screen.Settings) }
        compose.onNodeWithText("Phone", substring = false).performScrollTo().performClick()
        capture("phone-sheet")
        compose.runOnIdle { state.changeAppearance("Dark") }
        capture("phone-sheet-dark")
        compose.runOnIdle { state.changeAppearance("Light") }
        compose.onNodeWithContentDescription("Close phone setup").performClick()
        compose.onNodeWithTag("cue-sheet").assertDoesNotExist()
        compose.onNodeWithText("Phone", substring = false).performClick()
        compose.onNodeWithTag("cue-sheet").performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(width / 2f, 20f),
                end = androidx.compose.ui.geometry.Offset(width / 2f, height * .85f),
                durationMillis = 300,
            )
        }
        compose.onNodeWithTag("cue-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(Screen.Settings, state.route.screen) }
    }
}
