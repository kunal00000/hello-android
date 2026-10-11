package dev.cue.rebuild

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Only ciphertext and a fresh IV are stored in preferences. The AES key never
// leaves Android Keystore, and application backups are disabled in the manifest.
class BackendCredentialStore(context: Context, name: String = "cue") {
    private val preferences = context.getSharedPreferences("${name}_backend", Context.MODE_PRIVATE)
    private val alias = "${context.packageName}.$name.backend"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    fun read(): String? =
        runCatching {
                val saved = preferences.getString("credential", null) ?: return null
                val parts = saved.split(":")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
                )
                String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            }
            .getOrNull()

    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(
            preferences
                .edit()
                .putString(
                    "credential",
                    Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                        ":" +
                        Base64.encodeToString(encrypted, Base64.NO_WRAP),
                )
                .commit()
        ) {
            "Could not save connection"
        }
    }

    fun clear() {
        check(preferences.edit().clear().commit())
    }
}

@Composable
fun BackendConnectionScreen(state: CueState) {
    var token by remember { mutableStateOf("") }
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Column(Modifier.fillMaxSize()) {
        Header("Connect backend", state::back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Your private agent", style = Cue.Title)
            Text(
                "Enter your access token once to connect. It stays encrypted on this device.",
                style = Cue.Body,
                color = Cue.Muted,
            )
            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it.trim()
                    state.connectionError = null
                },
                label = { Text("Access token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.checkingBackend,
                modifier = Modifier.fillMaxWidth().testTag("backend-token"),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
            )
            state.connectionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = Cue.Caption)
            }
            PillButton(
                if (state.checkingBackend) "Connecting…" else "Connect",
                Modifier.fillMaxWidth(),
                enabled = !state.checkingBackend && token.isNotBlank(),
                onClick = {
                    state.connectBackend(token) {
                        token = ""
                        state.back()
                    }
                },
            )
            if (state.hasBackendCredential) {
                Text(
                    "A connection is saved. Enter a replacement token above, or disconnect below.",
                    style = Cue.Caption,
                    color = Cue.Muted,
                )
                TextButton(
                    onClick = {
                        state.disconnectBackend()
                        token = ""
                    },
                    enabled = !state.checkingBackend && state.busy.isEmpty(),
                ) {
                    Text("Disconnect backend")
                }
            }
        }
    }
}
