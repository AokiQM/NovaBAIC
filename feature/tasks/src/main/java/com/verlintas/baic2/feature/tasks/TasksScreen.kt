/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of BetterAIChat2.
 *
 * BetterAIChat2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * BetterAIChat2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * BetterAIChat2. If not, see <https://www.gnu.org/licenses/>.
 */

package com.verlintas.baic2.feature.tasks

import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.PlanStepStatus
import com.verlintas.baic2.core.model.Run
import com.verlintas.baic2.core.model.RunBudget
import com.verlintas.baic2.core.model.RunState
import com.verlintas.baic2.core.model.RunSummary
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Spacing
import com.verlintas.baic2.designsystem.component.Baic2EmptyState
import com.verlintas.baic2.designsystem.component.Baic2ModeChip
import kotlinx.coroutines.delay

private object TasksRoute {
    const val ROOT = "tasks_root"
    const val RUN = "tasks_run/{runId}"

    fun run(id: Long) = "tasks_run/$id"
}

@Composable
fun TasksScreen(
    onOpenConversation: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRetryConversation: (Long) -> Unit = {},
    onInnerRouteChanged: (Boolean) -> Unit = {},
    initialRunId: Long? = null,
    onInitialRunConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onInnerRoute = backStackEntry?.destination?.route != TasksRoute.ROOT

    LaunchedEffect(onInnerRoute) { onInnerRouteChanged(onInnerRoute) }
    LaunchedEffect(initialRunId) {
        val id = initialRunId ?: return@LaunchedEffect
        navController.navigate(TasksRoute.run(id))
        onInitialRunConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = TasksRoute.ROOT,
        enterTransition = {
            slideInHorizontally(animationSpec = spring(stiffness = 380f)) { it / 5 } +
                fadeIn(tween(220))
        },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = {
            slideInHorizontally(animationSpec = spring(stiffness = 380f)) { -it / 5 } +
                fadeIn(tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(tween(200)) { it / 5 } + fadeOut(tween(120))
        },
        modifier = modifier.fillMaxSize(),
    ) {
        composable(TasksRoute.ROOT) {
            TasksListPage(onOpenRun = { navController.navigate(TasksRoute.run(it)) })
        }
        composable(
            route = TasksRoute.RUN,
            arguments = listOf(navArgument("runId") { type = NavType.LongType }),
        ) {
            RunDetailPage(
                onBack = { navController.popBackStack() },
                onOpenConversation = onOpenConversation,
                onRetry = onRetryConversation,
            )
        }
    }
}

// ---------------------------------------------------------------- list

@Composable
private fun TasksListPage(
    onOpenRun: (Long) -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
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
                        text = stringResource(R.string.tasks_subtitle, state.totalCount),
                        style = Baic2Mono.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "filters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Baic2Spacing.sm),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = state.filter == TaskFilter.ALL,
                        onClick = { viewModel.setFilter(TaskFilter.ALL) },
                        shape = RoundedCornerShape(10.dp),
                        label = { Text(stringResource(R.string.tasks_filter_all)) },
                    )
                    FilterChip(
                        selected = state.filter == TaskFilter.RUNNING,
                        onClick = { viewModel.setFilter(TaskFilter.RUNNING) },
                        shape = RoundedCornerShape(10.dp),
                        label = {
                            Text(
                                if (state.runningCount > 0) {
                                    "${stringResource(R.string.tasks_filter_running)} · ${state.runningCount}"
                                } else {
                                    stringResource(R.string.tasks_filter_running)
                                },
                            )
                        },
                    )
                    FilterChip(
                        selected = state.filter == TaskFilter.COMPLETED,
                        onClick = { viewModel.setFilter(TaskFilter.COMPLETED) },
                        shape = RoundedCornerShape(10.dp),
                        label = { Text(stringResource(R.string.tasks_filter_completed)) },
                    )
                    FilterChip(
                        selected = state.filter == TaskFilter.FAILED,
                        onClick = { viewModel.setFilter(TaskFilter.FAILED) },
                        shape = RoundedCornerShape(10.dp),
                        label = { Text(stringResource(R.string.tasks_filter_failed)) },
                    )
                }
            }

            if (state.runs.isEmpty()) {
                item(key = "empty") {
                    Baic2EmptyState(
                        title = stringResource(R.string.tasks_empty_title),
                        description = stringResource(R.string.tasks_empty_description),
                        icon = Icons.Outlined.List,
                    )
                }
            } else {
                items(state.runs, key = { it.id }) { run ->
                    RunRow(
                        run = run,
                        onClick = { onOpenRun(run.id) },
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
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(16.dp)
                    .rotate(-90f),
            )
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
                text = durationLabel(run.updatedAt - run.startedAt),
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

// ---------------------------------------------------------------- detail

@Composable
private fun RunDetailPage(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    viewModel: RunDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.detail.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = Baic2Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.tasks_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.tasks_detail_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.tasks_delete_run),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val detail = state
        if (detail == null) {
            Spacer(Modifier.weight(1f))
        } else {
            RunDetailContent(
                detail = detail,
                onOpenConversation = { onOpenConversation(detail.run.conversationId) },
                onStop = { viewModel.stopRun(detail.run.conversationId) },
                onRetry = { onRetry(detail.run.conversationId) },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tasks_delete_run)) },
            text = { Text(stringResource(R.string.tasks_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteRun(onBack)
                    },
                ) {
                    Text(stringResource(R.string.tasks_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.tasks_cancel))
                }
            },
        )
    }
}

