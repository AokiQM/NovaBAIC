package com.verlintas.baic2

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.verlintas.baic2.designsystem.component.Baic2EmptyState
import com.verlintas.baic2.feature.conversations.ConversationsScreen
import com.verlintas.baic2.feature.settings.SettingsScreen
import com.verlintas.baic2.feature.tasks.TasksScreen

/**
 * Top-level information architecture: Chats / Tasks / Library / Settings.
 * NavigationSuiteScaffold adapts between a bottom bar (phones) and a rail
 * (tablets, foldables) automatically.
 */
private enum class Baic2Destination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Chats(R.string.nav_chats, Icons.Outlined.MailOutline),
    Tasks(R.string.nav_tasks, Icons.Outlined.List),
    Library(R.string.nav_library, Icons.Outlined.Star),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Baic2App() {
    var destination by rememberSaveable { mutableStateOf(Baic2Destination.Chats) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Baic2Destination.entries.forEach { dest ->
                item(
                    selected = destination == dest,
                    onClick = { destination = dest },
                    icon = { Icon(imageVector = dest.icon, contentDescription = null) },
                    label = { Text(stringResource(dest.labelRes)) },
                )
            }
        },
    ) {
        when (destination) {
            Baic2Destination.Chats -> ConversationsScreen()
            Baic2Destination.Tasks -> TasksScreen()
            Baic2Destination.Library -> LibraryPlaceholder()
            Baic2Destination.Settings -> SettingsScreen()
        }
    }
}

// Temporary M0 placeholder: the Library surface (skills / automations /
// memory) gets its own feature module when those screens are built.
@Composable
private fun LibraryPlaceholder() {
    Baic2EmptyState(
        title = stringResource(R.string.nav_library),
        description = stringResource(R.string.library_placeholder),
    )
}
