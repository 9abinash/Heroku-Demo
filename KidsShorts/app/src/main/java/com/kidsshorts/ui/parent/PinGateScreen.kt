package com.kidsshorts.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kidsshorts.security.PinManager.VerifyResult
import com.kidsshorts.ui.PinField
import com.kidsshorts.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PinGateScreen(
    auth: AuthViewModel,
    onUnlocked: () -> Unit,
    onBackToVideos: () -> Unit,
    onCloseApp: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedMs by remember { mutableLongStateOf(auth.lockoutRemainingMs()) }
    var showRecovery by remember { mutableStateOf(false) }
    var newRecoveryCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lockedMs > 0) {
        while (lockedMs > 0) {
            delay(1_000)
            lockedMs = auth.lockoutRemainingMs()
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Parent area", style = MaterialTheme.typography.headlineMedium)
        Text("Enter your PIN to continue.")
        PinField(pin, { pin = it; error = null }, "PIN", Modifier.fillMaxWidth())

        when {
            lockedMs > 0 -> Text("Too many attempts. Try again in ${lockedMs / 1000 + 1}s.", color = MaterialTheme.colorScheme.error)
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        Button(
            enabled = pin.length >= 4 && lockedMs <= 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    when (val r = auth.unlock(pin)) {
                        VerifyResult.Success -> onUnlocked()
                        is VerifyResult.Wrong -> { error = "That PIN isn't right."; pin = "" }
                        is VerifyResult.Locked -> { lockedMs = r.remainingMs; pin = "" }
                        VerifyResult.NotSet -> error = "No PIN has been set up."
                    }
                }
            },
        ) { Text("Unlock") }

        TextButton(onClick = { showRecovery = true }) { Text("Forgot PIN?") }
        OutlinedButton(onClick = onBackToVideos, modifier = Modifier.fillMaxWidth()) { Text("Back to videos") }
        OutlinedButton(onClick = onCloseApp, modifier = Modifier.fillMaxWidth()) { Text("Close app") }
    }

    if (showRecovery) {
        RecoveryDialog(
            auth = auth,
            onDismiss = { showRecovery = false },
            onRecovered = { code -> showRecovery = false; newRecoveryCode = code },
        )
    }

    newRecoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("New recovery code") },
            text = { Text("Your PIN was reset. Save this new code; the old one no longer works:\n\n$code") },
            confirmButton = { TextButton(onClick = { newRecoveryCode = null; onUnlocked() }) { Text("I've saved it") } },
        )
    }
}

@Composable
private fun RecoveryDialog(auth: AuthViewModel, onDismiss: () -> Unit, onRecovered: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the recovery code you saved when you created the PIN, then choose a new PIN. If you've lost both, clear the app's data in Android settings (this deletes all of KidsShorts' settings).")
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Recovery code") }, singleLine = true)
                PinField(pin, { pin = it }, "New PIN")
                PinField(confirm, { confirm = it }, "Confirm new PIN")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val (newCode, message) = auth.recover(code, pin, confirm)
                    if (newCode != null) onRecovered(newCode) else error = message
                }
            }) { Text("Reset PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
