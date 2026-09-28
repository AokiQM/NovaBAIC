package com.verlintas.baic2.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.verlintas.baic2.designsystem.component.Baic2EmptyState

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Baic2EmptyState(
        title = stringResource(R.string.feature_settings_title),
        description = stringResource(R.string.feature_settings_description),
        icon = Icons.Outlined.Settings,
        badge = stringResource(R.string.feature_settings_milestone),
        modifier = modifier,
    )
}
