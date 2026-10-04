package dev.cue.rebuild

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Explicit integration check. Run with Eve + mobile adapter and adb reverse active. */
class LiveBackendTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sendsAndContinuesARealConversation() {
        val state = CueState(compose.activity.application, "cue-live-${System.nanoTime()}")
        compose.setContent { CueTheme { CueApp(state) } }
        compose.onNodeWithText("Luna").performClick()
        compose
            .onNode(hasSetTextAction())
            .performTextInput(
                "For this conversation only, our code word is sunflower. Do not save it to memory or write any files. Reply with exactly: Connected."
            )
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.waitUntil(180000) { state.busy.isEmpty() }
        compose.runOnIdle {
            assertNull(state.error)
            assertTrue(
                state.agent().messages.any { !it.user && it.text.contains("Connected", true) }
            )
            assertTrue(state.agent().sessionId.isNotEmpty())
        }
        val session = state.agent().sessionId
        compose
            .onNode(hasSetTextAction())
            .performTextInput("What was the code word? Reply with just the word.")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.waitUntil(180000) { state.busy.isEmpty() }
        compose.runOnIdle {
            assertNull(state.error)
            assertEquals(session, state.agent().sessionId)
            assertTrue(state.agent().messages.last().text.contains("sunflower", true))
        }
    }

    @Test
    fun anAgentQuestionResumesAfterSelectingAnAnswer() {
        val state = CueState(compose.activity.application, "cue-input-live-${System.nanoTime()}")
        compose.setContent { CueTheme { CueApp(state) } }
        compose.onNodeWithText("Luna").performClick()
        compose
            .onNode(hasSetTextAction())
            .performTextInput(
                "Use ask_question to ask Which color? with Blue and Red options. Wait for my answer, then reply only with the selected color. This is a temporary test; do not save memory or write files."
            )
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.waitUntil(180000) { state.busy.isEmpty() }
        compose.runOnIdle { assertTrue(state.agent().pendingInputs.isNotEmpty()) }
        val choice = state.agent().pendingInputs.first().choices.first()
        compose.onNodeWithText(choice.label, substring = false).performClick()
        compose.waitUntil(180000) { state.busy.isEmpty() }
        compose.runOnIdle {
            assertTrue(state.agent().pendingInputs.isEmpty())
            assertFalse(state.agent().messages.any { it.failed })
            assertFalse(state.agent().messages.last().user)
            assertTrue(state.agent().messages.last().text.contains(choice.label, ignoreCase = true))
            assertTrue(state.agent().messages.any { it.id.startsWith("input-") })
        }
    }
}
