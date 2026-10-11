package dev.cue.rebuild

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class BackendConnectionTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun credentialIsEncryptedAndDisconnectRemovesIt() {
        val store = BackendCredentialStore(context, "credential-test")
        val token = "cue_" + "a".repeat(43)
        try {
            store.save(token)
            assertEquals(token, BackendCredentialStore(context, "credential-test").read())
            val values =
                context.getSharedPreferences("credential-test_backend", 0).all.values.joinToString()
            assertFalse(values.contains(token))
            store.clear()
            assertNull(store.read())
        } finally {
            store.clear()
        }
    }

    @Test
    fun missingCredentialPreservesDraftAndOpensSetup() = runBlocking {
        withContext(Dispatchers.Main) {
            val state = CueState(context.applicationContext as Application, "connection-test")
            state.disconnectBackend()
            val agent = state.agents.first()
            state.update(agent.copy(draft = "Keep this draft"))
            assertFalse(state.send(agent.id, "Keep this draft"))
            assertEquals("Keep this draft", state.agent(agent.id).draft)
            assertEquals(Screen.Backend, state.route.screen)
        }
    }

    // Explicitly invoked after ADB writes a temporary file into the debuggable
    // app's private directory. Never accepts credentials through exported intents.
    @Test
    fun provisionConnectedPhone() = runBlocking {
        val file = File(context.filesDir, "backend-token")
        org.junit.Assume.assumeTrue("No provisioning credential supplied", file.exists())
        val token = file.readText().trim()
        file.delete()
        val state =
            withContext(Dispatchers.Main) {
                CueState(context.applicationContext as Application).also {
                    it.connectBackend(token)
                }
            }
        repeat(60) {
            if (!withContext(Dispatchers.Main) { state.checkingBackend }) return@repeat
            kotlinx.coroutines.delay(500)
        }
        withContext(Dispatchers.Main) {
            assertEquals(null, state.connectionError)
            assertEquals("Connected", state.backendStatus)
            assertTrue(state.hasBackendCredential)
        }
    }

    @Test
    fun productionRoundTrip() = runBlocking {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("liveProduction") == "true"
        )
        val activity = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val saved = BackendCredentialStore(context).read() ?: error("Phone is not connected")
        val name = "production-verification"
        BackendCredentialStore(context, name).save(saved)
        val state =
            withContext(Dispatchers.Main) {
                CueState(context.applicationContext as Application, name)
            }
        val file =
            File(context.cacheDir, "cue-verification.txt").apply {
                writeText("The verification word is pineapple.")
            }
        try {
            val id =
                withContext(Dispatchers.Main) {
                    val a = state.agents.first()
                    state.update(
                        a.copy(sessionId = "", messages = emptyList(), pendingInputs = emptyList())
                    )
                    assertTrue(state.send(a.id, "Reply with exactly: connected"))
                    a.id
                }
            suspend fun awaitReply(word: String) {
                kotlinx.coroutines.withTimeout(180_000) {
                    while (withContext(Dispatchers.Main) { id in state.busy }) kotlinx.coroutines
                        .delay(200)
                }
                withContext(Dispatchers.Main) {
                    val agent = state.agent(id)
                    assertFalse(
                        "Failed while expecting $word: ${agent.messages.filter { it.failed }.map { it.failureReason }}",
                        agent.messages.any { it.failed },
                    )
                    assertTrue(
                        agent.messages.last { !it.user }.text.contains(word, ignoreCase = true)
                    )
                    assertTrue(agent.sessionId.contains('.'))
                }
            }
            awaitReply("connected")
            withContext(Dispatchers.Main) {
                assertTrue(
                    state.send(
                        id,
                        "What single word did you just reply with? Reply only with that word.",
                    )
                )
            }
            awaitReply("connected")
            withContext(Dispatchers.Main) {
                assertTrue(
                    state.send(
                        id,
                        "Read the attachment and reply only with its verification word.",
                        listOf(Attachment(file.name, "text/plain", file.absolutePath)),
                    )
                )
            }
            awaitReply("pineapple")
        } finally {
            withContext(Dispatchers.Main) { state.agents.forEach { state.cancel(it.id) } }
            BackendCredentialStore(context, name).clear()
            context.getSharedPreferences(name, 0).edit().clear().commit()
            file.delete()
            activity.close()
        }
    }
}
