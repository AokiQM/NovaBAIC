package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AttachmentKind {
    IMAGE,
    TEXT,
}

/**
 * A user attachment.
 *
 * Images live as files ([localPath]); [base64] is filled ephemerally when a
 * request is built and never persisted. Text files keep their content inline
 * ([text]), bounded at import time.
 */
@Serializable
data class Attachment(
    val id: String,
    val kind: AttachmentKind,
    val mimeType: String,
    val fileName: String? = null,
    val localPath: String? = null,
    val text: String? = null,
    val base64: String? = null,
    val sizeBytes: Long = 0L,
)
