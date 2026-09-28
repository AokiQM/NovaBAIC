package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

/**
 * Autonomy levels. Chat+ folds the old Plan mode in: read-only tools plus
 * planning are allowed, writes are not.
 */
@Serializable
enum class AppMode {
    CHAT,
    CHAT_PLUS,
    ACT,
    MAX,
    ;

    val toolsVisible: Boolean get() = this != CHAT

    val readOnlyOnly: Boolean get() = this == CHAT_PLUS

    val requiresConfirmation: Boolean get() = this == ACT
}
