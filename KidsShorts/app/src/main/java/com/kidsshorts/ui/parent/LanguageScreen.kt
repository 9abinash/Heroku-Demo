package com.kidsshorts.ui.parent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidsshorts.domain.LanguageCatalog
import com.kidsshorts.domain.LanguageOption
import com.kidsshorts.domain.LanguagePolicy
import com.kidsshorts.ui.ParentScaffold
import com.kidsshorts.ui.viewmodel.ParentViewModel

@Composable
fun LanguageScreen(vm: ParentViewModel, onBack: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val policy = snap?.policy ?: LanguagePolicy.STRICT_DEFAULT
    var showMore by remember { mutableStateOf(false) }

    fun toggle(code: String) {
        val allowed = policy.allowedLanguageCodes
        vm.setAllowedLanguages(if (code in allowed) allowed - code else allowed + code)
    }

    ParentScaffold("Allowed languages", onBack, vm) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Strict language control. A video is shown only if its language is verified by " +
                            "YouTube's audio-language metadata or confirmed by you for that exact video, AND that " +
                            "language is ticked below. There is no automatic fallback, translation or \"similar " +
                            "language\" matching. Language data from YouTube can be missing or wrong, so " +
                            "reviewing individual videos is your strongest control.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Block videos with unknown language", style = MaterialTheme.typography.titleSmall)
                        Text("Recommended. Videos with conflicting language signals stay blocked either way.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = policy.blockUnknownLanguage, onCheckedChange = { vm.setBlockUnknown(it) })
                }
            }
            if (!policy.blockUnknownLanguage) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "Unknown-language videos can now reach your child (only from approved channels, and only " +
                                "when at least one language is ticked). Their language could be anything.",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            item {
                Text(
                    "Preferred language only changes the ORDER of videos that are already allowed. It never adds videos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(LanguageCatalog.india, key = { it.code }) { option ->
                LanguageRow(option, policy, { toggle(option.code) }, vm)
            }
            item {
                TextButton(onClick = { showMore = !showMore }) {
                    Text(if (showMore) "Hide other languages" else "Add other languages")
                }
            }
            if (showMore) {
                items(LanguageCatalog.additional, key = { it.code }) { option ->
                    LanguageRow(option, policy, { toggle(option.code) }, vm)
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(option: LanguageOption, policy: LanguagePolicy, onToggle: () -> Unit, vm: ParentViewModel) {
    val allowed = option.code in policy.allowedLanguageCodes
    val preferred = policy.preferredLanguageCode == option.code
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = allowed, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text("${option.englishName} (${option.code})", style = MaterialTheme.typography.bodyLarge)
            Text(option.nativeName, style = MaterialTheme.typography.bodySmall)
        }
        if (allowed) {
            RadioButton(selected = preferred, onClick = { vm.setPreferredLanguage(if (preferred) null else option.code) })
            Text("Preferred", style = MaterialTheme.typography.labelSmall)
        }
    }
}
