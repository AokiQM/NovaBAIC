package com.verlintas.baic2.device.api

import kotlinx.coroutines.flow.StateFlow

/** One accessibility node's text with its on-screen bounds. */
data class TextNode(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * UI automation through an AccessibilityService. All gestures run on the
 * main thread; a missing service produces an actionable failure.
 */
interface AccessibilityBridge {
    val connected: StateFlow<Boolean>

    suspend fun tap(x: Int, y: Int): Result<Unit>

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Result<Unit>

    suspend fun typeText(text: String): Result<Unit>

    /** key: back | home | recents | notifications */
    suspend fun pressKey(key: String): Result<Unit>

    /** Interactive elements whose text/description contains [query]. */
    fun findText(query: String): List<TextNode>

    fun screenText(maxNodes: Int = 200): String

    /** Package name of the app currently in the foreground, when known. */
    fun foregroundPackage(): String?
}
