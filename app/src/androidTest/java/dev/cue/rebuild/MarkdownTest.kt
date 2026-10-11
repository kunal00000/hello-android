package dev.cue.rebuild

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MarkdownTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bothSpeakersRenderAndStreamingUpdatesRemainVisible() {
        var text by mutableStateOf("**Bold** and _italic_")
        compose.setContent {
            CueTheme {
                Column {
                    MessageBubble(Message(text = text, user = true))
                    MessageBubble(Message(text = text, user = false))
                }
            }
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Bold and italic").fetchSemanticsNodes().size == 2
        }
        compose.runOnIdle { text += "\n\n## Heading\n\n- Item\n\n```kotlin\nval x = 1\n```" }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Heading").fetchSemanticsNodes().size == 2
        }
        compose.onAllNodesWithText("Item").assertCountEquals(2)
        compose.onAllNodesWithText("Copy code").assertCountEquals(2)
        compose.onAllNodesWithText("val x = 1", substring = true).assertCountEquals(2)
    }

    @Test
    fun linksAreInteractiveAndUnsafeSchemesAreIgnored() {
        var opened = ""
        compose.setContent {
            CueTheme {
                CompositionLocalProvider(
                    LocalUriHandler provides
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                opened = uri
                            }
                        }
                ) {
                    MarkdownText(
                        "[Docs](https://example.com/docs)\n\n[Invalid](javascript:alert%281%29)"
                    )
                }
            }
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Docs").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Docs").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals("https://example.com/docs", opened)
            opened = ""
        }
        compose.onNodeWithText("Invalid").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("", opened) }
    }

    @Test
    fun selectionFormattingPreviewAndSendPreserveMarkdownSource() {
        var draft by mutableStateOf("")
        var sent = ""
        compose.setContent {
            CueTheme {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Composer(
                        draft,
                        { draft = it },
                        "Message",
                        onSend = {
                            sent = draft
                            draft = ""
                        },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Message").performTextInput("hello world")
        compose.onNodeWithContentDescription("Message").performTextInputSelection(TextRange(6, 11))
        compose.onNodeWithContentDescription("Markdown formatting").performClick()
        compose.onNodeWithContentDescription("Bold").performClick()
        compose.runOnIdle { assertEquals("hello **world**", draft) }
        compose.onNodeWithText("Preview").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("hello world").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("markdown-preview").assertIsDisplayed()
        capture("markdown-composer-preview")
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithContentDescription("Message").assertTextEquals("hello **world**")
        capture("markdown-composer-edit")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.runOnIdle {
            assertEquals("hello **world**", sent)
            assertEquals("", draft)
        }
        compose
            .onNodeWithContentDescription("Message")
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
            )
    }

    @Test
    fun hidingKeyboardReturnsToADockedCompactDraft() {
        var draft by mutableStateOf("")
        compose.setContent {
            CueTheme {
                Column(
                    Modifier.fillMaxSize()
                        .safeDrawingPadding()
                        .imePadding()
                        .testTag("composer-host"),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Composer(draft, { draft = it }, "Message", onSend = {}, onAdd = {}, onMic = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Message").performTextInput("Keep this draft")
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithContentDescription("Markdown formatting")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Expand").assertDoesNotExist()
        compose.onNodeWithText("Collapse").assertDoesNotExist()
        compose.runOnUiThread {
            androidx.core.view.WindowCompat.getInsetsController(
                    compose.activity.window,
                    compose.activity.window.decorView,
                )
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithContentDescription("Markdown formatting")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        compose.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand composer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").assertTextEquals("Keep this draft")
        val composer = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag("composer-host").fetchSemanticsNode().boundsInRoot
        assertEquals(host.left, composer.left, 1f)
        assertEquals(host.right, composer.right, 1f)
        assertEquals(host.bottom, composer.bottom, 1f)
        capture("composer-docked-draft")
        compose.onNodeWithContentDescription("Message").performClick()
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithContentDescription("Markdown formatting")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Message").assertTextEquals("Keep this draft")
    }

    @Test
    fun composerExpandsWithoutLosingSelectionAndCollapsesByDragging() {
        var draft by mutableStateOf("")
        compose.setContent {
            CueTheme {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Composer(draft, { draft = it }, "Message", onSend = {}, onAdd = {}, onMic = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Markdown formatting").assertDoesNotExist()
        capture("composer-idle")
        compose.onNodeWithContentDescription("Message").performClick()
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithContentDescription("Markdown formatting")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Message").performTextInput("hello world")
        compose.onNodeWithContentDescription("Message").performTextInputSelection(TextRange(6, 11))
        capture("composer-writing")
        val shortHeight = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithContentDescription("Expand composer").performClick()
        compose.onNodeWithContentDescription("Collapse composer").assertIsDisplayed()
        assertTrue(
            compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot.height > shortHeight
        )
        compose.onNodeWithContentDescription("Markdown formatting").performClick()
        compose.onNodeWithContentDescription("Bold").performClick()
        compose.runOnIdle { assertEquals("hello **world**", draft) }
        capture("composer-expanded")
        compose.onNodeWithContentDescription("Collapse composer").performTouchInput {
            swipe(center, center.copy(y = center.y + 140f), 400)
        }
        compose.onNodeWithContentDescription("Expand composer").assertIsDisplayed()
        compose.onNodeWithText("Preview").performClick()
        compose.onNodeWithContentDescription("Close formatting").performClick()
        compose.onNodeWithTag("markdown-preview").assertIsDisplayed()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithContentDescription("Message").assertTextEquals("hello **world**")
        compose.onNodeWithContentDescription("Expand composer").performTouchInput {
            swipe(center, center.copy(y = center.y - 140f), 400)
        }
        compose.onNodeWithContentDescription("Collapse composer").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Expand composer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Message").assertTextEquals("hello **world**")
    }

    @Test
    fun composerKeepsAttachmentVoiceAndStopActionsAvailable() {
        var attachments = 0
        var voice = 0
        var stopped = 0
        var sent = 0
        var draft by mutableStateOf("")
        var busy by mutableStateOf(false)
        var canSend by mutableStateOf(false)
        compose.setContent {
            CueTheme {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Composer(
                        draft,
                        { draft = it },
                        "Message",
                        busy,
                        onSend = { sent++ },
                        onStop = { stopped++ },
                        onAdd = { attachments++ },
                        onMic = { voice++ },
                        canSend = canSend,
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Add attachment").performClick()
        compose.onNodeWithContentDescription("Voice input").performClick()
        compose.runOnIdle { canSend = true }
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.onNodeWithContentDescription("Message").performTextInput("A draft")
        compose.onNodeWithContentDescription("Add attachment").performClick()
        compose.onNodeWithContentDescription("Voice input").performClick()
        compose.runOnIdle { busy = true }
        compose.onNodeWithContentDescription("Markdown formatting").performClick()
        compose.onNodeWithContentDescription("Stop generating").performClick()
        compose.runOnIdle {
            assertEquals(2, attachments)
            assertEquals(2, voice)
            assertEquals(1, stopped)
            assertEquals(1, sent)
        }
    }

    @Test
    fun longDraftKeepsControlsVisibleInTheChatAndDarkTheme() {
        val state = CueState(compose.activity.application, "composer-test-${System.nanoTime()}")
        state.open(Screen.Chat, state.agent().id)
        compose.setContent { CueTheme(dark = state.appearance == "Dark") { CueApp(state) } }
        val field = compose.onNode(hasSetTextAction())
        val draft = "## A little room to write\n\n" + (1..20).joinToString("\n") { "- Idea $it" }
        field.performTextInput(draft)
        compose.onNodeWithContentDescription("Expand composer").performClick()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
        capture("composer-chat-expanded")
        compose.runOnIdle { state.changeAppearance("Dark") }
        capture("composer-chat-expanded-dark")
        compose.onNodeWithText("Preview").performClick()
        compose.onNodeWithTag("markdown-preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithContentDescription("Collapse composer").performClick()
        field.assertTextEquals(draft)
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(Screen.Chat, state.route.screen)
            assertEquals(draft, state.agent().draft)
        }
    }

    @Test
    fun markdownFitsBubblesInBothThemes() {
        var dark by mutableStateOf(false)
        val sample =
            "## A simple plan\n\n**Start small**, stay _curious_, and read [the guide](https://example.com).\n\n- Choose one idea\n- Make room to explore\n\n> Leave a little space.\n\n| Step | Status |\n| --- | --- |\n| Design | Ready |\n| Build | Next |"
        compose.setContent {
            CueTheme(dark = dark) {
                Column(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MessageBubble(
                        Message(text = "**Looks good.** Let's try it.", user = true),
                        Modifier.widthIn(max = 300.dp),
                    )
                    MessageBubble(
                        Message(text = sample, user = false),
                        Modifier.widthIn(max = 320.dp),
                    )
                    Composer("**A bold idea**", {}, "Message", onSend = {})
                }
            }
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("A simple plan").fetchSemanticsNodes().isNotEmpty()
        }
        capture("markdown-light")
        compose.onNodeWithText("Ready", substring = true).assertIsDisplayed()
        compose.runOnIdle { dark = true }
        capture("markdown-dark")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap =
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: error("No screenshot")
        val dir = File(compose.activity.filesDir, "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
