@file:OptIn(ExperimentalMaterial3Api::class)

package com.kidsshorts.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidsshorts.domain.ExclusionReason
import com.kidsshorts.domain.LanguageStatus
import com.kidsshorts.ui.viewmodel.ParentViewModel

/** Standard parent-area screen frame with a back arrow and a snackbar for transient messages. */
@Composable
fun ParentScaffold(
    title: String,
    onBack: (() -> Unit)?,
    vm: ParentViewModel? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    if (vm != null) {
        val message by vm.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                vm.consumeMessage()
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
fun PinField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier,
    )
}

@Composable
fun Pill(text: String, container: Color, content: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = container, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (content == Color.Unspecified) MaterialTheme.colorScheme.onSurface else content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun LanguageStatusPill(status: LanguageStatus, code: String?) {
    val (text, color) = when (status) {
        LanguageStatus.VERIFIED_BY_METADATA -> "Verified by metadata" to MaterialTheme.colorScheme.secondaryContainer
        LanguageStatus.PARENT_CONFIRMED -> "Parent-confirmed" to MaterialTheme.colorScheme.primaryContainer
        LanguageStatus.UNKNOWN -> "Unknown" to MaterialTheme.colorScheme.surfaceVariant
        LanguageStatus.CONFLICTING -> "Conflicting" to MaterialTheme.colorScheme.errorContainer
    }
    Pill(if (code != null) "$text · $code" else text, color)
}

fun ExclusionReason.parentText(): String = when (this) {
    ExclusionReason.CHANNEL_NOT_APPROVED -> "Channel not approved"
    ExclusionReason.CHANNEL_DISABLED -> "Channel is paused"
    ExclusionReason.VIDEO_UNAVAILABLE -> "Video unavailable or not public"
    ExclusionReason.VIDEO_NOT_EMBEDDABLE -> "Creator doesn't allow embedding"
    ExclusionReason.STATUS_NOT_VERIFIED -> "Waiting for YouTube status check"
    ExclusionReason.NOT_SHORTS_FORMAT -> "Longer than a Short"
    ExclusionReason.VIDEO_BLOCKED_BY_PARENT -> "Blocked by you"
    ExclusionReason.LANGUAGE_NOT_ALLOWED -> "Language not allowed"
    ExclusionReason.LANGUAGE_UNKNOWN -> "Language unknown"
    ExclusionReason.LANGUAGE_CONFLICT -> "Language signals conflict"
}

fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun formatMinuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
