package com.verlintas.baic2.device.impl.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.verlintas.baic2.device.api.AccessibilityBridge
import com.verlintas.baic2.device.api.TextNode
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class AndroidAccessibilityBridge @Inject constructor() : AccessibilityBridge {

    @Volatile
    private var service: Baic2AccessibilityService? = null

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun attach(active: Baic2AccessibilityService) {
        service = active
        _connected.value = true
    }

    fun detach(active: Baic2AccessibilityService) {
        if (service === active) {
            service = null
            _connected.value = false
        }
    }

    override suspend fun tap(x: Int, y: Int): Result<Unit> = withContext(Dispatchers.Main) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        dispatch(path, durationMs = 60)
    }

    override suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int,
    ): Result<Unit> = withContext(Dispatchers.Main) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        dispatch(path, durationMs = durationMs.coerceIn(50, 2_000))
    }

    override suspend fun typeText(text: String): Result<Unit> = withContext(Dispatchers.Main) {
        val active = service ?: return@withContext notEnabled()
        val target = active.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: active.rootInActiveWindow?.firstEditable()
            ?: return@withContext Result.failure(
                IllegalStateException("no_focused_input: tap a text field first"),
            )
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("set_text_rejected: the field refused the input"))
        }
    }

    override suspend fun pressKey(key: String): Result<Unit> = withContext(Dispatchers.Main) {
        val active = service ?: return@withContext notEnabled()
        val action = when (key.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications", "shade" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> return@withContext Result.failure(
                IllegalArgumentException("unknown_key: use back|home|recents|notifications"),
            )
        }
        if (active.performGlobalAction(action)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("global_action_rejected"))
        }
    }

    override fun findText(query: String): List<TextNode> {
        val root = service?.rootInActiveWindow ?: return emptyList()
        val needle = query.trim().lowercase()
        val results = mutableListOf<TextNode>()
        root.walk { node ->
            val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (label != null && label.lowercase().contains(needle)) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                if (!bounds.isEmpty) {
                    results += TextNode(label, bounds.left, bounds.top, bounds.right, bounds.bottom)
                }
            }
        }
        return results
    }

    override fun screenText(maxNodes: Int): String {
        val root = service?.rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        root.walk { node ->
            if (lines.size < maxNodes) {
                val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                label?.let { lines += it }
            }
        }
        return lines.joinToString("\n")
    }

    override fun foregroundPackage(): String? =
        runCatching { service?.rootInActiveWindow?.packageName?.toString() }.getOrNull()

    private suspend fun dispatch(path: Path, durationMs: Int): Result<Unit> {
        val active = service ?: return notEnabled()
        return suspendCancellableCoroutine { continuation ->
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.toLong()))
                .build()
            val accepted = active.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(description: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(Result.success(Unit))
                    }

                    override fun onCancelled(description: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(IllegalStateException("gesture_cancelled")),
                            )
                        }
                    }
                },
                null,
            )
            if (!accepted && continuation.isActive) {
                continuation.resume(Result.failure(IllegalStateException("gesture_rejected")))
            }
        }
    }

    private fun notEnabled(): Result<Unit> = Result.failure(
        IllegalStateException(
            "accessibility_not_enabled: ask the user to enable the BAIC2 accessibility service " +
                "in 设置 → 无障碍, then retry.",
        ),
    )

    private inline fun AccessibilityNodeInfo.walk(visit: (AccessibilityNodeInfo) -> Unit) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(this)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            runCatching { visit(node) }
            for (index in 0 until runCatching { node.childCount }.getOrDefault(0)) {
                runCatching { node.getChild(index) }.getOrNull()?.let { queue.add(it) }
            }
        }
    }

    private fun AccessibilityNodeInfo.firstEditable(): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk { node ->
            if (found == null && node.isEditable && node.isEnabled) {
                found = node
            }
        }
        return found
    }

    private companion object {
        const val MAX_NODES = 800
    }
}
