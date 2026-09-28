package com.verlintas.baic2.feature.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.verlintas.baic2.designsystem.component.Baic2EmptyState

@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    Baic2EmptyState(
        title = stringResource(R.string.feature_tasks_title),
        description = stringResource(R.string.feature_tasks_description),
        icon = Icons.Outlined.List,
        badge = stringResource(R.string.feature_tasks_milestone),
        modifier = modifier,
    )
}
