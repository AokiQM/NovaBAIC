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

package com.verlintas.baic2.feature.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verlintas.baic2.core.model.ProviderError
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Spacing

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgentSetupSheet(
    onDismiss: () -> Unit,
    viewModel: AgentSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fieldShape = RoundedCornerShape(12.dp)

    LaunchedEffect(state.saved) {
        if (state.saved) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Baic2Spacing.xl)
                .padding(bottom = Baic2Spacing.xxl),
        ) {
            Text(
                text = stringResource(R.string.agent_setup_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Baic2Spacing.xs))
            Text(
                text = stringResource(R.string.agent_setup_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Baic2Spacing.xl))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(Baic2Spacing.sm)) {
                items(AgentPreset.entries, key = { it.id }) { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { viewModel.selectPreset(preset) },
                        shape = RoundedCornerShape(10.dp),
                        label = { Text(presetLabel(preset)) },
                    )
                }
            }

            Spacer(Modifier.height(Baic2Spacing.lg))

            SetupField(
                label = stringResource(R.string.agent_setup_base_url),
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                shape = fieldShape,
                mono = true,
            )
            Spacer(Modifier.height(Baic2Spacing.md))
            SetupField(
                label = stringResource(R.string.agent_setup_api_key),
                value = state.apiKey,
                onValueChange = viewModel::updateApiKey,
                shape = fieldShape,
                mono = true,
                password = true,
            )
            Spacer(Modifier.height(Baic2Spacing.md))

            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(1f)) {
                    SetupField(
                        label = stringResource(R.string.agent_setup_model),
                        value = state.model,
                        onValueChange = viewModel::updateModel,
                        shape = fieldShape,
                        mono = true,
                    )
                }
                Spacer(Modifier.width(Baic2Spacing.sm))
                OutlinedButton(
                    onClick = viewModel::fetchModels,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !state.fetchingModels,
                    modifier = Modifier.height(56.dp),
                ) {
                    if (state.fetchingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.agent_setup_fetch_models))
                    }
                }
            }

            if (state.models.isNotEmpty()) {
                Spacer(Modifier.height(Baic2Spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Baic2Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Baic2Spacing.xs),
                ) {
                    state.models.take(10).forEach { model ->
                        FilterChip(
                            selected = state.model == model,
                            onClick = { viewModel.updateModel(model) },
                            shape = RoundedCornerShape(10.dp),
                            label = { Text(model, style = Baic2Mono.label) },
                        )
                    }
                }
            }

            state.error?.let { error ->
                Spacer(Modifier.height(Baic2Spacing.md))
                val shape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), shape)
                        .padding(Baic2Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Baic2Spacing.sm))
                    Text(
                        text = setupErrorText(error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(Baic2Spacing.xl))

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.agent_setup_save),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(Baic2Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(Baic2Spacing.xs))
                Text(
                    text = stringResource(R.string.agent_setup_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SetupField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    shape: RoundedCornerShape,
    mono: Boolean = false,
    password: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = Baic2Mono.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Baic2Spacing.xs, bottom = Baic2Spacing.xs),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = shape,
            textStyle = if (mono) Baic2Mono.body else MaterialTheme.typography.bodyMedium,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun presetLabel(preset: AgentPreset): String = stringResource(
    when (preset) {
        AgentPreset.DEEPSEEK -> R.string.preset_deepseek
        AgentPreset.OPENAI -> R.string.preset_openai
        AgentPreset.SILICONFLOW -> R.string.preset_siliconflow
        AgentPreset.MOONSHOT -> R.string.preset_moonshot
        AgentPreset.QWEN -> R.string.preset_qwen
        AgentPreset.CLAUDE -> R.string.preset_claude
        AgentPreset.GEMINI -> R.string.preset_gemini
        AgentPreset.CUSTOM -> R.string.preset_custom
    },
)

@Composable
private fun setupErrorText(error: SetupError): String = when (error) {
    SetupError.MissingKey -> stringResource(R.string.agent_setup_error_key)
    SetupError.MissingUrl -> stringResource(R.string.agent_setup_error_url)
    SetupError.MissingModel -> stringResource(R.string.agent_setup_error_model)
    SetupError.KeyStore -> stringResource(R.string.agent_setup_error_keystore)
    is SetupError.Request -> when (error.kind) {
        ProviderError.Kind.AUTH -> stringResource(R.string.agent_setup_error_auth)
        ProviderError.Kind.NETWORK, ProviderError.Kind.TIMEOUT ->
            stringResource(R.string.agent_setup_error_network)
        ProviderError.Kind.RATE_LIMIT -> stringResource(R.string.agent_setup_error_rate_limit)
        else -> stringResource(R.string.agent_setup_error_generic, error.detail)
    }
}
