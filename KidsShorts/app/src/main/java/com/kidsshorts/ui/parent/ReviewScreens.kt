package com.kidsshorts.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kidsshorts.data.repo.VideoEvaluation
import com.kidsshorts.data.repo.needsReview
import com.kidsshorts.domain.EligibilityResult
import com.kidsshorts.domain.LanguageCatalog
import com.kidsshorts.domain.LanguageNormalizer
import com.kidsshorts.domain.LanguageResolution
import com.kidsshorts.domain.ParentDecision
import com.kidsshorts.domain.VideoDecision
import com.kidsshorts.ui.LanguageStatusPill
import com.kidsshorts.ui.ParentScaffold
import com.kidsshorts.ui.formatDuration
import com.kidsshorts.ui.parentText
import com.kidsshorts.ui.viewmodel.ParentViewModel

private fun VideoEvaluation.language(): LanguageResolution = when (val r = result) {
    is EligibilityResult.Eligible -> r.language
    is EligibilityResult.Excluded -> r.language
}

@Composable
fun ReviewScreen(vm: ParentViewModel, onBack: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var approving by remember { mutableStateOf<VideoEvaluation?>(null) }

    val evals = snap?.evaluations.orEmpty()
    val titles = snap?.channels.orEmpty().associate { it.channelId to it.title }
    val decisions = snap?.decisions.orEmpty()
    val allowed = snap?.policy?.allowedLanguageCodes.orEmpty()

    val needsReview = evals.filter { it.needsReview() }
    val showing = evals.filter { it.result is EligibilityResult.Eligible }
    val hidden = evals.filter { it.result is EligibilityResult.Excluded && !it.needsReview() }
    val tabs = listOf("Needs review (${needsReview.size})", "Showing (${showing.size})", "Hidden (${hidden.size})")
    val list = when (tab) { 0 -> needsReview; 1 -> showing; else -> hidden }

    ParentScaffold("Video review", onBack, vm) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
                }
            }
            if (allowed.isEmpty()) {
                Text(
                    "No languages are allowed yet, so every video is hidden. Choose languages first.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error,
                )
            }
            if (list.isEmpty()) {
                Text(
                    when (tab) {
                        0 -> "Nothing needs review."
                        1 -> "No videos are showing to your child right now."
                        else -> "No hidden videos."
                    },
                    Modifier.padding(16.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.video.videoId }) { ev ->
                    VideoCard(
                        ev = ev,
                        channelTitle = titles[ev.video.channelId].orEmpty(),
                        decision = decisions[ev.video.videoId],
                        onApprove = { approving = ev },
                        onBlock = { vm.blockVideo(ev.video.videoId, ev.video.title) },
                        onUndo = { vm.undoDecision(ev.video.videoId, ev.video.title) },
                    )
                }
            }
        }
    }

    approving?.let { ev ->
        ApproveDialog(
            ev = ev,
            allowed = allowed,
            onDismiss = { approving = null },
            onConfirm = { code ->
                vm.approveVideo(ev.video.videoId, ev.video.title, code)
                approving = null
            },
        )
    }
}

@Composable
private fun VideoCard(
    ev: VideoEvaluation,
    channelTitle: String,
    decision: VideoDecision?,
    onApprove: () -> Unit,
    onBlock: () -> Unit,
    onUndo: () -> Unit,
) {
    val language = ev.language()
    val reasons = (ev.result as? EligibilityResult.Excluded)?.reasons.orEmpty()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                AsyncImage(
                    model = ev.video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(ev.video.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        "$channelTitle · ${formatDuration(ev.video.durationSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageStatusPill(language.status, language.languageCode)
                val raw = listOfNotNull(
                    ev.video.audioLanguage?.let { "audio=$it" },
                    ev.video.metadataLanguage?.let { "text=$it" },
                ).joinToString(" ")
                if (raw.isNotEmpty()) Text(raw, style = MaterialTheme.typography.labelSmall)
            }
            if (reasons.isNotEmpty()) {
                Text("Why hidden: " + reasons.joinToString("; ") { it.parentText() }, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                if (ev.result !is EligibilityResult.Eligible || decision?.decision == ParentDecision.BLOCKED) {
                    TextButton(onClick = onApprove) { Text("Approve…") }
                }
                if (decision?.decision != ParentDecision.BLOCKED) {
                    TextButton(onClick = onBlock) { Text("Block") }
                }
                if (decision != null) {
                    TextButton(onClick = onUndo) { Text("Undo my decision") }
                }
            }
        }
    }
}

/** Approving one video REQUIRES the parent to name its language; nothing is inferred. */
@Composable
private fun ApproveDialog(
    ev: VideoEvaluation,
    allowed: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val suggestion = LanguageNormalizer.normalize(ev.video.audioLanguage)
    var selected by remember { mutableStateOf(suggestion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What language is this video in?") },
        text = {
            Column {
                Text(
                    "This approves only this one video, in the language you pick. Your choice outranks YouTube's " +
                        "metadata, so please check it by watching.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(Modifier.height(280.dp)) {
                    items(LanguageCatalog.all, key = { it.code }) { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected == option.code, onClick = { selected = option.code })
                            Text(
                                "${option.englishName} (${option.code})" + if (option.code in allowed) "" else " – not allowed",
                            )
                        }
                    }
                }
                val choice = selected
                if (choice != null && choice !in allowed) {
                    Text(
                        "That language isn't currently allowed, so the video will stay hidden until you allow it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let(onConfirm) }) { Text("Approve this video") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BlockedScreen(vm: ParentViewModel, onBack: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val decisions = snap?.decisions.orEmpty()
    val byId = snap?.evaluations.orEmpty().associateBy { it.video.videoId }
    val blocked = decisions.values.filter { it.decision == ParentDecision.BLOCKED }.sortedByDescending { it.updatedAtEpochMs }

    ParentScaffold("Blocked videos", onBack, vm) { padding ->
        if (blocked.isEmpty()) {
            Text("You haven't blocked any videos.", Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(blocked, key = { it.videoId }) { d ->
                    val video = byId[d.videoId]?.video
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(video?.title?.ifBlank { null } ?: d.videoId, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                if (video != null) Text(formatDuration(video.durationSeconds), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.undoDecision(d.videoId, video?.title ?: d.videoId) }) { Text("Undo") }
                        }
                    }
                }
            }
        }
    }
}
