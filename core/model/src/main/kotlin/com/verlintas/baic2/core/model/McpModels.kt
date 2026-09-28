package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
data class McpServer(
    val id: Long = 0L,
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
)

@Serializable
data class McpToolInfo(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
    val readOnly: Boolean,
)
