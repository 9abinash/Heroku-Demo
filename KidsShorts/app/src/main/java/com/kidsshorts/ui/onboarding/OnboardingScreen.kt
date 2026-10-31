package com.kidsshorts.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kidsshorts.ui.PinField
import com.kidsshorts.ui.viewmodel.AuthViewModel
import com.kidsshorts.ui.viewmodel.CreatePinResult
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(auth: AuthViewModel, onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var recoveryCode by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            0 -> WelcomeStep { step = 1 }
            1 -> CreatePinStep(auth) { code -> recoveryCode = code; step = 2 }
            else -> RecoveryStep(recoveryCode, onFinished)
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Text("Welcome to KidsShorts", style = MaterialTheme.typography.headlineMedium)
    Text(
        "KidsShorts plays short videos only from YouTube channels you approve, and only in the " +
            "languages you choose. If nothing matches, your child sees an empty screen instead of " +
            "anything else.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text("What to know first", style = MaterialTheme.typography.titleMedium)
    Text(
        "• Language information from YouTube can be missing or wrong. Videos with unknown or " +
            "conflicting language are blocked, and you can review and confirm them one by one.\n" +
            "• This app raises the bar for a child but is not tamper-proof. It works best alongside " +
            "Android's own parental controls (for example Google Family Link).\n" +
            "• You need your own free YouTube Data API key. It is added later under Data & privacy.\n" +
            "• No accounts, ads, analytics or tracking are used by this app.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Create parent PIN") }
}

@Composable
private fun CreatePinStep(auth: AuthViewModel, onCreated: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text("Create a parent PIN", style = MaterialTheme.typography.headlineSmall)
    Text("4 to 8 digits. You'll need it to open the Parent area. Pick something your child can't guess.")
    PinField(pin, { pin = it }, "PIN", Modifier.fillMaxWidth())
    PinField(confirm, { confirm = it }, "Confirm PIN", Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            scope.launch {
                busy = true
                when (val r = auth.createPin(pin, confirm)) {
                    is CreatePinResult.Created -> onCreated(r.recoveryCode)
                    is CreatePinResult.Invalid -> error = r.message
                }
                busy = false
            }
        },
    ) { Text(if (busy) "Saving…" else "Save PIN") }
}

@Composable
private fun RecoveryStep(code: String, onFinished: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    Text("Save your recovery code", style = MaterialTheme.typography.headlineSmall)
    Text(
        "If you ever forget the PIN, this code lets you set a new one. It is shown only once and " +
            "cannot be recovered, and the PIN itself can never be shown. Write it down and keep it " +
            "somewhere your child can't find it.",
    )
    Text(code, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = saved, onCheckedChange = { saved = it })
        Text("I've saved this code")
    }
    Button(enabled = saved, onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Continue to Parent area") }
}
