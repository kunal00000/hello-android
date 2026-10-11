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
