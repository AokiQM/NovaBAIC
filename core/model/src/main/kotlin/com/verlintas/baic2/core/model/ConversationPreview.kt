package com.verlintas.baic2.core.model

/** Conversation list row: the conversation plus its last message preview. */
data class ConversationPreview(
    val conversation: Conversation,
    val lastMessage: String?,
)
