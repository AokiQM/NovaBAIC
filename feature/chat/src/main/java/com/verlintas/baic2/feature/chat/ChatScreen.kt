package com.verlintas.baic2.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Motion
import com.verlintas.baic2.designsystem.Baic2Spacing
import com.verlintas.baic2.designsystem.component.Baic2ModeChip
import com.verlintas.baic2.designsystem.component.Baic2TypingDots

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var modePickerOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(
        state.messages.size,
        state.streamingText.length,
        state.streamingThinking.length,
        state.liveToolCalls.size,
    ) {
        val target = listState.layoutInfo.totalItemsCount - 1
        if (target >= 0 && !listState.isScrollInProgress) {
            runCatching { listState.animateScrollToItem(target) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ChatTopBar(
            title = state.title.ifBlank { stringResource(R.string.chat_untitled) },
            mode = state.mode,
            running = state.isRunning,
            onBack = onBack,
            onModeClick = { modePickerOpen = true },
            onStop = viewModel::stop,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Baic2Spacing.lg,
                end = Baic2Spacing.lg,
                top = Baic2Spacing.lg,
                bottom = Baic2Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Baic2Spacing.md),
        ) {
            if (state.messages.isEmpty() && !state.isRunning) {
                item(key = "welcome") {
                    WelcomePanel(onSuggestion = { input = it })
                }
            }

            items(state.messages, key = { it.id }) { message ->
                MessageRow(message = message, modifier = Modifier.animateItem())
            }

            val streaming = state.isRunning && (
                state.streamingText.isNotBlank() ||
                    state.streamingThinking.isNotBlank() ||
                    state.liveToolCalls.isNotEmpty()
                )
            if (streaming) {
                item(key = "streaming") {
                    Column(modifier = Modifier.animateItem()) {
                        if (state.streamingThinking.isNotBlank()) {
                            ThinkingCard(text = state.streamingThinking, streaming = true)
                            Spacer(Modifier.size(Baic2Spacing.sm))
                        }
                        if (state.streamingText.isNotBlank()) {
                            MarkdownMessage(text = state.streamingText, streaming = true)
                        }
                        state.liveToolCalls.forEach { call ->
                            Spacer(Modifier.size(Baic2Spacing.sm))
                            ToolCard(call = call)
                        }
                    }
                }
            } else if (state.isRunning) {
                item(key = "waiting") {
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .padding(vertical = Baic2Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Baic2TypingDots()
                    }
                }
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorCard(
                        error = error,
                        canRetry = state.messages.any { it.role == ChatRole.USER },
                        onRetry = {
                            viewModel.dismissError()
                            viewModel.retryLast()
                        },
                        onDismiss = viewModel::dismissError,
                    )
                }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            isRunning = state.isRunning,
            onSend = {
                val text = input
                input = ""
                viewModel.send(text)
            },
            onStop = viewModel::stop,
        )
    }

    if (modePickerOpen) {
        ModePickerSheet(
            current = state.mode,
            onSelect = { mode ->
                viewModel.setMode(mode)
                modePickerOpen = false
            },
            onDismiss = { modePickerOpen = false },
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    mode: AppMode,
    running: Boolean,
    onBack: () -> Unit,
    onModeClick: () -> Unit,
    onStop: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = Baic2Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.chat_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.clickable(onClick = onModeClick),
            ) {
                Baic2ModeChip(
                    mode = mode,
                    modifier = Modifier.padding(horizontal = Baic2Spacing.xs),
                )
            }
            AnimatedVisibility(
                visible = running,
                enter = scaleIn(animationSpec = Baic2Motion.spatialFast()) + fadeIn(),
                exit = scaleOut(animationSpec = Baic2Motion.effectsFast()) + fadeOut(),
            ) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.chat_stop),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun WelcomePanel(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Baic2Spacing.xxl, bottom = Baic2Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.chat_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Baic2Spacing.xs))
        Text(
            text = stringResource(R.string.chat_welcome_subtitle),
            style = Baic2Mono.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Baic2Spacing.xl))
        listOf(
            R.string.chat_suggestion_1,
            R.string.chat_suggestion_2,
            R.string.chat_suggestion_3,
        ).forEach { res ->
            val text = stringResource(res)
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Baic2Spacing.sm)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
                    .clickable { onSuggestion(text) }
                    .padding(horizontal = Baic2Spacing.lg, vertical = Baic2Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    error: ChatError,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), shape)
            .padding(Baic2Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Baic2Spacing.sm))
            Text(
                text = errorText(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Baic2Spacing.xs),
            horizontalArrangement = Arrangement.End,
        ) {
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.chat_retry))
                }
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_dismiss))
            }
        }
    }
}

