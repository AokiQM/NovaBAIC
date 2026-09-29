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

package com.verlintas.baic2.feature.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verlintas.baic2.core.model.AccentColor
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.AppLanguage
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.ThemeMode
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Spacing
import com.verlintas.baic2.designsystem.accentSpec

// ---------------------------------------------------------------- agents

@Composable
fun AgentsPage(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTarget by rememberSaveable { mutableStateOf<Long?>(null) }

    SettingsPage(
        title = stringResource(R.string.settings_agents_title),
        onBack = onBack,
    ) {
        item(key = "add") {
            OutlinedButton(
                onClick = onAdd,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Baic2Spacing.sm))
                Text(stringResource(R.string.settings_agents_add))
            }
        }
        if (state.agents.isEmpty()) {
            item(key = "empty") {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.settings_agents_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Baic2Spacing.lg),
                    )
                }
            }
        }
        items(state.agents.size, key = { state.agents[it].id }) { index ->
            val agent = state.agents[index]
            AgentRow(
                agent = agent,
                onEdit = { onEdit(agent.id) },
                onSetDefault = { viewModel.setDefaultAgent(agent.id) },
                onDelete = { deleteTarget = agent.id },
            )
        }
        item(key = "hint") {
            Text(
                text = stringResource(R.string.settings_agents_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Baic2Spacing.xs,
                    top = Baic2Spacing.md,
                ),
            )
        }
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.settings_agents_delete)) },
            text = { Text(stringResource(R.string.settings_agents_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAgent(id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.settings_agents_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun AgentRow(
    agent: Agent,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsRow(
        title = agent.name,
        summary = "${providerLabel(agent.provider)} · ${agent.model}",
        showChevron = true,
        onClick = onEdit,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSetDefault),
                    contentAlignment = Alignment.Center,
                ) {
                    if (agent.isDefault) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.settings_agents_default),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.settings_agents_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

// ---------------------------------------------------------------- appearance

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearancePage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsPage(
        title = stringResource(R.string.settings_appearance_title),
        onBack = onBack,
    ) {
        item(key = "theme") {
            Column {
                SettingsSectionLabel(stringResource(R.string.settings_theme_mode))
                Row(horizontalArrangement = Arrangement.spacedBy(Baic2Spacing.sm)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = RoundedCornerShape(10.dp),
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        item(key = "accent") {
            Column {
                SettingsSectionLabel(stringResource(R.string.settings_accent_color))
                SettingsCard {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Baic2Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Baic2Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Baic2Spacing.lg),
                    ) {
                        AccentColor.entries.forEach { accent ->
                            AccentDot(
                                accent = accent,
                                selected = state.accent == accent,
                                onClick = { viewModel.setAccent(accent) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentDot(
    accent: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        label = "accent-scale",
    )
    val color = accentSpec(accent).darkPrimary
    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color(0xFF10151C),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- language

@Composable
fun LanguagePage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsPage(
        title = stringResource(R.string.settings_language_title),
        onBack = onBack,
    ) {
        items(AppLanguage.entries.size, key = { AppLanguage.entries[it].name }) { index ->
            val language = AppLanguage.entries[index]
            SettingsRow(
                title = when (language) {
                    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                    AppLanguage.CHINESE -> stringResource(R.string.language_chinese)
                    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                },
                summary = language.languageTag ?: stringResource(R.string.language_system_hint),
                onClick = {
                    if (state.language != language) {
                        viewModel.setLanguage(language)
                        (context as? Activity)?.recreate()
                    }
                },
                trailing = {
                    if (state.language == language) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
        item(key = "hint") {
            Text(
                text = stringResource(R.string.settings_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Baic2Spacing.xs),
            )
        }
    }
}

// ---------------------------------------------------------------- storage

@Composable
fun StoragePage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val usage = state.storage

    SettingsPage(
        title = stringResource(R.string.settings_storage_title),
        onBack = onBack,
    ) {
        item(key = "overview") {
            SettingsCard {
                Column(modifier = Modifier.padding(Baic2Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_storage_total),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = usage?.let { formatBytes(it.totalBytes) } ?: "…",
                            style = Baic2Mono.body,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(Baic2Spacing.sm))
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.settings_storage_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { viewModel.refreshStorage() },
                        )
                    }
                    Spacer(Modifier.height(Baic2Spacing.sm))
                    usage?.let {
                        StorageLine(stringResource(R.string.settings_storage_db), formatBytes(it.databaseBytes))
                        StorageLine(
                            stringResource(R.string.settings_storage_attachments),
                            "${formatBytes(it.attachmentsBytes)} · ${it.attachmentsCount}",
                        )
                        StorageLine(
                            stringResource(R.string.settings_storage_screenshots),
                            "${formatBytes(it.screenshotsBytes)} · ${it.screenshotsCount}",
                        )
                        StorageLine(stringResource(R.string.settings_storage_skills), formatBytes(it.skillsBytes))
                    }
                }
            }
        }
        item(key = "actions") { SettingsSectionLabel(stringResource(R.string.settings_storage_cleanup)) }
        item(key = "clear-attachments") {
            SettingsRow(
                title = stringResource(R.string.settings_storage_clear_attachments),
                summary = stringResource(R.string.settings_storage_clear_attachments_desc),
                onClick = { confirm = "attachments" },
            )
        }
        item(key = "clear-screenshots") {
            SettingsRow(
                title = stringResource(R.string.settings_storage_clear_screenshots),
                summary = stringResource(R.string.settings_storage_clear_screenshots_desc),
                onClick = { confirm = "screenshots" },
            )
        }
        item(key = "clear-conversations") {
            SettingsRow(
                title = stringResource(R.string.settings_storage_clear_all),
                summary = stringResource(R.string.settings_storage_clear_all_desc),
                onClick = { confirm = "conversations" },
            )
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    when (action) {
                        "conversations" -> stringResource(R.string.settings_storage_clear_all)
                        "screenshots" -> stringResource(R.string.settings_storage_clear_screenshots)
                        else -> stringResource(R.string.settings_storage_clear_attachments)
                    },
                )
            },
            text = { Text(stringResource(R.string.settings_storage_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            "conversations" -> viewModel.clearConversations {}
                            "screenshots" -> viewModel.clearScreenshots {}
                            else -> viewModel.clearAttachments {}
                        }
                        confirm = null
                    },
                ) {
                    Text(stringResource(R.string.settings_storage_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun StorageLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = Baic2Mono.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = Baic2Mono.label,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------- about

@Composable
fun AboutPage(
    buildInfo: BuildInfo,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val repoUrl = "https://github.com/Verlintas/NovaBAIC"

    SettingsPage(
        title = stringResource(R.string.settings_about_title),
        onBack = onBack,
    ) {
        item(key = "brand") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Baic2Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_brand),
                    contentDescription = null,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(22.dp)),
                )
                Spacer(Modifier.height(Baic2Spacing.lg))
                Text(
                    text = stringResource(R.string.settings_brand_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "v${buildInfo.versionName} (${buildInfo.buildType})",
                    style = Baic2Mono.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Baic2Spacing.sm))
                Text(
                    text = stringResource(R.string.settings_about_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "links") { SettingsSectionLabel(stringResource(R.string.settings_about_links)) }
        item(key = "repo") {
            SettingsRow(
                title = stringResource(R.string.settings_about_repo),
                summary = repoUrl,
                icon = Icons.Outlined.Info,
                showChevron = true,
                onClick = { openUrl(context, repoUrl) },
            )
        }
        item(key = "releases") {
            SettingsRow(
                title = stringResource(R.string.settings_about_releases),
                icon = Icons.Outlined.Refresh,
                showChevron = true,
                onClick = { openUrl(context, "$repoUrl/releases") },
            )
        }
        item(key = "issues") {
            SettingsRow(
                title = stringResource(R.string.settings_about_issues),
                icon = Icons.Outlined.Info,
                showChevron = true,
                onClick = { openUrl(context, "$repoUrl/issues") },
            )
        }
        item(key = "licenses") {
            SettingsRow(
                title = stringResource(R.string.settings_about_licenses),
                icon = Icons.Outlined.Info,
                showChevron = true,
                onClick = onOpenLicenses,
            )
        }
        item(key = "legal") { SettingsSectionLabel(stringResource(R.string.settings_about_legal)) }
        item(key = "license") {
            SettingsRow(
                title = "GPL-3.0-or-later",
                summary = stringResource(R.string.settings_about_license_summary),
                onClick = { openUrl(context, "$repoUrl/blob/main/LICENSE") },
            )
        }
        item(key = "copyright") {
            Text(
                text = stringResource(R.string.settings_about_copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Baic2Spacing.xs,
                    top = Baic2Spacing.md,
                ),
            )
        }
    }
}

@Composable
fun LicensesPage(onBack: () -> Unit) {
    val libraries = listOf(
        "Kotlin & kotlinx.coroutines" to "Apache-2.0",
        "Jetpack Compose (UI, Material 3)" to "Apache-2.0",
        "AndroidX (Core, Lifecycle, Navigation, Room, DataStore, Activity)" to "Apache-2.0",
        "Dagger Hilt" to "Apache-2.0",
        "OkHttp" to "Apache-2.0",
        "kotlinx.serialization" to "Apache-2.0",
        "ML Kit Text Recognition (Chinese)" to "Google ML Kit Terms",
        "ZXing" to "Apache-2.0",
        "Jsoup" to "MIT",
        "SnakeYAML" to "Apache-2.0",
    )
    SettingsPage(
        title = stringResource(R.string.settings_licenses_title),
        onBack = onBack,
    ) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.settings_licenses_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Baic2Spacing.xs),
            )
        }
        items(libraries.size, key = { libraries[it].first }) { index ->
            val (name, license) = libraries[index]
            SettingsRow(title = name, summary = license)
        }
    }
}

// ---------------------------------------------------------------- developer

@Composable
fun DeveloperPage(
    buildInfo: BuildInfo,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    SettingsPage(
        title = stringResource(R.string.settings_developer_title),
        onBack = onBack,
    ) {
        item(key = "build") { SettingsSectionLabel(stringResource(R.string.settings_developer_build)) }
        item(key = "app-id") {
            SettingsRow(title = stringResource(R.string.settings_developer_app_id), summary = buildInfo.applicationId)
        }
        item(key = "version") {
            SettingsRow(
                title = stringResource(R.string.settings_developer_version),
                summary = "${buildInfo.versionName} · ${buildInfo.buildType}",
            )
        }
        item(key = "author") { SettingsSectionLabel(stringResource(R.string.settings_developer_author)) }
        item(key = "github") {
            SettingsRow(
                title = "Verlintas",
                summary = "github.com/Verlintas",
                showChevron = true,
                onClick = { openUrl(context, "https://github.com/Verlintas") },
            )
        }
        item(key = "mail") {
            SettingsRow(
                title = stringResource(R.string.settings_developer_email),
                summary = "ulv777777@gmail.com",
                showChevron = true,
                onClick = { openUrl(context, "mailto:ulv777777@gmail.com") },
            )
        }
        item(key = "note") {
            Text(
                text = stringResource(R.string.settings_developer_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Baic2Spacing.xs,
                    top = Baic2Spacing.md,
                ),
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
