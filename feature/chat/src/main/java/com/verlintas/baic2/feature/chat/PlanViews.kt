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

package com.verlintas.baic2.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.verlintas.baic2.core.model.Plan
import com.verlintas.baic2.core.model.PlanStep
import com.verlintas.baic2.core.model.PlanStepStatus
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Motion
import com.verlintas.baic2.designsystem.Baic2Spacing

/** Live view of the model-maintained plan artifact. */
@Composable
fun PlanCard(
    plan: Plan,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Baic2Motion.spatialFast(),
        label = "plan-chevron",
    )
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = Baic2Spacing.md, vertical = Baic2Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_plan_title),
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${plan.doneCount}/${plan.steps.size}",
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Baic2Spacing.sm))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = Baic2Motion.spatialDefault()) + fadeIn(),
            exit = shrinkVertically(animationSpec = Baic2Motion.effectsFast()) + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = Baic2Spacing.md,
                    end = Baic2Spacing.md,
                    bottom = Baic2Spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(Baic2Spacing.xs),
            ) {
                plan.steps.forEach { step ->
                    StepRow(step = step)
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: PlanStep) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepMarker(status = step.status)
        Spacer(Modifier.width(Baic2Spacing.sm))
        Text(
            text = step.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (step.status == PlanStepStatus.DOING) FontWeight.SemiBold else FontWeight.Normal,
            color = when (step.status) {
                PlanStepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                PlanStepStatus.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (step.status == PlanStepStatus.DONE) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun StepMarker(status: PlanStepStatus) {
    when (status) {
        PlanStepStatus.DONE -> Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(14.dp),
        )

        PlanStepStatus.FAILED -> Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )

        PlanStepStatus.DOING -> {
            val transition = rememberInfiniteTransition(label = "step-doing")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                label = "step-doing-alpha",
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .alpha(alpha)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        PlanStepStatus.PENDING -> Box(
            modifier = Modifier
                .size(14.dp)
                .padding(3.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}
