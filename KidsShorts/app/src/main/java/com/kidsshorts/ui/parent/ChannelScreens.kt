package com.kidsshorts.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.kidsshorts.ui.ParentScaffold
import com.kidsshorts.ui.viewmodel.ChannelRow
import com.kidsshorts.ui.viewmodel.LookupUi
import com.kidsshorts.ui.viewmodel.ParentViewModel

@Composable
fun ChannelsScreen(vm: ParentViewModel, onBack: () -> Unit, onAdd: () -> Unit) {
    val rows by vm.channelRows.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<ChannelRow?>(null) }

    ParentScaffold(
        title = "Approved channels",
        onBack = onBack,
        vm = vm,
        actions = {
            TextButton(onClick = { vm.refreshAll() }, enabled = !busy) { Text(if (busy) "Updating…" else "Refresh") }
            TextButton(onClick = onAdd) { Text("Add") }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No channels yet.", style = MaterialTheme.typography.titleMedium)
                Text("Your child can only watch channels you approve. Tap Add to approve your first channel.")
                Button(onClick = onAdd) { Text("Add a channel") }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it.channel.channelId }) { row ->
                    ChannelCard(
                        row = row,
                        onToggle = { vm.setChannelEnabled(row.channel.channelId, it) },
                        onRefresh = { vm.refreshChannel(row.channel.channelId) },
                        onRemove = { removing = row },
                    )
                }
            }
        }
    }

    removing?.let { row ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${row.channel.title}?") },
            text = { Text("Its videos disappear from your child's feed immediately, and your decisions about its individual videos are deleted.") },
            confirmButton = {
                TextButton(onClick = { vm.removeChannel(row.channel.channelId); removing = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChannelCard(row: ChannelRow, onToggle: (Boolean) -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelThumbnail(row.channel.thumbnailUrl)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(row.channel.title, style = MaterialTheme.typography.titleMedium)
                    Text(row.channel.channelId, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = row.channel.isEnabled, onCheckedChange = onToggle)
                    Text(if (row.channel.isEnabled) "Active" else "Paused", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("${row.eligibleCount} eligible now · ${row.needsReviewCount} need review · ${row.totalCount} checked")
            Row {
                TextButton(onClick = onRefresh) { Text("Refresh videos") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
fun ChannelThumbnail(url: String?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(48.dp).clip(CircleShape),
    )
}

@Composable
fun AddChannelScreen(vm: ParentViewModel, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val lookup by vm.lookup.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { vm.clearLookup() } }

    ParentScaffold("Add a channel", onBack, vm) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Paste a YouTube channel link, an @handle, or a channel ID (starts with UC). Links to single videos can't be used to approve a channel.")
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Channel link, @handle or ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.lookupChannel(input) },
                enabled = input.isNotBlank() && lookup !is LookupUi.Loading,
            ) { Text("Look up channel") }

            when (val l = lookup) {
                LookupUi.Idle -> Unit
                LookupUi.Loading -> CircularProgressIndicator()
                is LookupUi.Error -> Text(l.message, color = MaterialTheme.colorScheme.error)
                is LookupUi.Found -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelThumbnail(l.info.thumbnailUrl)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(l.info.title, style = MaterialTheme.typography.titleMedium)
                                Text(l.info.channelId, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (l.alreadyApproved) {
                            Text("This channel is already approved.")
                        } else {
                            Text("Approving lets your child watch matching Shorts from this channel. Languages are controlled separately, and every video still has to pass them.")
                            Button(
                                enabled = !busy,
                                onClick = { vm.approveChannel(l.info) { onBack() } },
                            ) { Text(if (busy) "Approving…" else "Approve this channel") }
                        }
                    }
                }
            }
        }
    }
}
