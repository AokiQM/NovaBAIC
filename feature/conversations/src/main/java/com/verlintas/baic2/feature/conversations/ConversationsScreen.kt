package com.verlintas.baic2.feature.conversations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.verlintas.baic2.designsystem.component.Baic2EmptyState

@Composable
fun ConversationsScreen(modifier: Modifier = Modifier) {
    Baic2EmptyState(
        title = stringResource(R.string.feature_conversations_title),
        description = stringResource(R.string.feature_conversations_description),
        icon = Icons.Outlined.MailOutline,
        badge = stringResource(R.string.feature_conversations_milestone),
        modifier = modifier,
    )
}
