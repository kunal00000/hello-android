package dev.cue.rebuild

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceProductionTest {
    @Test
    fun deviceWorkflowRoundTrip() = runBlocking {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("liveProduction") == "true"
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        suspend fun foregroundCue() {
            context.startActivity(
                android.content
                    .Intent(context, MainActivity::class.java)
                    .addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
            )
            withTimeout(10000) {
                while (
                    !withContext(Dispatchers.Main) {
                        androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
                            .getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                            .any { it is MainActivity }
                    }
                ) delay(100)
            }
        }
        foregroundCue()
        val name = "device-production-test"
        val token = BackendCredentialStore(context).read() ?: error("Phone is not connected")
        BackendCredentialStore(context, name).save(token)
        val state =
            withContext(Dispatchers.Main) {
                CueState(context.applicationContext as Application, name)
            }
        val id = state.agents.first().id
        try {
            withContext(Dispatchers.Main) {
                val agent = state.agent(id)
                state.update(
                    agent.copy(sessionId = "", messages = emptyList(), pendingInputs = emptyList())
                )
                state.deviceAccess.connect("device-dialer", true)
                assertTrue(state.send(id, "Open my phone dialer without entering a number."))
            }
            suspend fun awaitIdle() =
                withTimeout(180000) {
                    while (withContext(Dispatchers.Main) { id in state.busy }) delay(200)
                }
            awaitIdle()
            val input =
                withContext(Dispatchers.Main) {
                    state.agent(id).pendingInputs.firstOrNull { it.deviceAction != null }
                }
            assertNotNull("Backend did not request the device tool", input)
            val payload = JSONObject(input!!.deviceAction!!)
            assertEquals("open_dialer", payload.getString("operation"))
            assertTrue(payload.optString("phone").isEmpty())
            // Verify the same real request cannot run after disconnect or without Ask approval.
            withContext(Dispatchers.Main) { state.deviceAccess.connect("device-dialer", false) }
            assertEquals(
                "unavailable",
                state.deviceAccess.execute(payload, true).getString("status"),
            )
            withContext(Dispatchers.Main) { state.deviceAccess.connect("device-dialer", true) }
            assertEquals("unavailable", state.deviceAccess.execute(payload).getString("status"))
            val result = state.deviceAccess.execute(payload, approved = true)
            assertEquals("confirmation_opened", result.getString("status"))
            // Device drafts leave Cue. Return as a user would before continuing the chat.
            foregroundCue()
            withContext(Dispatchers.Main) {
                assertTrue(state.send(id, result.toString(), deviceResult = true))
            }
            awaitIdle()
            withContext(Dispatchers.Main) {
                assertTrue(state.agent(id).pendingInputs.isEmpty())
                assertTrue(state.agent(id).messages.last { !it.user }.text.contains("dialer", true))
                assertFalse(
                    state.agent(id).messages.any {
                        it.user &&
                            it.text.startsWith("{") &&
                            it.text.contains("confirmation_opened")
                    }
                )
            }
            withContext(Dispatchers.Main) {
                state.deviceAccess.setPolicy(
                    deviceActions.first { it.id == "open_dialer" },
                    AccessPolicy.Deny,
                )
                assertTrue(state.send(id, "Open the phone dialer again."))
            }
            awaitIdle()
            withContext(Dispatchers.Main) {
                assertTrue(
                    "Denied action must not request device execution",
                    state.agent(id).pendingInputs.isEmpty(),
                )
                assertFalse(
                    "Follow-up failed: ${state.agent(id).messages.filter { it.failed }.map { it.failureReason }}",
                    state.agent(id).messages.any { it.failed },
                )
                val answer = state.agent(id).messages.last { !it.user }.text
                assertTrue(
                    "Agent should explain the changed policy: $answer",
                    listOf("deny", "denied", "disabled", "blocked").any {
                        answer.contains(it, true)
                    },
                )
            }
        } finally {
            withContext(Dispatchers.Main) {
                state.cancel(id)
                state.deviceAccess.connect("device-dialer", false)
            }
            BackendCredentialStore(context, name).clear()
            context.getSharedPreferences(name, 0).edit().clear().commit()
        }
    }
}
