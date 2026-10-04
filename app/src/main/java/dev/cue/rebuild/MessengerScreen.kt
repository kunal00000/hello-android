package dev.cue.rebuild

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Messenger(val label: String, val asset: String, val purpose: String) {
    Telegram("Telegram", "messenger_telegram", "Chat with your agent from Telegram."),
    Line("LINE", "messenger_line", "Bring your agent into your LINE conversations."),
    Slack("Slack", "messenger_slack", "Work with your agent from your Slack workspace."),
}

@Composable
fun MessengerScreen(state: CueState) {
    var selected by remember { mutableStateOf<Messenger?>(null) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    fun asset(service: Messenger) =
        if (service == Messenger.Slack && dark) "messenger_slack_dark" else service.asset

    Column(Modifier.fillMaxSize()) {
        Header("Messenger", state::back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Group {
                Messenger.entries.forEachIndexed { index, service ->
                    SettingRow(
                        service.label,
                        divider = index < Messenger.entries.lastIndex,
                        onClick = { selected = service },
                        leading = { Asset(asset(service), Modifier.size(24.dp)) },
                        trailing = { Text("Set up", color = Cue.Blue, style = Cue.Label) },
                    )
                }
            }
            Text(
                "Use your agent in your favorite messenger. Connections are not available yet.",
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Cue.Muted,
                style = Cue.Caption,
            )
        }
    }
    selected?.let { service ->
        CueSheet(onDismissRequest = { selected = null }, height = 420.dp) { dismiss ->
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                RoundButton(Glyph.Close, "Close messenger setup", onClick = dismiss)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Asset(asset(service), Modifier.size(56.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("Connect ${service.label}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        service.purpose,
                        style = Cue.Body,
                        color = Cue.Muted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "${service.label} connections are not available yet.",
                        style = Cue.Caption,
                        color = Cue.Muted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                }
                PillButton("Done", Modifier.fillMaxWidth(), onClick = dismiss)
            }
        }
    }
}
