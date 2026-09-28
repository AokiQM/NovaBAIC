package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

/** Durable local knowledge. */
@Serializable
enum class MemoryKind {
    /** A distilled user fact (name, preferences, agreements...). */
    MEMORY,

    /** A compressed snapshot of older conversation history. */
    SNAPSHOT,
}

@Serializable
data class Memory(
    val id: Long = 0L,
    val kind: MemoryKind,
    val content: String,
    val conversationId: Long? = null,
    val createdAt: Long = 0L,
)
