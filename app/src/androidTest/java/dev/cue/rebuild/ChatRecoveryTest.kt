package dev.cue.rebuild

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(url: String): CueState {
        val state = CueState(compose.activity.application, "cue-recovery-${System.nanoTime()}", url)
        compose.setContent { CueTheme { CueApp(state) } }
        compose.onNodeWithText("Luna").performClick()
        return state
    }

    @Test
    fun interruptedStreamKeepsPartialReplyAndOffersRetry() {
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${listener.localPort}"
        val worker =
            thread(isDaemon = true) {
                listener.use { server ->
                    server.accept().use { socket ->
                        socket.getInputStream().bufferedReader().readLine()
                        val body =
                            "{\"type\":\"session\",\"id\":\"test-session\"}\n{\"type\":\"delta\",\"text\":\"Partial answer\"}\n"
                        socket
                            .getOutputStream()
                            .write(
                                ("HTTP/1.1 200 OK\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" +
                                        body)
                                    .toByteArray()
                            )
                        socket.getOutputStream().flush()
                    }
                }
            }
        try {
            val state = launch(url)
            compose.onNode(hasSetTextAction()).performTextInput("Hello")
            compose.onNodeWithContentDescription("Send message").performClick()
            compose.waitUntil(10000) { state.busy.isEmpty() }
            compose.onNodeWithText("Partial answer").assertExists()
            compose.onNodeWithText("Tap to retry.", substring = true).assertExists()
            compose.runOnIdle {
                assertNull(state.error) // Feedback remains inline, not in a blocking dialog.
                assertTrue(state.agent().messages.first().failed)
                assertEquals("test-session", state.agent().sessionId)
            }
        } finally {
            listener.close()
            worker.join(1000)
        }
    }

    @Test
    fun stoppingAReplyDoesNotMarkItAsFailed() {
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${listener.localPort}"
        val release = CountDownLatch(1)
        val worker =
            thread(isDaemon = true) {
                listener.accept().use { socket ->
                    listener.close()
                    socket.getInputStream().bufferedReader().readLine()
                    socket
                        .getOutputStream()
                        .write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: application/x-ndjson\r\nConnection: close\r\n\r\n" +
                                    "{\"type\":\"session\",\"id\":\"test-session\"}\n{\"type\":\"delta\",\"text\":\"Working\"}\n")
                                .toByteArray()
                        )
                    socket.getOutputStream().flush()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        try {
            val state = launch(url)
            compose.onNode(hasSetTextAction()).performTextInput("Hello")
            compose.onNodeWithContentDescription("Send message").performClick()
            compose.waitUntil(10000) { state.agent().messages.any { !it.user } }
            compose.onNodeWithContentDescription("Stop generating").performClick()
            compose.waitUntil(10000) { state.busy.isEmpty() }
            compose.runOnIdle {
                assertNull(state.error)
                assertFalse(state.agent().messages.any { it.failed })
            }
        } finally {
            release.countDown()
            listener.close()
            worker.join(1000)
        }
    }

    @Test
    fun expiredApprovalClearsTheStaleQuestionWithoutRetryingIt() {
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${listener.localPort}"
        val worker =
            thread(isDaemon = true) {
                listener.use { server ->
                    server.accept().use { socket ->
                        socket.getInputStream().bufferedReader().readLine()
                        val body =
                            """{"code":"input_expired","error":"That approval is no longer active. Send a new message to request it again."}"""
                        socket
                            .getOutputStream()
                            .write(
                                ("HTTP/1.1 409 Conflict\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" +
                                        body)
                                    .toByteArray()
                            )
                        socket.getOutputStream().flush()
                    }
                }
            }
        try {
            val state = launch(url)
            compose.runOnIdle {
                state.update(
                    state
                        .agent()
                        .copy(
                            sessionId = "old-session",
                            pendingInputs =
                                listOf(
                                    InputPrompt(
                                        "expired",
                                        "Approve?",
                                        false,
                                        listOf(InputChoice("approve", "Approve")),
                                    )
                                ),
                        )
                )
                assertTrue(state.send(state.agent().id, "Approve", optionId = "approve"))
            }
            compose.waitUntil(10000) { state.busy.isEmpty() }
            compose.runOnIdle {
                assertTrue(state.agent().pendingInputs.isEmpty())
                assertEquals("old-session", state.agent().sessionId)
                assertEquals(1, state.agent().messages.count { it.user })
                assertTrue(state.agent().messages.last().failureReason.contains("no longer active"))
            }
        } finally {
            listener.close()
            worker.join(1000)
        }
    }

    @Test
    fun draftsAppearanceAndPendingQuestionsSurviveReload() {
        val app = compose.activity.application
        val store = "cue-persist-${System.nanoTime()}"
        val state = CueState(app, store)
        val question =
            InputPrompt("question-1", "Pick a color", false, listOf(InputChoice("blue", "Blue")))
        compose.runOnIdle {
            state.update(
                state.agent().copy(draft = "Unsent text", pendingInputs = listOf(question))
            )
            state.changeAppearance("Dark")
            val restored = CueState(app, store)
            assertEquals("Unsent text", restored.agent().draft)
            assertEquals(listOf(question), restored.agent().pendingInputs)
            assertEquals("Dark", restored.appearance)
        }
    }
}
