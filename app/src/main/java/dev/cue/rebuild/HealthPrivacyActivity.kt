package dev.cue.rebuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Rationale opened by Android's Health Connect permission dialog. */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CueTheme {
                Column(
                    Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text("Health data in Cue", style = Cue.Title)
                    Text(
                        "Cue reads step records only when you request a health action in an open chat and your integration policy allows it. It does not collect health data in the background or write health records.",
                        style = Cue.Body,
                    )
                    Text(
                        "Requested records are sent to your personal backend and its AI provider to answer your request. They may be retained in the backend conversation history. A temporary result on this phone is kept only until delivery or the next device result.",
                        style = Cue.Body,
                    )
                    Text(
                        "Use Ask to approve each request, or Deny or Disconnect to stop future access. You can also revoke the Steps permission in Android Health Connect. Disconnecting does not erase previously shared conversation data.",
                        style = Cue.Body,
                    )
                    PillButton("Done", onClick = { finish() })
                }
            }
        }
    }
}
