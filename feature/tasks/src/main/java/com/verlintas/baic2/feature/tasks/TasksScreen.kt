package com.verlintas.baic2.feature.tasks

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.RunState
import com.verlintas.baic2.core.model.RunSummary
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Spacing
import com.verlintas.baic2.designsystem.component.Baic2EmptyState
import com.verlintas.baic2.designsystem.component.Baic2ModeChip

@Composable
fun TasksScreen(
    onOpenConversation: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Baic2Spacing.lg,
                end = Baic2Spacing.lg,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Baic2Spacing.sm),
        ) {
            item(key = "header") {
                Column {
                    Text(
                        text = stringResource(R.string.feature_tasks_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.tasks_subtitle, runs.size),
                        style = Baic2Mono.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (runs.isEmpty()) {
                item(key = "empty") {
                    Baic2EmptyState(
                        title = stringResource(R.string.tasks_empty_title),
                        description = stringResource(R.string.tasks_empty_description),
                        icon = Icons.Outlined.List,
                    )
                }
            } else {
                items(runs, key = { it.id }) { run ->
                    RunRow(
                        run = run,
                        onClick = { onOpenConversation(run.conversationId) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunRow(
    run: RunSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val stateColor = stateColor(run.state)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Baic2Spacing.lg, vertical = Baic2Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = run.conversationTitle.ifBlank { stringResource(R.string.tasks_untitled) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (run.mode != AppMode.CHAT) {
                Spacer(Modifier.width(Baic2Spacing.sm))
                Baic2ModeChip(mode = run.mode)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(stateColor),
            )
            Spacer(Modifier.width(Baic2Spacing.sm))
            Text(
                text = stateLabel(run.state),
                style = Baic2Mono.label,
                color = stateColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = durationLabel(run),
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(Baic2Spacing.sm))
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    run.startedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString(),
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun stateColor(state: RunState): Color = when (state) {
    RunState.RUNNING -> MaterialTheme.colorScheme.primary
    RunState.COMPLETED -> MaterialTheme.colorScheme.tertiary
    RunState.FAILED -> MaterialTheme.colorScheme.error
    RunState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun stateLabel(state: RunState): String = stringResource(
    when (state) {
        RunState.RUNNING -> R.string.tasks_state_running
        RunState.COMPLETED -> R.string.tasks_state_completed
        RunState.FAILED -> R.string.tasks_state_failed
        RunState.CANCELLED -> R.string.tasks_state_cancelled
    },
)

private fun durationLabel(run: RunSummary): String {
    val seconds = ((run.updatedAt - run.startedAt).coerceAtLeast(0) / 1000).toInt()
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
