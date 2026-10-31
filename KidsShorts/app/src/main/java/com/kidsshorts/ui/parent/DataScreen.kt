package com.kidsshorts.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidsshorts.ui.PinField
import com.kidsshorts.ui.ParentScaffold
import com.kidsshorts.ui.viewmodel.AuthViewModel
import com.kidsshorts.ui.viewmodel.ParentViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

const val PRIVACY_NOTICE =
    "What this app stores: the channels you approve, video titles and language labels fetched from " +
        "YouTube, your per-video decisions, settings, how long videos were on screen each day, and an " +
        "activity log of parent actions. Your PIN is never stored, only a salted hash protected by the " +
        "Android Keystore.\n\n" +
        "What it does not collect: no name, age, location, contacts, microphone, camera, advertising ID, " +
        "accounts, analytics or tracking SDKs. The app has no backend; data stays on this device and is " +
        "excluded from Android backups.\n\n" +
        "Network: the app contacts the YouTube Data API (with your API key) and plays videos through " +
        "YouTube's official embedded player (privacy-enhanced youtube-nocookie.com host). YouTube's own " +
        "terms and privacy policy apply to that traffic. You can delete everything below at any time."

@Composable
fun DataScreen(vm: ParentViewModel, auth: AuthViewModel, onBack: () -> Unit, onReset: () -> Unit) {
    val audit by vm.audit.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var changePin by remember { mutableStateOf(false) }
    var keyVersion by remember { mutableStateOf(0) }
    val hasKey = remember(keyVersion) { vm.hasApiKey() }
    val uriHandler = LocalUriHandler.current

    ParentScaffold("Data & privacy", onBack, vm) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("YouTube API key", style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasKey) "A key is configured." else "No key yet. Without one, no videos can be loaded.",
                color = if (hasKey) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Text(
                "Create a free key in Google Cloud Console: enable \"YouTube Data API v3\", create an API key, and " +
                    "restrict it to this Android app and to the YouTube Data API. Every family should use its own key.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Paste API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.saveApiKey(key); key = ""; keyVersion++ }, enabled = key.isNotBlank()) { Text("Save key") }

            HorizontalDivider()
            Text("Privacy notice", style = MaterialTheme.typography.titleMedium)
            Text(PRIVACY_NOTICE, style = MaterialTheme.typography.bodySmall)
            Text(
                "This app uses YouTube API Services. By using it you also agree to YouTube's Terms of Service and are " +
                    "subject to Google's Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { uriHandler.openUri("https://www.youtube.com/t/terms") }) { Text("YouTube Terms") }
                OutlinedButton(onClick = { uriHandler.openUri("https://policies.google.com/privacy") }) { Text("Google Privacy Policy") }
            }

            HorizontalDivider()
            Text("Parent PIN", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { changePin = true }) { Text("Change PIN") }

            HorizontalDivider()
            Text("Activity log", style = MaterialTheme.typography.titleMedium)
            if (audit.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodySmall)
            val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
            audit.take(40).forEach { e ->
                Text("${fmt.format(Date(e.timestamp))}  ${e.type.replace('_', ' ').lowercase()}: ${e.detail}", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()
            Text("Delete all data", style = MaterialTheme.typography.titleMedium)
            Text("Removes channels, decisions, settings, usage history, the activity log, your PIN and the API key from this device.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmReset = true }) { Text("Delete everything…") }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete everything?") },
            text = { Text("This cannot be undone. The app will return to first-time setup.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetAllData(onReset) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    if (changePin) ChangePinDialog(auth) { changePin = false }
}

@Composable
private fun ChangePinDialog(auth: AuthViewModel, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(old, { old = it }, "Current PIN")
                PinField(new, { new = it }, "New PIN")
                PinField(confirm, { confirm = it }, "Confirm new PIN")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val problem = auth.changePin(old, new, confirm)
                    if (problem == null) onDismiss() else error = problem
                }
            }) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
