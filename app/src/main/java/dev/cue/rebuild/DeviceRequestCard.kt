package dev.cue.rebuild

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun DeviceRequestCard(state: CueState, agent: Agent, input: InputPrompt) {
    val actionInput =
        remember(input.id) {
            runCatching { JSONObject(input.deviceAction!!) }.getOrDefault(JSONObject())
        }
    val action = deviceActions.firstOrNull { it.id == actionInput.optString("operation") }
    val access = state.deviceAccess
    val scope = rememberCoroutineScope()
    var attempted by rememberSaveable(input.id) { mutableStateOf(false) }
    var executing by remember(input.id) { mutableStateOf(false) }
    val busy = agent.id in state.busy || executing
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = LocalNavigationActive.current && lifecycleState == Lifecycle.State.RESUMED
    fun respond(approved: Boolean, denied: Boolean = false) {
        if (busy || !active) return
        attempted = true
        executing = true
        scope.launch {
            try {
                val canReuse =
                    !denied &&
                        action != null &&
                        access.connected(action.integrationId) &&
                        access.policy(action) != AccessPolicy.Deny &&
                        access.permitted(action)
                val saved = if (canReuse) access.savedResult(input.id) else null
                val result =
                    saved
                        ?: (if (denied) DeviceAccess.failure("The user denied this request.")
                            else access.execute(actionInput, approved))
                            .let { value ->
                                val records = value.optJSONArray("records")
                                while (
                                    value.toString().length > 40000 &&
                                        records != null &&
                                        records.length() > 0
                                ) records.remove(records.length() - 1)
                                value.toString()
                            }
                            .also { access.saveResult(input.id, it) }
                state.send(agent.id, result, deviceResult = true)
            } finally {
                executing = false
            }
        }
    }
    LaunchedEffect(input.id, busy, active) {
        if (
            !attempted &&
                !busy &&
                active &&
                (action == null ||
                    !access.connected(action.integrationId) ||
                    access.policy(action) != AccessPolicy.Ask ||
                    !access.permitted(action))
        )
            respond(false)
    }
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(action?.title ?: "Device request", style = Cue.Label)
        val detail =
            listOf("query", "phone", "name", "title", "text")
                .mapNotNull { key -> actionInput.optString(key).takeIf { it.isNotBlank() } }
                .joinToString("\n")
        if (detail.isNotBlank()) Text(detail.take(600), style = Cue.Caption, color = Cue.Muted)
        if (!attempted && action != null && access.policy(action) == AccessPolicy.Ask) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    "Allow once",
                    Modifier.weight(1f),
                    enabled = !busy,
                    onClick = { respond(true) },
                )
                PillButton(
                    "Deny",
                    Modifier.weight(1f),
                    enabled = !busy,
                    primary = false,
                    onClick = { respond(false, true) },
                )
            }
        } else if (attempted && !busy)
            PillButton("Retry device result", Modifier.fillMaxWidth(), onClick = { respond(false) })
        else Text("Waiting for your phone…", style = Cue.Caption, color = Cue.Muted)
    }
}