@Composable
private fun RunDetailContent(
    detail: RunDetailState,
    onOpenConversation: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
) {
    val run = detail.run
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.id, run.state) {
        while (run.state == RunState.RUNNING) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    var expandedMessage by rememberSaveable { mutableStateOf<Long?>(null) }
    val elapsed = ((if (run.state == RunState.RUNNING) now else run.updatedAt) - run.startedAt)
        .coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Baic2Spacing.lg,
            end = Baic2Spacing.lg,
            top = Baic2Spacing.sm,
            bottom = Baic2Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Baic2Spacing.sm),
    ) {
        item(key = "status") {
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(stateColor(run.state)),
                    )
                    Spacer(Modifier.width(Baic2Spacing.sm))
                    Text(
                        text = stateLabel(run.state),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = stateColor(run.state),
                        modifier = Modifier.weight(1f),
                    )
                    Baic2ModeChip(mode = run.mode)
                }
                Spacer(Modifier.height(Baic2Spacing.sm))
                Text(
                    text = detail.conversationTitle.ifBlank { stringResource(R.string.tasks_untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.tasks_meta,
                        durationLabel(elapsed),
                        DateUtils.getRelativeTimeSpanString(
                            run.startedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString(),
                    ),
                    style = Baic2Mono.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "budget") {
            val budget = RunBudget.forMode(run.mode)
            DetailCard {
                MonoLabel(stringResource(R.string.tasks_budget_title))
                Spacer(Modifier.height(Baic2Spacing.sm))
                BudgetLine(
                    label = stringResource(R.string.tasks_budget_rounds),
                    used = run.roundsUsed.toLong(),
                    max = budget.maxRounds.toLong(),
                )
                BudgetLine(
                    label = stringResource(R.string.tasks_budget_tools),
                    used = run.toolCallsUsed.toLong(),
                    max = budget.maxToolCalls.toLong(),
                )
                BudgetLine(
                    label = stringResource(R.string.tasks_budget_wallclock),
                    usedLabel = durationLabel(elapsed),
                    maxLabel = durationLabel(budget.maxWallClockMs),
                    fraction = elapsed.toFloat() / budget.maxWallClockMs,
                )
                if (run.state == RunState.RUNNING) {
                    Spacer(Modifier.height(Baic2Spacing.xs))
                    Text(
                        text = stringResource(R.string.tasks_budget_elapsed, durationLabel(elapsed)),
                        style = Baic2Mono.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (detail.impact.isNotEmpty()) {
            item(key = "impact") {
                DetailCard {
                    MonoLabel(stringResource(R.string.tasks_impact_title))
                    Spacer(Modifier.height(Baic2Spacing.sm))
                    detail.impact.forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        detail.plan?.takeIf { it.steps.isNotEmpty() }?.let { plan ->
            item(key = "plan") {
                DetailCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonoLabel(stringResource(R.string.tasks_plan_title))
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${plan.doneCount}/${plan.steps.size}",
                            style = Baic2Mono.label,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(Baic2Spacing.sm))
                    plan.steps.forEach { step ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlanStepIcon(step.status)
                            Spacer(Modifier.width(Baic2Spacing.sm))
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (step.status == PlanStepStatus.DONE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "timeline-label") {
            MonoLabel(
                text = stringResource(R.string.tasks_timeline_title),
                modifier = Modifier.padding(start = Baic2Spacing.xs, top = Baic2Spacing.md),
            )
        }

        if (detail.messages.isEmpty()) {
            item(key = "timeline-empty") {
                Text(
                    text = stringResource(R.string.tasks_timeline_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Baic2Spacing.xs),
                )
            }
        } else {
            items(detail.messages, key = { it.id }) { message ->
                TimelineEntry(
                    message = message,
                    expanded = expandedMessage == message.id,
                    onToggle = {
                        expandedMessage = if (expandedMessage == message.id) null else message.id
                    },
                )
            }
        }

        item(key = "open") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Baic2Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Baic2Spacing.md),
            ) {
                if (run.state == RunState.RUNNING) {
                    OutlinedButton(
                        onClick = onStop,
                        shape = RoundedCornerShape(14.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.tasks_stop_run))
                    }
                }
                if (run.state == RunState.FAILED || run.state == RunState.CANCELLED) {
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.tasks_retry_run))
                    }
                }
                Button(
                    onClick = onOpenConversation,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.tasks_open_conversation))
                }
            }
        }
    }
}

