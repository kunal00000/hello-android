package dev.cue.rebuild

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GestureNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(screen: Screen): CueState {
        val state = CueState(compose.activity.application, "gesture-test-${System.nanoTime()}")
        if (screen != Screen.Home) state.open(screen, state.agent().id)
        compose.setContent { CueTheme { CueApp(state) } }
        compose.waitForIdle()
        return state
    }

    // Deliberately start well away from both edges.
    private fun swipeBack(y: Float = .5f) {
        compose.onNodeWithTag("navigation").performTouchInput {
            swipe(Offset(width * .25f, height * y), Offset(width * .85f, height * y), 400)
        }
    }

    private fun swipeForward(y: Float = .5f) {
        compose.onNodeWithTag("navigation").performTouchInput {
            swipe(Offset(width * .8f, height * y), Offset(width * .2f, height * y), 400)
        }
    }

    @Test
    fun blankChatSwipesNavigateAndPreserveDraft() {
        val state = launch(Screen.Chat)
        compose.runOnIdle { state.update(state.agent().copy(draft = "Keep this draft")) }
        swipeForward()
        compose.runOnIdle { assertEquals(Screen.Agent, state.route.screen) }
        swipeBack(.23f)
        compose.runOnIdle {
            assertEquals(Screen.Chat, state.route.screen)
            assertEquals("Keep this draft", state.agent().draft)
        }
        swipeBack()
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
    }

    @Test
    fun shortReversedAndVerticalGesturesDoNotNavigate() {
        val state = launch(Screen.Chat)
        val navigation = compose.onNodeWithTag("navigation")
        navigation.performTouchInput {
            swipe(Offset(width * .4f, height * .5f), Offset(width * .4f + 30f, height * .5f), 400)
        }
        navigation.performTouchInput {
            swipe(Offset(width * .4f, height * .6f), Offset(width * .4f, height * .3f), 400)
        }
        navigation.performTouchInput {
            down(Offset(width * .25f, height * .5f))
            moveTo(Offset(width * .8f, height * .5f), 200)
            moveTo(Offset(width * .27f, height * .5f), 200)
            up()
        }
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
    }

    @Test
    fun bubblesHeaderAndComposerReserveTheirGestures() {
        val state = launch(Screen.Chat)
        val text = "This bubble keeps its own gestures for copying and future swipe to reply."
        compose.runOnIdle {
            state.update(state.agent().copy(messages = listOf(Message(text = text, user = false))))
        }
        compose.onNodeWithText(text).performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
        compose.onNodeWithText(text).performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
        compose.onNodeWithText(text).performTouchInput { longClick() }
        compose.onNodeWithText("Copy", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Cancel", substring = false).performClick()
        compose.onNodeWithTag("chat-header").performTouchInput {
            swipe(Offset(width * .8f, height * .7f), Offset(width * .2f, height * .7f), 400)
        }
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
        // Starting in the margin beside a bubble is navigation, even if the drag crosses it.
        val navigation = compose.onNodeWithTag("navigation")
        val bubbleY =
            compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.center.y -
                navigation.fetchSemanticsNode().boundsInRoot.top
        navigation.performTouchInput {
            swipe(Offset(width * .97f, bubbleY), Offset(width * .3f, bubbleY), 400)
        }
        compose.runOnIdle {
            assertEquals(Screen.Agent, state.route.screen)
            state.back()
        }
        compose.onNodeWithTag("composer").performTouchInput {
            swipe(Offset(width * .75f, height * .25f), Offset(width * .25f, height * .25f), 400)
        }
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
    }

    @Test
    fun homeAndSettingsRestoreHistoryAndNewDestinationsReplaceIt() {
        val state = launch(Screen.Home)
        swipeForward(.7f)
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
        swipeBack(.7f)
        compose.runOnIdle { assertEquals(Screen.Settings, state.route.screen) }
        swipeBack(.04f)
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
        swipeForward(.7f)
        compose.runOnIdle { assertEquals(Screen.Settings, state.route.screen) }
        swipeBack(.04f)
        compose.runOnIdle {
            state.open(Screen.Chat, state.agent().id)
            state.back()
        }
        swipeForward(.7f)
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
        swipeBack()
        swipeBack(.7f)
        compose.runOnIdle {
            assertEquals(Screen.Settings, state.route.screen)
            assertTrue(state.forwardRoutes.isEmpty())
            state.open(Screen.Language)
            state.back()
        }
        swipeForward(.04f)
        compose.runOnIdle {
            assertEquals(Screen.Language, state.route.screen)
            state.back()
            state.open(Screen.Skills)
            assertTrue(state.forwardRoutes.isEmpty())
            state.back()
        }
        swipeForward(.04f)
        compose.runOnIdle { assertEquals(Screen.Skills, state.route.screen) }
    }

    @Test
    fun chatAndAgentSettingsCanRestoreADeeperHistory() {
        val state = launch(Screen.Chat)
        compose.runOnIdle {
            state.open(Screen.Agent)
            state.open(Screen.Instructions)
            state.back()
            state.back()
        }
        swipeForward()
        compose.runOnIdle {
            assertEquals(Screen.Agent, state.route.screen)
            assertEquals(Screen.Instructions, state.forwardRoutes.first().screen)
        }
        swipeForward(.23f)
        compose.runOnIdle { assertEquals(Screen.Instructions, state.route.screen) }
    }

    @Test
    fun keyboardEditingDoesNotTriggerSwipeNavigation() {
        val state = launch(Screen.Chat)
        compose.onNode(hasSetTextAction()).performTextInput("Unsent text")
        compose.waitUntil(5_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        swipeBack(.6f)
        swipeForward(.6f)
        compose.runOnIdle {
            assertEquals(Screen.Chat, state.route.screen)
            assertEquals("Unsent text", state.agent().draft)
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    }

    @Test
    fun appPagingHasPriorityOverScreenNavigation() {
        val state = launch(Screen.Apps)
        val pager = compose.onNodeWithTag("app-categories")
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Finance").assertIsSelected()
        compose.onNodeWithText("Plaid").assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Health").assertIsSelected()
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithText("Finance").assertIsSelected()
        compose.onNodeWithText("Travel").performClick()
        compose.onNodeWithText("No apps found").assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Productivity").assertIsSelected()
        compose.onNodeWithText("Gmail").assertIsDisplayed()
        swipeBack(.035f)
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
    }
}
