package dev.cue.rebuild

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GestureNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(screen: Screen): CueState {
        compose.runOnUiThread {
            compose.activity.window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
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
    fun settingsHomeAndLastChatHaveStableNeighbors() {
        val state = launch(Screen.Home)
        swipeForward(.7f)
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
        swipeBack(.7f)
        compose.runOnIdle { assertEquals(Screen.Settings, state.route.screen) }
        // Settings is left of Home: dragging left returns to the center.
        swipeForward(.08f)
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
        compose.runOnIdle {
            state.create("Orbit", AvatarSpec())
            state.home()
        }
        compose.onNodeWithText(state.agents[1].name, substring = false).performClick()
        val chatId = state.agents[1].id
        compose.runOnIdle { assertEquals(chatId, state.route.agentId) }
        swipeBack()
        swipeBack(.7f)
        compose.runOnIdle { assertEquals(Screen.Settings, state.route.screen) }
        swipeForward(.08f)
        swipeForward(.7f)
        compose.runOnIdle {
            assertEquals(Route(Screen.Chat, chatId), state.route)
            assertEquals(listOf(Route(Screen.Home), Route(Screen.Chat, chatId)), state.routes)
        }
        swipeForward()
        compose.runOnIdle { assertEquals(Route(Screen.Agent, chatId), state.route) }
        swipeBack(.23f)
        swipeBack()
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
    }

    @Test
    fun pageMovesBeforeReleaseAndCancelledDragRestoresIt() {
        val state = launch(Screen.Chat)
        val navigation = compose.onNodeWithTag("navigation")
        val left = navigation.fetchSemanticsNode().boundsInRoot.left
        val width = navigation.fetchSemanticsNode().boundsInRoot.width
        navigation.performTouchInput {
            down(Offset(width * .25f, height * .5f))
            moveTo(Offset(width * .45f, height * .5f), 300)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
        val draggedLeft = compose.onNodeWithTag("page-Chat").fetchSemanticsNode().boundsInRoot.left
        assertTrue(
            "Page must follow the finger before navigation commits",
            draggedLeft > left + width * .15f,
        )
        navigation.performTouchInput {
            moveTo(Offset(width * .27f, height * .5f), 300)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
        assertEquals(
            left,
            compose.onNodeWithTag("page-Chat").fetchSemanticsNode().boundsInRoot.left,
            1f,
        )
    }

    @Test
    fun backDuringADragDoesNotCommitThePreviewedPage() {
        val state = launch(Screen.Chat)
        val navigation = compose.onNodeWithTag("navigation")
        navigation.performTouchInput {
            down(Offset(width * .8f, height * .5f))
            moveTo(Offset(width * .6f, height * .5f), 300)
        }
        compose.runOnIdle { state.back() }
        navigation.performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
        compose.onNodeWithTag("page-Home").assertIsDisplayed()
        compose.onNodeWithTag("page-Agent").assertDoesNotExist()
    }

    @Test
    fun returningToChatKeepsReadingPosition() {
        val state = launch(Screen.Chat)
        compose.runOnIdle {
            state.update(
                state
                    .agent()
                    .copy(
                        messages =
                            List(40) { index ->
                                Message(
                                    text =
                                        "Message $index. This is a longer response to read while checking that navigation preserves the conversation position.",
                                    user = false,
                                )
                            }
                    )
            )
        }
        val messages = compose.onNode(hasScrollToIndexAction())
        for (readOlderMessages in listOf(false, true)) {
            if (readOlderMessages)
                messages.performTouchInput {
                    swipe(
                        Offset(width * .5f, height * .45f),
                        Offset(width * .5f, height * .85f),
                        400,
                    )
                }
            compose.waitForIdle()
            val viewport = messages.fetchSemanticsNode().boundsInRoot
            val anchor =
                compose
                    .onAllNodes(hasText("Message", substring = true))
                    .fetchSemanticsNodes()
                    .filter {
                        it.boundsInRoot.top > viewport.top + 2 &&
                            it.boundsInRoot.bottom < viewport.bottom
                    }
                    .minBy { it.boundsInRoot.top }
            val anchorText = anchor.config[SemanticsProperties.Text].joinToString("") { it.text }
            val position = anchor.boundsInRoot.top
            compose.onNodeWithTag("navigation").performTouchInput {
                swipe(Offset(width * .97f, height * .5f), Offset(width * .3f, height * .5f), 400)
            }
            compose.runOnIdle { assertEquals(Screen.Agent, state.route.screen) }
            swipeBack(.23f)
            compose.runOnIdle { assertEquals(Screen.Chat, state.route.screen) }
            assertEquals(
                "Reading position changed (older messages: $readOlderMessages)",
                position,
                compose.onNodeWithText(anchorText).fetchSemanticsNode().boundsInRoot.top,
                1f,
            )
        }
    }

    @Test
    fun settingsButtonAndBackUseTheSameSpatialDirection() {
        val state = launch(Screen.Home)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.runOnIdle {
            assertEquals(Screen.Settings, state.route.screen)
            assertEquals(1, state.navigationDirection)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle {
            assertEquals(Screen.Home, state.route.screen)
            assertEquals(-1, state.navigationDirection)
        }
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
    fun integrationCategoryScrollingHasPriorityOverScreenNavigation() {
        val state = launch(Screen.Integrations)
        val categories = compose.onNodeWithTag("integration-categories")
        categories.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(Screen.Integrations, state.route.screen) }
        categories.performScrollToNode(hasText("Connectors"))
        compose.onNode(hasText("Connectors") and isSelectable()).performClick()
        compose.onNode(hasText("Connectors") and isSelectable()).assertIsSelected()
        compose.onNodeWithTag("integration-list").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(Screen.Integrations, state.route.screen) }
        swipeBack(.035f)
        compose.runOnIdle { assertEquals(Screen.Home, state.route.screen) }
    }
}
