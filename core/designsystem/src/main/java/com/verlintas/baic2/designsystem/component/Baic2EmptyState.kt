package com.verlintas.baic2.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Spacing

/**
 * Centered empty / placeholder state with an optional icon and a monospace
 * badge (milestone tag, count, shortcut...).
 */
@Composable
fun Baic2EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    badge: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Baic2Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            val iconShape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(iconShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = iconShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(Baic2Spacing.lg))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        if (description != null) {
            Spacer(Modifier.height(Baic2Spacing.sm))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (badge != null) {
            val badgeShape = RoundedCornerShape(6.dp)
            Spacer(Modifier.height(Baic2Spacing.lg))
            Box(
                modifier = Modifier
                    .clip(badgeShape)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = badgeShape)
                    .padding(horizontal = Baic2Spacing.sm, vertical = 3.dp),
            ) {
                Text(
                    text = badge,
                    style = Baic2Mono.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (action != null) {
            Spacer(Modifier.height(Baic2Spacing.xl))
            action()
        }
    }
}
