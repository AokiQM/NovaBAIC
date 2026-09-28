package com.verlintas.baic2

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.verlintas.baic2.designsystem.Baic2Mono
import com.verlintas.baic2.designsystem.Baic2Motion
import com.verlintas.baic2.designsystem.Baic2Spacing
import com.verlintas.baic2.designsystem.component.Baic2EmptyState
import com.verlintas.baic2.feature.chat.ChatScreen
import com.verlintas.baic2.feature.chat.StarredScreen
import com.verlintas.baic2.feature.conversations.ConversationsScreen
import com.verlintas.baic2.feature.settings.SettingsScreen
import com.verlintas.baic2.feature.tasks.TasksScreen

/**
 * Top-level information architecture: Chats / Tasks / Library / Settings.
 *
 * Navigation chrome is a floating dock in the top-left corner instead of a
 * bottom bar: tapping it unfolds a sidebar panel downward, so the content
 * area (the conversation) owns the whole screen.
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

@Composable
fun Baic2App() {
    var destination by rememberSaveable { mutableStateOf(Baic2Destination.Chats) }
    var dockOpen by rememberSaveable { mutableStateOf(false) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onInnerRoute = backStackEntry?.destination?.route in setOf(ROUTE_CHAT, ROUTE_STARRED)

    LaunchedEffect(onInnerRoute) {
        if (onInnerRoute) dockOpen = false
    }

    BackHandler(enabled = dockOpen) { dockOpen = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                (fadeIn(tween(durationMillis = 220, delayMillis = 60)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 260),
                        initialOffsetY = { it / 24 },
                    ))
                    .togetherWith(fadeOut(tween(durationMillis = 120)))
            },
            label = "zone-content",
        ) { dest ->
            when (dest) {
                Baic2Destination.Chats -> ChatsZone(navController)
                Baic2Destination.Tasks -> TasksScreen()
                Baic2Destination.Library -> LibraryPlaceholder()
                Baic2Destination.Settings -> SettingsScreen(appVersion = BuildConfig.VERSION_NAME)
            }
        }

        DockScrim(visible = dockOpen && !onInnerRoute, onDismiss = { dockOpen = false })

        // The dock (and its label) would collide with inner-screen top bars,
        // so it steps aside while a conversation or the starred list is open.
        AnimatedVisibility(
            visible = !onInnerRoute,
            enter = fadeIn(tween(durationMillis = 160)) +
                scaleIn(initialScale = 0.9f, animationSpec = Baic2Motion.spatialFast()),
            exit = fadeOut(tween(durationMillis = 120)) +
                scaleOut(targetScale = 0.9f, animationSpec = Baic2Motion.effectsFast()),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            DockAnchor(
                current = destination,
                open = dockOpen,
                onToggle = { dockOpen = !dockOpen },
                onSelect = { dest ->
                    destination = dest
                    dockOpen = false
                },
            )
        }
    }
}

@Composable
private fun ChatsZone(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_CONVERSATIONS,
    ) {
        composable(ROUTE_CONVERSATIONS) {
            ConversationsScreen(
                onOpenConversation = { id -> navController.navigate("chat/$id") },
            )
        }
        composable(
            route = ROUTE_CHAT,
            arguments = listOf(
                navArgument(ChatViewModelArgs.CONVERSATION_ID) { type = NavType.LongType },
            ),
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenStarred = { navController.navigate(ROUTE_STARRED) },
            )
        }
        composable(ROUTE_STARRED) {
            StarredScreen(
                onBack = { navController.popBackStack() },
                onOpenConversation = { id -> navController.navigate("chat/$id") },
            )
        }
    }
}

private const val ROUTE_CONVERSATIONS = "conversations"
private const val ROUTE_CHAT = "chat/{conversationId}"
private const val ROUTE_STARRED = "starred"

private object ChatViewModelArgs {
    const val CONVERSATION_ID = "conversationId"
}

// Temporary M0 placeholder: the Library surface (skills / automations /
// memory) gets its own feature module when those screens are built.
@Composable
private fun LibraryPlaceholder() {
    Baic2EmptyState(
        title = stringResource(R.string.nav_library),
        description = stringResource(R.string.library_placeholder),
        icon = Icons.Outlined.Star,
        badge = stringResource(R.string.library_milestone),
    )
}

@Composable
private fun BoxScope.DockScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 220)),
        exit = fadeOut(tween(durationMillis = 160)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
}

@Composable
private fun DockAnchor(
    current: Baic2Destination,
    open: Boolean,
    onToggle: () -> Unit,
    onSelect: (Baic2Destination) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(Baic2Spacing.md),
    ) {
        DockTrigger(
            current = current,
            open = open,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
        )

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(
                animationSpec = Baic2Motion.spatialDefault<IntSize>(),
                expandFrom = Alignment.Top,
            ) + fadeIn(Baic2Motion.effectsFast<Float>()),
            exit = shrinkVertically(
                animationSpec = Baic2Motion.effectsDefault<IntSize>(),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(Baic2Motion.effectsFast<Float>()),
        ) {
            DockPanel(
                current = current,
                onSelect = { dest ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(dest)
                },
            )
        }
    }
}

@Composable
private fun DockTrigger(
    current: Baic2Destination,
    open: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = Baic2Motion.spatialFast<Float>(),
        label = "dock-chevron",
    )
    val dockLabel = stringResource(R.string.dock_content_description)
    val activate = onClick
    val borderColor = if (open) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clearAndSetSemantics {
                contentDescription = dockLabel
                role = Role.Button
                onClick(label = dockLabel) {
                    activate()
                    true
                }
            }
            .clickable(onClick = activate)
            .padding(horizontal = Baic2Spacing.md, vertical = Baic2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (fadeIn(tween(durationMillis = 180)) + scaleIn(initialScale = 0.8f))
                    .togetherWith(fadeOut(tween(durationMillis = 120)) + scaleOut(targetScale = 0.8f))
            },
            label = "dock-zone-icon",
        ) { dest ->
            Icon(
                imageVector = dest.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(Baic2Spacing.sm))
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(14.dp)
                .rotate(chevronRotation),
        )
    }
}

@Composable
private fun AnimatedVisibilityScope.DockPanel(
    current: Baic2Destination,
    onSelect: (Baic2Destination) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .padding(top = Baic2Spacing.sm)
            .widthIn(max = 300.dp)
            .fillMaxWidth(0.84f)
            .shadow(elevation = 18.dp, shape = shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = shape)
            .padding(Baic2Spacing.sm),
    ) {
        DockHeader()

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = Baic2Spacing.sm),
        )

        Baic2Destination.entries.forEachIndexed { index, dest ->
            DockItem(
                destination = dest,
                selected = dest == current,
                onClick = { onSelect(dest) },
                modifier = Modifier.animateEnterExit(
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = 180,
                            delayMillis = index * 45,
                        ),
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = 220,
                            delayMillis = index * 45,
                        ),
                        initialOffsetY = { -it / 3 },
                    ),
                    exit = fadeOut(tween(durationMillis = 90)),
                ),
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = Baic2Spacing.sm),
        )

        Text(
            text = stringResource(R.string.dock_footer),
            style = Baic2Mono.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = Baic2Spacing.sm, vertical = Baic2Spacing.xs),
        )
    }
}

@Composable
private fun DockHeader() {
    Row(
        modifier = Modifier.padding(horizontal = Baic2Spacing.sm, vertical = Baic2Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "B2",
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(Baic2Spacing.sm))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME} · ${stringResource(R.string.dock_channel)}",
                style = Baic2Mono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DockItem(
    destination: Baic2Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val label = stringResource(destination.labelRes)
    val activate = onClick
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(container)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
                onClick(label = label) {
                    activate()
                    true
                }
            }
            .selectable(
                selected = selected,
                onClick = activate,
                role = Role.Tab,
            )
            .padding(horizontal = Baic2Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Baic2Spacing.sm),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else contentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