@Composable
private fun PlanStepIcon(status: PlanStepStatus) {
    when (status) {
        PlanStepStatus.DONE -> Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp),
        )
        PlanStepStatus.DOING -> {
            val transition = rememberInfiniteTransition(label = "plan-spin")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
                label = "plan-angle",
            )
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(angle),
            )
        }
        PlanStepStatus.FAILED -> Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        PlanStepStatus.PENDING -> Box(
            modifier = Modifier
                .size(16.dp)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun TimelineEntry(
    message: ChatMessage,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val label = when (message.role) {
        ChatRole.USER -> stringResource(R.string.tasks_role_user)
        ChatRole.ASSISTANT -> stringResource(R.string.tasks_role_assistant)
        ChatRole.TOOL -> message.toolName ?: stringResource(R.string.tasks_role_tool)
        ChatRole.SYSTEM -> "system"
    }
    val labelColor = when (message.role) {
        ChatRole.USER -> MaterialTheme.colorScheme.primary
        ChatRole.ASSISTANT -> MaterialTheme.colorScheme.tertiary
        ChatRole.TOOL -> MaterialTheme.colorScheme.onSurfaceVariant
        ChatRole.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
            .clickable(onClick = onToggle)
            .padding(Baic2Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = Baic2Mono.label,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)
                    .format(java.util.Date(message.createdAt)),
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        if (message.content.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = message.content,
                style = if (message.role == ChatRole.TOOL) {
                    Baic2Mono.body
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        message.toolCalls.forEach { call ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "→ ${call.name}",
                    style = Baic2Mono.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = call.status.name.lowercase(),
                    style = Baic2Mono.label,
                    color = when (call.status) {
                        com.verlintas.baic2.core.model.ToolCallStatus.DONE ->
                            MaterialTheme.colorScheme.tertiary
                        com.verlintas.baic2.core.model.ToolCallStatus.FAILED,
                        com.verlintas.baic2.core.model.ToolCallStatus.DENIED,
                        com.verlintas.baic2.core.model.ToolCallStatus.REJECTED,
                        -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- shared

@Composable
private fun DetailCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(Baic2Spacing.lg),
        content = content,
    )
}

@Composable
private fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = Baic2Mono.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun BudgetLine(
    label: String,
    used: Long = -1L,
    max: Long = -1L,
    usedLabel: String? = null,
    maxLabel: String? = null,
    fraction: Float? = null,
) {
    val ratio = fraction ?: if (max > 0 && used >= 0) used.toFloat() / max else 0f
    val color = when {
        ratio >= 0.9f -> MaterialTheme.colorScheme.error
        ratio >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row {
            Text(
                text = label,
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    usedLabel != null && maxLabel != null -> "$usedLabel / $maxLabel"
                    used >= 0 && max >= 0 -> "$used / $max"
                    else -> ""
                },
                style = Baic2Mono.label,
                color = color,
            )
        }
        if (max > 0 || fraction != null) {
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
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

private fun durationLabel(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) / 1000).toInt()
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
