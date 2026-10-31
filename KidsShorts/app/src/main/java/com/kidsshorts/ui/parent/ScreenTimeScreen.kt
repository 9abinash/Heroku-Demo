package com.kidsshorts.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidsshorts.domain.QuietHours
import com.kidsshorts.domain.ScreenTimeSettings
import com.kidsshorts.ui.ParentScaffold
import com.kidsshorts.ui.formatMinuteOfDay
import com.kidsshorts.ui.viewmodel.ParentViewModel

@Composable
fun ScreenTimeScreen(vm: ParentViewModel, onBack: () -> Unit) {
    val settings by vm.screenTime.collectAsStateWithLifecycle()
    val usedSeconds by vm.usedTodaySeconds.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf("") }

    ParentScaffold("Screen time", onBack, vm) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Watched today: ${usedSeconds / 60} min ${usedSeconds % 60} s", style = MaterialTheme.typography.titleMedium)

            Text("Daily limit", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenTimeSettings.PRESET_LIMITS.forEach { minutes ->
                    FilterChip(
                        selected = settings.dailyLimitMinutes == minutes,
                        onClick = { vm.setDailyLimit(minutes) },
                        label = { Text("$minutes min") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) custom = it },
                    label = { Text("Custom (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    enabled = custom.toIntOrNull()?.let { it > 0 } == true,
                    onClick = { custom.toIntOrNull()?.let { vm.setDailyLimit(it) }; custom = "" },
                ) { Text("Set") }
            }
            Text("Current limit: ${settings.dailyLimitMinutes} min per day", style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quiet hours (bedtime)", style = MaterialTheme.typography.titleSmall)
                    Text("No videos during this window.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = settings.quietHours.enabled,
                    onCheckedChange = { vm.setQuietHours(settings.quietHours.copy(enabled = it)) },
                )
            }
            if (settings.quietHours.enabled) {
                TimeStepper("Starts", settings.quietHours.startMinuteOfDay) {
                    vm.setQuietHours(settings.quietHours.copy(startMinuteOfDay = it))
                }
                TimeStepper("Ends", settings.quietHours.endMinuteOfDay) {
                    vm.setQuietHours(settings.quietHours.copy(endMinuteOfDay = it))
                }
                if (settings.quietHours.startMinuteOfDay == settings.quietHours.endMinuteOfDay) {
                    Text("Start and end are the same, so quiet hours are not active.", color = MaterialTheme.colorScheme.error)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Parent override (today only)", style = MaterialTheme.typography.titleSmall)
                    Text("You're already signed in with your PIN. Every override is saved in the activity log.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 30).forEach { m ->
                            OutlinedButton(onClick = { vm.grantExtraTime(m) }) { Text("+$m min") }
                        }
                    }
                    if (settings.quietHours.enabled) {
                        Button(onClick = { vm.grantExtraTime(0, quietBypassMinutes = 30) }) { Text("Allow during quiet hours for 30 min") }
                    }
                }
            }

            Text(
                "Usage is stored on this device and is measured while videos are on screen, so restarting the app does not reset it. " +
                    "Changing the phone's date or clearing app data can still bypass it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TimeStepper(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    fun wrap(m: Int) = ((m % 1440) + 1440) % 1440
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(wrap(minuteOfDay - 15)) }) { Text("−15") }
        Text(formatMinuteOfDay(minuteOfDay), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange(wrap(minuteOfDay + 15)) }) { Text("+15") }
    }
}
