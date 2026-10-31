package com.kidsshorts.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidsshorts.data.repo.needsReview
import com.kidsshorts.domain.EligibilityResult
import com.kidsshorts.domain.LanguageCatalog
import com.kidsshorts.domain.ParentDecision
import com.kidsshorts.ui.ParentScaffold
import com.kidsshorts.ui.nav.Routes
import com.kidsshorts.ui.viewmodel.ParentViewModel

@Composable
fun ParentDashboardScreen(vm: ParentViewModel, onNavigate: (String) -> Unit, onExit: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val rows by vm.channelRows.collectAsStateWithLifecycle()
    val screenTime by vm.screenTime.collectAsStateWithLifecycle()
    val usedSeconds by vm.usedTodaySeconds.collectAsStateWithLifecycle()
    val hasKey = remember { vm.hasApiKey() }

    BackHandler(onBack = onExit)

    val evals = snap?.evaluations.orEmpty()
    val readyCount = evals.count { it.result is EligibilityResult.Eligible }
    val reviewCount = evals.count { it.needsReview() }
    val blockedCount = snap?.decisions?.values?.count { it.decision == ParentDecision.BLOCKED } ?: 0
    val allowed = snap?.policy?.allowedLanguageCodes.orEmpty()

    ParentScaffold("Parent area", onBack = null, vm = vm) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasKey) {
                Banner("Add a YouTube API key to load videos.") { onNavigate(Routes.DATA) }
            }
            if (snap != null && allowed.isEmpty()) {
                Banner("No languages are allowed yet, so your child will see no videos. Choose languages.") { onNavigate(Routes.LANGUAGES) }
            }

            DashboardCard(
                "Approved channels",
                "${rows.size} channel(s) · $readyCount video(s) ready for your child",
            ) { onNavigate(Routes.CHANNELS) }

            DashboardCard(
                "Allowed languages",
                if (allowed.isEmpty()) "None selected"
                else allowed.sorted().joinToString { LanguageCatalog.label(it) },
            ) { onNavigate(Routes.LANGUAGES) }

            DashboardCard(
                "Video review",
                if (reviewCount == 0) "Nothing waiting for review" else "$reviewCount video(s) need your review",
            ) { onNavigate(Routes.REVIEW) }

            DashboardCard("Blocked videos", "$blockedCount blocked by you") { onNavigate(Routes.BLOCKED) }

            DashboardCard(
                "Screen time",
                "${screenTime.dailyLimitMinutes} min/day · ${usedSeconds / 60} min watched today" +
                    if (screenTime.quietHours.enabled) " · quiet hours on" else "",
            ) { onNavigate(Routes.SCREEN_TIME) }

            DashboardCard("Data & privacy", "API key, activity log, PIN, delete all data") { onNavigate(Routes.DATA) }

            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Back to child mode") }
        }
    }
}

@Composable
private fun DashboardCard(title: String, summary: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Banner(text: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