@Composable
private fun errorText(error: ChatError): String = when (error.kind) {
    ChatError.Kind.PROVIDER -> stringResource(R.string.chat_error_provider, error.detail.orEmpty())
    ChatError.Kind.BUDGET -> stringResource(R.string.chat_error_budget)
    ChatError.Kind.NO_AGENT -> stringResource(R.string.chat_error_no_agent)
    ChatError.Kind.API_KEY -> stringResource(R.string.chat_error_api_key)
    ChatError.Kind.UNSUPPORTED -> stringResource(R.string.chat_error_unsupported)
    ChatError.Kind.INTERNAL -> stringResource(R.string.chat_error_internal, error.detail.orEmpty())
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isRunning: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Baic2Spacing.md, vertical = Baic2Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            val shape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = 1.dp,
                        color = if (focused) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = shape,
                    )
                    .padding(horizontal = Baic2Spacing.lg, vertical = Baic2Spacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_input_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.width(Baic2Spacing.sm))
            SendButton(
                isRunning = isRunning,
                enabled = value.isNotBlank(),
                onSend = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSend()
                },
                onStop = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStop()
                },
            )
        }
    }
}

@Composable
private fun SendButton(
    isRunning: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 1200f),
        label = "send-scale",
    )
    val shape = RoundedCornerShape(16.dp)
    val container = when {
        isRunning -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        isRunning -> MaterialTheme.colorScheme.error
        enabled -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(shape)
            .background(container)
            .border(
                width = 1.dp,
                color = if (isRunning) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                } else {
                    Color.Transparent
                },
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = isRunning || enabled,
            ) {
                if (isRunning) onStop() else onSend()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isRunning,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.7f))
                    .togetherWith(fadeOut() + scaleOut(targetScale = 0.7f))
            },
            label = "send-morph",
        ) { running ->
            Icon(
                imageVector = if (running) Icons.Outlined.Close else Icons.Outlined.Send,
                contentDescription = stringResource(
                    if (running) R.string.chat_stop else R.string.chat_send,
                ),
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePickerSheet(
    current: AppMode,
    onSelect: (AppMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Baic2Spacing.xl)
                .padding(bottom = Baic2Spacing.xxl),
        ) {
            Text(
                text = stringResource(R.string.chat_mode_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Baic2Spacing.lg))
            AppMode.entries.forEach { mode ->
                val selected = mode == current
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Baic2Spacing.sm)
                        .clip(shape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            },
                            shape = shape,
                        )
                        .clickable { onSelect(mode) }
                        .padding(horizontal = Baic2Spacing.lg, vertical = Baic2Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Baic2ModeChip(mode = mode)
                    Spacer(Modifier.width(Baic2Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = modeTitle(mode),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = modeDescription(mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun modeTitle(mode: AppMode): String = stringResource(
    when (mode) {
        AppMode.CHAT -> R.string.chat_mode_chat_title
        AppMode.CHAT_PLUS -> R.string.chat_mode_chat_plus_title
        AppMode.ACT -> R.string.chat_mode_act_title
        AppMode.MAX -> R.string.chat_mode_max_title
    },
)

@Composable
private fun modeDescription(mode: AppMode): String = stringResource(
    when (mode) {
        AppMode.CHAT -> R.string.chat_mode_chat_description
        AppMode.CHAT_PLUS -> R.string.chat_mode_chat_plus_description
        AppMode.ACT -> R.string.chat_mode_act_description
        AppMode.MAX -> R.string.chat_mode_max_description
    },
)
