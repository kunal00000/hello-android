package dev.cue.rebuild

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// Small value types shared by storage, screens, and transport; no parallel DTO hierarchy.
data class AvatarSpec(
    val shape: String = "heart",
    val face: String = "bashful",
    val color: Long = 0xFFB17A4D,
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val user: Boolean,
    val failed: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val failureReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class InputChoice(val id: String, val label: String)

data class InputPrompt(
    val id: String,
    val prompt: String,
    val freeform: Boolean,
    val choices: List<InputChoice>,
)

data class Agent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatar: AvatarSpec = AvatarSpec(),
    val instructions: String = "",
    val memories: List<String> = emptyList(),
    val messages: List<Message> = emptyList(),
    val sessionId: String = "",
    val pinned: Boolean = false,
    val group: Boolean = false,
    val draft: String = "",
    val members: List<String> = emptyList(),
    val pendingInputs: List<InputPrompt> = emptyList(),
)

enum class Screen {
    Home,
    Chat,
    Agent,
    Settings,
    Instructions,
    Memory,
    Apps,
    Skills,
    Mail,
    Computer,
    Language,
    AppIcon,
    Account,
    Search,
    Messenger,
    Wallet,
}

data class Route(val screen: Screen, val agentId: String? = null)

class CueState
@JvmOverloads
constructor(
    application: Application,
    storageName: String = "cue",
    private val backendUrl: String = BuildConfig.BACKEND_URL,
) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(storageName, 0)
    var error by mutableStateOf<String?>(null)
    var agents by mutableStateOf(loadAgents())
        private set

    var routes by mutableStateOf(listOf(Route(Screen.Home)))
        private set

    var forwardRoutes by mutableStateOf(emptyList<Route>())
        private set

    var appearance by
        mutableStateOf(preferences.getString("appearance", "Follow system") ?: "Follow system")
        private set

    var userName by mutableStateOf(preferences.getString("name", "Kunal") ?: "Kunal")
        private set

    var language by mutableStateOf(preferences.getString("language", "English") ?: "English")
        private set

    var skills by mutableStateOf(preferences.getStringSet("skills", emptySet())!!.toList())
        private set

    var busy by mutableStateOf(setOf<String>())
        private set

    var backendStatus by mutableStateOf("Not checked")
        private set

    var checkingBackend by mutableStateOf(false)
        private set

    fun checkBackend() {
        if (checkingBackend) return
        checkingBackend = true
        viewModelScope.launch {
            backendStatus =
                withContext(Dispatchers.IO) {
                    runCatching {
                            client
                                .newCall(Request.Builder().url(backendUrl + "/health").build())
                                .execute()
                                .use { if (it.isSuccessful) "Connected" else "Unavailable" }
                        }
                        .getOrDefault("Unavailable")
                }
            checkingBackend = false
        }
    }

    private val calls = mutableMapOf<String, Call>()
    private val jobs = mutableMapOf<String, Job>()
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    val route
        get() = routes.last()

    fun agent(id: String? = route.agentId): Agent =
        agents.firstOrNull { it.id == id } ?: agents.first()

    fun open(screen: Screen, agentId: String? = route.agentId) {
        forwardRoutes = emptyList()
        routes = routes + Route(screen, agentId)
    }

    fun back() {
        if (routes.size > 1) {
            forwardRoutes = listOf(route) + forwardRoutes
            routes = routes.dropLast(1)
        }
    }

    fun forward() {
        val next = forwardRoutes.firstOrNull() ?: return
        routes = routes + next
        forwardRoutes = forwardRoutes.drop(1)
    }

    fun home() {
        routes = listOf(Route(Screen.Home))
        forwardRoutes = emptyList()
    }

    fun update(agent: Agent) {
        agents = agents.map { if (it.id == agent.id) agent else it }
        persist()
    }

    fun create(name: String, avatar: AvatarSpec, group: Boolean = false) {
        if (name.isBlank()) return
        val agent = Agent(name = name.trim(), avatar = avatar, group = group)
        agents = agents + agent
        persist()
        home()
        open(Screen.Chat, agent.id)
    }

    fun createGroup(members: Set<String>) {
        val participants = agents.filter { it.id in members && !it.group }
        if (participants.isEmpty()) return
        val group =
            Agent(
                name = participants.joinToString(", ") { it.name },
                avatar = AvatarSpec("chat", "wide_eyes", 0xFF248BFF),
                group = true,
                members = participants.map { it.id },
            )
        agents = agents + group
        persist()
        home()
        open(Screen.Chat, group.id)
    }

    fun changeAppearance(value: String) {
        appearance = value
        preferences.edit().putString("appearance", value).apply()
    }

    fun setName(value: String) {
        userName = value.trim()
        preferences.edit().putString("name", userName).apply()
    }

    fun changeLanguage(value: String) {
        language = value
        preferences.edit().putString("language", value).apply()
    }

    fun addSkill(value: String) {
        skills = (skills + value).distinct()
        preferences.edit().putStringSet("skills", skills.toSet()).apply()
    }

    fun removeSkill(value: String) {
        skills = skills - value
        preferences.edit().putStringSet("skills", skills.toSet()).apply()
    }

    fun remember(agentId: String, text: String) {
        val a = agent(agentId)
        if (text.isNotBlank()) update(a.copy(memories = a.memories + text.trim()))
    }

    fun send(
        agentId: String,
        text: String,
        attachments: List<Attachment> = emptyList(),
        optionId: String? = null,
    ): Boolean {
        if ((text.isBlank() && attachments.isEmpty()) || agentId in busy) return false
        val current = agent(agentId)
        val pending = current.pendingInputs.firstOrNull()
        if (pending != null && attachments.isNotEmpty()) {
            error = "Reply to the agent’s question before adding attachments."
            return false
        }
        if (pending != null && !pending.freeform && optionId == null) {
            error = "Choose one of the options to continue."
            return false
        }
        val message = Message(text = text.trim(), user = true, attachments = attachments)
        update(current.copy(messages = current.messages + message, draft = ""))
        busy = busy + agentId
        jobs[agentId] =
            viewModelScope.launch {
                var assistantId: String? = null
                try {
                    val encoded =
                        withContext(Dispatchers.IO) {
                            JSONArray(
                                attachments.map { file ->
                                    JSONObject()
                                        .put("name", file.name)
                                        .put("mime", file.mime)
                                        .put(
                                            "data",
                                            android.util.Base64.encodeToString(
                                                java.io.File(file.path).readBytes(),
                                                android.util.Base64.NO_WRAP,
                                            ),
                                        )
                                }
                            )
                        }
                    val body =
                        JSONObject()
                            .put("attachments", encoded)
                            .put(
                                "message",
                                text.trim().ifBlank { "Please review the attached files." },
                            )
                            .put("sessionId", current.sessionId)
                            .put(
                                "agent",
                                JSONObject()
                                    .put("name", current.name)
                                    .put("instructions", current.instructions)
                                    .put("memories", JSONArray(current.memories))
                                    .put("skills", JSONArray(skills))
                                    .put("language", language)
                                    .put(
                                        "participants",
                                        JSONArray(
                                            current.members.map { id ->
                                                val member = agent(id)
                                                JSONObject()
                                                    .put("name", member.name)
                                                    .put("instructions", member.instructions)
                                            }
                                        ),
                                    ),
                            )
                    if (pending != null)
                        body.put(
                            "reply",
                            JSONObject().put("requestId", pending.id).apply {
                                if (optionId != null) put("optionId", optionId)
                            },
                        )
                    val request =
                        Request.Builder()
                            .url(backendUrl + "/chat")
                            .post(body.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                    val call = client.newCall(request)
                    calls[agentId] = call
                    withContext(Dispatchers.IO) {
                        call.execute().use { response ->
                            if (!response.isSuccessful)
                                throw java.io.IOException("Backend returned ${response.code}")
                            val reader =
                                response.body?.charStream()?.buffered()
                                    ?: throw java.io.IOException("Empty response")
                            var completed = false
                            reader.forEachLine { line ->
                                if (line.isNotBlank()) {
                                    val event = JSONObject(line)
                                    runBlocking(Dispatchers.Main) {
                                        val a = agent(agentId)
                                        when (event.getString("type")) {
                                            "session" ->
                                                update(
                                                    a.copy(
                                                        sessionId = event.getString("id"),
                                                        pendingInputs =
                                                            if (pending == null) a.pendingInputs
                                                            else
                                                                a.pendingInputs.filterNot {
                                                                    it.id == pending.id
                                                                },
                                                    )
                                                )
                                            "delta" -> {
                                                if (assistantId == null) {
                                                    assistantId = UUID.randomUUID().toString()
                                                    update(
                                                        a.copy(
                                                            messages =
                                                                a.messages +
                                                                    Message(
                                                                        assistantId!!,
                                                                        event.getString("text"),
                                                                        false,
                                                                    )
                                                        )
                                                    )
                                                } else
                                                    update(
                                                        a.copy(
                                                            messages =
                                                                a.messages.map {
                                                                    if (it.id == assistantId)
                                                                        it.copy(
                                                                            text =
                                                                                it.text +
                                                                                    event.getString(
                                                                                        "text"
                                                                                    )
                                                                        )
                                                                    else it
                                                                }
                                                        )
                                                    )
                                            }
                                            "input" -> {
                                                val questions =
                                                    readInputs(event.getJSONArray("requests"))
                                                val newQuestions =
                                                    questions.filter { question ->
                                                        a.messages.none {
                                                            it.id == "input-${question.id}"
                                                        }
                                                    }
                                                update(
                                                    a.copy(
                                                        pendingInputs = questions,
                                                        messages =
                                                            a.messages +
                                                                newQuestions.map {
                                                                    Message(
                                                                        id = "input-${it.id}",
                                                                        text = it.prompt,
                                                                        user = false,
                                                                    )
                                                                },
                                                    )
                                                )
                                                assistantId = null
                                            }
                                            "done" -> completed = true
                                            "message_end" -> assistantId = null
                                            "error" ->
                                                throw java.io.IOException(
                                                    event.optString(
                                                        "message",
                                                        "Unable to send message",
                                                    )
                                                )
                                        }
                                    }
                                }
                            }
                            if (!completed)
                                throw java.io.IOException("Stream ended before completion")
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    val reason =
                        if (e is java.net.ConnectException || e is java.net.SocketTimeoutException)
                            "Connection lost. Check that your backend is running and try again."
                        else "The response could not finish. You can try again."
                    val a = agent(agentId)
                    update(
                        a.copy(
                            messages =
                                a.messages.map {
                                    if (it.id == message.id)
                                        it.copy(failed = true, failureReason = reason)
                                    else it
                                }
                        )
                    )
                } finally {
                    busy = busy - agentId
                    calls.remove(agentId)
                    jobs.remove(agentId)
                }
            }
        return true
    }

    fun cancel(agentId: String) {
        jobs[agentId]?.cancel()
        calls[agentId]?.cancel()
        val sessionId = agent(agentId).sessionId
        if (sessionId.isNotBlank())
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    client
                        .newCall(
                            Request.Builder()
                                .url(backendUrl + "/cancel")
                                .post(
                                    JSONObject()
                                        .put("sessionId", sessionId)
                                        .toString()
                                        .toRequestBody("application/json".toMediaType())
                                )
                                .build()
                        )
                        .execute()
                        .close()
                }
            }
    }

    override fun onCleared() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        super.onCleared()
    }

    private fun persist() {
        val data =
            JSONArray(
                agents.map { a ->
                    JSONObject()
                        .put("id", a.id)
                        .put("name", a.name)
                        .put("shape", a.avatar.shape)
                        .put("face", a.avatar.face)
                        .put("color", a.avatar.color)
                        .put("instructions", a.instructions)
                        .put("memories", JSONArray(a.memories))
                        .put("session", a.sessionId)
                        .put("pinned", a.pinned)
                        .put("group", a.group)
                        .put("draft", a.draft)
                        .put("members", JSONArray(a.members))
                        .put(
                            "pendingInputs",
                            JSONArray(
                                a.pendingInputs.map { input ->
                                    JSONObject()
                                        .put("requestId", input.id)
                                        .put("prompt", input.prompt)
                                        .put("allowFreeform", input.freeform)
                                        .put(
                                            "options",
                                            JSONArray(
                                                input.choices.map {
                                                    JSONObject()
                                                        .put("id", it.id)
                                                        .put("label", it.label)
                                                }
                                            ),
                                        )
                                }
                            ),
                        )
                        .put(
                            "messages",
                            JSONArray(
                                a.messages.map { m ->
                                    JSONObject()
                                        .put("id", m.id)
                                        .put("text", m.text)
                                        .put("user", m.user)
                                        .put("failed", m.failed)
                                        .put("failureReason", m.failureReason)
                                        .put("createdAt", m.createdAt)
                                        .put(
                                            "attachments",
                                            JSONArray(
                                                m.attachments.map { f ->
                                                    JSONObject()
                                                        .put("name", f.name)
                                                        .put("mime", f.mime)
                                                        .put("path", f.path)
                                                }
                                            ),
                                        )
                                }
                            ),
                        )
                }
            )
        preferences.edit().putString("agents", data.toString()).apply()
    }

    private fun readInputs(array: JSONArray): List<InputPrompt> =
        (0 until array.length()).map { i ->
            val input = array.getJSONObject(i)
            val options = input.optJSONArray("options") ?: JSONArray()
            InputPrompt(
                input.getString("requestId"),
                input.getString("prompt"),
                input.optBoolean("allowFreeform", options.length() == 0),
                (0 until options.length()).map { index ->
                    val option = options.getJSONObject(index)
                    InputChoice(option.getString("id"), option.getString("label"))
                },
            )
        }

    private fun loadAgents(): List<Agent> {
        val saved =
            preferences.getString("agents", null)
                ?: return listOf(Agent(id = "luna", name = "Luna"))
        return runCatching {
                val array = JSONArray(saved)
                (0 until array.length())
                    .map { i ->
                        val a = array.getJSONObject(i)
                        val memories = a.optJSONArray("memories") ?: JSONArray()
                        val messages = a.optJSONArray("messages") ?: JSONArray()
                        Agent(
                            a.getString("id"),
                            a.getString("name"),
                            AvatarSpec(
                                a.getString("shape"),
                                a.getString("face"),
                                a.getLong("color"),
                            ),
                            a.optString("instructions"),
                            (0 until memories.length()).map { memories.getString(it) },
                            (0 until messages.length()).map {
                                val m = messages.getJSONObject(it)
                                Message(
                                    m.getString("id"),
                                    m.getString("text"),
                                    m.getBoolean("user"),
                                    m.optBoolean("failed"),
                                    m.optJSONArray("attachments")?.let { files ->
                                        (0 until files.length()).map { index ->
                                            val f = files.getJSONObject(index)
                                            Attachment(
                                                f.getString("name"),
                                                f.getString("mime"),
                                                f.getString("path"),
                                            )
                                        }
                                    } ?: emptyList(),
                                    m.optString("failureReason"),
                                    m.optLong("createdAt", 0),
                                )
                            },
                            a.optString("session"),
                            a.optBoolean("pinned"),
                            a.optBoolean("group"),
                            a.optString("draft"),
                            a.optJSONArray("members")?.let { ids ->
                                (0 until ids.length()).map { ids.getString(it) }
                            } ?: emptyList(),
                            readInputs(a.optJSONArray("pendingInputs") ?: JSONArray()),
                        )
                    }
                    .ifEmpty { listOf(Agent(id = "luna", name = "Luna")) }
            }
            .getOrElse {
                error = "Saved conversations could not be read."
                listOf(Agent(id = "luna", name = "Luna"))
            }
    }
}
