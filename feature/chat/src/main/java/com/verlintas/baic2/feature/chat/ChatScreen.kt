package com.verlintas.baic2.feature.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.verlintas.baic2.designsystem.component.Baic2EmptyState

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    Baic2EmptyState(
        title = stringResource(R.string.feature_chat_title),
        description = stringResource(R.string.feature_chat_description),
        icon = Icons.Outlined.Send,
        badge = stringResource(R.string.feature_chat_milestone),
        modifier = modifier,
    )
}
