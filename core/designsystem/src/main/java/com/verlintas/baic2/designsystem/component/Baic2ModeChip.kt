package com.verlintas.baic2.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.R

/** Compact monospace badge for the current autonomy mode. */
@Composable
fun Baic2ModeChip(
    mode: AppMode,
    modifier: Modifier = Modifier,
) {
    val container: Color
    val content: Color
    when (mode) {
        AppMode.CHAT -> {
            container = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        AppMode.CHAT_PLUS -> {
            container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
            content = MaterialTheme.colorScheme.tertiary
        }
        AppMode.ACT -> {
            container = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            content = MaterialTheme.colorScheme.primary
        }
        AppMode.MAX -> {
            container = MaterialTheme.colorScheme.primary
            content = MaterialTheme.colorScheme.onPrimary
        }
    }
    val label = when (mode) {
        AppMode.CHAT -> stringResource(R.string.mode_chat)
        AppMode.CHAT_PLUS -> stringResource(R.string.mode_chat_plus)
        AppMode.ACT -> stringResource(R.string.mode_act)
        AppMode.MAX -> stringResource(R.string.mode_max)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = Baic2Mono.label,
            color = content,
        )
    }
}
