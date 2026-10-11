package dev.cue.rebuild

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IntegrationsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(id: String? = null): CueState {
        compose.runOnUiThread {
            compose.activity.window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        val state = CueState(compose.activity.application, "integrations-test-${System.nanoTime()}")
        state.open(Screen.Integrations)
        if (id != null) state.open(Screen.IntegrationDetail, integrationId = id)
        compose.setContent { CueTheme { CueApp(state) } }
        return state
    }

    @Test
    fun catalogAndBrandAssetsAreComplete() {
        val context = compose.activity
        val catalog = integrationCatalog(context)
        assertEquals(102, catalog.size)
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        assertEquals(46, catalog.count { it.category == "connectors" })
        catalog
            .mapNotNull { it.icon }
            .forEach { icon ->
                context.assets.open("integrations/$icon.svg").use {
                    SVG.getFromInputStream(it).renderToPicture(64, 64)
                }
            }
    }

    @Test
    fun deviceSetupPoliciesAndDisconnect() {
        val state = launch("device-dialer")
        compose.onNodeWithText("Connect", useUnmergedTree = true).performClick()
        compose.onNode(hasText("Connect this device") and hasClickAction()).performClick()
        compose.onNodeWithText("Manage").assertIsDisplayed()
        compose.onNodeWithText("Android access").assertDoesNotExist()
        compose.onNodeWithText("Open the phone dialer").performClick()
        compose.onNodeWithText("Deny", useUnmergedTree = true).performClick()
        val action = deviceActions.first { it.id == "open_dialer" }
        compose.runOnIdle { assertEquals(AccessPolicy.Deny, state.deviceAccess.policy(action)) }
        compose.onNodeWithText("Disconnect").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(state.deviceAccess.connected("device-dialer")) }
        compose.onNodeWithText("Connect", useUnmergedTree = true).performClick()
        compose.onNode(hasText("Connect this device") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(AccessPolicy.Ask, state.deviceAccess.policy(action)) }
    }

    @Test
    fun deviceExecutionFailsClosedAndReconnectClearsQueuedData() = runBlocking {
        val access = DeviceAccess(compose.activity, "access-test-${System.nanoTime()}")
        val action = deviceActions.first { it.id == "open_dialer" }
        val input = JSONObject().put("operation", action.id)
        fun capability() =
            access
                .capabilities()
                .getJSONArray("operations")
                .getJSONObject(deviceActions.indexOf(action))
        assertFalse(capability().getBoolean("connected"))
        assertEquals("unavailable", access.execute(input, true).getString("status"))
        access.connect(action.integrationId, true)
        assertTrue(access.execute(input).getString("message").contains("approval"))
        assertTrue(capability().getBoolean("connected"))
        access.setPolicy(action, AccessPolicy.Deny)
        assertEquals("Deny", capability().getString("policy"))
        assertEquals("unavailable", access.execute(input, true).getString("status"))
        access.saveResult("test", "private queued result")
        access.connect(action.integrationId, false)
        assertNull(access.savedResult("test"))
        access.connect(action.integrationId, true)
        assertEquals(AccessPolicy.Ask, access.policy(action))
        assertEquals(
            "unavailable",
            access.execute(JSONObject().put("operation", "unknown"), true).getString("status"),
        )
    }

    @Test
    fun searchFindsProviderAndBackPreservesQuery() {
        val state = launch()
        compose.onNodeWithText("Search integrations").performTextInput("Linear")
        compose.onNode(hasText("Linear") and hasSetTextAction().not()).performClick()
        compose.runOnIdle { assertEquals("linear", state.route.integrationId) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Linear") and hasSetTextAction().not()).assertIsDisplayed()
        compose.onNodeWithText("Calendar").assertDoesNotExist()
    }

    @Test
    fun deviceRequestWaitsForConsentAndSendsDenialWithoutChatBubble() {
        val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val received = java.util.concurrent.atomic.AtomicReference<JSONObject>()
        val worker =
            kotlin.concurrent.thread(isDaemon = true) {
                server.use { listener ->
                    listener.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", true))
                                length = line.substringAfter(":").trim().toInt()
                        }
                        val chars = CharArray(length)
                        var read = 0
                        while (read < length) {
                            val count = reader.read(chars, read, length - read)
                            if (count < 0) break
                            read += count
                        }
                        received.set(JSONObject(String(chars)))
                        val body =
                            """{"type":"session","id":"device-ui"}
{"type":"delta","text":"Access denied."}
{"type":"done"}
"""
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
            val state =
                CueState(
                    compose.activity.application,
                    "device-ui-${System.nanoTime()}",
                    "http://127.0.0.1:${server.localPort}",
                )
            val agent = state.agent()
            state.deviceAccess.connect("device-dialer", true)
            state.update(
                agent.copy(
                    pendingInputs =
                        listOf(
                            InputPrompt(
                                "device-request",
                                "Open dialer",
                                true,
                                emptyList(),
                                """{"operation":"open_dialer"}""",
                            )
                        )
                )
            )
            state.open(Screen.Chat, agent.id)
            compose.setContent { CueTheme { CueApp(state) } }
            compose.onNodeWithText("Allow once").assertIsDisplayed()
            assertNull(received.get())
            compose.onNodeWithText("Deny").performClick()
            compose.waitUntil(10000) { received.get() != null && state.busy.isEmpty() }
            val request = received.get()
            assertTrue(request.getString("message").contains("user denied"))
            compose.runOnIdle {
                assertTrue(state.agent(agent.id).pendingInputs.isEmpty())
                assertTrue(state.agent(agent.id).messages.none { it.user })
            }
        } finally {
            server.close()
            worker.join(1000)
        }
    }
}
