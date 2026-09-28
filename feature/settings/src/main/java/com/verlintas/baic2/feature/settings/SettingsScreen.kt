package com.verlintas.baic2.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.ThemeMode
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Spacing
import com.verlintas.baic2.feature.agents.AgentSetupSheet

@Composable
fun SettingsScreen(
    appVersion: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var setupOpen by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                    text = stringResource(R.string.feature_settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "v$appVersion · local-first",
                    style = Baic2Mono.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "agents-title") {
            SectionLabel(stringResource(R.string.settings_agents_title))
        }

        if (state.loading) {
            items(2, key = { "agent-skeleton-$it" }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        } else {
            items(state.agents, key = { it.id }) { agent ->
                AgentRow(
                    agent = agent,
                    onSetDefault = { viewModel.setDefaultAgent(agent.id) },
                    onDelete = { viewModel.deleteAgent(agent.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "agents-add") {
                OutlinedButton(
                    onClick = { setupOpen = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Baic2Spacing.sm))
                    Text(stringResource(R.string.settings_agents_add))
                }
            }
        }

        item(key = "appearance-title") {
            SectionLabel(stringResource(R.string.settings_appearance_title))
        }
        item(key = "appearance") {
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
                    .padding(Baic2Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Baic2Spacing.sm),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = RoundedCornerShape(10.dp),
                        label = { Text(themeLabel(mode)) },
                    )
                }
            }
        }

        item(key = "about-title") {
            SectionLabel(stringResource(R.string.settings_about_title))
        }
        item(key = "about") {
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
                    .padding(Baic2Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Baic2Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "BetterAIChat2 · v$appVersion",
                        style = Baic2Mono.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (setupOpen) {
        AgentSetupSheet(onDismiss = { setupOpen = false })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = Baic2Mono.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Baic2Spacing.xs,
            top = Baic2Spacing.lg,
            bottom = Baic2Spacing.xs,
        ),
    )
}

@Composable
private fun AgentRow(
    agent: Agent,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (agent.isDefault) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
                shape = shape,
            )
            .padding(start = Baic2Spacing.lg, end = Baic2Spacing.sm, top = Baic2Spacing.sm, bottom = Baic2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = agent.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${providerLabel(agent.provider)} · ${agent.model}",
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (agent.isDefault) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.settings_agents_default),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onSetDefault),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                    .clickable(onClick = onSetDefault),
            )
        }
        Spacer(Modifier.width(Baic2Spacing.sm))
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.settings_agents_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun providerLabel(provider: ProviderId): String = stringResource(
    when (provider) {
        ProviderId.OPENAI_COMPATIBLE -> R.string.settings_provider_openai
        ProviderId.ANTHROPIC -> R.string.settings_provider_claude
        ProviderId.GEMINI -> R.string.settings_provider_gemini
    },
)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)
