package com.verlintas.baic2.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.verlintas.baic2.core.model.Automation
import com.verlintas.baic2.core.model.McpServer
import com.verlintas.baic2.core.model.Memory
import com.verlintas.baic2.core.model.Skill
import com.verlintas.baic2.mcp.McpServerStatus
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Spacing

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var mcpDialogOpen by rememberSaveable { mutableStateOf(false) }
    val skillPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.importSkill(it, it.lastPathSegment?.substringAfterLast('/'))
        }
    }

    if (mcpDialogOpen) {
        var name by rememberSaveable { mutableStateOf("") }
        var url by rememberSaveable { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { mcpDialogOpen = false },
            title = { Text(stringResource(R.string.library_mcp_add)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.library_mcp_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Baic2Spacing.sm))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.library_mcp_url)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addMcpServer(name, url)
                        mcpDialogOpen = false
                    },
                    enabled = name.isNotBlank() && url.startsWith("http"),
                ) {
                    Text(stringResource(R.string.library_mcp_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { mcpDialogOpen = false }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }

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
                    text = stringResource(R.string.feature_library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.library_subtitle,
                        state.automations.size,
                        state.memories.size,
                    ),
                    style = Baic2Mono.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "automations-title") {
            SectionLabel(stringResource(R.string.library_automations_title))
        }
        if (state.automations.isEmpty()) {
            item(key = "automations-empty") {
                HintCard(stringResource(R.string.library_automations_empty))
            }
        } else {
            items(state.automations, key = { "auto-${it.id}" }) { automation ->
                AutomationRow(
                    automation = automation,
                    onToggle = { enabled -> viewModel.setAutomationEnabled(automation.id, enabled) },
                    onDelete = { viewModel.deleteAutomation(automation.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(key = "skills-title") {
            SectionLabel(stringResource(R.string.library_skills_title))
        }
        item(key = "skills-import") {
            OutlinedButton(
                onClick = {
                    skillPicker.launch(arrayOf("application/yaml", "application/x-yaml", "text/yaml", "text/plain"))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.library_skills_import))
            }
        }
        if (state.skills.isEmpty()) {
            item(key = "skills-empty") {
                HintCard(stringResource(R.string.library_skills_empty))
            }
        } else {
            items(state.skills, key = { "skill-${it.id}" }) { skill ->
                SkillRow(
                    skill = skill,
                    onDelete = { viewModel.deleteSkill(skill.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(key = "mcp-title") {
            SectionLabel(stringResource(R.string.library_mcp_title))
        }
        item(key = "mcp-add") {
            OutlinedButton(
                onClick = { mcpDialogOpen = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.library_mcp_add))
            }
        }
        if (state.mcpServers.isEmpty()) {
            item(key = "mcp-empty") {
                HintCard(stringResource(R.string.library_mcp_empty))
            }
        } else {
            items(state.mcpServers, key = { "mcp-${it.id}" }) { server ->
                McpRow(
                    server = server,
                    status = state.mcpStatus[server.id],
                    onToggle = { enabled -> viewModel.setMcpEnabled(server.id, enabled) },
                    onDelete = { viewModel.deleteMcpServer(server.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(key = "memory-title") {
            SectionLabel(stringResource(R.string.library_memory_title))
        }
        if (state.memories.isEmpty()) {
            item(key = "memory-empty") {
                HintCard(stringResource(R.string.library_memory_empty))
            }
        } else {
            items(state.memories, key = { "mem-${it.id}" }) { memory ->
                MemoryRow(
                    memory = memory,
                    onDelete = { viewModel.deleteMemory(memory.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
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
private fun HintCard(text: String) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(horizontal = Baic2Spacing.lg, vertical = Baic2Spacing.md),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutomationRow(
    automation: Automation,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(start = Baic2Spacing.lg, end = Baic2Spacing.sm, top = Baic2Spacing.sm, bottom = Baic2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = automation.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = automation.scheduleLabel() + " · " +
                    automation.actions.joinToString(", ") { it.tool },
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = automation.enabled,
            onCheckedChange = onToggle,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.library_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun McpRow(
    server: McpServer,
    status: McpServerStatus?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(start = Baic2Spacing.lg, end = Baic2Spacing.sm, top = Baic2Spacing.sm, bottom = Baic2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val statusText = when (status) {
                is McpServerStatus.Connected ->
                    stringResource(R.string.library_mcp_connected, status.toolCount)

                is McpServerStatus.Failed ->
                    stringResource(R.string.library_mcp_failed, status.reason)

                McpServerStatus.Disabled -> stringResource(R.string.library_mcp_disabled)
                null -> server.url
            }
            Text(
                text = statusText,
                style = Baic2Mono.label,
                color = if (status is McpServerStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = server.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.library_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SkillRow(
    skill: Skill,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(start = Baic2Spacing.lg, end = Baic2Spacing.sm, top = Baic2Spacing.md, bottom = Baic2Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = skill.description.ifBlank { skill.id } +
                    " · " + stringResource(R.string.library_skill_tools, skill.tools.size),
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.library_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MemoryRow(
    memory: Memory,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(start = Baic2Spacing.lg, end = Baic2Spacing.sm, top = Baic2Spacing.md, bottom = Baic2Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = memory.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Baic2Spacing.sm))
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.library_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
